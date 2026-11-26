package com.oceanhunter.hunter

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.SoundPool

internal enum class Track(val resource: Int) {
    MENU(R.raw.menu), EXPLORE(R.raw.explore), ACTION(R.raw.action), BOSS(R.raw.boss)
}

internal class OceanAudio(private val context: Context) {
    private val attrs = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build()
    private val soundPool = SoundPool.Builder().setAudioAttributes(attrs).setMaxStreams(6).build()
    private val cueIds = intArrayOf(R.raw.click, R.raw.fire, R.raw.blast, R.raw.coin, R.raw.treasure,
        R.raw.shield, R.raw.hurt, R.raw.upgrade, R.raw.win, R.raw.lose).map { soundPool.load(context, it, 1) }
    private var enableSfx = false
    private var foreground = false
    private var playing: Track? = null
    private var player: MediaPlayer? = null
    private var closed = false

    fun update(track: Track?, progress: Progress, active: Boolean) {
        if (closed) return
        enableSfx = progress.sfx
        foreground = active
        if (active && enableSfx) soundPool.autoResume() else soundPool.autoPause()
        val wanted = if (active && progress.music) track else null
        if (playing == wanted) return
        player?.release()
        player = null
        playing = wanted
        if (wanted == null) return
        player = MediaPlayer.create(context, wanted.resource, attrs, 0)?.apply {
            isLooping = true
            setVolume(.43f, .43f)
            start()
        }
    }

    fun play(cue: Cue) {
        if (!closed && foreground && enableSfx) {
            soundPool.play(cueIds[cue.ordinal], .56f, .56f, 1, 0, 1f)
        }
    }
    fun suspend() {
        foreground = false
        soundPool.autoPause()
        player?.pause()
        playing = null
    }
    fun close() {
        closed = true
        player?.release()
        player = null
        soundPool.release()
    }
}
