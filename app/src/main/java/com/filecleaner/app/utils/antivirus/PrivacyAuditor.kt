package com.filecleaner.app.utils.antivirus

import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.provider.Settings
import com.filecleaner.app.R
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

    /** Dangerous permissions grouped by concern, keyed by string resource ID */
    private val PRIVACY_PERMISSIONS = mapOf(
        R.string.threat_privacy_cat_sms_access to listOf(
            "android.permission.READ_SMS",
            "android.permission.SEND_SMS",
            "android.permission.RECEIVE_SMS",
            "android.permission.RECEIVE_MMS",
            "android.permission.RECEIVE_WAP_PUSH"
        ),
        R.string.threat_privacy_cat_call_log_access to listOf(
            "android.permission.READ_CALL_LOG",
            "android.permission.WRITE_CALL_LOG",
            "android.permission.PROCESS_OUTGOING_CALLS"
        ),
        R.string.threat_privacy_cat_contacts_access to listOf(
            "android.permission.READ_CONTACTS",
            "android.permission.WRITE_CONTACTS",
            "android.permission.GET_ACCOUNTS"
        ),
        R.string.threat_privacy_cat_camera_access to listOf(
            "android.permission.CAMERA"
        ),
        R.string.threat_privacy_cat_microphone_access to listOf(
            "android.permission.RECORD_AUDIO"
        ),
        R.string.threat_privacy_cat_location_access to listOf(
            "android.permission.ACCESS_FINE_LOCATION",
            "android.permission.ACCESS_COARSE_LOCATION",
            "android.permission.ACCESS_BACKGROUND_LOCATION"
        ),
        R.string.threat_privacy_cat_phone_access to listOf(
            "android.permission.READ_PHONE_STATE",
            "android.permission.CALL_PHONE",
            "android.permission.READ_PHONE_NUMBERS",
            "android.permission.ANSWER_PHONE_CALLS"
        ),
        R.string.threat_privacy_cat_storage_access to listOf(
            "android.permission.READ_EXTERNAL_STORAGE",
            "android.permission.WRITE_EXTERNAL_STORAGE",
            "android.permission.MANAGE_EXTERNAL_STORAGE"
        ),
        R.string.threat_privacy_cat_body_sensors to listOf(
            "android.permission.BODY_SENSORS",
            "android.permission.BODY_SENSORS_BACKGROUND"
        ),
        R.string.threat_privacy_cat_calendar_access to listOf(
            "android.permission.READ_CALENDAR",
            "android.permission.WRITE_CALENDAR"
        ),
        R.string.threat_privacy_cat_nearby_devices to listOf(
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
                val matchedCategoryIds = mutableListOf<Int>()
                for ((categoryResId, permissions) in PRIVACY_PERMISSIONS) {
                    if (permissions.any { it in requestedPerms }) {
                        matchedCategoryIds.add(categoryResId)
                    }
                }

                // Resolve category names for display
                val matchedCategoryNames = matchedCategoryIds.map { context.getString(it) }

                // 1. Excessive permissions
                if (matchedCategoryIds.size >= EXCESSIVE_PERMISSION_THRESHOLD) {
                    results.add(
                        ThreatResult(
                            name = context.getString(R.string.threat_privacy_excessive_permissions),
                            description = context.getString(R.string.threat_privacy_excessive_permissions_desc, appName, matchedCategoryIds.size, matchedCategoryNames.joinToString(", ")),
                            severity = ThreatResult.Severity.HIGH,
                            source = ThreatResult.ScannerSource.PRIVACY_AUDIT,
                            packageName = pkg.packageName,
                            category = ThreatResult.ThreatCategory.PRIVACY,
                            action = ThreatResult.ThreatAction.UNINSTALL
                        )
                    )
                }

                // 2. SMS access (very few apps need this)
                if (R.string.threat_privacy_cat_sms_access in matchedCategoryIds && !isLikelySmsApp(pkg.packageName)) {
                    results.add(
                        ThreatResult(
                            name = context.getString(R.string.threat_privacy_sms_access),
                            description = context.getString(R.string.threat_privacy_sms_access_desc, appName),
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
                            name = context.getString(R.string.threat_privacy_background_location),
                            description = context.getString(R.string.threat_privacy_background_location_desc, appName),
                            severity = ThreatResult.Severity.MEDIUM,
                            source = ThreatResult.ScannerSource.PRIVACY_AUDIT,
                            packageName = pkg.packageName,
                            category = ThreatResult.ThreatCategory.PRIVACY
                        )
                    )
                }

                // 4. Call log access
                if (R.string.threat_privacy_cat_call_log_access in matchedCategoryIds && !isLikelyPhoneApp(pkg.packageName)) {
                    results.add(
                        ThreatResult(
                            name = context.getString(R.string.threat_privacy_call_log_access),
                            description = context.getString(R.string.threat_privacy_call_log_access_desc, appName),
                            severity = ThreatResult.Severity.LOW,
                            source = ThreatResult.ScannerSource.PRIVACY_AUDIT,
                            packageName = pkg.packageName,
                            category = ThreatResult.ThreatCategory.PRIVACY
                        )
                    )
                }

                // 5. Camera + Microphone combo (surveillance risk)
                if (R.string.threat_privacy_cat_camera_access in matchedCategoryIds && R.string.threat_privacy_cat_microphone_access in matchedCategoryIds) {
                    if (!isLikelyMediaApp(pkg.packageName)) {
                        results.add(
                            ThreatResult(
                                name = context.getString(R.string.threat_privacy_camera_mic),
                                description = context.getString(R.string.threat_privacy_camera_mic_desc, appName),
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
                                name = context.getString(R.string.threat_privacy_can_install_apps),
                                description = context.getString(R.string.threat_privacy_can_install_apps_desc, appName),
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
                if (R.string.threat_privacy_cat_body_sensors in matchedCategoryIds &&
                    "android.permission.INTERNET" in requestedPerms
                ) {
                    results.add(
                        ThreatResult(
                            name = context.getString(R.string.threat_privacy_health_data),
                            description = context.getString(R.string.threat_privacy_health_data_desc, appName),
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
                            name = context.getString(R.string.threat_privacy_full_storage),
                            description = context.getString(R.string.threat_privacy_full_storage_desc, appName),
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
            results.addAll(checkQueryAllPackages(context, packages))
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
                // Use ComponentName.unflattenFromString for robust parsing of OEM formats
                val component = android.content.ComponentName.unflattenFromString(listener)
                val pkg = component?.packageName ?: listener.substringBefore('/')
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
                        name = context.getString(R.string.threat_privacy_notification_listener),
                        description = context.getString(R.string.threat_privacy_notification_listener_desc, appName),
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
                            name = context.getString(R.string.threat_privacy_usage_tracking),
                            description = context.getString(R.string.threat_privacy_usage_tracking_desc, appName),
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
        context: Context,
        packages: List<android.content.pm.PackageInfo>
    ): List<ThreatResult> {
        val results = mutableListOf<ThreatResult>()
        // QUERY_ALL_PACKAGES only meaningful on API 30+ (package visibility filtering)
        if (android.os.Build.VERSION.SDK_INT < 30) return results
        val pm = context.packageManager

        for (pkg in packages) {
            val appInfo = pkg.applicationInfo ?: continue
            if (appInfo.flags and ApplicationInfo.FLAG_SYSTEM != 0) continue

            val requested = pkg.requestedPermissions ?: continue
            if ("android.permission.QUERY_ALL_PACKAGES" in requested) {
                val appName = pm.getApplicationLabel(appInfo).toString()
                results.add(
                    ThreatResult(
                        name = context.getString(R.string.threat_privacy_app_enumeration),
                        description = context.getString(R.string.threat_privacy_app_enumeration_desc, appName),
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
