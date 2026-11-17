package com.qqmusictv.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.qqmusictv.R

/** Small QQ Music note mark shown in the top bar when no account avatar is available. */
@Composable
fun QQNoteMark(modifier: Modifier = Modifier, color: Color = Color(0xFF31C27C)) {
    Box(modifier, contentAlignment = Alignment.Center) {
        androidx.compose.material3.Text(
            text = "♪",
            color = color,
            fontSize = 24.sp,
            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
        )
    }
}

@Composable
fun ProfileAvatar(url: String?, modifier: Modifier = Modifier, onLoadError: () -> Unit = {}) {
    var failed by remember(url) { mutableStateOf(false) }
    Box(modifier.size(44.dp).clip(CircleShape).background(Color.White)
        .border(0.5.dp, com.qqmusictv.ui.theme.AppPalette.adaptive(Color(0xFFD9D9DE), Color(0xFF48484D)), CircleShape), Alignment.Center) {
        QQNoteMark(Modifier.size(width = 23.dp, height = 30.dp))
        if (url != null && !failed) AsyncImage(model = url, contentDescription = null,
            contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize(),
            onError = { failed = true; onLoadError() })
    }
}
