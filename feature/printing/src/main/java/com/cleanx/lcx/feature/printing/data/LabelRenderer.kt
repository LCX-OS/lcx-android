package com.cleanx.lcx.feature.printing.data

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

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

    private const val STANDARD_MARGIN = 10f
    private const val STANDARD_PADDING_X = 18f
    private const val STANDARD_PADDING_Y = 14f
    private const val STANDARD_BORDER_WIDTH = 3f

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

        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            style = Paint.Style.STROKE
            strokeWidth = STANDARD_BORDER_WIDTH
        }

        val ticketPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = 44f
            typeface = Typeface.DEFAULT_BOLD
        }

        val bagPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = 29f
            typeface = Typeface.DEFAULT_BOLD
        }

        val customerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = 38f
            typeface = Typeface.DEFAULT_BOLD
        }

        val paymentPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = 25f
            typeface = Typeface.DEFAULT_BOLD
        }

        val metaPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = 24f
            typeface = Typeface.DEFAULT
        }

        val footerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = 22f
            typeface = Typeface.DEFAULT_BOLD
        }

        val outer = RectF(
            STANDARD_MARGIN,
            STANDARD_MARGIN,
            LABEL_WIDTH - STANDARD_MARGIN,
            LABEL_HEIGHT - STANDARD_MARGIN,
        )
        canvas.drawRect(outer, borderPaint)

        val left = STANDARD_MARGIN + STANDARD_PADDING_X
        val right = LABEL_WIDTH - STANDARD_MARGIN - STANDARD_PADDING_X
        val top = STANDARD_MARGIN + STANDARD_PADDING_Y
        val bottom = LABEL_HEIGHT - STANDARD_MARGIN - STANDARD_PADDING_Y

        val bagText = label.bagLabel()
        val bagWidth = bagPaint.measureText(bagText)
        val ticketMaxWidth = (right - left - bagWidth - 16f).coerceAtLeast(80f)
        val headerBaseline = top + ticketPaint.textHeight()
        canvas.drawText(
            label.ticketNumber.ellipsize(ticketPaint, ticketMaxWidth),
            left,
            headerBaseline,
            ticketPaint,
        )
        canvas.drawText(bagText, right - bagWidth, headerBaseline, bagPaint)

        val customerBaseline = headerBaseline + 58f
        canvas.drawText(
            label.customerName.ifBlank { "Cliente" }.ellipsize(customerPaint, right - left),
            left,
            customerBaseline,
            customerPaint,
        )

        val paymentBaseline = customerBaseline + 38f
        canvas.drawText(
            label.safePaymentLabel().ellipsize(paymentPaint, right - left),
            left,
            paymentBaseline,
            paymentPaint,
        )

        val deliveryBaseline = paymentBaseline + 34f
        canvas.drawText(
            label.deliveryLabel().ellipsize(metaPaint, right - left),
            left,
            deliveryBaseline,
            metaPaint,
        )

        val footerBaseline = bottom
        canvas.drawText(label.shortIdLabel(), left, footerBaseline, footerPaint)
        val copyText = label.copyLabel()
        canvas.drawText(copyText, right - footerPaint.measureText(copyText), footerBaseline, footerPaint)

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

        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            style = Paint.Style.STROKE
            strokeWidth = 3f
        }

        val ticketPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = 34f
            typeface = Typeface.DEFAULT_BOLD
        }

        val bagPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = 30f
            typeface = Typeface.DEFAULT_BOLD
        }

        val customerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = 30f
            typeface = Typeface.DEFAULT_BOLD
        }

        val paymentPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = 24f
            typeface = Typeface.DEFAULT_BOLD
        }

        val metaPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = 22f
            typeface = Typeface.DEFAULT
        }

        val footerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = 20f
            typeface = Typeface.DEFAULT
        }

        val margin = 10f
        val padding = 16f
        val outer = RectF(margin, margin, COMPACT_LABEL_WIDTH - margin, COMPACT_LABEL_HEIGHT - margin)
        canvas.drawRect(outer, borderPaint)

        val left = margin + padding
        val right = COMPACT_LABEL_WIDTH - margin - padding
        val bottom = COMPACT_LABEL_HEIGHT - margin - padding
        var y = margin + padding + ticketPaint.textHeight()
        canvas.drawText(
            label.ticketNumber.ellipsize(ticketPaint, right - left),
            left,
            y,
            ticketPaint,
        )

        y += 48f
        canvas.drawText(label.bagLabel(), left, y, bagPaint)

        y += 52f
        canvas.drawText(
            label.customerName.ifBlank { "Cliente" }.ellipsize(customerPaint, right - left),
            left,
            y,
            customerPaint,
        )

        y += 38f
        canvas.drawText(
            label.safePaymentLabel().ellipsize(paymentPaint, right - left),
            left,
            y,
            paymentPaint,
        )

        y += 34f
        canvas.drawText(label.deliveryLabel().ellipsize(metaPaint, right - left), left, y, metaPaint)

        canvas.drawText(label.shortIdLabel(), left, bottom - 28f, footerPaint)
        canvas.drawText(label.copyLabel(), left, bottom, footerPaint)

        return bitmap
    }

    // -- Helpers --------------------------------------------------------------

    /** Descent-aware text height for layout purposes. */
    private fun Paint.textHeight(): Float = -fontMetrics.ascent

    private fun LabelData.bagLabel(): String {
        val safeTotal = totalBags.coerceIn(LabelData.MIN_BAG_COUNT, LabelData.MAX_BAG_COUNT)
        val safeBag = bagNumber.coerceIn(LabelData.MIN_BAG_COUNT, safeTotal)
        return "Bolsa $safeBag/$safeTotal"
    }

    private fun LabelData.copyLabel(): String = "Copia ${copyNumber.coerceAtLeast(1)}"

    private fun LabelData.deliveryLabel(): String = "Entrega ${formatShortDate(promisedPickupDate)}"

    private fun LabelData.safePaymentLabel(): String =
        paymentLabel.trim().ifEmpty { LabelData.DEFAULT_PAYMENT_LABEL }

    private fun LabelData.shortIdLabel(): String {
        val shortId = ticketId
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?.take(8)
            ?.uppercase(Locale.US)
            ?: "--"
        return "ID $shortId"
    }

    private fun formatShortDate(dateValue: String?): String {
        val raw = dateValue?.trim().orEmpty()
        if (raw.isEmpty()) return "Sin fecha"

        val datePart = raw
            .substringBefore("T")
            .substringBefore(" ")
            .takeIf { it.isNotBlank() }
            ?: return "Sin fecha"

        return runCatching {
            LocalDate.parse(datePart).format(SHORT_DATE_FORMATTER)
        }.getOrDefault("Sin fecha")
    }

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

    private val SHORT_DATE_FORMATTER: DateTimeFormatter =
        DateTimeFormatter.ofPattern("dd/MM/yy", Locale.forLanguageTag("es-MX"))
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
