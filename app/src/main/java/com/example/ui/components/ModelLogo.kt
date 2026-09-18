package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Model family marks.
 *
 * These are original geometric marks drawn by the app, not the vendors' trademarks: each family
 * gets a distinct shape and tint so a model is recognisable at a glance in the library.
 */
enum class ModelBrand(val label: String, val tint: Color) {
    META("Meta", Color(0xFF5C8DE8)),
    QWEN("Qwen", Color(0xFFA57BE8)),
    GEMMA("Gemma", Color(0xFF66B6A2)),
    PHI("Phi", Color(0xFF6CB5E8)),
    DEEPSEEK("DeepSeek", Color(0xFF6C7BE8)),
    SMOLLM("SmolLM", Color(0xFFE8B86C)),
    TINYLLAMA("TinyLlama", Color(0xFFD98FA8)),
    STARCODER("StarCoder", Color(0xFF8FC96C)),
    STABLELM("StableLM", Color(0xFF6CD3D9)),
    FALCON("Falcon", Color(0xFFD9A06C)),
    MISTRAL("Mistral", Color(0xFFE87F6C)),
    GENERIC("GGUF", Color(0xFF8A8A94));

    companion object {
        /** Resolves a family from whatever we know about a model: id, name, author, architecture. */
        fun of(vararg hints: String?): ModelBrand {
            val haystack = hints.filterNotNull().joinToString(" ").lowercase()
            return when {
                haystack.contains("deepseek") -> DEEPSEEK
                haystack.contains("smollm") -> SMOLLM
                haystack.contains("tinyllama") -> TINYLLAMA
                haystack.contains("qwen") -> QWEN
                haystack.contains("gemma") -> GEMMA
                haystack.contains("phi") || haystack.contains("microsoft") -> PHI
                haystack.contains("starcoder") || haystack.contains("bigcode") -> STARCODER
                haystack.contains("stablelm") || haystack.contains("stability") -> STABLELM
                haystack.contains("falcon") || haystack.contains("tii") -> FALCON
                haystack.contains("mistral") || haystack.contains("mixtral") -> MISTRAL
                haystack.contains("llama") || haystack.contains("meta") -> META
                else -> GENERIC
            }
        }
    }
}

@Composable
fun ModelLogo(
    brand: ModelBrand,
    modifier: Modifier = Modifier,
    size: Dp = 38.dp,
    fallbackLetter: Char? = null
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(size / 3))
            .background(brand.tint.copy(alpha = 0.14f)),
        contentAlignment = Alignment.Center
    ) {
        if (brand == ModelBrand.GENERIC && fallbackLetter != null) {
            Text(
                text = fallbackLetter.uppercaseChar().toString(),
                color = brand.tint,
                fontWeight = FontWeight.SemiBold,
                fontSize = (size.value * 0.42f).sp,
                style = MaterialTheme.typography.titleMedium
            )
        } else {
            Canvas(modifier = Modifier.size(size * 0.5f)) {
                drawBrandMark(brand)
            }
        }
    }
}

/** Convenience overload for catalog entries and downloaded files. */
@Composable
fun ModelLogo(
    modelName: String,
    author: String? = null,
    architecture: String? = null,
    modifier: Modifier = Modifier,
    size: Dp = 38.dp
) {
    val brand = ModelBrand.of(modelName, author, architecture)
    ModelLogo(
        brand = brand,
        modifier = modifier,
        size = size,
        fallbackLetter = modelName.firstOrNull { it.isLetterOrDigit() }
    )
}

private fun DrawScope.drawBrandMark(brand: ModelBrand) {
    val c = brand.tint
    val w = size.width
    val h = size.height
    val stroke = Stroke(width = w * 0.12f)

    when (brand) {
        // Two interlocking loops.
        ModelBrand.META -> {
            drawArc(
                color = c,
                startAngle = 30f,
                sweepAngle = 300f,
                useCenter = false,
                topLeft = Offset(0f, h * 0.2f),
                size = Size(w * 0.62f, h * 0.6f),
                style = stroke
            )
            drawArc(
                color = c,
                startAngle = 210f,
                sweepAngle = 300f,
                useCenter = false,
                topLeft = Offset(w * 0.38f, h * 0.2f),
                size = Size(w * 0.62f, h * 0.6f),
                style = stroke
            )
        }
        // Hexagon with an inner bar.
        ModelBrand.QWEN -> {
            drawPath(hexagonPath(w, h), color = c, style = stroke)
            drawLine(
                color = c,
                start = Offset(w * 0.35f, h * 0.62f),
                end = Offset(w * 0.65f, h * 0.38f),
                strokeWidth = w * 0.12f
            )
        }
        // Four point spark.
        ModelBrand.GEMMA -> {
            val path = Path().apply {
                moveTo(w / 2f, 0f)
                cubicTo(w * 0.56f, h * 0.44f, w * 0.56f, h * 0.44f, w, h / 2f)
                cubicTo(w * 0.56f, h * 0.56f, w * 0.56f, h * 0.56f, w / 2f, h)
                cubicTo(w * 0.44f, h * 0.56f, w * 0.44f, h * 0.56f, 0f, h / 2f)
                cubicTo(w * 0.44f, h * 0.44f, w * 0.44f, h * 0.44f, w / 2f, 0f)
                close()
            }
            drawPath(path, color = c)
        }
        // Four tiles.
        ModelBrand.PHI -> {
            val tile = w * 0.42f
            val gap = w * 0.16f
            drawRect(color = c, topLeft = Offset(0f, 0f), size = Size(tile, tile))
            drawRect(color = c, topLeft = Offset(tile + gap, 0f), size = Size(tile, tile))
            drawRect(color = c, topLeft = Offset(0f, tile + gap), size = Size(tile, tile))
            drawRect(
                color = c.copy(alpha = 0.55f),
                topLeft = Offset(tile + gap, tile + gap),
                size = Size(tile, tile)
            )
        }
        // Circle cut by a wave.
        ModelBrand.DEEPSEEK -> {
            drawCircle(color = c, radius = w * 0.46f, center = Offset(w / 2f, h / 2f), style = stroke)
            val wave = Path().apply {
                moveTo(w * 0.18f, h * 0.58f)
                quadraticTo(w * 0.36f, h * 0.28f, w * 0.52f, h * 0.5f)
                quadraticTo(w * 0.68f, h * 0.72f, w * 0.84f, h * 0.42f)
            }
            drawPath(wave, color = c, style = Stroke(width = w * 0.1f))
        }
        // Two overlapping circles.
        ModelBrand.SMOLLM -> {
            drawCircle(color = c, radius = w * 0.3f, center = Offset(w * 0.34f, h * 0.5f), style = stroke)
            drawCircle(color = c.copy(alpha = 0.6f), radius = w * 0.3f, center = Offset(w * 0.66f, h * 0.5f), style = stroke)
        }
        // Triangle.
        ModelBrand.TINYLLAMA -> {
            val path = Path().apply {
                moveTo(w / 2f, h * 0.06f)
                lineTo(w * 0.96f, h * 0.9f)
                lineTo(w * 0.04f, h * 0.9f)
                close()
            }
            drawPath(path, color = c, style = stroke)
        }
        // Asterisk / star burst.
        ModelBrand.STARCODER -> {
            val cx = w / 2f
            val cy = h / 2f
            val r = w * 0.48f
            for (i in 0 until 6) {
                val angle = Math.toRadians((i * 30).toDouble())
                val dx = (r * kotlin.math.cos(angle)).toFloat()
                val dy = (r * kotlin.math.sin(angle)).toFloat()
                drawLine(
                    color = if (i % 2 == 0) c else c.copy(alpha = 0.55f),
                    start = Offset(cx - dx, cy - dy),
                    end = Offset(cx + dx, cy + dy),
                    strokeWidth = w * 0.1f
                )
            }
        }
        // Three stacked bars.
        ModelBrand.STABLELM -> {
            val bar = h * 0.18f
            val gap = h * 0.13f
            drawRect(color = c, topLeft = Offset(0f, 0f), size = Size(w, bar))
            drawRect(color = c.copy(alpha = 0.7f), topLeft = Offset(w * 0.15f, bar + gap), size = Size(w * 0.85f, bar))
            drawRect(color = c.copy(alpha = 0.45f), topLeft = Offset(w * 0.3f, (bar + gap) * 2), size = Size(w * 0.7f, bar))
        }
        // Wing chevrons.
        ModelBrand.FALCON -> {
            val chevron = Path().apply {
                moveTo(w * 0.08f, h * 0.24f)
                lineTo(w / 2f, h * 0.62f)
                lineTo(w * 0.92f, h * 0.24f)
            }
            drawPath(chevron, color = c, style = stroke)
            val chevron2 = Path().apply {
                moveTo(w * 0.24f, h * 0.62f)
                lineTo(w / 2f, h * 0.9f)
                lineTo(w * 0.76f, h * 0.62f)
            }
            drawPath(chevron2, color = c.copy(alpha = 0.6f), style = stroke)
        }
        // Offset stripes.
        ModelBrand.MISTRAL -> {
            val col = w * 0.28f
            drawRect(color = c, topLeft = Offset(0f, 0f), size = Size(col, h))
            drawRect(color = c.copy(alpha = 0.75f), topLeft = Offset(w * 0.36f, h * 0.2f), size = Size(col, h * 0.8f))
            drawRect(color = c.copy(alpha = 0.5f), topLeft = Offset(w * 0.72f, h * 0.4f), size = Size(col, h * 0.6f))
        }
        // Rounded container as a neutral placeholder.
        ModelBrand.GENERIC -> {
            drawRoundRect(
                color = c,
                topLeft = Offset(w * 0.06f, h * 0.06f),
                size = Size(w * 0.88f, h * 0.88f),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(w * 0.24f, h * 0.24f),
                style = stroke
            )
        }
    }
}

private fun hexagonPath(w: Float, h: Float): Path {
    val rect = Rect(0f, 0f, w, h)
    return Path().apply {
        moveTo(rect.center.x, 0f)
        lineTo(w, h * 0.27f)
        lineTo(w, h * 0.73f)
        lineTo(rect.center.x, h)
        lineTo(0f, h * 0.73f)
        lineTo(0f, h * 0.27f)
        close()
    }
}
