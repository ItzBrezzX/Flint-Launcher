package com.flintlauncher.android.ui.accounts

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.flintlauncher.android.ui.state.AccountsUiState
import com.flintlauncher.android.account.AccountType

@Composable
fun AccountsScreen(
    state: AccountsUiState,
    onUsernameChange: (String) -> Unit,
    onAddOffline: () -> Unit,
    onSelect: (String) -> Unit,
    onRemove: (String) -> Unit,
    onDismissMessage: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Accounts", style = MaterialTheme.typography.headlineMedium)
        Text("Manage the player identity used by Minecraft.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
            Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Add offline account", style = MaterialTheme.typography.titleMedium)
                OutlinedTextField(
                    value = state.username,
                    onValueChange = onUsernameChange,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Minecraft username") },
                    supportingText = { Text("3–16 characters: letters, numbers, underscore") },
                    singleLine = true,
                )
                Button(onClick = onAddOffline, enabled = !state.busy && state.username.isNotBlank()) {
                    Text(if (state.busy) "Adding…" else "Add account")
                }
            }
        }
        Text("Saved accounts (${state.accounts.size})", style = MaterialTheme.typography.titleMedium)
        if (state.accounts.isEmpty()) {
            Text("No accounts yet. Add an offline account to set a player identity.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(state.accounts, key = { it.id }) { account ->
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Column(Modifier.weight(1f)) {
                                    Text(account.username, style = MaterialTheme.typography.titleMedium)
                                    Text(if (account.type == AccountType.OFFLINE) "Offline · Java UUID" else "Microsoft", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                if (state.selectedId == account.id) Text("SELECTED", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelMedium)
                            }
                            Text("UUID: ${account.uuid}", style = MaterialTheme.typography.bodySmall)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(onClick = { onSelect(account.id) }, enabled = state.selectedId != account.id) { Text("Select") }
                                TextButton(onClick = { onRemove(account.id) }) { Text("Remove") }
                            }
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(2.dp))
        Text("Microsoft sign-in is not connected to this screen yet. Offline accounts do not authenticate with online-mode servers.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        state.message?.let { message ->
            AlertDialog(
                onDismissRequest = onDismissMessage,
                title = { Text("Account") },
                text = { Text(message) },
                confirmButton = { TextButton(onClick = onDismissMessage) { Text("OK") } },
            )
        }
    }
}
