package com.cleanx.lcx.feature.printing.data

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface

/**
 * Renders a [LabelData] into a [Bitmap] suitable for a 62x29 mm DK-1209 label
 * at 300 DPI (696 x 342 pixels).
 *
 * Uses the Android [Canvas] API exclusively -- no external dependencies --
 * so this class can be tested on Robolectric / with an instrumented bitmap.
 */
object LabelRenderer {

    /** Label width in pixels (62 mm at 300 DPI). */
    const val LABEL_WIDTH = 696

    /** Label height in pixels (29 mm at 300 DPI). */
    const val LABEL_HEIGHT = 342

    private const val PADDING_LEFT = 24f
    private const val PADDING_TOP = 16f

    fun render(label: LabelData): Bitmap {
        val bitmap = Bitmap.createBitmap(LABEL_WIDTH, LABEL_HEIGHT, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)

        // -- Paints ----------------------------------------------------------

        val ticketNumberPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = 56f
            typeface = Typeface.DEFAULT_BOLD
        }

        val folioPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = 40f
            typeface = Typeface.DEFAULT_BOLD
        }

        val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = 32f
            typeface = Typeface.DEFAULT
        }

        val smallPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.DKGRAY
            textSize = 26f
            typeface = Typeface.DEFAULT
        }

        // -- Layout -----------------------------------------------------------

        var y = PADDING_TOP

        // Row 1: Ticket number (large, bold) — left-aligned
        y += ticketNumberPaint.textHeight()
        canvas.drawText(label.ticketNumber, PADDING_LEFT, y, ticketNumberPaint)

        // Folio in the top-right corner
        val folioText = "#${label.dailyFolio}"
        val folioWidth = folioPaint.measureText(folioText)
        canvas.drawText(folioText, LABEL_WIDTH - folioWidth - PADDING_LEFT, y, folioPaint)

        y += 16f

        // Row 2: Customer name
        y += bodyPaint.textHeight()
        canvas.drawText(
            label.customerName.ellipsize(bodyPaint, LABEL_WIDTH - 2 * PADDING_LEFT),
            PADDING_LEFT,
            y,
            bodyPaint,
        )

        y += 12f

        // Row 3: Service type
        y += bodyPaint.textHeight()
        canvas.drawText(label.serviceType, PADDING_LEFT, y, bodyPaint)

        y += 12f

        // Row 4: Date (smaller, gray)
        y += smallPaint.textHeight()
        canvas.drawText(label.date, PADDING_LEFT, y, smallPaint)

        return bitmap
    }

    // -- Helpers --------------------------------------------------------------

    /** Descent-aware text height for layout purposes. */
    private fun Paint.textHeight(): Float = -fontMetrics.ascent

    /**
     * Truncate [text] with an ellipsis if it is wider than [maxWidth] pixels
     * when measured with this paint.
     */
    private fun String.ellipsize(paint: Paint, maxWidth: Float): String {
        if (paint.measureText(this) <= maxWidth) return this
        val ellipsis = "..."
        val ellipsisWidth = paint.measureText(ellipsis)
        var end = length
        while (end > 0 && paint.measureText(this, 0, end) + ellipsisWidth > maxWidth) {
            end--
        }
        return substring(0, end) + ellipsis
    }
}
