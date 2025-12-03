package com.github.alphapaca.claudeclient.presentation.weather

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.sin
import kotlin.math.cos
import kotlin.math.PI

// Data classes
@Composable
fun FancyWeatherWidget(
    weatherData: WeatherData,
    modifier: Modifier = Modifier
) {
    // Animation for the entire widget entrance
    var visible by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        visible = true
    }

    val scale by animateFloatAsState(
        targetValue = if (visible) 1f else 0.9f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "scale"
    )

    Box(
        modifier = modifier
            .scale(scale)
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
    ) {
        // Animated background
        WeatherBackground(weatherData.weatherCondition)

        // Content
        Column(
            modifier = Modifier
                .padding(20.dp)
                .fillMaxWidth()
        ) {
            // Location header
            Text(
                text = weatherData.city,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                color = Color.White.copy(alpha = 0.9f)
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Main temperature display
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                // Temperature
                Row(verticalAlignment = Alignment.Top) {
                    AnimatedTemperature(
                        temperature = weatherData.temperature,
                        modifier = Modifier
                    )
                    Text(
                        text = "°",
                        fontSize = 72.sp,
                        fontWeight = FontWeight.Light,
                        color = Color.White,
                        modifier = Modifier.offset(y = (-8).dp)
                    )
                }

                // Weather icon
                AnimatedWeatherIcon(
                    condition = weatherData.weatherCondition,
                    modifier = Modifier.size(100.dp)
                )
            }

            // Condition text
            Text(
                text = weatherData.weatherCondition.displayName(),
                fontSize = 20.sp,
                fontWeight = FontWeight.Medium,
                color = Color.White.copy(alpha = 0.95f),
                modifier = Modifier.padding(top = 4.dp)
            )

            Spacer(modifier = Modifier.height(20.dp))

            // Weather details grid
            WeatherDetailsGrid(weatherData)

            Spacer(modifier = Modifier.height(12.dp))

            // High/Low temperature bar
            TemperatureBar(
                high = weatherData.highTemperature,
                low = weatherData.lowTemperature,
                current = weatherData.temperature
            )
        }
    }
}

@Composable
fun WeatherBackground(condition: WeatherCondition) {
    val infiniteTransition = rememberInfiniteTransition(label = "background")

    val animatedOffset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(20000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "offset"
    )

    val colors = when (condition) {
        WeatherCondition.SUNNY -> listOf(
            Color(0xFFFDB813),
            Color(0xFFF37335),
            Color(0xFFEF4E7B)
        )
        WeatherCondition.CLOUDY -> listOf(
            Color(0xFF696E79),
            Color(0xFFA4B0BD),
            Color(0xFF57606F)
        )
        WeatherCondition.RAINY -> listOf(
            Color(0xFF4A69BD),
            Color(0xFF6A89CC),
            Color(0xFF1E3799)
        )
        WeatherCondition.STORMY -> listOf(
            Color(0xFF2C3A47),
            Color(0xFF485460),
            Color(0xFF1B262C)
        )
        WeatherCondition.SNOWY -> listOf(
            Color(0xFF8FBDD3),
            Color(0xFFB8E6F6),
            Color(0xFF5DA7C0)
        )
        WeatherCondition.FOGGY -> listOf(
            Color(0xFF9CA3AF),
            Color(0xFFD1D5DB),
            Color(0xFF6B7280)
        )
        WeatherCondition.PARTLY_CLOUDY -> listOf(
            Color(0xFF74B9FF),
            Color(0xFF0984E3),
            Color(0xFFFECB2E)
        )
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(350.dp)
            .background(
                brush = Brush.linearGradient(
                    colors = colors,
                    start = Offset(0f, 0f),
                    end = Offset(1000f, 1000f)
                )
            )
    ) {
        // Animated particles/effects based on weather
        Canvas(modifier = Modifier.fillMaxSize()) {
            when (condition) {
                WeatherCondition.SUNNY -> drawSunRays(animatedOffset)
                WeatherCondition.RAINY -> drawRainDrops(animatedOffset)
                WeatherCondition.SNOWY -> drawSnowflakes(animatedOffset)
                WeatherCondition.CLOUDY, WeatherCondition.PARTLY_CLOUDY -> drawClouds(animatedOffset)
                WeatherCondition.STORMY -> drawLightning(animatedOffset)
                WeatherCondition.FOGGY -> drawFog(animatedOffset)
            }
        }
    }
}

fun DrawScope.drawSunRays(offset: Float) {
    val rayCount = 12
    val centerX = size.width * 0.8f
    val centerY = size.height * 0.3f
    val rayLength = 40f

    for (i in 0 until rayCount) {
        val angle = (i * 2 * PI / rayCount) + (offset * 2 * PI)
        val startX = centerX + cos(angle).toFloat() * 30f
        val startY = centerY + sin(angle).toFloat() * 30f
        val endX = centerX + cos(angle).toFloat() * (30f + rayLength)
        val endY = centerY + sin(angle).toFloat() * (30f + rayLength)

        drawLine(
            color = Color.White.copy(alpha = 0.3f),
            start = Offset(startX, startY),
            end = Offset(endX, endY),
            strokeWidth = 3f
        )
    }
}

fun DrawScope.drawRainDrops(offset: Float) {
    val dropCount = 30
    for (i in 0 until dropCount) {
        val x = (i * size.width / dropCount) + (offset * 20) % size.width
        val y = ((offset * size.height * 2 + i * 100) % (size.height + 100))

        drawLine(
            color = Color.White.copy(alpha = 0.4f),
            start = Offset(x, y),
            end = Offset(x + 2f, y + 15f),
            strokeWidth = 2f
        )
    }
}

fun DrawScope.drawSnowflakes(offset: Float) {
    val flakeCount = 25
    for (i in 0 until flakeCount) {
        val x = (i * size.width / flakeCount + sin(offset * 2 + i) * 30) % size.width
        val y = ((offset * size.height * 0.5f + i * 150) % (size.height + 100))

        drawCircle(
            color = Color.White.copy(alpha = 0.6f),
            radius = 3f,
            center = Offset(x.toFloat(), y)
        )
    }
}

fun DrawScope.drawClouds(offset: Float) {
    // Simple cloud shapes
    val path = Path()
    val cloudY = size.height * 0.2f + sin(offset * PI * 2).toFloat() * 10f

    path.addOval(
        Rect(
            left = size.width * 0.6f,
            top = cloudY,
            right = size.width * 0.75f,
            bottom = cloudY + 40f
        )
    )

    drawPath(
        path = path,
        color = Color.White.copy(alpha = 0.2f)
    )
}

fun DrawScope.drawLightning(offset: Float) {
    if (offset > 0.9f) { // Flash occasionally
        drawLine(
            color = Color.White.copy(alpha = 0.8f),
            start = Offset(size.width * 0.7f, 0f),
            end = Offset(size.width * 0.65f, size.height * 0.5f),
            strokeWidth = 4f
        )
    }
}

fun DrawScope.drawFog(offset: Float) {
    val layers = 5
    for (i in 0 until layers) {
        val y = size.height * (0.2f + i * 0.15f)
        val alpha = 0.1f + (sin(offset * PI * 2 + i).toFloat() + 1) * 0.05f

        drawRect(
            color = Color.White.copy(alpha = alpha),
            topLeft = Offset(0f, y),
            size = Size(size.width, 40f)
        )
    }
}

@Composable
fun AnimatedTemperature(temperature: Int, modifier: Modifier = Modifier) {
    var targetTemp by remember { mutableStateOf(0) }

    LaunchedEffect(temperature) {
        targetTemp = temperature
    }

    val animatedTemp by animateIntAsState(
        targetValue = targetTemp,
        animationSpec = tween(1000, easing = EaseOutQuart),
        label = "temperature"
    )

    Text(
        text = animatedTemp.toString(),
        fontSize = 72.sp,
        fontWeight = FontWeight.Light,
        color = Color.White,
        modifier = modifier
    )
}

@Composable
fun AnimatedWeatherIcon(condition: WeatherCondition, modifier: Modifier = Modifier) {
    val infiniteTransition = rememberInfiniteTransition(label = "icon")

    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = if (condition == WeatherCondition.SUNNY) 360f else 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(20000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )

    val scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )

    val icon = when (condition) {
        WeatherCondition.SUNNY -> Icons.Default.WbSunny
        WeatherCondition.CLOUDY -> Icons.Default.Cloud
        WeatherCondition.RAINY -> Icons.Default.Opacity
        WeatherCondition.STORMY -> Icons.Default.Thunderstorm
        WeatherCondition.SNOWY -> Icons.Default.AcUnit
        WeatherCondition.FOGGY -> Icons.Default.Cloud
        WeatherCondition.PARTLY_CLOUDY -> Icons.Default.WbCloudy
    }

    Icon(
        imageVector = icon,
        contentDescription = condition.displayName(),
        tint = Color.White.copy(alpha = 0.9f),
        modifier = modifier
            .rotate(rotation)
            .scale(scale)
    )
}

@Composable
fun WeatherDetailsGrid(weatherData: WeatherData) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        WeatherDetail(
            icon = Icons.Default.Thermostat,
            label = "Feels Like",
            value = "${weatherData.feelsLikeTemperature}°"
        )
        WeatherDetail(
            icon = Icons.Default.Water,
            label = "Humidity",
            value = "${weatherData.humidity}%"
        )
        WeatherDetail(
            icon = Icons.Default.Air,
            label = "Wind",
            value = "${weatherData.windSpeed} km/h"
        )
    }
}

@Composable
fun WeatherDetail(icon: ImageVector, label: String, value: String) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .background(
                color = Color.White.copy(alpha = 0.15f),
                shape = RoundedCornerShape(12.dp)
            )
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = Color.White.copy(alpha = 0.8f),
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label,
            fontSize = 11.sp,
            color = Color.White.copy(alpha = 0.7f),
            fontWeight = FontWeight.Medium
        )
        Text(
            text = value,
            fontSize = 15.sp,
            color = Color.White,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
fun TemperatureBar(high: Int, low: Int, current: Int) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "$low°",
                fontSize = 13.sp,
                color = Color.White.copy(alpha = 0.7f),
                fontWeight = FontWeight.Medium
            )
            Text(
                text = "$high°",
                fontSize = 13.sp,
                color = Color.White.copy(alpha = 0.7f),
                fontWeight = FontWeight.Medium
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(Color.White.copy(alpha = 0.3f))
        ) {
            val progress = if (high != low) {
                ((current - low).toFloat() / (high - low).toFloat()).coerceIn(0f, 1f)
            } else 0.5f

            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(progress)
                    .background(
                        brush = Brush.horizontalGradient(
                            colors = listOf(
                                Color(0xFF60A5FA),
                                Color(0xFFF59E0B)
                            )
                        )
                    )
            )

            // Current temperature indicator
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .fillMaxWidth(progress)
                    .offset(x = (-6).dp)
            ) {
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .size(12.dp)
                        .background(Color.White, shape = RoundedCornerShape(6.dp))
                )
            }
        }
    }
}

// Extension function for display names
fun WeatherCondition.displayName(): String = when (this) {
    WeatherCondition.SUNNY -> "Sunny"
    WeatherCondition.CLOUDY -> "Cloudy"
    WeatherCondition.RAINY -> "Rainy"
    WeatherCondition.STORMY -> "Stormy"
    WeatherCondition.SNOWY -> "Snowy"
    WeatherCondition.FOGGY -> "Foggy"
    WeatherCondition.PARTLY_CLOUDY -> "Partly Cloudy"
}
