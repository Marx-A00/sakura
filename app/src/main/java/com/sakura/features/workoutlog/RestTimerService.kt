package com.sakura.features.workoutlog

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.sakura.R
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Shared singleton for timer state communication between ViewModel and Service.
 * Both observe the same StateFlow. The ViewModel drives updates; the Service reads
 * to update the notification text.
 */
object RestTimerBridge {
    private val _state = MutableStateFlow<TimerState>(TimerState.Idle)
    val state: StateFlow<TimerState> = _state.asStateFlow()

    fun update(newState: TimerState) {
        _state.value = newState
    }
}

/**
 * Optional foreground service that shows a persistent notification with the
 * rest timer countdown. Only started when the user has enabled "Background
 * Notification" in settings.
 *
 * Uses foregroundServiceType="shortService" (~3 min timeout, which covers
 * all reasonable rest durations).
 *
 * The actual timer logic lives in WorkoutLogViewModel. This service only
 * observes the shared RestTimerBridge.state and updates the notification.
 */
class RestTimerService : Service() {

    companion object {
        const val CHANNEL_ID = "sakura_rest_timer"
        const val NOTIFICATION_ID = 7001
        const val ACTION_STOP = "com.sakura.REST_TIMER_STOP"

        fun startService(context: Context) {
            val intent = Intent(context, RestTimerService::class.java)
            context.startForegroundService(intent)
        }

        fun stopService(context: Context) {
            val intent = Intent(context, RestTimerService::class.java)
            context.stopService(intent)
        }
    }

    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var observeJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }

        val initialNotification = buildNotification("Rest timer active")
        startForeground(NOTIFICATION_ID, initialNotification)

        observeJob?.cancel()
        observeJob = serviceScope.launch {
            RestTimerBridge.state.collect { timerState ->
                when (timerState) {
                    is TimerState.Running -> {
                        val mins = timerState.remainingSecs / 60
                        val secs = timerState.remainingSecs % 60
                        val text = "Rest: %d:%02d".format(mins, secs)
                        updateNotification(text)
                    }
                    is TimerState.Done -> {
                        updateNotification("Rest complete!")
                        delay(2_500L)
                        stopSelf()
                    }
                    is TimerState.Idle -> {
                        stopSelf()
                    }
                }
            }
        }

        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onTimeout(startId: Int) {
        // shortService ~3 min timeout — graceful stop
        stopSelf()
    }

    override fun onDestroy() {
        observeJob?.cancel()
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Rest Timer",
            NotificationManager.IMPORTANCE_LOW  // Low = no sound/vibration from notification itself
        ).apply {
            description = "Shows countdown during workout rest periods"
        }
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    private fun buildNotification(text: String): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Sakura Rest Timer")
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setOngoing(true)
            .setSilent(true)
            .build()
    }

    private fun updateNotification(text: String) {
        val notification = buildNotification(text)
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, notification)
    }
}
