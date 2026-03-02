package com.cleanx.lcx.feature.printing

import android.graphics.Bitmap
import com.cleanx.lcx.feature.printing.data.LabelData
import com.cleanx.lcx.feature.printing.data.LabelRenderer
import com.cleanx.lcx.feature.printing.data.LabelVariant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class LabelRendererTest {

    private val sampleLabel = LabelData(
        ticketNumber = "T-20260302-0001",
        customerName = "Juan Perez",
        serviceType = "wash-fold",
        date = "2026-03-02",
        dailyFolio = 1,
    )

    // -- Standard variant -----------------------------------------------------

    @Test
    fun `rendered bitmap has correct dimensions 696x342`() {
        val bitmap = LabelRenderer.render(sampleLabel)

        assertNotNull(bitmap)
        assertEquals(LabelRenderer.LABEL_WIDTH, bitmap.width)
        assertEquals(LabelRenderer.LABEL_HEIGHT, bitmap.height)
    }

    @Test
    fun `rendered bitmap is ARGB_8888 config`() {
        val bitmap = LabelRenderer.render(sampleLabel)

        assertEquals(Bitmap.Config.ARGB_8888, bitmap.config)
    }

    @Test
    fun `render does not throw with long customer name`() {
        val longNameLabel = sampleLabel.copy(
            customerName = "A".repeat(200),
        )
        val bitmap = LabelRenderer.render(longNameLabel)

        assertNotNull(bitmap)
        assertEquals(LabelRenderer.LABEL_WIDTH, bitmap.width)
        assertEquals(LabelRenderer.LABEL_HEIGHT, bitmap.height)
    }

    @Test
    fun `render handles empty strings`() {
        val emptyLabel = LabelData(
            ticketNumber = "",
            customerName = "",
            serviceType = "",
            date = "",
            dailyFolio = 0,
        )
        val bitmap = LabelRenderer.render(emptyLabel)

        assertNotNull(bitmap)
        assertEquals(LabelRenderer.LABEL_WIDTH, bitmap.width)
        assertEquals(LabelRenderer.LABEL_HEIGHT, bitmap.height)
    }

    // -- Compact variant ------------------------------------------------------

    @Test
    fun `compact variant renders with correct dimensions 342x1062`() {
        val bitmap = LabelRenderer.render(sampleLabel, LabelVariant.COMPACT)

        assertNotNull(bitmap)
        assertEquals(LabelRenderer.COMPACT_LABEL_WIDTH, bitmap.width)
        assertEquals(LabelRenderer.COMPACT_LABEL_HEIGHT, bitmap.height)
    }

    @Test
    fun `compact variant bitmap is ARGB_8888 config`() {
        val bitmap = LabelRenderer.render(sampleLabel, LabelVariant.COMPACT)

        assertEquals(Bitmap.Config.ARGB_8888, bitmap.config)
    }

    @Test
    fun `compact variant does not throw with long customer name`() {
        val longNameLabel = sampleLabel.copy(
            customerName = "A".repeat(200),
        )
        val bitmap = LabelRenderer.render(longNameLabel, LabelVariant.COMPACT)

        assertNotNull(bitmap)
        assertEquals(LabelRenderer.COMPACT_LABEL_WIDTH, bitmap.width)
        assertEquals(LabelRenderer.COMPACT_LABEL_HEIGHT, bitmap.height)
    }

    @Test
    fun `compact variant handles empty strings`() {
        val emptyLabel = LabelData(
            ticketNumber = "",
            customerName = "",
            serviceType = "",
            date = "",
            dailyFolio = 0,
        )
        val bitmap = LabelRenderer.render(emptyLabel, LabelVariant.COMPACT)

        assertNotNull(bitmap)
        assertEquals(LabelRenderer.COMPACT_LABEL_WIDTH, bitmap.width)
        assertEquals(LabelRenderer.COMPACT_LABEL_HEIGHT, bitmap.height)
    }

    // -- Explicit variant parameter -------------------------------------------

    @Test
    fun `standard variant explicitly produces same as default`() {
        val defaultBitmap = LabelRenderer.render(sampleLabel)
        val explicitBitmap = LabelRenderer.render(sampleLabel, LabelVariant.STANDARD)

        assertEquals(defaultBitmap.width, explicitBitmap.width)
        assertEquals(defaultBitmap.height, explicitBitmap.height)
    }

    // -- Preview --------------------------------------------------------------

    @Test
    fun `renderPreview returns half-scale bitmap for standard variant`() {
        val preview = LabelRenderer.renderPreview(sampleLabel, LabelVariant.STANDARD)

        assertNotNull(preview)
        assertEquals(LabelRenderer.LABEL_WIDTH / 2, preview.width)
        assertEquals(LabelRenderer.LABEL_HEIGHT / 2, preview.height)
    }

    @Test
    fun `renderPreview returns half-scale bitmap for compact variant`() {
        val preview = LabelRenderer.renderPreview(sampleLabel, LabelVariant.COMPACT)

        assertNotNull(preview)
        assertEquals(LabelRenderer.COMPACT_LABEL_WIDTH / 2, preview.width)
        assertEquals(LabelRenderer.COMPACT_LABEL_HEIGHT / 2, preview.height)
    }
}
