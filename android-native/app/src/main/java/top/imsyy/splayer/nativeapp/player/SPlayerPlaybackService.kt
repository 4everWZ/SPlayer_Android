package top.imsyy.splayer.nativeapp.player

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.pm.ServiceInfo
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.MediaStyleNotificationHelper
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import top.imsyy.splayer.nativeapp.MainActivity
import top.imsyy.splayer.nativeapp.R

@AndroidEntryPoint
class SPlayerPlaybackService : MediaSessionService() {
    @Inject
    lateinit var playbackCoordinator: PlaybackCoordinator

    private var mediaSession: MediaSession? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        mediaSession = MediaSession.Builder(this, SystemMediaTransportPlayer(playbackCoordinator)).build()
        configureMediaNotificationProvider()
        mediaSession?.let(::startPlaybackServiceForeground)
        playbackCoordinator.attachSessionService()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return mediaSession
    }

    override fun onDestroy() {
        stopForeground(STOP_FOREGROUND_REMOVE)
        mediaSession?.release()
        mediaSession = null
        playbackCoordinator.detachSessionService()
        super.onDestroy()
    }

    private fun configureMediaNotificationProvider() {
        val provider = DefaultMediaNotificationProvider.Builder(this)
            .setNotificationId(PLAYBACK_NOTIFICATION_ID)
            .setChannelId(PLAYBACK_CHANNEL_ID)
            .setChannelName(R.string.playback_channel_name)
            .build()
        provider.setSmallIcon(R.mipmap.ic_launcher)
        setMediaNotificationProvider(provider)
    }

    private fun startPlaybackServiceForeground(session: MediaSession) {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(this, PLAYBACK_CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(playbackCoordinator.uiState.value.currentTrack?.name ?: "SPlayer")
            .setContentText(playbackCoordinator.uiState.value.currentTrack?.artists ?: "正在准备播放")
            .setContentIntent(pendingIntent)
            .setCategory(NotificationCompat.CATEGORY_TRANSPORT)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setStyle(MediaStyleNotificationHelper.MediaStyle(session))
            .setOngoing(true)
            .setSilent(true)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                PLAYBACK_NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK,
            )
        } else {
            startForeground(PLAYBACK_NOTIFICATION_ID, notification)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(NotificationManager::class.java) ?: return
        val channel = NotificationChannel(
            PLAYBACK_CHANNEL_ID,
            getString(R.string.playback_channel_name),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = getString(R.string.playback_channel_description)
            setShowBadge(false)
        }
        manager.createNotificationChannel(channel)
    }

    companion object {
        private const val PLAYBACK_CHANNEL_ID = "splayer_native_playback"
        private const val PLAYBACK_NOTIFICATION_ID = 1001
    }
}

@OptIn(UnstableApi::class)
private class SystemMediaTransportPlayer(
    private val playbackCoordinator: PlaybackCoordinator,
) : ForwardingPlayer(playbackCoordinator.player) {
    override fun getAvailableCommands(): Player.Commands {
        return super.getAvailableCommands()
            .buildUpon()
            .add(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)
            .add(Player.COMMAND_SEEK_TO_PREVIOUS)
            .add(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
            .add(Player.COMMAND_SEEK_TO_NEXT)
            .build()
    }

    override fun isCommandAvailable(command: Int): Boolean {
        return when (command) {
            Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM,
            Player.COMMAND_SEEK_TO_PREVIOUS,
            Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM,
            Player.COMMAND_SEEK_TO_NEXT,
            -> true
            else -> super.isCommandAvailable(command)
        }
    }

    override fun hasPreviousMediaItem(): Boolean {
        return resolveSystemMediaTransportAvailability(
            queueSize = playbackCoordinator.uiState.value.queue.size,
            currentIndex = playbackCoordinator.uiState.value.currentIndex,
        ).previousAvailable || super.hasPreviousMediaItem()
    }

    override fun hasNextMediaItem(): Boolean {
        return resolveSystemMediaTransportAvailability(
            queueSize = playbackCoordinator.uiState.value.queue.size,
            currentIndex = playbackCoordinator.uiState.value.currentIndex,
        ).nextAvailable || super.hasNextMediaItem()
    }

    override fun seekToPreviousMediaItem() {
        playbackCoordinator.skipPrevious()
    }

    override fun seekToPrevious() {
        playbackCoordinator.skipPrevious()
    }

    override fun seekToNextMediaItem() {
        playbackCoordinator.skipNext()
    }

    override fun seekToNext() {
        playbackCoordinator.skipNext()
    }
}
