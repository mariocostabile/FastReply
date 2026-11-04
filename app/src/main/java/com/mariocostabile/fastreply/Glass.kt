package com.mariocostabile.fastreply

import android.os.Build
import android.view.WindowManager
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogWindowProvider

val Accent = Color(0xFF25D366) // WhatsApp green
val Danger = Color(0xFFFF453A)

class GlassPalette(
    val background: List<Color>,
    val blobs: List<Color>,
    val glassTop: Color,
    val glassBottom: Color,
    val edgeHigh: Color,
    val edgeLow: Color,
    val dialog: Color,
    val text: Color,
    val textDim: Color,
)

private val DarkGlass = GlassPalette(
    background = listOf(Color(0xFF05070F), Color(0xFF120A24)),
    blobs = listOf(Accent.copy(alpha = 0.45f), Color(0xFF3A7BFF).copy(alpha = 0.5f), Color(0xFFB04CFF).copy(alpha = 0.45f)),
    glassTop = Color.White.copy(alpha = 0.16f),
    glassBottom = Color.White.copy(alpha = 0.05f),
    edgeHigh = Color.White.copy(alpha = 0.45f),
    edgeLow = Color.White.copy(alpha = 0.06f),
    dialog = Color(0xFF1C1C26).copy(alpha = 0.72f),
    text = Color.White,
    textDim = Color.White.copy(alpha = 0.6f),
)

private val LightGlass = GlassPalette(
    background = listOf(Color(0xFFEEF2FF), Color(0xFFF8F0FF)),
    blobs = listOf(Accent.copy(alpha = 0.45f), Color(0xFF5AA0FF).copy(alpha = 0.45f), Color(0xFFFF7AC8).copy(alpha = 0.4f)),
    glassTop = Color.White.copy(alpha = 0.7f),
    glassBottom = Color.White.copy(alpha = 0.35f),
    edgeHigh = Color.White,
    edgeLow = Color.White.copy(alpha = 0.25f),
    dialog = Color.White.copy(alpha = 0.7f),
    text = Color(0xFF0B0B12),
    textDim = Color(0xFF0B0B12).copy(alpha = 0.55f),
)

val LocalGlass = staticCompositionLocalOf { DarkGlass }

@Composable
fun GlassTheme(content: @Composable () -> Unit) {
    val p = if (isSystemInDarkTheme()) DarkGlass else LightGlass
    MaterialTheme {
        CompositionLocalProvider(LocalGlass provides p, LocalContentColor provides p.text, content = content)
    }
}

/**
 * Full-screen gradient with soft colour blobs. Glass surfaces are translucent, so the blobs show through them:
 * the background is already soft, which gives the frosted look without a real (expensive) backdrop blur.
 */
@Composable
fun GlassBackground(content: @Composable BoxScope.() -> Unit) {
    val p = LocalGlass.current
    Box(
        Modifier.fillMaxSize()
            .background(Brush.verticalGradient(p.background))
            .drawBehind {
                blob(p.blobs[0], Offset(size.width * 0.05f, size.height * 0.12f), size.width * 0.8f)
                blob(p.blobs[1], Offset(size.width * 1.0f, size.height * 0.45f), size.width * 0.9f)
                blob(p.blobs[2], Offset(size.width * 0.2f, size.height * 0.95f), size.width * 0.85f)
            },
        content = content,
    )
}

private fun DrawScope.blob(color: Color, center: Offset, radius: Float) =
    drawCircle(Brush.radialGradient(listOf(color, Color.Transparent), center, radius), radius, center)

/** Translucent fill + light top-left specular edge. [tint] colours the glass (buttons, warnings). */
@Composable
fun Modifier.glass(shape: Shape, tint: Color? = null): Modifier {
    val p = LocalGlass.current
    val top = tint?.copy(alpha = 0.55f) ?: p.glassTop
    val bottom = tint?.copy(alpha = 0.3f) ?: p.glassBottom
    return this
        .clip(shape)
        .background(Brush.verticalGradient(listOf(top, bottom)))
        .drawBehind {
            // soft sheen in the top-left corner, like light hitting curved glass
            drawCircle(
                Brush.radialGradient(listOf(Color.White.copy(alpha = 0.18f), Color.Transparent), Offset.Zero, size.minDimension),
                size.minDimension,
                Offset.Zero,
            )
        }
        .border(1.dp, Brush.linearGradient(listOf(p.edgeHigh, p.edgeLow, p.edgeHigh.copy(alpha = p.edgeHigh.alpha * 0.4f))), shape)
}

/** Click/long-click with a springy shrink on press instead of the Material ripple. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun Modifier.pressable(onClick: () -> Unit, onLongClick: (() -> Unit)? = null): Modifier {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.93f else 1f, spring(dampingRatio = 0.45f, stiffness = 500f), label = "press")
    return this
        .graphicsLayer { scaleX = scale; scaleY = scale }
        .combinedClickable(interactionSource = source, indication = null, onLongClick = onLongClick, onClick = onClick)
}

@Composable
fun GlassButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, tint: Color? = null) {
    Box(
        modifier.pressable(onClick).glass(CircleShape, tint).padding(horizontal = 20.dp, vertical = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = if (tint != null) Color.White else LocalGlass.current.text)
    }
}

@Composable
fun Avatar(name: String, size: Int = 36) {
    Box(Modifier.size(size.dp).glass(CircleShape, Accent), contentAlignment = Alignment.Center) {
        Text(name.trim().take(1).uppercase(), color = Color.White, fontWeight = FontWeight.Bold, fontSize = (size * 0.45f).sp)
    }
}

/** Dialog on a glass card; on Android 12+ the window behind is really blurred. */
@Composable
fun GlassDialog(
    title: String,
    onDismiss: () -> Unit,
    actions: @Composable RowScope.() -> Unit,
    content: @Composable () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        val window = (LocalView.current.parent as? DialogWindowProvider)?.window
        SideEffect {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && window != null) {
                window.addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
                window.attributes = window.attributes.also { it.blurBehindRadius = 80 }
            }
        }
        val p = LocalGlass.current
        val shape = RoundedCornerShape(32.dp)
        Column(
            Modifier.fillMaxWidth()
                .clip(shape)
                .background(p.dialog)
                .glass(shape)
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Text(title, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
            content()
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), content = actions)
        }
    }
}
