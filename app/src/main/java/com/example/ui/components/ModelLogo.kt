package com.example.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.ui.theme.VipoLogoTile

/**
 * Model family marks: each family shows its vendor's own logo, taken from the lobe-icons set
 * (MIT) rather than redrawn. Models with no published mark fall back to a neutral chip icon.
 */
enum class ModelBrand(val label: String, @param:DrawableRes val logoRes: Int?) {
    META("Meta", R.drawable.logo_meta),
    QWEN("Qwen", R.drawable.logo_qwen),
    GEMMA("Google Gemma", R.drawable.logo_gemma),
    PHI("Microsoft", R.drawable.logo_microsoft),
    DEEPSEEK("DeepSeek", R.drawable.logo_deepseek),
    SMOLLM("Hugging Face", R.drawable.logo_huggingface),
    STARCODER("BigCode", R.drawable.logo_huggingface),
    STABLELM("Stability AI", R.drawable.logo_stability),
    FALCON("TII", R.drawable.logo_falcon),
    MISTRAL("Mistral AI", R.drawable.logo_mistral),
    TINYLLAMA("TinyLlama", null),
    GENERIC("GGUF", null);

    companion object {
        /** Resolves a family from whatever we know about a model: id, name, author, architecture. */
        fun of(vararg hints: String?): ModelBrand {
            val haystack = hints.filterNotNull().joinToString(" ").lowercase()
            return when {
                haystack.contains("deepseek") -> DEEPSEEK
                haystack.contains("smollm") || haystack.contains("hugging") -> SMOLLM
                haystack.contains("tinyllama") -> TINYLLAMA
                haystack.contains("qwen") -> QWEN
                haystack.contains("gemma") || haystack.contains("google") -> GEMMA
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
    size: Dp = 44.dp
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(size / 3.6f))
            .background(VipoLogoTile),
        contentAlignment = Alignment.Center
    ) {
        val logoRes = brand.logoRes
        if (logoRes != null) {
            Image(
                painter = painterResource(logoRes),
                contentDescription = brand.label,
                modifier = Modifier.size(size * 0.64f)
            )
        } else {
            Icon(
                imageVector = Icons.Default.Memory,
                contentDescription = brand.label,
                tint = Color(0xFF3B3B42),
                modifier = Modifier.size(size * 0.58f)
            )
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
    size: Dp = 44.dp
) {
    ModelLogo(
        brand = ModelBrand.of(modelName, author, architecture),
        modifier = modifier,
        size = size
    )
}
