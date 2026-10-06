package dev.endlesssea.app.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.endlesssea.app.ui.components.MediaCard
import dev.endlesssea.app.ui.components.glass
import dev.endlesssea.app.ui.search.SearchItemUi

/**
 * Page « Interface » (capture AnyMEX — *UI Settings*).
 *
 * Trois blocs : COMMUN (animations, barre translucide, en-tête, immersif),
 * MISE EN PAGE & STYLES (cartes, historique, carrousel, barre de navigation —
 * chacun dans une boîte avec aperçu en direct), EXTRAS (multiplicateurs halo,
 * rayon, flou, arrondi des cartes, marge de la barre).
 *
 * Tous les réglages sont persistés par AppPrefs et republiés dans UiTuning,
 * donc l'aperçu de la boîte reflète immédiatement le choix courant.
 */
@Composable
fun UiSettingsSection(viewModel: SettingsViewModel, state: SettingsUiState) {
    val enableAnimation by viewModel.enableAnimation.collectAsState()
    val translucentNav by viewModel.translucentNav.collectAsState()
    val legacyHeader by viewModel.legacyHeader.collectAsState()
    val immersive by viewModel.immersiveModeFlow.collectAsState()
    val cardStyle = state.cardStyle
    val historyStyle by viewModel.historyCardStyle.collectAsState()
    val carouselStyle by viewModel.carouselStyle.collectAsState()
    val navLayout by viewModel.navBarLayout.collectAsState()
    val navStyle by viewModel.navBarStyle.collectAsState()
    val glow by viewModel.glowMultiplier.collectAsState()
    val radius by viewModel.radiusMultiplier.collectAsState()
    val blur by viewModel.blurMultiplier.collectAsState()
    val roundness by viewModel.cardRoundness.collectAsState()
    val cardAnimation by viewModel.cardAnimation.collectAsState()

    var dialog by remember { mutableStateOf<String?>(null) }

    Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
        // ------------------------------------------------------------- COMMUN
        Section("Commun")
        GroupCard {
            SwitchRow(
                "", "Animations",
                "Carrousels animés et transitions fluides",
                enableAnimation, viewModel::setEnableAnimation,
            )
            HorizontalDivider(color = Color.White.copy(alpha = 0.08f))
            SwitchRow(
                "", "Barre translucide",
                "La barre de navigation laisse voir le contenu",
                translucentNav, viewModel::setTranslucentNav,
            )
            HorizontalDivider(color = Color.White.copy(alpha = 0.08f))
            SwitchRow(
                "", "En-tête classique",
                "Titre simple en haut des écrans d'accueil",
                legacyHeader, viewModel::setLegacyHeader,
            )
            HorizontalDivider(color = Color.White.copy(alpha = 0.08f))
            SwitchRow(
                "", "Mode immersif",
                "Masque les barres système (statut et navigation)",
                immersive, viewModel::setImmersiveMode,
            )
            HorizontalDivider(color = Color.White.copy(alpha = 0.08f))
            SwitchRow(
                "", "Animation des cartes",
                "Léger effet d'enfoncement à l'appui",
                cardAnimation, viewModel::setCardAnimation,
            )
        }

        // ------------------------------------------- MISE EN PAGE & STYLES
        Section("Mise en page & styles")
        GroupCard {
            NavRow("", "Style des cartes", CARD_STYLE_LABELS[cardStyle] ?: "Saikou") { dialog = "card" }
            HorizontalDivider(color = Color.White.copy(alpha = 0.08f))
            NavRow("", "Cartes d'historique", HISTORY_LABELS[historyStyle] ?: "Bootiful") { dialog = "history" }
            HorizontalDivider(color = Color.White.copy(alpha = 0.08f))
            NavRow("", "Carrousel d'accueil", CAROUSEL_LABELS[carouselStyle] ?: "Classique") { dialog = "carousel" }
            HorizontalDivider(color = Color.White.copy(alpha = 0.08f))
            NavRow(
                "", "Disposition de la barre",
                if (navLayout == "modern") "Moderne (Explorer + Bibliothèque)" else "Classique (mes onglets)",
            ) { dialog = "navLayout" }
            HorizontalDivider(color = Color.White.copy(alpha = 0.08f))
            NavRow(
                "", "Style de la barre",
                if (navStyle == "dynamic") "Pilule dynamique" else "Classique",
            ) { dialog = "navStyle" }
        }

        // ------------------------------------------------------------ EXTRAS
        Section("Extras")
        GroupCard {
            SliderRow("", "Multiplicateur de halo", "Intensité de la lueur des éléments", glow, viewModel::setGlowMultiplier)
            HorizontalDivider(color = Color.White.copy(alpha = 0.08f))
            SliderRow("", "Multiplicateur de rayon", "Arrondi des éléments d'interface", radius, viewModel::setRadiusMultiplier)
            HorizontalDivider(color = Color.White.copy(alpha = 0.08f))
            SliderRow("", "Multiplicateur de flou", "Diffusion des halos lumineux", blur, viewModel::setBlurMultiplier)
            HorizontalDivider(color = Color.White.copy(alpha = 0.08f))
            SliderRow("", "Arrondi des cartes", "Coins de toutes les cartes média", roundness, viewModel::setCardRoundness)
            HorizontalDivider(color = Color.White.copy(alpha = 0.08f))
            // §bordures : « trop de bordures, et des bordures à l'intérieur »
            Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
                val borders by viewModel.borderStrength.collectAsState()
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Liserés des cartes", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            "0 % = aucune bordure (verre franc, plus de cadre dans le cadre)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text("$borders %", style = MaterialTheme.typography.labelLarge)
                }
                Slider(
                    value = borders.toFloat(),
                    onValueChange = { viewModel.setBorderStrength(it.toInt()) },
                    valueRange = 0f..100f,
                )
            }
            HorizontalDivider(color = Color.White.copy(alpha = 0.08f))
            Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Marge de la barre", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    Badge("${state.barMargin}")
                }
                Slider(
                    value = state.barMargin.toFloat(),
                    onValueChange = { viewModel.setBarMargin(it.toInt()) },
                    valueRange = 0f..48f,
                )
            }
        }
        Spacer(Modifier.height(24.dp))
    }

    // ------------------------------------------------------------- boîtes
    when (dialog) {
        "card" -> ChoiceDialog(
            title = "Style des cartes",
            preview = { CardPreview() },
            options = CARD_STYLE_OPTIONS,
            selected = cardStyle,
            onSelect = viewModel::setCardStyle,
            onClose = { dialog = null },
        )
        "history" -> ChoiceDialog(
            title = "Cartes d'historique",
            preview = { HistoryPreview(historyStyle) },
            options = HISTORY_OPTIONS,
            selected = historyStyle,
            onSelect = viewModel::setHistoryCardStyle,
            onClose = { dialog = null },
        )
        "carousel" -> ChoiceDialog(
            title = "Carrousel d'accueil",
            preview = { CarouselPreview(carouselStyle) },
            options = CAROUSEL_OPTIONS,
            selected = carouselStyle,
            onSelect = viewModel::setCarouselStyle,
            onClose = { dialog = null },
        )
        "navLayout" -> ChoiceDialog(
            title = "Disposition de la barre",
            preview = null,
            options = listOf(
                Triple("legacy", "Barre classique", "Mes onglets : Accueil, Explorer, Recherche, Bibliothèque, Téléchargements."),
                Triple("modern", "Barre moderne", "Accueil, Explorer, Bibliothèque, Téléchargements — recherche intégrée à Explorer."),
            ),
            selected = navLayout,
            onSelect = viewModel::setNavBarLayout,
            onClose = { dialog = null },
        )
        "navStyle" -> ChoiceDialog(
            title = "Style de la barre",
            preview = { NavPreview(navStyle) },
            options = listOf(
                Triple("classic", "Classique", "Pilule avec icône et libellé."),
                Triple("dynamic", "Pilule dynamique", "L'onglet actif s'étire avec son libellé, les autres restent en icône."),
            ),
            selected = navStyle,
            onSelect = viewModel::setNavBarStyle,
            onClose = { dialog = null },
        )
    }
}

private val CARD_STYLE_OPTIONS = listOf(
    Triple("saikou", "Saikou", "Affiche verticale nette, note en pastille en bas à droite."),
    Triple("exotic", "Exotic", "Carte bordée, halo coloré et bandeau d'action vif."),
    Triple("minimal_exotic", "Minimal Exotic", "Carte bordée, titre sur dégradé bas, sans bandeau."),
    Triple("modern", "Moderne", "Affiche pleine, titre en surimpression sur dégradé sombre."),
)
private val CARD_STYLE_LABELS = CARD_STYLE_OPTIONS.associate { it.first to it.second }

private val HISTORY_OPTIONS = listOf(
    Triple("regular", "Classique", "Titre, progression et date, mise en page sobre."),
    Triple("frosted", "Verre dépoli", "Conteneur en verre translucide sur fond coloré doux."),
    Triple("bootiful", "Bootiful", "Mise en page immersive, l'affiche d'abord."),
)
private val HISTORY_LABELS = HISTORY_OPTIONS.associate { it.first to it.second }

private val CAROUSEL_OPTIONS = listOf(
    Triple("classic", "Classique", "Bannière pleine largeur, détails compacts et synopsis court."),
    Triple("portrait", "Portrait", "Cartes d'affiches verticales qui défilent."),
)
private val CAROUSEL_LABELS = CAROUSEL_OPTIONS.associate { it.first to it.second }

// --------------------------------------------------------------- briques

@Composable
private fun Section(title: String) {
    Text(
        title.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 6.dp, top = 18.dp, bottom = 8.dp),
    )
}

@Composable
private fun GroupCard(content: @Composable () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().glass(cornerRadius = 22.dp),
        color = Color.Transparent,
    ) {
        Column { content() }
    }
}

@Composable
private fun Tile(emoji: String) {
    Box(
        Modifier
            .size(40.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)),
        contentAlignment = Alignment.Center,
    ) { Text(emoji) }
}

@Composable
private fun SwitchRow(
    emoji: String,
    title: String,
    subtitle: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().clickable { onChange(!checked) }.padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Tile(emoji)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun NavRow(emoji: String, title: String, value: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Tile(emoji)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                value,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Icon(
            Icons.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun Badge(value: String) {
    Box(
        Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.16f))
            .padding(horizontal = 10.dp, vertical = 4.dp),
    ) {
        Text(value, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
    }
}

@Composable
private fun SliderRow(
    emoji: String,
    title: String,
    subtitle: String,
    value: Float,
    onChange: (Float) -> Unit,
) {
    Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Tile(emoji)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyLarge)
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Badge("%.1f".format(value))
        }
        Slider(value = value, onValueChange = onChange, valueRange = 0f..5f, steps = 49)
    }
}

/** Boîte « choix + aperçu en direct », reprise des captures AnyMEX. */
@Composable
private fun ChoiceDialog(
    title: String,
    preview: (@Composable () -> Unit)?,
    options: List<Triple<String, String, String>>,
    selected: String,
    onSelect: (String) -> Unit,
    onClose: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(title, fontWeight = FontWeight.Bold) },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                if (preview != null) {
                    Text(
                        "APERÇU EN DIRECT",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Box(
                        Modifier.fillMaxWidth().glass(cornerRadius = 18.dp).padding(12.dp),
                        contentAlignment = Alignment.Center,
                    ) { preview() }
                    Spacer(Modifier.height(8.dp))
                }
                options.forEach { (key, label, desc) ->
                    Row(
                        Modifier.fillMaxWidth().clickable { onSelect(key) }.padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(label, style = MaterialTheme.typography.bodyLarge)
                            Text(
                                desc,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        RadioButton(selected = selected == key, onClick = { onSelect(key) })
                    }
                }
            }
        },
        confirmButton = { Button(onClick = onClose) { Text("Terminé") } },
        dismissButton = { TextButton(onClick = onClose) { Text("Fermer") } },
    )
}

// -------------------------------------------------------------- aperçus

@Composable
private fun CardPreview() {
    MediaCard(
        item = SearchItemUi(
            id = "preview",
            title = "Demon Slayer",
            posterUrl = null,
            subtitle = "2026",
            rating = 8.7,
            audioLangs = listOf("vostfr"),
        ),
        onClick = {},
    )
}

@Composable
private fun HistoryPreview(style: String) {
    val accent = MaterialTheme.colorScheme.primary
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(
                when (style) {
                    "frosted" -> Color.White.copy(alpha = 0.10f)
                    "bootiful" -> accent.copy(alpha = 0.18f)
                    else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                },
            )
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(width = 46.dp, height = 64.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(accent.copy(alpha = 0.35f)),
        )
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text("Épisode 3", style = MaterialTheme.typography.labelLarge, color = accent)
            Text("Sabito et Makomo", style = MaterialTheme.typography.bodyMedium)
            Text(
                "19:37 restantes",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun CarouselPreview(style: String) {
    val accent = MaterialTheme.colorScheme.primary
    if (style == "portrait") {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            repeat(3) {
                Box(
                    Modifier
                        .size(width = 54.dp, height = 78.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(accent.copy(alpha = if (it == 1) 0.45f else 0.2f)),
                )
            }
        }
    } else {
        Box(
            Modifier
                .fillMaxWidth()
                .height(78.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(accent.copy(alpha = 0.3f)),
            contentAlignment = Alignment.BottomStart,
        ) {
            Text("Titre à la une", Modifier.padding(10.dp), style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
private fun NavPreview(style: String) {
    val accent = MaterialTheme.colorScheme.primary
    Row(
        Modifier.clip(RoundedCornerShape(30.dp)).background(Color.White.copy(alpha = 0.08f)).padding(6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            Modifier.clip(RoundedCornerShape(24.dp)).background(accent).padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("", style = MaterialTheme.typography.labelLarge)
            if (style == "dynamic") {
                Spacer(Modifier.width(6.dp))
                Text("Accueil", style = MaterialTheme.typography.labelLarge, color = Color.Black)
            }
        }
        listOf("", "", "").forEach {
            Box(Modifier.size(36.dp), contentAlignment = Alignment.Center) { Text(it) }
        }
    }
}
