package com.filecleaner.app.ui.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.SeekBar
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import com.filecleaner.app.R
import com.filecleaner.app.data.UserPreferences
import com.filecleaner.app.data.cloud.CloudConnectionStore
import com.filecleaner.app.databinding.FragmentSettingsBinding
import com.filecleaner.app.utils.CrashReporter
import com.google.android.material.snackbar.Snackbar
import java.io.File

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

        // Theme toggle (System / Light / Dark)
        when (UserPreferences.themeMode) {
            1 -> binding.rbThemeLight.isChecked = true
            2 -> binding.rbThemeDark.isChecked = true
            else -> binding.rbThemeSystem.isChecked = true
        }
        binding.rgTheme.setOnCheckedChangeListener { _, checkedId ->
            val mode = when (checkedId) {
                R.id.rb_theme_light -> 1
                R.id.rb_theme_dark -> 2
                else -> 0
            }
            UserPreferences.themeMode = mode
            AppCompatDelegate.setDefaultNightMode(
                when (mode) {
                    1 -> AppCompatDelegate.MODE_NIGHT_NO
                    2 -> AppCompatDelegate.MODE_NIGHT_YES
                    else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
                }
            )
        }

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
        // Round to nearest step to avoid lossy round-trip (e.g., 30 → progress 2 → 27)
        binding.seekStaleAge.progress = ((UserPreferences.staleDownloadDays - 7 + 5) / 10).coerceIn(0, 35)
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
        binding.seekUndoTimeout.progress = ((UserPreferences.undoTimeoutMs / 1000) - 3).coerceIn(0, 27)
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

        // ── Crash Reporting ──
        binding.switchCrashReporting.isChecked = UserPreferences.crashReportingEnabled
        binding.switchCrashReporting.setOnCheckedChangeListener { _, isChecked ->
            UserPreferences.crashReportingEnabled = isChecked
            updateCrashFieldsVisibility()
        }

        binding.etGithubToken.setText(UserPreferences.crashReportGithubToken)
        binding.etGithubToken.doAfterTextChanged { text ->
            UserPreferences.crashReportGithubToken = text?.toString()?.trim() ?: ""
        }

        binding.etGithubRepo.setText(UserPreferences.crashReportRepo)
        binding.etGithubRepo.doAfterTextChanged { text ->
            val repo = text?.toString()?.trim() ?: ""
            if (repo.isNotEmpty()) UserPreferences.crashReportRepo = repo
        }

        updateCrashFieldsVisibility()
        updatePendingReportsLabel()

        // Note: Settings take effect on next scan
        binding.tvSettingsNote.text = getString(R.string.settings_rescan_note)

        // Clear All Data (P3 Security: GDPR data erasure)
        // Avoid duplicate button on configuration change by checking for existing tagged button
        if (binding.settingsContainer.findViewWithTag<View>(TAG_CLEAR_BUTTON) == null) {
            val clearButton = com.google.android.material.button.MaterialButton(requireContext()).apply {
                tag = TAG_CLEAR_BUTTON
                text = getString(R.string.settings_clear_data)
                setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.colorError))
                setTextColor(ContextCompat.getColor(requireContext(), R.color.textOnPrimary))
                val lp = android.widget.LinearLayout.LayoutParams(
                    android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                    android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
                )
                val margin = resources.getDimensionPixelSize(R.dimen.spacing_lg)
                lp.setMargins(margin, margin * 2, margin, margin)
                layoutParams = lp
            }
            clearButton.setOnClickListener {
                val ctx = context ?: return@setOnClickListener
                MaterialAlertDialogBuilder(ctx)
                    .setTitle(getString(R.string.settings_clear_data_confirm_title))
                    .setMessage(getString(R.string.settings_clear_data_confirm_message))
                    .setPositiveButton(getString(R.string.delete)) { _, _ ->
                        clearAllData()
                    }
                    .setNegativeButton(getString(R.string.cancel), null)
                    .show()
            }
            binding.settingsContainer.addView(clearButton)
        }
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

    private fun updateCrashFieldsVisibility() {
        val visible = if (UserPreferences.crashReportingEnabled) View.VISIBLE else View.GONE
        binding.tilGithubToken.visibility = visible
        binding.tilGithubRepo.visibility = visible
    }

    private fun updatePendingReportsLabel() {
        val count = CrashReporter.pendingReportCount()
        if (count > 0) {
            binding.tvCrashPending.visibility = View.VISIBLE
            binding.tvCrashPending.text = getString(R.string.settings_crash_pending, count)
        } else {
            binding.tvCrashPending.visibility = View.GONE
        }
    }

    companion object {
        private const val TAG_CLEAR_BUTTON = "clear_all_data_button"
    }

    private fun clearAllData() {
        val ctx = context ?: return
        // Clear scan cache
        File(ctx.filesDir, "scan_cache.json").delete()
        File(ctx.filesDir, "scan_cache.json.tmp").delete()
        // Clear SFTP known hosts
        File(ctx.filesDir, "sftp_known_hosts").delete()
        // Clear cloud connections
        CloudConnectionStore.init(ctx)
        for (conn in CloudConnectionStore.getConnections()) {
            CloudConnectionStore.removeConnection(conn.id)
        }
        // Clear scan history
        ctx.getSharedPreferences("av_scan_history", android.content.Context.MODE_PRIVATE)
            .edit().clear().apply()
        // Reset user preferences
        ctx.getSharedPreferences("raccoon_prefs", android.content.Context.MODE_PRIVATE)
            .edit().clear().apply()
        // Clear pending crash reports
        CrashReporter.clearPendingReports()
        // Clear encrypted cloud prefs
        try {
            ctx.getSharedPreferences("cloud_connections", android.content.Context.MODE_PRIVATE)
                .edit().clear().apply()
            ctx.getSharedPreferences("cloud_connections_plain", android.content.Context.MODE_PRIVATE)
                .edit().clear().apply()
        } catch (_: Exception) {}

        Snackbar.make(binding.root, getString(R.string.settings_clear_data_done), Snackbar.LENGTH_LONG).show()
    }

    // B5: Remove listeners to prevent callbacks on destroyed binding
    override fun onDestroyView() {
        binding.rgTheme.setOnCheckedChangeListener(null)
        binding.seekLargeFile.setOnSeekBarChangeListener(null)
        binding.seekStaleAge.setOnSeekBarChangeListener(null)
        binding.seekUndoTimeout.setOnSeekBarChangeListener(null)
        binding.switchHiddenFiles.setOnCheckedChangeListener(null)
        binding.switchCrashReporting.setOnCheckedChangeListener(null)
        super.onDestroyView()
        _binding = null
    }
}
