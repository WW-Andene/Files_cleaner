package com.filecleaner.app.ui.raccoon

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import com.filecleaner.app.MainActivity
import com.filecleaner.app.R
import com.filecleaner.app.databinding.FragmentRaccoonManagerBinding
import com.filecleaner.app.utils.UndoHelper
import com.filecleaner.app.viewmodel.MainViewModel
import com.filecleaner.app.viewmodel.ScanState
import com.google.android.material.snackbar.Snackbar

/**
 * Raccoon Manager — central hub that replaces the floating raccoon bubble
 * and scan FAB. Provides quick access to Scan, Analysis, Quick Clean,
 * Arborescence (tree view), and Janitor (deep clean).
 */
class RaccoonManagerFragment : Fragment() {

    private var _binding: FragmentRaccoonManagerBinding? = null
    private val binding get() = _binding!!
    private val vm: MainViewModel by activityViewModels()

    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?): View {
        _binding = FragmentRaccoonManagerBinding.inflate(i, c, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Scan Storage — triggers permission check + scan
        binding.cardScan.setOnClickListener {
            (activity as? MainActivity)?.requestPermissionsAndScan()
        }

        // Analysis — navigate to storage dashboard
        binding.cardAnalysis.setOnClickListener {
            if (hasScanData()) {
                findNavController().navigate(R.id.dashboardFragment)
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
                Snackbar.make(binding.root, getString(R.string.raccoon_no_junk), Snackbar.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val totalSize = UndoHelper.totalSize(junk)
            AlertDialog.Builder(requireContext())
                .setTitle(getString(R.string.raccoon_quick_clean_title))
                .setMessage(resources.getQuantityString(
                    R.plurals.raccoon_quick_clean_confirm,
                    junk.size, junk.size, totalSize))
                .setPositiveButton(getString(R.string.clean)) { _, _ ->
                    vm.deleteFiles(junk)
                }
                .setNegativeButton(getString(R.string.cancel), null)
                .show()
        }

        // Arborescence — tree view
        binding.cardArborescence.setOnClickListener {
            if (hasScanData()) {
                findNavController().navigate(R.id.arborescenceFragment)
            } else {
                showScanNeeded()
            }
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
                is ScanState.Scanning -> getString(R.string.scanning_phase_indexing, state.filesFound)
                else -> getString(R.string.raccoon_manager_subtitle)
            }
            // F5: Dim cards that require scan data when none is available
            val alpha = if (hasData) 1.0f else 0.5f
            binding.cardAnalysis.alpha = alpha
            binding.cardQuickClean.alpha = alpha
            binding.cardArborescence.alpha = alpha
            binding.cardJanitor.alpha = alpha
        }

        // Observe delete result for undo snackbar
        vm.deleteResult.observe(viewLifecycleOwner) { result ->
            UndoHelper.showUndoSnackbar(binding.root, result, vm)
        }
    }

    private fun hasScanData(): Boolean = vm.scanState.value is ScanState.Done

    private fun showScanNeeded() {
        Snackbar.make(binding.root, getString(R.string.raccoon_scan_needed), Snackbar.LENGTH_SHORT).show()
    }

    private fun showJanitorDialog() {
        AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.raccoon_janitor_title))
            .setMessage(getString(R.string.raccoon_janitor_desc))
            .setPositiveButton(getString(R.string.raccoon_janitor_start)) { _, _ ->
                // Deep clean starts a fresh scan then navigates to duplicates for review
                (activity as? MainActivity)?.requestPermissionsAndScan()
                // After scan completes, user can review duplicates/junk/large tabs
                Snackbar.make(binding.root,
                    getString(R.string.raccoon_janitor_started),
                    Snackbar.LENGTH_LONG).show()
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
