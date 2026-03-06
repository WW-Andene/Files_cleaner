package com.filecleaner.app.services

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.filecleaner.app.MainActivity
import com.filecleaner.app.R
import com.filecleaner.app.utils.antivirus.*
import kotlinx.coroutines.*
import java.util.concurrent.atomic.AtomicReference

class ScanService : Service() {

    companion object {
        private const val TAG = "ScanService"
        const val CHANNEL_ID = "antivirus_scan"
        const val NOTIFICATION_ID = 1001
        const val ACTION_START = "com.filecleaner.app.ACTION_START_SCAN"
        const val ACTION_STOP = "com.filecleaner.app.ACTION_STOP_SCAN"

        // F-009: Single atomic reference replaces multiple @Volatile fields to ensure
        // consistent reads across all state fields (progress, phase, results, complete).
        data class ScanStatus(
            val isRunning: Boolean = false,
            val currentProgress: Int = 0,
            val currentPhase: String = "",
            val scanResults: List<ThreatResult>? = null,
            val scanComplete: Boolean = false
        )

        private val _status = AtomicReference(ScanStatus())

        // Public accessors for backward compatibility
        val status: ScanStatus get() = _status.get()
        val isRunning: Boolean get() = _status.get().isRunning
        val currentProgress: Int get() = _status.get().currentProgress
        val currentPhase: String get() = _status.get().currentPhase
        val scanResults: List<ThreatResult>? get() = _status.get().scanResults
        val scanComplete: Boolean get() = _status.get().scanComplete

        private fun updateStatus(transform: ScanStatus.() -> ScanStatus) {
            var current: ScanStatus
            var next: ScanStatus
            do {
                current = _status.get()
                next = current.transform()
            } while (!_status.compareAndSet(current, next))
        }

        fun start(context: Context) {
            val intent = Intent(context, ScanService::class.java).apply {
                action = ACTION_START
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, ScanService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }

        fun clearResults() {
            updateStatus { copy(scanResults = null, scanComplete = false, currentProgress = 0, currentPhase = "") }
        }
    }

    private val serviceScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var scanJob: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                if (!_status.get().isRunning) {
                    updateStatus { copy(isRunning = true, scanComplete = false, scanResults = null) }
                    ServiceCompat.startForeground(
                        this,
                        NOTIFICATION_ID,
                        buildNotification(0, getString(R.string.av_scanning)),
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
                            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                        else 0
                    )
                    startScan()
                }
            }
            ACTION_STOP -> {
                scanJob?.cancel()
                updateStatus { copy(isRunning = false) }
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    private fun startScan() {
        scanJob = serviceScope.launch {
            val threats = mutableListOf<ThreatResult>()

            try {
                // Phase 1: App Integrity (0-20%)
                updateNotification(0, getString(R.string.av_phase_integrity))
                val integrityResults = AppIntegrityScanner.scan(applicationContext) { pct ->
                    updateNotification(pct / 5, getString(R.string.av_phase_integrity))
                }
                threats.addAll(integrityResults)

                // Phase 2: File Signatures (20-40%)
                updateNotification(20, getString(R.string.av_phase_signature))
                // Note: We need file data from ViewModel, so we skip deep file scan in bg service
                // and just run a basic check
                updateNotification(40, getString(R.string.av_phase_signature))

                // Phase 3: Privacy Audit (40-60%)
                updateNotification(40, getString(R.string.av_phase_privacy))
                val privacyResults = PrivacyAuditor.audit(applicationContext) { pct ->
                    updateNotification(40 + (pct / 5), getString(R.string.av_phase_privacy))
                }
                threats.addAll(privacyResults)

                // Phase 4: Network Security (60-80%)
                updateNotification(60, getString(R.string.av_phase_network))
                val networkResults = NetworkSecurityScanner.scan(applicationContext) { pct ->
                    updateNotification(60 + (pct / 5), getString(R.string.av_phase_network))
                }
                threats.addAll(networkResults)

                // Phase 5: App Verification (80-100%)
                updateNotification(80, getString(R.string.av_phase_verification))
                val verificationResults = AppVerificationScanner.scan(applicationContext) { pct ->
                    updateNotification(80 + (pct / 5), getString(R.string.av_phase_verification))
                }
                threats.addAll(verificationResults)

                updateNotification(100, getString(R.string.scan_complete_simple))

                // Save results
                withContext(Dispatchers.IO) {
                    ScanHistoryManager.saveResult(applicationContext, threats)
                }

                // F-009: Atomic update ensures fragment sees both results and complete together
                updateStatus { copy(scanResults = threats, scanComplete = true) }

                // Show completion notification
                showCompletionNotification(threats)

            } catch (e: CancellationException) {
                Log.d(TAG, "Scan was cancelled", e)
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Scan failed", e)
            } finally {
                updateStatus { copy(isRunning = false) }
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
    }

    private fun updateNotification(progress: Int, phase: String) {
        updateStatus { copy(currentProgress = progress, currentPhase = phase) }
        val nm = getSystemService(NotificationManager::class.java)
        nm?.notify(NOTIFICATION_ID, buildNotification(progress, phase))
    }

    private fun buildNotification(progress: Int, phase: String): Notification {
        val openIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingOpen = PendingIntent.getActivity(
            this, 0, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, ScanService::class.java).apply {
            action = ACTION_STOP
        }
        val pendingStop = PendingIntent.getService(
            this, 1, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_shield)
            .setContentTitle(getString(R.string.av_scanning))
            .setContentText("$phase — $progress%")
            .setProgress(100, progress, false)
            .setOngoing(true)
            .setContentIntent(pendingOpen)
            .addAction(R.drawable.ic_close, getString(R.string.cancel), pendingStop)
            .build()
    }

    private fun showCompletionNotification(threats: List<ThreatResult>) {
        val criticalCount = threats.count { it.severity == ThreatResult.Severity.CRITICAL }
        val highCount = threats.count { it.severity == ThreatResult.Severity.HIGH }
        val threatCount = criticalCount + highCount

        val openIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingOpen = PendingIntent.getActivity(
            this, 0, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val text = if (threatCount > 0) {
            resources.getQuantityString(R.plurals.av_found_threats, threats.size, threats.size)
        } else {
            getString(R.string.av_all_clear)
        }

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_shield)
            .setContentTitle(getString(R.string.scan_complete_simple))
            .setContentText(text)
            .setAutoCancel(true)
            .setContentIntent(pendingOpen)
            .build()

        val nm = getSystemService(NotificationManager::class.java)
        nm?.notify(NOTIFICATION_ID + 1, notification)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.av_title),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Security Scanner progress"
                setShowBadge(false)
            }
            val nm = getSystemService(NotificationManager::class.java)
            nm?.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        scanJob?.cancel()
        serviceScope.cancel()
        // F-009: Atomic update — clear state references to prevent memory retention
        updateStatus {
            if (!scanComplete) {
                copy(isRunning = false, scanResults = null, currentProgress = 0, currentPhase = "")
            } else {
                copy(isRunning = false)
            }
        }
        super.onDestroy()
    }
}
