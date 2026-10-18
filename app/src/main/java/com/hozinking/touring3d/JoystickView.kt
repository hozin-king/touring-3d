package com.hozinking.touring3d

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import kotlin.math.hypot
import kotlin.math.min

/**
 * Joystick virtual: sumbu X = setir (-1 kiri .. 1 kanan).
 * Sumbu Y diabaikan (gas/rem pakai tombol).
 */
class JoystickView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    var onMove: ((Float) -> Unit)? = null

    private val basePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(110, 0, 0, 0)
        style = Paint.Style.FILL
    }
    private val knobPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(200, 255, 201, 60)
        style = Paint.Style.FILL
    }

    private var knobDX = 0f
    private var activePointer = -1

    override fun onDraw(canvas: Canvas) {
        val cx = width / 2f
        val cy = height / 2f
        val r = min(width, height) / 2f - 8f
        canvas.drawCircle(cx, cy, r, basePaint)
        canvas.drawCircle(cx + knobDX, cy, r * 0.45f, knobPaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val cx = width / 2f
        val r = min(width, height) / 2f - 8f
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                activePointer = event.getPointerId(event.actionIndex)
                parent.requestDisallowInterceptTouchEvent(true)
            }
            MotionEvent.ACTION_MOVE -> {
                val idx = event.findPointerIndex(activePointer)
                if (idx >= 0) {
                    var dx = event.getX(idx) - cx
                    dx = dx.coerceIn(-r, r)
                    knobDX = dx
                    onMove?.invoke(dx / r)
                    invalidate()
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP,
            MotionEvent.ACTION_CANCEL -> {
                if (event.getPointerId(event.actionIndex) == activePointer) {
                    activePointer = -1
                    knobDX = 0f
                    onMove?.invoke(0f)
                    invalidate()
                }
            }
        }
        return true
    }
}
