package com.github.alphapaca.claudeclient.presentation.weather

import androidx.compose.animation.core.EaseOutQuart
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AcUnit
import androidx.compose.material.icons.filled.Air
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Opacity
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material.icons.filled.Thunderstorm
import androidx.compose.material.icons.filled.Water
import androidx.compose.material.icons.filled.WbCloudy
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import com.github.alphapaca.claudeclient.domain.model.ConversationItem.WeatherData
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

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
fun WeatherBackground(condition: WeatherData.Condition) {
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
        WeatherData.Condition.SUNNY -> listOf(
            Color(0xFFFDB813),
            Color(0xFFF37335),
            Color(0xFFEF4E7B)
        )
        WeatherData.Condition.CLOUDY -> listOf(
            Color(0xFF696E79),
            Color(0xFFA4B0BD),
            Color(0xFF57606F)
        )
        WeatherData.Condition.RAINY -> listOf(
            Color(0xFF4A69BD),
            Color(0xFF6A89CC),
            Color(0xFF1E3799)
        )
        WeatherData.Condition.STORMY -> listOf(
            Color(0xFF2C3A47),
            Color(0xFF485460),
            Color(0xFF1B262C)
        )
        WeatherData.Condition.SNOWY -> listOf(
            Color(0xFF8FBDD3),
            Color(0xFFB8E6F6),
            Color(0xFF5DA7C0)
        )
        WeatherData.Condition.FOGGY -> listOf(
            Color(0xFF9CA3AF),
            Color(0xFFD1D5DB),
            Color(0xFF6B7280)
        )
        WeatherData.Condition.PARTLY_CLOUDY -> listOf(
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
                WeatherData.Condition.SUNNY -> drawSunRays(animatedOffset)
                WeatherData.Condition.RAINY -> drawRainDrops(animatedOffset)
                WeatherData.Condition.SNOWY -> drawSnowflakes(animatedOffset)
                WeatherData.Condition.CLOUDY, WeatherData.Condition.PARTLY_CLOUDY -> drawClouds(animatedOffset)
                WeatherData.Condition.STORMY -> drawLightning(animatedOffset)
                WeatherData.Condition.FOGGY -> drawFog(animatedOffset)
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
fun AnimatedWeatherIcon(condition: WeatherData.Condition, modifier: Modifier = Modifier) {
    val infiniteTransition = rememberInfiniteTransition(label = "icon")

    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = if (condition == WeatherData.Condition.SUNNY) 360f else 0f,
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
        WeatherData.Condition.SUNNY -> Icons.Default.WbSunny
        WeatherData.Condition.CLOUDY -> Icons.Default.Cloud
        WeatherData.Condition.RAINY -> Icons.Default.Opacity
        WeatherData.Condition.STORMY -> Icons.Default.Thunderstorm
        WeatherData.Condition.SNOWY -> Icons.Default.AcUnit
        WeatherData.Condition.FOGGY -> Icons.Default.Cloud
        WeatherData.Condition.PARTLY_CLOUDY -> Icons.Default.WbCloudy
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
fun WeatherData.Condition.displayName(): String = when (this) {
    WeatherData.Condition.SUNNY -> "Sunny"
    WeatherData.Condition.CLOUDY -> "Cloudy"
    WeatherData.Condition.RAINY -> "Rainy"
    WeatherData.Condition.STORMY -> "Stormy"
    WeatherData.Condition.SNOWY -> "Snowy"
    WeatherData.Condition.FOGGY -> "Foggy"
    WeatherData.Condition.PARTLY_CLOUDY -> "Partly Cloudy"
}
