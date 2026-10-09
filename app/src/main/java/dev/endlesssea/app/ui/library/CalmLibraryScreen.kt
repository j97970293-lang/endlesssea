package dev.endlesssea.app.ui.library

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import dev.endlesssea.app.ui.components.*
import dev.endlesssea.app.ui.search.SearchItemUi

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(onMediaClick: (String) -> Unit, onLocalFolderClick: (String) -> Unit = {},
    onDownloadedFolderClick: (String) -> Unit = {}, onFindSource: (String) -> Unit = {},
    viewModel: LibraryViewModel = androidx.hilt.navigation.compose.hiltViewModel()) {
    val state by viewModel.uiState.collectAsState()
    val categories by viewModel.customCategories.collectAsState()
    val directories by viewModel.localDirectories.collectAsState()
    val categoryTick by viewModel.categoryItemsTick.collectAsState()
    val context = LocalContext.current
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var query by rememberSaveable { mutableStateOf("") }
    var category by rememberSaveable { mutableStateOf("ALL") }
    var origin by rememberSaveable { mutableStateOf("ALL") }
    var folderCategory by rememberSaveable { mutableStateOf<String?>(null) }
    var showFilters by remember { mutableStateOf(false) }
    var manage by remember { mutableStateOf(false) }
    var newCategory by remember { mutableStateOf("") }
    var editing by remember { mutableStateOf<SearchItemUi?>(null) }
    val picker = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            runCatching { context.contentResolver.takePersistableUriPermission(uri,
                android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION) }
            viewModel.addLocalDir(uri.toString())
        }
    }
    LaunchedEffect(category) {
        viewModel.setOnDeviceOnly(false); viewModel.setSourceFilter("ALL"); viewModel.setWatchFilter("ALL"); viewModel.setFilterStatus("ALL")
        viewModel.navigate(category)
    }
    LaunchedEffect(tab) { if (tab == 1) viewModel.scanLocal() }
    fun open(item: SearchItemUi) { when {
        item.id.startsWith("local-folder:") -> onLocalFolderClick(item.id.removePrefix("local-folder:"))
        item.id.startsWith("downloaded:") -> onDownloadedFolderClick(item.id.removePrefix("downloaded:"))
        else -> onMediaClick(item.id)
    } }
    val localVideoUris = remember(state.localFiles) { state.localFiles.map { it.uri }.toSet() }
    val downloadedIds = state.downloadedGroups.mapNotNull { it.mediaId }.toSet()
    val selectedUris = remember(folderCategory,categoryTick) { folderCategory?.let { viewModel.categoryItems(it).toSet() } }
    val local = viewModel.folderCards(if (selectedUris == null) state.localFiles else state.localFiles.filter {
        it.uri in selectedUris || "folder:${it.parentUri}" in selectedUris
    })
    val candidates = if (tab == 0) state.items else buildList {
        if (origin != "LOCAL" && folderCategory == null) addAll(state.downloadedGroups.map { it.toCard() })
        if (origin != "DOWNLOADS") addAll(local)
    }
    val cards = candidates.distinctBy { it.id }.filter { it.title.contains(query,true) }.sortedBy { it.title.lowercase() }
    Column(Modifier.fillMaxSize()) {
        EndlessSeaTopBar("Bibliothèque", icon = Icons.Default.VideoLibrary, onMenu = { manage = true }, menuDescription = "Gérer mes dossiers et catégories")
        TabRow(tab) { listOf("Ma liste","Hors ligne","AniList").forEachIndexed { i,label -> Tab(tab == i,{ tab = i }, text = { Text(label) }) } }
        OutlinedTextField(query,{query=it},Modifier.fillMaxWidth().padding(horizontal=16.dp,vertical=12.dp),singleLine=true,
            placeholder={Text("Rechercher un titre")},leadingIcon={Icon(Icons.Default.Search,null)},
            trailingIcon={ if(query.isNotEmpty()) IconButton(onClick={query=""}) { Icon(Icons.Default.Close,"Effacer") } }, shape=androidx.compose.foundation.shape.RoundedCornerShape(18.dp))
        if (tab == 2) {
            dev.endlesssea.app.ui.tracking.AniListLibraryPanel(query,onMediaClick,onFindSource)
        } else {
            Row(Modifier.fillMaxWidth().padding(horizontal=16.dp),verticalAlignment=Alignment.CenterVertically) {
                Text("${cards.size} titres",Modifier.weight(1f),style=MaterialTheme.typography.labelLarge)
                TextButton(onClick={showFilters=true}) { Icon(Icons.Default.Tune,null,Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Filtrer") }
                if(tab==1) IconButton(onClick={viewModel.scanLocal()},enabled=!state.localScanning) { Icon(Icons.Default.Refresh,"Actualiser les fichiers") }
            }
            if(tab==1 && state.localScanning) LinearProgressIndicator(Modifier.fillMaxWidth())
            if(cards.isEmpty()) Column(Modifier.fillMaxWidth().padding(32.dp),horizontalAlignment=Alignment.CenterHorizontally) {
                Text(if(query.isNotBlank()) "Aucun résultat" else if(tab==1) "Vos vidéos, réunies par titre" else "Votre liste commence ici",style=MaterialTheme.typography.titleLarge)
                Text(if(tab==1) "Ajoutez un dossier ou téléchargez des épisodes depuis une fiche." else "Ajoutez des titres à votre liste depuis leur fiche. Vos listes AniList ont leur propre onglet.",Modifier.padding(top=10.dp),color=MaterialTheme.colorScheme.onSurfaceVariant)
                if(tab==1) Button(onClick={picker.launch(null)},modifier=Modifier.padding(top=16.dp)) { Text("Ajouter un dossier") }
            }
            LazyVerticalGrid(GridCells.Adaptive(140.dp),contentPadding=PaddingValues(16.dp),
                horizontalArrangement=Arrangement.spacedBy(14.dp),verticalArrangement=Arrangement.spacedBy(20.dp)) {
                items(cards,key={it.id}) { item -> CatalogPoster(item,{open(item)},
                    offline=tab==1 || item.id in downloadedIds || item.id.startsWith("local:"),
                    videoPoster=item.posterUrl in localVideoUris,
                    onLongClick={editing=item}) }
            }
        }
    }
    if(showFilters) ModalBottomSheet(onDismissRequest={showFilters=false}) {
        Column(Modifier.fillMaxWidth().padding(20.dp).verticalScroll(rememberScrollState())) {
            Text("Afficher",style=MaterialTheme.typography.titleLarge)
            if(tab==0) (listOf("Tous les titres" to "ALL") + LIBRARY_TABS.filter{it.second!="LOCAL"}).forEach { (label,id) ->
                TextButton(onClick={category=id;showFilters=false}) { Text((if(category==id) "✓ " else "")+label) }
            } else {
                listOf("Tout sur l'appareil" to "ALL","Téléchargements" to "DOWNLOADS","Mes dossiers" to "LOCAL").forEach { (label,id) ->
                    TextButton(onClick={origin=id;folderCategory=null;showFilters=false}) {Text((if(origin==id&&folderCategory==null) "✓ " else "")+label)}
                }
                categories.forEach { name -> TextButton(onClick={folderCategory=name;origin="LOCAL";showFilters=false}) {Text(name)} }
            }
        }
    }
    if(manage) ModalBottomSheet(onDismissRequest={manage=false}) {
        Column(Modifier.padding(20.dp).fillMaxWidth().verticalScroll(rememberScrollState())) {
            Text("Organiser ma bibliothèque",style=MaterialTheme.typography.titleLarge)
            TextButton(onClick={manage=false;tab=1;picker.launch(null)}) {Text("Ajouter un dossier vidéo")}
            directories.forEach { uri -> Row(verticalAlignment=Alignment.CenterVertically) {
                Text(dev.endlesssea.app.local.LocalNames.pretty(uri),Modifier.weight(1f),maxLines=2)
                TextButton(onClick={viewModel.removeLocalDir(uri)}) {Text("Retirer")}
            } }
            Text("Retirer un dossier ne supprime aucun fichier.",style=MaterialTheme.typography.bodySmall)
            OutlinedTextField(newCategory,{newCategory=it},label={Text("Nouvelle catégorie")},modifier=Modifier.padding(top=16.dp))
            TextButton(enabled=newCategory.isNotBlank(),onClick={viewModel.addCategory(newCategory.trim());newCategory=""}) {Text("Créer")}
            categories.forEach { name -> Row(verticalAlignment=Alignment.CenterVertically) {
                Text(name,Modifier.weight(1f));TextButton(onClick={viewModel.removeCategory(name)}) {Text("Retirer la catégorie")}
            } }
        }
    }
    editing?.let { item ->
        val folder=item.id.takeIf{it.startsWith("local-folder:")}?.removePrefix("local-folder:")
            ?: item.id.takeIf{it.startsWith("local:")}?.removePrefix("local:")
        var editedTitle by remember(item.id) { mutableStateOf(item.title) }
        var editedPoster by remember(item.id) { mutableStateOf(item.posterUrl.orEmpty()) }
        AlertDialog(onDismissRequest={editing=null},title={Text(item.title)},text={Column(Modifier.heightIn(max=380.dp).verticalScroll(rememberScrollState())) {
            if(folder==null && !item.id.startsWith("downloaded:")) {
                OutlinedTextField(editedTitle,{editedTitle=it},label={Text("Titre personnel")})
                OutlinedTextField(editedPoster,{editedPoster=it},label={Text("Affiche : URL")})
                TextButton(onClick={viewModel.saveCustomMediaMeta(item.id,editedTitle,editedPoster);editing=null}) {Text("Enregistrer")}
            }
            TextButton(onClick={editing=null;open(item)}) {Text("Ouvrir la fiche / modifier les métadonnées")}
            if(folder!=null) categories.forEach { name ->
                val members=viewModel.categoryItems(name)
                val included="folder:$folder" in members
                Row(verticalAlignment=Alignment.CenterVertically) {
                    Checkbox(included,{viewModel.setFolderCategory(name,folder,it)});Text(name)
                }
            }
        }},confirmButton={TextButton(onClick={editing=null}) {Text("Fermer")}})
    }
}
