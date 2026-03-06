package com.filecleaner.app.utils.antivirus

import android.content.Context
import com.filecleaner.app.R
import com.filecleaner.app.data.FileItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.RandomAccessFile
import java.security.MessageDigest

/**
 * Heuristic file scanner.
 *
 * F-011: This scanner uses heuristic analysis — it does NOT provide comprehensive
 * antivirus protection. The hash database contains only test/sample entries.
 * Detection relies primarily on:
 * - Filename pattern matching for known malware names
 * - Suspicious file characteristics (APKs in unusual locations)
 * - ELF binary detection (Linux executables on Android storage)
 * - Suspicious script detection (shell/python/JS scripts with dangerous patterns)
 * - Hidden file detection (dotfiles in non-standard locations)
 * - Large APK detection (unusually large APKs that may be packed malware)
 * - Archive bombs (suspiciously small archives)
 * - DEX file detection outside app directories
 * - Limited file hash matching (test signatures only — not a production malware DB)
 */
object SignatureScanner {

    /**
     * F-011: Test-only hash database — contains only the EICAR test file and sample entries.
     * This is NOT a production malware signature database. Hash matching is provided for
     * demonstration and testing purposes only. For real malware detection, integrate an
     * updatable signature database from a threat intelligence feed.
     */
    private val KNOWN_MALWARE_MD5 = setOf(
        "44d88612fea8a8f36de82e1278abb02f", // EICAR test file
        "e1105070ba828007508566e28a2b8d4c", // Known Android malware sample
        "3395856ce81f2b7382dee72602f798b6"  // Suspicious payload
    )

    /** Test-only SHA-256 hashes (see F-011 note above) */
    private val KNOWN_MALWARE_SHA256 = setOf(
        "275a021bbfb6489e54d471899f7db9d1663fc695ec2fe2a2c4538aabf651fd0f"  // EICAR SHA-256
    )

    /** Simple suspicious extensions — checked via O(1) Set lookup instead of regex */
    private val SUSPICIOUS_EXTENSIONS = setOf(
        "exe", "bat", "cmd", "scr", "pif", "com", "vbs", "wsf", "dll", "hta", "ps1", "psm1"
    )

    /** Complex suspicious filename patterns that require regex (kept minimal) */
    private val SUSPICIOUS_PATTERNS = listOf(
        Regex(".*\\.apk\\..*", RegexOption.IGNORE_CASE),      // Double extension APK
        Regex(".*payload.*\\.apk", RegexOption.IGNORE_CASE),   // Payload APKs
        Regex(".*keylog.*", RegexOption.IGNORE_CASE),           // Keyloggers
        Regex(".*trojan.*", RegexOption.IGNORE_CASE),           // Trojan indicators
        Regex(".*rat_.*", RegexOption.IGNORE_CASE),             // RAT
        Regex(".*backdoor.*", RegexOption.IGNORE_CASE),         // Backdoor
        Regex(".*exploit.*", RegexOption.IGNORE_CASE),          // Exploits
        Regex(".*rootkit.*", RegexOption.IGNORE_CASE),          // Rootkits
        Regex(".*spyware.*", RegexOption.IGNORE_CASE),          // Spyware
        Regex(".*meterpreter.*", RegexOption.IGNORE_CASE),      // Metasploit payload
        Regex(".*reverse.*shell.*", RegexOption.IGNORE_CASE),   // Reverse shells
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
        context: Context,
        files: List<FileItem>,
        onProgress: (scanned: Int, total: Int) -> Unit
    ): List<ThreatResult> = withContext(Dispatchers.IO) {
        val results = mutableListOf<ThreatResult>()
        val total = files.size

        for ((index, item) in files.withIndex()) {
            if (index % 100 == 0) onProgress(index, total)

            // 1. Check filename patterns
            checkFilenamePatterns(context, item, results)

            // 2. Check APKs in suspicious locations
            checkSuspiciousApkLocation(context, item, results)

            // 3. Hash check for files < 50MB
            checkFileHashes(context, item, results)

            // 4. Check for ELF binaries in storage
            checkElfBinary(context, item, results)

            // 5. Check for DEX files outside app directories
            checkLooseDex(context, item, results)

            // 6. Check suspicious scripts (content analysis for small scripts)
            checkSuspiciousScript(context, item, results)

            // 7. Check hidden files in non-standard locations
            checkHiddenFiles(context, item, results)

            // 8. Check unusually large APKs
            checkLargeApk(context, item, results)

            // 9. Check archive bombs (tiny archives)
            checkArchiveBomb(context, item, results)
        }

        onProgress(total, total)
        results
    }

    private fun checkFilenamePatterns(context: Context, item: FileItem, results: MutableList<ThreatResult>) {
        // D3-04: O(1) extension check replaces 12 regex matches
        if (item.extension.lowercase() in SUSPICIOUS_EXTENSIONS) {
            results.add(
                ThreatResult(
                    name = context.getString(R.string.threat_suspicious_filename),
                    description = context.getString(R.string.threat_desc_suspicious_extension, item.name, item.extension),
                    severity = ThreatResult.Severity.MEDIUM,
                    source = ThreatResult.ScannerSource.FILE_SIGNATURE,
                    filePath = item.path,
                    category = ThreatResult.ThreatCategory.SUSPICIOUS_FILE,
                    action = ThreatResult.ThreatAction.QUARANTINE
                )
            )
            return
        }
        for (pattern in SUSPICIOUS_PATTERNS) {
            if (pattern.matches(item.name)) {
                results.add(
                    ThreatResult(
                        name = context.getString(R.string.threat_suspicious_filename),
                        description = context.getString(R.string.threat_desc_suspicious_filename, item.name),
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

    private fun checkSuspiciousApkLocation(context: Context, item: FileItem, results: MutableList<ThreatResult>) {
        if (item.extension != "apk") return
        for (dir in SUSPICIOUS_APK_DIRS) {
            if (item.path.contains(dir)) {
                results.add(
                    ThreatResult(
                        name = context.getString(R.string.threat_apk_unusual_location),
                        description = context.getString(R.string.threat_desc_apk_unusual_location, item.path.substringBeforeLast('/')),
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

    private fun checkFileHashes(context: Context, item: FileItem, results: MutableList<ThreatResult>) {
        // D3-03: Limit hashing to files ≤5MB — the known malware DB has only 4 hashes,
        // so hashing every file up to 50MB wastes enormous I/O for negligible detection gain.
        if (item.size !in 1..5_242_880) return

        try {
            val file = File(item.path)
            if (!file.canRead()) return

            val md5 = hashFile(file, "MD5")
            if (md5 in KNOWN_MALWARE_MD5) {
                results.add(
                    ThreatResult(
                        name = context.getString(R.string.threat_known_malware),
                        description = context.getString(R.string.threat_desc_known_malware_md5, item.name, md5),
                        severity = ThreatResult.Severity.CRITICAL,
                        source = ThreatResult.ScannerSource.FILE_SIGNATURE,
                        filePath = item.path,
                        category = ThreatResult.ThreatCategory.MALWARE,
                        action = ThreatResult.ThreatAction.DELETE
                    )
                )
                return
            }

            // SHA-256 for higher confidence (file is already <=5MB per the check above)
            run {
                val sha256 = hashFile(file, "SHA-256")
                if (sha256 in KNOWN_MALWARE_SHA256) {
                    results.add(
                        ThreatResult(
                            name = context.getString(R.string.threat_known_malware),
                            description = context.getString(R.string.threat_desc_known_malware_sha256, item.name),
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

    private fun checkElfBinary(context: Context, item: FileItem, results: MutableList<ThreatResult>) {
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
                                name = context.getString(R.string.threat_elf_binary),
                                description = context.getString(R.string.threat_desc_elf_binary, item.name),
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

    private fun checkLooseDex(context: Context, item: FileItem, results: MutableList<ThreatResult>) {
        if (item.extension.lowercase() != "dex") return
        // DEX files should only be inside APKs or in /data/dalvik-cache
        if (!item.path.contains("/data/dalvik-cache/") && !item.path.contains("/data/app/")) {
            results.add(
                ThreatResult(
                    name = context.getString(R.string.threat_loose_dex),
                    description = context.getString(R.string.threat_desc_loose_dex, item.name),
                    severity = ThreatResult.Severity.HIGH,
                    source = ThreatResult.ScannerSource.FILE_SIGNATURE,
                    filePath = item.path,
                    category = ThreatResult.ThreatCategory.MALWARE,
                    action = ThreatResult.ThreatAction.DELETE
                )
            )
        }
    }

    private fun checkSuspiciousScript(context: Context, item: FileItem, results: MutableList<ThreatResult>) {
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
                            name = context.getString(R.string.threat_dangerous_script),
                            description = context.getString(R.string.threat_desc_dangerous_script, item.name, pattern.pattern.take(40)),
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

    private fun checkHiddenFiles(context: Context, item: FileItem, results: MutableList<ThreatResult>) {
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
                name = context.getString(R.string.threat_hidden_executable),
                description = context.getString(R.string.threat_desc_hidden_executable, item.name, ext, item.path.substringBeforeLast('/')),
                severity = ThreatResult.Severity.MEDIUM,
                source = ThreatResult.ScannerSource.FILE_SIGNATURE,
                filePath = item.path,
                category = ThreatResult.ThreatCategory.SUSPICIOUS_FILE,
                action = ThreatResult.ThreatAction.QUARANTINE
            )
        )
    }

    private fun checkLargeApk(context: Context, item: FileItem, results: MutableList<ThreatResult>) {
        if (item.extension.lowercase() != "apk") return
        // APKs > 200MB in user storage are suspicious (packed malware / data exfil)
        if (item.size > 209_715_200) {
            results.add(
                ThreatResult(
                    name = context.getString(R.string.threat_large_apk),
                    description = context.getString(R.string.threat_desc_large_apk, item.name, (item.size / (1024 * 1024)).toInt()),
                    severity = ThreatResult.Severity.LOW,
                    source = ThreatResult.ScannerSource.FILE_SIGNATURE,
                    filePath = item.path,
                    category = ThreatResult.ThreatCategory.SUSPICIOUS_FILE,
                    action = ThreatResult.ThreatAction.QUARANTINE
                )
            )
        }
    }

    private fun checkArchiveBomb(context: Context, item: FileItem, results: MutableList<ThreatResult>) {
        val ext = item.extension.lowercase()
        if (ext !in setOf("zip", "gz", "bz2", "xz", "7z", "rar")) return
        // Minimum valid archive sizes: .gz=20B, .zip=22B, .bz2=14B
        // Archives smaller than the minimum for their format are likely corrupt, not bombs.
        val minValidSize = when (ext) {
            "gz" -> 20L
            "zip" -> 22L
            "bz2" -> 14L
            "xz" -> 32L
            "7z" -> 32L
            "rar" -> 20L
            else -> 20L
        }
        if (item.size in 1 until minValidSize) {
            results.add(
                ThreatResult(
                    name = context.getString(R.string.threat_suspicious_archive),
                    description = context.getString(R.string.threat_desc_suspicious_archive, item.name, item.size.toInt(), ext),
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
