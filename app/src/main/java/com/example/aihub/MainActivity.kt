package com.example.aihub

import android.content.Context
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST
import kotlinx.coroutines.launch
import java.net.URLEncoder

// --- 1. DATA MODELS ---
data class ChatRequest(val model: String, val messages: List<Message>, val temperature: Double = 0.7)
data class Message(val role: String, val content: String)
data class ChatResponse(val choices: List<Choice>)
data class Choice(val message: Message)

// --- 2. API INTERFACE ---
interface MistralApi {
    @POST("v1/chat/completions")
    suspend fun chat(
        @Header("Authorization") auth: String,
        @Body request: ChatRequest
    ): ChatResponse
}

// --- 3. RETROFIT CLIENT ---
object RetrofitClient {
    private const val BASE_URL = "https://api.mistral.ai/"
    
    val api: MistralApi by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(MistralApi::class.java)
    }
}

// --- 4. PREFERENCES HELPER (Сохранение ключа) ---
class AppPreferences(context: Context) {
    private val prefs = context.getSharedPreferences("ai_hub_prefs", Context.MODE_PRIVATE)

    fun saveApiKey(key: String) {
        prefs.edit().putString("mistral_api_key", key.trim()).apply()
    }

    fun getApiKey(): String {
        return prefs.getString("mistral_api_key", "") ?: ""
    }
}

// --- 5. MAIN ACTIVITY ---
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
    // 0 = Chat, 1 = Img, 2 = Settings
    var selectedTab by remember { mutableIntStateOf(0) }

    Scaffold(
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    icon = { Text("Chat") },
                    label = { Text("LLM") }
                )
                NavigationBarItem(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    icon = { Text("Img") },
                    label = { Text("Gen") }
                )
                NavigationBarItem(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    icon = { Text("Set") },
                    label = { Text("Settings") }
                )
            }
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            when (selectedTab) {
                0 -> LLMScreen()
                1 -> IMGScreen()
                2 -> SettingsScreen()
            }
        }
    }
}

@Composable
fun SettingsScreen() {
    val context = LocalContext.current
    val prefs = remember { AppPreferences(context) }
    var apiKey by remember { mutableStateOf(prefs.getApiKey()) }
    
    Column(
        Modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("Mistral API Settings", style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(24.dp))
        
        OutlinedTextField(
            value = apiKey,
            onValueChange = { apiKey = it },
            label = { Text("Enter API Key") },
            modifier = Modifier.fillMaxWidth(),
            visualTransformation = PasswordVisualTransformation() // Скрывает символы точками
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Button(onClick = {
            prefs.saveApiKey(apiKey)
            Toast.makeText(context, "Key Saved!", Toast.LENGTH_SHORT).show()
        }) {
            Text("Save API Key")
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        Text("Get key at console.mistral.ai", style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
fun LLMScreen() {
    val context = LocalContext.current
    val prefs = remember { AppPreferences(context) }
    
    val messages = remember { mutableStateListOf<Message>() }
    var prompt by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    var isLoading by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
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

        Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            TextField(
                value = prompt,
                onValueChange = { prompt = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("Ask AI...") }
            )
            Button(onClick = {
                val currentKey = prefs.getApiKey()
                if (currentKey.isBlank()) {
                    Toast.makeText(context, "Please set API Key in Settings first!", Toast.LENGTH_LONG).show()
                    return@Button
                }

                if (prompt.isNotBlank()) {
                    val userText = prompt
                    messages.add(Message("user", userText))
                    prompt = ""
                    isLoading = true

                    scope.launch {
                        try {
                            val request = ChatRequest(
                                model = "mistral-tiny",
                                messages = messages.toList()
                            )
                            // Передаем сохраненный ключ
                            val response = RetrofitClient.api.chat("Bearer $currentKey", request)
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
                Text("Send")
            }
        }
    }
}

@Composable
fun IMGScreen() {
    var prompt by remember { mutableStateOf("") }
    var imageUrl by remember { mutableStateOf<String?>(null) }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        TextField(
            value = prompt,
            onValueChange = { prompt = it },
            label = { Text("Describe image") },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(8.dp))
        Button(
            onClick = {
                // Pollinations не требует ключа
                val encoded = URLEncoder.encode(prompt, "UTF-8")
                imageUrl = "https://image.pollinations.ai/prompt/$encoded"
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Generate")
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (imageUrl != null) {
            AsyncImage(
                model = imageUrl,
                contentDescription = null,
                modifier = Modifier.fillMaxWidth().height(300.dp)
            )
        }
    }
}
