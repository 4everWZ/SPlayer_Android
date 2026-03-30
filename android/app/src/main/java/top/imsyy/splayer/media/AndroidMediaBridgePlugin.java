package top.imsyy.splayer.media;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Build;
import android.os.PowerManager;
import android.os.SystemClock;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.PlaybackStateCompat;
import android.util.Base64;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;
import androidx.media.app.NotificationCompat.MediaStyle;

import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;
import com.getcapacitor.annotation.Permission;
import top.imsyy.splayer.MainActivity;
import top.imsyy.splayer.R;

@CapacitorPlugin(
        name = "AndroidMediaBridge",
        permissions = {
                @Permission(
                        alias = "notifications",
                        strings = { Manifest.permission.POST_NOTIFICATIONS }
                )
        }
)
public class AndroidMediaBridgePlugin extends Plugin {

    public static final String ACTION_PLAY = "top.imsyy.splayer.media.PLAY";
    public static final String ACTION_PAUSE = "top.imsyy.splayer.media.PAUSE";
    public static final String ACTION_NEXT = "top.imsyy.splayer.media.NEXT";
    public static final String ACTION_PREVIOUS = "top.imsyy.splayer.media.PREVIOUS";
    public static final String ACTION_STOP = "top.imsyy.splayer.media.STOP";

    private static final String CHANNEL_ID = "splayer_media";
    private static final int NOTIFICATION_ID = 31030;

    private static AndroidMediaBridgePlugin instance;

    private NotificationManagerCompat notificationManager;
    private MediaSessionCompat mediaSession;
    private PowerManager.WakeLock playbackWakeLock;

    private String currentTitle = "";
    private String currentArtist = "";
    private String currentAlbum = "";
    private long currentDuration = 0L;
    private long currentPosition = 0L;
    private boolean currentPlaying = false;
    private String currentPlaybackStatus = "Paused";
    private float currentPlaybackRate = 1F;
    private boolean currentShuffling = false;
    private String currentRepeatMode = "None";
    private Bitmap currentArtwork = null;

    @Override
    public void load() {
        super.load();
        instance = this;
        notificationManager = NotificationManagerCompat.from(getContext());
        initWakeLock();
        ensureNotificationChannel();
        initMediaSession();
    }

    @Override
    protected void handleOnDestroy() {
        super.handleOnDestroy();
        releasePlaybackWakeLock();
        notificationManager.cancel(NOTIFICATION_ID);
        if (mediaSession != null) {
          mediaSession.release();
          mediaSession = null;
        }
        if (instance == this) {
            instance = null;
        }
    }

    @PluginMethod
    public void updateMetadata(PluginCall call) {
        currentTitle = call.getString("title", "");
        currentArtist = call.getString("artist", "");
        currentAlbum = call.getString("album", "");
        currentDuration = readLong(call, "duration", currentDuration);
        currentArtwork = decodeArtwork(call.getString("coverBase64", null));
        updateSessionMetadata();
        updatePlaybackState();
        refreshNotification();
        call.resolve();
    }

    @PluginMethod
    public void updatePlayState(PluginCall call) {
        currentPlaybackStatus = call.getString("status", currentPlaybackStatus);
        currentPlaying = isPlaybackActive();
        syncPlaybackWakeLock();
        updatePlaybackState();
        refreshNotification();
        call.resolve();
    }

    @PluginMethod
    public void updatePlaybackRate(PluginCall call) {
        currentPlaybackRate = Math.max(0.1F, readFloat(call, "rate", currentPlaybackRate));
        updatePlaybackState();
        call.resolve();
    }

    @PluginMethod
    public void updateVolume(PluginCall call) {
        call.resolve();
    }

    @PluginMethod
    public void updateTimeline(PluginCall call) {
        currentPosition = readLong(call, "currentTime", currentPosition);
        currentDuration = readLong(call, "totalTime", currentDuration);
        updateSessionMetadata();
        updatePlaybackState();
        refreshNotification();
        call.resolve();
    }

    @PluginMethod
    public void updatePlayMode(PluginCall call) {
        currentShuffling = call.getBoolean("isShuffling", false);
        currentRepeatMode = call.getString("repeatMode", "None");
        updatePlaybackState();
        refreshNotification();
        call.resolve();
    }

    public static void dispatchAction(String action) {
        if (instance == null || action == null) return;
        instance.handleExternalAction(action);
    }

    private void initWakeLock() {
        PowerManager powerManager = getContext().getSystemService(PowerManager.class);
        if (powerManager == null) return;
        playbackWakeLock = powerManager.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                getContext().getPackageName() + ":playback"
        );
        playbackWakeLock.setReferenceCounted(false);
    }

    private void syncPlaybackWakeLock() {
        if (playbackWakeLock == null) return;
        try {
            if (isPlaybackActive()) {
                if (!playbackWakeLock.isHeld()) {
                    playbackWakeLock.acquire();
                }
            } else {
                releasePlaybackWakeLock();
            }
        } catch (RuntimeException ignored) {
        }
    }

    private void releasePlaybackWakeLock() {
        if (playbackWakeLock == null || !playbackWakeLock.isHeld()) return;
        try {
            playbackWakeLock.release();
        } catch (RuntimeException ignored) {
        }
    }

    private void initMediaSession() {
        mediaSession = new MediaSessionCompat(getContext(), "SPlayerMediaSession");
        mediaSession.setFlags(
                MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS |
                        MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS
        );
        mediaSession.setSessionActivity(buildContentIntent());
        mediaSession.setCallback(new MediaSessionCompat.Callback() {
            @Override
            public void onPlay() {
                emitMediaEvent("Play", null);
            }

            @Override
            public void onPause() {
                emitMediaEvent("Pause", null);
            }

            @Override
            public void onSkipToNext() {
                emitMediaEvent("NextSong", null);
            }

            @Override
            public void onSkipToPrevious() {
                emitMediaEvent("PreviousSong", null);
            }

            @Override
            public void onStop() {
                emitMediaEvent("Stop", null);
            }

            @Override
            public void onSeekTo(long pos) {
                currentPosition = Math.max(0L, pos);
                updatePlaybackState();
                emitMediaEvent("Seek", pos);
            }
        });
        mediaSession.setActive(true);
        updatePlaybackState();
    }

    private void handleExternalAction(String action) {
        switch (action) {
            case ACTION_PLAY:
                emitMediaEvent("Play", null);
                break;
            case ACTION_PAUSE:
                emitMediaEvent("Pause", null);
                break;
            case ACTION_NEXT:
                emitMediaEvent("NextSong", null);
                break;
            case ACTION_PREVIOUS:
                emitMediaEvent("PreviousSong", null);
                break;
            case ACTION_STOP:
                emitMediaEvent("Stop", null);
                break;
            default:
                break;
        }
    }

    private void emitMediaEvent(String type, Long positionMs) {
        JSObject data = new JSObject();
        data.put("type", type);
        if (positionMs != null) {
            data.put("positionMs", positionMs);
        }
        notifyListeners("media-event", data, true);
    }

    private void updateSessionMetadata() {
        if (mediaSession == null) return;
        MediaMetadataCompat.Builder builder = new MediaMetadataCompat.Builder()
                .putString(MediaMetadataCompat.METADATA_KEY_TITLE, currentTitle)
                .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, currentArtist)
                .putString(MediaMetadataCompat.METADATA_KEY_ALBUM, currentAlbum)
                .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, currentDuration);
        if (currentArtwork != null) {
            builder.putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, currentArtwork);
            builder.putBitmap(MediaMetadataCompat.METADATA_KEY_DISPLAY_ICON, currentArtwork);
        }
        mediaSession.setMetadata(builder.build());
    }

    private void updatePlaybackState() {
        if (mediaSession == null) return;
        long actions = PlaybackStateCompat.ACTION_PLAY |
                PlaybackStateCompat.ACTION_PAUSE |
                PlaybackStateCompat.ACTION_PLAY_PAUSE |
                PlaybackStateCompat.ACTION_SKIP_TO_NEXT |
                PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS |
                PlaybackStateCompat.ACTION_STOP |
                PlaybackStateCompat.ACTION_SEEK_TO;
        int state;
        if (isPlaybackLoading()) {
            state = PlaybackStateCompat.STATE_BUFFERING;
        } else if (isPlaybackPlaying()) {
            state = PlaybackStateCompat.STATE_PLAYING;
        } else {
            state = PlaybackStateCompat.STATE_PAUSED;
        }
        float speed = isPlaybackPlaying() ? currentPlaybackRate : 0F;
        PlaybackStateCompat.Builder builder = new PlaybackStateCompat.Builder()
                .setActions(actions)
                .setState(state, currentPosition, speed, SystemClock.elapsedRealtime());
        mediaSession.setPlaybackState(builder.build());
        mediaSession.setShuffleMode(
                currentShuffling ? PlaybackStateCompat.SHUFFLE_MODE_ALL : PlaybackStateCompat.SHUFFLE_MODE_NONE
        );
        mediaSession.setRepeatMode(resolveRepeatMode(currentRepeatMode));
    }

    private int resolveRepeatMode(String repeatMode) {
        if ("Track".equalsIgnoreCase(repeatMode)) {
            return PlaybackStateCompat.REPEAT_MODE_ONE;
        }
        if ("List".equalsIgnoreCase(repeatMode)) {
            return PlaybackStateCompat.REPEAT_MODE_ALL;
        }
        return PlaybackStateCompat.REPEAT_MODE_NONE;
    }

    private void refreshNotification() {
        if (mediaSession == null || currentTitle == null || currentTitle.isEmpty()) {
            notificationManager.cancel(NOTIFICATION_ID);
            return;
        }
        if (!canPostNotification()) {
            return;
        }

        boolean playbackActive = isPlaybackActive();

        Notification notification = new NotificationCompat.Builder(getContext(), CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle(currentTitle)
                .setContentText(currentArtist)
                .setSubText(currentAlbum)
                .setLargeIcon(currentArtwork)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setOnlyAlertOnce(true)
                .setOngoing(playbackActive)
                .setSilent(!playbackActive)
                .setContentIntent(buildContentIntent())
                .addAction(
                        android.R.drawable.ic_media_previous,
                        "上一首",
                        buildControlIntent(ACTION_PREVIOUS)
                )
                .addAction(
                        playbackActive ? android.R.drawable.ic_media_pause : android.R.drawable.ic_media_play,
                        playbackActive ? "暂停" : "播放",
                        buildControlIntent(playbackActive ? ACTION_PAUSE : ACTION_PLAY)
                )
                .addAction(
                        android.R.drawable.ic_media_next,
                        "下一首",
                        buildControlIntent(ACTION_NEXT)
                )
                .setStyle(
                        new MediaStyle()
                                .setMediaSession(mediaSession.getSessionToken())
                                .setShowActionsInCompactView(0, 1, 2)
                )
                .build();

        notificationManager.notify(NOTIFICATION_ID, notification);
    }

    private void ensureNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;
        NotificationManager manager = getContext().getSystemService(NotificationManager.class);
        if (manager == null) return;
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                "SPlayer 播放控制",
                NotificationManager.IMPORTANCE_LOW
        );
        channel.setDescription("用于显示播放通知和锁屏控制");
        channel.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
        manager.createNotificationChannel(channel);
    }

    private boolean canPostNotification() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true;
        return ContextCompat.checkSelfPermission(
                getContext(),
                Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED;
    }

    private PendingIntent buildContentIntent() {
        Intent intent = new Intent(getContext(), MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        return PendingIntent.getActivity(
                getContext(),
                1,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
    }

    private PendingIntent buildControlIntent(String action) {
        Intent intent = new Intent(getContext(), AndroidMediaControlReceiver.class);
        intent.setAction(action);
        intent.setPackage(getContext().getPackageName());
        return PendingIntent.getBroadcast(
                getContext(),
                action.hashCode(),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
    }

    private Bitmap decodeArtwork(String coverBase64) {
        if (coverBase64 == null || coverBase64.isEmpty()) return currentArtwork;
        try {
            byte[] imageBytes = Base64.decode(coverBase64, Base64.DEFAULT);
            return BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.length);
        } catch (IllegalArgumentException error) {
            return currentArtwork;
        }
    }

    private long readLong(PluginCall call, String key, long fallbackValue) {
        Double value = call.getDouble(key);
        if (value == null) {
            Long longValue = call.getLong(key);
            return longValue == null ? fallbackValue : Math.max(0L, longValue);
        }
        return Math.max(0L, Math.round(value));
    }

    private float readFloat(PluginCall call, String key, float fallbackValue) {
        Float value = call.getFloat(key);
        return value == null ? fallbackValue : value;
    }

    private boolean isPlaybackPlaying() {
        return "Playing".equalsIgnoreCase(currentPlaybackStatus);
    }

    private boolean isPlaybackLoading() {
        return "Loading".equalsIgnoreCase(currentPlaybackStatus);
    }

    private boolean isPlaybackActive() {
        return isPlaybackPlaying() || isPlaybackLoading();
    }
}
