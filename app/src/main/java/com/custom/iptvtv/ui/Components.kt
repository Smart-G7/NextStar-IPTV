package com.custom.iptvtv.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.custom.iptvtv.data.MediaItem

const val GROUP_ALL = "Tümü"
const val GROUP_FAV = "★ Favoriler"

/** TV için odaklanabilir kutu: odakta büyür, vurgu çerçevesi çıkar. Tıklama = kumandada OK. */
@Composable
fun FocusBox(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(12.dp),
    selected: Boolean = false,
    scaleOnFocus: Float = 1.03f,
    color: Color = Pal.Surface,
    focusColor: Color = Pal.SurfaceHi,
    onFocused: () -> Unit = {},
    content: @Composable BoxScope.(focused: Boolean) -> Unit
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val currentOnFocused by rememberUpdatedState(onFocused)
    LaunchedEffect(focused) { if (focused) currentOnFocused() }
    val scale by animateFloatAsState(if (focused) scaleOnFocus else 1f, tween(120), label = "focusScale")
    val bg = when {
        focused -> focusColor
        selected -> Pal.Accent.copy(alpha = 0.18f)
        else -> color
    }
    val decorated = if (focused || selected) {
        Modifier.border(if (focused) 2.dp else 1.dp, if (focused) Pal.Accent else Pal.Accent.copy(alpha = 0.5f), shape)
    } else Modifier
    Box(
        modifier = modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(shape)
            .background(bg)
            .then(decorated)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
        content = { content(focused) }
    )
}

@Composable
fun Logo(
    title: String,
    url: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Fit,
    padding: Dp = 4.dp
) {
    var failed by remember(url) { mutableStateOf(false) }
    Box(modifier.clip(RoundedCornerShape(8.dp)).background(Pal.SurfaceHi), contentAlignment = Alignment.Center) {
        if (url.isNullOrBlank() || failed) {
            Text(title.trim().take(1).uppercase(), color = Pal.Muted, style = MaterialTheme.typography.titleLarge)
        } else {
            AsyncImage(
                model = url,
                contentDescription = null,
                contentScale = contentScale,
                onError = { failed = true },
                modifier = Modifier.fillMaxSize().padding(padding)
            )
        }
    }
}

@Composable
fun ActionButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    primary: Boolean = false
) {
    FocusBox(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(10.dp),
        scaleOnFocus = 1.04f,
        color = if (primary) Pal.Accent else Pal.Surface,
        focusColor = if (primary) Color.White else Pal.SurfaceHi
    ) {
        Row(
            Modifier.padding(horizontal = 18.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val tint = if (primary) Pal.OnAccent else Pal.Text
            if (icon != null) Icon(icon, null, tint = tint, modifier = Modifier.size(22.dp))
            Text(text, color = tint, fontWeight = FontWeight.SemiBold, maxLines = 1)
        }
    }
}

@Composable
fun Chip(text: String, selected: Boolean, onClick: () -> Unit) {
    FocusBox(onClick = onClick, shape = RoundedCornerShape(50), selected = selected, scaleOnFocus = 1.05f) {
        Text(
            text,
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 9.dp),
            color = if (selected) Pal.Accent else Pal.Text,
            maxLines = 1
        )
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(text, style = MaterialTheme.typography.titleMedium, color = Pal.Text, modifier = modifier)
}

@Composable
fun EmptyState(icon: ImageVector, title: String, message: String, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(icon, null, tint = Pal.Muted, modifier = Modifier.size(56.dp))
            Text(title, style = MaterialTheme.typography.titleMedium, color = Pal.Text)
            Text(message, color = Pal.Muted, textAlign = TextAlign.Center)
        }
    }
}

@Composable
fun AppTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    keyboardType: KeyboardType = KeyboardType.Text,
    onDone: () -> Unit = {}
) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(10.dp)
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        textStyle = TextStyle(color = Pal.Text, fontSize = 17.sp),
        cursorBrush = SolidColor(Pal.Accent),
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { onDone() }),
        modifier = modifier
            .onFocusChanged { focused = it.isFocused }
            .clip(shape)
            .background(if (focused) Pal.SurfaceHi else Pal.Surface)
            .border(if (focused) 2.dp else 1.dp, if (focused) Pal.Accent else Color(0x33FFFFFF), shape)
            .padding(horizontal = 16.dp, vertical = 13.dp),
        decorationBox = { inner ->
            Box {
                if (value.isEmpty()) Text(placeholder, color = Pal.Muted)
                inner()
            }
        }
    )
}

/** Yatay kart: logo üstte, başlık altta (ana sayfa satırları). */
@Composable
fun WideCard(item: MediaItem, onClick: () -> Unit, modifier: Modifier = Modifier) {
    FocusBox(onClick = onClick, modifier = modifier.size(width = 220.dp, height = 176.dp), shape = RoundedCornerShape(14.dp), scaleOnFocus = 1.06f) {
        Column {
            Logo(item.title, item.logo, Modifier.weight(1f).fillMaxWidth(), padding = 14.dp)
            Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                Text(item.title, maxLines = 1, color = Pal.Text, overflow = TextOverflow.Ellipsis)
                Text(item.group, maxLines = 1, color = Pal.Muted, style = MaterialTheme.typography.bodySmall, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

