package com.filecleaner.app.utils.antivirus

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import com.filecleaner.app.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Network security scanner.
 * Detects apps and configurations that may expose the device to network-based attacks:
 * - Apps configured for cleartext (HTTP) traffic
 * - Apps with INTERNET + sensitive permissions (data exfiltration risk)
 * - Apps bundling known network attack tools
 * - Listening ports on the device
 * - VPN/proxy apps that may intercept traffic
 */
object NetworkSecurityScanner {

    /** Dangerous permission groups that are risky when combined with INTERNET */
    private val SENSITIVE_PERMISSIONS = listOf(
        "android.permission.READ_SMS",
        "android.permission.READ_CONTACTS",
        "android.permission.READ_CALL_LOG",
        "android.permission.ACCESS_FINE_LOCATION",
        "android.permission.CAMERA",
        "android.permission.RECORD_AUDIO",
        "android.permission.READ_PHONE_STATE"
    )

    /** Known proxy/VPN package names that may intercept traffic */
    private val KNOWN_INTERCEPT_PACKAGES = setOf(
        "com.mitmproxy.android",
        "org.proxydroid",
        "com.reqable.android",
        "app.greyshirts.sslcapture",
        "com.guoshi.httpcanary",
        "com.egorovandreyrm.pcapremote",
        "jp.co.because.android.packetcapture",
        "com.emanuelef.remote_capture",
        "tech.httptoolkit.android.v1"
    )

    /** Known port scanning / network attack tool packages */
    private val NETWORK_ATTACK_TOOLS = setOf(
        "com.aadhk.wifianalyzer",
        "com.overlook.android.fing",
        "org.kali.nethunter",
        "com.yourcompany.zanti",
        "com.zimperium.zips"
    )

    /** Well-known ports that shouldn't be listening on a mobile device */
    private val SUSPICIOUS_PORTS = listOf(
        21, 22, 23, 25, 53, 80, 443, 445, 1080, 1433, 3306,
        3389, 4444, 5432, 5555, 5900, 6379, 8080, 8443, 8888, 9090
    )

    @Suppress("DEPRECATION")
    suspend fun scan(context: Context, onProgress: (Int) -> Unit): List<ThreatResult> =
        withContext(Dispatchers.IO) {
            val results = mutableListOf<ThreatResult>()
            val pm = context.packageManager
            onProgress(0)

            // 1. Check for apps with cleartext traffic enabled
            results.addAll(checkCleartextTraffic(pm, context))
            onProgress(20)

            // 2. Check for INTERNET + sensitive data exfiltration risk
            results.addAll(checkDataExfiltrationRisk(pm, context))
            onProgress(40)

            // 3. Check for traffic interception tools
            results.addAll(checkInterceptTools(pm, context))
            onProgress(60)

            // 4. Check for network attack tools
            results.addAll(checkNetworkAttackTools(pm, context))
            onProgress(75)

            // 5. Check for suspicious listening ports
            results.addAll(checkListeningPorts(context))
            onProgress(90)

            // 6. Check ADB over network (port 5555)
            results.addAll(checkAdbNetwork(context))
            onProgress(100)

            results
        }

    @Suppress("DEPRECATION")
    private fun checkCleartextTraffic(pm: PackageManager, context: Context): List<ThreatResult> {
        val results = mutableListOf<ThreatResult>()
        val packages = pm.getInstalledPackages(0)

        for (pkg in packages) {
            val appInfo = pkg.applicationInfo ?: continue
            if (appInfo.flags and ApplicationInfo.FLAG_SYSTEM != 0) continue

            // Check if app targets low API and allows cleartext
            if (appInfo.targetSdkVersion < 28) {
                // Apps targeting below API 28 default to allowing cleartext traffic
                val appName = pm.getApplicationLabel(appInfo).toString()
                results.add(
                    ThreatResult(
                        name = context.getString(R.string.threat_net_cleartext_traffic),
                        description = context.getString(R.string.threat_net_cleartext_traffic_desc, appName, appInfo.targetSdkVersion),
                        severity = ThreatResult.Severity.LOW,
                        source = ThreatResult.ScannerSource.NETWORK_SECURITY,
                        packageName = pkg.packageName,
                        category = ThreatResult.ThreatCategory.NETWORK
                    )
                )
            }

            // Check for explicit usesCleartextTraffic in the manifest via PackageManager
            // Only for API >= 28 where the flag being set indicates explicit opt-in (avoids double-report)
            try {
                if (appInfo.targetSdkVersion >= 28 && hasCleartextInManifest(pkg.packageName, context)) {
                    val appName = pm.getApplicationLabel(appInfo).toString()
                    results.add(
                        ThreatResult(
                            name = context.getString(R.string.threat_net_explicit_cleartext),
                            description = context.getString(R.string.threat_net_explicit_cleartext_desc, appName),
                            severity = ThreatResult.Severity.MEDIUM,
                            source = ThreatResult.ScannerSource.NETWORK_SECURITY,
                            packageName = pkg.packageName,
                            category = ThreatResult.ThreatCategory.NETWORK
                        )
                    )
                }
            } catch (_: Exception) {
                // Can't read APK
            }
        }

        return results
    }

    private fun hasCleartextInManifest(packageName: String, context: Context): Boolean {
        // I3-05: Use PackageManager instead of parsing binary XML as text
        return try {
            val appInfo = context.packageManager.getApplicationInfo(packageName, 0)
            // FLAG_USES_CLEARTEXT_TRAFFIC is set when android:usesCleartextTraffic="true"
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                appInfo.flags and ApplicationInfo.FLAG_USES_CLEARTEXT_TRAFFIC != 0
            } else {
                false // Pre-M defaults to allowing cleartext
            }
        } catch (_: Exception) {
            false
        }
    }

    @Suppress("DEPRECATION")
    private fun checkDataExfiltrationRisk(pm: PackageManager, context: Context): List<ThreatResult> {
        val results = mutableListOf<ThreatResult>()
        val packages = pm.getInstalledPackages(PackageManager.GET_PERMISSIONS)

        for (pkg in packages) {
            val appInfo = pkg.applicationInfo ?: continue
            if (appInfo.flags and ApplicationInfo.FLAG_SYSTEM != 0) continue
            // Don't flag this app itself
            if (pkg.packageName == context.packageName) continue

            val requested = pkg.requestedPermissions ?: continue
            val hasInternet = "android.permission.INTERNET" in requested

            if (!hasInternet) continue

            val sensitiveMatches = SENSITIVE_PERMISSIONS.filter { it in requested }
            if (sensitiveMatches.size >= 3) {
                val appName = pm.getApplicationLabel(appInfo).toString()
                val permNames = sensitiveMatches.map { it.substringAfterLast('.') }
                results.add(
                    ThreatResult(
                        name = context.getString(R.string.threat_net_data_exfiltration),
                        description = context.getString(R.string.threat_net_data_exfiltration_desc, appName, sensitiveMatches.size, permNames.joinToString(", ")),
                        severity = ThreatResult.Severity.MEDIUM,
                        source = ThreatResult.ScannerSource.NETWORK_SECURITY,
                        packageName = pkg.packageName,
                        category = ThreatResult.ThreatCategory.PRIVACY,
                        action = ThreatResult.ThreatAction.UNINSTALL
                    )
                )
            }
        }

        return results
    }

    private fun checkInterceptTools(pm: PackageManager, context: Context): List<ThreatResult> {
        val results = mutableListOf<ThreatResult>()

        for (pkg in KNOWN_INTERCEPT_PACKAGES) {
            try {
                val info = pm.getApplicationInfo(pkg, 0)
                val appName = pm.getApplicationLabel(info).toString()
                results.add(
                    ThreatResult(
                        name = context.getString(R.string.threat_net_traffic_interception),
                        description = context.getString(R.string.threat_net_traffic_interception_desc, appName, pkg),
                        severity = ThreatResult.Severity.HIGH,
                        source = ThreatResult.ScannerSource.NETWORK_SECURITY,
                        packageName = pkg,
                        category = ThreatResult.ThreatCategory.NETWORK,
                        action = ThreatResult.ThreatAction.UNINSTALL
                    )
                )
            } catch (_: PackageManager.NameNotFoundException) {
                // Not installed
            }
        }

        return results
    }

    private fun checkNetworkAttackTools(pm: PackageManager, context: Context): List<ThreatResult> {
        val results = mutableListOf<ThreatResult>()

        for (pkg in NETWORK_ATTACK_TOOLS) {
            try {
                val info = pm.getApplicationInfo(pkg, 0)
                val appName = pm.getApplicationLabel(info).toString()
                results.add(
                    ThreatResult(
                        name = context.getString(R.string.threat_net_network_attack),
                        description = context.getString(R.string.threat_net_network_attack_desc, appName, pkg),
                        severity = ThreatResult.Severity.MEDIUM,
                        source = ThreatResult.ScannerSource.NETWORK_SECURITY,
                        packageName = pkg,
                        category = ThreatResult.ThreatCategory.NETWORK
                    )
                )
            } catch (_: PackageManager.NameNotFoundException) {
                // Not installed
            }
        }

        return results
    }

    private fun checkListeningPorts(context: Context): List<ThreatResult> {
        val results = mutableListOf<ThreatResult>()

        // Read /proc/net/tcp and /proc/net/tcp6 to find listening sockets
        val listeningPorts = mutableSetOf<Int>()
        for (procFile in listOf("/proc/net/tcp", "/proc/net/tcp6")) {
            try {
                File(procFile).readLines().drop(1).forEach { line ->
                    val fields = line.trim().split("\\s+".toRegex())
                    if (fields.size >= 4 && fields[3] == "0A") { // 0A = LISTEN state
                        val portHex = fields[1].substringAfter(':')
                        val port = portHex.toIntOrNull(16) ?: return@forEach
                        listeningPorts.add(port)
                    }
                }
            } catch (_: Exception) {
                // May not have permission
            }
        }

        val suspiciousListening = listeningPorts.intersect(SUSPICIOUS_PORTS.toSet())
        for (port in suspiciousListening) {
            val service = portToService(port)
            results.add(
                ThreatResult(
                    name = context.getString(R.string.threat_net_suspicious_port),
                    description = context.getString(R.string.threat_net_suspicious_port_desc, port, service),
                    severity = if (port in listOf(4444, 5555, 1080)) ThreatResult.Severity.HIGH
                    else ThreatResult.Severity.MEDIUM,
                    source = ThreatResult.ScannerSource.NETWORK_SECURITY,
                    category = ThreatResult.ThreatCategory.NETWORK
                )
            )
        }

        return results
    }

    private fun checkAdbNetwork(context: Context): List<ThreatResult> {
        val results = mutableListOf<ThreatResult>()

        // Check if ADB over TCP is active (port 5555)
        try {
            val prop = ProcessBuilder("getprop", "service.adb.tcp.port").redirectErrorStream(true).start()
            val output = prop.inputStream.bufferedReader().readText().trim()
            if (!prop.waitFor(5, java.util.concurrent.TimeUnit.SECONDS)) prop.destroyForcibly()
            if (output.isNotEmpty() && output != "-1" && output != "0") {
                results.add(
                    ThreatResult(
                        name = context.getString(R.string.threat_net_adb_network),
                        description = context.getString(R.string.threat_net_adb_network_desc, output),
                        severity = ThreatResult.Severity.CRITICAL,
                        source = ThreatResult.ScannerSource.NETWORK_SECURITY,
                        category = ThreatResult.ThreatCategory.DEBUG_RISK,
                        action = ThreatResult.ThreatAction.OPEN_SETTINGS
                    )
                )
            }
        } catch (_: Exception) {
            // Can't read property
        }

        return results
    }

    private fun portToService(port: Int): String = when (port) {
        21 -> "FTP"
        22 -> "SSH"
        23 -> "Telnet"
        25 -> "SMTP"
        53 -> "DNS"
        80 -> "HTTP"
        443 -> "HTTPS"
        445 -> "SMB"
        1080 -> "SOCKS Proxy"
        1433 -> "MSSQL"
        3306 -> "MySQL"
        3389 -> "RDP"
        4444 -> "Metasploit"
        5432 -> "PostgreSQL"
        5555 -> "ADB"
        5900 -> "VNC"
        6379 -> "Redis"
        8080 -> "HTTP Proxy"
        8443 -> "HTTPS Alt"
        8888 -> "HTTP Alt"
        9090 -> "Management"
        else -> "Unknown"
    }
}
