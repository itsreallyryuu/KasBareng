package com.app.kasbareng.ui.reports

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import android.graphics.RectF
import android.util.AttributeSet
import android.util.TypedValue
import android.view.View
import androidx.core.content.ContextCompat
import com.app.kasbareng.R
import com.app.kasbareng.domain.ChartBucket
import kotlin.math.max
import kotlin.math.min

class CashChartView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    enum class Mode { BARS, LINE }

    var mode: Mode = Mode.BARS
        set(value) {
            field = value
            invalidate()
        }

    private var data: List<ChartBucket> = emptyList()

    private fun dp(v: Float) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, resources.displayMetrics)
    private fun sp(v: Float) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, v, resources.displayMetrics)

    private val cIncome = ContextCompat.getColor(context, R.color.status_lunas)
    private val cExpense = ContextCompat.getColor(context, R.color.status_nunggak)
    private val cLine = ContextCompat.getColor(context, R.color.accent_text)
    private val cGrid = ContextCompat.getColor(context, R.color.card_stroke)
    private val cText = ContextCompat.getColor(context, R.color.text_secondary)

    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = cGrid
        strokeWidth = dp(1f)
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = cText
        textSize = sp(10f)
    }
    private val valuePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = cLine
        textSize = sp(11f)
        isFakeBoldText = true
    }
    private val barPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = cLine
        strokeWidth = dp(2.5f)
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = cLine }
    private val areaPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = cLine
        alpha = 30
    }
    private val path = Path()
    private val area = Path()
    private val rect = RectF()

    fun setData(list: List<ChartBucket>) {
        data = list
        invalidate()
    }

    private fun compact(v: Long): String = when {
        v >= 1_000_000L -> {
            val x = v / 100_000L / 10.0
            (if (x % 1.0 == 0.0) x.toLong().toString() else x.toString().replace('.', ',')) + "jt"
        }
        v >= 1_000L -> "${v / 1000}rb"
        else -> v.toString()
    }

    override fun onDraw(c: Canvas) {
        super.onDraw(c)
        if (data.isEmpty()) return

        val left = dp(8f)
        val right = width - dp(8f)
        val top = dp(18f)
        val bottom = height - dp(22f)
        val w = right - left
        val h = bottom - top
        if (w <= 0f || h <= 0f) return

        val n = data.size
        val slot = w / n
        val balances = data.mapNotNull { it.balanceEnd }

        val maxV: Long
        val minV: Long
        if (mode == Mode.BARS) {
            maxV = max(data.maxOf { max(it.income, it.expense) }, 1L)
            minV = 0L
        } else {
            maxV = max(balances.maxOrNull() ?: 0L, 1L)
            minV = min(balances.minOrNull() ?: 0L, 0L)
        }
        val span = (maxV - minV).toFloat()

        // Garis bantu + angka sumbu
        textPaint.textAlign = Paint.Align.LEFT
        for (i in 0..2) {
            val y = bottom - h * i / 2f
            c.drawLine(left, y, right, y, gridPaint)
            val value = minV + (maxV - minV) * i / 2
            c.drawText(compact(value), left, y - dp(3f), textPaint)
        }
        // Label sumbu X
        textPaint.textAlign = Paint.Align.CENTER
        data.forEachIndexed { i, b ->
            c.drawText(b.label, left + slot * (i + 0.5f), height - dp(6f), textPaint)
        }

        val empty = if (mode == Mode.BARS) data.all { it.income == 0L && it.expense == 0L }
        else balances.isEmpty()
        if (empty) {
            c.drawText("Belum ada transaksi", width / 2f, top + h / 2f, textPaint)
            return
        }

        if (mode == Mode.BARS) {
            val bw = slot * 0.32f
            val gap = dp(1f)
            data.forEachIndexed { i, b ->
                val cx = left + slot * (i + 0.5f)
                drawBar(c, cx - gap - bw, cx - gap, bottom, h, b.income, maxV, cIncome)
                drawBar(c, cx + gap, cx + gap + bw, bottom, h, b.expense, maxV, cExpense)
            }
        } else {
            val pts = ArrayList<PointF>()
            data.forEachIndexed { i, b ->
                val bal = b.balanceEnd ?: return@forEachIndexed
                pts.add(PointF(left + slot * (i + 0.5f), bottom - (bal - minV).toFloat() / span * h))
            }
            if (pts.isEmpty()) return
            path.reset()
            area.reset()
            pts.forEachIndexed { i, p ->
                if (i == 0) {
                    path.moveTo(p.x, p.y)
                    area.moveTo(p.x, bottom)
                    area.lineTo(p.x, p.y)
                } else {
                    path.lineTo(p.x, p.y)
                    area.lineTo(p.x, p.y)
                }
            }
            area.lineTo(pts.last().x, bottom)
            area.close()
            c.drawPath(area, areaPaint)
            c.drawPath(path, linePaint)
            pts.forEach { c.drawCircle(it.x, it.y, dp(3.5f), dotPaint) }

            val last = pts.last()
            val lastValue = balances.last()
            val label = "Rp" + compact(lastValue)
            valuePaint.textAlign = Paint.Align.CENTER
            val half = valuePaint.measureText(label) / 2f
            val tx = last.x.coerceIn(left + half, right - half)
            c.drawText(label, tx, max(last.y - dp(8f), top - dp(2f)), valuePaint)
        }
    }

    private fun drawBar(
        c: Canvas, l: Float, r: Float, bottom: Float, h: Float, v: Long, maxV: Long, color: Int
    ) {
        if (v <= 0L) return
        val bh = max(v.toFloat() / maxV * h, dp(2f))
        barPaint.color = color
        rect.set(l, bottom - bh, r, bottom)
        c.drawRoundRect(rect, dp(3f), dp(3f), barPaint)
    }
}