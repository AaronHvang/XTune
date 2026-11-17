package com.qqmusictv.ui.screens

import com.qqmusictv.i18n.tr
import com.qqmusictv.R

import com.qqmusictv.ui.components.pageHorizontalInset
import com.qqmusictv.ui.components.FadeThrough
import com.qqmusictv.ui.components.TouchSurface as Surface

import androidx.compose.foundation.background
import com.qqmusictv.ui.components.returnToTabOnUp
import androidx.compose.foundation.layout.*
import com.qqmusictv.ui.navigation.ReturnLazyColumn as LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.material3.CircularProgressIndicator
import androidx.tv.material3.*
import coil.compose.AsyncImage
import com.qqmusictv.data.datasource.RadioStation
import com.qqmusictv.ui.viewmodel.PlayerViewModel
import com.qqmusictv.ui.viewmodel.RadioViewModel

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun RadioScreen(playerVm: PlayerViewModel, modifier: Modifier = Modifier, onReturnToTab: (() -> Unit)? = null) {
    val vm: RadioViewModel = hiltViewModel()
    val state by vm.state.collectAsState()
    var adding by remember { mutableStateOf(false) }
    var addDraft by remember { mutableStateOf("") }
    val addFocus = remember { FocusRequester() }
    LaunchedEffect(adding) { if (adding) runCatching { addFocus.requestFocus() } }

    Column(modifier.fillMaxSize().background(com.qqmusictv.ui.theme.AppPalette.adaptive(Color(0xFFFFFFFF), Color(0xFF101012))).padding(start = pageHorizontalInset, end = pageHorizontalInset, top = 100.dp, bottom = 28.dp)) {
        // Title + search on one line
        Row(Modifier.returnToTabOnUp(onReturnToTab), verticalAlignment = Alignment.CenterVertically) {
            Text(tr(R.string.msg_108), fontSize = 22.sp, fontWeight = FontWeight.SemiBold, color = com.qqmusictv.ui.theme.AppPalette.primary)
            Spacer(Modifier.width(24.dp))
            Row(
                Modifier.width(360.dp).height(38.dp).background(com.qqmusictv.ui.theme.AppPalette.adaptive(Color(0xFFF5F5F7), Color(0xFF222225)), RoundedCornerShape(10.dp)).padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BasicTextField(
                    value = state.query, onValueChange = vm::onQueryChange, singleLine = true,
                    textStyle = TextStyle(color = com.qqmusictv.ui.theme.AppPalette.primary, fontSize = 14.sp),
                    cursorBrush = SolidColor(Color(0xFF31C27C)),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { vm.search() }),
                    modifier = Modifier.weight(1f),
                    decorationBox = { inner ->
                        if (state.query.isEmpty()) Text(tr(R.string.msg_109), color = com.qqmusictv.ui.theme.AppPalette.adaptive(Color(0xFF8E8E93), Color(0xFFA3A3AA)), fontSize = 14.sp)
                        inner()
                    },
                )
                Chip(tr(R.string.msg_090)) { vm.search() }
            }
        }

        Spacer(Modifier.height(12.dp))

        // Country chips: Popular + detected/added + "+"
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            SelChip(tr(R.string.msg_110), state.activeCountry == "") { vm.selectPopular() }
            state.countries.forEach { c ->
                SelChip(c, state.activeCountry == c) { vm.selectCountry(c) }
            }
            if (adding) {
                com.qqmusictv.ui.navigation.ReturnFocusLayer {
                    androidx.activity.compose.BackHandler { adding=false }
                Row(
                    Modifier.height(34.dp).background(com.qqmusictv.ui.theme.AppPalette.adaptive(Color(0xFFF5F5F7), Color(0xFF222225)), RoundedCornerShape(50)).padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    BasicTextField(
                        value = addDraft, onValueChange = { addDraft = it }, singleLine = true,
                        textStyle = TextStyle(color = com.qqmusictv.ui.theme.AppPalette.primary, fontSize = 13.sp),
                        cursorBrush = SolidColor(Color(0xFF31C27C)),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = {
                            vm.addCountry(addDraft); addDraft = ""; adding = false
                        }),
                        modifier = Modifier.width(120.dp).focusRequester(addFocus),
                        decorationBox = { inner ->
                            if (addDraft.isEmpty()) Text(tr(R.string.msg_111), color = com.qqmusictv.ui.theme.AppPalette.adaptive(Color(0xFF8E8E93), Color(0xFFA3A3AA)), fontSize = 13.sp)
                            inner()
                        },
                    )
                }
            }
            } else {
                Chip("＋") { adding = true }
            }
        }

        state.correctionNote?.let {
            Spacer(Modifier.height(6.dp))
            Text(tr(R.string.msg_112, it), fontSize = 11.sp, color = Color(0xFF31C27C))
        }

        Spacer(Modifier.height(12.dp))

        FadeThrough(state, modifier = Modifier.fillMaxSize(), contentKey = { when { it.loading && it.stations.isEmpty() -> "loading"; it.stations.isEmpty() -> "empty"; else -> "${it.activeCountry}-${it.loading}" } }) { state ->
            if (state.loading && state.stations.isEmpty()) {
                Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator(color = Color(0xFF31C27C)) }
            } else if (state.stations.isEmpty()) {
                Box(Modifier.fillMaxSize(), Alignment.Center) { Text(tr(R.string.msg_113), color = com.qqmusictv.ui.theme.AppPalette.adaptive(Color(0xFF6E6E73), Color(0xFFB1B1B8)), fontSize = 14.sp) }
            } else {
                LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(state.stations, key = { it.id }) { st ->
                        StationRow(st) { st.streamUrl?.let { url -> playerVm.playInternetRadio(st.name, url, ((st.tags?.ifBlank { st.country }) ?: st.country).orEmpty()) } }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun StationRow(st: RadioStation, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(8.dp)),
        colors = ClickableSurfaceDefaults.colors(containerColor = com.qqmusictv.ui.theme.AppPalette.adaptive(Color(0xFFE8E8ED), Color(0xFF38383D)), focusedContainerColor = com.qqmusictv.ui.theme.AppPalette.adaptive(Color(0xFFE8E8ED), Color(0xFF38383D))),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.005f),
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Box(Modifier.size(38.dp).background(com.qqmusictv.ui.theme.AppPalette.adaptive(Color(0xFFE8E8ED), Color(0xFF38383D)), RoundedCornerShape(6.dp)), Alignment.Center) {
                if (st.faviconUrl != null) AsyncImage(model = st.faviconUrl, contentDescription = null, modifier = Modifier.fillMaxSize())
                else Text("📻", fontSize = 17.sp)
            }
            Column(Modifier.weight(1f)) {
                Text(st.name, fontSize = 14.sp, color = com.qqmusictv.ui.theme.AppPalette.primary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                val sub = listOfNotNull(
                    st.country?.uppercase()?.takeIf { it.isNotBlank() },
                    st.tags?.takeIf { it.isNotBlank() }?.split(",")?.firstOrNull()?.trim(),
                    st.bitrate.takeIf { it > 0 }?.let { "${it}kbps" },
                ).joinToString(" · ")
                if (sub.isNotEmpty()) Text(sub, fontSize = 10.sp, color = com.qqmusictv.ui.theme.AppPalette.adaptive(Color(0xFF6E6E73), Color(0xFFB1B1B8)), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 1.dp))
            }
            com.qqmusictv.ui.components.Icon(com.qqmusictv.ui.components.Glyph.PLAY, size = 13.dp, color = Color(0xFF31C27C))
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun Chip(label: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(50)),
        colors = com.qqmusictv.ui.components.contentActionColors(com.qqmusictv.ui.theme.AppPalette.adaptive(Color(0xFFE8E8ED), Color(0xFF38383D))),
        border = com.qqmusictv.ui.components.contentActionBorder(RoundedCornerShape(50)),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.06f),
    ) {
        Text(label, fontSize = 12.sp, color = com.qqmusictv.ui.theme.AppPalette.primary, fontWeight = FontWeight.Medium, modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp))
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun SelChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(50)),
        colors = com.qqmusictv.ui.components.contentActionColors(if (selected) Color(0xFF31C27C) else com.qqmusictv.ui.theme.AppPalette.adaptive(Color(0xFFE8E8ED), Color(0xFF38383D))),
        border = com.qqmusictv.ui.components.contentActionBorder(RoundedCornerShape(50)),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.06f),
    ) {
        Text(label, fontSize = 12.sp, color = com.qqmusictv.ui.theme.AppPalette.primary, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp))
    }
}
