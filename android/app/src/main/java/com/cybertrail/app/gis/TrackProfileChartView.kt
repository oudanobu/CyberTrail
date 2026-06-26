package com.cybertrail.app.gis

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.View

class TrackProfileChartView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var dataPoints: List<ChartPoint> = emptyList()
    private var chartType = ChartType.ELEVATION

    enum class ChartType {
        ELEVATION, SPEED, DISTANCE
    }

    data class ChartPoint(
        val distanceKm: Float,
        val elevation: Float,
        val speed: Float
    )

    fun setData(points: List<ChartPoint>) {
        this.dataPoints = points
        invalidate()
    }

    fun setChartType(type: ChartType) {
        this.chartType = type
        invalidate()
    }

    private val linePaint = Paint().apply {
        style = Paint.Style.STROKE
        strokeWidth = 4f
        isAntiAlias = true
    }

    private val fillPaint = Paint().apply {
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    private val axisPaint = Paint().apply {
        color = 0x22FFFFFF
        style = Paint.Style.STROKE
        strokeWidth = 2f
        isAntiAlias = true
    }

    private val textPaint = Paint().apply {
        color = 0xFF94A3B8.toInt() // cyber_light_gray
        textSize = 24f
        isAntiAlias = true
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (dataPoints.isEmpty() || dataPoints.size < 2) {
            textPaint.textAlign = Paint.Align.CENTER
            canvas.drawText("暂无有效统计图表数据", width / 2f, height / 2f, textPaint)
            return
        }

        val paddingLeft = 80f
        val paddingRight = 40f
        val paddingTop = 40f
        val paddingBottom = 60f

        val chartWidth = width - paddingLeft - paddingRight
        val chartHeight = height - paddingTop - paddingBottom

        // Draw Axes
        canvas.drawLine(paddingLeft, paddingTop, paddingLeft, height - paddingBottom, axisPaint)
        canvas.drawLine(paddingLeft, height - paddingBottom, width - paddingRight, height - paddingBottom, axisPaint)

        val maxDist = dataPoints.last().distanceKm
        var maxVal = 0f
        var minVal = 0f

        when (chartType) {
            ChartType.ELEVATION -> {
                val elevations = dataPoints.map { it.elevation }
                maxVal = elevations.maxOrNull() ?: 0f
                minVal = elevations.minOrNull() ?: 0f
                val diff = maxVal - minVal
                if (diff < 10f) {
                    maxVal += 10f
                    minVal = Math.max(0f, minVal - 10f)
                } else {
                    maxVal += diff * 0.15f
                    minVal = Math.max(0f, minVal - diff * 0.15f)
                }
                linePaint.color = 0xFF22D3EE.toInt() // Cyan
            }
            ChartType.SPEED -> {
                val speeds = dataPoints.map { it.speed }
                maxVal = speeds.maxOrNull() ?: 0f
                minVal = speeds.minOrNull() ?: 0f
                val diff = maxVal - minVal
                if (diff < 1f) {
                    maxVal += 2f
                    minVal = 0f
                } else {
                    maxVal += diff * 0.15f
                    minVal = Math.max(0f, minVal - diff * 0.15f)
                }
                linePaint.color = 0xFFF59E0B.toInt() // Amber
            }
            ChartType.DISTANCE -> {
                maxVal = maxDist
                minVal = 0f
                if (maxVal < 1f) maxVal = 1f
                linePaint.color = 0xFF10B981.toInt() // Green
            }
        }

        // Y Axis Grid and Labels
        textPaint.textAlign = Paint.Align.RIGHT
        canvas.drawText("%.1f".format(maxVal), paddingLeft - 10f, paddingTop + 10f, textPaint)
        canvas.drawText("%.1f".format((maxVal + minVal) / 2f), paddingLeft - 10f, paddingTop + chartHeight / 2f + 8f, textPaint)
        canvas.drawText("%.1f".format(minVal), paddingLeft - 10f, height - paddingBottom, textPaint)

        // Draw horizontal grid lines
        canvas.drawLine(paddingLeft, paddingTop, width - paddingRight, paddingTop, axisPaint)
        canvas.drawLine(paddingLeft, paddingTop + chartHeight / 2f, width - paddingRight, paddingTop + chartHeight / 2f, axisPaint)

        // X Axis Labels
        textPaint.textAlign = Paint.Align.CENTER
        canvas.drawText("0.0k", paddingLeft, height - paddingBottom + 35f, textPaint)
        canvas.drawText("%.1fk".format(maxDist / 2f), paddingLeft + chartWidth / 2f, height - paddingBottom + 35f, textPaint)
        canvas.drawText("%.1fk".format(maxDist), width - paddingRight, height - paddingBottom + 35f, textPaint)

        // Draw Chart Paths
        val linePath = Path()
        val fillPath = Path()

        fillPath.moveTo(paddingLeft, height - paddingBottom)

        for (i in dataPoints.indices) {
            val pt = dataPoints[i]
            val pctX = if (maxDist > 0f) pt.distanceKm / maxDist else 0f
            val x = paddingLeft + pctX * chartWidth
            
            val value = when (chartType) {
                ChartType.ELEVATION -> pt.elevation
                ChartType.SPEED -> pt.speed
                ChartType.DISTANCE -> pt.distanceKm
            }
            
            val range = maxVal - minVal
            val yPct = if (range > 0f) (value - minVal) / range else 0.5f
            val y = height - paddingBottom - yPct * chartHeight

            if (i == 0) {
                linePath.moveTo(x, y)
                fillPath.lineTo(x, y)
            } else {
                linePath.lineTo(x, y)
                fillPath.lineTo(x, y)
            }

            if (i == dataPoints.size - 1) {
                fillPath.lineTo(x, height - paddingBottom)
            }
        }

        fillPath.close()

        val gradientColor = when (chartType) {
            ChartType.ELEVATION -> 0x2222D3EE
            ChartType.SPEED -> 0x22F59E0B
            ChartType.DISTANCE -> 0x2210B981
        }
        fillPaint.color = gradientColor
        
        canvas.drawPath(fillPath, fillPaint)
        canvas.drawPath(linePath, linePaint)
    }
}
