package dev.endlesssea.app.ui.explore

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import dev.endlesssea.app.ui.components.*

private const val EXPLORE_PROGRAMS_KEY = "__endlesssea_programs__"
private const val EXPLORE_NEWS_KEY = "__endlesssea_news__"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExploreScreen(onMediaClick: (String) -> Unit, onSearch: (String,String) -> Unit = {_,_->},
    onSeeAll: (String,String) -> Unit, viewModel: ExploreViewModel = androidx.hilt.navigation.compose.hiltViewModel()) {
    val state by viewModel.uiState.collectAsState()
    val suggestions by viewModel.suggestions.collectAsState()
    var source by rememberSaveable { mutableStateOf("ALL") }
    var query by rememberSaveable { mutableStateOf("") }
    var category by rememberSaveable(source) { mutableStateOf<String?>(null) }
    var suggested by rememberSaveable { mutableStateOf(false) }
    var sources by remember { mutableStateOf(false) }
    var filters by remember { mutableStateOf(false) }
    var errors by remember { mutableStateOf(false) }
    var genre by rememberSaveable { mutableStateOf<String?>(null) }
    var year by rememberSaveable { mutableStateOf<Int?>(null) }
    var type by rememberSaveable { mutableStateOf<String?>(null) }
    val visible = state.rows.filter { source == "ALL" || it.pkg == source }
    val selected = visible.firstOrNull { "${it.pkg}:${it.category}" == category }
    val isPrograms = category == EXPLORE_PROGRAMS_KEY
    val isNews = category == EXPLORE_NEWS_KEY
    val rows = when {
        suggested -> listOf(ExploreRowUi("Suggestions", suggestions.filter { source == "ALL" || it.id.startsWith("$source:") }, source))
        isPrograms -> visible.filter { row ->
            exploreSpecialKind(row.category, row.title.substringAfter(" — ", "")) == ExploreSpecialKind.PROGRAMS
        }
        isNews -> visible.filter { row ->
            exploreSpecialKind(row.category, row.title.substringAfter(" — ", "")) == ExploreSpecialKind.NEWS
        }
        selected != null -> listOf(selected)
        else -> visible
    }
    val cards = filterExploreRows(rows,genre,year,type).flatMap { it.items }.distinctBy { it.id }
    val seeAllRow = selected ?: rows.singleOrNull()?.takeIf { isPrograms || isNews }
    Column(Modifier.fillMaxSize()) {
        EndlessSeaTopBar("Explorer",icon=Icons.Default.Explore)
        // Filters and search scroll away with the catalogue; only the section bar stays fixed.
        LazyVerticalGrid(GridCells.Adaptive(140.dp), modifier=Modifier.weight(1f),
            contentPadding=PaddingValues(bottom=16.dp), horizontalArrangement=Arrangement.spacedBy(8.dp),
            verticalArrangement=Arrangement.spacedBy(16.dp)) {
          item(span={GridItemSpan(maxLineSpan)}) {
           Column {
        OutlinedTextField(query,{query=it},Modifier.fillMaxWidth().padding(horizontal=16.dp,vertical=12.dp),singleLine=true,
            placeholder={Text("Chercher un film, une série…")},leadingIcon={Icon(Icons.Default.Search,null)},
            trailingIcon={IconButton(onClick={onSearch(query,source)}) {Icon(Icons.Default.ArrowForward,"Lancer la recherche")}},
            shape=androidx.compose.foundation.shape.RoundedCornerShape(18.dp),
            keyboardOptions=KeyboardOptions(imeAction=ImeAction.Search),keyboardActions=KeyboardActions(onSearch={onSearch(query,source)}))
        Row(Modifier.fillMaxWidth().padding(horizontal=16.dp),verticalAlignment=Alignment.CenterVertically) {
            FilledTonalButton(onClick={sources=true},modifier=Modifier.weight(1f)) {
                Text(state.extensions.firstOrNull{it.first==source}?.second ?: "Toutes les extensions",maxLines=1)
                Spacer(Modifier.width(6.dp));Icon(Icons.Default.ExpandMore,null)
            }
            IconButton(onClick={filters=true}) {Icon(Icons.Default.Tune,"Filtres de découverte")}
            IconButton(onClick={viewModel.refresh()},enabled=!state.loading) {Icon(Icons.Default.Refresh,"Actualiser les catalogues")}
        }
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal=16.dp,vertical=8.dp),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            FilterChip(!suggested && category == null, { suggested = false; category = null }, label = { Text("À découvrir") })
            FilterChip(suggested, { suggested = true; category = null }, label = { Text("Pour vous") })
            FilterChip(isPrograms, { suggested = false; category = EXPLORE_PROGRAMS_KEY }, label = { Text("Programmes") })
            FilterChip(isNews, { suggested = false; category = EXPLORE_NEWS_KEY }, label = { Text("Actualités") })
            visible.forEach { row ->
                FilterChip(
                    !suggested && category == "${row.pkg}:${row.category}",
                    { suggested = false; category = "${row.pkg}:${row.category}" },
                    label = { Text(row.title.substringAfter(" — "), maxLines = 1) },
                )
            }
        }
        if(state.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
        if(state.errors.isNotEmpty()) TextButton(onClick={errors=true},modifier=Modifier.padding(horizontal=16.dp)) {Text("${state.errors.size} source(s) indisponible(s) · Détails")}
        Row(Modifier.fillMaxWidth().padding(horizontal=16.dp),verticalAlignment=Alignment.CenterVertically) {
            Text(
                when {
                    suggested -> "Selon votre activité"
                    isPrograms -> "Programmes & plannings"
                    isNews -> "Actualités"
                    else -> selected?.title?.substringAfter(" — ") ?: "À découvrir"
                },
                Modifier.weight(1f),
                style = MaterialTheme.typography.titleMedium,
            )
            if (seeAllRow != null && !suggested) {
                TextButton(onClick = { onSeeAll(seeAllRow.pkg, seeAllRow.category) }) { Text("Tout voir") }
            }
        }
        if (cards.isEmpty() && !state.loading) Column(
            Modifier.fillMaxWidth().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                if (state.extensionCount == 0) "Ajoutez votre première extension"
                else if (isPrograms) "Aucun programme ou planning fourni"
                else if (isNews) "Aucune actualité fournie"
                else "Aucun titre pour cette sélection",
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                when {
                    state.extensionCount == 0 -> "Les rubriques disponibles dépendent des extensions activées."
                    isPrograms || isNews -> "Cette section s'affiche lorsqu'une extension activée déclare une rubrique correspondante."
                    suggested -> "Les suggestions dépendent des catalogues et de votre historique."
                    else -> "Essayez une autre source ou ajustez les filtres."
                },
                Modifier.padding(top = 8.dp),
            )
            val menu = LocalSectionMenu.current
            if (state.extensionCount == 0) TextButton(onClick = menu.extensions) { Text("Ouvrir les extensions") }
        }
           }
          }
            items(cards,key={it.id}) { item -> Box(Modifier.padding(horizontal=8.dp)) { CatalogPoster(item,{onMediaClick(item.id)},caption=item.year?.toString()) } }
        }
    }
    if(sources) ModalBottomSheet(onDismissRequest={sources=false}) {
        Column(Modifier.fillMaxWidth().padding(20.dp)) {
            Text("Choisir une extension",style=MaterialTheme.typography.titleLarge)
            LazyColumn(Modifier.heightIn(max=420.dp)) {
                item {TextButton(onClick={source="ALL";sources=false}) {Text("Toutes les extensions")}}
                items(state.extensions,key={it.first}) {(id,name)->TextButton(onClick={source=id;sources=false}) {Text((if(source==id) "✓ " else "")+name)}}
            }
        }
    }
    if(filters) FilterDialog({filters=false},genre,year,type,{genre=it},{year=it},{type=it},{genre=null;year=null;type=null})
    if(errors) AlertDialog(onDismissRequest={errors=false},title={Text("Sources indisponibles")},text={LazyColumn(Modifier.heightIn(max=320.dp)) {
        items(state.errors.toList()) {(name,error)->Text("$name : $error",Modifier.padding(vertical=6.dp))}
    }},confirmButton={TextButton(onClick={errors=false}) {Text("Fermer")}})
}
