package com.example.cnv.cad

/**
 * Theme mode for CAD Viewer. Colors live only in [CADTheme].
 */
enum class CADThemeMode {
    LIGHT,
    DARK,
}

/**
 * All CAD colors. No hard-coded Color.* in renderer draws.
 */
data class CADTheme(
    val mode: CADThemeMode,
    val background: Int,
    val grid: Int,
    val route: Int,
    val branch: Int,
    val node: Int,
    val startPoint: Int,
    val endPoint: Int,
    val currentPosition: Int,
    val overlayText: Int,
    val overlayBackground: Int,
    val debugText: Int,
    val validationError: Int,
) {
    companion object {
        fun light(): CADTheme = CADTheme(
            mode = CADThemeMode.LIGHT,
            background = 0xFFF5F5F5.toInt(),
            grid = 0xFFE0E0E0.toInt(),
            route = 0xFF1565C0.toInt(),
            branch = 0xFF6A1B9A.toInt(),
            node = 0xFF424242.toInt(),
            startPoint = 0xFF2E7D32.toInt(),
            endPoint = 0xFFC62828.toInt(),
            currentPosition = 0xFFFF6F00.toInt(),
            overlayText = 0xFF212121.toInt(),
            overlayBackground = 0xCCFFFFFF.toInt(),
            debugText = 0xFF37474F.toInt(),
            validationError = 0xFFD32F2F.toInt(),
        )

        fun dark(): CADTheme = CADTheme(
            mode = CADThemeMode.DARK,
            background = 0xFF121212.toInt(),
            grid = 0xFF2A2A2A.toInt(),
            route = 0xFF64B5F6.toInt(),
            branch = 0xFFCE93D8.toInt(),
            node = 0xFFEEEEEE.toInt(),
            startPoint = 0xFF81C784.toInt(),
            endPoint = 0xFFE57373.toInt(),
            currentPosition = 0xFFFFB74D.toInt(),
            overlayText = 0xFFFFFFFF.toInt(),
            overlayBackground = 0x99000000.toInt(),
            debugText = 0xFFB0BEC5.toInt(),
            validationError = 0xFFEF5350.toInt(),
        )

        fun of(mode: CADThemeMode): CADTheme = when (mode) {
            CADThemeMode.LIGHT -> light()
            CADThemeMode.DARK -> dark()
        }
    }
}
