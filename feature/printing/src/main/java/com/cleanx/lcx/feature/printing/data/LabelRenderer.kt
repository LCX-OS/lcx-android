package com.cleanx.lcx.feature.printing.data

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface

/**
 * Renders a [LabelData] into a [Bitmap] suitable for Brother label printers.
 *
 * Supports two variants via [LabelVariant]:
 * - [LabelVariant.STANDARD] — 62x29 mm DK-1209 at 300 DPI (696 x 342 px)
 * - [LabelVariant.COMPACT]  — 29x90 mm DK-1201 at 300 DPI (342 x 1062 px)
 *
 * Uses the Android [Canvas] API exclusively -- no external dependencies --
 * so this class can be tested on Robolectric / with an instrumented bitmap.
 */
object LabelRenderer {

    // -- Standard label: DK-1209 62x29 mm at 300 DPI -------------------------

    /** Label width in pixels (62 mm at 300 DPI). */
    const val LABEL_WIDTH = 696

    /** Label height in pixels (29 mm at 300 DPI). */
    const val LABEL_HEIGHT = 342

    // -- Compact label: DK-1201 29x90 mm at 300 DPI --------------------------

    /** Compact label width in pixels (29 mm at 300 DPI). */
    const val COMPACT_LABEL_WIDTH = 342

    /** Compact label height in pixels (90 mm at 300 DPI). */
    const val COMPACT_LABEL_HEIGHT = 1062

    private const val PADDING_LEFT = 24f
    private const val PADDING_TOP = 16f

    /**
     * Renders [label] using the specified [variant].
     * Defaults to [LabelVariant.STANDARD].
     */
    fun render(label: LabelData, variant: LabelVariant = LabelVariant.STANDARD): Bitmap {
        return when (variant) {
            LabelVariant.STANDARD -> renderStandard(label)
            LabelVariant.COMPACT -> renderCompact(label)
        }
    }

    /**
     * Returns a preview bitmap at 1/2 scale suitable for display in
     * the printer settings screen.
     */
    fun renderPreview(label: LabelData, variant: LabelVariant = LabelVariant.STANDARD): Bitmap {
        val full = render(label, variant)
        return Bitmap.createScaledBitmap(
            full,
            full.width / 2,
            full.height / 2,
            true,
        )
    }

    // -- Standard layout (DK-1209 62x29 mm) -----------------------------------

    private fun renderStandard(label: LabelData): Bitmap {
        val bitmap = Bitmap.createBitmap(LABEL_WIDTH, LABEL_HEIGHT, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)

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

        var y = PADDING_TOP

        // Row 1: Ticket number (large, bold) + Folio top-right
        y += ticketNumberPaint.textHeight()
        canvas.drawText(label.ticketNumber, PADDING_LEFT, y, ticketNumberPaint)

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

    // -- Compact layout (DK-1201 29x90 mm) ------------------------------------

    private fun renderCompact(label: LabelData): Bitmap {
        val bitmap = Bitmap.createBitmap(
            COMPACT_LABEL_WIDTH,
            COMPACT_LABEL_HEIGHT,
            Bitmap.Config.ARGB_8888,
        )
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)

        val ticketPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = 36f
            typeface = Typeface.DEFAULT_BOLD
        }

        val folioPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = 28f
            typeface = Typeface.DEFAULT_BOLD
        }

        val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = 24f
            typeface = Typeface.DEFAULT
        }

        val smallPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.DKGRAY
            textSize = 20f
            typeface = Typeface.DEFAULT
        }

        val padding = 16f
        var y = padding

        // Row 1: Ticket number
        y += ticketPaint.textHeight()
        canvas.drawText(
            label.ticketNumber.ellipsize(ticketPaint, COMPACT_LABEL_WIDTH - 2 * padding),
            padding,
            y,
            ticketPaint,
        )

        y += 12f

        // Row 2: Folio
        y += folioPaint.textHeight()
        val folioText = "#${label.dailyFolio}"
        canvas.drawText(folioText, padding, y, folioPaint)

        y += 10f

        // Row 3: Customer name
        y += bodyPaint.textHeight()
        canvas.drawText(
            label.customerName.ellipsize(bodyPaint, COMPACT_LABEL_WIDTH - 2 * padding),
            padding,
            y,
            bodyPaint,
        )

        y += 10f

        // Row 4: Service type
        y += bodyPaint.textHeight()
        canvas.drawText(
            label.serviceType.ellipsize(bodyPaint, COMPACT_LABEL_WIDTH - 2 * padding),
            padding,
            y,
            bodyPaint,
        )

        y += 10f

        // Row 5: Date
        y += smallPaint.textHeight()
        canvas.drawText(label.date, padding, y, smallPaint)

        return bitmap
    }

    // -- Helpers --------------------------------------------------------------

    /** Descent-aware text height for layout purposes. */
    private fun Paint.textHeight(): Float = -fontMetrics.ascent

    /**
     * Truncate [this] string with an ellipsis if it is wider than [maxWidth] pixels
     * when measured with [paint].
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

/**
 * Label size variant.
 *
 * Each variant targets a specific Brother DK label cartridge.
 */
enum class LabelVariant {
    /** DK-1209 — 62 x 29 mm (standard ticket label). */
    STANDARD,

    /** DK-1201 — 29 x 90 mm (compact / address-style label). */
    COMPACT,
}
