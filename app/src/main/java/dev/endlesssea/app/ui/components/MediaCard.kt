package dev.endlesssea.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import dev.endlesssea.app.ui.search.SearchItemUi

/** Affiche 2:3 à grand rayon + badge (année/type) — inspiration Anymex, styles réglables. */
@Composable
fun MediaCard(item: SearchItemUi, onClick: () -> Unit) {
    val style by UiTuning.cardStyle.collectAsState()
    Column(
        modifier = Modifier.width(130.dp).clickable(onClick = onClick),
    ) {
        Box(
            Modifier.fillMaxWidth().height(185.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            AsyncImage(
                model = item.posterUrl,
                contentDescription = item.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxWidth().height(185.dp),
            )
            // ---- Badges toujours visibles (⭐ note + VF/VOSTFR fournis par la source)
            val cardBadges = buildList {
                item.rating?.let { add("⭐ %.1f".format(it)) }
                if (item.audioLangs.any { it.equals("vf", ignoreCase = true) }) add("VF")
                else if (item.audioLangs.any { it.equals("vostfr", ignoreCase = true) }) add("VOSTFR")
            }
            if (cardBadges.isNotEmpty()) {
                androidx.compose.foundation.layout.Column(
                    Modifier.align(Alignment.TopStart).padding(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    cardBadges.forEach { tag ->
                        Box(
                            Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color.Black.copy(alpha = 0.62f))
                                .padding(horizontal = 6.dp, vertical = 2.dp),
                        ) {
                            Text(
                                tag,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFFFD08A),
                            )
                        }
                    }
                }
            }
            if (style == "detail") {
                item.subtitle?.take(10)?.let { badge ->
                    Box(
                        Modifier
                            .align(Alignment.TopEnd)
                            .padding(8.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color.Black.copy(alpha = 0.55f))
                            .padding(horizontal = 8.dp, vertical = 3.dp),
                    ) {
                        Text(
                            badge,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
        }
        if (style != "poster") {
            Text(
                item.title,
                modifier = Modifier.padding(top = 6.dp, start = 2.dp),
                style = MaterialTheme.typography.bodySmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
