package com.hozinking.touring3d

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private lateinit var game: GameView

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContentView(R.layout.activity_main)

        val hudBar = findViewById<LinearLayout>(R.id.hud_bar)
        val hudSpeed = findViewById<TextView>(R.id.hud_speed)
        val hudCp = findViewById<TextView>(R.id.hud_checkpoints)
        val hudTime = findViewById<TextView>(R.id.hud_time)
        val joystick = findViewById<JoystickView>(R.id.joystick)
        val pedalBox = findViewById<LinearLayout>(R.id.pedal_box)
        val btnGas = findViewById<Button>(R.id.btn_gas)
        val btnBrake = findViewById<Button>(R.id.btn_brake)
        val countdown = findViewById<TextView>(R.id.countdown_text)
        val menuPanel = findViewById<LinearLayout>(R.id.menu_panel)
        val finishPanel = findViewById<LinearLayout>(R.id.finish_panel)
        val bestLabel = findViewById<TextView>(R.id.best_label)

        findViewById<TextView>(R.id.tv_version).text =
            "v${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})"

        game = GameView(this, findViewById(R.id.filament_view))

        game.onHud = { kmh, time, visited, total ->
            hudSpeed.text = "$kmh km/h"
            hudTime.text = time
            hudCp.text = "\uD83D\uDCCD $visited/$total"
        }
        game.onToast = { msg -> Toast.makeText(this, msg, Toast.LENGTH_SHORT).show() }
        game.onCountdown = { label ->
            if (label.isEmpty()) countdown.visibility = View.GONE
            else {
                countdown.text = label
                countdown.visibility = View.VISIBLE
            }
        }
        game.onMenuBest = { s -> bestLabel.text = s }
        game.onFinish = { time, best, isNew ->
            finishPanel.visibility = View.VISIBLE
            findViewById<TextView>(R.id.finish_time).text = "Waktu: $time"
            findViewById<TextView>(R.id.finish_best).text =
                if (isNew) "Rekor baru! Tercepat: $best" else "Tercepat: $best"
            hudBar.visibility = View.GONE
            joystick.visibility = View.GONE
            pedalBox.visibility = View.GONE
        }

        joystick.onMove = { x -> game.setSteer(x) }

        fun hold(btn: Button, fn: (Boolean) -> Unit) {
            btn.setOnTouchListener { _, e ->
                when (e.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        fn(true)
                        btn.isPressed = true
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        fn(false)
                        btn.isPressed = false
                    }
                }
                true
            }
        }
        hold(btnGas) { game.setGas(it) }
        hold(btnBrake) { game.setBrake(it) }

        fun begin(trial: Boolean) {
            menuPanel.visibility = View.GONE
            finishPanel.visibility = View.GONE
            hudBar.visibility = View.VISIBLE
            joystick.visibility = View.VISIBLE
            pedalBox.visibility = View.VISIBLE
            game.startGame(trial)
        }
        findViewById<Button>(R.id.btn_start_tour).setOnClickListener { begin(false) }
        findViewById<Button>(R.id.btn_start_trial).setOnClickListener { begin(true) }
        findViewById<Button>(R.id.btn_again).setOnClickListener {
            finishPanel.visibility = View.GONE
            menuPanel.visibility = View.VISIBLE
            game.backToMenu()
        }

        // Init Filament + dunia itu berat: tunda sampai loading overlay kegambar
        // agar tidak ANR dan user tahu app sedang bekerja.
        findViewById<View>(R.id.filament_view).post {
            try {
                game.start()
            } catch (t: Throwable) {
                android.util.Log.e("Touring3D", "Gagal init game", t)
            }
            findViewById<View>(R.id.loading_overlay).visibility = View.GONE
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                    or View.SYSTEM_UI_FLAG_FULLSCREEN
                    or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                )
        }
    }

    override fun onResume() {
        super.onResume()
        if (::game.isInitialized) game.resume()
    }

    override fun onPause() {
        if (::game.isInitialized) game.pause()
        super.onPause()
    }

    override fun onDestroy() {
        if (::game.isInitialized) game.destroy()
        super.onDestroy()
    }
}
