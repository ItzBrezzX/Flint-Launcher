package com.flintlauncher.android.ui.accounts

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.flintlauncher.android.ui.state.AccountsActions
import com.flintlauncher.android.ui.state.AccountsUiState

private val Bg = Color(0xFF07110B)
private val Panel = Color(0xFF101B14)
private val Accent = Color(0xFF35D12C)
private val MainText = Color(0xFFE8F0E8)
private val Muted = Color(0xFF8A9A8E)
private val Border = Color(0xFF1A2A1E)
private val PanelShape = RoundedCornerShape(12.dp)

@Composable
fun AccountsScreen(state: AccountsUiState, actions: AccountsActions) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().background(Bg),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(22.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Text("Accounts", color = MainText, fontSize = 25.sp, fontWeight = FontWeight.Bold)
        }
        item {
            Text("Choose the identity Flint uses when starting Minecraft.", color = Muted, fontSize = 13.sp)
        }
        item {
            Column(
                Modifier.fillMaxWidth().background(Panel, PanelShape)
                    .border(1.dp, Border, PanelShape).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text("Add offline account", color = MainText, fontWeight = FontWeight.SemiBold)
                OutlinedTextField(
                    value = state.usernameInput,
                    onValueChange = actions.onUsernameChange,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("Minecraft username") },
                    supportingText = { Text("3–16 letters, numbers or underscore", color = Muted) },
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None),
                )
                Button(
                    onClick = actions.onAddOffline,
                    colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = Bg),
                    enabled = state.usernameInput.isNotBlank(),
                ) { Text("Add account", fontWeight = FontWeight.Bold) }
            }
        }
        item { Text("Saved accounts", color = MainText, fontWeight = FontWeight.SemiBold) }
        if (state.accounts.isEmpty()) {
            item { Text("No accounts added yet.", color = Muted, fontSize = 13.sp) }
        } else {
            items(state.accounts, key = { it.id }) { account ->
                Row(
                    Modifier.fillMaxWidth().background(Panel, PanelShape)
                        .border(1.dp, Border, PanelShape).padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(account.username, color = MainText, fontWeight = FontWeight.Medium)
                        Text(account.typeLabel, color = Muted, fontSize = 11.sp)
                    }
                    if (account.selected) {
                        Text("ACTIVE", color = Accent, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    } else {
                        TextButton(onClick = { actions.onSelect(account.id) }) { Text("Select") }
                    }
                    Spacer(Modifier.width(4.dp))
                    TextButton(onClick = { actions.onRemove(account.id) }) {
                        Text("Remove", color = Color(0xFFFF8A80))
                    }
                }
            }
        }
        item {
            Column(
                Modifier.fillMaxWidth().background(Panel, PanelShape)
                    .border(1.dp, Border, PanelShape).padding(16.dp),
            ) {
                Text("Microsoft account", color = MainText, fontWeight = FontWeight.SemiBold)
                Text(
                    "Microsoft OAuth is not connected to the account screen yet. No fake sign-in button is shown.",
                    color = Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp),
                )
            }
        }
        state.message?.let { message ->
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(message, color = Color(0xFFFFC27A), modifier = Modifier.weight(1f), fontSize = 12.sp)
                    TextButton(onClick = actions.onDismissMessage) { Text("Dismiss") }
                }
            }
        }
    }
}
