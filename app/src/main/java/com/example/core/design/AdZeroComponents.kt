package com.example.core.design

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.AdZeroError
import com.example.ui.theme.AdZeroErrorContainer
import com.example.ui.theme.AdZeroLavenderBg
import com.example.ui.theme.AdZeroLavenderBorder
import com.example.ui.theme.AdZeroLavenderSurface
import com.example.ui.theme.AdZeroPurple
import com.example.ui.theme.AdZeroPurpleDark
import com.example.ui.theme.AdZeroSuccess
import com.example.ui.theme.AdZeroSuccessContainer
import com.example.ui.theme.AdZeroWarning
import com.example.ui.theme.AdZeroWarningContainer
import com.example.ui.theme.AdZeroWhite
import com.example.ui.theme.PlusJakartaSansFamily

enum class StatusTone {
    SUCCESS, WARNING, ERROR, NEUTRAL, PRIMARY
}

@Composable
fun AdZeroShieldLogo(
    size: Dp = 40.dp,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.matchParentSize()) {
            val w = this.size.width
            val h = this.size.height
            val outer = Path().apply {
                moveTo(w * 0.50f, h * 0.04f)
                lineTo(w * 0.90f, h * 0.20f)
                lineTo(w * 0.90f, h * 0.52f)
                cubicTo(w * 0.90f, h * 0.76f, w * 0.72f, h * 0.90f, w * 0.50f, h * 0.96f)
                cubicTo(w * 0.28f, h * 0.90f, w * 0.10f, h * 0.76f, w * 0.10f, h * 0.52f)
                lineTo(w * 0.10f, h * 0.20f)
                close()
            }
            drawPath(path = outer, color = AdZeroPurple)

            val rightHalf = Path().apply {
                moveTo(w * 0.50f, h * 0.04f)
                lineTo(w * 0.90f, h * 0.20f)
                lineTo(w * 0.90f, h * 0.52f)
                cubicTo(w * 0.90f, h * 0.76f, w * 0.72f, h * 0.90f, w * 0.50f, h * 0.96f)
                close()
            }
            drawPath(path = rightHalf, color = AdZeroPurpleDark.copy(alpha = 0.25f))
        }
        Text(
            text = "A",
            color = AdZeroWhite,
            fontFamily = PlusJakartaSansFamily,
            fontWeight = FontWeight.Bold,
            fontSize = (size.value * 0.45f).sp
        )
    }
}

@Composable
fun AdZeroCard(
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surface,
    borderColor: Color = AdZeroLavenderBorder,
    contentPadding: Dp = 16.dp,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    if (onClick != null) {
        Surface(
            onClick = onClick,
            modifier = modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            color = containerColor,
            border = BorderStroke(1.dp, borderColor),
            tonalElevation = 0.dp
        ) {
            Column(modifier = Modifier.padding(contentPadding), content = content)
        }
    } else {
        Surface(
            modifier = modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            color = containerColor,
            border = BorderStroke(1.dp, borderColor),
            tonalElevation = 0.dp
        ) {
            Column(modifier = Modifier.padding(contentPadding), content = content)
        }
    }
}

@Composable
fun StatusPill(
    label: String,
    tone: StatusTone,
    modifier: Modifier = Modifier
) {
    val (bg, fg) = when (tone) {
        StatusTone.SUCCESS -> AdZeroSuccessContainer to AdZeroSuccess
        StatusTone.WARNING -> AdZeroWarningContainer to AdZeroWarning
        StatusTone.ERROR -> AdZeroErrorContainer to AdZeroError
        StatusTone.PRIMARY -> AdZeroLavenderSurface to AdZeroPurple
        StatusTone.NEUTRAL -> AdZeroLavenderBg to MaterialTheme.colorScheme.onSurfaceVariant
    }
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(999.dp))
            .background(bg)
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(fg)
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = fg,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
fun SectionHeader(
    title: String,
    subtitle: String? = null,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground
            )
            if (subtitle != null) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        if (action != null) {
            Spacer(Modifier.width(8.dp))
            action()
        }
    }
}

@Composable
fun InfoBanner(
    icon: ImageVector,
    title: String,
    message: String,
    tone: StatusTone = StatusTone.PRIMARY,
    modifier: Modifier = Modifier
) {
    val (bg, accent) = when (tone) {
        StatusTone.SUCCESS -> AdZeroSuccessContainer to AdZeroSuccess
        StatusTone.WARNING -> AdZeroWarningContainer to AdZeroWarning
        StatusTone.ERROR -> AdZeroErrorContainer to AdZeroError
        StatusTone.PRIMARY -> AdZeroLavenderSurface to AdZeroPurple
        StatusTone.NEUTRAL -> AdZeroLavenderBg to MaterialTheme.colorScheme.onSurfaceVariant
    }
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = bg,
        border = BorderStroke(1.dp, accent.copy(alpha = 0.22f))
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = accent,
                modifier = Modifier
                    .padding(top = 2.dp)
                    .size(20.dp)
            )
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
