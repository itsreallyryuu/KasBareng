package com.app.kasbareng.ui.splash

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.os.SystemClock
import android.util.AttributeSet
import android.util.TypedValue
import android.view.View
import androidx.core.content.ContextCompat
import com.app.kasbareng.R
import kotlin.math.PI
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/** Splash yang digambar per frame (berbasis waktu), tetap bergerak walau skala animasi sistem dimatikan. */
class SplashView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    private fun dp(v: Float) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, resources.displayMetrics)
    private fun sp(v: Float) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, v, resources.displayMetrics)

    private val accent = ContextCompat.getColor(context, R.color.primary)
    private val onBrand = ContextCompat.getColor(context, R.color.on_brand)
    private val logo = ContextCompat.getDrawable(context, R.drawable.ic_launcher_foreground)?.mutate()

    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(2f)
        color = accent
    }
    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(4f)
        color = onBrand
    }
    private val arcPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(4f)
        strokeCap = Paint.Cap.ROUND
        color = accent
    }
    private val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = onBrand
        textSize = sp(26f)
        typeface = Typeface.DEFAULT_BOLD
        textAlign = Paint.Align.CENTER
    }
    private val subPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = onBrand
        textSize = sp(14f)
        textAlign = Paint.Align.CENTER
    }
    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = accent }
    private val oval = RectF()
    private val twoPi = (2.0 * PI).toFloat()
    private var startMs = 0L

    private fun easeOutBack(x: Float): Float {
        val c1 = 1.70158f
        val c3 = c1 + 1f
        val y = x - 1f
        return 1f + c3 * y * y * y + c1 * y * y
    }

    override fun onDraw(c: Canvas) {
        super.onDraw(c)
        val now = SystemClock.uptimeMillis()
        if (startMs == 0L) startMs = now
        val t = (now - startMs) / 1000f

        val cx = width / 2f
        val cy = height * 0.40f
        val half = dp(60f)

        val intro = min(t / 0.8f, 1f)
        val scaleIn = 0.6f + 0.4f * easeOutBack(intro)
        val alphaIn = min(t / 0.5f, 1f)
        val breath = if (t > 0.8f) 1f + 0.035f * sin(twoPi * (t - 0.8f) / 1.8f) else 1f

        // Gelombang yang melebar
        for (i in 0..1) {
            val phase = ((t / 2.0f) + i * 0.5f) % 1f
            val r = half * (1.3f + 0.7f * phase)
            ringPaint.alpha = (alphaIn * 120f * (1f - phase)).toInt().coerceIn(0, 255)
            c.drawCircle(cx, cy, r, ringPaint)
        }

        // Cincin berputar
        val rr = half * 1.18f
        trackPaint.alpha = (alphaIn * 40f).toInt()
        c.drawCircle(cx, cy, rr, trackPaint)
        oval.set(cx - rr, cy - rr, cx + rr, cy + rr)
        val angle = (t * 330f) % 360f
        val sweep = 80f + 40f * sin(twoPi * t / 1.2f)
        arcPaint.alpha = (alphaIn * 255f).toInt()
        c.drawArc(oval, angle, sweep, false, arcPaint)

        // Logo
        logo?.let { d ->
            val h = half.toInt()
            d.setBounds(-h, -h, h, h)
            d.alpha = (alphaIn * 255f).toInt()
            c.save()
            c.translate(cx, cy)
            val s = scaleIn * breath
            c.scale(s, s)
            d.draw(c)
            c.restore()
        }

        // Teks
        val titleY = cy + half * 2.0f + dp(44f)
        val textA = ((t - 0.35f) / 0.5f).coerceIn(0f, 1f)
        val slide = (1f - textA) * dp(14f)
        titlePaint.alpha = (textA * 255f).toInt()
        c.drawText("KasBareng", cx, titleY + slide, titlePaint)
        subPaint.alpha = (textA * 190f).toInt()
        c.drawText("Kas kelas, rapi dan transparan", cx, titleY + dp(28f) + slide, subPaint)

        // Titik loading yang melompat
        val dotsA = ((t - 0.7f) / 0.4f).coerceIn(0f, 1f)
        val dotsY = titleY + dp(76f)
        for (i in 0..2) {
            val v = max(0f, sin(twoPi * (t * 1.1f - i * 0.16f)))
            dotPaint.alpha = (255f * dotsA * (0.35f + 0.65f * v)).toInt().coerceIn(0, 255)
            c.drawCircle(cx + (i - 1) * dp(18f), dotsY - v * dp(7f), dp(4.5f), dotPaint)
        }

        postInvalidateOnAnimation()
    }
}