package com.oceanhunter.hunter

import android.content.pm.ActivityInfo
import android.os.Bundle
import android.view.KeyEvent
import android.view.Window
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

class MainActivity : ComponentActivity() {
    private var active by mutableStateOf(false)
    private var gameView: OceanView? = null
    private lateinit var store: ProgressStore
    private lateinit var sound: OceanAudio

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (resources.configuration.smallestScreenWidthDp < 600) {
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_USER_LANDSCAPE
        }
        WindowCompat.setDecorFitsSystemWindows(window, false)
        immerse()
        store = ProgressStore(this)
        sound = OceanAudio(this)
        setContent { OceanGame(store, sound, active, ::finish) { gameView = it } }
        val defaultCallback = window.callback
        window.callback = object : Window.Callback by defaultCallback {
            override fun dispatchKeyEvent(event: KeyEvent): Boolean {
                if (event.keyCode == KeyEvent.KEYCODE_ESCAPE) {
                    if (event.action == KeyEvent.ACTION_UP) onBackPressedDispatcher.onBackPressed()
                    return true
                }
                return gameView?.handleKey(event) == true || defaultCallback.dispatchKeyEvent(event)
            }
        }
    }

    private fun immerse() {
        WindowCompat.getInsetsController(window, window.decorView).apply {
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            hide(WindowInsetsCompat.Type.systemBars())
        }
    }
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) immerse()
    }
    override fun onResume() {
        super.onResume()
        active = true
        immerse()
    }
    override fun onPause() {
        gameView?.live = false
        active = false
        sound.suspend()
        super.onPause()
    }
    override fun onDestroy() {
        sound.close()
        super.onDestroy()
    }
}
