package com.filecleaner.app.utils.antivirus

/**
 * Unified result from any scanner in the hybrid antivirus system.
 */
data class ThreatResult(
    val name: String,
    val description: String,
    val severity: Severity,
    val source: ScannerSource,
    val filePath: String? = null,
    val packageName: String? = null,
    val action: ThreatAction = ThreatAction.NONE,
    val category: ThreatCategory = ThreatCategory.GENERAL,
    val timestamp: Long = System.currentTimeMillis()
) {
    enum class Severity {
        INFO,
        LOW,
        MEDIUM,
        HIGH,
        CRITICAL
    }

    enum class ScannerSource {
        APP_INTEGRITY,
        FILE_SIGNATURE,
        PRIVACY_AUDIT,
        NETWORK_SECURITY,
        APP_VERIFICATION
    }

    enum class ThreatAction {
        NONE,
        QUARANTINE,
        DELETE,
        UNINSTALL,
        OPEN_SETTINGS,
        REVOKE_ADMIN
    }

    enum class ThreatCategory {
        GENERAL,
        MALWARE,
        ROOT_TAMPERING,
        PRIVACY,
        NETWORK,
        SIDELOAD,
        ACCESSIBILITY_ABUSE,
        DEVICE_ADMIN,
        SUSPICIOUS_FILE,
        DEBUG_RISK
    }
}
