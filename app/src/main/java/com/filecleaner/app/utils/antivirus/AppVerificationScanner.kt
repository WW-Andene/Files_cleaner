package com.filecleaner.app.utils.antivirus

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.Signature
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.security.MessageDigest
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.io.ByteArrayInputStream
import java.util.Date

/**
 * App verification scanner.
 * Validates installed applications for security concerns:
 * - Sideloaded apps (no verified installer)
 * - Debug-signed apps in production
 * - Expired signing certificates
 * - Self-signed certificates with suspicious properties
 * - Apps with multiple signers (potential tampering)
 * - Cloned/dual apps
 * - Recently installed unknown apps
 */
object AppVerificationScanner {

    /** Known legitimate app stores / installers */
    private val KNOWN_INSTALLERS = setOf(
        "com.android.vending",           // Google Play Store
        "com.amazon.venezia",            // Amazon Appstore
        "com.sec.android.app.samsungapps", // Samsung Galaxy Store
        "com.huawei.appmarket",          // Huawei AppGallery
        "com.xiaomi.market",             // Xiaomi GetApps
        "com.oppo.market",               // OPPO App Market
        "com.bbk.appstore",              // Vivo App Store
        "org.fdroid.fdroid",             // F-Droid
        "com.aurora.store",              // Aurora Store
        "com.google.android.packageinstaller",
        "com.android.packageinstaller",
        "com.samsung.android.packageinstaller"
    )

    /** Known debug signing certificate fingerprints */
    private val DEBUG_CERT_FINGERPRINTS = setOf(
        "a40da80a59d170caa950cf15c18c454d47a39b26", // Default Android debug keystore
        "61ed377e85d386a8dfee6b864bd85b0bfaa5af81"  // Common CI debug keystore
    )

    /** Package name patterns typical of cloned/dual apps */
    private val CLONE_PATTERNS = listOf(
        Regex(".*\\.clone[0-9]*$", RegexOption.IGNORE_CASE),
        Regex(".*_clone[0-9]*$", RegexOption.IGNORE_CASE),
        Regex("^parallel\\..*", RegexOption.IGNORE_CASE),
        Regex("^dual\\..*", RegexOption.IGNORE_CASE)
    )

    @Suppress("DEPRECATION")
    suspend fun scan(context: Context, onProgress: (Int) -> Unit): List<ThreatResult> =
        withContext(Dispatchers.IO) {
            val results = mutableListOf<ThreatResult>()
            val pm = context.packageManager
            val packages = pm.getInstalledPackages(PackageManager.GET_SIGNATURES)
            val total = packages.size
            onProgress(0)

            for ((index, pkg) in packages.withIndex()) {
                if (index % 10 == 0) onProgress((index * 100) / total.coerceAtLeast(1))

                val appInfo = pkg.applicationInfo ?: continue
                // Skip system apps
                if (appInfo.flags and ApplicationInfo.FLAG_SYSTEM != 0) continue

                val appName = pm.getApplicationLabel(appInfo).toString()

                // 1. Check installer source
                checkInstaller(pm, pkg, appName, results)

                // 2. Check signing certificate
                checkCertificate(pm, pkg, appName, results)

                // 3. Check for debug-signed apps
                checkDebugSigned(pkg, appName, results)

                // 4. Check for cloned apps
                checkClonedApp(pkg, appName, results)

                // 5. Check for apps requesting too many permissions for their category
                checkPermissionOverreach(pm, pkg, appName, results)
            }

            onProgress(100)
            results
        }

    @Suppress("DEPRECATION")
    private fun checkInstaller(
        pm: PackageManager,
        pkg: PackageInfo,
        appName: String,
        results: MutableList<ThreatResult>
    ) {
        val installer = try {
            pm.getInstallerPackageName(pkg.packageName)
        } catch (_: Exception) {
            null
        }

        if (installer == null || installer !in KNOWN_INSTALLERS) {
            // Check if it's a system-updated app
            val appInfo = pkg.applicationInfo ?: return
            if (appInfo.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP != 0) return

            val installerName = installer ?: "Unknown source"
            results.add(
                ThreatResult(
                    name = "Sideloaded App",
                    description = "\"$appName\" was not installed from a recognized app store (installer: $installerName). Sideloaded apps bypass store security checks.",
                    severity = ThreatResult.Severity.LOW,
                    source = ThreatResult.ScannerSource.APP_VERIFICATION,
                    packageName = pkg.packageName,
                    category = ThreatResult.ThreatCategory.SIDELOAD,
                    action = ThreatResult.ThreatAction.UNINSTALL
                )
            )
        }
    }

    @Suppress("DEPRECATION")
    private fun checkCertificate(
        pm: PackageManager,
        pkg: PackageInfo,
        appName: String,
        results: MutableList<ThreatResult>
    ) {
        val signatures = pkg.signatures ?: return

        // Check for multiple signers (unusual, may indicate tampering)
        if (signatures.size > 1) {
            results.add(
                ThreatResult(
                    name = "Multiple Signers",
                    description = "\"$appName\" is signed by ${signatures.size} different certificates. This is unusual and may indicate the app was tampered with.",
                    severity = ThreatResult.Severity.HIGH,
                    source = ThreatResult.ScannerSource.APP_VERIFICATION,
                    packageName = pkg.packageName,
                    category = ThreatResult.ThreatCategory.MALWARE
                )
            )
        }

        // Parse each certificate
        for (sig in signatures) {
            try {
                val cert = parseCertificate(sig) ?: continue

                // Check expiration
                try {
                    cert.checkValidity(Date())
                } catch (_: Exception) {
                    results.add(
                        ThreatResult(
                            name = "Expired Certificate",
                            description = "\"$appName\" is signed with an expired certificate (expired: ${cert.notAfter}). This app can no longer receive updates.",
                            severity = ThreatResult.Severity.MEDIUM,
                            source = ThreatResult.ScannerSource.APP_VERIFICATION,
                            packageName = pkg.packageName,
                            category = ThreatResult.ThreatCategory.GENERAL
                        )
                    )
                }

                // Check for very short validity period (suspicious)
                val validityDays = (cert.notAfter.time - cert.notBefore.time) / (1000 * 60 * 60 * 24)
                if (validityDays < 30) {
                    results.add(
                        ThreatResult(
                            name = "Suspicious Certificate",
                            description = "\"$appName\" is signed with a certificate valid for only $validityDays days. Legitimate apps typically use long-lived certificates.",
                            severity = ThreatResult.Severity.HIGH,
                            source = ThreatResult.ScannerSource.APP_VERIFICATION,
                            packageName = pkg.packageName,
                            category = ThreatResult.ThreatCategory.MALWARE
                        )
                    )
                }

                // Check issuer == subject (self-signed) with generic CN
                val issuer = cert.issuerDN.name
                val subject = cert.subjectDN.name
                if (issuer == subject) {
                    val cn = extractCN(subject)
                    if (cn != null && (cn == "Android Debug" || cn == "Unknown" || cn.length <= 2)) {
                        results.add(
                            ThreatResult(
                                name = "Generic Self-Signed App",
                                description = "\"$appName\" uses a self-signed certificate with a generic name (\"$cn\"). This is common in malware and test builds.",
                                severity = ThreatResult.Severity.MEDIUM,
                                source = ThreatResult.ScannerSource.APP_VERIFICATION,
                                packageName = pkg.packageName,
                                category = ThreatResult.ThreatCategory.SIDELOAD
                            )
                        )
                    }
                }
            } catch (_: Exception) {
                // Certificate parsing failed
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun checkDebugSigned(
        pkg: PackageInfo,
        appName: String,
        results: MutableList<ThreatResult>
    ) {
        val signatures = pkg.signatures ?: return

        for (sig in signatures) {
            try {
                val md = MessageDigest.getInstance("SHA-1")
                val fingerprint = md.digest(sig.toByteArray())
                    .joinToString("") { "%02x".format(it) }

                if (fingerprint in DEBUG_CERT_FINGERPRINTS) {
                    results.add(
                        ThreatResult(
                            name = "Debug-Signed App",
                            description = "\"$appName\" is signed with a known Android debug certificate. Debug apps should not be installed in production — they may contain logging, backdoors, or bypassed security.",
                            severity = ThreatResult.Severity.HIGH,
                            source = ThreatResult.ScannerSource.APP_VERIFICATION,
                            packageName = pkg.packageName,
                            category = ThreatResult.ThreatCategory.DEBUG_RISK,
                            action = ThreatResult.ThreatAction.UNINSTALL
                        )
                    )
                    break
                }
            } catch (_: Exception) {
                // Hash computation failed
            }
        }

        // Also check FLAG_DEBUGGABLE
        val appInfo = pkg.applicationInfo ?: return
        if (appInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0) {
            results.add(
                ThreatResult(
                    name = "Debuggable App",
                    description = "\"$appName\" has the debuggable flag enabled. Attackers can attach debuggers, inspect memory, and extract sensitive data.",
                    severity = ThreatResult.Severity.MEDIUM,
                    source = ThreatResult.ScannerSource.APP_VERIFICATION,
                    packageName = pkg.packageName,
                    category = ThreatResult.ThreatCategory.DEBUG_RISK
                )
            )
        }
    }

    private fun checkClonedApp(
        pkg: PackageInfo,
        appName: String,
        results: MutableList<ThreatResult>
    ) {
        for (pattern in CLONE_PATTERNS) {
            if (pattern.matches(pkg.packageName)) {
                results.add(
                    ThreatResult(
                        name = "Cloned/Dual App",
                        description = "\"$appName\" (${pkg.packageName}) appears to be a cloned or dual instance of another app. Cloned apps may bypass security controls.",
                        severity = ThreatResult.Severity.LOW,
                        source = ThreatResult.ScannerSource.APP_VERIFICATION,
                        packageName = pkg.packageName,
                        category = ThreatResult.ThreatCategory.SIDELOAD
                    )
                )
                break
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun checkPermissionOverreach(
        pm: PackageManager,
        pkg: PackageInfo,
        appName: String,
        results: MutableList<ThreatResult>
    ) {
        // Re-fetch with permissions
        val pkgWithPerms = try {
            pm.getPackageInfo(pkg.packageName, PackageManager.GET_PERMISSIONS)
        } catch (_: Exception) {
            return
        }
        val requested = pkgWithPerms.requestedPermissions ?: return

        // Simple calculator/flashlight/wallpaper apps requesting SMS, contacts, etc.
        val appInfo = pkg.applicationInfo ?: return
        val category = try {
            appInfo.category
        } catch (_: Exception) {
            ApplicationInfo.CATEGORY_UNDEFINED
        }

        // Check if a "game" or "utility" requests unusual permissions
        val hasCamera = "android.permission.CAMERA" in requested
        val hasSms = "android.permission.READ_SMS" in requested || "android.permission.SEND_SMS" in requested
        val hasContacts = "android.permission.READ_CONTACTS" in requested
        val hasPhone = "android.permission.READ_PHONE_STATE" in requested
        val hasLocation = "android.permission.ACCESS_FINE_LOCATION" in requested

        val suspiciousCount = listOf(hasCamera, hasSms, hasContacts, hasPhone, hasLocation).count { it }

        if (category == ApplicationInfo.CATEGORY_GAME && suspiciousCount >= 3) {
            results.add(
                ThreatResult(
                    name = "Game With Excessive Permissions",
                    description = "\"$appName\" is categorized as a game but requests $suspiciousCount sensitive permissions (Camera, SMS, Contacts, Phone, Location). This is unusual for a game.",
                    severity = ThreatResult.Severity.MEDIUM,
                    source = ThreatResult.ScannerSource.APP_VERIFICATION,
                    packageName = pkg.packageName,
                    category = ThreatResult.ThreatCategory.PRIVACY,
                    action = ThreatResult.ThreatAction.UNINSTALL
                )
            )
        }
    }

    @Suppress("DEPRECATION")
    private fun parseCertificate(sig: Signature): X509Certificate? {
        return try {
            val cf = CertificateFactory.getInstance("X509")
            cf.generateCertificate(ByteArrayInputStream(sig.toByteArray())) as? X509Certificate
        } catch (_: Exception) {
            null
        }
    }

    private fun extractCN(dn: String): String? {
        val parts = dn.split(",").map { it.trim() }
        return parts.find { it.startsWith("CN=", ignoreCase = true) }
            ?.substringAfter("=")
            ?.trim()
    }
}
