package com.filecleaner.app.ui.dashboard

import android.os.Bundle
import android.os.StatFs
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.filecleaner.app.R
import com.filecleaner.app.data.FileCategory
import com.filecleaner.app.databinding.FragmentDashboardBinding
import com.filecleaner.app.utils.UndoHelper
import com.filecleaner.app.viewmodel.MainViewModel

/**
 * Storage dashboard showing device storage summary and category breakdown (P2).
 */
class StorageDashboardFragment : Fragment() {

    private var _binding: FragmentDashboardBinding? = null
    private val binding get() = _binding!!
    private val vm: MainViewModel by activityViewModels()

    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?): View {
        _binding = FragmentDashboardBinding.inflate(i, c, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Device storage via StatFs
        @Suppress("DEPRECATION")
        val statFs = StatFs(android.os.Environment.getExternalStorageDirectory().absolutePath)
        val totalBytes = statFs.totalBytes
        val freeBytes = statFs.freeBytes
        val usedBytes = totalBytes - freeBytes
        val usedPct = if (totalBytes > 0) ((usedBytes * 100.0) / totalBytes).toInt() else 0

        binding.tvStorageTitle.text = getString(R.string.dashboard_storage_title)
        binding.tvStorageUsed.text = getString(R.string.dashboard_storage_used,
            UndoHelper.formatBytes(usedBytes), UndoHelper.formatBytes(totalBytes), usedPct)
        binding.tvStorageFree.text = getString(R.string.dashboard_storage_free,
            UndoHelper.formatBytes(freeBytes))
        binding.progressStorage.max = 100
        binding.progressStorage.progress = usedPct

        // Category breakdown from scan data
        vm.filesByCategory.observe(viewLifecycleOwner) { catMap ->
            val entries = catMap.entries.sortedByDescending { it.value.sumOf { f -> f.size } }
            val lines = entries.joinToString("\n") { (cat, files) ->
                val totalSize = files.sumOf { it.size }
                "${cat.emoji} ${getString(cat.displayNameRes)}: ${files.size} files (${UndoHelper.formatBytes(totalSize)})"
            }
            binding.tvCategoryBreakdown.text = lines.ifEmpty { getString(R.string.dashboard_no_scan) }
        }

        // Scan stats
        vm.storageStats.observe(viewLifecycleOwner) { stats ->
            if (stats != null) {
                binding.tvStatsDetail.text = getString(R.string.dashboard_stats_detail,
                    stats.totalFiles,
                    UndoHelper.formatBytes(stats.duplicateSize),
                    UndoHelper.formatBytes(stats.junkSize),
                    UndoHelper.formatBytes(stats.largeSize))
                binding.tvStatsDetail.visibility = View.VISIBLE
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
