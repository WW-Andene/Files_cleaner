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

        // Scan button
        binding.fabScan.setOnClickListener { requestPermissionsAndScan() }

        // Scan state observer
        viewModel.scanState.observe(this) { state ->
            when (state) {
                is ScanState.Idle     -> {
                    binding.progressBar.visibility = View.GONE
                    binding.tvScanStatus.text = "Tap ▶ to scan your storage"
                }
                is ScanState.Scanning -> {
                    binding.progressBar.visibility = View.VISIBLE
                    binding.tvScanStatus.text = "Scanning… ${state.filesFound} files found"
                }
                is ScanState.Done     -> {
                    binding.progressBar.visibility = View.GONE
                    binding.tvScanStatus.text = "Scan complete ✓"
                }
                is ScanState.Error    -> {
                    binding.progressBar.visibility = View.GONE
                    Toast.makeText(this, "Error: ${state.message}", Toast.LENGTH_LONG).show()
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
                        .setTitle("Storage access needed")
                        .setMessage(
                            "To scan all files, please grant 'Allow access to manage all files' " +
                            "in the next screen."
                        )
                        .setPositiveButton("Open Settings") { _, _ ->
                            manageFilesLauncher.launch(
                                Intent(
                                    Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                                    Uri.parse("package:$packageName")
                                )
                            )
                        }
                        .setNegativeButton("Cancel", null)
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
            .setTitle("Permission required")
            .setMessage("Storage permission is required to scan files. Please grant it in Settings.")
            .setPositiveButton("Settings") { _, _ ->
                startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:$packageName")))
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}
