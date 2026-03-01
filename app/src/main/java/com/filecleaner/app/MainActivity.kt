package com.filecleaner.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.findNavController
import androidx.core.content.ContextCompat
import androidx.navigation.NavOptions
import androidx.navigation.fragment.NavHostFragment
import com.filecleaner.app.data.UserPreferences
import com.filecleaner.app.databinding.ActivityMainBinding
import com.filecleaner.app.ui.onboarding.OnboardingDialog
import com.filecleaner.app.utils.UndoHelper
import com.filecleaner.app.viewmodel.MainViewModel
import com.filecleaner.app.viewmodel.ScanPhase
import com.filecleaner.app.viewmodel.ScanState
import com.google.android.material.snackbar.Snackbar

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    val viewModel: MainViewModel by viewModels()

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
        super.onCreate(savedInstanceState)
        UserPreferences.init(applicationContext)
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
            // Pop non-tab fragments (arborescence, settings, dashboard) from back stack
            if (currentDest != null && currentDest !in bottomNavIds) {
                navController.popBackStack(currentDest, true)
            }
            val options = NavOptions.Builder()
                .setPopUpTo(R.id.raccoonManagerFragment, inclusive = false, saveState = true)
                .setLaunchSingleTop(true)
                .setRestoreState(true)
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
                    .build()
                navController.navigate(destId, null, options)
            }
        }

        // F1/F3: Select Manager tab on first launch to match startDestination
        if (savedInstanceState == null) {
            binding.bottomNav.selectedItemId = R.id.raccoonManagerFragment
        }

        // Keep bottom nav selection in sync with current destination
        navController.addOnDestinationChangedListener { _, dest, _ ->
            if (dest.id in bottomNavIds) {
                binding.bottomNav.menu.findItem(dest.id)?.isChecked = true
            }
        }

        // Navigate to tree on "Show in Tree"
        viewModel.navigateToTree.observe(this) { filePath ->
            if (filePath != null) {
                val currentDest = navController.currentDestination?.id
                if (currentDest != R.id.arborescenceFragment) {
                    navController.navigate(R.id.arborescenceFragment)
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

        // Cancel scan button
        binding.btnCancelScan.setOnClickListener { viewModel.cancelScan() }

        // Scan state observer
        viewModel.scanState.observe(this) { state ->
            when (state) {
                is ScanState.Idle     -> {
                    binding.progressBar.visibility = View.GONE
                    binding.btnCancelScan.visibility = View.GONE
                    binding.tvScanStatus.text = getString(R.string.scan_prompt)
                    // F3: Make scan bar tappable to start scan
                    binding.tvScanStatus.setOnClickListener {
                        requestPermissionsAndScan()
                    }
                }
                is ScanState.Scanning -> {
                    binding.progressBar.visibility = View.VISIBLE
                    binding.btnCancelScan.visibility = View.VISIBLE
                    binding.tvScanStatus.text = when (state.phase) {
                        ScanPhase.INDEXING   -> getString(R.string.scanning_phase_indexing, state.filesFound)
                        ScanPhase.DUPLICATES -> getString(R.string.scanning_phase_duplicates, state.filesFound)
                        ScanPhase.ANALYZING  -> getString(R.string.scanning_phase_analyzing, state.filesFound)
                        ScanPhase.JUNK       -> getString(R.string.scanning_phase_junk, state.filesFound)
                    }
                }
                is ScanState.Done     -> {
                    binding.progressBar.visibility = View.GONE
                    binding.btnCancelScan.visibility = View.GONE
                    val stats = viewModel.storageStats.value
                    if (stats != null) {
                        val durationText = if (stats.scanDurationMs > 0) {
                            " in %.1fs".format(stats.scanDurationMs / 1000.0)
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
                            findNavController(R.id.nav_host_fragment).navigate(R.id.dashboardFragment)
                        }
                    } else {
                        binding.tvScanStatus.text = getString(R.string.scan_complete_simple)
                    }
                }
                is ScanState.Cancelled -> {
                    binding.progressBar.visibility = View.GONE
                    binding.btnCancelScan.visibility = View.GONE
                    binding.tvScanStatus.text = getString(R.string.scan_cancelled)
                }
                is ScanState.Error    -> {
                    binding.progressBar.visibility = View.GONE
                    binding.btnCancelScan.visibility = View.GONE
                    binding.tvScanStatus.text = getString(R.string.error_scan_failed)
                    Snackbar.make(binding.root,
                        getString(R.string.error_prefix, state.message),
                        Snackbar.LENGTH_LONG).show()
                }
            }
        }

        // Bottom nav badges — show count of actionable items per tab
        viewModel.duplicates.observe(this) { dupes ->
            val badge = binding.bottomNav.getOrCreateBadge(R.id.duplicatesFragment)
            badge.backgroundColor = ContextCompat.getColor(this, R.color.colorPrimary)
            badge.badgeTextColor = ContextCompat.getColor(this, R.color.textOnPrimary)
            badge.maxCharacterCount = 3
            badge.isVisible = dupes.isNotEmpty()
            if (dupes.isNotEmpty()) badge.number = dupes.size
        }
        viewModel.largeFiles.observe(this) { large ->
            val badge = binding.bottomNav.getOrCreateBadge(R.id.largeFilesFragment)
            badge.backgroundColor = ContextCompat.getColor(this, R.color.colorPrimary)
            badge.badgeTextColor = ContextCompat.getColor(this, R.color.textOnPrimary)
            badge.maxCharacterCount = 3
            badge.isVisible = large.isNotEmpty()
            if (large.isNotEmpty()) badge.number = large.size
        }
        viewModel.junkFiles.observe(this) { junk ->
            val badge = binding.bottomNav.getOrCreateBadge(R.id.junkFragment)
            badge.backgroundColor = ContextCompat.getColor(this, R.color.colorPrimary)
            badge.badgeTextColor = ContextCompat.getColor(this, R.color.textOnPrimary)
            badge.maxCharacterCount = 3
            badge.isVisible = junk.isNotEmpty()
            if (junk.isNotEmpty()) badge.number = junk.size
        }

        // Settings button
        binding.btnSettings.setOnClickListener {
            findNavController(R.id.nav_host_fragment).navigate(R.id.settingsFragment)
        }

        // First-launch onboarding
        OnboardingDialog.showIfNeeded(this)
    }

    fun requestPermissionsAndScan() {
        when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> {
                // Android 11+ needs MANAGE_EXTERNAL_STORAGE
                if (Environment.isExternalStorageManager()) {
                    startScan()
                } else {
                    AlertDialog.Builder(this)
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
            }
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> {
                // Android 13+
                permLauncher.launch(arrayOf(
                    Manifest.permission.READ_MEDIA_IMAGES,
                    Manifest.permission.READ_MEDIA_VIDEO,
                    Manifest.permission.READ_MEDIA_AUDIO
                ))
            }
            else -> {
                // Android 10
                val needed = arrayOf(
                    Manifest.permission.READ_EXTERNAL_STORAGE,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
                ).filter {
                    ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
                }.toTypedArray()

                if (needed.isEmpty()) startScan() else permLauncher.launch(needed)
            }
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
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.permission_required))
            .setMessage(getString(R.string.permission_required_message))
            .setPositiveButton(getString(R.string.settings)) { _, _ ->
                startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:$packageName")))
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }
}
