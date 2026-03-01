package com.filecleaner.app.ui.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.SeekBar
import androidx.fragment.app.Fragment
import com.filecleaner.app.R
import com.filecleaner.app.data.UserPreferences
import com.filecleaner.app.databinding.FragmentSettingsBinding

/**
 * Settings screen (P14) — configurable thresholds and display preferences.
 */
class SettingsFragment : Fragment() {

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?): View {
        _binding = FragmentSettingsBinding.inflate(i, c, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Large file threshold (10–500 MB)
        binding.seekLargeFile.max = 49 // 10 to 500 in steps of 10
        binding.seekLargeFile.progress = (UserPreferences.largeFileThresholdMb - 10) / 10
        updateLargeFileLabel()
        binding.seekLargeFile.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    UserPreferences.largeFileThresholdMb = (progress * 10) + 10
                    updateLargeFileLabel()
                }
            }
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        })

        // Stale download age (7–365 days)
        binding.seekStaleAge.max = 35 // 7 to 365 in steps of ~10
        binding.seekStaleAge.progress = (UserPreferences.staleDownloadDays - 7) / 10
        updateStaleAgeLabel()
        binding.seekStaleAge.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    UserPreferences.staleDownloadDays = (progress * 10) + 7
                    updateStaleAgeLabel()
                }
            }
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        })

        // Undo timeout (3–30 seconds)
        binding.seekUndoTimeout.max = 27 // 3 to 30
        binding.seekUndoTimeout.progress = (UserPreferences.undoTimeoutMs / 1000) - 3
        updateUndoLabel()
        binding.seekUndoTimeout.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    UserPreferences.undoTimeoutMs = (progress + 3) * 1000
                    updateUndoLabel()
                }
            }
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        })

        // Show hidden files toggle
        binding.switchHiddenFiles.isChecked = UserPreferences.showHiddenFiles
        binding.switchHiddenFiles.setOnCheckedChangeListener { _, isChecked ->
            UserPreferences.showHiddenFiles = isChecked
        }

        // Note: Settings take effect on next scan
        binding.tvSettingsNote.text = getString(R.string.settings_rescan_note)
    }

    private fun updateLargeFileLabel() {
        binding.tvLargeFileValue.text = getString(R.string.settings_large_file_value, UserPreferences.largeFileThresholdMb)
    }

    private fun updateStaleAgeLabel() {
        binding.tvStaleAgeValue.text = getString(R.string.settings_stale_age_value, UserPreferences.staleDownloadDays)
    }

    private fun updateUndoLabel() {
        binding.tvUndoValue.text = getString(R.string.settings_undo_value, UserPreferences.undoTimeoutMs / 1000)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
