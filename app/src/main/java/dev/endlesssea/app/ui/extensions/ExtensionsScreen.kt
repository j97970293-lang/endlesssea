package dev.endlesssea.app.ui.extensions

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.unit.dp

/**
 * Extensions (doc 04) : dépôts (ajouter/activer/supprimer), extensions installées
 * (activer/désinstaller/permissions affichées), mises à jour, journal d'erreurs.
 */
@Composable
fun ExtensionsScreen(
    viewModel: ExtensionsViewModel = androidx.hilt.navigation.compose.hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    var addDialog by remember { mutableStateOf(false) }
    var repoUrl by remember { mutableStateOf("") }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(onClick = { addDialog = true }) {
                Icon(Icons.Filled.Add, "Ajouter un dépôt")
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { Text("Dépôts", style = MaterialTheme.typography.titleMedium) }
            if (state.repos.isEmpty()) {
                item {
                    Text(
                        "Aucun dépôt. Ajoutez une URL d'index JSON pour découvrir des extensions (ex. le dépôt officiel de démonstration). L'utilisateur choisit ses sources : l'application n'impose rien.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            state.repos.forEach { repo ->
                item {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(repo.name, style = MaterialTheme.typography.bodyLarge)
                            Text(repo.url, style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(checked = repo.enabled, onCheckedChange = { viewModel.setRepoEnabled(repo.url, it) })
                    }
                }
            }

            item { Text("Extensions installées", style = MaterialTheme.typography.titleMedium) }
            state.extensions.forEach { ext ->
                item {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("${ext.name} · v${ext.versionName}", style = MaterialTheme.typography.bodyLarge)
                            Text(
                                "Permissions : ${ext.permissions.ifEmpty { "aucune" }}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            if (ext.lastError != null) {
                                Text("Dernière erreur : ${ext.lastError}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error)
                            }
                        }
                        Switch(checked = ext.enabled, onCheckedChange = { viewModel.setExtensionEnabled(ext.pkg, it) })
                    }
                }
            }
        }
    }

    if (addDialog) {
        AlertDialog(
            onDismissRequest = { addDialog = false },
            title = { Text("Ajouter un dépôt") },
            text = {
                OutlinedTextField(
                    value = repoUrl,
                    onValueChange = { repoUrl = it },
                    placeholder = { Text("https://exemple.com/index.json") },
                    singleLine = true,
                )
            },
            confirmButton = {
                Button(onClick = { viewModel.addRepo(repoUrl.trim()); repoUrl = ""; addDialog = false }) {
                    Text("Ajouter")
                }
            },
            dismissButton = { TextButton(onClick = { addDialog = false }) { Text("Annuler") } },
        )
    }
}
