package com.eduardo.nunes.drt.features.splash

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

@Composable
fun SplashScreen(onSplashFinished: () -> Unit) {
    var lightStep by remember { mutableStateOf(0) }

    LaunchedEffect(Unit) {
        // Animação acelerada (não-linear) total ~800ms
        delay(400)
        lightStep = 1 // Amber 1
        delay(250)
        lightStep = 2 // Amber 2
        delay(180)
        lightStep = 3 // Amber 3
        delay(150)
        lightStep = 4 // GREEN (GO!)

        delay(500) // Mantém o verde por um momento antes de entrar
        onSplashFinished()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0A0A0A)),
        contentAlignment = Alignment.Center
    ) {
        DragRacingTree(
            lightStep = lightStep,
            modifier = Modifier.fillMaxHeight(0.85f).fillMaxWidth()
        )
    }
}

@Composable
fun DragRacingTree(lightStep: Int, modifier: Modifier = Modifier) {
    val textMeasurer = rememberTextMeasurer()
    val labelStyle = TextStyle(
        color = Color.White.copy(alpha = 0.8f),
        fontSize = 9.sp,
        fontWeight = FontWeight.Black
    )

    Canvas(modifier = modifier) {
        val centerX = size.width / 2
        val centerY = size.height / 2
        val treeHeight = size.height * 0.9f
        val pillarWidth = 32.dp.toPx()

        val offGray = Color(0xFF222222)
        val amberOn = Color(0xFFFFC107)
        val greenOn = Color(0xFF4CAF50)
        val structureColor = Color(0xFF1A1A1A)

        // Pilar Central
        drawRoundRect(
            color = structureColor,
            topLeft = Offset(centerX - pillarWidth / 2, centerY - treeHeight / 2),
            size = Size(pillarWidth, treeHeight),
            cornerRadius = CornerRadius(4.dp.toPx())
        )

        val lightRadius = 24.dp.toPx()
        val horizontalOffset = 52.dp.toPx()
        val verticalSpacing = 68.dp.toPx()
        val topY = centerY - treeHeight / 2 + 120.dp.toPx()

        // 1. PRE-STAGE
        val preStageY = topY - 95.dp.toPx()
        val preStageText = textMeasurer.measure("PRE-STAGE", labelStyle)
        drawText(
            textLayoutResult = preStageText,
            topLeft = Offset(centerX - preStageText.size.width / 2, preStageY - 22.dp.toPx())
        )
        drawStagingRow(centerX, preStageY, amberOn)

        // 2. STAGE
        val stageY = topY - 55.dp.toPx()
        val stageText = textMeasurer.measure("STAGE", labelStyle)
        drawText(
            textLayoutResult = stageText,
            topLeft = Offset(centerX - stageText.size.width / 2, stageY - 22.dp.toPx())
        )
        drawStagingRow(centerX, stageY, amberOn)

        // 3. Countdown Ambers
        for (i in 0..2) {
            val y = topY + (i * verticalSpacing)
            val isOn = lightStep > i
            drawLightPair(
                centerLeft = Offset(centerX - horizontalOffset, y),
                centerRight = Offset(centerX + horizontalOffset, y),
                radius = lightRadius,
                color = if (isOn) amberOn else offGray,
                glow = isOn
            )
        }

        // 4. GREEN
        val greenY = topY + (3 * verticalSpacing)
        val isGreenOn = lightStep >= 4
        drawLightPair(
            centerLeft = Offset(centerX - horizontalOffset, greenY),
            centerRight = Offset(centerX + horizontalOffset, greenY),
            radius = lightRadius,
            color = if (isGreenOn) greenOn else offGray,
            glow = isGreenOn
        )

        // 5. RED (Foul)
        val redY = topY + (4 * verticalSpacing)
        drawLightPair(
            centerLeft = Offset(centerX - horizontalOffset, redY),
            centerRight = Offset(centerX + horizontalOffset, redY),
            radius = lightRadius,
            color = Color(0xFF331111),
            glow = false
        )
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawStagingRow(centerX: Float, y: Float, color: Color) {
    val offsets = listOf(-45.dp.toPx(), -15.dp.toPx(), 15.dp.toPx(), 45.dp.toPx())
    offsets.forEach { xOffset ->
        drawCircle(color = color, radius = 7.dp.toPx(), center = Offset(centerX + xOffset, y))
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(color.copy(alpha = 0.3f), Color.Transparent),
                center = Offset(centerX + xOffset, y),
                radius = 15.dp.toPx()
            ),
            radius = 15.dp.toPx(),
            center = Offset(centerX + xOffset, y)
        )
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawLightPair(
    centerLeft: Offset,
    centerRight: Offset,
    radius: Float,
    color: Color,
    glow: Boolean
) {
    val housingSize = radius * 2.4f
    listOf(centerLeft, centerRight).forEach { center ->
        drawRect(
            color = Color(0xFF111111),
            topLeft = Offset(center.x - housingSize/2, center.y - housingSize/2),
            size = Size(housingSize, housingSize)
        )
        if (glow) {
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(color.copy(alpha = 0.4f), Color.Transparent),
                    center = center,
                    radius = radius * 3.5f
                ),
                radius = radius * 3.5f, center = center
            )
        }
        drawCircle(color = color, radius = radius, center = center)
        drawCircle(
            color = Color.White.copy(alpha = 0.15f),
            radius = radius, center = center,
            style = Stroke(width = 1.5.dp.toPx())
        )
    }
}


@Preview
@Composable
fun SplashScreenPreview() {
    MaterialTheme(colorScheme = darkColorScheme()) {
        SplashScreen(onSplashFinished = {})
    }
}