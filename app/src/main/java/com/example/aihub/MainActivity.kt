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
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.ImageLoader
import coil.compose.SubcomposeAsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST
import java.net.URLEncoder
import java.util.UUID
import java.util.concurrent.TimeUnit

// --- 1. DATA MODELS ---
data class ChatRequest(val model: String, val messages: List<Message>, val temperature: Double = 0.7)
data class Message(val role: String, val content: String)
data class ChatResponse(val choices: List<Choice>)
data class Choice(val message: Message)

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

// --- 4. DATA MANAGER ---
class DataManager(context: Context) {
    private val prefs = context.getSharedPreferences("ai_hub_data", Context.MODE_PRIVATE)
    private val gson = Gson()

    // API Key
    fun saveApiKey(key: String) = prefs.edit().putString("mistral_api_key", key.trim()).apply()
    fun getApiKey(): String = prefs.getString("mistral_api_key", "") ?: ""

    // Chat History
    fun saveChats(chats: List<ChatSession>) {
        val json = gson.toJson(chats)
        prefs.edit().putString("chat_history", json).apply()
    }
    fun getChats(): MutableList<ChatSession> {
        val json = prefs.getString("chat_history", null) ?: return mutableListOf()
        val type = object : TypeToken<MutableList<ChatSession>>() {}.type
        return try { gson.fromJson(json, type) } catch (e: Exception) { mutableListOf() }
    }

    // Settings
    fun saveChatModel(model: String) = prefs.edit().putString("chat_model_pref", model).apply()
    fun getChatModel(): String = prefs.getString("chat_model_pref", "mistral-small-latest") ?: "mistral-small-latest"

    fun saveImgModel(model: String) = prefs.edit().putString("img_model_pref", model).apply()
    fun getImgModel(): String = prefs.getString("img_model_pref", "default") ?: "default"
}

// --- 5. MAIN ACTIVITY ---
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
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
                NavigationBarItem(selected = selectedTab == 0, onClick = { selectedTab = 0 }, icon = { Text("💬", fontSize = 20.sp) }, label = { Text("Chat") })
                NavigationBarItem(selected = selectedTab == 1, onClick = { selectedTab = 1 }, icon = { Text("🎨", fontSize = 20.sp) }, label = { Text("Gen") })
                NavigationBarItem(selected = selectedTab == 2, onClick = { selectedTab = 2 }, icon = { Text("⚙️", fontSize = 20.sp) }, label = { Text("Set") })
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

// --- TAB 1: LLM CHAT ---
@Composable
fun LLMTab() {
    val context = LocalContext.current
    val dataManager = remember { DataManager(context) }
    var allChats by remember { mutableStateOf(dataManager.getChats()) }
    var currentChat by remember { mutableStateOf<ChatSession?>(null) }
    
    if (currentChat == null) {
        Scaffold(
            floatingActionButton = {
                FloatingActionButton(onClick = {
                    val newChat = ChatSession()
                    allChats.add(0, newChat)
                    currentChat = newChat
                    dataManager.saveChats(allChats)
                }) {
                    Text("➕", fontSize = 24.sp)
                }
            }
        ) { p ->
            Column(Modifier.padding(p)) {
                Text("Chats", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(16.dp))
                LazyColumn {
                    items(allChats) { chat ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 4.dp)
                                .clickable { currentChat = chat },
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(chat.title, style = MaterialTheme.typography.titleMedium, maxLines = 1)
                                    Text(
                                        if (chat.messages.isNotEmpty()) chat.messages.last().content else "Empty",
                                        style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis
                                    )
                                }
                                IconButton(onClick = {
                                    allChats = allChats.filter { it.id != chat.id }.toMutableList()
                                    dataManager.saveChats(allChats)
                                }) {
                                    Text("🗑️")
                                }
                            }
                        }
                    }
                }
            }
        }
    } else {
        ChatScreen(
            chat = currentChat!!,
            onBack = { dataManager.saveChats(allChats); currentChat = null },
            onUpdate = { dataManager.saveChats(allChats) }
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
    val messages = remember { mutableStateListOf<Message>().apply { addAll(chat.messages) } }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant).padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Text("⬅️", fontSize = 24.sp) }
            Column {
                Text(chat.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                val modelName = if(dataManager.getChatModel().contains("large")) "Large Model" else "Small Model"
                Text(modelName, style = MaterialTheme.typography.labelSmall, color = Color.Gray)
            }
        }
        LazyColumn(Modifier.weight(1f).padding(8.dp), reverseLayout = true) {
            items(messages.reversed()) { msg -> ChatBubble(msg) }
        }
        if (isLoading) LinearProgressIndicator(Modifier.fillMaxWidth())
        Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            TextField(
                value = prompt, onValueChange = { prompt = it },
                modifier = Modifier.weight(1f), placeholder = { Text("Message...") },
                colors = TextFieldDefaults.colors(focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent),
                shape = RoundedCornerShape(24.dp)
            )
            IconButton(onClick = {
                val apiKey = dataManager.getApiKey()
                if (apiKey.isBlank()) {
                    Toast.makeText(context, "No API Key!", Toast.LENGTH_SHORT).show()
                    return@IconButton
                }
                if (prompt.isNotBlank()) {
                    val userMsg = Message("user", prompt)
                    messages.add(userMsg)
                    chat.messages.add(userMsg)
                    if (chat.messages.size == 1) chat.title = prompt.take(30)
                    prompt = ""
                    isLoading = true
                    onUpdate()
                    scope.launch {
                        try {
                            val selectedModel = dataManager.getChatModel()
                            val request = ChatRequest(model = selectedModel, messages = chat.messages.toList())
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
                Text("📤", fontSize = 24.sp)
            }
        }
    }
}

@Composable
fun ChatBubble(message: Message) {
    val isUser = message.role == "user"
    Box(Modifier.fillMaxWidth(), contentAlignment = if (isUser) Alignment.CenterEnd else Alignment.CenterStart) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = if (isUser) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.secondaryContainer,
            modifier = Modifier.padding(4.dp).widthIn(max = 300.dp)
        ) {
            Text(message.content, modifier = Modifier.padding(12.dp), fontSize = 16.sp)
        }
    }
}

// --- TAB 2: IMAGE GEN ---
@Composable
fun IMGTab() {
    val context = LocalContext.current
    val dataManager = remember { DataManager(context) }
    var prompt by remember { mutableStateOf("") }
    var imageUrl by remember { mutableStateOf<String?>(null) }
    var isEnhancing by remember { mutableStateOf(false) }
    var useEnhancer by remember { mutableStateOf(true) }
    var lastError by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    val customImageLoader = remember {
        ImageLoader.Builder(context)
            .okHttpClient {
                OkHttpClient.Builder()
                    .connectTimeout(60, TimeUnit.SECONDS)
                    .readTimeout(60, TimeUnit.SECONDS)
                    .build()
            }
            .build()
    }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Image Generator", style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.width(8.dp))
            val currentModel = dataManager.getImgModel()
            // Используем Surface вместо Badge (безопаснее для сборки)
            Surface(
                color = if(currentModel == "flux") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary,
                shape = RoundedCornerShape(4.dp)
            ) {
                Text(
                    text = if(currentModel == "flux") " FLUX " else " TURBO ",
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }
        Spacer(Modifier.height(16.dp))

        OutlinedTextField(
            value = prompt,
            onValueChange = { prompt = it },
            label = { Text("What to generate?") },
            modifier = Modifier.fillMaxWidth().height(100.dp),
            maxLines = 5
        )
        
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = useEnhancer, onCheckedChange = { useEnhancer = it })
            Text("Enhance Prompt")
            if (useEnhancer) Text(" ✨", fontSize = 18.sp)
        }

        Spacer(Modifier.height(8.dp))
        
        Button(
            onClick = {
                val apiKey = dataManager.getApiKey()
                if (useEnhancer && apiKey.isBlank()) {
                    Toast.makeText(context, "Need API Key!", Toast.LENGTH_SHORT).show()
                    return@Button
                }

                lastError = null 
                imageUrl = null 

                scope.launch {
                    var finalPrompt = prompt
                    isEnhancing = true
                    
                    if (useEnhancer && prompt.isNotBlank()) {
                        try {
                            val enhanceRequest = ChatRequest(
                                model = "mistral-tiny",
                                messages = listOf(
                                    Message("system", "Rewrite as detailed Stable Diffusion prompt. Keep it under 40 words."),
                                    Message("user", prompt)
                                )
                            )
                            val response = RetrofitClient.api.chat("Bearer $apiKey", enhanceRequest)
                            finalPrompt = response.choices.first().message.content
                        } catch (e: Exception) {
                            Toast.makeText(context, "Enhance Error", Toast.LENGTH_SHORT).show()
                        }
                    }

                    val encoded = URLEncoder.encode(finalPrompt, "UTF-8")
                    val uniqueId = "${System.currentTimeMillis()}-${(1..999).random()}"
                    
                    val selectedImgModel = dataManager.getImgModel()
                    val modelParam = if (selectedImgModel == "flux") "&model=flux" else ""
                    
                    imageUrl = "https://image.pollinations.ai/prompt/$encoded?nologo=true&cachebuster=$uniqueId$modelParam"
                    
                    isEnhancing = false
                }
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = !isEnhancing
        ) {
            if (isEnhancing) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Color.White)
                Text(" Working...")
            } else {
                Text("Generate 🎨")
            }
        }

        Spacer(Modifier.height(16.dp))

        if (imageUrl != null) {
            Card(
                modifier = Modifier.fillMaxWidth().weight(1f),
                colors = CardDefaults.cardColors(containerColor = Color.Black)
            ) {
                SubcomposeAsyncImage(
                    imageLoader = customImageLoader,
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(imageUrl)
                        .memoryCachePolicy(CachePolicy.DISABLED)
                        .diskCachePolicy(CachePolicy.DISABLED)
                        .networkCachePolicy(CachePolicy.ENABLED)
                        .addHeader("Connection", "close")
                        .crossfade(true)
                        .listener(onError = { _, result -> lastError = result.throwable.message ?: "Unknown Error" })
                        .build(),
                    contentDescription = "Art",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit,
                    loading = {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                CircularProgressIndicator()
                                Spacer(Modifier.height(8.dp))
                                Text("Generating...", color = Color.White)
                            }
                        }
                    },
                    error = {
                        Box(
                            Modifier.fillMaxSize().clickable { Toast.makeText(context, "Error: $lastError", Toast.LENGTH_LONG).show() }, 
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("⚠️", fontSize = 48.sp)
                                Text("Failed", color = Color.Red)
                            }
                        }
                    }
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
    
    var selectedChatModel by remember { mutableStateOf(dataManager.getChatModel()) }
    var selectedImgModel by remember { mutableStateOf(dataManager.getImgModel()) }
    
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("Settings", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(16.dp))
        
        OutlinedTextField(
            value = apiKey, onValueChange = { apiKey = it },
            label = { Text("Mistral API Key") }, modifier = Modifier.fillMaxWidth(),
            visualTransformation = PasswordVisualTransformation(), singleLine = true
        )
        
        Spacer(Modifier.height(16.dp))
        Box(Modifier.fillMaxWidth().height(1.dp).background(Color.Gray)) // Divider replacement
        Spacer(Modifier.height(16.dp))
        
        Text("Chat Model", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                RadioButton(
                    selected = selectedChatModel == "mistral-small-latest",
                    onClick = { selectedChatModel = "mistral-small-latest" }
                )
                Text("Small (Fast)")
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                RadioButton(
                    selected = selectedChatModel == "mistral-large-latest",
                    onClick = { selectedChatModel = "mistral-large-latest" }
                )
                Text("Large (Smart)")
            }
        }
        
        Spacer(Modifier.height(16.dp))
        Box(Modifier.fillMaxWidth().height(1.dp).background(Color.Gray))
        Spacer(Modifier.height(16.dp))

        Text("Image Model", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text("Flux is better but slower.", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                RadioButton(
                    selected = selectedImgModel == "default",
                    onClick = { selectedImgModel = "default" }
                )
                Text("Turbo")
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                RadioButton(
                    selected = selectedImgModel == "flux",
                    onClick = { selectedImgModel = "flux" }
                )
                Text("Flux (HQ)")
            }
        }

        Spacer(Modifier.weight(1f))
        
        Button(onClick = {
            dataManager.saveApiKey(apiKey)
            dataManager.saveChatModel(selectedChatModel)
            dataManager.saveImgModel(selectedImgModel)
            Toast.makeText(context, "All Settings Saved!", Toast.LENGTH_SHORT).show()
        }, modifier = Modifier.fillMaxWidth()) {
            Text("Save 💾")
        }
    }
}
