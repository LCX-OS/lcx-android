package com.cleanx.lcx.core.theme

import androidx.compose.ui.graphics.Color

// Palette extracted from public/cleanx-brand-kit.pdf.
val LcxBrandBlue = Color(0xFF96CDE2)
val LcxBrandBlueSoft = Color(0xFFCAE5EF)
val LcxBrandInk = Color(0xFF45595A)
val LcxBrandOlive = Color(0xFFACAC86)
val LcxBrandCream = Color(0xFFEDE7DA)
val LcxBrandWhite = Color(0xFFFFFFFF)

// Supporting neutrals derived from the brand palette for app surfaces.
val LcxBackgroundWarm = Color(0xFFF8F6F1)
val LcxSurface = LcxBrandWhite
val LcxOnSurface = LcxBrandInk
val LcxOnSurfaceMuted = Color(0xFF667B7D)
val LcxOutline = Color(0xFFD5E4EA)
val LcxOutlineStrong = Color(0xFFA3B6BC)

// Accessible semantic colors tuned to sit beside the brand palette.
val LcxInfo = Color(0xFF5F8897)
val LcxSuccess = Color(0xFF667A57)
val LcxWarning = Color(0xFFA6793A)
val LcxError = Color(0xFFB46860)

val LcxInfoDark = Color(0xFFC1E2EE)
val LcxSuccessDark = Color(0xFFC8D2B4)
val LcxWarningDark = Color(0xFFE9C894)
val LcxErrorDark = Color(0xFFF1B8B0)

// Legacy aliases kept for existing components.
val LcxBlue = LcxInfo
val LcxBlueDark = LcxInfoDark
val LcxGreen = LcxSuccess
val LcxRed = LcxError
val LcxYellow = LcxWarning

// Ticket status colors.
val LcxStatusReceived = LcxInfo
val LcxStatusProcessing = LcxWarning
val LcxStatusReady = LcxSuccess
val LcxStatusDelivered = Color(0xFF78888A)

val LcxStatusReceivedDark = LcxInfoDark
val LcxStatusProcessingDark = LcxWarningDark
val LcxStatusReadyDark = LcxSuccessDark
val LcxStatusDeliveredDark = Color(0xFFC8D4D7)

// Payment status colors.
val LcxPaymentPending = LcxWarning
val LcxPaymentPrepaid = LcxInfo
val LcxPaymentPaid = LcxSuccess

val LcxPaymentPendingDark = LcxWarningDark
val LcxPaymentPrepaidDark = LcxInfoDark
val LcxPaymentPaidDark = LcxSuccessDark
