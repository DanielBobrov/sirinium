package com.dlab.sirinium.ui.theme

import android.app.Activity
import android.os.Build
import android.util.Log // <--- ДОБАВЛЕН ИМПОРТ ДЛЯ ЛОГИРОВАНИЯ
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

// Цветовая схема для темной темы (используется, если динамические цвета недоступны)
private val DarkColorScheme = darkColorScheme(
    primary = Purple80,
    secondary = PurpleGrey80,
    tertiary = Pink80,
    background = Color(0xFF1C1B1F), // Типичный темный фон
    surface = Color(0xFF1C1B1F),
    onPrimary = Color(0xFF381E72),
    onSecondary = Color(0xFF332D41),
    onTertiary = Color(0xFF492532),
    onBackground = Color(0xFFE6E1E5),
    onSurface = Color(0xFFE6E1E5),
    surfaceVariant = Color(0xFF49454F), // Для Card
    onSurfaceVariant = Color(0xFFCAC4D0)
)

// Цветовая схема для светлой темы (используется, если динамические цвета недоступны)
private val LightColorScheme = lightColorScheme(
    primary = Purple40,
    secondary = PurpleGrey40,
    tertiary = Pink40,
    background = Color(0xFFFFFBFE), // Типичный светлый фон
    surface = Color(0xFFFFFBFE),
    onPrimary = Color.White,
    onSecondary = Color.White,
    onTertiary = Color.White,
    onBackground = Color(0xFF1C1B1F),
    onSurface = Color(0xFF1C1B1F),
    surfaceVariant = Color(0xFFE7E0EC), // Для Card
    onSurfaceVariant = Color(0xFF49454F)
)

private const val THEME_TAG = "SiriniumTheme" // Тег для логов

@Composable
fun SiriniumScheduleTheme(
    darkTheme: Boolean = isSystemInDarkTheme(), // По умолчанию используется системная настройка
    dynamicColor: Boolean = true, // Включить динамические цвета Material You (для Android 12+)
    content: @Composable () -> Unit
) {
    // Логирование для диагностики темной темы
    Log.d(THEME_TAG, "isSystemInDarkTheme() returned: ${isSystemInDarkTheme()}")
    Log.d(THEME_TAG, "Effective darkTheme parameter: $darkTheme")
    Log.d(THEME_TAG, "Dynamic color enabled: $dynamicColor, SDK version: ${Build.VERSION.SDK_INT}")


    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) {
                Log.d(THEME_TAG, "Applying dynamicDarkColorScheme")
                dynamicDarkColorScheme(context)
            } else {
                Log.d(THEME_TAG, "Applying dynamicLightColorScheme")
                dynamicLightColorScheme(context)
            }
        }
        darkTheme -> {
            Log.d(THEME_TAG, "Applying DarkColorScheme")
            DarkColorScheme
        }
        else -> {
            Log.d(THEME_TAG, "Applying LightColorScheme")
            LightColorScheme
        }
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.background.toArgb()
            window.navigationBarColor = colorScheme.background.toArgb()

            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
            WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = !darkTheme
            Log.d(THEME_TAG, "System bars configured for darkTheme: $darkTheme. Status bar icons light: ${!darkTheme}")
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = AppTypography,
        content = content
    )
}
