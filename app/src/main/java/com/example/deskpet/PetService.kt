package com.example.deskpet

import android.app.*
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.speech.tts.TextToSpeech
import android.util.Base64
import android.view.*
import android.widget.ImageView
import java.util.Locale
import kotlin.random.Random

class PetService : Service(), TextToSpeech.OnInitListener {
    private lateinit var wm: WindowManager
    private var petView: ImageView? = null
    private lateinit var params: WindowManager.LayoutParams
    private var tts: TextToSpeech? = null

    private val poses = listOf("idle", "smile", "gesture", "wave", "proud", "lean")
    private val lines = listOf(
        "来直投",
        "来推饼",
        "来炸金花",
        "来打麻将",
        "压岁钱拿过来"
    )

    override fun onCreate() {
        super.onCreate()
        createChannel()
        startForeground(1, Notification.Builder(this, "deskpet")
            .setContentTitle("桌宠运行中")
            .setContentText("点击互动，拖动移动")
            .setSmallIcon(android.R.drawable.star_big_on)
            .build())

        tts = TextToSpeech(this, this)
        wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        showPet()
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(NotificationChannel("deskpet", "桌宠", NotificationManager.IMPORTANCE_LOW))
        }
    }

    private fun showPet() {
        if (petView != null) return
        val image = ImageView(this).apply {
            adjustViewBounds = true
            scaleType = ImageView.ScaleType.FIT_CENTER
            setImageBitmap(loadPose("idle"))
        }
        params = WindowManager.LayoutParams(
            360,
            520,
            if (Build.VERSION.SDK_INT >= 26) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 80
            y = 350
        }

        var downX = 0f
        var downY = 0f
        var startX = 0
        var startY = 0
        var moved = false

        image.setOnTouchListener { _, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN -> {
                    downX = e.rawX; downY = e.rawY
                    startX = params.x; startY = params.y
                    moved = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (e.rawX - downX).toInt()
                    val dy = (e.rawY - downY).toInt()
                    if (kotlin.math.abs(dx) > 8 || kotlin.math.abs(dy) > 8) moved = true
                    params.x = startX + dx
                    params.y = startY + dy
                    wm.updateViewLayout(image, params)
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!moved) interact()
                    true
                }
                else -> false
            }
        }

        petView = image
        wm.addView(image, params)
    }

    private fun interact() {
        val p = poses[Random.nextInt(poses.size)]
        petView?.setImageBitmap(loadPose(p))
        val line = lines[Random.nextInt(lines.size)]
        tts?.speak(line, TextToSpeech.QUEUE_FLUSH, null, "deskpet_line")
    }

    private fun loadPose(name: String): Bitmap? {
        return try {
            val b64 = assets.open("$name.b64").bufferedReader().use { it.readText() }
            val bytes = Base64.decode(b64, Base64.DEFAULT)
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        } catch (_: Exception) {
            null
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val r = tts?.setLanguage(Locale.SIMPLIFIED_CHINESE)
            if (r == TextToSpeech.LANG_MISSING_DATA || r == TextToSpeech.LANG_NOT_SUPPORTED) {
                tts?.language = Locale.CHINESE
            }
            tts?.setSpeechRate(1.05f)
            tts?.setPitch(1.1f)
        }
    }

    override fun onDestroy() {
        petView?.let { runCatching { wm.removeView(it) } }
        petView = null
        tts?.stop(); tts?.shutdown(); tts = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
