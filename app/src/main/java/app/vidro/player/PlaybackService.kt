package app.vidro.player

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.support.v4.media.session.PlaybackStateCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.media.app.NotificationCompat.MediaStyle
import androidx.media.session.MediaButtonReceiver
import app.vidro.MainActivity
import app.vidro.R

/**
 * Keeps the app alive while a video plays in the background and shows the playback notification.
 * The player itself lives in [PlayerHolder]; this service only mirrors its media session.
 */
class PlaybackService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        instance = this
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val controller = PlayerHolder.controller
        if (controller == null) {
            stopSelf()
            return START_NOT_STICKY
        }
        MediaButtonReceiver.handleIntent(controller.session, intent)
        ServiceCompat.startForeground(
            this, NOTIFICATION_ID, buildNotification(controller),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK else 0,
        )
        return START_NOT_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        // Swiping the app away from recents ends background playback.
        PlayerHolder.controller?.let { PlayerHolder.clear(it) }
        stopSelf()
    }

    override fun onDestroy() {
        if (instance === this) instance = null
        super.onDestroy()
    }

    private fun update() {
        val controller = PlayerHolder.controller ?: return
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, buildNotification(controller))
    }

    private fun buildNotification(controller: PlayerController): android.app.Notification {
        ensureChannel(this)
        val paused = controller.engine.state.value.paused
        // Listening opens the music screen; a video in the background goes back to its player.
        val target = if (controller.audioOnly.value) MainActivity.nowPlayingIntent(this)
        else Intent(this, PlayerActivity::class.java).putExtra(PlayerActivity.EXTRA_REATTACH, true)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val open = PendingIntent.getActivity(
            this, if (controller.audioOnly.value) 1 else 0, target,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        fun action(icon: Int, title: Int, command: Long) = NotificationCompat.Action(
            icon, getString(title), MediaButtonReceiver.buildMediaButtonPendingIntent(this, command),
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(controller.currentItem.title)
            .setContentText(controller.currentItem.artist ?: getString(R.string.app_name))
            .setLargeIcon(controller.artwork.value)
            .setContentIntent(open)
            .setDeleteIntent(MediaButtonReceiver.buildMediaButtonPendingIntent(this, PlaybackStateCompat.ACTION_STOP))
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOnlyAlertOnce(true)
            .setOngoing(!paused)
            .addAction(action(R.drawable.ic_skip_previous, R.string.player_previous, PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS))
            .addAction(
                if (paused) action(R.drawable.ic_play, R.string.player_play, PlaybackStateCompat.ACTION_PLAY)
                else action(R.drawable.ic_pause, R.string.player_pause, PlaybackStateCompat.ACTION_PAUSE),
            )
            .addAction(action(R.drawable.ic_skip_next, R.string.player_next, PlaybackStateCompat.ACTION_SKIP_TO_NEXT))
            .setStyle(
                MediaStyle()
                    .setMediaSession(controller.session.sessionToken)
                    .setShowActionsInCompactView(0, 1, 2),
            )
            .build()
    }

    companion object {
        private const val CHANNEL_ID = "playback"
        private const val NOTIFICATION_ID = 7
        private var instance: PlaybackService? = null

        fun start(context: Context) {
            ContextCompat.startForegroundService(context, Intent(context, PlaybackService::class.java))
        }

        fun stop(context: Context) {
            if (instance != null) context.stopService(Intent(context, PlaybackService::class.java))
        }

        /** Re-renders the notification after a play/pause or track change. */
        fun refresh() {
            instance?.update()
        }

        private fun ensureChannel(context: Context) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
            val manager = context.getSystemService(NotificationManager::class.java)
            if (manager.getNotificationChannel(CHANNEL_ID) != null) return
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, context.getString(R.string.notification_channel), NotificationManager.IMPORTANCE_LOW)
                    .apply { setShowBadge(false) },
            )
        }
    }
}
