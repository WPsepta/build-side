package com.example.chatpreview

import android.content.Context
import android.os.Bundle
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ChatMessage(val text: String, val fromUser: Boolean)

object GameController {
    data class Command(val id: Long, val type: String)
    private val _commands = MutableStateFlow<List<Command>>(emptyList())
    val commands: StateFlow<List<Command>> = _commands.asStateFlow()
    private var counter = 0L
    private var lastProcessedId = 0L
    fun enqueue(type: String) {
        _commands.value = _commands.value + Command(++counter, type)
    }
    fun processNew(block: (Command) -> Unit) {
        for (cmd in _commands.value) {
            if (cmd.id > lastProcessedId) {
                block(cmd)
                lastProcessedId = cmd.id
            }
        }
    }
}

class WebAppInterface(private val context: Context) {
    @JavascriptInterface
    fun postMessage(message: String) {
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { ChatPreviewApp() }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatPreviewApp() {
    val messages = remember { mutableStateListOf<ChatMessage>() }
    var input by remember { mutableStateOf("") }
    var sending by remember { mutableStateOf(false) }
    var showChat by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val webViewState = remember { mutableStateOf<android.webkit.WebView?>(null) }
    var pageLoaded by remember { mutableStateOf(false) }

    fun processPending(wv: android.webkit.WebView) {
        GameController.processNew { cmd ->
            val jsType = when (cmd.type) {
                "water" -> "water"
                "grass" -> "grass"
                "dirt" -> "dirt"
                "stone" -> "stone"
                else -> null
            }
            jsType?.let { wv.evaluateJavascript("addTileRandom('$it')", null) }
        }
    }

    LaunchedEffect(pageLoaded) {
        if (pageLoaded) webViewState.value?.let { processPending(it) }
    }
    LaunchedEffect(Unit) {
        GameController.commands.collect {
            if (pageLoaded) webViewState.value?.let { processPending(it) }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(factory = { context ->
            android.webkit.WebView(context).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                addJavascriptInterface(WebAppInterface(context), "Android")
                webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: android.webkit.WebView?, url: String?) {
                        super.onPageFinished(view, url)
                        pageLoaded = true
                        view?.let { processPending(it) }
                    }
                }
                loadUrl("file:///android_asset/www/index.html")
                webViewState.value = this
            }
        }, modifier = Modifier.fillMaxSize())

        FloatingActionButton(
            onClick = { showChat = true },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
        ) {
            Text("💬")
        }

        if (showChat) {
            ModalBottomSheet(
                onDismissRequest = { showChat = false },
                sheetState = sheetState
            ) {
                ChatSheetContent(
                    messages = messages,
                    input = input,
                    onInputChange = { input = it },
                    sending = sending,
                    onSend = {
                        val text = input.trim()
                        if (text.isBlank() || sending) return@ChatSheetContent
                        messages += ChatMessage(text, true)
                        input = ""
                        sending = true
                        val normalized = text.lowercase()
                        val reply = when {
                            normalized.contains("air") -> {
                                GameController.enqueue("water")
                                "Oke, aku tambahkan air di map."
                            }
                            normalized.contains("rumput") -> {
                                GameController.enqueue("grass")
                                "Oke, aku tambahkan rumput di map."
                            }
                            normalized.contains("tanah") -> {
                                GameController.enqueue("dirt")
                                "Oke, aku tambahkan tanah di map."
                            }
                            normalized.contains("batu") -> {
                                GameController.enqueue("stone")
                                "Oke, aku tambahkan batu di map."
                            }
                            else -> "Siap, aku update map-nya ya!"
                        }
                        scope.launch {
                            delay(800)
                            messages += ChatMessage(reply, false)
                            sending = false
                        }
                    }
                )
            }
        }
    }
}

@Composable
fun ChatSheetContent(
    messages: List<ChatMessage>,
    input: String,
    onInputChange: (String) -> Unit,
    sending: Boolean,
    onSend: () -> Unit
) {
    val listState = rememberLazyListState()
    LaunchedEffect(messages.size, sending) {
        if (messages.isNotEmpty() || sending) {
            listState.animateScrollToItem((messages.size + (if (sending) 1 else 0)).coerceAtLeast(0))
        }
    }
    Column(modifier = Modifier.fillMaxWidth().height(420.dp)) {
        Text("Chat", modifier = Modifier.padding(12.dp), style = MaterialTheme.typography.titleMedium)
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(messages) { msg -> MessageBubble(msg) }
            if (sending) {
                item {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
                        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                            Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(modifier = Modifier.padding(end = 8.dp), strokeWidth = 2.dp)
                                Text("AI sedang mengetik...")
                            }
                        }
                    }
                }
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = input,
                onValueChange = onInputChange,
                modifier = Modifier.weight(1f),
                label = { Text("Pesan") },
                maxLines = 4
            )
            Spacer(Modifier.width(8.dp))
            TextButton(onClick = onSend, enabled = !sending) {
                Text(if (sending) "..." else "Kirim")
            }
        }
    }
}

@Composable
fun MessageBubble(msg: ChatMessage) {
    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = if (msg.fromUser) Alignment.CenterEnd else Alignment.CenterStart) {
        Card(
            colors = CardDefaults.cardColors(
                containerColor = if (msg.fromUser) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.secondaryContainer
            ),
            modifier = Modifier.widthIn(max = 280.dp)
        ) {
            Text(
                text = msg.text,
                modifier = Modifier.padding(12.dp),
                textAlign = if (msg.fromUser) TextAlign.End else TextAlign.Start
            )
        }
    }
}
