package dev.arialite

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color

@Composable
internal fun PrimaryButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit,
) {
    val colors = LocalFlow.current
    ActionButton(onClick, modifier, enabled, colors.accent, colors.onAction, content)
}

@Composable
internal fun SecondaryButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit,
) {
    val colors = LocalFlow.current
    ActionButton(onClick, modifier, enabled, colors.secondaryButtonFill, colors.accent, content)
}

@Composable
private fun ActionButton(
    onClick: () -> Unit,
    modifier: Modifier,
    enabled: Boolean,
    fill: Color,
    ink: Color,
    content: @Composable RowScope.() -> Unit,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier =
            modifier
                .height(FlowMetric.buttonHeight)
                .alpha(if (enabled) 1f else FlowMetric.disabledAlpha),
        shape = RoundedCornerShape(FlowMetric.buttonRadius),
        colors =
            ButtonDefaults.buttonColors(
                containerColor = fill,
                contentColor = ink,
                disabledContainerColor = fill,
                disabledContentColor = ink,
            ),
        contentPadding = PaddingValues(horizontal = FlowMetric.s16),
    ) {
        ProvideTextStyle(FlowType.button) { content() }
    }
}
