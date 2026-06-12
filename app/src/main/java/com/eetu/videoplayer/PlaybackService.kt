package com.eetu.videoplayer

import android.content.Intent
import android.os.Bundle
import androidx.annotation.OptIn
import androidx.media3.common.Effect
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.Brightness
import androidx.media3.effect.Contrast
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture

@OptIn(UnstableApi::class)
class PlaybackService : MediaSessionService() {
    private var mediaSession: MediaSession? = null

    companion object {
        const val ACTION_SET_VIDEO_EFFECTS = "SET_VIDEO_EFFECTS"
        const val KEY_BRIGHTNESS = "KEY_BRIGHTNESS"
        const val KEY_CONTRAST = "KEY_CONTRAST"
    }

    override fun onCreate() {
        super.onCreate()
        
        val renderersFactory = DefaultRenderersFactory(this).apply {
            setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_OFF)
            forceEnableMediaCodecAsynchronousQueueing()
        }
        
        val player = ExoPlayer.Builder(this, renderersFactory).build()
            
        mediaSession = MediaSession.Builder(this, player)
            .setCallback(MediaSessionCallback())
            .build()
    }

    private inner class MediaSessionCallback : MediaSession.Callback {
        override fun onCustomCommand(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            customCommand: SessionCommand,
            args: Bundle
        ): ListenableFuture<SessionResult> {
            if (customCommand.customAction == ACTION_SET_VIDEO_EFFECTS) {
                val brightness = args.getFloat(KEY_BRIGHTNESS, 0f)
                val contrast = args.getFloat(KEY_CONTRAST, 0f)
                
                val effects = mutableListOf<Effect>()
                if (brightness != 0f) effects.add(Brightness(brightness))
                if (contrast != 0f) effects.add(Contrast(contrast))
                
                (session.player as? ExoPlayer)?.setVideoEffects(effects)
                return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
            }
            return super.onCustomCommand(session, controller, customCommand, args)
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return mediaSession
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = mediaSession?.player
        if (player != null) {
            if (!player.playWhenReady || player.mediaItemCount == 0) {
                stopSelf()
            }
        }
    }

    override fun onDestroy() {
        mediaSession?.run {
            player.release()
            release()
            mediaSession = null
        }
        super.onDestroy()
    }
}
