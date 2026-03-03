package com.filecleaner.app.utils.antivirus

import android.accessibilityservice.AccessibilityServiceInfo
import android.app.admin.DevicePolicyManager
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Debug
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader

/**
 * App integrity scanner.
 * Comprehensive device security checks including:
 * - Root detection (su binary, Magisk, root management apps, mount points)
 * - Hooking framework detection (Xposed, Frida, Substrate, EdXposed, LSPosed)
 * - Debugger attachment
 * - Emulator detection
 * - Developer options / USB debugging
 * - Known malicious packages
 * - Suspicious debuggable apps
 * - Overlay attack detection (apps with SYSTEM_ALERT_WINDOW)
 * - Accessibility service abuse detection
 * - Device admin abuse detection
 */
object AppIntegrityScanner {

    private val ROOT_BINARIES = listOf(
        "/system/bin/su", "/system/xbin/su", "/sbin/su",
        "/system/su", "/system/bin/.ext/.su",
        "/system/usr/we-need-root/su-backup",
        "/data/local/su", "/data/local/bin/su", "/data/local/xbin/su",
        "/system/app/Superuser.apk",
        "/data/local/tmp/su",
        "/system/bin/failsafe/su"
    )

    private val ROOT_PACKAGES = listOf(
        "com.noshufou.android.su",
        "com.noshufou.android.su.elite",
        "eu.chainfire.supersu",
        "com.koushikdutta.superuser",
        "com.thirdparty.superuser",
        "com.yellowes.su",
        "com.topjohnwu.magisk",
        "me.phh.superuser",
        "com.kingouser.com",
        "com.kingo.root",
        "com.smedialink.onecleanpro",
        "com.zhiqupk.root.global"
    )

    private val KNOWN_MALICIOUS_PACKAGES = listOf(
        "com.android.fakeapp",
        "com.android.fakeid",
        "com.svpnfree.proxy",
        "com.apphider.applock",
        "com.system.battery.optimizer.fake",
        "com.android.provision.confirm",
        "com.android.smspush",
        "com.android.system.manager",
        "com.androhelm.antivirus.tablet.free",
        "com.chinese.flashlight",
        "com.freeflashlightpro",
        "com.ironSource.aura.tmo"
    )

    private val EMULATOR_INDICATORS = listOf(
        "goldfish", "ranchu", "sdk_gphone",
        "google_sdk", "Emulator", "Android SDK",
        "vbox86", "nox", "bluestacks", "genymotion",
        "ttVM_Hdragon", "andy", "Droid4X"
    )

    /** Known hooking framework packages */
    private val HOOK_FRAMEWORK_PACKAGES = listOf(
        "de.robv.android.xposed.installer",     // Xposed Framework
        "org.meowcat.edxposed.manager",          // EdXposed
        "org.lsposed.manager",                    // LSPosed
        "com.saurik.substrate",                   // Cydia Substrate
        "com.topjohnwu.magisk",                   // Magisk (also root)
        "io.va.exposed",                          // VirtualXposed
        "com.taichi.app",                         // TaiChi (Xposed on non-root)
        "me.weishu.exp"                           // Riru/EdXposed manager
    )

    /** Frida detection: files and process names */
    private val FRIDA_INDICATORS = listOf(
        "/data/local/tmp/frida-server",
        "/data/local/tmp/re.frida.server",
        "/system/bin/frida-server",
        "/system/xbin/frida-server"
    )

    /** Known device admin abuse packages (fake security apps) */
    private val SUSPICIOUS_DEVICE_ADMIN_PACKAGES = setOf(
        "com.android.locker",
        "com.fakeav.protection",
        "com.security.fake.shield"
    )

    suspend fun scan(context: Context, onProgress: (Int) -> Unit): List<ThreatResult> =
        withContext(Dispatchers.IO) {
            val results = mutableListOf<ThreatResult>()
            onProgress(0)

            // 1. Root detection
            results.addAll(checkRoot(context))
            onProgress(10)

            // 2. Hooking framework detection
            results.addAll(checkHookingFrameworks(context))
            onProgress(20)

            // 3. Frida detection
            results.addAll(checkFrida())
            onProgress(30)

            // 4. Debugger detection
            results.addAll(checkDebugger())
            onProgress(40)

            // 5. Emulator detection
            results.addAll(checkEmulator())
            onProgress(50)

            // 6. Developer options & USB debugging
            results.addAll(checkDeveloperSettings(context))
            onProgress(60)

            // 7. Known malicious packages
            results.addAll(checkMaliciousPackages(context))
            onProgress(70)

            // 8. Suspicious apps from unknown sources
            results.addAll(checkSuspiciousApps(context))
            onProgress(80)

            // 9. Overlay attack risk (SYSTEM_ALERT_WINDOW)
            results.addAll(checkOverlayApps(context))
            onProgress(90)

            // 10. Accessibility service abuse
            results.addAll(checkAccessibilityAbuse(context))
            onProgress(95)

            // 11. Device admin abuse
            results.addAll(checkDeviceAdminAbuse(context))
            onProgress(100)

            results
        }

    private fun checkRoot(context: Context): List<ThreatResult> {
        val results = mutableListOf<ThreatResult>()

        // Check for su binaries
        for (path in ROOT_BINARIES) {
            if (File(path).exists()) {
                results.add(
                    ThreatResult(
                        name = "Root Binary Found",
                        description = "Su binary detected at $path. Device may be rooted.",
                        severity = ThreatResult.Severity.HIGH,
                        source = ThreatResult.ScannerSource.APP_INTEGRITY,
                        filePath = path,
                        category = ThreatResult.ThreatCategory.ROOT_TAMPERING
                    )
                )
            }
        }

        // Check for root management apps
        val pm = context.packageManager
        for (pkg in ROOT_PACKAGES) {
            try {
                pm.getPackageInfo(pkg, 0)
                results.add(
                    ThreatResult(
                        name = "Root Management App",
                        description = "Root management app installed: $pkg",
                        severity = ThreatResult.Severity.MEDIUM,
                        source = ThreatResult.ScannerSource.APP_INTEGRITY,
                        packageName = pkg,
                        category = ThreatResult.ThreatCategory.ROOT_TAMPERING
                    )
                )
            } catch (_: PackageManager.NameNotFoundException) {
                // Not installed — safe
            }
        }

        // Check build tags
        if (Build.TAGS?.contains("test-keys") == true) {
            results.add(
                ThreatResult(
                    name = "Test Keys Detected",
                    description = "Device build uses test signing keys, indicating custom ROM or root.",
                    severity = ThreatResult.Severity.MEDIUM,
                    source = ThreatResult.ScannerSource.APP_INTEGRITY,
                    category = ThreatResult.ThreatCategory.ROOT_TAMPERING
                )
            )
        }

        // Check for rw mount on /system
        try {
            val process = Runtime.getRuntime().exec(arrayOf("mount"))
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            reader.useLines { lines ->
                lines.forEach { line ->
                    if (line.contains("/system") && line.contains("rw")) {
                        results.add(
                            ThreatResult(
                                name = "System Partition Writable",
                                description = "The /system partition is mounted as read-write. This is a strong indicator of root access.",
                                severity = ThreatResult.Severity.HIGH,
                                source = ThreatResult.ScannerSource.APP_INTEGRITY,
                                category = ThreatResult.ThreatCategory.ROOT_TAMPERING
                            )
                        )
                    }
                }
            }
            process.waitFor()
        } catch (_: Exception) {
            // Can't execute mount
        }

        // Check for Magisk hide props
        try {
            val process = Runtime.getRuntime().exec(arrayOf("getprop", "ro.boot.vbmeta.device_state"))
            val output = process.inputStream.bufferedReader().readText().trim()
            process.waitFor()
            if (output == "unlocked") {
                results.add(
                    ThreatResult(
                        name = "Bootloader Unlocked",
                        description = "Device bootloader is unlocked. This allows flashing custom firmware and enables root access.",
                        severity = ThreatResult.Severity.MEDIUM,
                        source = ThreatResult.ScannerSource.APP_INTEGRITY,
                        category = ThreatResult.ThreatCategory.ROOT_TAMPERING
                    )
                )
            }
        } catch (_: Exception) {
            // Can't read property
        }

        return results
    }

    private fun checkHookingFrameworks(context: Context): List<ThreatResult> {
        val results = mutableListOf<ThreatResult>()
        val pm = context.packageManager

        for (pkg in HOOK_FRAMEWORK_PACKAGES) {
            try {
                val info = pm.getApplicationInfo(pkg, 0)
                val appName = pm.getApplicationLabel(info).toString()
                results.add(
                    ThreatResult(
                        name = "Hooking Framework Detected",
                        description = "\"$appName\" ($pkg) is a code-injection framework that can modify any app's behavior at runtime. Malware can use this to steal data or bypass security.",
                        severity = ThreatResult.Severity.CRITICAL,
                        source = ThreatResult.ScannerSource.APP_INTEGRITY,
                        packageName = pkg,
                        category = ThreatResult.ThreatCategory.ROOT_TAMPERING,
                        action = ThreatResult.ThreatAction.UNINSTALL
                    )
                )
            } catch (_: PackageManager.NameNotFoundException) {
                // Not installed
            }
        }

        // Check for Xposed in loaded native libraries via /proc/self/maps
        try {
            val maps = File("/proc/self/maps").readText()
            if (maps.contains("XposedBridge", ignoreCase = true) ||
                maps.contains("libxposed", ignoreCase = true)
            ) {
                results.add(
                    ThreatResult(
                        name = "Xposed Hooks Active",
                        description = "Xposed framework modules are actively loaded in memory. App behavior may be modified by third-party hooks.",
                        severity = ThreatResult.Severity.CRITICAL,
                        source = ThreatResult.ScannerSource.APP_INTEGRITY,
                        category = ThreatResult.ThreatCategory.ROOT_TAMPERING
                    )
                )
            }
        } catch (_: Exception) {
            // Can't read process maps
        }

        return results
    }

    private fun checkFrida(): List<ThreatResult> {
        val results = mutableListOf<ThreatResult>()

        // Check for frida-server binary
        for (path in FRIDA_INDICATORS) {
            if (File(path).exists()) {
                results.add(
                    ThreatResult(
                        name = "Frida Server Detected",
                        description = "Frida instrumentation server found at $path. Frida allows dynamic code injection and is commonly used for reverse engineering and hacking.",
                        severity = ThreatResult.Severity.CRITICAL,
                        source = ThreatResult.ScannerSource.APP_INTEGRITY,
                        filePath = path,
                        category = ThreatResult.ThreatCategory.ROOT_TAMPERING,
                        action = ThreatResult.ThreatAction.DELETE
                    )
                )
            }
        }

        // Check for frida in loaded libs
        try {
            val maps = File("/proc/self/maps").readText()
            if (maps.contains("frida", ignoreCase = true) ||
                maps.contains("gadget", ignoreCase = true)
            ) {
                results.add(
                    ThreatResult(
                        name = "Frida Agent Active",
                        description = "Frida instrumentation agent is actively loaded in process memory. App behavior is being dynamically modified.",
                        severity = ThreatResult.Severity.CRITICAL,
                        source = ThreatResult.ScannerSource.APP_INTEGRITY,
                        category = ThreatResult.ThreatCategory.ROOT_TAMPERING
                    )
                )
            }
        } catch (_: Exception) {
            // Can't read maps
        }

        return results
    }

    private fun checkDebugger(): List<ThreatResult> {
        val results = mutableListOf<ThreatResult>()

        if (Debug.isDebuggerConnected()) {
            results.add(
                ThreatResult(
                    name = "Debugger Attached",
                    description = "A debugger is currently attached to the application. An attacker may be inspecting app memory and data.",
                    severity = ThreatResult.Severity.HIGH,
                    source = ThreatResult.ScannerSource.APP_INTEGRITY,
                    category = ThreatResult.ThreatCategory.DEBUG_RISK
                )
            )
        }

        // Check for tracers via /proc/self/status
        try {
            val status = File("/proc/self/status").readText()
            val tracerPid = Regex("TracerPid:\\s+(\\d+)").find(status)?.groupValues?.get(1)?.toIntOrNull()
            if (tracerPid != null && tracerPid > 0) {
                results.add(
                    ThreatResult(
                        name = "Process Being Traced",
                        description = "This process is being traced by PID $tracerPid (ptrace). This indicates active debugging or instrumentation.",
                        severity = ThreatResult.Severity.HIGH,
                        source = ThreatResult.ScannerSource.APP_INTEGRITY,
                        category = ThreatResult.ThreatCategory.DEBUG_RISK
                    )
                )
            }
        } catch (_: Exception) {
            // Can't read status
        }

        return results
    }

    private fun checkEmulator(): List<ThreatResult> {
        val results = mutableListOf<ThreatResult>()

        val isEmulator = EMULATOR_INDICATORS.any { indicator ->
            Build.FINGERPRINT.contains(indicator, ignoreCase = true) ||
                    Build.MODEL.contains(indicator, ignoreCase = true) ||
                    Build.MANUFACTURER.contains(indicator, ignoreCase = true) ||
                    Build.HARDWARE.contains(indicator, ignoreCase = true) ||
                    Build.PRODUCT.contains(indicator, ignoreCase = true)
        }

        if (isEmulator) {
            results.add(
                ThreatResult(
                    name = "Emulator Detected",
                    description = "App appears to be running on an emulator (${Build.MODEL}/${Build.MANUFACTURER}). Emulators are often used for reverse engineering and automated attacks.",
                    severity = ThreatResult.Severity.INFO,
                    source = ThreatResult.ScannerSource.APP_INTEGRITY,
                    category = ThreatResult.ThreatCategory.DEBUG_RISK
                )
            )
        }

        // Additional emulator checks
        try {
            val props = mapOf(
                "ro.hardware.chipname" to "generic",
                "ro.kernel.qemu" to "1",
                "ro.product.device" to "generic"
            )
            for ((prop, indicator) in props) {
                val process = Runtime.getRuntime().exec(arrayOf("getprop", prop))
                val value = process.inputStream.bufferedReader().readText().trim()
                process.waitFor()
                if (value.contains(indicator, ignoreCase = true) && !isEmulator) {
                    results.add(
                        ThreatResult(
                            name = "Emulator Detected",
                            description = "System property $prop indicates an emulator ($value).",
                            severity = ThreatResult.Severity.INFO,
                            source = ThreatResult.ScannerSource.APP_INTEGRITY,
                            category = ThreatResult.ThreatCategory.DEBUG_RISK
                        )
                    )
                    break
                }
            }
        } catch (_: Exception) {
            // Can't read properties
        }

        return results
    }

    private fun checkDeveloperSettings(context: Context): List<ThreatResult> {
        val results = mutableListOf<ThreatResult>()

        // Check developer options enabled
        try {
            val devEnabled = Settings.Secure.getInt(
                context.contentResolver,
                Settings.Global.DEVELOPMENT_SETTINGS_ENABLED, 0
            )
            if (devEnabled == 1) {
                results.add(
                    ThreatResult(
                        name = "Developer Options Enabled",
                        description = "Developer Options are enabled on this device. This exposes debugging features that could be exploited.",
                        severity = ThreatResult.Severity.LOW,
                        source = ThreatResult.ScannerSource.APP_INTEGRITY,
                        category = ThreatResult.ThreatCategory.DEBUG_RISK,
                        action = ThreatResult.ThreatAction.OPEN_SETTINGS
                    )
                )
            }
        } catch (_: Exception) {
            // Can't read setting
        }

        // Check USB debugging
        try {
            val adbEnabled = Settings.Secure.getInt(
                context.contentResolver,
                Settings.Global.ADB_ENABLED, 0
            )
            if (adbEnabled == 1) {
                results.add(
                    ThreatResult(
                        name = "USB Debugging Enabled",
                        description = "USB Debugging (ADB) is enabled. A connected computer or attacker with physical access can fully control this device.",
                        severity = ThreatResult.Severity.MEDIUM,
                        source = ThreatResult.ScannerSource.APP_INTEGRITY,
                        category = ThreatResult.ThreatCategory.DEBUG_RISK,
                        action = ThreatResult.ThreatAction.OPEN_SETTINGS
                    )
                )
            }
        } catch (_: Exception) {
            // Can't read setting
        }

        // Check install from unknown sources
        try {
            @Suppress("DEPRECATION")
            val unknownSources = Settings.Secure.getInt(
                context.contentResolver,
                Settings.Secure.INSTALL_NON_MARKET_APPS, 0
            )
            if (unknownSources == 1) {
                results.add(
                    ThreatResult(
                        name = "Unknown Sources Enabled",
                        description = "Installation from unknown sources is enabled globally. This allows any app to be installed without Play Store verification.",
                        severity = ThreatResult.Severity.MEDIUM,
                        source = ThreatResult.ScannerSource.APP_INTEGRITY,
                        category = ThreatResult.ThreatCategory.SIDELOAD,
                        action = ThreatResult.ThreatAction.OPEN_SETTINGS
                    )
                )
            }
        } catch (_: Exception) {
            // Can't read setting
        }

        return results
    }

    private fun checkMaliciousPackages(context: Context): List<ThreatResult> {
        val results = mutableListOf<ThreatResult>()
        val pm = context.packageManager

        for (pkg in KNOWN_MALICIOUS_PACKAGES) {
            try {
                pm.getPackageInfo(pkg, 0)
                results.add(
                    ThreatResult(
                        name = "Known Malicious App",
                        description = "Known malicious package installed: $pkg. Consider uninstalling immediately.",
                        severity = ThreatResult.Severity.CRITICAL,
                        source = ThreatResult.ScannerSource.APP_INTEGRITY,
                        packageName = pkg,
                        category = ThreatResult.ThreatCategory.MALWARE,
                        action = ThreatResult.ThreatAction.UNINSTALL
                    )
                )
            } catch (_: PackageManager.NameNotFoundException) {
                // Not installed
            }
        }

        return results
    }

    @Suppress("DEPRECATION")
    private fun checkSuspiciousApps(context: Context): List<ThreatResult> {
        val results = mutableListOf<ThreatResult>()
        val pm = context.packageManager
        val packages = pm.getInstalledPackages(0)

        for (pkg in packages) {
            val appInfo = pkg.applicationInfo ?: continue
            if (appInfo.flags and ApplicationInfo.FLAG_SYSTEM == 0) {
                val installer = pm.getInstallerPackageName(pkg.packageName)
                if (installer == null && appInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0) {
                    val appName = pm.getApplicationLabel(appInfo).toString()
                    results.add(
                        ThreatResult(
                            name = "Suspicious Debuggable App",
                            description = "\"$appName\" (${pkg.packageName}) is debuggable and has no known installer. This is common in malware and pirated apps.",
                            severity = ThreatResult.Severity.LOW,
                            source = ThreatResult.ScannerSource.APP_INTEGRITY,
                            packageName = pkg.packageName,
                            category = ThreatResult.ThreatCategory.DEBUG_RISK
                        )
                    )
                }
            }
        }

        return results
    }

    @Suppress("DEPRECATION")
    private fun checkOverlayApps(context: Context): List<ThreatResult> {
        val results = mutableListOf<ThreatResult>()
        val pm = context.packageManager
        val packages = pm.getInstalledPackages(PackageManager.GET_PERMISSIONS)

        for (pkg in packages) {
            val appInfo = pkg.applicationInfo ?: continue
            if (appInfo.flags and ApplicationInfo.FLAG_SYSTEM != 0) continue

            val requested = pkg.requestedPermissions ?: continue
            if ("android.permission.SYSTEM_ALERT_WINDOW" in requested) {
                val appName = pm.getApplicationLabel(appInfo).toString()
                // Exclude well-known legitimate overlay apps
                if (pkg.packageName.startsWith("com.facebook.") ||
                    pkg.packageName.startsWith("com.google.") ||
                    pkg.packageName.contains("bubble") ||
                    pkg.packageName.contains("chat.head")
                ) continue

                results.add(
                    ThreatResult(
                        name = "Overlay Permission",
                        description = "\"$appName\" can draw over other apps (SYSTEM_ALERT_WINDOW). This can be used for tapjacking attacks — tricking you into tapping hidden buttons.",
                        severity = ThreatResult.Severity.LOW,
                        source = ThreatResult.ScannerSource.APP_INTEGRITY,
                        packageName = pkg.packageName,
                        category = ThreatResult.ThreatCategory.PRIVACY
                    )
                )
            }
        }

        return results
    }

    private fun checkAccessibilityAbuse(context: Context): List<ThreatResult> {
        val results = mutableListOf<ThreatResult>()

        try {
            val am = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager
                ?: return results

            val enabledServices = am.getEnabledAccessibilityServiceList(
                AccessibilityServiceInfo.FEEDBACK_ALL_MASK
            )

            for (service in enabledServices) {
                val serviceInfo = service.resolveInfo?.serviceInfo ?: continue
                val pkg = serviceInfo.packageName

                // Skip system and well-known accessibility apps
                if (pkg.startsWith("com.google.") || pkg.startsWith("com.android.") ||
                    pkg.startsWith("com.samsung.")
                ) continue

                val appName = service.resolveInfo?.loadLabel(context.packageManager)?.toString()
                    ?: serviceInfo.name

                val capabilities = mutableListOf<String>()
                if (service.capabilities and AccessibilityServiceInfo.CAPABILITY_CAN_RETRIEVE_WINDOW_CONTENT != 0)
                    capabilities.add("read screen content")
                if (service.capabilities and AccessibilityServiceInfo.CAPABILITY_CAN_PERFORM_GESTURES != 0)
                    capabilities.add("perform gestures")
                if (service.capabilities and AccessibilityServiceInfo.CAPABILITY_CAN_REQUEST_TOUCH_EXPLORATION != 0)
                    capabilities.add("intercept touch events")

                val severity = if (capabilities.size >= 2) ThreatResult.Severity.HIGH
                else ThreatResult.Severity.MEDIUM

                results.add(
                    ThreatResult(
                        name = "Accessibility Service Active",
                        description = "\"$appName\" ($pkg) has an active accessibility service that can ${capabilities.joinToString(", ")}. Malicious accessibility services can steal passwords, read messages, and control your device.",
                        severity = severity,
                        source = ThreatResult.ScannerSource.APP_INTEGRITY,
                        packageName = pkg,
                        category = ThreatResult.ThreatCategory.ACCESSIBILITY_ABUSE,
                        action = ThreatResult.ThreatAction.OPEN_SETTINGS
                    )
                )
            }
        } catch (_: Exception) {
            // Can't access accessibility manager
        }

        return results
    }

    private fun checkDeviceAdminAbuse(context: Context): List<ThreatResult> {
        val results = mutableListOf<ThreatResult>()

        try {
            val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as? DevicePolicyManager
                ?: return results

            val admins = dpm.activeAdmins ?: return results

            for (admin in admins) {
                val pkg = admin.packageName

                // Skip system device admin providers
                if (pkg.startsWith("com.google.") || pkg.startsWith("com.android.") ||
                    pkg.startsWith("com.samsung.")
                ) continue

                val pm = context.packageManager
                val appName = try {
                    val appInfo = pm.getApplicationInfo(pkg, 0)
                    pm.getApplicationLabel(appInfo).toString()
                } catch (_: Exception) {
                    pkg
                }

                val severity = if (pkg in SUSPICIOUS_DEVICE_ADMIN_PACKAGES) ThreatResult.Severity.CRITICAL
                else ThreatResult.Severity.MEDIUM

                results.add(
                    ThreatResult(
                        name = "Device Admin Active",
                        description = "\"$appName\" ($pkg) is a device administrator. Device admins can lock the device, wipe data, enforce policies, and prevent their own uninstallation.",
                        severity = severity,
                        source = ThreatResult.ScannerSource.APP_INTEGRITY,
                        packageName = pkg,
                        category = ThreatResult.ThreatCategory.DEVICE_ADMIN,
                        action = ThreatResult.ThreatAction.REVOKE_ADMIN
                    )
                )
            }
        } catch (_: Exception) {
            // Can't access device policy manager
        }

        return results
    }
}
