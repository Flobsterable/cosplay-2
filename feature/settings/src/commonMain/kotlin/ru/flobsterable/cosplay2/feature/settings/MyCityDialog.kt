package ru.flobsterable.cosplay2.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun MyCityDialog(
    city: String,
    suggestions: List<String>,
    isSaving: Boolean,
    error: String?,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var input by rememberSaveable(city) { mutableStateOf(city) }
    AlertDialog(
        onDismissRequest = { if (!isSaving) onDismiss() },
        title = { Text("Мой город") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Укажите город, чтобы быстро находить фестивали рядом.")
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    label = { Text("Город") },
                    placeholder = { Text("Например, Омск") },
                    singleLine = true,
                    enabled = !isSaving,
                    modifier = Modifier.fillMaxWidth()
                )
                Column(Modifier.heightIn(max = 200.dp).verticalScroll(rememberScrollState())) {
                    suggestions.filter { it.contains(input.trim(), ignoreCase = true) }
                        .distinctBy { it.lowercase() }.sorted().take(5).forEach { suggestion ->
                            TextButton(onClick = { input = suggestion }, enabled = !isSaving) {
                                Text(suggestion)
                            }
                        }
                }
                if (city.isNotBlank()) {
                    TextButton(onClick = { onSave("") }, enabled = !isSaving) { Text("Сбросить город") }
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(input) }, enabled = input.isNotBlank() && !isSaving) {
                Text(if (isSaving) "Сохранение…" else "Сохранить")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isSaving) { Text("Отмена") }
        }
    )
}
