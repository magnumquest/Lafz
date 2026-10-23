// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.utils

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight

@Composable
fun Theme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val material3 = Typography()
    val colorScheme = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        if (dark) dynamicDarkColorScheme(LocalContext.current)
        else dynamicLightColorScheme(LocalContext.current)
    } else {
        if (dark) darkColorScheme(
            primary = Color(0xFF9DD5BB),
            background = Color(0xFF101512),
            surface = Color(0xFF101512),
            primaryContainer = Color(0xFF234E3B),
            onPrimaryContainer = Color(0xFFB9F1D6),
        )
        else lightColorScheme(
            primary = Color(0xFF326B50),
            background = Color(0xFFF4F7F3),
            surface = Color(0xFFF4F7F3),
            primaryContainer = Color(0xFFD1ECDD),
            onPrimaryContainer = Color(0xFF153B2B)
        )
    }
    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography(
            titleLarge = material3.titleLarge.copy(fontWeight = FontWeight.SemiBold),
            titleMedium = material3.titleMedium.copy(fontWeight = FontWeight.SemiBold),
            titleSmall = material3.titleSmall.copy(fontWeight = FontWeight.SemiBold)
        ),
        shapes = Shapes(
            small = RoundedCornerShape(12.dp),
            medium = RoundedCornerShape(20.dp),
            large = RoundedCornerShape(28.dp),
        ),
        content = content
    )
}

const val previewDark = true
