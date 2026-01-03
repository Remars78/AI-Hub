
package com.example.aihub

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.os.Bundle // <--- ДОБАВЛЕНО ВОТ ЭТО
import android.util.Base64
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query
import java.io.ByteArrayOutputStream
import java.util.UUID
import java.util.concurrent.TimeUnit

// --- 1. DATA MODELS ---

// Mistral (Chat)
data class ChatRequest(val model: String, val messages: List<Message>, val temperature: Double = 0.7)
data class Message(val role: String, val content: String)
data class ChatResponse(val choices: List<Choice>)
data class Choice(val message: Message)

// Gemini (Universal: Text & Image)
data class GeminiRequest(val contents: List<GeminiContent>)
data class GeminiContent(val parts: List<GeminiPart>)
data class GeminiPart(val text: String? = null, val inline_data: GeminiBlob? = null)
data class GeminiBlob(val mime_type: String, val data: String)
data class GeminiResponse(val candidates: List<GeminiCandidate>?)
data class GeminiCandidate(val content: GeminiContent?)

data class ChatSession(
    val id: String = UUID.randomUUID().toString(),
    var title: String = "New Chat",
    val messages: MutableList<Message> = mutableListOf(),
    var timestamp: Long = System.currentTimeMillis()
)

// --- 2. API INTERFACES ---

interface MistralApi {
    @POST("v1/chat/completions")
    suspend fun chat(@Header("Authorization") auth: String, @Body request: ChatRequest): ChatResponse
}

interface GoogleAiApi {
    // Универсальный метод для Gemini.
    @POST("v1beta/models/{model}:generateContent")
    suspend fun generateContent(
        @Path("model") modelName: String,
        @Query("key") apiKey: String,
        @Body request: GeminiRequest
    ): GeminiResponse
}

// --- 3. SINGLETONS ---
object RetrofitClient {
    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    val mistralApi: MistralApi by lazy {
        Retrofit.Builder()
            .baseUrl("https://api.mistral.ai/")
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(MistralApi::class.java)
    }

    val googleApi: GoogleAiApi by lazy {
        Retrofit.Builder()
            .baseUrl("https://generativelanguage.googleapis.com/")
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(GoogleAiApi::class.java)
    }
}

// --- 4. DATA MANAGER ---
class DataManager(context: Context) {
    private val prefs = context.getSharedPreferences("ai_hub_data", Context.MODE_PRIVATE)
    private val gson = Gson()

    fun saveMistralKey(key: String) = prefs.edit().putString("mistral_key", key.trim()).apply()
    fun getMistralKey(): String = prefs.getString("mistral_key", "") ?: ""

    fun saveGeminiKey(key: String) = prefs.edit().putString("gemini_key", key.trim()).apply()
    fun getGeminiKey(): String = prefs.getString("gemini_key", "") ?: ""

    fun saveChats(chats: List<ChatSession>) {
        prefs.edit().putString("chat_history", gson.toJson(chats)).apply()
    }
    fun getChats(): MutableList<ChatSession> {
        val json = prefs.getString("chat_history", null) ?: return mutableListOf()
        return try { gson.fromJson(json, object : TypeToken<MutableList<ChatSession>>() {}.type) } catch (e: Exception) { mutableListOf() }
    }

    fun saveChatModel(model: String) = prefs.edit().putString("chat_model", model).apply()
    fun getChatModel(): String = prefs.getString("chat_model", "mistral-small-latest") ?: "mistral-small-latest"
}

// --- 5. UTILS ---
data class Line(val start: Offset, val end: Offset, val color: Color = Color.Black, val strokeWidth: Float = 5f)

fun bitmapFromLines(lines: List<Line>, width: Int, height: Int): String {
    if (width <= 0 || height <= 0) return ""
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    canvas.drawColor(android.graphics.Color.WHITE)
    val paint = Paint().apply {
        color = android.graphics.Color.BLACK
        strokeWidth = 10f
        strokeCap = Paint.Cap.ROUND
        isAntiAlias = true
    }
    for (line in lines) canvas.drawLine(line.start.x, line.start.y, line.end.x, line.end.y, paint)
    
    val outputStream = ByteArrayOutputStream()
    bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
    return Base64.encodeToString(outputStream.toByteArray(), Base64.NO_WRAP)
}

fun base64ToBitmap(base64Str: String): Bitmap? {
    return try {
        val decodedBytes = Base64.decode(base64Str, Base64.DEFAULT)
        BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.size)
    } catch (e: Exception) {
        null
    }
}

// --- 6. MAIN ACTIVITY ---
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
                NavigationBarItem(selected = selectedTab == 2, onClick = { selectedTab = 2 }, icon = { Text("✏️", fontSize = 20.sp) }, label = { Text("Sketch") })
                NavigationBarItem(selected = selectedTab == 3, onClick = { selectedTab = 3 }, icon = { Text("⚙️", fontSize = 20.sp) }, label = { Text("Set") })
            }
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding)) {
            when (selectedTab) {
                0 -> LLMTab()
                1 -> IMGTab()
                2 -> SketchTab()
                3 -> SettingsTab()
            }
        }
    }
}

// --- TAB 1: MISTRAL CHAT ---
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
                }) { Text("➕", fontSize = 24.sp) }
            }
        ) { p ->
            Column(Modifier.padding(p)) {
                Text("Chats", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(16.dp))
                LazyColumn {
                    items(allChats) { chat ->
                        Card(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp).clickable { currentChat = chat },
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(chat.title, style = MaterialTheme.typography.titleMedium, maxLines = 1)
                                    Text(if (chat.messages.isNotEmpty()) chat.messages.last().content else "Empty", style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                                IconButton(onClick = {
                                    allChats = allChats.filter { it.id != chat.id }.toMutableList()
                                    dataManager.saveChats(allChats)
                                }) { Text("🗑️") }
                            }
                        }
                    }
                }
            }
        }
    } else {
        ChatScreen(currentChat!!, { dataManager.saveChats(allChats); currentChat = null }, { dataManager.saveChats(allChats) })
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
                Text(if(dataManager.getChatModel().contains("large")) "Large" else "Small", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
            }
        }
        LazyColumn(Modifier.weight(1f).padding(8.dp), reverseLayout = true) {
            items(messages.reversed()) { msg -> ChatBubble(msg) }
        }
        if (isLoading) LinearProgressIndicator(Modifier.fillMaxWidth())
        Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            TextField(value = prompt, onValueChange = { prompt = it }, modifier = Modifier.weight(1f), placeholder = { Text("Message...") })
            IconButton(onClick = {
                val apiKey = dataManager.getMistralKey()
                if (apiKey.isBlank()) { Toast.makeText(context, "No Mistral Key!", Toast.LENGTH_SHORT).show(); return@IconButton }
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
                            val response = RetrofitClient.mistralApi.chat("Bearer $apiKey", ChatRequest(model = dataManager.getChatModel(), messages = chat.messages.toList()))
                            val botMsg = response.choices.first().message
                            messages.add(botMsg)
                            chat.messages.add(botMsg)
                            onUpdate()
                        } catch (e: Exception) { messages.add(Message("system", "Error: ${e.message}")) } finally { isLoading = false }
                    }
                }
            }) { Text("📤", fontSize = 24.sp) }
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

// --- TAB 2: TEXT -> IMAGE (Gemini 2.5 Flash Image) ---
@Composable
fun IMGTab() {
    val context = LocalContext.current
    val dataManager = remember { DataManager(context) }
    var prompt by remember { mutableStateOf("") }
    var resultBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var isGenerating by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("Gemini 2.5 Image", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(16.dp))
        
        OutlinedTextField(
            value = prompt, onValueChange = { prompt = it },
            label = { Text("What to see?") }, modifier = Modifier.fillMaxWidth().height(100.dp), maxLines = 5
        )
        
        Spacer(Modifier.height(16.dp))
        
        Button(
            onClick = {
                val gKey = dataManager.getGeminiKey()
                if (gKey.isBlank()) { Toast.makeText(context, "Set Gemini Key!", Toast.LENGTH_SHORT).show(); return@Button }
                isGenerating = true
                resultBitmap = null
                scope.launch {
                    try {
                        // Прямой запрос к модели gemini-2.5-flash-image
                        val req = GeminiRequest(listOf(GeminiContent(listOf(GeminiPart(text = prompt)))))
                        val response = RetrofitClient.googleApi.generateContent("gemini-2.0-flash", gKey, req)
                        
                        // Ищем картинку в ответе (inline_data)
                        val b64 = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull { it.inline_data != null }?.inline_data?.data
                        
                        if (b64 != null) {
                            resultBitmap = base64ToBitmap(b64)
                        } else {
                            // Если картинки нет, возможно модель вернула текст (отказ)
                            val text = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
                            Toast.makeText(context, text ?: "No image returned", Toast.LENGTH_LONG).show()
                        }
                    } catch (e: Exception) {
                        Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                    } finally {
                        isGenerating = false
                    }
                }
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = !isGenerating
        ) {
            if (isGenerating) { CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Color.White); Text(" Generating...") } else { Text("Generate") }
        }
        
        Spacer(Modifier.height(16.dp))
        if (resultBitmap != null) {
            Card(modifier = Modifier.fillMaxWidth().weight(1f), colors = CardDefaults.cardColors(containerColor = Color.Black)) {
                androidx.compose.foundation.Image(
                    bitmap = resultBitmap!!.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit
                )
            }
        } else {
            Spacer(Modifier.weight(1f))
        }
    }
}

// --- TAB 3: SKETCH -> IMAGE (Gemini 2.5 Flash Image) ---
@Composable
fun SketchTab() {
    val context = LocalContext.current
    val dataManager = remember { DataManager(context) }
    val scope = rememberCoroutineScope()

    val lines = remember { mutableStateListOf<Line>() }
    var prompt by remember { mutableStateOf("") }
    var resultBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var isProcessing by remember { mutableStateOf(false) }
    var canvasSize by remember { mutableStateOf(androidx.compose.ui.geometry.Size.Zero) }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("Sketch -> Gemini Image", style = MaterialTheme.typography.headlineSmall)
        
        // Canvas Drawing Area
        Box(
            modifier = Modifier.fillMaxWidth().weight(1f).background(Color.White, RoundedCornerShape(8.dp))
                .pointerInput(Unit) {
                    detectDragGestures { change, dragAmount ->
                        change.consume()
                        lines.add(Line(change.position - dragAmount, change.position))
                    }
                }
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                canvasSize = size
                lines.forEach { drawLine(it.color, it.start, it.end, it.strokeWidth, androidx.compose.ui.graphics.StrokeCap.Round) }
            }
            Button(
                onClick = { lines.clear(); resultBitmap = null },
                modifier = Modifier.align(Alignment.TopEnd).padding(8.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color.Red.copy(0.7f))
            ) { Text("Clear") }
        }

        Spacer(Modifier.height(8.dp))
        OutlinedTextField(value = prompt, onValueChange = { prompt = it }, label = { Text("Instruction (e.g. 'Make it realistic')") }, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(8.dp))

        Button(
            onClick = {
                val gKey = dataManager.getGeminiKey()
                if (gKey.isBlank()) { Toast.makeText(context, "Set Gemini Key!", Toast.LENGTH_SHORT).show(); return@Button }
                if (lines.isEmpty()) { Toast.makeText(context, "Draw first!", Toast.LENGTH_SHORT).show(); return@Button }

                isProcessing = true
                resultBitmap = null
                
                scope.launch {
                    try {
                        // 1. Sketch -> Base64
                        val b64Sketch = bitmapFromLines(lines, canvasSize.width.toInt(), canvasSize.height.toInt())
                        
                        // 2. Формируем запрос в Gemini 2.0 Flash
                        val textPart = GeminiPart(text = if(prompt.isBlank()) "Generate a high quality image based on this sketch." else prompt)
                        val imagePart = GeminiPart(inline_data = GeminiBlob("image/png", b64Sketch))
                        
                        val req = GeminiRequest(listOf(GeminiContent(listOf(textPart, imagePart))))
                        
                        // Вызов модели gemini-2.0-flash (она новее и поддерживает это)
                        val response = RetrofitClient.googleApi.generateContent("gemini-2.0-flash", gKey, req)
                        
                        val b64Img = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull { it.inline_data != null }?.inline_data?.data

                        if (b64Img != null) {
                            resultBitmap = base64ToBitmap(b64Img)
                        } else {
                            val text = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
                            Toast.makeText(context, text ?: "No image returned", Toast.LENGTH_LONG).show()
                        }
                    } catch (e: Exception) { 
                        Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_LONG).show() 
                    } finally { 
                        isProcessing = false 
                    }
                }
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = !isProcessing
        ) {
            if (isProcessing) { CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Color.White); Text(" Processing...") } else { Text("Generate Native") }
        }
        
        if (resultBitmap != null) {
            Spacer(Modifier.height(8.dp))
            Card(modifier = Modifier.fillMaxWidth().height(250.dp), colors = CardDefaults.cardColors(containerColor = Color.Black)) {
                androidx.compose.foundation.Image(bitmap = resultBitmap!!.asImageBitmap(), contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
            }
        }
    }
}

// --- TAB 3: SETTINGS ---
@Composable
fun SettingsTab() {
    val context = LocalContext.current
    val dataManager = remember { DataManager(context) }
    var mistralKey by remember { mutableStateOf(dataManager.getMistralKey()) }
    var geminiKey by remember { mutableStateOf(dataManager.getGeminiKey()) }
    var selectedChatModel by remember { mutableStateOf(dataManager.getChatModel()) }
    
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("Settings", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(16.dp))
        
        OutlinedTextField(value = mistralKey, onValueChange = { mistralKey = it }, label = { Text("Mistral API Key (Chat)") }, modifier = Modifier.fillMaxWidth(), visualTransformation = PasswordVisualTransformation(), singleLine = true)
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(value = geminiKey, onValueChange = { geminiKey = it }, label = { Text("Google API Key (Gen & Sketch)") }, modifier = Modifier.fillMaxWidth(), visualTransformation = PasswordVisualTransformation(), singleLine = true)
        
        Spacer(Modifier.height(16.dp))
        Text("Chat Model", fontWeight = FontWeight.Bold)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Row(verticalAlignment = Alignment.CenterVertically) { RadioButton(selected = selectedChatModel == "mistral-small-latest", onClick = { selectedChatModel = "mistral-small-latest" }); Text("Small") }
            Row(verticalAlignment = Alignment.CenterVertically) { RadioButton(selected = selectedChatModel == "mistral-large-latest", onClick = { selectedChatModel = "mistral-large-latest" }); Text("Large") }
        }
        
        Spacer(Modifier.weight(1f))
        Button(onClick = { 
            dataManager.saveMistralKey(mistralKey)
            dataManager.saveGeminiKey(geminiKey)
            dataManager.saveChatModel(selectedChatModel)
            Toast.makeText(context, "Saved!", Toast.LENGTH_SHORT).show()
        }, modifier = Modifier.fillMaxWidth()) { Text("Save 💾") }
    }
}
