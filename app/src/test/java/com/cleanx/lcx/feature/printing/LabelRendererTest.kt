package com.cleanx.lcx.feature.printing

import android.graphics.Bitmap
import com.cleanx.lcx.feature.printing.data.LabelData
import com.cleanx.lcx.feature.printing.data.LabelRenderer
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
}
