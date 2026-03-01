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
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.navigation.NavOptions
import androidx.navigation.fragment.NavHostFragment
import com.filecleaner.app.databinding.ActivityMainBinding
import com.filecleaner.app.ui.widget.RaccoonBubble
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
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Show version number in header
        binding.tvVersion.text = "v${BuildConfig.VERSION_NAME}"

        // Navigation setup
        val navHost = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        val navController = navHost.navController

        val bottomNavIds = setOf(
            R.id.browseFragment, R.id.duplicatesFragment,
            R.id.largeFilesFragment, R.id.junkFragment
        )

        // Map menu item IDs to nav destination IDs
        val menuToNav = mapOf(
            R.id.browseFragment to R.id.browseFragment,
            R.id.duplicatesFragment to R.id.duplicatesFragment,
            R.id.largeFilesFragment to R.id.largeFilesFragment,
            R.id.junkFragment to R.id.junkFragment
        )

        // Manual bottom nav handling — ensures arborescence is popped before navigating
        binding.bottomNav.setOnItemSelectedListener { item ->
            val destId = menuToNav[item.itemId] ?: return@setOnItemSelectedListener false
            // Pop arborescence if currently showing
            if (navController.currentDestination?.id == R.id.arborescenceFragment) {
                navController.popBackStack(R.id.arborescenceFragment, true)
            }
            val options = NavOptions.Builder()
                .setPopUpTo(R.id.browseFragment, inclusive = false, saveState = true)
                .setLaunchSingleTop(true)
                .setRestoreState(true)
                .build()
            navController.navigate(destId, null, options)
            true
        }
        // Reselect = do nothing (stay on current tab)
        binding.bottomNav.setOnItemReselectedListener { /* no-op */ }

        // Keep bottom nav selection in sync with current destination
        navController.addOnDestinationChangedListener { _, dest, _ ->
            if (dest.id in bottomNavIds) {
                binding.bottomNav.menu.findItem(dest.id)?.isChecked = true
            }
        }

        // Raccoon bubble → toggle arborescence
        RaccoonBubble.attach(binding.raccoonBubbleCard) {
            val currentDest = navController.currentDestination?.id
            if (currentDest == R.id.arborescenceFragment) {
                navController.popBackStack()
            } else {
                navController.navigate(R.id.arborescenceFragment)
            }
        }

        // Navigate to tree on "Open in Raccoon Tab"
        viewModel.navigateToTree.observe(this) { filePath ->
            if (filePath != null) {
                val currentDest = navController.currentDestination?.id
                if (currentDest != R.id.arborescenceFragment) {
                    navController.navigate(R.id.arborescenceFragment)
                }
            }
        }

        // Scan button
        binding.fabScan.setOnClickListener { requestPermissionsAndScan() }

        // Cancel scan button
        binding.btnCancelScan.setOnClickListener { viewModel.cancelScan() }

        // Scan state observer
        viewModel.scanState.observe(this) { state ->
            when (state) {
                is ScanState.Idle     -> {
                    binding.progressBar.visibility = View.GONE
                    binding.btnCancelScan.visibility = View.GONE
                    binding.tvScanStatus.text = getString(R.string.scan_prompt)
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
                        binding.tvScanStatus.text = resources.getQuantityString(
                            R.plurals.scan_complete,
                            stats.totalFiles,
                            stats.totalFiles,
                            UndoHelper.formatBytes(stats.totalSize)
                        )
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
                    Toast.makeText(this, getString(R.string.error_prefix, state.message), Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun requestPermissionsAndScan() {
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
