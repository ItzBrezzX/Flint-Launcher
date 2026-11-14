package com.flintlauncher.android.ui.mods

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.flintlauncher.android.ui.state.ModsActions
import com.flintlauncher.android.ui.state.ModsUiState

private val Bg = Color(0xFF07110B)
private val Panel = Color(0xFF0B1410)
private val CardBg = Color(0xFF101B14)
private val Accent = Color(0xFF35D12C)
private val TextPrimary = Color(0xFFE8F0E8)
private val TextMuted = Color(0xFF8A9A8E)
private val TextSoft = Color(0xFFC8D4C8)
private val Border = Color(0xFF1A2A1E)
private val TagBg = Color(0xFF1A2A1E)
private val DarkText = Color(0xFF07110B)
private val StarYellow = Color(0xFFFFC107)

private val Round6 = RoundedCornerShape(6.dp)
private val Round8 = RoundedCornerShape(8.dp)
private val Round10 = RoundedCornerShape(10.dp)
private val Round12 = RoundedCornerShape(12.dp)
private val Round14 = RoundedCornerShape(14.dp)
private val Round16 = RoundedCornerShape(16.dp)
private val Round20 = RoundedCornerShape(20.dp)

private val BannerBrush = Brush.horizontalGradient(
    listOf(Color(0xFF0A2E1A), Color(0xFF145C2E), Color(0xFF0A2E1A))
)
private val AccentBorder = BorderStroke(1.dp, Accent.copy(alpha = 0.35f))
private val CardBorder = BorderStroke(1.dp, Border)
private val FeaturedBorder = BorderStroke(1.dp, Accent.copy(alpha = 0.5f))

@Immutable
private data class ModCard(
    val id: String,
    val name: String,
    val description: String,
    val downloads: String,
    val rating: String,
    val tags: List<String>,
    val iconEmoji: String,
)

private val SampleMods = listOf(
    ModCard("sodium", "Sodium", "A powerful performance mod.", "7.5M", "4.8", listOf("Fabric", "1.21.x", "Performance"), "⚡"),
    ModCard("iris", "Iris Shaders", "A modern shader mod.", "5.2M", "4.7", listOf("Fabric", "1.21.x", "Shaders"), "🌈"),
    ModCard("optifine", "OptiFine", "Boost your Minecraft performance.", "4.9M", "4.6", listOf("Forge", "Fabric", "Performance"), "🔧"),
    ModCard("backpack", "Backpack", "A simple backpack mod.", "3.1M", "4.5", listOf("Fabric", "1.21.x", "Utility"), "🎒"),
)

private val Categories = listOf("All", "Mods", "Shaders", "Resource Packs", "Add-Ons", "Data Packs", "Maps")
private val FeaturedItems = listOf(
    "Complementary Shaders" to "2.1M",
    "Sodium" to "1.8M",
    "Iris Shaders" to "1.6M",
)

@Composable
fun ModsScreen(
    state: ModsUiState,
    actions: ModsActions,
    modifier: Modifier = Modifier,
) {
    var selectedCategory by remember { mutableStateOf("All") }
    var showComingSoon by remember { mutableStateOf<String?>(null) }
    val onComingSoon: (String) -> Unit = remember { { showComingSoon = it } }

    Column(
        modifier
            .fillMaxSize()
            .background(Bg)
    ) {
        // Category tabs – full width, scrollable if needed
        CategoryTabs(
            selected = selectedCategory,
            onSelect = { selectedCategory = it },
            onMoreClick = { onComingSoon("More categories") },
        )

        Row(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(bottom = 0.dp),
        ) {
            // Center column
            Column(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .padding(start = 16.dp, end = 10.dp),
            ) {
                FeaturedBanner(onExplore = { onComingSoon("Explore") })
                Spacer(Modifier.height(12.dp))
                FilterRow(onComingSoon = onComingSoon)
                Spacer(Modifier.height(12.dp))

                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 260.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(bottom = 12.dp),
                    modifier = Modifier.weight(1f),
                ) {
                    items(items = SampleMods, key = { it.id }) { mod ->
                        ModCardItem(mod = mod, onDownload = { onComingSoon("Download ${mod.name}") })
                    }
                }
            }

            // Right Featured panel – fixed width so it never clips
            FeaturedPanel(onComingSoon = onComingSoon)
        }

        // Bottom bar – always fully visible
        DownloadQueueBar(onComingSoon = onComingSoon)
    }

    showComingSoon?.let { feature ->
        AlertDialog(
            onDismissRequest = { showComingSoon = null },
            title = { Text("Coming Soon") },
            text = { Text("Fitur \"$feature\" masih dalam pengembangan.") },
            confirmButton = {
                TextButton(onClick = { showComingSoon = null }) {
                    Text("OK", color = Accent)
                }
            },
            containerColor = CardBg,
            titleContentColor = TextPrimary,
            textContentColor = TextSoft,
        )
    }
}

@Composable
private fun CategoryTabs(
    selected: String,
    onSelect: (String) -> Unit,
    onMoreClick: () -> Unit,
) {
    val scroll = rememberScrollState()
    Row(
        Modifier
            .fillMaxWidth()
            .horizontalScroll(scroll)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Categories.forEach { cat ->
            val isSelected = selected == cat
            Box(
                Modifier
                    .clip(Round20)
                    .background(if (isSelected) Accent else CardBg)
                    .clickable { onSelect(cat) }
                    .padding(horizontal = 14.dp, vertical = 7.dp),
            ) {
                Text(
                    cat,
                    color = if (isSelected) DarkText else TextSoft,
                    fontSize = 12.sp,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                    maxLines = 1,
                )
            }
        }
        Box(
            Modifier
                .clip(Round20)
                .background(CardBg)
                .clickable(onClick = onMoreClick)
                .padding(horizontal = 14.dp, vertical = 7.dp),
        ) {
            Text("⋯ More", color = TextSoft, fontSize = 12.sp, maxLines = 1)
        }
    }
}

@Composable
private fun FeaturedBanner(onExplore: () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(150.dp)
            .clip(Round16)
            .background(BannerBrush)
            .border(AccentBorder, Round16),
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            Text("MINECRAFT", color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Black, maxLines = 1)
            Text("MOD MENU", color = Accent, fontSize = 26.sp, fontWeight = FontWeight.Black, maxLines = 1)
            Text(
                "Download the best mods, shaders and more!",
                color = TextSoft,
                fontSize = 12.sp,
                maxLines = 1,
                modifier = Modifier.padding(top = 2.dp),
            )
            Spacer(Modifier.height(10.dp))
            Button(
                onClick = onExplore,
                colors = ButtonDefaults.buttonColors(containerColor = Accent),
                shape = Round8,
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
            ) {
                Text("Explore Now →", color = DarkText, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
        }
    }
}

@Composable
private fun FilterRow(onComingSoon: (String) -> Unit) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FilterDropdown("Platform", "All Platforms") { onComingSoon("Platform filter") }
        FilterDropdown("Mod Loader", "All Loaders") { onComingSoon("Loader filter") }
        FilterDropdown("Minecraft Version", "All Versions") { onComingSoon("Version filter") }
        FilterDropdown("Sort By", "Most Downloaded") { onComingSoon("Sort") }
    }
}

@Composable
private fun FilterDropdown(label: String, value: String, onClick: () -> Unit) {
    Column(Modifier.weight(1f)) {
        Text(label, color = TextMuted, fontSize = 10.sp, maxLines = 1)
        Spacer(Modifier.height(3.dp))
        Row(
            Modifier
                .fillMaxWidth()
                .clip(Round8)
                .background(CardBg)
                .border(1.dp, Border, Round8)
                .clickable(onClick = onClick)
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                value,
                color = TextPrimary,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Icon(Icons.Default.ArrowDropDown, null, tint = TextMuted, modifier = Modifier.size(16.dp))
        }
    }
}

@Composable
private fun FeaturedPanel(onComingSoon: (String) -> Unit) {
    Column(
        Modifier
            .width(220.dp)
            .fillMaxHeight()
            .background(Panel)
            .padding(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Star, null, tint = Accent, modifier = Modifier.size(16.dp))
            Text(
                "Featured",
                color = TextPrimary,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                modifier = Modifier.padding(start = 5.dp),
                maxLines = 1,
            )
        }
        Spacer(Modifier.height(12.dp))
        FeaturedItems.forEach { (name, downloads) ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(Round10)
                    .clickable { onComingSoon(name) }
                    .padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier.size(36.dp).clip(Round8).background(TagBg),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("★", fontSize = 16.sp)
                }
                Column(Modifier.padding(start = 8.dp).weight(1f)) {
                    Text(name, color = TextPrimary, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("↓ $downloads", color = TextMuted, fontSize = 10.sp, maxLines = 1)
                }
            }
        }
        Spacer(Modifier.weight(1f))
        OutlinedButton(
            onClick = { onComingSoon("View More Featured") },
            modifier = Modifier.fillMaxWidth(),
            shape = Round10,
            border = FeaturedBorder,
            colors = ButtonDefaults.outlinedButtonColors(contentColor = Accent),
            contentPadding = PaddingValues(vertical = 6.dp),
        ) {
            Text("View More →", fontSize = 12.sp)
        }
    }
}

@Composable
private fun DownloadQueueBar(onComingSoon: (String) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(48.dp)
            .background(Panel)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Default.Download, null, tint = Accent, modifier = Modifier.size(16.dp))
        Text(
            "Download Queue (0)",
            color = TextPrimary,
            fontSize = 12.sp,
            modifier = Modifier.padding(start = 6.dp),
            maxLines = 1,
        )
        Spacer(Modifier.weight(1f))
        TextButton(
            onClick = { onComingSoon("Clear Queue") },
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
        ) {
            Icon(Icons.Default.Delete, null, tint = TextMuted, modifier = Modifier.size(14.dp))
            Text("Clear", color = TextMuted, fontSize = 12.sp, modifier = Modifier.padding(start = 3.dp))
        }
        Button(
            onClick = { onComingSoon("View Downloads") },
            colors = ButtonDefaults.buttonColors(containerColor = Accent),
            shape = Round8,
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
        ) {
            Text("View Downloads →", color = DarkText, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
        }
    }
}

@Composable
private fun ModCardItem(mod: ModCard, onDownload: () -> Unit) {
    Card(
        shape = Round14,
        colors = CardDefaults.cardColors(containerColor = CardBg),
        border = CardBorder,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(42.dp).clip(Round10).background(TagBg),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(mod.iconEmoji, fontSize = 20.sp)
                }
                Column(Modifier.padding(start = 10.dp).weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(mod.name, color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp, maxLines = 1)
                        Spacer(Modifier.width(4.dp))
                        Icon(Icons.Default.CheckCircle, null, tint = Accent, modifier = Modifier.size(14.dp))
                    }
                    Text(mod.description, color = TextMuted, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                mod.tags.forEach { tag ->
                    Box(
                        Modifier
                            .clip(Round6)
                            .background(TagBg)
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                    ) {
                        Text(tag, color = TextSoft, fontSize = 10.sp, maxLines = 1)
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Download, null, tint = TextMuted, modifier = Modifier.size(12.dp))
                Text(mod.downloads, color = TextMuted, fontSize = 11.sp, modifier = Modifier.padding(start = 3.dp))
                Spacer(Modifier.width(10.dp))
                Icon(Icons.Default.Star, null, tint = StarYellow, modifier = Modifier.size(12.dp))
                Text(mod.rating, color = TextMuted, fontSize = 11.sp, modifier = Modifier.padding(start = 3.dp))
                Spacer(Modifier.weight(1f))
                Button(
                    onClick = onDownload,
                    colors = ButtonDefaults.buttonColors(containerColor = Accent),
                    shape = Round8,
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                ) {
                    Icon(Icons.Default.Download, null, tint = DarkText, modifier = Modifier.size(12.dp))
                    Text("Download", color = DarkText, fontWeight = FontWeight.SemiBold, fontSize = 11.sp, modifier = Modifier.padding(start = 3.dp))
                }
            }
        }
    }
}
