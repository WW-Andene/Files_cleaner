package com.filecleaner.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.view.KeyEvent
import android.view.View
import android.view.accessibility.AccessibilityEvent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.view.WindowCompat
import androidx.navigation.findNavController
import androidx.core.content.ContextCompat
import androidx.navigation.NavOptions
import androidx.navigation.fragment.NavHostFragment
import com.filecleaner.app.utils.MotionUtil
import com.filecleaner.app.data.UserPreferences
import com.filecleaner.app.data.cloud.OAuthHelper
import com.filecleaner.app.databinding.ActivityMainBinding
import com.filecleaner.app.ui.cloud.CloudSetupDialog
import com.filecleaner.app.ui.onboarding.OnboardingDialog
import com.filecleaner.app.utils.UndoHelper
import com.filecleaner.app.utils.styleAsError
import com.filecleaner.app.viewmodel.MainViewModel
import com.filecleaner.app.viewmodel.ScanPhase
import com.filecleaner.app.viewmodel.ScanState
import com.google.android.material.snackbar.Snackbar
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    // Internal access: fragments use activityViewModels() to access the shared ViewModel
    internal val viewModel: MainViewModel by viewModels()

    // Permissions request launcher
    private val permLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        if (grants.values.all { it }) startScan()
        else showPermissionDeniedDialog()
    }

    // Manage all files (Android 11+) settings launcher
    private val manageFilesLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        if (hasAllStoragePermission()) startScan()
        else showPermissionDeniedDialog()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        // UserPreferences and CloudConnectionStore are initialized in FileCleanerApp

        // Apply saved theme before inflating views
        AppCompatDelegate.setDefaultNightMode(
            when (UserPreferences.themeMode) {
                1 -> AppCompatDelegate.MODE_NIGHT_NO
                2 -> AppCompatDelegate.MODE_NIGHT_YES
                else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
            }
        )

        WindowCompat.setDecorFitsSystemWindows(window, false)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Show version number in header
        binding.tvVersion.text = getString(R.string.version_format, BuildConfig.VERSION_NAME)

        // Navigation setup
        val navHost = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment) as? NavHostFragment ?: return
        val navController = navHost.navController

        // All bottom nav tab destination IDs (including the raccoon manager hub)
        val bottomNavIds = setOf(
            R.id.browseFragment, R.id.duplicatesFragment,
            R.id.raccoonManagerFragment,
            R.id.largeFilesFragment, R.id.junkFragment
        )

        // Map menu item IDs to nav destination IDs
        val menuToNav = mapOf(
            R.id.browseFragment to R.id.browseFragment,
            R.id.duplicatesFragment to R.id.duplicatesFragment,
            R.id.raccoonManagerFragment to R.id.raccoonManagerFragment,
            R.id.largeFilesFragment to R.id.largeFilesFragment,
            R.id.junkFragment to R.id.junkFragment
        )

        // Manual bottom nav handling — pops any non-tab fragment before navigating
        binding.bottomNav.setOnItemSelectedListener { item ->
            val destId = menuToNav[item.itemId] ?: return@setOnItemSelectedListener false
            val currentDest = navController.currentDestination?.id
            // Guard: skip navigation if already on this destination (prevents back stack duplication on rapid taps)
            if (destId == currentDest) return@setOnItemSelectedListener true
            // Pop non-tab fragments (arborescence, settings, dashboard) from back stack
            if (currentDest != null && currentDest !in bottomNavIds) {
                navController.popBackStack(currentDest, true)
            }
            val options = NavOptions.Builder()
                .setPopUpTo(R.id.raccoonManagerFragment, inclusive = false, saveState = true)
                .setLaunchSingleTop(true)
                .setRestoreState(true)
                .setEnterAnim(R.anim.nav_enter)
                .setExitAnim(R.anim.nav_exit)
                .setPopEnterAnim(R.anim.nav_pop_enter)
                .setPopExitAnim(R.anim.nav_pop_exit)
                .build()
            navController.navigate(destId, null, options)
            true
        }
        // Reselect: if on a non-tab fragment (e.g. tree view), pop back to the tab
        binding.bottomNav.setOnItemReselectedListener { item ->
            val currentDest = navController.currentDestination?.id
            if (currentDest != null && currentDest !in bottomNavIds) {
                navController.popBackStack(currentDest, true)
                val destId = menuToNav[item.itemId] ?: return@setOnItemReselectedListener
                val options = NavOptions.Builder()
                    .setPopUpTo(R.id.raccoonManagerFragment, inclusive = false, saveState = true)
                    .setLaunchSingleTop(true)
                    .setRestoreState(true)
                    .setEnterAnim(R.anim.nav_enter)
                    .setExitAnim(R.anim.nav_exit)
                    .setPopEnterAnim(R.anim.nav_pop_enter)
                    .setPopExitAnim(R.anim.nav_pop_exit)
                    .build()
                navController.navigate(destId, null, options)
            }
        }

        // F1/F3: Select Manager tab on first launch to match startDestination
        if (savedInstanceState == null) {
            binding.bottomNav.selectedItemId = R.id.raccoonManagerFragment
        }

        // Keep bottom nav selection in sync with current destination and toggle visibility
        val bottomNavWrapper = binding.bottomNav.parent as View
        navController.addOnDestinationChangedListener { _, dest, _ ->
            val isTabDest = dest.id in bottomNavIds
            if (isTabDest) {
                binding.bottomNav.menu.findItem(dest.id)?.isChecked = true
                // §G1: Announce tab change to TalkBack
                val tabLabel = dest.label
                if (tabLabel != null) {
                    binding.root.announceForAccessibility(tabLabel)
                }
            }

            // Show/hide bottom nav with slide animation
            val navHostFragment = binding.navHostFragment
            if (isTabDest && bottomNavWrapper.visibility != View.VISIBLE) {
                bottomNavWrapper.visibility = View.VISIBLE
                bottomNavWrapper.translationY = bottomNavWrapper.height.toFloat()
                bottomNavWrapper.animate()
                    .translationY(0f)
                    .setDuration(150)
                    .start()
                navHostFragment.setPadding(0, 0, 0,
                    resources.getDimensionPixelSize(R.dimen.bottom_nav_height))
            } else if (!isTabDest && bottomNavWrapper.visibility == View.VISIBLE) {
                bottomNavWrapper.animate()
                    .translationY(bottomNavWrapper.height.toFloat())
                    .setDuration(150)
                    .withEndAction { bottomNavWrapper.visibility = View.GONE }
                    .start()
                navHostFragment.setPadding(0, 0, 0, 0)
            }
        }

        // §DM2: Use centralized motion vocabulary NavOptions for consistent page transitions
        val navAnimOptions = MotionUtil.navOptions()

        // Navigate to tree on "Show in Tree"
        viewModel.navigateToTree.observe(this) { filePath ->
            if (filePath != null) {
                val currentDest = navController.currentDestination?.id
                if (currentDest != R.id.arborescenceFragment) {
                    navController.navigate(R.id.arborescenceFragment, null, navAnimOptions)
                }
            }
        }

        // Navigate to Browse tab on "Browse folder"
        viewModel.navigateToBrowse.observe(this) { folderPath ->
            if (folderPath != null) {
                val currentDest = navController.currentDestination?.id
                if (currentDest != R.id.browseFragment) {
                    binding.bottomNav.selectedItemId = R.id.browseFragment
                }
            }
        }

        // §G1: Make scan status announce updates to TalkBack
        binding.tvScanStatus.accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE

        // Cancel scan button
        binding.btnCancelScan.setOnClickListener { viewModel.cancelScan() }

        // Scan state observer — drives the Material LinearProgressIndicator and phase labels
        viewModel.scanState.observe(this) { state ->
            when (state) {
                is ScanState.Idle     -> {
                    hideScanProgress()
                    binding.tvScanStatus.text = getString(R.string.scan_prompt)
                    // F3: Make scan bar tappable to start scan
                    binding.tvScanStatus.setOnClickListener {
                        requestPermissionsAndScan()
                    }
                }
                is ScanState.Scanning -> {
                    showScanProgress(state)
                }
                is ScanState.Done     -> {
                    hideScanProgress()
                    // §G1: Announce scan completion to accessibility services
                    binding.tvScanStatus.sendAccessibilityEvent(AccessibilityEvent.TYPE_ANNOUNCEMENT)
                    val stats = viewModel.storageStats.value
                    if (stats != null) {
                        val durationText = if (stats.scanDurationMs > 0) {
                            getString(R.string.scan_duration_suffix, stats.scanDurationMs / 1000.0)
                        } else ""
                        binding.tvScanStatus.text = resources.getQuantityString(
                            R.plurals.scan_complete,
                            stats.totalFiles,
                            stats.totalFiles,
                            UndoHelper.formatBytes(stats.totalSize)
                        ) + durationText
                        Snackbar.make(
                            binding.root,
                            getString(R.string.scan_summary,
                                stats.totalFiles,
                                UndoHelper.formatBytes(stats.totalSize),
                                viewModel.duplicates.value?.size ?: 0,
                                viewModel.junkFiles.value?.size ?: 0,
                                viewModel.largeFiles.value?.size ?: 0
                            ),
                            Snackbar.LENGTH_LONG
                        ).show()
                        // Make scan status tappable to open dashboard
                        binding.tvScanStatus.setOnClickListener {
                            findNavController(R.id.nav_host_fragment).navigate(R.id.dashboardFragment, null, navAnimOptions)
                        }
                    } else {
                        binding.tvScanStatus.text = getString(R.string.scan_complete_simple)
                    }
                }
                is ScanState.Cancelled -> {
                    hideScanProgress()
                    binding.tvScanStatus.text = getString(R.string.scan_cancelled)
                }
                is ScanState.Error    -> {
                    hideScanProgress()
                    binding.tvScanStatus.text = getString(R.string.error_scan_failed)
                    Snackbar.make(binding.root,
                        getString(R.string.error_prefix, state.message),
                        Snackbar.LENGTH_LONG).styleAsError().show()
                }
            }
        }

        // Bottom nav badges — show count of actionable items per tab
        viewModel.duplicates.observe(this) { dupes ->
            updateBadge(R.id.duplicatesFragment, dupes.size)
        }
        viewModel.largeFiles.observe(this) { large ->
            updateBadge(R.id.largeFilesFragment, large.size)
        }
        viewModel.junkFiles.observe(this) { junk ->
            updateBadge(R.id.junkFragment, junk.size)
        }

        // Settings button
        binding.btnSettings.setOnClickListener {
            findNavController(R.id.nav_host_fragment).navigate(R.id.settingsFragment, null, navAnimOptions)
        }

        // First-launch onboarding
        OnboardingDialog.showIfNeeded(this)

        // P3 Security: Privacy disclosure on first launch (F-C6-01)
        // Only show on fresh launch (not rotation) to prevent duplicate dialogs
        if (savedInstanceState == null && !UserPreferences.hasSeenPrivacyNotice) {
            MaterialAlertDialogBuilder(this)
                .setTitle(getString(R.string.privacy_notice_title))
                .setMessage(getString(R.string.privacy_notice_message))
                .setCancelable(false)
                .setPositiveButton(getString(R.string.privacy_notice_accept)) { _, _ ->
                    UserPreferences.hasSeenPrivacyNotice = true
                }
                .show()
        }

        // Handle OAuth callback
        handleOAuthIntent(intent)
    }

    // ── Keyboard shortcuts ──

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (event == null) return super.onKeyDown(keyCode, event)
        val isCtrl = event.isCtrlPressed

        if (isCtrl) {
            when (keyCode) {
                // Ctrl+S → Navigate to Settings
                KeyEvent.KEYCODE_S -> {
                    val navController = findNavController(R.id.nav_host_fragment)
                    if (navController.currentDestination?.id != R.id.settingsFragment) {
                        navController.navigate(R.id.settingsFragment, null, MotionUtil.navOptions())
                    }
                    return true
                }
                // Ctrl+F → Navigate to Browse tab and focus the search field
                KeyEvent.KEYCODE_F -> {
                    binding.bottomNav.selectedItemId = R.id.browseFragment
                    // Post to allow fragment transaction to complete before requesting focus
                    binding.root.post {
                        val searchField = findViewById<View>(R.id.et_search)
                        searchField?.requestFocus()
                    }
                    return true
                }
            }
        }

        return super.onKeyDown(keyCode, event)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleOAuthIntent(intent)
    }

    private fun handleOAuthIntent(intent: Intent?) {
        val uri = intent?.data ?: return
        val code = OAuthHelper.parseCallbackCode(uri) ?: return

        // Try the setup dialog callback first (dialog is still open).
        // If it returns true, the setup dialog handled the code exchange itself.
        if (CloudSetupDialog.handleOAuthCallback(code)) {
            return
        }

        // No setup dialog was waiting. This was launched from the provider
        // picker dialog which bypassed the setup dialog. Exchange the code here
        // and create the connection directly.
        val pendingProvider = OAuthHelper.getPendingProvider()
        if (pendingProvider != null) {
            lifecycleScope.launch {
                val result = OAuthHelper.exchangeCodeForToken(this@MainActivity, code, pendingProvider)
                if (result.isSuccess) {
                    CloudSetupDialog.showOAuthResult(
                        this@MainActivity,
                        pendingProvider,
                        result.accessToken
                    ) { _ ->
                        Snackbar.make(
                            binding.root,
                            getString(R.string.cloud_oauth_success),
                            Snackbar.LENGTH_SHORT
                        ).show()
                    }
                } else {
                    Snackbar.make(
                        binding.root,
                        getString(R.string.cloud_oauth_failed, result.error),
                        Snackbar.LENGTH_LONG
                    ).styleAsError().show()
                }
            }
        }
    }

    fun requestPermissionsAndScan() {
        // minSdk 29 (Android 10). Android 11+ (R) uses MANAGE_EXTERNAL_STORAGE;
        // Android 10 falls through to legacy READ/WRITE_EXTERNAL_STORAGE.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Android 11+ needs MANAGE_EXTERNAL_STORAGE
            if (Environment.isExternalStorageManager()) {
                startScan()
            } else {
                MaterialAlertDialogBuilder(this)
                    .setTitle(getString(R.string.storage_access_needed))
                    .setMessage(getString(R.string.storage_access_message))
                    .setPositiveButton(getString(R.string.open_settings)) { _, _ ->
                        manageFilesLauncher.launch(
                            Intent(
                                Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                                Uri.parse("package:$packageName")
                            )
                        )
                    }
                    .setNegativeButton(getString(R.string.cancel), null)
                    .show()
            }
        } else {
            // Android 10 (API 29)
            val needed = arrayOf(
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            ).filter {
                ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
            }.toTypedArray()

            if (needed.isEmpty()) startScan() else permLauncher.launch(needed)
        }
    }

    private fun startScan() {
        viewModel.startScan()
    }

    private fun hasAllStoragePermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
            Environment.isExternalStorageManager()
        else
            ContextCompat.checkSelfPermission(
                this, Manifest.permission.READ_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED
    }

    private fun showPermissionDeniedDialog() {
        MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.permission_required))
            .setMessage(getString(R.string.permission_required_message))
            .setPositiveButton(getString(R.string.settings)) { _, _ ->
                startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:$packageName")))
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    // ── Badge helper ──

    private fun updateBadge(menuItemId: Int, count: Int) {
        val badge = binding.bottomNav.getOrCreateBadge(menuItemId)
        badge.backgroundColor = ContextCompat.getColor(this, R.color.colorPrimary)
        badge.badgeTextColor = ContextCompat.getColor(this, R.color.textOnPrimary)
        badge.maxCharacterCount = 3
        badge.isVisible = count > 0
        if (count > 0) badge.number = count
    }

    // ── Progress indicator helpers ──

    private fun showScanProgress(state: ScanState.Scanning) {
        binding.btnCancelScan.visibility = View.VISIBLE
        binding.scanProgressIndicator.visibility = View.VISIBLE
        binding.scanPhaseRow.visibility = View.VISIBLE

        binding.tvScanStatus.text = when (state.phase) {
            ScanPhase.INDEXING   -> getString(R.string.scanning_phase_indexing, state.filesFound)
            ScanPhase.DUPLICATES -> getString(R.string.scanning_phase_duplicates, state.filesFound)
            ScanPhase.ANALYZING  -> getString(R.string.scanning_phase_analyzing, state.filesFound)
            ScanPhase.JUNK       -> getString(R.string.scanning_phase_junk, state.filesFound)
        }

        val indicator = binding.scanProgressIndicator
        if (state.progressPercent < 0) {
            indicator.isIndeterminate = true
            binding.tvScanPercent.visibility = View.GONE
        } else {
            if (indicator.isIndeterminate) {
                indicator.isIndeterminate = false
                indicator.max = 100
            }
            indicator.setProgressCompat(state.progressPercent, true)
            binding.tvScanPercent.visibility = View.VISIBLE
            binding.tvScanPercent.text = getString(R.string.scan_percent, state.progressPercent)
        }

        binding.tvPhaseLabel.text = when (state.phase) {
            ScanPhase.INDEXING   -> getString(R.string.phase_label_indexing)
            ScanPhase.DUPLICATES -> getString(R.string.phase_label_duplicates)
            ScanPhase.ANALYZING  -> getString(R.string.phase_label_analyzing)
            ScanPhase.JUNK       -> getString(R.string.phase_label_junk)
        }
        binding.tvPhaseStep.text = getString(
            R.string.phase_step,
            state.phase.order + 1,
            state.phase.totalPhases
        )
    }

    private fun hideScanProgress() {
        binding.btnCancelScan.visibility = View.GONE
        binding.scanProgressIndicator.visibility = View.GONE
        binding.scanPhaseRow.visibility = View.GONE
        binding.tvScanPercent.visibility = View.GONE
        binding.scanProgressIndicator.isIndeterminate = true
    }
}
