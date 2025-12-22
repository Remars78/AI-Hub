package com.example.aihub

import android.content.Context
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Star // ЗАМЕНИЛИ AutoAwesome на Star
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.launch
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST
import java.net.URLEncoder
import java.util.UUID

// --- 1. DATA MODELS ---
data class ChatRequest(val model: String, val messages: List<Message>, val temperature: Double = 0.7)
data class Message(val role: String, val content: String)
data class ChatResponse(val choices: List<Choice>)
data class Choice(val message: Message)

// Модель сессии чата для истории
data class ChatSession(
    val id: String = UUID.randomUUID().toString(),
    var title: String = "New Chat",
    val messages: MutableList<Message> = mutableListOf(),
    var timestamp: Long = System.currentTimeMillis()
)

// --- 2. API INTERFACE ---
interface MistralApi {
    @POST("v1/chat/completions")
    suspend fun chat(
        @Header("Authorization") auth: String,
        @Body request: ChatRequest
    ): ChatResponse
}

// --- 3. SINGLETONS ---
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

// --- 4. DATA MANAGER (SharedPreferences + JSON) ---
class DataManager(context: Context) {
    private val prefs = context.getSharedPreferences("ai_hub_data", Context.MODE_PRIVATE)
    private val gson = Gson()

    // API Key
    fun saveApiKey(key: String) = prefs.edit().putString("mistral_api_key", key.trim()).apply()
    fun getApiKey(): String = prefs.getString("mistral_api_key", "") ?: ""

    // Chats History
    fun saveChats(chats: List<ChatSession>) {
        val json = gson.toJson(chats)
        prefs.edit().putString("chat_history", json).apply()
    }

    fun getChats(): MutableList<ChatSession> {
        val json = prefs.getString("chat_history", null) ?: return mutableListOf()
        val type = object : TypeToken<MutableList<ChatSession>>() {}.type
        return try {
            gson.fromJson(json, type)
        } catch (e: Exception) {
            mutableListOf()
        }
    }
}

// --- 5. MAIN ACTIVITY ---
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) { // Dark Theme по умолчанию
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
                NavigationBarItem(selected = selectedTab == 0, onClick = { selectedTab = 0 }, icon = { Text("Chat") }, label = { Text("LLM") })
                NavigationBarItem(selected = selectedTab == 1, onClick = { selectedTab = 1 }, icon = { Text("Img") }, label = { Text("Gen") })
                NavigationBarItem(selected = selectedTab == 2, onClick = { selectedTab = 2 }, icon = { Icon(Icons.Default.Settings, "Set") }, label = { Text("Set") })
            }
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding)) {
            when (selectedTab) {
                0 -> LLMTab()
                1 -> IMGTab()
                2 -> SettingsTab()
            }
        }
    }
}

// --- TAB 1: LLM CHAT SYSTEM ---
@Composable
fun LLMTab() {
    val context = LocalContext.current
    val dataManager = remember { DataManager(context) }
    
    // Состояние: список всех чатов или конкретный открытый чат
    var allChats by remember { mutableStateOf(dataManager.getChats()) }
    var currentChat by remember { mutableStateOf<ChatSession?>(null) }
    
    // Если чат не выбран, показываем список
    if (currentChat == null) {
        Scaffold(
            floatingActionButton = {
                FloatingActionButton(onClick = {
                    // Создаем новый чат
                    val newChat = ChatSession()
                    allChats.add(0, newChat) // Добавляем в начало
                    currentChat = newChat
                    dataManager.saveChats(allChats)
                }) {
                    Icon(Icons.Default.Add, "New Chat")
                }
            }
        ) { p ->
            Column(Modifier.padding(p)) {
                Text("Your Chats", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(16.dp))
                LazyColumn {
                    items(allChats) { chat ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 4.dp)
                                .clickable { currentChat = chat },
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            Row(
                                Modifier.padding(16.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(chat.title, style = MaterialTheme.typography.titleMedium, maxLines = 1)
                                    Text(
                                        if (chat.messages.isNotEmpty()) chat.messages.last().content else "Empty chat",
                                        style = MaterialTheme.typography.bodySmall,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                IconButton(onClick = {
                                    allChats = allChats.filter { it.id != chat.id }.toMutableList()
                                    dataManager.saveChats(allChats)
                                }) {
                                    Icon(Icons.Default.Delete, "Delete", tint = Color.Gray)
                                }
                            }
                        }
                    }
                }
            }
        }
    } else {
        // Если чат выбран, показываем переписку
        ChatScreen(
            chat = currentChat!!,
            onBack = {
                // При выходе обновляем список и сохраняем
                dataManager.saveChats(allChats) 
                currentChat = null
            },
            onUpdate = { 
                // Сохраняем после каждого сообщения
                dataManager.saveChats(allChats) 
            }
        )
    }
}

@Composable
fun ChatScreen(chat: ChatSession, onBack: () -> Unit, onUpdate: () -> Unit) {
    val context = LocalContext.current
    val dataManager = remember { DataManager(context) }
    val scope = rememberCoroutineScope()
    var prompt by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    
    // Используем rememberUpdatedState для списка сообщений, чтобы UI обновлялся
    val messages = remember { mutableStateListOf<Message>().apply { addAll(chat.messages) } }

    Column(Modifier.fillMaxSize()) {
        // Top Bar
        Row(
            Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant).padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back") }
            Text(chat.title, style = MaterialTheme.typography.titleMedium, maxLines = 1)
        }

        // Messages
        LazyColumn(Modifier.weight(1f).padding(8.dp), reverseLayout = true) {
            items(messages.reversed()) { msg ->
                ChatBubble(msg)
            }
        }

        if (isLoading) LinearProgressIndicator(Modifier.fillMaxWidth())

        // Input
        Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            TextField(
                value = prompt,
                onValueChange = { prompt = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("Message Mistral...") },
                colors = TextFieldDefaults.colors(focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent),
                shape = RoundedCornerShape(24.dp)
            )
            IconButton(onClick = {
                val apiKey = dataManager.getApiKey()
                if (apiKey.isBlank()) {
                    Toast.makeText(context, "Set API Key in Settings!", Toast.LENGTH_SHORT).show()
                    return@IconButton
                }
                if (prompt.isNotBlank()) {
                    val userMsg = Message("user", prompt)
                    messages.add(userMsg)
                    chat.messages.add(userMsg)
                    
                    // Обновляем заголовок чата по первому сообщению, если он дефолтный
                    if (chat.messages.size == 1) chat.title = prompt.take(30)
                    
                    val currentPrompt = prompt
                    prompt = ""
                    isLoading = true
                    onUpdate()

                    scope.launch {
                        try {
                            val request = ChatRequest(
                                model = "mistral-small", // Используем модель поумнее
                                messages = chat.messages.toList()
                            )
                            val response = RetrofitClient.api.chat("Bearer $apiKey", request)
                            val botMsg = response.choices.first().message
                            
                            messages.add(botMsg)
                            chat.messages.add(botMsg)
                            onUpdate()
                        } catch (e: Exception) {
                            messages.add(Message("system", "Error: ${e.message}"))
                        } finally {
                            isLoading = false
                        }
                    }
                }
            }) {
                Icon(Icons.Default.Send, "Send", tint = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@Composable
fun ChatBubble(message: Message) {
    val isUser = message.role == "user"
    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = if (isUser) Alignment.CenterEnd else Alignment.CenterStart
    ) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = if (isUser) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.secondaryContainer,
            modifier = Modifier.padding(4.dp).widthIn(max = 300.dp)
        ) {
            Text(
                text = message.content,
                modifier = Modifier.padding(12.dp),
                fontSize = 16.sp
            )
        }
    }
}

// --- TAB 2: IMAGE GEN (With Prompt Enhancer) ---
@Composable
fun IMGTab() {
    val context = LocalContext.current
    val dataManager = remember { DataManager(context) }
    var prompt by remember { mutableStateOf("") }
    var imageUrl by remember { mutableStateOf<String?>(null) }
    var isEnhancing by remember { mutableStateOf(false) }
    var useEnhancer by remember { mutableStateOf(true) } // Галочка
    val scope = rememberCoroutineScope()

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("AI Image Generator", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(16.dp))

        OutlinedTextField(
            value = prompt,
            onValueChange = { prompt = it },
            label = { Text("What do you want to see?") },
            modifier = Modifier.fillMaxWidth().height(100.dp),
            maxLines = 5
        )
        
        // Чекбокс улучшения
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = useEnhancer, onCheckedChange = { useEnhancer = it })
            Text("Enhance prompt with Mistral")
            // ИСПОЛЬЗУЕМ ЗВЕЗДОЧКУ ВМЕСТО AutoAwesome
            if (useEnhancer) Icon(Icons.Default.Star, null, tint = Color.Yellow, modifier = Modifier.padding(start = 4.dp))
        }

        Spacer(Modifier.height(8.dp))
        
        Button(
            onClick = {
                val apiKey = dataManager.getApiKey()
                if (useEnhancer && apiKey.isBlank()) {
                    Toast.makeText(context, "API Key needed for enhancement!", Toast.LENGTH_SHORT).show()
                    return@Button
                }

                scope.launch {
                    var finalPrompt = prompt
                    isEnhancing = true
                    
                    if (useEnhancer && prompt.isNotBlank()) {
                        try {
                            // 1. Улучшаем промпт через Mistral
                            val enhanceRequest = ChatRequest(
                                model = "mistral-tiny",
                                messages = listOf(
                                    Message("system", "You are an expert Stable Diffusion prompt engineer. Rewrite the user's request into a detailed, descriptive, photorealistic English prompt. Just the prompt, no intro."),
                                    Message("user", prompt)
                                )
                            )
                            val response = RetrofitClient.api.chat("Bearer $apiKey", enhanceRequest)
                            finalPrompt = response.choices.first().message.content
                        } catch (e: Exception) {
                            Toast.makeText(context, "Enhance failed, using raw prompt", Toast.LENGTH_SHORT).show()
                        }
                    }

                    // 2. Генерируем ссылку
                    val encoded = URLEncoder.encode(finalPrompt, "UTF-8")
                    // Добавляем seed для разнообразия
                    val seed = (0..10000).random()
                    imageUrl = "https://image.pollinations.ai/prompt/$encoded?seed=$seed&width=1024&height=1024&nologo=true"
                    isEnhancing = false
                }
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = !isEnhancing
        ) {
            if (isEnhancing) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Color.White)
                Spacer(Modifier.width(8.dp))
                Text("Optimizing Prompt...")
            } else {
                Text("Generate Art")
            }
        }

        Spacer(Modifier.height(16.dp))

        if (imageUrl != null) {
            Card(Modifier.fillMaxWidth().weight(1f)) {
                AsyncImage(
                    model = imageUrl,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize()
                )
            }
        } else {
            Spacer(Modifier.weight(1f))
        }
    }
}

// --- TAB 3: SETTINGS ---
@Composable
fun SettingsTab() {
    val context = LocalContext.current
    val dataManager = remember { DataManager(context) }
    var apiKey by remember { mutableStateOf(dataManager.getApiKey()) }
    
    Column(
        Modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(Icons.Default.Settings, null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(16.dp))
        Text("Configuration", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(24.dp))
        
        OutlinedTextField(
            value = apiKey,
            onValueChange = { apiKey = it },
            label = { Text("Mistral API Key") },
            modifier = Modifier.fillMaxWidth(),
            visualTransformation = PasswordVisualTransformation(),
            singleLine = true
        )
        
        Spacer(Modifier.height(16.dp))
        
        Button(onClick = {
            dataManager.saveApiKey(apiKey)
            Toast.makeText(context, "Settings Saved!", Toast.LENGTH_SHORT).show()
        }, modifier = Modifier.fillMaxWidth()) {
            Text("Save")
        }
    }
}

