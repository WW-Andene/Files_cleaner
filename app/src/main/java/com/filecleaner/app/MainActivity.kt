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
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import com.filecleaner.app.databinding.ActivityMainBinding
import com.filecleaner.app.ui.widget.RaccoonBubble
import com.filecleaner.app.viewmodel.MainViewModel
import com.filecleaner.app.viewmodel.ScanState

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

        // Bottom navigation
        val navHost = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        binding.bottomNav.setupWithNavController(navHost.navController)

        // Raccoon bubble → toggle arborescence
        val navController = navHost.navController
        RaccoonBubble.attach(binding.raccoonBubble) {
            val currentDest = navController.currentDestination?.id
            if (currentDest == R.id.arborescenceFragment) {
                navController.popBackStack()
            } else {
                navController.navigate(R.id.arborescenceFragment)
            }
        }

        // Scan button
        binding.fabScan.setOnClickListener { requestPermissionsAndScan() }

        // Scan state observer
        viewModel.scanState.observe(this) { state ->
            when (state) {
                is ScanState.Idle     -> {
                    binding.progressBar.visibility = View.GONE
                    binding.tvScanStatus.text = getString(R.string.scan_prompt)
                }
                is ScanState.Scanning -> {
                    binding.progressBar.visibility = View.VISIBLE
                    binding.tvScanStatus.text = getString(R.string.scanning_progress, state.filesFound)
                }
                is ScanState.Done     -> {
                    binding.progressBar.visibility = View.GONE
                    binding.tvScanStatus.text = getString(R.string.scan_complete)
                }
                is ScanState.Error    -> {
                    binding.progressBar.visibility = View.GONE
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
