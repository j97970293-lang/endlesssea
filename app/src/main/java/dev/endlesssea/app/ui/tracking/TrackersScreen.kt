package dev.endlesssea.app.ui.tracking

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.endlesssea.app.ui.components.GlassCard

/**
 * §suivi (conversation 11) — « Comptes & suivi » : brancher les services
 * gratuits, régler le marquage automatique, voir les fiches suivies.
 *
 * Aucun compte n'est obligatoire : cet écran est purement additif. Les jetons
 * sont stockés dans la base locale de l'application et ne partent que vers le
 * service concerné.
 */
@Composable
fun TrackersScreen(
    onBack: () -> Unit,
    viewModel: TrackersViewModel = androidx.hilt.navigation.compose.hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
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
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Marquer automatiquement les épisodes vus",
                            style = MaterialTheme.typography.titleSmall)
                        Text(
                            "À 90 % de lecture, l'épisode est coché sur le service connecté.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(checked = state.autoMark, onCheckedChange = { viewModel.setAutoMark(it) })
                }
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Compléter les fiches avec TMDB", style = MaterialTheme.typography.titleSmall)
                        Text(
                            "Affiche, bannière et bande-annonce quand l'extension n'en fournit pas.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(checked = state.enrichTmdb, onCheckedChange = { viewModel.setEnrichTmdb(it) })
                }
            }
        }
        state.notice?.let { notice ->
            item {
                Text(
                    notice,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.clickable { viewModel.clearNotice() },
                )
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = { viewModel.syncNow() }) { Text("Synchroniser maintenant") }
                Text(
                    "${state.links.size} fiche(s) suivie(s) · ${state.links.count { it.pendingSync }} en attente",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 12.dp),
                )
            }
        }

        // ---- un bloc par service
        viewModel.services.forEach { id ->
            val account = viewModel.account(id)
            item {
                GlassCard(
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(14.dp),
                ) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(viewModel.label(id), style = MaterialTheme.typography.titleMedium)
                                Text(
                                    if (account == null) "Non connecté"
                                    else "Connecté · ${account.userName}" +
                                        (if (!account.enabled) " (désactivé)" else ""),
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = if (account != null) FontWeight.Medium else FontWeight.Normal,
                                    color = if (account != null) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            if (account != null) {
                                Switch(
                                    checked = account.enabled,
                                    onCheckedChange = { viewModel.setEnabled(id, it) },
                                )
                            }
                        }
                        Text(
                            viewModel.hint(id),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                        account?.lastError?.let { err ->
                            Text(
                                "Dernière erreur : $err",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                        Spacer(Modifier.height(6.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(onClick = {
                                if (state.editing == id) viewModel.closeForm() else viewModel.openForm(id)
                            }) {
                                Text(if (account == null) "Connecter" else "Remplacer les identifiants")
                            }
                            if (account != null) {
                                TextButton(onClick = { viewModel.disconnect(id) }) { Text("Déconnecter") }
                            }
                        }
                        if (state.editing == id) {
                            ConnectForm(
                                isTmdb = id == "TMDB",
                                busy = state.busy,
                                onSubmit = { token, apiKey, clientId, refresh ->
                                    viewModel.connect(id, token, apiKey, clientId, refresh)
                                },
                            )
                        }
                        val followed = viewModel.linksFor(id)
                        if (followed.isNotEmpty()) {
                            Spacer(Modifier.height(6.dp))
                            Text("Fiches suivies", style = MaterialTheme.typography.titleSmall)
                            followed.take(12).forEach { link ->
                                Row(
                                    Modifier.fillMaxWidth().padding(vertical = 3.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Column(Modifier.weight(1f)) {
                                        Text(
                                            link.title.ifBlank { link.mediaId },
                                            style = MaterialTheme.typography.bodyMedium,
                                            maxLines = 1,
                                        )
                                        Text(
                                            "${link.progress}" +
                                                (link.totalEpisodes.takeIf { it > 0 }?.let { " / $it" } ?: "") +
                                                " · " + link.status.lowercase() +
                                                (if (link.pendingSync) " · à synchroniser" else ""),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                    Text(
                                        "Retirer",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.clickable { viewModel.unlink(link.mediaId) },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Formulaire d'identifiants : un champ par information utile au service. */
@Composable
private fun ConnectForm(
    isTmdb: Boolean,
    busy: Boolean,
    onSubmit: (token: String, apiKey: String, clientId: String, refresh: String) -> Unit,
) {
    var token by remember { mutableStateOf("") }
    var apiKey by remember { mutableStateOf("") }
    var clientId by remember { mutableStateOf("") }
    var refresh by remember { mutableStateOf("") }

    Column(Modifier.padding(top = 6.dp)) {
        if (isTmdb) {
            OutlinedTextField(
                value = apiKey,
                onValueChange = { apiKey = it },
                label = { Text("Clé d'API TMDB (v3)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        } else {
            OutlinedTextField(
                value = token,
                onValueChange = { token = it },
                label = { Text("Jeton d'accès (access token)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            if (clientId.isNotEmpty() || true) {
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(
                    value = clientId,
                    onValueChange = { clientId = it },
                    label = { Text("Client ID (facultatif — nécessaire pour MAL)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Spacer(Modifier.height(6.dp))
            OutlinedTextField(
                value = refresh,
                onValueChange = { refresh = it },
                label = { Text("Refresh token (facultatif — renouvellement auto)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Spacer(Modifier.height(8.dp))
        Button(
            onClick = { onSubmit(token.trim(), apiKey.trim(), clientId.trim(), refresh.trim()) },
            enabled = !busy && (if (isTmdb) apiKey.isNotBlank() else token.isNotBlank()),
        ) { Text(if (busy) "Vérification…" else "Enregistrer et vérifier") }
    }
}
