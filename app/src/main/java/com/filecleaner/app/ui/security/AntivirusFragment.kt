package com.filecleaner.app.ui.security

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
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
import com.filecleaner.app.utils.antivirus.AppIntegrityScanner
import com.filecleaner.app.utils.antivirus.PrivacyAuditor
import com.filecleaner.app.utils.antivirus.SignatureScanner
import com.filecleaner.app.utils.antivirus.ThreatResult
import com.filecleaner.app.viewmodel.MainViewModel
import com.google.android.material.button.MaterialButton
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Antivirus scanner fragment with 3-phase hybrid scan:
 * 1. App Integrity (root, debugger, emulator, malicious apps)
 * 2. File Signature (malware hash matching, suspicious filenames)
 * 3. Privacy Audit (excessive permissions, dangerous app behaviors)
 *
 * Results are displayed in a RecyclerView with severity indicators
 * and actionable buttons (quarantine, delete, uninstall).
 */
class AntivirusFragment : Fragment() {

    private var _binding: FragmentAntivirusBinding? = null
    private val binding get() = _binding!!
    private val vm: MainViewModel by activityViewModels()
    private var isScanning = false
    private val allThreats = mutableListOf<ThreatResult>()

    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?): View {
        _binding = FragmentAntivirusBinding.inflate(i, c, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.btnBack.setOnClickListener { findNavController().popBackStack() }
        binding.recyclerResults.layoutManager = LinearLayoutManager(requireContext())

        binding.btnScan.setOnClickListener {
            if (!isScanning) startScan()
        }
    }

    private fun startScan() {
        isScanning = true
        allThreats.clear()
        binding.btnScan.isEnabled = false
        binding.btnScan.text = getString(R.string.av_scanning)
        binding.progress.visibility = View.VISIBLE
        binding.tvPhase.visibility = View.VISIBLE
        binding.summaryRow.visibility = View.GONE
        binding.recyclerResults.visibility = View.GONE

        viewLifecycleOwner.lifecycleScope.launch {
            // Phase 1: App Integrity
            _binding?.tvStatus?.text = getString(R.string.av_phase_integrity)
            _binding?.tvPhase?.text = getString(R.string.av_phase_integrity_desc)
            _binding?.progress?.isIndeterminate = false

            val ctx = context ?: return@launch
            val integrityResults = AppIntegrityScanner.scan(ctx) { pct ->
                _binding?.progress?.post { _binding?.progress?.progress = pct / 3 }
            }
            allThreats.addAll(integrityResults)

            // Phase 2: File Signature Scan
            _binding?.tvStatus?.text = getString(R.string.av_phase_signature)
            _binding?.tvPhase?.text = getString(R.string.av_phase_signature_desc)

            val allFiles = vm.filesByCategory.value?.values?.flatten() ?: emptyList()
            if (allFiles.isNotEmpty()) {
                val signatureResults = SignatureScanner.scan(allFiles) { scanned, total ->
                    val pct = if (total > 0) (scanned * 33 / total) + 33 else 33
                    _binding?.progress?.post { _binding?.progress?.progress = pct }
                }
                allThreats.addAll(signatureResults)
            }

            // Phase 3: Privacy Audit
            _binding?.tvStatus?.text = getString(R.string.av_phase_privacy)
            _binding?.tvPhase?.text = getString(R.string.av_phase_privacy_desc)

            val auditCtx = context ?: return@launch
            val privacyResults = PrivacyAuditor.audit(auditCtx) { pct ->
                _binding?.progress?.post { _binding?.progress?.progress = 66 + (pct / 3) }
            }
            allThreats.addAll(privacyResults)

            // Done
            _binding?.progress?.progress = 100
            isScanning = false
            if (_binding != null) {
                showResults()
            }
        }
    }

    private fun showResults() {
        val b = _binding ?: return
        b.btnScan.isEnabled = true
        b.btnScan.text = getString(R.string.av_scan_again)
        b.progress.visibility = View.GONE
        b.tvPhase.visibility = View.GONE
        b.summaryRow.visibility = View.VISIBLE

        // Sort by severity (critical first)
        val sorted = allThreats.sortedByDescending { it.severity.ordinal }

        val threatCount = sorted.count {
            it.severity >= ThreatResult.Severity.MEDIUM
        }
        val cleanChecks = sorted.size - threatCount

        b.tvThreatCount.text = threatCount.toString()
        b.tvCleanCount.text = cleanChecks.coerceAtLeast(0).toString()

        if (sorted.isEmpty()) {
            b.tvStatus.text = getString(R.string.av_all_clear)
            b.tvStatus.setTextColor(ContextCompat.getColor(requireContext(), R.color.colorPrimary))
            b.recyclerResults.visibility = View.GONE
        } else {
            b.tvStatus.text = resources.getQuantityString(
                R.plurals.av_found_threats, sorted.size, sorted.size
            )
            b.recyclerResults.visibility = View.VISIBLE
            b.recyclerResults.adapter = ThreatAdapter(sorted)
        }
    }

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

            holder.name.text = item.name
            holder.description.text = item.description

            val sourceLabel = when (item.source) {
                ThreatResult.ScannerSource.APP_INTEGRITY -> ctx.getString(R.string.av_source_integrity)
                ThreatResult.ScannerSource.FILE_SIGNATURE -> ctx.getString(R.string.av_source_signature)
                ThreatResult.ScannerSource.PRIVACY_AUDIT -> ctx.getString(R.string.av_source_privacy)
            }
            holder.source.text = sourceLabel

            val (severityColor, severityLabel) = when (item.severity) {
                ThreatResult.Severity.CRITICAL -> R.color.colorError to ctx.getString(R.string.av_critical)
                ThreatResult.Severity.HIGH -> R.color.colorError to ctx.getString(R.string.av_high)
                ThreatResult.Severity.MEDIUM -> R.color.catVideo to ctx.getString(R.string.av_medium)
                ThreatResult.Severity.LOW -> R.color.colorAccent to ctx.getString(R.string.av_low)
                ThreatResult.Severity.INFO -> R.color.textTertiary to ctx.getString(R.string.av_info)
            }
            holder.severity.text = severityLabel
            holder.severity.setTextColor(ContextCompat.getColor(ctx, severityColor))
            holder.severityDot.backgroundTintList =
                android.content.res.ColorStateList.valueOf(ContextCompat.getColor(ctx, severityColor))

            // Action button
            when (item.action) {
                ThreatResult.ThreatAction.QUARANTINE -> {
                    holder.actionBtn.visibility = View.VISIBLE
                    holder.actionBtn.text = ctx.getString(R.string.av_quarantine)
                    holder.actionBtn.setOnClickListener {
                        val path = item.filePath ?: return@setOnClickListener
                        quarantineFile(path)
                        holder.actionBtn.isEnabled = false
                        holder.actionBtn.text = ctx.getString(R.string.av_quarantined)
                    }
                }
                ThreatResult.ThreatAction.DELETE -> {
                    holder.actionBtn.visibility = View.VISIBLE
                    holder.actionBtn.text = ctx.getString(R.string.delete)
                    holder.actionBtn.setOnClickListener {
                        val path = item.filePath ?: return@setOnClickListener
                        confirmDelete(path)
                    }
                }
                ThreatResult.ThreatAction.UNINSTALL -> {
                    holder.actionBtn.visibility = View.VISIBLE
                    holder.actionBtn.text = ctx.getString(R.string.av_uninstall)
                    holder.actionBtn.setOnClickListener {
                        val pkg = item.packageName ?: return@setOnClickListener
                        requestUninstall(pkg)
                    }
                }
                ThreatResult.ThreatAction.NONE -> {
                    holder.actionBtn.visibility = View.GONE
                }
            }
        }

        override fun getItemCount() = items.size
    }

    private fun quarantineFile(filePath: String) {
        val ctx = context ?: return
        val quarantineDir = File(
            ctx.getExternalFilesDir(null), ".quarantine"
        )
        quarantineDir.mkdirs()
        val src = File(filePath)
        val dst = File(quarantineDir, "${System.currentTimeMillis()}_${src.name}")
        if (src.renameTo(dst)) {
            val root = _binding?.root ?: return
            Snackbar.make(root,
                getString(R.string.av_quarantined_msg, src.name),
                Snackbar.LENGTH_SHORT).show()
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
                    val root = _binding?.root ?: return@setPositiveButton
                    Snackbar.make(root,
                        getString(R.string.av_deleted, file.name),
                        Snackbar.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    private fun requestUninstall(packageName: String) {
        try {
            val intent = Intent(Intent.ACTION_DELETE).apply {
                data = Uri.parse("package:$packageName")
            }
            startActivity(intent)
        } catch (e: Exception) {
            val root = _binding?.root ?: return
            Snackbar.make(root,
                getString(R.string.av_uninstall_failed),
                Snackbar.LENGTH_SHORT).show()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
