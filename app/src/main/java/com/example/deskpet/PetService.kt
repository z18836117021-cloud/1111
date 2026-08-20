package com.example.deskpet

import android.app.*
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Base64
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import kotlin.math.abs

class PetService : Service() {
    private lateinit var wm: WindowManager
    private var rootView: LinearLayout? = null
    private var petView: ImageView? = null
    private var bubbleView: TextView? = null
    private lateinit var params: WindowManager.LayoutParams
    private var sheet: Bitmap? = null
    private val handler = Handler(Looper.getMainLooper())

    private val poses = listOf("idle", "smile", "gesture", "wave", "proud", "lean")

    // Tight crop rectangles based on each independent alpha component in the 600x450 sprite sheet.
    // These deliberately avoid overlap between the top and bottom rows.
    private val cropRects = mapOf(
        "idle" to Rect(73, 0, 175, 226),
        "smile" to Rect(249, 0, 355, 225),
        "gesture" to Rect(425, 32, 535, 229),
        "wave" to Rect(66, 226, 181, 450),
        "proud" to Rect(243, 227, 366, 450),
        "lean" to Rect(414, 233, 540, 450)
    )

    private val lines = listOf(
        "来直投",
        "来推饼",
        "来炸金花",
        "来打麻将",
        "压岁钱拿过来"
    )

    private val hideBubble = Runnable {
        bubbleView?.visibility = View.GONE
    }

    override fun onCreate() {
        super.onCreate()
        createChannel()
        startForeground(
            1,
            Notification.Builder(this, "deskpet")
                .setContentTitle("桌宠运行中")
                .setContentText("点击互动，拖动移动")
                .setSmallIcon(android.R.drawable.star_big_on)
                .build()
        )

        wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        sheet = loadSheet()
        showPet()
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(
                NotificationChannel("deskpet", "桌宠", NotificationManager.IMPORTANCE_LOW)
            )
        }
    }

    private fun showPet() {
        if (rootView != null) return

        val bubble = TextView(this).apply {
            textSize = 17f
            setTextColor(Color.BLACK)
            gravity = Gravity.CENTER
            setPadding(26, 16, 26, 16)
            visibility = View.GONE
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 28f
                setColor(Color.WHITE)
                setStroke(3, Color.rgb(55, 55, 55))
            }
            elevation = 10f
        }

        val image = ImageView(this).apply {
            adjustViewBounds = true
            scaleType = ImageView.ScaleType.FIT_CENTER
            setImageBitmap(loadPose("idle"))
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(8, 8, 8, 8)
            addView(
                bubble,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    bottomMargin = 4
                }
            )
            addView(
                image,
                LinearLayout.LayoutParams(320, 430)
            )
        }

        params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= 26)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 80
            y = 300
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
                    if (abs(dx) > 8 || abs(dy) > 8) moved = true
                    params.x = startX + dx
                    params.y = startY + dy
                    wm.updateViewLayout(root, params)
                    true
                }

                MotionEvent.ACTION_UP -> {
                    if (!moved) interact()
                    true
                }

                else -> false
            }
        }

        bubbleView = bubble
        petView = image
        rootView = root
        wm.addView(root, params)
    }

    private fun interact() {
        petView?.setImageBitmap(loadPose(poses.random()))
        showBubble(lines.random())
    }

    private fun showBubble(text: String) {
        val bubble = bubbleView ?: return
        bubble.text = text
        bubble.visibility = View.VISIBLE
        handler.removeCallbacks(hideBubble)
        handler.postDelayed(hideBubble, 2400)
    }

    private fun loadSheet(): Bitmap? {
        return try {
            val all = buildString {
                for (i in 0..5) {
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

    override fun onDestroy() {
        handler.removeCallbacks(hideBubble)
        rootView?.let { runCatching { wm.removeView(it) } }
        rootView = null
        petView = null
        bubbleView = null
        sheet?.recycle()
        sheet = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
