package com.example.ui

import android.app.Activity
import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.PariMessage
import com.example.ui.theme.*
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.InputStreamReader

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PariScreen(viewModel: PariViewModel) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val messages by viewModel.messages.collectAsState()
    val listState = rememberLazyListState()

    // Smooth scroll to bottom when a new message arrives
    LaunchedEffect(messages.size, viewModel.isGenerating, viewModel.isSynthesizing) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    // System File Picker Launcher
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                val contentResolver = context.contentResolver
                val name = uri.lastPathSegment?.substringAfterLast("/") ?: "Attached_Document.txt"
                
                contentResolver.openInputStream(uri)?.use { inputStream ->
                    BufferedReader(InputStreamReader(inputStream)).use { reader ->
                        val content = reader.readText()
                        viewModel.selectSampleFile(name, content)
                        Toast.makeText(context, "Successfully attached: $name", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Failed to read file: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    var showVoiceSettings by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        PariAvatarAura()
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Pari",
                                style = MaterialTheme.typography.titleLarge.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.SansSerif,
                                    letterSpacing = 0.5.sp,
                                    color = TextDark
                                )
                            )
                            AnimatedVisibility(
                                visible = true,
                                enter = fadeIn(),
                                exit = fadeOut()
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(6.dp)
                                            .clip(CircleShape)
                                            .background(
                                                if (viewModel.isGenerating || viewModel.isSynthesizing) PrimaryIndigo 
                                                else SecondaryEmerald
                                            )
                                    )
                                    Text(
                                        text = when {
                                            viewModel.isGenerating -> "Pari is thinking..."
                                            viewModel.isSynthesizing -> "Pari is speaking..."
                                            else -> "Creative Partner"
                                        },
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            color = TextMuted,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Medium
                                        )
                                    )
                                }
                            }
                        }
                    }
                },
                actions = {
                    IconButton(onClick = { showVoiceSettings = !showVoiceSettings }) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Voice Tuning Settings",
                            tint = PrimaryIndigo
                        )
                    }
                    IconButton(onClick = { viewModel.clearAllHistory() }) {
                        Icon(
                            imageVector = Icons.Default.DeleteSweep,
                            contentDescription = "Clear History",
                            tint = TextMuted.copy(alpha = 0.7f)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = BgSlate,
                    titleContentColor = TextDark
                )
            )
        },
        bottomBar = {
            PariInputBar(
                viewModel = viewModel,
                onAttachClick = { filePickerLauncher.launch("*/*") }
            )
        },
        containerColor = BgSlate
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Main content layout split between message list and configuration panel
            Column(modifier = Modifier.fillMaxSize()) {
                
                // Mode Toggle Carousel at the top
                PariModeSelector(
                    selectedMode = viewModel.selectedMode,
                    onModeSelected = { viewModel.selectedMode = it }
                )

                // Optional expandable voice settings box
                AnimatedVisibility(
                    visible = showVoiceSettings,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    PariVoiceSettingsPanel(viewModel = viewModel)
                }

                // If a file is currently attached, show a preview bar
                AnimatedVisibility(
                    visible = viewModel.attachedFileName != null,
                    enter = slideInVertically() + fadeIn(),
                    exit = slideOutVertically() + fadeOut()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(SurfaceWhite)
                            .border(1.dp, BorderIndigo)
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.AttachFile,
                                contentDescription = "Attached File",
                                tint = PrimaryIndigo,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = viewModel.attachedFileName ?: "",
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    color = TextDark,
                                    fontWeight = FontWeight.Bold
                                ),
                                maxLines = 1
                            )
                        }
                        IconButton(
                            onClick = { viewModel.removeAttachment() },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Remove attachment",
                                tint = Color.Red,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }

                // Main conversational lazy column
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(horizontal = 16.dp),
                    contentPadding = PaddingValues(top = 8.dp, bottom = 16.dp)
                ) {
                    items(messages) { message ->
                        PariMessageBubble(
                            message = message,
                            viewModel = viewModel
                        )
                    }

                    if (viewModel.isGenerating) {
                        item {
                            PariThinkingIndicator()
                        }
                    }
                }
            }
        }
    }
}

/**
 * Custom animated avatar representing Pari's cosmic energy aura.
 */
@Composable
fun PariAvatarAura() {
    val infiniteTransition = rememberInfiniteTransition(label = "AuraAnimation")
    
    // Smooth pulse for outer ring
    val scale by infiniteTransition.animateFloat(
        initialValue = 0.85f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "AuraScale"
    )

    // Smooth rotation of the aura gradient
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(6000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "AuraRotation"
    )

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.size(44.dp)
    ) {
        Canvas(modifier = Modifier.size(40.dp)) {
            val radius = size.minDimension / 2
            val brush = Brush.sweepGradient(
                colors = listOf(PrimaryIndigo, SecondaryEmerald, PrimarySoftViolet, PrimaryIndigo)
            )

            // Draw outer pulsing background aura
            drawCircle(
                brush = brush,
                radius = radius * scale,
                alpha = 0.35f,
                style = Stroke(width = 3.dp.toPx())
            )

            // Draw solid inner core
            drawCircle(
                color = SurfaceWhite,
                radius = radius - 3.dp.toPx()
            )

            // Draw a subtle shining arc
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(SecondaryEmerald, Color.Transparent),
                    radius = radius * 0.8f
                ),
                radius = radius - 6.dp.toPx(),
                alpha = 0.6f
            )
        }

        // Beautiful smiley face in the center
        Icon(
            imageVector = Icons.Default.Face,
            contentDescription = "Pari face",
            tint = PrimaryIndigo,
            modifier = Modifier.size(22.dp)
        )
    }
}

@Composable
fun PariModeSelector(
    selectedMode: String,
    onModeSelected: (String) -> Unit
) {
    val modes = listOf(
        Triple("chat", "Companion", Icons.Default.ChatBubbleOutline),
        Triple("tts", "Speak", Icons.Default.SettingsVoice),
        Triple("podcast", "Podcast", Icons.Default.Podcasts),
        Triple("audiobook", "Audiobook", Icons.Default.Book)
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(BgSlate)
            .padding(vertical = 12.dp)
    ) {
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(modes) { (key, title, icon) ->
                val isSelected = selectedMode == key
                val backgroundColor = if (isSelected) PrimaryIndigo else SurfaceWhite
                val contentColor = if (isSelected) Color.White else TextDark
                val borderColor = if (isSelected) PrimaryIndigo else BorderIndigo

                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(backgroundColor)
                        .border(1.dp, borderColor, RoundedCornerShape(16.dp))
                        .clickable { onModeSelected(key) }
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                        .testTag("mode_${key}"),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = title,
                        tint = if (isSelected) Color.White else PrimaryIndigo,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = title,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            color = contentColor,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                        )
                    )
                }
            }
        }
        
        Text(
            text = when (selectedMode) {
                "chat" -> "💬 Warm personal chat, empathetic guidance & creative banter."
                "tts" -> "🔊 Instantly converts text input into studio-quality vocal speeches."
                "podcast" -> "🎙️ Structured broadcast dialogue script and full audio generator."
                "audiobook" -> "📚 Deep, cinematic storytelling and audiobook narrations."
                else -> ""
            },
            style = MaterialTheme.typography.bodySmall.copy(
                color = TextMuted,
                fontFamily = FontFamily.SansSerif,
                fontWeight = FontWeight.Medium
            ),
            modifier = Modifier.padding(horizontal = 20.dp).padding(top = 8.dp)
        )
    }
}

@Composable
fun PariVoiceSettingsPanel(viewModel: PariViewModel) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceWhite),
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, BorderIndigo)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Studio Voice Tuning 🎙️",
                style = MaterialTheme.typography.titleMedium.copy(
                    color = PrimaryIndigo,
                    fontWeight = FontWeight.Bold
                )
            )
            Spacer(modifier = Modifier.height(14.dp))

            // Pitch Slider
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Voice Tone (Pitch)",
                        style = MaterialTheme.typography.bodyMedium.copy(color = TextDark, fontWeight = FontWeight.SemiBold)
                    )
                    Text(
                        text = when {
                            viewModel.voicePitch < 0.8f -> "Deep Narrative"
                            viewModel.voicePitch > 1.3f -> "Playful/Flirty"
                            else -> "Melodious Companion"
                        },
                        style = MaterialTheme.typography.bodySmall.copy(color = PrimaryIndigo, fontWeight = FontWeight.Bold)
                    )
                }
                Slider(
                    value = viewModel.voicePitch,
                    onValueChange = { viewModel.voicePitch = it },
                    valueRange = 0.5f..2.0f,
                    colors = SliderDefaults.colors(
                        thumbColor = PrimaryIndigo,
                        activeTrackColor = PrimaryIndigo,
                        inactiveTrackColor = PrimarySoftViolet
                    )
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Speed Slider
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Emotional Pacing (Speed)",
                        style = MaterialTheme.typography.bodyMedium.copy(color = TextDark, fontWeight = FontWeight.SemiBold)
                    )
                    Text(
                        text = when {
                            viewModel.voiceSpeed < 0.8f -> "Cinematic Slow"
                            viewModel.voiceSpeed > 1.3f -> "Rapid Dialogue"
                            else -> "Natural Flow"
                        },
                        style = MaterialTheme.typography.bodySmall.copy(color = PrimaryIndigo, fontWeight = FontWeight.Bold)
                    )
                }
                Slider(
                    value = viewModel.voiceSpeed,
                    onValueChange = { viewModel.voiceSpeed = it },
                    valueRange = 0.5f..2.0f,
                    colors = SliderDefaults.colors(
                        thumbColor = PrimaryIndigo,
                        activeTrackColor = PrimaryIndigo,
                        inactiveTrackColor = PrimarySoftViolet
                    )
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Language Toggles
            Text(
                text = "Language / Accents",
                style = MaterialTheme.typography.bodyMedium.copy(color = TextDark, fontWeight = FontWeight.Bold)
            )
            Spacer(modifier = Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val languages = listOf(
                    "en" to "US Accent",
                    "hi" to "Hinglish/Hindi",
                    "en_gb" to "UK Accent"
                )
                languages.forEach { (code, label) ->
                    val isSelected = viewModel.selectedLanguage == code
                    OutlinedButton(
                        onClick = { viewModel.selectedLanguage = code },
                        border = BorderStroke(1.dp, if (isSelected) PrimaryIndigo else BorderIndigo),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = if (isSelected) PrimarySoftViolet else Color.Transparent
                        ),
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = if (isSelected) PrimaryIndigo else TextMuted,
                                fontWeight = FontWeight.SemiBold
                            )
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun PariMessageBubble(
    message: PariMessage,
    viewModel: PariViewModel
) {
    val isPari = message.role == "pari"
    val bubbleShape = if (isPari) {
        RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomEnd = 16.dp, bottomStart = 4.dp)
    } else {
        RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomEnd = 4.dp, bottomStart = 16.dp)
    }

    val bubbleBg = if (isPari) SurfaceWhite else PrimarySoftViolet
    val borderColor = if (isPari) BorderIndigo else PrimaryIndigo.copy(alpha = 0.3f)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = if (isPari) Arrangement.Start else Arrangement.End
    ) {
        if (isPari) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(PrimarySoftViolet),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Face,
                    contentDescription = "Pari",
                    tint = PrimaryIndigo,
                    modifier = Modifier.size(18.dp)
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
        }

        Column(
            modifier = Modifier
                .fillMaxWidth(0.85f)
                .shadow(1.dp, bubbleShape)
                .background(bubbleBg, bubbleShape)
                .border(1.dp, borderColor, bubbleShape)
                .padding(14.dp)
        ) {
            if (message.scriptTitle != null) {
                Text(
                    text = "🎬 ${message.scriptTitle}",
                    style = MaterialTheme.typography.titleSmall.copy(
                        color = PrimaryIndigo,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp
                    ),
                    modifier = Modifier.padding(bottom = 6.dp)
                )
            }

            // Message text contents
            Text(
                text = message.content,
                style = MaterialTheme.typography.bodyMedium.copy(
                    color = TextDark,
                    lineHeight = 22.sp
                )
            )

            // Dynamic Audio Player Widget for Pari's generated speech
            if (isPari && message.isAudioGenerated && message.audioPath != null) {
                Spacer(modifier = Modifier.height(14.dp))
                HorizontalDivider(color = BorderIndigo)
                Spacer(modifier = Modifier.height(10.dp))
                
                var isPlaying by remember { mutableStateOf(viewModel.isPlayingAudio(message)) }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(AccentSoftBlue)
                        .border(1.dp, BorderIndigo, RoundedCornerShape(12.dp))
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = {
                                if (isPlaying) {
                                    viewModel.pauseMessageAudio(message)
                                    isPlaying = false
                                } else {
                                    viewModel.playMessageAudio(message) {
                                        isPlaying = false
                                    }
                                    isPlaying = true
                                }
                            },
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(PrimaryIndigo)
                                .testTag("play_button_${message.id}")
                        ) {
                            Icon(
                                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = if (isPlaying) "Pause Voice note" else "Play Voice note",
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        
                        Spacer(modifier = Modifier.width(10.dp))
                        
                        Column {
                            Text(
                                text = if (isPlaying) "Speaking now..." else "Pari's Voice entry",
                                style = MaterialTheme.typography.labelMedium.copy(
                                    color = TextDark,
                                    fontWeight = FontWeight.Bold
                                )
                            )
                            Text(
                                text = "Listen & Download ready",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = TextMuted,
                                    fontSize = 10.sp
                                )
                            )
                        }
                    }

                    // Direct Download button
                    IconButton(
                        onClick = { viewModel.downloadMessageAudio(message) },
                        modifier = Modifier
                            .size(34.dp)
                            .testTag("download_button_${message.id}")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Download,
                            contentDescription = "Download audio file to device",
                            tint = PrimaryIndigo,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun PariThinkingIndicator() {
    val infiniteTransition = rememberInfiniteTransition(label = "Thinking")
    val translation by infiniteTransition.animateFloat(
        initialValue = -10f,
        targetValue = 10f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "ThinkingTranslation"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.Start
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(PrimarySoftViolet),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Face,
                contentDescription = "Pari thinking",
                tint = PrimaryIndigo,
                modifier = Modifier.size(18.dp)
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Box(
            modifier = Modifier
                .shadow(1.dp, RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomEnd = 16.dp, bottomStart = 4.dp))
                .background(SurfaceWhite, RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomEnd = 16.dp, bottomStart = 4.dp))
                .border(1.dp, BorderIndigo, RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomEnd = 16.dp, bottomStart = 4.dp))
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                repeat(3) { index ->
                    val delay = index * 200
                    val offset by infiniteTransition.animateFloat(
                        initialValue = -4f,
                        targetValue = 4f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(600, delayMillis = delay, easing = FastOutSlowInEasing),
                            repeatMode = RepeatMode.Reverse
                        ),
                        label = "DotOffset_$index"
                    )
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .offset(y = offset.dp)
                            .clip(CircleShape)
                            .background(if (index == 1) SecondaryEmerald else PrimaryIndigo)
                    )
                }
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "Pari is whispering ideas...",
                    style = MaterialTheme.typography.bodySmall.copy(color = TextMuted)
                )
            }
        }
    }
}

@Composable
fun PariInputBar(
    viewModel: PariViewModel,
    onAttachClick: () -> Unit
) {
    var expandedSampleFiles by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = SurfaceWhite,
                shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp)
            )
            .border(
                width = 1.dp,
                color = BorderIndigo,
                shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp)
            )
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        // Sample attachments panel toggled via paperclip long click or auxiliary tray
        AnimatedVisibility(
            visible = expandedSampleFiles,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                colors = CardDefaults.cardColors(containerColor = AccentSoftIndigo),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, BorderIndigo)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = "Quick Sample Documents (Analyze instantly) 📁",
                        style = MaterialTheme.typography.labelMedium.copy(
                            color = PrimaryIndigo,
                            fontWeight = FontWeight.Bold
                        ),
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    val sampleDocuments = listOf(
                        Pair(
                            "SciFi_World_Draft.txt",
                            "Title: The Holographic Nexus. In the year 2390, human consciousness can be serialized and uploaded into central planetary quantum networks. A lonely programmer discovers a hidden server housing a forgotten love story of the old world."
                        ),
                        Pair(
                            "Warm_Letter.txt",
                            "Dear Friend, I know things have been challenging lately with your studies and creative goals, but your passion is truly radiant. Remember to take a deep breath and keep pushing—you have an exceptional mind and an amazing heart."
                        ),
                        Pair(
                            "AI_Podcast_Notes.txt",
                            "Pari Podcast episode 12. Main theme: The evolution of companionship in the cybernetic age. Key points: How emotional support agents alleviate loneliness, the combination of TTS + LLM, and the ethical parameters of AI partnership."
                        )
                    )

                    sampleDocuments.forEach { (title, contents) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable {
                                    viewModel.selectSampleFile(title, contents)
                                    expandedSampleFiles = false
                                }
                                .padding(vertical = 8.dp, horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Description,
                                contentDescription = "Doc",
                                tint = PrimaryIndigo,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = title,
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = TextDark,
                                    fontWeight = FontWeight.Bold
                                )
                            )
                        }
                    }
                }
            }
        }

        // Title Row if Podcast or Audiobook mode is selected
        if (viewModel.selectedMode == "podcast" || viewModel.selectedMode == "audiobook") {
            OutlinedTextField(
                value = viewModel.scriptTitle,
                onValueChange = { viewModel.scriptTitle = it },
                label = {
                    Text(
                        text = if (viewModel.selectedMode == "podcast") "Podcast Episode Title" else "Book / Chapter Title",
                        style = MaterialTheme.typography.bodySmall.copy(color = TextMuted)
                    )
                },
                placeholder = {
                    Text(
                        text = if (viewModel.selectedMode == "podcast") "e.g. Cybernetic Love Chronicles" else "e.g. Whispers of the Star Nebula",
                        style = MaterialTheme.typography.bodySmall.copy(color = TextMuted.copy(alpha = 0.5f))
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp)
                    .testTag("script_title_field"),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = PrimaryIndigo,
                    unfocusedBorderColor = BorderIndigo,
                    focusedTextColor = TextDark,
                    unfocusedTextColor = TextDark
                ),
                shape = RoundedCornerShape(12.dp),
                maxLines = 1
            )
        }

        // Standard Message Input Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Bottom
        ) {
            // Local Attachment Button
            IconButton(
                onClick = onAttachClick,
                modifier = Modifier
                    .size(44.dp)
                    .padding(bottom = 4.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.AttachFile,
                    contentDescription = "Attach local file from device",
                    tint = PrimaryIndigo,
                    modifier = Modifier.size(22.dp)
                )
            }

            // Quick Samples Toggle Button
            IconButton(
                onClick = { expandedSampleFiles = !expandedSampleFiles },
                modifier = Modifier
                    .size(44.dp)
                    .padding(bottom = 4.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.FolderOpen,
                    contentDescription = "Browse sample document templates",
                    tint = if (expandedSampleFiles) PrimaryIndigo else TextMuted,
                    modifier = Modifier.size(22.dp)
                )
            }

            // Input TextField
            OutlinedTextField(
                value = viewModel.inputText,
                onValueChange = { viewModel.inputText = it },
                placeholder = {
                    Text(
                        text = when (viewModel.selectedMode) {
                            "chat" -> "Say something charming..."
                            "tts" -> "Input script for voice..."
                            "podcast" -> "Podcast topic..."
                            "audiobook" -> "Story outline..."
                            else -> "Talk to Pari..."
                        },
                        style = MaterialTheme.typography.bodyMedium.copy(
                            color = TextMuted,
                            fontSize = 14.sp
                        )
                    )
                },
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 4.dp)
                    .testTag("message_input"),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = PrimaryIndigo,
                    unfocusedBorderColor = BorderIndigo,
                    focusedContainerColor = AccentSoftBlue,
                    unfocusedContainerColor = AccentSoftBlue,
                    focusedTextColor = TextDark,
                    unfocusedTextColor = TextDark
                ),
                shape = RoundedCornerShape(24.dp),
                maxLines = 4
            )

            // Send or Speak Action Button
            IconButton(
                onClick = {
                    if (viewModel.inputText.isNotBlank() || viewModel.attachedFileContent != null) {
                        viewModel.sendMessage()
                    }
                },
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(PrimaryIndigo)
                    .padding(bottom = 4.dp)
                    .testTag("send_button")
            ) {
                Icon(
                    imageVector = if (viewModel.selectedMode != "chat") Icons.Default.Audiotrack else Icons.Default.Send,
                    contentDescription = "Send and process audio",
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}
