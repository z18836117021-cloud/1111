package com.example.deskpet

import android.app.*
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.PixelFormat
import android.graphics.Rect
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
    private var sheet: Bitmap? = null

    private val poses = listOf("idle", "smile", "gesture", "wave", "proud", "lean")
    private val cropRects = mapOf(
        "idle" to Rect(50, 0, 178, 232),
        "smile" to Rect(232, 0, 352, 232),
        "gesture" to Rect(402, 21, 526, 232),
        "wave" to Rect(54, 211, 178, 450),
        "proud" to Rect(232, 211, 356, 450),
        "lean" to Rect(398, 224, 530, 450)
    )

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
        sheet = loadSheet()
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
                    downX = e.rawX
                    downY = e.rawY
                    startX = params.x
                    startY = params.y
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
        val p = poses.random()
        petView?.setImageBitmap(loadPose(p))
        val line = lines.random()
        tts?.speak(line, TextToSpeech.QUEUE_FLUSH, null, "deskpet_line")
    }

    private fun loadSheet(): Bitmap? {
        return try {
            val all = buildString {
                for (i in 0..9) {
                    val name = "pet_sheet.b64.part" + i.toString().padStart(2, '0')
                    append(assets.open(name).bufferedReader().use { it.readText() })
                }
            }
            val bytes = Base64.decode(all, Base64.DEFAULT)
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        } catch (_: Exception) {
            null
        }
    }

    private fun loadPose(name: String): Bitmap? {
        val source = sheet ?: return null
        val r = cropRects[name] ?: return null
        val left = r.left.coerceIn(0, source.width - 1)
        val top = r.top.coerceIn(0, source.height - 1)
        val right = r.right.coerceIn(left + 1, source.width)
        val bottom = r.bottom.coerceIn(top + 1, source.height)
        return Bitmap.createBitmap(source, left, top, right - left, bottom - top)
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
        tts?.stop()
        tts?.shutdown()
        tts = null
        sheet?.recycle()
        sheet = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
