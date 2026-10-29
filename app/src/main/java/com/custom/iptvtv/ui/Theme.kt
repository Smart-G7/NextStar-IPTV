package com.custom.iptvtv.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

object Pal {
    val Bg = Color(0xFF0A0E15)
    val BgEnd = Color(0xFF0E1A2B)
    val Surface = Color(0xFF141C28)
    val SurfaceHi = Color(0xFF1F2B3D)
    val Accent = Color(0xFF3DA5FF)
    val OnAccent = Color(0xFF04121F)
    val Text = Color(0xFFEAF0F7)
    val Muted = Color(0xFF8793A5)
    val Danger = Color(0xFFFF6B6B)
}

@Composable
fun IptvTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Pal.Accent,
            background = Pal.Bg,
            surface = Pal.Surface,
            onSurface = Pal.Text,
            onBackground = Pal.Text
        ),
        typography = Typography(
            bodySmall = TextStyle(fontSize = 13.sp),
            bodyMedium = TextStyle(fontSize = 16.sp),
            bodyLarge = TextStyle(fontSize = 18.sp),
            labelLarge = TextStyle(fontSize = 15.sp),
            titleMedium = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.SemiBold),
            titleLarge = TextStyle(fontSize = 28.sp, fontWeight = FontWeight.Bold),
            headlineMedium = TextStyle(fontSize = 36.sp, fontWeight = FontWeight.Bold)
        )
    ) {
        // Varsayılan yazı rengi açık olsun (Surface contentColor'ı sağlar)
        Surface(color = Pal.Bg, contentColor = Pal.Text, content = content)
    }
}
