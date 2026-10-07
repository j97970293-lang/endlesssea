package dev.endlesssea.app.ui.tracking

import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.endlesssea.app.tracking.TrackerId
import dev.endlesssea.app.ui.components.GlassCard

/**
 * §suivi (conversation 11) — « Comptes & suivi » : connexion des services
 * gratuits (AniList, MyAnimeList, Shikimori, TMDB) et réglages associés.
 *
 * Aucune inscription obligatoire : chaque service est indépendant, et tout
 * fonctionne sans compte (le suivi est simplement local).
 */
@Composable
fun TrackersScreen(
    onBack: () -> Unit,
    viewModel: TrackersViewModel = androidx.hilt.navigation.compose.hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 100.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onBack) { Text("‹ Retour") }
                Text(
                    "Comptes & suivi",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(start = 4.dp),
                )
            }
        }
        item {
            Text(
                "Connecte un ou plusieurs services pour synchroniser automatiquement les " +
                    "épisodes vus. Les identifiants restent sur ton appareil ; rien n'est " +
                    "envoyé tant qu'aucun compte n'est connecté.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        // ---------------------------------------------------------- réglages globaux
        item {
            GlassCard(
                modifier = Modifier.fillMaxWidth(),
                cornerRadius = 16.dp,
                contentPadding = PaddingValues(14.dp),
            ) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Marquer automatiquement les épisodes vus",
                                style = MaterialTheme.typography.bodyLarge)
                            Text(
                                "À 90 % de lecture, l'épisode est coché sur les services connectés.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(checked = state.autoMark, onCheckedChange = viewModel::setAutoMark)
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Compléter avec TMDB", style = MaterialTheme.typography.bodyLarge)
                            Text(
                                "Récupère affiches, bannières et bandes-annonces manquantes " +
                                    "(nécessite une clé TMDB).",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(checked = state.enrichTmdb, onCheckedChange = viewModel::setEnrichTmdb)
                    }
                }
            }
        }

        if (state.status.isNotBlank()) {
            item {
                Text(
                    state.status,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }

        items(TrackerId.entries.size) { index ->
            val id = TrackerId.entries[index]
            TrackerCard(
                id = id,
                connected = state.accounts[id]?.userName,
                busy = state.busy == id,
                draft = viewModel.draft(id),
                onToken = { v -> viewModel.updateDraft(id) { it.copy(token = v.trim()) } },
                onApiKey = { v -> viewModel.updateDraft(id) { it.copy(apiKey = v.trim()) } },
                onClientId = { v -> viewModel.updateDraft(id) { it.copy(clientId = v.trim()) } },
                onClientSecret = { v -> viewModel.updateDraft(id) { it.copy(clientSecret = v.trim()) } },
                onConnect = { viewModel.connect(id) },
                onOpenAuth = {
                    // Sans Client ID saisi, on ouvre la page où le créer : c'est
                    // presque toujours le vrai besoin de l'utilisateur à ce moment.
                    val url = viewModel.authorizationUrl(id).ifBlank { helpUrl(id) }
                    runCatching {
                        context.startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url)))
                    }
                },
                onExchange = { code -> viewModel.completeAuthorization(id, code) },
                onDisconnect = { viewModel.disconnect(id) },
            )
        }
    }
}

@Composable
private fun TrackerCard(
    id: TrackerId,
    connected: String?,
    busy: Boolean,
    draft: dev.endlesssea.app.tracking.TrackerCredentials,
    onToken: (String) -> Unit,
    onApiKey: (String) -> Unit,
    onClientId: (String) -> Unit,
    onClientSecret: (String) -> Unit,
    onConnect: () -> Unit,
    onOpenAuth: () -> Unit,
    onExchange: (String) -> Unit,
    onDisconnect: () -> Unit,
) {
    var code by remember(id) { mutableStateOf("") }

    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = 16.dp,
        contentPadding = PaddingValues(14.dp),
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(id.label, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.weight(1f))
                Text(
                    if (connected != null) "✓ $connected" else "non connecté",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = if (connected != null) FontWeight.Medium else FontWeight.Normal,
                    color = if (connected != null) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(
                helpFor(id),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(10.dp))

            when (id) {
                TrackerId.TMDB -> {
                    OutlinedTextField(
                        value = draft.apiKey,
                        onValueChange = onApiKey,
                        label = { Text("Clé d'API TMDB (v3 ou jeton v4)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = onConnect, enabled = !busy) {
                            Text(if (busy) "Vérification…" else "Enregistrer la clé")
                        }
                        TextButton(onClick = onOpenAuth) { Text("Obtenir une clé") }
                    }
                }
                TrackerId.ANILIST -> {
                    OutlinedTextField(
                        value = draft.token,
                        onValueChange = onToken,
                        label = { Text("Jeton d'accès AniList") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = draft.clientId,
                        onValueChange = onClientId,
                        label = { Text("Client ID de ton application (facultatif si jeton fourni)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = onConnect, enabled = !busy) {
                            Text(if (busy) "Vérification…" else "Connecter")
                        }
                        TextButton(onClick = onOpenAuth) { Text("Page d'autorisation") }
                    }
                }
                TrackerId.MAL, TrackerId.SHIKIMORI -> {
                    OutlinedTextField(
                        value = draft.clientId,
                        onValueChange = onClientId,
                        label = { Text("Client ID de ton application") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(8.dp))
                    if (id == TrackerId.SHIKIMORI) {
                        OutlinedTextField(
                            value = draft.clientSecret,
                            onValueChange = onClientSecret,
                            label = { Text("Client Secret") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(Modifier.height(8.dp))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = onOpenAuth, enabled = !busy) { Text("Ouvrir l'autorisation") }
                    }
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = code,
                        onValueChange = { code = it },
                        label = { Text("Code reçu après autorisation") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { onExchange(code) },
                            enabled = !busy && code.isNotBlank(),
                        ) { Text(if (busy) "Échange…" else "Échanger le code") }
                        TextButton(onClick = onConnect, enabled = !busy && draft.token.isNotBlank()) {
                            Text("Vérifier un jeton")
                        }
                    }
                }
            }

            if (connected != null) {
                Spacer(Modifier.height(6.dp))
                Text(
                    "Déconnecter",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.clickable { onDisconnect() },
                )
            }
        }
    }
}

/** Page où créer l'application/clé du service (quand aucun Client ID n'est saisi). */
private fun helpUrl(id: TrackerId): String = when (id) {
    TrackerId.ANILIST -> "https://anilist.co/settings/developer"
    TrackerId.MAL -> "https://myanimelist.net/apiconfig"
    TrackerId.SHIKIMORI -> "https://shikimori.one/oauth/applications"
    TrackerId.TMDB -> "https://www.themoviedb.org/settings/api"
}

private fun helpFor(id: TrackerId): String = when (id) {
    TrackerId.ANILIST ->
        "Crée une application sur anilist.co/settings/developer, colle son Client ID, " +
            "ouvre la page d'autorisation puis colle le jeton reçu dans l'URL."
    TrackerId.MAL ->
        "Déclare une application sur myanimelist.net/apiconfig avec l'URL de redirection " +
            "https://localhost/endlesssea, puis colle le code reçu."
    TrackerId.SHIKIMORI ->
        "Crée une application OAuth sur shikimori.one/oauth/applications (redirection " +
            "https://localhost/endlesssea), autorise, puis colle le code."
    TrackerId.TMDB ->
        "Clé gratuite sur themoviedb.org/settings/api : sert aux affiches et bandes-annonces, " +
            "et au rattachement des films."
}
