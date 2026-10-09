package dev.endlesssea.app.ui.details

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.endlesssea.app.tracking.TrackerSearchHit
import dev.endlesssea.app.ui.components.GlassCard

/** Metadata choice is deliberately separate from the progress tracker, including on movies. */
@Composable
internal fun MetadataCard(viewModel: DetailsViewModel, onOpenAccounts: () -> Unit) {
    val services by viewModel.metadataServices.collectAsState()
    val search by viewModel.metadataSearch.collectAsState()
    var open by remember { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    var selection by remember { mutableStateOf<Pair<String, TrackerSearchHit>?>(null) }
    GlassCard(Modifier.fillMaxWidth().padding(horizontal=16.dp), contentPadding=PaddingValues(14.dp)) {
        Column {
            Text("Métadonnées", style=MaterialTheme.typography.titleMedium)
            Text("Choisir ou corriger le titre, l’affiche et le résumé, sans changer le suivi.", style=MaterialTheme.typography.bodySmall)
            TextButton(onClick={ if(services.isEmpty()) onOpenAccounts() else open=true }) {
                Text(if(services.isEmpty()) "Connecter TMDB ou AniList" else "Choisir la source et le titre")
            }
        }
    }
    if(open) AlertDialog(onDismissRequest={open=false}, title={Text("Source des métadonnées")},
        text={ Column {
            OutlinedTextField(query,{query=it},label={Text("Titre (vide : titre de la fiche)")},singleLine=true)
            Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                services.forEach { service -> FilterChip(selected=search.service==service,
                    onClick={viewModel.searchMetadata(service,query)},label={Text(if(service=="TMDB") "TMDB" else "AniList")}) }
            }
            if(search.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
            else if(search.error!=null) Text(search.error.orEmpty(),color=MaterialTheme.colorScheme.error)
            else if(search.service!=null && search.hits.isEmpty()) Text("Aucun résultat. Modifiez le titre, puis touchez la source pour relancer.")
            LazyColumn(Modifier.heightIn(max=280.dp)) {
                items(search.hits, key={it.remoteId}) { hit ->
                    Column(Modifier.fillMaxWidth().clickable { search.service?.let { selection=it to hit } }.padding(vertical=10.dp)) {
                        Text(hit.title,style=MaterialTheme.typography.bodyLarge)
                        Text(listOfNotNull(hit.year?.toString(), when { hit.remoteId.startsWith("movie:")->"Film";hit.remoteId.startsWith("tv:")->"Série";else->"Anime" }).joinToString(" · "),style=MaterialTheme.typography.bodySmall)
                    }
                }
            }
        } }, confirmButton={TextButton(onClick={open=false}) {Text("Fermer")}})
    selection?.let { (service,hit) ->
        AlertDialog(onDismissRequest={selection=null},title={Text("Importer depuis $service ?")},
            text={Text("${hit.title}\nVos corrections personnelles restent prioritaires. Le tracker, la progression et les fichiers ne changent pas.")},
            confirmButton={TextButton(onClick={viewModel.importTrackerMetadata(service,hit.remoteId);selection=null;open=false}) {Text("Importer")}},
            dismissButton={TextButton(onClick={selection=null}) {Text("Annuler")}})
    }
}
