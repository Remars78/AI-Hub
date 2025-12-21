package com.example.aihub

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.aihub.data.Message
import com.example.aihub.data.RetrofitClient
import com.example.aihub.data.ChatRequest
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                MainScreen()
            }
        }
    }
}

@Composable
fun MainScreen() {
    var selectedTab by remember { mutableIntStateOf(0) }

    Scaffold(
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    icon = { Icon(Icons.Default.Chat, "LLM") },
                    label = { Text("Chat") }
                )
                NavigationBarItem(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    icon = { Icon(Icons.Default.Image, "IMG") },
                    label = { Text("Image") }
                )
            }
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            when (selectedTab) {
                0 -> LLMScreen()
                1 -> IMGScreen()
            }
        }
    }
}

// --- LLM TAB ---
@Composable
fun LLMScreen() {
    // Временное хранение сообщений в памяти
    val messages = remember { mutableStateListOf<Message>() }
    var prompt by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    var isLoading by remember { mutableStateOf(false) }
    
    // Твой ключ Mistral (лучше хранить в local.properties и BuildConfig, но для теста хардкод)
    val apiKey = "Bearer ТВОЙ_КЛЮЧ_MISTRAL" 

    Column(Modifier.fillMaxSize()) {
        // Список сообщений
        LazyColumn(Modifier.weight(1f).padding(8.dp)) {
            items(messages) { msg ->
                Text(
                    text = "${msg.role}: ${msg.content}",
                    modifier = Modifier.padding(4.dp),
                    color = if (msg.role == "user") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                )
            }
        }

        if (isLoading) LinearProgressIndicator(Modifier.fillMaxWidth())

        // Поле ввода
        Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            TextField(
                value = prompt,
                onValueChange = { prompt = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("Ask Mistral...") }
            )
            IconButton(onClick = {
                if (prompt.isNotBlank()) {
                    val userMsg = Message("user", prompt)
                    messages.add(userMsg)
                    val currentPrompt = prompt
                    prompt = ""
                    isLoading = true

                    scope.launch {
                        try {
                            // Формируем историю для контекста
                            val request = ChatRequest(
                                model = "mistral-tiny", // Дешевая и быстрая модель
                                messages = messages.toList()
                            )
                            val response = RetrofitClient.api.chat(apiKey, request)
                            val botMsg = response.choices.first().message
                            messages.add(botMsg)
                        } catch (e: Exception) {
                            messages.add(Message("system", "Error: ${e.message}"))
                        } finally {
                            isLoading = false
                        }
                    }
                }
            }) {
                Icon(Icons.Default.Send, "Send")
            }
        }
    }
}

// --- IMG TAB ---
@Composable
fun IMGScreen() {
    var prompt by remember { mutableStateOf("") }
    var imageUrl by remember { mutableStateOf<String?>(null) }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        TextField(
            value = prompt,
            onValueChange = { prompt = it },
            label = { Text("Что нарисовать?") },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(8.dp))
        Button(
            onClick = {
                // Pollinations лайфхак: просто подставляем промпт в URL
                val encoded = java.net.URLEncoder.encode(prompt, "UTF-8")
                imageUrl = "https://image.pollinations.ai/prompt/$encoded"
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Generate Image")
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (imageUrl != null) {
            // Coil сам загрузит картинку по ссылке
            AsyncImage(
                model = imageUrl,
                contentDescription = "Generated Image",
                modifier = Modifier.fillMaxWidth().height(300.dp)
            )
        }
    }
}
