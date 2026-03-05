package com.filecleaner.app

import android.app.Application
import com.filecleaner.app.data.UserPreferences
import com.filecleaner.app.utils.CrashReporter

class FileCleanerApp : Application() {

    companion object {
        /**
         * Canonical reference version: Build APK #124, Pull Request #20
         * by WW-Andene (commit 8e8dc9f on master).
         * All features and layouts must stay consistent with this baseline.
         */
        const val REFERENCE_BUILD = 124
        const val REFERENCE_PR = 20
        const val REFERENCE_COMMIT = "8e8dc9f"
    }

    override fun onCreate() {
        super.onCreate()

        // Initialize preferences early so CrashReporter can read the token
        UserPreferences.init(applicationContext)
        com.filecleaner.app.data.cloud.CloudConnectionStore.init(applicationContext)

        // Install crash handler (writes crash files to disk on uncaught exceptions)
        CrashReporter.install(applicationContext)

        // Upload any crash reports from previous sessions
        CrashReporter.uploadPendingCrashReports()
    }
}
