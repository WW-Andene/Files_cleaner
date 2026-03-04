package com.filecleaner.app.ui.security

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.Animation
import android.view.animation.ScaleAnimation
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.filecleaner.app.R
import com.filecleaner.app.databinding.FragmentAntivirusBinding
import com.filecleaner.app.services.ScanService
import com.filecleaner.app.utils.antivirus.*
import com.filecleaner.app.viewmodel.MainViewModel
import com.google.android.material.button.MaterialButton
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Enhanced antivirus scanner fragment with 5-phase hybrid scan:
 * 1. App Integrity (root, debugger, emulator, hooks, dev settings, overlay, accessibility, device admin)
 * 2. File Signatures (hash matching, patterns, ELF/DEX, scripts, hidden files)
 * 3. Privacy Audit (permissions, notification listeners, usage stats, app enumeration)
 * 4. Network Security (cleartext, data exfiltration, intercept tools, listening ports)
 * 5. App Verification (installer, certificates, debug signing, cloned apps)
 *
 * Features: severity filter chips, scan history, threat detail dialog, Fix All, lifecycle-safe.
 */
class AntivirusFragment : Fragment() {

    private var _binding: FragmentAntivirusBinding? = null
    private val binding get() = _binding!!
    private val vm: MainViewModel by activityViewModels()
    private var isScanning = false
    private val allThreats = mutableListOf<ThreatResult>()
    private var currentFilter: SeverityFilter = SeverityFilter.ALL
    private var pulseAnimation: Animation? = null

    private val progressHandler = Handler(Looper.getMainLooper())
    private val progressRunnable = object : Runnable {
        override fun run() {
            if (ScanService.isRunning) {
                updateProgress(ScanService.currentProgress)
                _binding?.tvPhase?.text = ScanService.currentPhase
                progressHandler.postDelayed(this, 500)
            } else if (ScanService.scanComplete) {
                val results = ScanService.scanResults
                if (results != null) {
                    allThreats.clear()
                    allThreats.addAll(results)
                    isScanning = false
                    stopShieldPulse()
                    showResults()
                }
            }
        }
    }

    private enum class SeverityFilter {
        ALL, CRITICAL, HIGH, MEDIUM, LOW_INFO
    }

    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?): View {
        _binding = FragmentAntivirusBinding.inflate(i, c, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.btnBack.setOnClickListener { findNavController().popBackStack() }
        binding.recyclerResults.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerResults.isNestedScrollingEnabled = false

        binding.btnScan.setOnClickListener {
            if (!isScanning) startScan()
        }

        binding.btnHistory.setOnClickListener {
            showScanHistory()
        }

        binding.btnFixAll.setOnClickListener {
            fixAllThreats()
        }

        // Filter chips
        binding.chipAll.setOnClickListener { applyFilter(SeverityFilter.ALL) }
        binding.chipCritical.setOnClickListener { applyFilter(SeverityFilter.CRITICAL) }
        binding.chipHigh.setOnClickListener { applyFilter(SeverityFilter.HIGH) }
        binding.chipMedium.setOnClickListener { applyFilter(SeverityFilter.MEDIUM) }
        binding.chipLowInfo.setOnClickListener { applyFilter(SeverityFilter.LOW_INFO) }

        // Show last scan time
        showLastScanTime()

        // Restore state if the service is running or has completed results
        if (ScanService.isRunning) {
            isScanning = true
            binding.btnScan.isEnabled = false
            binding.btnScan.text = getString(R.string.av_scanning)
            binding.progressContainer.visibility = View.VISIBLE
            startShieldPulse()
            progressHandler.post(progressRunnable)
        } else if (ScanService.scanComplete && ScanService.scanResults != null) {
            allThreats.clear()
            allThreats.addAll(ScanService.scanResults!!)
            showResults()
        }
    }

    private fun showLastScanTime() {
        val ctx = context ?: return
        val lastTime = ScanHistoryManager.getLastScanTimeFormatted(ctx)
        val b = _binding ?: return
        if (lastTime != null) {
            b.tvLastScan.text = getString(R.string.av_last_scan, lastTime)
            b.tvLastScan.visibility = View.VISIBLE
        } else {
            b.tvLastScan.visibility = View.GONE
        }
    }

    /** Update both the ProgressBar value and the percentage text. */
    private fun updateProgress(pct: Int) {
        _binding?.progress?.post {
            _binding?.progress?.progress = pct
            _binding?.tvProgressPct?.text = getString(R.string.av_progress_pct, pct)
        }
    }

    private fun startScan() {
        isScanning = true
        allThreats.clear()
        currentFilter = SeverityFilter.ALL
        ScanService.clearResults()

        val b = _binding ?: return
        b.btnScan.isEnabled = false
        b.btnScan.text = getString(R.string.av_scanning)
        b.btnScan.icon = null
        b.progressContainer.visibility = View.VISIBLE
        b.progress.isIndeterminate = false
        b.progress.progress = 0
        b.tvProgressPct.text = getString(R.string.av_progress_pct, 0)
        b.summaryRow.visibility = View.GONE
        b.recyclerResults.visibility = View.GONE
        b.filterScroll.visibility = View.GONE
        b.btnFixAll.visibility = View.GONE
        b.tvLastScan.visibility = View.GONE

        // Pulse the shield icon during scan
        startShieldPulse()

        // Start the foreground service
        ScanService.start(requireContext())

        // Start polling for progress
        progressHandler.post(progressRunnable)
    }

    private fun updatePhase(titleRes: Int, descRes: Int) {
        val ctx = _binding?.tvPhase?.context ?: return
        _binding?.tvPhase?.text = ctx.getString(titleRes)
        _binding?.tvPhaseDesc?.text = ctx.getString(descRes)
    }

    private fun startShieldPulse() {
        _binding?.ivShield?.let {
            it.setColorFilter(ContextCompat.getColor(it.context, R.color.colorAccent))
        }
        if (com.filecleaner.app.utils.MotionUtil.isReducedMotion(requireContext())) {
            return
        }
        pulseAnimation = ScaleAnimation(
            1f, 1.1f, 1f, 1.1f,
            Animation.RELATIVE_TO_SELF, 0.5f,
            Animation.RELATIVE_TO_SELF, 0.5f
        ).apply {
            duration = resources.getInteger(R.integer.motion_emphasis).toLong()
            repeatCount = Animation.INFINITE
            repeatMode = Animation.REVERSE
        }
        _binding?.ivShield?.startAnimation(pulseAnimation)
    }

    private fun stopShieldPulse() {
        _binding?.ivShield?.clearAnimation()
        pulseAnimation = null
    }

    private fun showResults() {
        val b = _binding ?: return
        val ctx = context ?: return

        b.btnScan.isEnabled = true
        b.btnScan.text = getString(R.string.av_scan_again)
        b.btnScan.icon = null
        b.progressContainer.visibility = View.GONE
        b.summaryRow.visibility = View.VISIBLE

        // Sort by severity (critical first)
        val sorted = allThreats.sortedByDescending { it.severity.ordinal }

        val criticalCount = sorted.count { it.severity == ThreatResult.Severity.CRITICAL }
        val highCount = sorted.count { it.severity == ThreatResult.Severity.HIGH }
        val mediumCount = sorted.count { it.severity == ThreatResult.Severity.MEDIUM }
        val lowInfoCount = sorted.count { it.severity <= ThreatResult.Severity.LOW }

        b.tvCriticalCount.text = criticalCount.toString()
        b.tvHighCount.text = highCount.toString()
        b.tvMediumCount.text = mediumCount.toString()
        b.tvCleanCount.text = lowInfoCount.toString()

        // Update shield color based on results
        val shieldColor = when {
            criticalCount > 0 -> R.color.colorError
            highCount > 0 -> R.color.colorError
            mediumCount > 0 -> R.color.colorAccent
            else -> R.color.colorPrimary
        }
        b.ivShield.setColorFilter(ContextCompat.getColor(ctx, shieldColor))

        if (sorted.isEmpty()) {
            b.tvStatus.text = getString(R.string.av_all_clear)
            b.tvStatus.setTextColor(ContextCompat.getColor(ctx, R.color.colorPrimary))
            b.recyclerResults.visibility = View.GONE
            b.filterScroll.visibility = View.GONE
            b.btnFixAll.visibility = View.GONE
        } else {
            val threatCount = criticalCount + highCount + mediumCount
            b.tvStatus.text = resources.getQuantityString(
                R.plurals.av_found_threats, sorted.size, sorted.size
            )
            b.tvStatus.setTextColor(
                ContextCompat.getColor(
                    ctx,
                    if (threatCount > 0) R.color.colorError else R.color.textPrimary
                )
            )
            b.recyclerResults.visibility = View.VISIBLE
            b.filterScroll.visibility = View.VISIBLE

            // Show Fix All only if there are actionable threats
            val actionableCount = sorted.count { it.action != ThreatResult.ThreatAction.NONE }
            if (actionableCount > 0) {
                b.btnFixAll.visibility = View.VISIBLE
                b.btnFixAll.text = getString(R.string.av_fix_all_count, actionableCount)
            }

            applyFilter(SeverityFilter.ALL)
        }

        showLastScanTime()
    }

    private fun applyFilter(filter: SeverityFilter) {
        currentFilter = filter
        val b = _binding ?: return

        // Update chip selection
        when (filter) {
            SeverityFilter.ALL -> b.chipGroupFilter.check(R.id.chip_all)
            SeverityFilter.CRITICAL -> b.chipGroupFilter.check(R.id.chip_critical)
            SeverityFilter.HIGH -> b.chipGroupFilter.check(R.id.chip_high)
            SeverityFilter.MEDIUM -> b.chipGroupFilter.check(R.id.chip_medium)
            SeverityFilter.LOW_INFO -> b.chipGroupFilter.check(R.id.chip_low_info)
        }

        val filtered = when (filter) {
            SeverityFilter.ALL -> allThreats.sortedByDescending { it.severity.ordinal }
            SeverityFilter.CRITICAL -> allThreats.filter { it.severity == ThreatResult.Severity.CRITICAL }
            SeverityFilter.HIGH -> allThreats.filter { it.severity == ThreatResult.Severity.HIGH }
            SeverityFilter.MEDIUM -> allThreats.filter { it.severity == ThreatResult.Severity.MEDIUM }
            SeverityFilter.LOW_INFO -> allThreats.filter { it.severity <= ThreatResult.Severity.LOW }
        }

        b.recyclerResults.adapter = ThreatAdapter(filtered)
    }

    private fun showScanHistory() {
        val ctx = context ?: return
        val history = ScanHistoryManager.getHistory(ctx)

        val dialogView = LayoutInflater.from(ctx).inflate(R.layout.dialog_scan_history, null)
        val recycler = dialogView.findViewById<RecyclerView>(R.id.recycler_history)
        val noHistory = dialogView.findViewById<TextView>(R.id.tv_no_history)

        recycler.layoutManager = LinearLayoutManager(ctx)

        if (history.isEmpty()) {
            recycler.visibility = View.GONE
            noHistory.visibility = View.VISIBLE
        } else {
            recycler.adapter = HistoryAdapter(history)
        }

        AlertDialog.Builder(ctx)
            .setView(dialogView)
            .setPositiveButton(R.string.av_dismiss, null)
            .setNeutralButton(R.string.av_clear_history) { _, _ ->
                ScanHistoryManager.clearHistory(ctx)
                _binding?.let { showLastScanTime() }
            }
            .show()
    }

    private fun fixAllThreats() {
        val ctx = context ?: return
        val actionable = allThreats.filter { it.action != ThreatResult.ThreatAction.NONE }
        if (actionable.isEmpty()) return

        val quarantine = actionable.filter { it.action == ThreatResult.ThreatAction.QUARANTINE }
        val delete = actionable.filter { it.action == ThreatResult.ThreatAction.DELETE }
        val uninstall = actionable.filter { it.action == ThreatResult.ThreatAction.UNINSTALL }

        val summary = buildString {
            if (quarantine.isNotEmpty()) append(resources.getQuantityString(R.plurals.av_fix_quarantine, quarantine.size, quarantine.size) + "\n")
            if (delete.isNotEmpty()) append(resources.getQuantityString(R.plurals.av_fix_delete, delete.size, delete.size) + "\n")
            if (uninstall.isNotEmpty()) append(resources.getQuantityString(R.plurals.av_fix_uninstall, uninstall.size, uninstall.size))
        }.trim()

        AlertDialog.Builder(ctx)
            .setTitle(getString(R.string.av_fix_all))
            .setMessage(getString(R.string.av_fix_all_confirm, summary))
            .setPositiveButton(getString(R.string.av_proceed)) { _, _ ->
                viewLifecycleOwner.lifecycleScope.launch {
                    var fixed = 0

                    // Quarantine files
                    for (threat in quarantine) {
                        val path = threat.filePath ?: continue
                        if (quarantineFileInternal(path)) fixed++
                    }

                    // Delete files
                    for (threat in delete) {
                        val path = threat.filePath ?: continue
                        val file = File(path)
                        if (file.exists() && file.delete()) fixed++
                    }

                    // Open uninstall dialogs
                    for (threat in uninstall) {
                        val pkg = threat.packageName ?: continue
                        try {
                            startActivity(Intent(Intent.ACTION_DELETE).apply {
                                data = Uri.parse("package:$pkg")
                            })
                        } catch (_: Exception) {
                            // Can't open uninstall dialog
                        }
                    }

                    val b = _binding ?: return@launch
                    Snackbar.make(
                        b.root,
                        getString(R.string.av_fixed_count, fixed),
                        Snackbar.LENGTH_SHORT
                    ).show()

                    // Refresh results
                    allThreats.removeAll {
                        (it.action == ThreatResult.ThreatAction.QUARANTINE || it.action == ThreatResult.ThreatAction.DELETE)
                                && it.filePath != null && !File(it.filePath).exists()
                    }
                    showResults()
                }
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    private fun showThreatDetail(threat: ThreatResult) {
        val ctx = context ?: return
        val dialogView = LayoutInflater.from(ctx).inflate(R.layout.dialog_threat_detail, null)

        val severityDot = dialogView.findViewById<View>(R.id.severity_dot)
        val tvSeverity = dialogView.findViewById<TextView>(R.id.tv_severity)
        val tvSource = dialogView.findViewById<TextView>(R.id.tv_source)
        val tvName = dialogView.findViewById<TextView>(R.id.tv_name)
        val tvDescription = dialogView.findViewById<TextView>(R.id.tv_description)
        val filePathRow = dialogView.findViewById<View>(R.id.file_path_row)
        val tvFilePath = dialogView.findViewById<TextView>(R.id.tv_file_path)
        val packageRow = dialogView.findViewById<View>(R.id.package_row)
        val tvPackage = dialogView.findViewById<TextView>(R.id.tv_package)
        val tvCategory = dialogView.findViewById<TextView>(R.id.tv_category)
        val btnAction = dialogView.findViewById<MaterialButton>(R.id.btn_action)
        val btnDismiss = dialogView.findViewById<MaterialButton>(R.id.btn_dismiss)

        // Severity
        val (severityColor, severityLabel) = severityInfo(ctx, threat.severity)
        tvSeverity.text = severityLabel
        tvSeverity.setTextColor(ContextCompat.getColor(ctx, severityColor))
        severityDot.backgroundTintList =
            android.content.res.ColorStateList.valueOf(ContextCompat.getColor(ctx, severityColor))

        // Source
        tvSource.text = sourceLabel(ctx, threat.source)

        // Details
        tvName.text = threat.name
        tvDescription.text = threat.description

        // File path
        if (threat.filePath != null) {
            filePathRow.visibility = View.VISIBLE
            tvFilePath.text = threat.filePath
        }

        // Package
        if (threat.packageName != null) {
            packageRow.visibility = View.VISIBLE
            tvPackage.text = threat.packageName
        }

        // Category
        tvCategory.text = categoryLabel(ctx, threat.category)

        val dialog = AlertDialog.Builder(ctx)
            .setView(dialogView)
            .create()

        btnDismiss.setOnClickListener { dialog.dismiss() }

        // Action button
        if (threat.action != ThreatResult.ThreatAction.NONE) {
            btnAction.visibility = View.VISIBLE
            btnAction.text = actionLabel(ctx, threat.action)
            btnAction.setOnClickListener {
                performAction(threat)
                dialog.dismiss()
            }
        }

        dialog.show()
    }

    private fun performAction(threat: ThreatResult) {
        when (threat.action) {
            ThreatResult.ThreatAction.QUARANTINE -> {
                val path = threat.filePath ?: return
                if (quarantineFileInternal(path)) {
                    _binding?.root?.let {
                        Snackbar.make(it, getString(R.string.av_quarantined_msg, File(path).name), Snackbar.LENGTH_SHORT).show()
                    }
                }
            }
            ThreatResult.ThreatAction.DELETE -> {
                confirmDelete(threat.filePath ?: return)
            }
            ThreatResult.ThreatAction.UNINSTALL -> {
                requestUninstall(threat.packageName ?: return)
            }
            ThreatResult.ThreatAction.OPEN_SETTINGS -> {
                openSettings()
            }
            ThreatResult.ThreatAction.REVOKE_ADMIN -> {
                revokeDeviceAdmin(threat.packageName ?: return)
            }
            ThreatResult.ThreatAction.NONE -> { /* no action */ }
        }
    }

    private fun quarantineFileInternal(filePath: String): Boolean {
        val ctx = context ?: return false
        val quarantineDir = File(ctx.getExternalFilesDir(null), ".quarantine")
        quarantineDir.mkdirs()
        val src = File(filePath)
        val dst = File(quarantineDir, "${System.currentTimeMillis()}_${src.name}")
        // renameTo fails across filesystems; fall back to copy + delete
        if (src.renameTo(dst)) return true
        return try {
            src.copyTo(dst, overwrite = false)
            if (!src.delete()) {
                dst.delete()
                false
            } else true
        } catch (_: Exception) {
            dst.delete()
            false
        }
    }

    private fun confirmDelete(filePath: String) {
        val ctx = context ?: return
        AlertDialog.Builder(ctx)
            .setTitle(getString(R.string.delete))
            .setMessage(getString(R.string.av_confirm_delete, File(filePath).name))
            .setPositiveButton(getString(R.string.delete)) { _, _ ->
                val file = File(filePath)
                if (file.delete()) {
                    _binding?.root?.let {
                        Snackbar.make(it, getString(R.string.av_deleted, file.name), Snackbar.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    private fun requestUninstall(packageName: String) {
        try {
            startActivity(Intent(Intent.ACTION_DELETE).apply {
                data = Uri.parse("package:$packageName")
            })
        } catch (e: Exception) {
            _binding?.root?.let {
                Snackbar.make(it, getString(R.string.av_uninstall_failed), Snackbar.LENGTH_SHORT).show()
            }
        }
    }

    private fun openSettings() {
        try {
            startActivity(Intent(Settings.ACTION_SETTINGS))
        } catch (_: Exception) {
            // Can't open settings
        }
    }

    private fun revokeDeviceAdmin(packageName: String) {
        try {
            startActivity(Intent(Settings.ACTION_SECURITY_SETTINGS))
        } catch (_: Exception) {
            try {
                startActivity(Intent().apply {
                    component = ComponentName("com.android.settings",
                        "com.android.settings.DeviceAdminSettings")
                })
            } catch (_: Exception) {
                // Can't open
            }
        }
    }

    private fun severityInfo(ctx: android.content.Context, severity: ThreatResult.Severity): Pair<Int, String> {
        return when (severity) {
            ThreatResult.Severity.CRITICAL -> R.color.colorError to ctx.getString(R.string.av_critical)
            ThreatResult.Severity.HIGH -> R.color.colorError to ctx.getString(R.string.av_high)
            ThreatResult.Severity.MEDIUM -> R.color.catVideo to ctx.getString(R.string.av_medium)
            ThreatResult.Severity.LOW -> R.color.colorAccent to ctx.getString(R.string.av_low)
            ThreatResult.Severity.INFO -> R.color.textTertiary to ctx.getString(R.string.av_info)
        }
    }

    private fun sourceLabel(ctx: android.content.Context, source: ThreatResult.ScannerSource): String {
        return when (source) {
            ThreatResult.ScannerSource.APP_INTEGRITY -> ctx.getString(R.string.av_source_integrity)
            ThreatResult.ScannerSource.FILE_SIGNATURE -> ctx.getString(R.string.av_source_signature)
            ThreatResult.ScannerSource.PRIVACY_AUDIT -> ctx.getString(R.string.av_source_privacy)
            ThreatResult.ScannerSource.NETWORK_SECURITY -> ctx.getString(R.string.av_source_network)
            ThreatResult.ScannerSource.APP_VERIFICATION -> ctx.getString(R.string.av_source_verification)
        }
    }

    private fun categoryLabel(ctx: android.content.Context, category: ThreatResult.ThreatCategory): String {
        return when (category) {
            ThreatResult.ThreatCategory.GENERAL -> ctx.getString(R.string.threat_category_general)
            ThreatResult.ThreatCategory.MALWARE -> ctx.getString(R.string.threat_category_malware)
            ThreatResult.ThreatCategory.ROOT_TAMPERING -> ctx.getString(R.string.threat_category_root_tampering)
            ThreatResult.ThreatCategory.PRIVACY -> ctx.getString(R.string.threat_category_privacy)
            ThreatResult.ThreatCategory.NETWORK -> ctx.getString(R.string.threat_category_network)
            ThreatResult.ThreatCategory.SIDELOAD -> ctx.getString(R.string.threat_category_sideload)
            ThreatResult.ThreatCategory.ACCESSIBILITY_ABUSE -> ctx.getString(R.string.threat_category_accessibility_abuse)
            ThreatResult.ThreatCategory.DEVICE_ADMIN -> ctx.getString(R.string.threat_category_device_admin)
            ThreatResult.ThreatCategory.SUSPICIOUS_FILE -> ctx.getString(R.string.threat_category_suspicious_file)
            ThreatResult.ThreatCategory.DEBUG_RISK -> ctx.getString(R.string.threat_category_debug_risk)
        }
    }

    private fun actionLabel(ctx: android.content.Context, action: ThreatResult.ThreatAction): String {
        return when (action) {
            ThreatResult.ThreatAction.QUARANTINE -> ctx.getString(R.string.av_quarantine)
            ThreatResult.ThreatAction.DELETE -> ctx.getString(R.string.delete)
            ThreatResult.ThreatAction.UNINSTALL -> ctx.getString(R.string.av_uninstall)
            ThreatResult.ThreatAction.OPEN_SETTINGS -> ctx.getString(R.string.av_open_settings)
            ThreatResult.ThreatAction.REVOKE_ADMIN -> ctx.getString(R.string.av_revoke_admin)
            ThreatResult.ThreatAction.NONE -> ""
        }
    }

    // ── Adapter for threat results ──

    private inner class ThreatAdapter(
        private val items: List<ThreatResult>
    ) : RecyclerView.Adapter<ThreatAdapter.ViewHolder>() {

        inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val severityDot: View = view.findViewById(R.id.severity_dot)
            val name: TextView = view.findViewById(R.id.tv_name)
            val severity: TextView = view.findViewById(R.id.tv_severity)
            val description: TextView = view.findViewById(R.id.tv_description)
            val source: TextView = view.findViewById(R.id.tv_source)
            val actionBtn: MaterialButton = view.findViewById(R.id.btn_action)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_threat_result, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = items[position]
            val ctx = holder.itemView.context

            // Reset state from recycled ViewHolder
            holder.actionBtn.isEnabled = true

            holder.name.text = item.name
            holder.description.text = item.description
            holder.source.text = sourceLabel(ctx, item.source)

            val (severityColor, severityLabel) = severityInfo(ctx, item.severity)
            holder.severity.text = severityLabel
            holder.severity.setTextColor(ContextCompat.getColor(ctx, severityColor))
            holder.severityDot.backgroundTintList =
                android.content.res.ColorStateList.valueOf(ContextCompat.getColor(ctx, severityColor))

            // Tap to see details
            holder.itemView.setOnClickListener {
                showThreatDetail(item)
            }

            // Action button
            when (item.action) {
                ThreatResult.ThreatAction.QUARANTINE -> {
                    holder.actionBtn.visibility = View.VISIBLE
                    holder.actionBtn.text = ctx.getString(R.string.av_quarantine)
                    holder.actionBtn.setOnClickListener {
                        val filePath = item.filePath ?: return@setOnClickListener
                        if (quarantineFileInternal(filePath)) {
                            holder.actionBtn.isEnabled = false
                            holder.actionBtn.text = ctx.getString(R.string.av_quarantined)
                            _binding?.root?.let { root ->
                                Snackbar.make(root, ctx.getString(R.string.av_quarantined_msg, File(filePath).name), Snackbar.LENGTH_SHORT).show()
                            }
                        }
                    }
                }
                ThreatResult.ThreatAction.DELETE -> {
                    holder.actionBtn.visibility = View.VISIBLE
                    holder.actionBtn.text = ctx.getString(R.string.delete)
                    holder.actionBtn.setOnClickListener {
                        confirmDelete(item.filePath ?: return@setOnClickListener)
                    }
                }
                ThreatResult.ThreatAction.UNINSTALL -> {
                    holder.actionBtn.visibility = View.VISIBLE
                    holder.actionBtn.text = ctx.getString(R.string.av_uninstall)
                    holder.actionBtn.setOnClickListener {
                        requestUninstall(item.packageName ?: return@setOnClickListener)
                    }
                }
                ThreatResult.ThreatAction.OPEN_SETTINGS -> {
                    holder.actionBtn.visibility = View.VISIBLE
                    holder.actionBtn.text = ctx.getString(R.string.av_open_settings)
                    holder.actionBtn.setOnClickListener { openSettings() }
                }
                ThreatResult.ThreatAction.REVOKE_ADMIN -> {
                    holder.actionBtn.visibility = View.VISIBLE
                    holder.actionBtn.text = ctx.getString(R.string.av_revoke_admin)
                    holder.actionBtn.setOnClickListener {
                        revokeDeviceAdmin(item.packageName ?: return@setOnClickListener)
                    }
                }
                ThreatResult.ThreatAction.NONE -> {
                    holder.actionBtn.visibility = View.GONE
                }
            }
        }

        override fun getItemCount() = items.size
    }

    // ── Adapter for scan history ──

    private inner class HistoryAdapter(
        private val items: List<ScanHistoryManager.ScanRecord>
    ) : RecyclerView.Adapter<HistoryAdapter.ViewHolder>() {

        inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val time: TextView = view.findViewById(R.id.tv_time)
            val summary: TextView = view.findViewById(R.id.tv_summary)
            val threatCount: TextView = view.findViewById(R.id.tv_threat_count)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_scan_history, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val record = items[position]
            holder.time.text = record.formattedTime(holder.itemView.context)
            holder.summary.text = holder.itemView.context.getString(R.string.av_scan_history_summary, record.totalFindings, record.critical, record.high, record.medium)
            holder.threatCount.text = record.threatCount.toString()
            holder.threatCount.setTextColor(
                ContextCompat.getColor(
                    holder.itemView.context,
                    if (record.threatCount > 0) R.color.colorError else R.color.colorPrimary
                )
            )
        }

        override fun getItemCount() = items.size
    }

    override fun onDestroyView() {
        progressHandler.removeCallbacksAndMessages(null)
        stopShieldPulse()
        super.onDestroyView()
        _binding = null
    }
}
