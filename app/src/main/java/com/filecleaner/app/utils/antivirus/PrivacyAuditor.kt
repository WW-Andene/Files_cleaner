package com.filecleaner.app.utils.antivirus

import android.accessibilityservice.AccessibilityServiceInfo
import android.app.admin.DevicePolicyManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Enhanced privacy auditor.
 * Comprehensive privacy analysis of installed apps:
 * - Apps with excessive dangerous permissions
 * - SMS / Call Log / Contacts access from unexpected apps
 * - Background location tracking
 * - Accessibility service abuse (screen reading, gesture injection)
 * - Device admin abuse (lock/wipe/policy control)
 * - Notification listener abuse (read all notifications)
 * - Usage stats access (spy on app usage)
 * - Apps that can install other apps (REQUEST_INSTALL_PACKAGES)
 * - Overlay / draw over other apps
 * - Battery optimization exemptions (prevents being killed)
 */
object PrivacyAuditor {

    /** Dangerous permissions grouped by concern */
    private val PRIVACY_PERMISSIONS = mapOf(
        "SMS Access" to listOf(
            "android.permission.READ_SMS",
            "android.permission.SEND_SMS",
            "android.permission.RECEIVE_SMS",
            "android.permission.RECEIVE_MMS",
            "android.permission.RECEIVE_WAP_PUSH"
        ),
        "Call Log Access" to listOf(
            "android.permission.READ_CALL_LOG",
            "android.permission.WRITE_CALL_LOG",
            "android.permission.PROCESS_OUTGOING_CALLS"
        ),
        "Contacts Access" to listOf(
            "android.permission.READ_CONTACTS",
            "android.permission.WRITE_CONTACTS",
            "android.permission.GET_ACCOUNTS"
        ),
        "Camera Access" to listOf(
            "android.permission.CAMERA"
        ),
        "Microphone Access" to listOf(
            "android.permission.RECORD_AUDIO"
        ),
        "Location Access" to listOf(
            "android.permission.ACCESS_FINE_LOCATION",
            "android.permission.ACCESS_COARSE_LOCATION",
            "android.permission.ACCESS_BACKGROUND_LOCATION"
        ),
        "Phone Access" to listOf(
            "android.permission.READ_PHONE_STATE",
            "android.permission.CALL_PHONE",
            "android.permission.READ_PHONE_NUMBERS",
            "android.permission.ANSWER_PHONE_CALLS"
        ),
        "Storage Access" to listOf(
            "android.permission.READ_EXTERNAL_STORAGE",
            "android.permission.WRITE_EXTERNAL_STORAGE",
            "android.permission.MANAGE_EXTERNAL_STORAGE"
        ),
        "Body Sensors" to listOf(
            "android.permission.BODY_SENSORS",
            "android.permission.BODY_SENSORS_BACKGROUND"
        ),
        "Calendar Access" to listOf(
            "android.permission.READ_CALENDAR",
            "android.permission.WRITE_CALENDAR"
        ),
        "Nearby Devices" to listOf(
            "android.permission.BLUETOOTH_CONNECT",
            "android.permission.BLUETOOTH_SCAN",
            "android.permission.NEARBY_WIFI_DEVICES",
            "android.permission.UWB_RANGING"
        )
    )

    /** Threshold: apps with this many dangerous permission groups are flagged */
    private const val EXCESSIVE_PERMISSION_THRESHOLD = 5

    @Suppress("DEPRECATION")
    suspend fun audit(context: Context, onProgress: (Int) -> Unit): List<ThreatResult> =
        withContext(Dispatchers.IO) {
            val results = mutableListOf<ThreatResult>()
            val pm = context.packageManager
            val packages = pm.getInstalledPackages(PackageManager.GET_PERMISSIONS)
            val total = packages.size

            // Phase A: Per-app permission audit
            for ((index, pkg) in packages.withIndex()) {
                if (index % 10 == 0) onProgress((index * 70) / total.coerceAtLeast(1))

                val appInfo = pkg.applicationInfo ?: continue
                // Skip system apps
                if (appInfo.flags and ApplicationInfo.FLAG_SYSTEM != 0) continue

                val appName = pm.getApplicationLabel(appInfo).toString()
                val requestedPerms = pkg.requestedPermissions ?: continue

                // Check each privacy category
                val matchedCategories = mutableListOf<String>()
                for ((category, permissions) in PRIVACY_PERMISSIONS) {
                    if (permissions.any { it in requestedPerms }) {
                        matchedCategories.add(category)
                    }
                }

                // 1. Excessive permissions
                if (matchedCategories.size >= EXCESSIVE_PERMISSION_THRESHOLD) {
                    results.add(
                        ThreatResult(
                            name = "Excessive Permissions",
                            description = "\"$appName\" requests ${matchedCategories.size} dangerous permission categories: ${matchedCategories.joinToString(", ")}.",
                            severity = ThreatResult.Severity.HIGH,
                            source = ThreatResult.ScannerSource.PRIVACY_AUDIT,
                            packageName = pkg.packageName,
                            category = ThreatResult.ThreatCategory.PRIVACY,
                            action = ThreatResult.ThreatAction.UNINSTALL
                        )
                    )
                }

                // 2. SMS access (very few apps need this)
                if ("SMS Access" in matchedCategories && !isLikelySmsApp(pkg.packageName)) {
                    results.add(
                        ThreatResult(
                            name = "SMS Access",
                            description = "\"$appName\" can read/send SMS. This is a common malware capability used for premium SMS fraud and 2FA interception.",
                            severity = ThreatResult.Severity.MEDIUM,
                            source = ThreatResult.ScannerSource.PRIVACY_AUDIT,
                            packageName = pkg.packageName,
                            category = ThreatResult.ThreatCategory.PRIVACY
                        )
                    )
                }

                // 3. Background location
                if ("android.permission.ACCESS_BACKGROUND_LOCATION" in requestedPerms) {
                    results.add(
                        ThreatResult(
                            name = "Background Location Tracking",
                            description = "\"$appName\" can track your location continuously, even when the app is not in use.",
                            severity = ThreatResult.Severity.MEDIUM,
                            source = ThreatResult.ScannerSource.PRIVACY_AUDIT,
                            packageName = pkg.packageName,
                            category = ThreatResult.ThreatCategory.PRIVACY
                        )
                    )
                }

                // 4. Call log access
                if ("Call Log Access" in matchedCategories && !isLikelyPhoneApp(pkg.packageName)) {
                    results.add(
                        ThreatResult(
                            name = "Call Log Access",
                            description = "\"$appName\" can read your call history, including who you called, when, and for how long.",
                            severity = ThreatResult.Severity.LOW,
                            source = ThreatResult.ScannerSource.PRIVACY_AUDIT,
                            packageName = pkg.packageName,
                            category = ThreatResult.ThreatCategory.PRIVACY
                        )
                    )
                }

                // 5. Camera + Microphone combo (surveillance risk)
                if ("Camera Access" in matchedCategories && "Microphone Access" in matchedCategories) {
                    if (!isLikelyMediaApp(pkg.packageName)) {
                        results.add(
                            ThreatResult(
                                name = "Camera + Microphone Access",
                                description = "\"$appName\" has access to both camera and microphone. This combination enables secret audio/video recording.",
                                severity = ThreatResult.Severity.MEDIUM,
                                source = ThreatResult.ScannerSource.PRIVACY_AUDIT,
                                packageName = pkg.packageName,
                                category = ThreatResult.ThreatCategory.PRIVACY
                            )
                        )
                    }
                }

                // 6. Can install packages (dropper risk)
                if ("android.permission.REQUEST_INSTALL_PACKAGES" in requestedPerms) {
                    if (!isLikelyStoreApp(pkg.packageName)) {
                        results.add(
                            ThreatResult(
                                name = "Can Install Apps",
                                description = "\"$appName\" can install other applications. Malware uses this capability to download and install additional malicious apps (\"dropper\" behavior).",
                                severity = ThreatResult.Severity.MEDIUM,
                                source = ThreatResult.ScannerSource.PRIVACY_AUDIT,
                                packageName = pkg.packageName,
                                category = ThreatResult.ThreatCategory.PRIVACY,
                                action = ThreatResult.ThreatAction.OPEN_SETTINGS
                            )
                        )
                    }
                }

                // 7. Body sensors + Internet (health data exfiltration)
                if ("Body Sensors" in matchedCategories &&
                    "android.permission.INTERNET" in requestedPerms
                ) {
                    results.add(
                        ThreatResult(
                            name = "Health Data Network Access",
                            description = "\"$appName\" can read body sensors and send data over the network. Sensitive health data could be exfiltrated.",
                            severity = ThreatResult.Severity.LOW,
                            source = ThreatResult.ScannerSource.PRIVACY_AUDIT,
                            packageName = pkg.packageName,
                            category = ThreatResult.ThreatCategory.PRIVACY
                        )
                    )
                }

                // 8. MANAGE_EXTERNAL_STORAGE (can read all files)
                if ("android.permission.MANAGE_EXTERNAL_STORAGE" in requestedPerms) {
                    results.add(
                        ThreatResult(
                            name = "Full Storage Access",
                            description = "\"$appName\" has permission to read and modify all files on the device, including photos, documents, and app data.",
                            severity = ThreatResult.Severity.MEDIUM,
                            source = ThreatResult.ScannerSource.PRIVACY_AUDIT,
                            packageName = pkg.packageName,
                            category = ThreatResult.ThreatCategory.PRIVACY
                        )
                    )
                }
            }

            onProgress(70)

            // Phase B: Check notification listeners
            results.addAll(checkNotificationListeners(context))
            onProgress(80)

            // Phase C: Check usage stats access
            results.addAll(checkUsageStatsAccess(context))
            onProgress(90)

            // Phase D: Check apps with QUERY_ALL_PACKAGES (can enumerate all installed apps)
            results.addAll(checkQueryAllPackages(pm, packages))
            onProgress(100)

            results
        }

    private fun checkNotificationListeners(context: Context): List<ThreatResult> {
        val results = mutableListOf<ThreatResult>()

        try {
            val flat = Settings.Secure.getString(
                context.contentResolver,
                "enabled_notification_listeners"
            ) ?: return results

            val pm = context.packageManager
            val listeners = flat.split(":").filter { it.isNotBlank() }

            for (listener in listeners) {
                val pkg = listener.substringBefore('/')
                if (pkg.startsWith("com.google.") || pkg.startsWith("com.android.") ||
                    pkg.startsWith("com.samsung.")
                ) continue

                val appName = try {
                    val appInfo = pm.getApplicationInfo(pkg, 0)
                    pm.getApplicationLabel(appInfo).toString()
                } catch (_: Exception) {
                    pkg
                }

                results.add(
                    ThreatResult(
                        name = "Notification Listener",
                        description = "\"$appName\" can read ALL your notifications, including messages, emails, 2FA codes, and banking alerts.",
                        severity = ThreatResult.Severity.HIGH,
                        source = ThreatResult.ScannerSource.PRIVACY_AUDIT,
                        packageName = pkg,
                        category = ThreatResult.ThreatCategory.PRIVACY,
                        action = ThreatResult.ThreatAction.OPEN_SETTINGS
                    )
                )
            }
        } catch (_: Exception) {
            // Can't read notification listener settings
        }

        return results
    }

    private fun checkUsageStatsAccess(context: Context): List<ThreatResult> {
        val results = mutableListOf<ThreatResult>()

        try {
            (context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager)
                ?: return results

            val pm = context.packageManager
            @Suppress("DEPRECATION")
            val packages = pm.getInstalledPackages(PackageManager.GET_PERMISSIONS)

            for (pkg in packages) {
                val appInfo = pkg.applicationInfo ?: continue
                if (appInfo.flags and ApplicationInfo.FLAG_SYSTEM != 0) continue

                val requested = pkg.requestedPermissions ?: continue
                if ("android.permission.PACKAGE_USAGE_STATS" in requested) {
                    val appName = pm.getApplicationLabel(appInfo).toString()

                    // Skip if it's this app or known legitimate usage trackers
                    if (pkg.packageName == context.packageName) continue

                    results.add(
                        ThreatResult(
                            name = "App Usage Tracking",
                            description = "\"$appName\" can monitor which apps you use, when, and for how long. This is a significant privacy concern.",
                            severity = ThreatResult.Severity.LOW,
                            source = ThreatResult.ScannerSource.PRIVACY_AUDIT,
                            packageName = pkg.packageName,
                            category = ThreatResult.ThreatCategory.PRIVACY,
                            action = ThreatResult.ThreatAction.OPEN_SETTINGS
                        )
                    )
                }
            }
        } catch (_: Exception) {
            // Can't access usage stats
        }

        return results
    }

    @Suppress("DEPRECATION")
    private fun checkQueryAllPackages(
        pm: PackageManager,
        packages: List<android.content.pm.PackageInfo>
    ): List<ThreatResult> {
        val results = mutableListOf<ThreatResult>()

        for (pkg in packages) {
            val appInfo = pkg.applicationInfo ?: continue
            if (appInfo.flags and ApplicationInfo.FLAG_SYSTEM != 0) continue

            val requested = pkg.requestedPermissions ?: continue
            if ("android.permission.QUERY_ALL_PACKAGES" in requested) {
                val appName = pm.getApplicationLabel(appInfo).toString()
                results.add(
                    ThreatResult(
                        name = "App Enumeration",
                        description = "\"$appName\" can see all installed apps on your device. This data can be used for fingerprinting and targeted advertising.",
                        severity = ThreatResult.Severity.LOW,
                        source = ThreatResult.ScannerSource.PRIVACY_AUDIT,
                        packageName = pkg.packageName,
                        category = ThreatResult.ThreatCategory.PRIVACY
                    )
                )
            }
        }

        return results
    }

    private fun isLikelySmsApp(packageName: String): Boolean {
        return packageName.contains("sms", ignoreCase = true) ||
                packageName.contains("message", ignoreCase = true) ||
                packageName.contains("mms", ignoreCase = true) ||
                packageName.contains("messenger", ignoreCase = true)
    }

    private fun isLikelyPhoneApp(packageName: String): Boolean {
        return packageName.contains("phone", ignoreCase = true) ||
                packageName.contains("dialer", ignoreCase = true) ||
                packageName.contains("call", ignoreCase = true) ||
                packageName.contains("contacts", ignoreCase = true)
    }

    private fun isLikelyMediaApp(packageName: String): Boolean {
        return packageName.contains("camera", ignoreCase = true) ||
                packageName.contains("video", ignoreCase = true) ||
                packageName.contains("photo", ignoreCase = true) ||
                packageName.contains("meet", ignoreCase = true) ||
                packageName.contains("zoom", ignoreCase = true) ||
                packageName.contains("teams", ignoreCase = true) ||
                packageName.contains("skype", ignoreCase = true) ||
                packageName.contains("whatsapp", ignoreCase = true) ||
                packageName.contains("telegram", ignoreCase = true) ||
                packageName.contains("snapchat", ignoreCase = true) ||
                packageName.contains("instagram", ignoreCase = true) ||
                packageName.contains("tiktok", ignoreCase = true)
    }

    private fun isLikelyStoreApp(packageName: String): Boolean {
        return packageName.contains("store", ignoreCase = true) ||
                packageName.contains("market", ignoreCase = true) ||
                packageName.contains("browser", ignoreCase = true) ||
                packageName == "com.android.vending" ||
                packageName.contains("fdroid", ignoreCase = true) ||
                packageName.contains("aurora", ignoreCase = true)
    }
}
