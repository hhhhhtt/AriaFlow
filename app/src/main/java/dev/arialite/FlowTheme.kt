package dev.arialite

import androidx.compose.runtime.*
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*

@Immutable
internal class FlowPalette(val dark: Boolean) {
    private fun tone(light: Long, night: Long) = Color(if (dark) night else light)

    private val white = Color.White
    private val black = Color.Black
    val canvas = tone(0xFFF2F2F7, 0xFF1C1C1E)
    val card = tone(0xFFFFFFFF, 0xFF2C2C2E)
    val textPrimary = tone(0xFF171719, 0xFFF5F5F7)
    val textSecondary = tone(0xFF686870, 0xFFAEAEB6)
    val placeholder = tone(0xFF777780, 0xFFA0A0A8)
    val searchFill = tone(0xFFE3E3E8, 0xFF2C2C2E)
    val segmentTrack = tone(0xFFE2E2E7, 0xFF2C2C2E)
    val segmentSelected = tone(0xFFFFFFFF, 0xFF48484A)
    val accent = tone(0xFF3984F6, 0xFF64A3FF)
    val fieldFill = tone(0xFFF1F1F5, 0xFF3A3A3C)
    val secondaryButtonFill = accent.copy(alpha = .12f)
    val onAction = white
    val iconInk = textPrimary
    val unavailableIcon = textSecondary
    val axisBacking = card.copy(alpha = .82f)
    val taskControlFill = accent.copy(alpha = if (dark) .20f else .12f)
    val taskControlPressedFill = accent.copy(alpha = .20f)
    val navSelectedFill = if (dark) white.copy(alpha = .10f) else black.copy(alpha = .06f)
    val noticeFill = tone(0xEB252529, 0xF044444A)
    val noticeText = white
    val onRemove = Color(0xFF171719)
    val navEdge = tone(0x18000000, 0x25FFFFFF)
    val grid = tone(0xFFDCDCE0, 0xFF45454A)
    val progressTrack = tone(0xFFE8E8EB, 0xFF48484C)
    val statusActive = tone(0xFF3984F6, 0xFF64A3FF)
    val statusStopped = tone(0xFF8E8E93, 0xFF98989D)
    val statusComplete = tone(0xFF61C568, 0xFF73D67A)
    val statusWaiting = tone(0xFFCA8A04, 0xFFF6BC4B)
    val statusError = tone(0xFFE44D48, 0xFFFF746C)
    val downloadColor = tone(0xFF3984FF, 0xFF64A3FF)
    val uploadColor = tone(0xFFB95D0B, 0xFFFFAC67)
    val serverTagText = tone(0xFF167782, 0xFF79D9DF)
    val serverTagFill = tone(0xFFE3F6F8, 0xFF17383D)
    val fileVideo = tone(0xFF6455EB, 0xFFB4A8FF)
    val fileVideoFill = tone(0xFFEEECFF, 0xFF302C48)
    val fileInstaller = tone(0xFF3984F6, 0xFF83BDFF)
    val fileInstallerFill = tone(0xFFE4F1FF, 0xFF20354B)
    val fileIso = tone(0xFFC34F95, 0xFFF29CCB)
    val fileIsoFill = tone(0xFFFBE9F3, 0xFF452C3B)
    val fileArchive = tone(0xFFBD621C, 0xFFFFB475)
    val fileArchiveFill = tone(0xFFFFEFDF, 0xFF453326)
    val fileOther = tone(0xFF77777F, 0xFFB2B2BB)
    val fileOtherFill = tone(0xFFEFEFF2, 0xFF323236)
    val headerTint = canvas.copy(alpha = .70f)
    val headerFallback = headerTint
    val glassTint = tone(0xB3FFFFFF, 0xBF303033)
    val glassEdge = tone(0x99FFFFFF, 0x40FFFFFF)
    val darkEdge = white.copy(alpha = .08f)
    val disabledInk = textPrimary.copy(alpha = .38f)
    val cardShadow = black.copy(alpha = .05f)
    val capsuleShadow = tone(0x22000000, 0x55000000)
    val segmentShadow = black.copy(alpha = .08f)
}

internal val LocalFlow = staticCompositionLocalOf { FlowPalette(false) }

internal object FlowType {
    private fun face(size: Int, line: Int, weight: FontWeight = FontWeight.Normal) =
        TextStyle(fontSize = size.sp, lineHeight = line.sp, fontWeight = weight)

    val button = face(15, 20, FontWeight.Medium)
    val title = face(17, 22, FontWeight.Medium)
    val section = face(13, 18, FontWeight.Medium)
    val file = face(15, 20, FontWeight.Medium)
    val search = face(16, 22)
    val cardTitle = face(15, 20, FontWeight.Medium)
    val segment = face(13, 18)
    val segmentSelected = segment.copy(fontWeight = FontWeight.Medium)
    val readout = face(12, 16, FontWeight.Medium)
    val metadata = face(12, 16)
    val axis = face(11, 14)
    val nav = axis.copy(fontWeight = FontWeight.Medium)
}

internal object FlowMetric {
    val buttonHeight = 44.dp
    val buttonRadius = 22.dp
    const val disabledAlpha = .38f
    val s2 = 2.dp
    val s4 = 4.dp
    val s6 = 6.dp
    val s8 = 8.dp
    val s12 = 12.dp
    val s16 = 16.dp
    val s24 = 24.dp
    val linkInputMin = 92.dp
    val launcherPreview = 52.dp
    val launcherRadius = 13.dp
    val launcherItemPadding = 10.dp
    val page = 16.dp
    val taskInset = 14.dp
    val actionWidth = 56.dp
    val cardGap = 8.dp
    val cardRadius = 16.dp
    val fileRadius = 12.dp
    val tagRadius = 10.dp
    val segmentBarRadius = 2.dp
    val headerDivider = 0.5.dp
    val topHeight = 44.dp
    val capsuleWidth = 104.dp
    val capsuleHeight = 44.dp
    val icon = 21.dp
    val iconStroke = 1.75.dp
    val searchHeight = 44.dp
    val searchIcon = 19.dp
    val searchInset = 14.dp
    val segmentHeight = 36.dp
    val chartHeight = 68.dp
    val statusDot = 8.dp
    val taskTouch = 48.dp
    val taskControlRadius = 10.dp
    val taskControlIcon = 18.dp
    val playOffset = 1.dp
    val spinner = 16.dp
    val spinnerStroke = 2.dp
    const val pressScale = .92f
    const val pressMillis = 120
    const val iconFadeMillis = 150
    val taskControl = 36.dp
    val navHeight = 56.dp
    val navBottom = 8.dp
    val navIcon = 22.dp
    val tagHeight = 20.dp
    val progressHeight = 6.dp
    val progressGap = 2.dp
    val chartStroke = 1.5.dp
    val uploadStroke = 1.dp
    val gridStroke = 1.dp
    val gridDash = 4.dp
    val gridGap = 4.dp
    val shadowY = 1.dp
    val cardBlur = 4.dp
    val selectedBlur = 3.dp
    val topBlur = 20.dp
    val navRadius = 28.dp
    val navSelectionRadius = 22.dp
    val noticeRadius = 20.dp
    const val navSlideMillis = 200
    const val noticeMillis = 2000L
    val highlight = 0.75.dp
    val menuWidth = 230.dp
    const val progressSegments = 40
    const val chartFillAlpha = .38f
}

internal fun androidx.compose.ui.Modifier.segmentShade(color: Color, fill: Color) =
    this.drawWithCache {
        val paint =
            android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                this.color = fill.toArgb()
                setShadowLayer(
                    FlowMetric.selectedBlur.toPx(),
                    0f,
                    FlowMetric.shadowY.toPx(),
                    color.toArgb(),
                )
            }
        onDrawBehind {
            drawContext.canvas.nativeCanvas.drawRoundRect(
                0f,
                0f,
                size.width,
                size.height,
                size.height / 2,
                size.height / 2,
                paint,
            )
        }
    }
