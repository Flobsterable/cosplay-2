package ru.flobsterable.cosplay2.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** State supplied by the chat integration; this UI never invents bot replies. */
data class SupportChatState(
    val messages: List<SupportMessage> = emptyList(),
    val isAvailable: Boolean = false,
    val isSending: Boolean = false,
    val error: String? = null
)

data class SupportMessage(val id: String, val text: String, val author: SupportAuthor)
enum class SupportAuthor { User, Assistant }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SupportScreen(
    state: SupportChatState,
    onBack: () -> Unit,
    // Return only after successful delivery; throw on failure. Cancellation keeps the draft.
    onSendMessage: suspend (String) -> Unit
) {
    var draft by rememberSaveable { mutableStateOf("") }
    var isSubmitting by remember { mutableStateOf(false) }
    var sendError by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val canSend = state.isAvailable && !state.isSending && !isSubmitting && draft.isNotBlank()
    LaunchedEffect(state.messages.size, state.isSending) {
        val count = state.messages.size + if (state.isSending) 1 else 0
        if (count > 0) {
            snapshotFlow { listState.layoutInfo.totalItemsCount }.first { it == count }
            listState.animateScrollToItem(count - 1)
        }
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Поддержка") },
                navigationIcon = { TextButton(onClick = onBack) { Text("Назад") } }
            )
        },
        contentWindowInsets = WindowInsets.safeDrawing
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).imePadding()) {
            if (!state.isAvailable) {
                Text(
                    "Чат поддержки скоро будет доступен. Отправка сообщений пока недоступна.",
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            if (state.messages.isEmpty() && !state.isSending) {
                Column(
                    modifier = Modifier.weight(1f).fillMaxWidth().padding(24.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("Чат с поддержкой", style = MaterialTheme.typography.headlineSmall)
                    Text("Здесь будет история вашей переписки", modifier = Modifier.padding(top = 8.dp))
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(state.messages, key = { it.id }) { message ->
                        val isUser = message.author == SupportAuthor.User
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
                        ) {
                            Card(
                                modifier = Modifier.fillMaxWidth(0.88f),
                                colors = CardDefaults.cardColors(
                                    containerColor = if (isUser) MaterialTheme.colorScheme.primaryContainer
                                    else MaterialTheme.colorScheme.surfaceVariant
                                )
                            ) {
                                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(if (isUser) "Вы" else "Поддержка", style = MaterialTheme.typography.labelMedium)
                                    Text(message.text)
                                }
                            }
                        }
                    }
                    if (state.isSending) {
                        item {
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator()
                                Text("Ожидаем ответ поддержки…")
                            }
                        }
                    }
                }
            }
            (sendError ?: state.error)?.let { error ->
                Text(error, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(16.dp))
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                OutlinedTextField(
                    value = draft,
                    onValueChange = { if (it.length <= 4000) draft = it },
                    modifier = Modifier.weight(1f),
                    label = { Text("Сообщение") },
                    minLines = 1,
                    maxLines = 4,
                    enabled = !state.isSending
                )
                TextButton(
                    enabled = canSend,
                    onClick = {
                        if (!isSubmitting) {
                            val submittedDraft = draft
                            isSubmitting = true
                            sendError = null
                            scope.launch {
                                try {
                                    onSendMessage(submittedDraft.trim())
                                    if (draft == submittedDraft) draft = ""
                                } catch (cancelled: CancellationException) {
                                    throw cancelled
                                } catch (error: Exception) {
                                    sendError = error.message ?: "Не удалось отправить сообщение. Повторите попытку."
                                } finally {
                                    isSubmitting = false
                                }
                            }
                        }
                    }
                ) { Text("Отправить") }
            }
        }
    }
}
