package com.filecleaner.app.ui.raccoon

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import com.filecleaner.app.MainActivity
import com.filecleaner.app.R
import com.filecleaner.app.databinding.FragmentRaccoonManagerBinding
import com.filecleaner.app.utils.MotionUtil
import com.filecleaner.app.utils.UndoHelper
import com.filecleaner.app.viewmodel.MainViewModel
import com.filecleaner.app.viewmodel.ScanPhase
import com.filecleaner.app.viewmodel.ScanState
import com.google.android.material.snackbar.Snackbar

/**
 * Raccoon Manager — central hub that replaces the floating raccoon bubble
 * and scan FAB. Provides quick access to Scan, Analysis, Quick Clean,
 * Arborescence (tree view), and Janitor (deep clean).
 */
class RaccoonManagerFragment : Fragment() {

    private var _binding: FragmentRaccoonManagerBinding? = null
    private var activeDialog: AlertDialog? = null
    private val binding get() = _binding!!
    private val vm: MainViewModel by activityViewModels()
    /** Tracks whether a scan was in progress so we only celebrate on Scanning -> Done transitions. */
    private var wasScanning = false

    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?): View {
        _binding = FragmentRaccoonManagerBinding.inflate(i, c, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // §DM2: Use centralized motion vocabulary NavOptions for consistent transitions
        val navAnimOptions = MotionUtil.navOptions()

        // Scan Storage — triggers permission check + scan
        binding.cardScan.setOnClickListener {
            (activity as? MainActivity)?.requestPermissionsAndScan()
        }

        // Analysis — navigate to storage dashboard
        binding.cardAnalysis.setOnClickListener {
            if (hasScanData()) {
                findNavController().navigate(R.id.dashboardFragment, null, navAnimOptions)
            } else {
                showScanNeeded()
            }
        }

        // Quick Clean — one-tap junk removal
        binding.cardQuickClean.setOnClickListener {
            if (!hasScanData()) {
                showScanNeeded()
                return@setOnClickListener
            }
            val junk = vm.junkFiles.value ?: emptyList()
            if (junk.isEmpty()) {
                Snackbar.make(binding.root, getString(R.string.raccoon_no_junk), Snackbar.LENGTH_SHORT)
                    .setBackgroundTint(androidx.core.content.ContextCompat.getColor(requireContext(), R.color.colorPrimaryContainer))
                    .setTextColor(androidx.core.content.ContextCompat.getColor(requireContext(), R.color.colorOnPrimaryContainer))
                    .show()
                return@setOnClickListener
            }
            val totalSize = UndoHelper.totalSize(junk)
            val undoSeconds = try { com.filecleaner.app.data.UserPreferences.undoTimeoutMs / 1000 } catch (_: Exception) { 8 }
            val detail = resources.getQuantityString(
                R.plurals.confirm_delete_detail,
                junk.size, junk.size, totalSize, undoSeconds)
            activeDialog?.dismiss()
            activeDialog = MaterialAlertDialogBuilder(requireContext())
                .setTitle(getString(R.string.raccoon_quick_clean_title))
                .setMessage(detail)
                .setPositiveButton(getString(R.string.clean)) { _, _ ->
                    vm.deleteFiles(junk)
                }
                .setNegativeButton(getString(R.string.cancel), null)
                .show()
        }

        // Arborescence — tree view
        binding.cardArborescence.setOnClickListener {
            if (hasScanData()) {
                findNavController().navigate(R.id.arborescenceFragment, null, navAnimOptions)
            } else {
                showScanNeeded()
            }
        }

        // Optimize Storage — rule-based file organization
        binding.cardOptimize.setOnClickListener {
            if (hasScanData()) {
                findNavController().navigate(R.id.optimizeFragment, null, navAnimOptions)
            } else {
                showScanNeeded()
            }
        }

        // Dual Pane — side-by-side file manager (no scan required)
        binding.cardDualPane.setOnClickListener {
            findNavController().navigate(R.id.dualPaneFragment, null, navAnimOptions)
        }

        // Cloud / Network — remote file browsing (no scan required)
        binding.cardCloud.setOnClickListener {
            findNavController().navigate(R.id.cloudBrowserFragment, null, navAnimOptions)
        }

        // Antivirus — hybrid security scanner (no scan required for app integrity/privacy)
        binding.cardAntivirus.setOnClickListener {
            findNavController().navigate(R.id.antivirusFragment, null, navAnimOptions)
        }

        // Settings — app preferences & configuration
        binding.cardSettings.setOnClickListener {
            findNavController().navigate(R.id.settingsFragment, null, navAnimOptions)
        }

        // Janitor — deep clean (navigate to duplicates tab for comprehensive review)
        binding.cardJanitor.setOnClickListener {
            if (!hasScanData()) {
                showScanNeeded()
                return@setOnClickListener
            }
            showJanitorDialog()
        }

        // Update subtitle and card states based on scan state
        vm.scanState.observe(viewLifecycleOwner) { state ->
            val hasData = state is ScanState.Done
            val isScanning = state is ScanState.Scanning
            binding.progressScan.visibility = if (isScanning) View.VISIBLE else View.GONE
            // DST4 / E6: Raccoon celebration bounce when scan finishes
            if (hasData && wasScanning && !MotionUtil.isReducedMotion(requireContext())) {
                val raccoonView = binding.ivRaccoonAvatar
                raccoonView.animate()
                    .scaleX(1.15f).scaleY(1.15f)
                    .setDuration(resources.getInteger(R.integer.motion_emphasis).toLong() / 2)
                    .setInterpolator(android.view.animation.OvershootInterpolator(2f))
                    .withEndAction {
                        raccoonView.animate()
                            .scaleX(1f).scaleY(1f)
                            .setDuration(resources.getInteger(R.integer.motion_emphasis).toLong() / 2)
                            .start()
                    }
                    .start()
            }
            wasScanning = isScanning
            binding.tvSubtitle.text = when (state) {
                is ScanState.Done -> {
                    val stats = vm.storageStats.value
                    if (stats != null) {
                        getString(R.string.scan_summary,
                            stats.totalFiles,
                            UndoHelper.formatBytes(stats.totalSize),
                            vm.duplicates.value?.size ?: 0,
                            vm.junkFiles.value?.size ?: 0,
                            vm.largeFiles.value?.size ?: 0)
                    } else getString(R.string.raccoon_manager_subtitle)
                }
                is ScanState.Scanning -> when (state.phase) {
                    ScanPhase.INDEXING -> getString(R.string.scanning_phase_indexing, state.filesFound)
                    ScanPhase.DUPLICATES -> getString(R.string.scanning_phase_duplicates, state.filesFound)
                    ScanPhase.ANALYZING -> getString(R.string.scanning_phase_analyzing, state.filesFound)
                    ScanPhase.JUNK -> getString(R.string.scanning_phase_junk, state.filesFound)
                }
                else -> getString(R.string.raccoon_greeting_pre_scan)
            }
            // F-059: Dim cards that require scan data; raised from 0.5 to 0.6 for better readability
            val alpha = if (hasData || isScanning) 1.0f else 0.6f
            binding.cardAnalysis.alpha = alpha
            binding.cardQuickClean.alpha = alpha
            binding.cardArborescence.alpha = alpha
            binding.cardOptimize.alpha = alpha
            binding.cardJanitor.alpha = alpha
            // Show scan phase on card descriptions during active scan
            val scanPhaseText = if (state is ScanState.Scanning) when (state.phase) {
                ScanPhase.INDEXING -> getString(R.string.scanning_phase_indexing, state.filesFound)
                ScanPhase.DUPLICATES -> getString(R.string.scanning_phase_duplicates, state.filesFound)
                ScanPhase.ANALYZING -> getString(R.string.scanning_phase_analyzing, state.filesFound)
                ScanPhase.JUNK -> getString(R.string.scanning_phase_junk, state.filesFound)
            } else null
            binding.tvAnalysisDesc.text = when {
                hasData -> getString(R.string.raccoon_action_analysis_desc)
                scanPhaseText != null -> scanPhaseText
                else -> getString(R.string.raccoon_scan_needed)
            }
            binding.tvQuickCleanDesc.text = when {
                hasData -> getString(R.string.raccoon_action_quick_clean_desc)
                scanPhaseText != null -> scanPhaseText
                else -> getString(R.string.raccoon_scan_needed)
            }
            binding.tvJanitorDesc.text = when {
                hasData -> getString(R.string.raccoon_action_janitor_desc)
                scanPhaseText != null -> scanPhaseText
                else -> getString(R.string.raccoon_scan_needed)
            }
        }

        // Observe delete result for undo snackbar
        vm.deleteResult.observe(viewLifecycleOwner) { result ->
            UndoHelper.showUndoSnackbar(binding.root, result, vm)
        }
    }

    private fun hasScanData(): Boolean = vm.scanState.value is ScanState.Done

    private fun showScanNeeded() {
        Snackbar.make(binding.root, getString(R.string.raccoon_scan_needed), Snackbar.LENGTH_SHORT)
            .apply { activity?.findViewById<View>(R.id.bottom_nav)?.let { anchorView = it } }
            .show()
    }

    private fun showJanitorDialog() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(R.string.raccoon_janitor_title))
            .setMessage(getString(R.string.raccoon_janitor_desc))
            .setPositiveButton(getString(R.string.raccoon_janitor_start)) { _, _ ->
                // F-057: Deep clean starts a fresh scan, then shows review dialog on completion
                (activity as? MainActivity)?.requestPermissionsAndScan()
                Snackbar.make(binding.root,
                    getString(R.string.raccoon_janitor_started),
                    Snackbar.LENGTH_LONG).show()
                // Observe scan completion to show review guidance
                vm.scanState.observe(viewLifecycleOwner) { state ->
                    if (state is ScanState.Done) {
                        vm.scanState.removeObservers(viewLifecycleOwner)
                        showJanitorReviewDialog()
                    }
                }
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    /** F-057: Post-scan dialog guiding user to review tabs */
    private fun showJanitorReviewDialog() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(R.string.raccoon_janitor_done_title))
            .setMessage(getString(R.string.raccoon_janitor_done_message))
            .setPositiveButton(getString(R.string.raccoon_janitor_review_duplicates)) { _, _ ->
                (activity as? MainActivity)?.let {
                    it.findViewById<com.google.android.material.bottomnavigation.BottomNavigationView>(R.id.bottom_nav)
                        ?.selectedItemId = R.id.duplicatesFragment
                }
            }
            .setNeutralButton(getString(R.string.raccoon_janitor_review_junk)) { _, _ ->
                (activity as? MainActivity)?.let {
                    it.findViewById<com.google.android.material.bottomnavigation.BottomNavigationView>(R.id.bottom_nav)
                        ?.selectedItemId = R.id.junkFragment
                }
            }
            .setNegativeButton(getString(R.string.dismiss), null)
            .show()
    }

    override fun onDestroyView() {
        activeDialog?.dismiss()
        activeDialog = null
        super.onDestroyView()
        _binding = null
    }
}
