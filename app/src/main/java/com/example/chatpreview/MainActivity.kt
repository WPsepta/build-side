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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.Button
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
        val list = _commands.value
        for (cmd in list) {
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
        // Hook for JS -> Kotlin communication
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { ChatPreviewApp() }
    }
}

@Composable
fun ChatPreviewApp() {
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("Chat", "Preview")
    Column(modifier = Modifier.fillMaxSize()) {
        TabRow(selectedTabIndex = selectedTab) {
            tabs.forEachIndexed { index, title ->
                Tab(selected = selectedTab == index, onClick = { selectedTab = index }, text = { Text(title) })
            }
        }
        when (selectedTab) {
            0 -> ChatTab()
            1 -> PreviewTab()
        }
    }
}

@Composable
fun ChatTab() {
    var input by remember { mutableStateOf("") }
    var sending by remember { mutableStateOf(false) }
    val messages = remember { mutableStateListOf<ChatMessage>() }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    LaunchedEffect(messages.size, sending) {
        if (messages.isNotEmpty() || sending) {
            listState.animateScrollToItem((messages.size + (if (sending) 1 else 0)).coerceAtLeast(0))
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).padding(8.dp),
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
                onValueChange = { input = it },
                modifier = Modifier.weight(1f),
                label = { Text("Pesan") },
                maxLines = 4
            )
            Spacer(Modifier.width(8.dp))
            Button(onClick = {
                val text = input.trim()
                if (text.isBlank() || sending) return@Button
                messages += ChatMessage(text, fromUser = true)
                input = ""
                sending = true
                val normalized = text.lowercase()
                if (normalized.contains("pohon")) GameController.enqueue("tree")
                if (normalized.contains("kanan")) GameController.enqueue("right")
                if (normalized.contains("kiri")) GameController.enqueue("left")
                if (normalized.contains("atas")) GameController.enqueue("up")
                if (normalized.contains("bawah")) GameController.enqueue("down")
                scope.launch {
                    delay(1200)
                    messages += ChatMessage("Oke, aku lagi buat game pixel art 2D top-down-nya ya!", fromUser = false)
                    sending = false
                }
            }, enabled = !sending) {
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

@Composable
fun PreviewTab() {
    val webViewState = remember { mutableStateOf<WebView?>(null) }
    var pageLoaded by remember { mutableStateOf(false) }

    fun processPending(wv: WebView) {
        GameController.processNew { cmd ->
            when (cmd.type) {
                "tree" -> wv.evaluateJavascript("drawRandomTree()", null)
                "right" -> wv.evaluateJavascript("moveRight()", null)
                "left" -> wv.evaluateJavascript("moveLeft()", null)
                "up" -> wv.evaluateJavascript("moveUp()", null)
                "down" -> wv.evaluateJavascript("moveDown()", null)
            }
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

    AndroidView(factory = { context ->
        WebView(context).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            addJavascriptInterface(WebAppInterface(context), "Android")
            webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    pageLoaded = true
                    view?.let { processPending(it) }
                }
            }
            loadUrl("file:///android_asset/www/index.html")
            webViewState.value = this
        }
    }, modifier = Modifier.fillMaxSize())
}
