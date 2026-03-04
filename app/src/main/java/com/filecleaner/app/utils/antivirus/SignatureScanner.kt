package com.filecleaner.app.utils.antivirus

import com.filecleaner.app.data.FileItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.RandomAccessFile
import java.security.MessageDigest

/**
 * Enhanced file signature scanner.
 * Scans files against multiple threat vectors:
 * - File hash matching (MD5 + SHA-256 dual-hash)
 * - Filename pattern matching for known malware names
 * - Suspicious file characteristics (APKs in unusual locations)
 * - ELF binary detection (Linux executables on Android storage)
 * - Suspicious script detection (shell/python/JS scripts with dangerous patterns)
 * - Hidden file detection (dotfiles in non-standard locations)
 * - Large APK detection (unusually large APKs that may be packed malware)
 * - Archive bombs (suspiciously small archives)
 * - DEX file detection outside app directories
 */
object SignatureScanner {

    /** Known malware file hashes (MD5). In production, load from updatable DB. */
    private val KNOWN_MALWARE_MD5 = setOf(
        "d41d8cd98f00b204e9800998ecf8427e", // Empty file (placeholder)
        "44d88612fea8a8f36de82e1278abb02f", // EICAR test file
        "e1105070ba828007508566e28a2b8d4c", // Known Android malware sample
        "3395856ce81f2b7382dee72602f798b6"  // Suspicious payload
    )

    /** Known malware SHA-256 hashes */
    private val KNOWN_MALWARE_SHA256 = setOf(
        "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855", // Empty file
        "275a021bbfb6489e54d471899f7db9d1663fc695ec2fe2a2c4538aabf651fd0f"  // EICAR SHA-256
    )

    /** Suspicious filename patterns (regex) */
    private val SUSPICIOUS_PATTERNS = listOf(
        Regex(".*\\.apk\\..*", RegexOption.IGNORE_CASE),      // Double extension APK
        Regex(".*payload.*\\.apk", RegexOption.IGNORE_CASE),   // Payload APKs
        Regex(".*keylog.*", RegexOption.IGNORE_CASE),           // Keyloggers
        Regex(".*trojan.*", RegexOption.IGNORE_CASE),           // Trojan indicators
        Regex(".*rat_.*", RegexOption.IGNORE_CASE),             // RAT (Remote Access Trojan)
        Regex(".*\\.exe", RegexOption.IGNORE_CASE),             // Windows executables
        Regex(".*\\.bat", RegexOption.IGNORE_CASE),             // Batch files
        Regex(".*\\.cmd", RegexOption.IGNORE_CASE),             // Command files
        Regex(".*\\.scr", RegexOption.IGNORE_CASE),             // Screen saver
        Regex(".*\\.pif", RegexOption.IGNORE_CASE),             // Program Information File
        Regex(".*\\.com", RegexOption.IGNORE_CASE),             // COM executable
        Regex(".*\\.vbs", RegexOption.IGNORE_CASE),             // VBScript
        Regex(".*\\.wsf", RegexOption.IGNORE_CASE),             // Windows Script File
        Regex(".*\\.dll", RegexOption.IGNORE_CASE),             // Dynamic Link Library
        Regex(".*backdoor.*", RegexOption.IGNORE_CASE),         // Backdoor
        Regex(".*exploit.*", RegexOption.IGNORE_CASE),          // Exploits
        Regex(".*rootkit.*", RegexOption.IGNORE_CASE),          // Rootkits
        Regex(".*spyware.*", RegexOption.IGNORE_CASE),          // Spyware
        Regex(".*meterpreter.*", RegexOption.IGNORE_CASE),      // Metasploit payload
        Regex(".*reverse.*shell.*", RegexOption.IGNORE_CASE),   // Reverse shells
        Regex(".*\\.(hta|ps1|psm1)", RegexOption.IGNORE_CASE),  // PowerShell/HTA
    )

    /** Suspicious APK locations (outside standard install paths) */
    private val SUSPICIOUS_APK_DIRS = listOf(
        "/Download/", "/WhatsApp/", "/Telegram/",
        "/DCIM/", "/Pictures/", "/Music/",
        "/Documents/", "/Bluetooth/", "/.Trash/",
        "/Android/data/", "/tmp/"
    )

    /** Suspicious script patterns (content-level) */
    private val DANGEROUS_SCRIPT_PATTERNS = listOf(
        Regex("(curl|wget).*\\|.*sh", RegexOption.IGNORE_CASE),           // Pipe to shell
        Regex("(rm\\s+-rf|rmdir)\\s+/", RegexOption.IGNORE_CASE),        // Recursive delete from root
        Regex("chmod\\s+777", RegexOption.IGNORE_CASE),                    // World writable
        Regex("nc\\s+-[elp]", RegexOption.IGNORE_CASE),                   // Netcat listener
        Regex("base64\\s+-d.*\\|.*sh", RegexOption.IGNORE_CASE),          // Base64 decoded to shell
        Regex("eval.*base64", RegexOption.IGNORE_CASE),                    // Eval base64
        Regex("\\bam\\s+start\\b.*\\bcom\\.\\b", RegexOption.IGNORE_CASE), // Activity Manager launch
        Regex("\\bpm\\s+install\\b", RegexOption.IGNORE_CASE),            // Package Manager install
        Regex("\\bsu\\s+-c\\b", RegexOption.IGNORE_CASE),                 // su command execution
        Regex("\\bdd\\s+if=.*of=/dev/", RegexOption.IGNORE_CASE),         // DD to device
    )

    /** Script file extensions to inspect content */
    private val SCRIPT_EXTENSIONS = setOf(
        "sh", "bash", "zsh", "py", "rb", "pl", "js", "php", "lua", "cgi"
    )

    /** ELF magic bytes */
    private val ELF_MAGIC = byteArrayOf(0x7F, 'E'.code.toByte(), 'L'.code.toByte(), 'F'.code.toByte())

    /** DEX magic bytes */
    private val DEX_MAGIC = "dex\n".toByteArray()

    suspend fun scan(
        files: List<FileItem>,
        onProgress: (scanned: Int, total: Int) -> Unit
    ): List<ThreatResult> = withContext(Dispatchers.IO) {
        val results = mutableListOf<ThreatResult>()
        val total = files.size

        for ((index, item) in files.withIndex()) {
            if (index % 100 == 0) onProgress(index, total)

            // 1. Check filename patterns
            checkFilenamePatterns(item, results)

            // 2. Check APKs in suspicious locations
            checkSuspiciousApkLocation(item, results)

            // 3. Hash check for files < 50MB
            checkFileHashes(item, results)

            // 4. Check for ELF binaries in storage
            checkElfBinary(item, results)

            // 5. Check for DEX files outside app directories
            checkLooseDex(item, results)

            // 6. Check suspicious scripts (content analysis for small scripts)
            checkSuspiciousScript(item, results)

            // 7. Check hidden files in non-standard locations
            checkHiddenFiles(item, results)

            // 8. Check unusually large APKs
            checkLargeApk(item, results)

            // 9. Check archive bombs (tiny archives)
            checkArchiveBomb(item, results)
        }

        onProgress(total, total)
        results
    }

    private fun checkFilenamePatterns(item: FileItem, results: MutableList<ThreatResult>) {
        for (pattern in SUSPICIOUS_PATTERNS) {
            if (pattern.matches(item.name)) {
                results.add(
                    ThreatResult(
                        name = "Suspicious Filename",
                        description = "File \"${item.name}\" matches a known malware filename pattern.",
                        severity = ThreatResult.Severity.MEDIUM,
                        source = ThreatResult.ScannerSource.FILE_SIGNATURE,
                        filePath = item.path,
                        category = ThreatResult.ThreatCategory.SUSPICIOUS_FILE,
                        action = ThreatResult.ThreatAction.QUARANTINE
                    )
                )
                break
            }
        }
    }

    private fun checkSuspiciousApkLocation(item: FileItem, results: MutableList<ThreatResult>) {
        if (item.extension != "apk") return
        for (dir in SUSPICIOUS_APK_DIRS) {
            if (item.path.contains(dir)) {
                results.add(
                    ThreatResult(
                        name = "APK in Unusual Location",
                        description = "APK file found in ${item.path.substringBeforeLast('/')}. APKs outside standard install locations may be side-loaded malware.",
                        severity = ThreatResult.Severity.LOW,
                        source = ThreatResult.ScannerSource.FILE_SIGNATURE,
                        filePath = item.path,
                        category = ThreatResult.ThreatCategory.SIDELOAD,
                        action = ThreatResult.ThreatAction.QUARANTINE
                    )
                )
                break
            }
        }
    }

    private fun checkFileHashes(item: FileItem, results: MutableList<ThreatResult>) {
        if (item.size !in 1..52_428_800) return // Skip empty and >50MB files

        try {
            val file = File(item.path)
            if (!file.canRead()) return

            val md5 = hashFile(file, "MD5")
            if (md5 in KNOWN_MALWARE_MD5) {
                results.add(
                    ThreatResult(
                        name = "Known Malware Detected",
                        description = "File \"${item.name}\" matches known malware signature (MD5: $md5).",
                        severity = ThreatResult.Severity.CRITICAL,
                        source = ThreatResult.ScannerSource.FILE_SIGNATURE,
                        filePath = item.path,
                        category = ThreatResult.ThreatCategory.MALWARE,
                        action = ThreatResult.ThreatAction.DELETE
                    )
                )
                return
            }

            // SHA-256 for higher confidence (only for files < 10MB to limit IO)
            if (item.size <= 10_485_760) {
                val sha256 = hashFile(file, "SHA-256")
                if (sha256 in KNOWN_MALWARE_SHA256) {
                    results.add(
                        ThreatResult(
                            name = "Known Malware Detected",
                            description = "File \"${item.name}\" matches known malware signature (SHA-256).",
                            severity = ThreatResult.Severity.CRITICAL,
                            source = ThreatResult.ScannerSource.FILE_SIGNATURE,
                            filePath = item.path,
                            category = ThreatResult.ThreatCategory.MALWARE,
                            action = ThreatResult.ThreatAction.DELETE
                        )
                    )
                }
            }
        } catch (_: Exception) {
            // File may be unreadable
        }
    }

    private fun checkElfBinary(item: FileItem, results: MutableList<ThreatResult>) {
        if (item.size < 4) return
        // Only check files without common extension or with suspicious extensions
        val ext = item.extension.lowercase()
        if (ext in setOf("so", "apk", "zip", "jar", "dex", "odex", "oat")) return

        try {
            val file = File(item.path)
            if (!file.canRead()) return
            RandomAccessFile(file, "r").use { raf ->
                val header = ByteArray(4)
                if (raf.read(header) == 4 && header.contentEquals(ELF_MAGIC)) {
                    // It's an ELF binary in user storage
                    if (item.path.contains("/storage/") || item.path.contains("/sdcard/") ||
                        item.path.contains("/Download/")
                    ) {
                        results.add(
                            ThreatResult(
                                name = "ELF Binary in Storage",
                                description = "Linux executable binary \"${item.name}\" found in user storage. This could be a hacking tool, exploit, or backdoor.",
                                severity = ThreatResult.Severity.HIGH,
                                source = ThreatResult.ScannerSource.FILE_SIGNATURE,
                                filePath = item.path,
                                category = ThreatResult.ThreatCategory.SUSPICIOUS_FILE,
                                action = ThreatResult.ThreatAction.DELETE
                            )
                        )
                    }
                }
            }
        } catch (_: Exception) {
            // Can't read file
        }
    }

    private fun checkLooseDex(item: FileItem, results: MutableList<ThreatResult>) {
        if (item.extension.lowercase() != "dex") return
        // DEX files should only be inside APKs or in /data/dalvik-cache
        if (!item.path.contains("/data/dalvik-cache/") && !item.path.contains("/data/app/")) {
            results.add(
                ThreatResult(
                    name = "Loose DEX File",
                    description = "Android bytecode file \"${item.name}\" found outside app directories. This may be a dynamically loaded malware payload.",
                    severity = ThreatResult.Severity.HIGH,
                    source = ThreatResult.ScannerSource.FILE_SIGNATURE,
                    filePath = item.path,
                    category = ThreatResult.ThreatCategory.MALWARE,
                    action = ThreatResult.ThreatAction.DELETE
                )
            )
        }
    }

    private fun checkSuspiciousScript(item: FileItem, results: MutableList<ThreatResult>) {
        val ext = item.extension.lowercase()
        if (ext !in SCRIPT_EXTENSIONS) return
        if (item.size > 1_048_576) return // Skip scripts > 1MB

        try {
            val file = File(item.path)
            if (!file.canRead()) return
            val content = file.readText(Charsets.UTF_8)

            for (pattern in DANGEROUS_SCRIPT_PATTERNS) {
                if (pattern.containsMatchIn(content)) {
                    results.add(
                        ThreatResult(
                            name = "Dangerous Script",
                            description = "Script \"${item.name}\" contains suspicious commands that could compromise device security (pattern: ${pattern.pattern.take(40)}).",
                            severity = ThreatResult.Severity.HIGH,
                            source = ThreatResult.ScannerSource.FILE_SIGNATURE,
                            filePath = item.path,
                            category = ThreatResult.ThreatCategory.SUSPICIOUS_FILE,
                            action = ThreatResult.ThreatAction.QUARANTINE
                        )
                    )
                    break
                }
            }
        } catch (_: Exception) {
            // Can't read script
        }
    }

    private fun checkHiddenFiles(item: FileItem, results: MutableList<ThreatResult>) {
        if (!item.name.startsWith(".")) return
        // Only flag hidden executables/APKs/scripts — not .nomedia or config files
        val ext = item.extension.lowercase()
        val dangerous = ext in setOf("apk", "sh", "dex", "so", "exe", "jar", "py", "js")
        if (!dangerous) return

        // Skip well-known hidden dirs
        if (item.path.contains("/.git/") || item.path.contains("/.gradle/") ||
            item.path.contains("/.npm/") || item.path.contains("/.config/")
        ) return

        results.add(
            ThreatResult(
                name = "Hidden Executable",
                description = "Hidden file \"${item.name}\" (.$ext) found at ${item.path.substringBeforeLast('/')}. Malware often uses hidden files to evade detection.",
                severity = ThreatResult.Severity.MEDIUM,
                source = ThreatResult.ScannerSource.FILE_SIGNATURE,
                filePath = item.path,
                category = ThreatResult.ThreatCategory.SUSPICIOUS_FILE,
                action = ThreatResult.ThreatAction.QUARANTINE
            )
        )
    }

    private fun checkLargeApk(item: FileItem, results: MutableList<ThreatResult>) {
        if (item.extension.lowercase() != "apk") return
        // APKs > 200MB in user storage are suspicious (packed malware / data exfil)
        if (item.size > 209_715_200) {
            results.add(
                ThreatResult(
                    name = "Unusually Large APK",
                    description = "APK \"${item.name}\" is ${item.size / (1024 * 1024)}MB. Unusually large APKs may contain packed malware or stolen data.",
                    severity = ThreatResult.Severity.LOW,
                    source = ThreatResult.ScannerSource.FILE_SIGNATURE,
                    filePath = item.path,
                    category = ThreatResult.ThreatCategory.SUSPICIOUS_FILE,
                    action = ThreatResult.ThreatAction.QUARANTINE
                )
            )
        }
    }

    private fun checkArchiveBomb(item: FileItem, results: MutableList<ThreatResult>) {
        val ext = item.extension.lowercase()
        if (ext !in setOf("zip", "gz", "bz2", "xz", "7z", "rar")) return
        // Extremely small archives (<100 bytes) are suspicious — could be zip bombs or corrupt
        if (item.size in 1..99) {
            results.add(
                ThreatResult(
                    name = "Suspicious Archive",
                    description = "Archive \"${item.name}\" is only ${item.size} bytes. Very small archives may be zip bombs (files that expand to enormous sizes when extracted).",
                    severity = ThreatResult.Severity.LOW,
                    source = ThreatResult.ScannerSource.FILE_SIGNATURE,
                    filePath = item.path,
                    category = ThreatResult.ThreatCategory.SUSPICIOUS_FILE
                )
            )
        }
    }

    private fun hashFile(file: File, algorithm: String): String {
        val md = MessageDigest.getInstance(algorithm)
        file.inputStream().buffered().use { stream ->
            val buffer = ByteArray(8192)
            var bytesRead: Int
            while (stream.read(buffer).also { bytesRead = it } != -1) {
                md.update(buffer, 0, bytesRead)
            }
        }
        return md.digest().joinToString("") { "%02x".format(it) }
    }
}
