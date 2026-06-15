package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.AppDatabase
import com.example.data.AudioRepository
import com.example.ui.AppTab
import com.example.ui.MainViewModel
import com.example.ui.MainViewModelFactory
import com.example.ui.screens.EditorScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.SavedFilesScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Setup local Room DB components
        val database = AppDatabase.getDatabase(applicationContext)
        val repository = AudioRepository(database.audioRecordDao())

        // ViewModel Delegate instantiation with factory
        val viewModel: MainViewModel by viewModels {
            MainViewModelFactory(repository, applicationContext)
        }

        enableEdgeToEdge()

        setContent {
            MyApplicationTheme {
                // UI Core
                StudioAppContent(viewModel = viewModel)
            }
        }
    }
}

@Composable
fun StudioAppContent(viewModel: MainViewModel) {
    val currentTab by viewModel.currentTab.collectAsStateWithLifecycle()
    val isTtsInitialized by viewModel.isTtsInitialized.collectAsStateWithLifecycle()
    val savedRecords by viewModel.savedRecords.collectAsStateWithLifecycle()

    // Editor fields
    val editorText by viewModel.editorText.collectAsStateWithLifecycle()
    val selectedStyle by viewModel.selectedStyle.collectAsStateWithLifecycle()
    val pitch by viewModel.pitch.collectAsStateWithLifecycle()
    val speed by viewModel.speed.collectAsStateWithLifecycle()
    val fileNameInput by viewModel.fileNameInput.collectAsStateWithLifecycle()
    val isGenerating by viewModel.isGenerating.collectAsStateWithLifecycle()
    val generationSuccess by viewModel.generationSuccessMessage.collectAsStateWithLifecycle()
    val generationError by viewModel.generationError.collectAsStateWithLifecycle()

    // Player fields
    val playingRecordId by viewModel.playingRecordId.collectAsStateWithLifecycle()
    val isPlaying by viewModel.isPlaying.collectAsStateWithLifecycle()
    val playbackPosition by viewModel.playbackPosition.collectAsStateWithLifecycle()
    val playbackDuration by viewModel.playbackDuration.collectAsStateWithLifecycle()

    // Event notifications (using standard M3 SnackBar)
    val snackbarHostState = remember { SnackbarHostState() }
    val uiNotification by viewModel.uiNotification.collectAsStateWithLifecycle()

    LaunchedEffect(uiNotification) {
        uiNotification?.let { message ->
            snackbarHostState.showSnackbar(message)
            viewModel.clearNotification()
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets.safeDrawing, // support edge to edge status & nav bar
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        bottomBar = {
            StudioBottomNavigation(
                currentTab = currentTab,
                onTabSelected = { tab ->
                    // Release playing buffers if transferring tabs
                    viewModel.stopPlayback()
                    viewModel.selectTab(tab)
                }
            )
        }
    ) { innerPadding ->
        Crossfade(
            targetState = currentTab,
            animationSpec = tween(durationMillis = 220),
            modifier = Modifier.padding(innerPadding),
            label = "ScreenCrossfade"
        ) { tab ->
            when (tab) {
                AppTab.HOME -> {
                    HomeScreen(
                        recordCount = savedRecords.size,
                        onNavigateToTab = { target ->
                            viewModel.selectTab(target)
                        }
                    )
                }
                AppTab.EDITOR -> {
                    EditorScreen(
                        text = editorText,
                        onTextChanged = { viewModel.updateEditorText(it) },
                        selectedStyle = selectedStyle,
                        onStyleChanged = { viewModel.updateSelectedStyle(it) },
                        pitch = pitch,
                        onPitchChanged = { viewModel.updatePitch(it) },
                        speed = speed,
                        onSpeedChanged = { viewModel.updateSpeed(it) },
                        fileNameInput = fileNameInput,
                        onFileNameInputChanged = { viewModel.updateFileNameInput(it) },
                        isGenerating = isGenerating,
                        generationSuccess = generationSuccess,
                        generationError = generationError,
                        onPlayPreview = { viewModel.playPreview() },
                        onStopPreview = { viewModel.stopPreview() },
                        onSynthesizeFile = { viewModel.synthesizeSpeechToFile() },
                        onClearStates = { viewModel.clearGenerationStates() }
                    )
                }
                AppTab.SAVED_FILES -> {
                    SavedFilesScreen(
                        records = savedRecords,
                        playingRecordId = playingRecordId,
                        isPlaying = isPlaying,
                        playbackPosition = playbackPosition,
                        playbackDuration = playbackDuration,
                        onPlayRecord = { viewModel.playAudioRecord(it) },
                        onSeek = { viewModel.seekPlayback(it) },
                        onDeleteRecord = { viewModel.deleteAudioRecord(it) }
                    )
                }
                AppTab.SETTINGS -> {
                    SettingsScreen(
                        ttsInitialized = isTtsInitialized
                    )
                }
            }
        }
    }
}

@Composable
fun StudioBottomNavigation(
    currentTab: AppTab,
    onTabSelected: (AppTab) -> Unit,
    modifier: Modifier = Modifier
) {
    NavigationBar(
        modifier = modifier.testTag("app_bottom_bar"),
        containerColor = MaterialTheme.colorScheme.surfaceVariant,
        tonalElevation = 8.dp
    ) {
        NavigationBarItem(
            selected = currentTab == AppTab.HOME,
            onClick = { onTabSelected(AppTab.HOME) },
            icon = {
                Icon(
                    imageVector = Icons.Default.Home,
                    contentDescription = "Home dashboard tab"
                )
            },
            label = { Text("Home") },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = MaterialTheme.colorScheme.primary,
                selectedTextColor = MaterialTheme.colorScheme.primary,
                indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
            ),
            modifier = Modifier.testTag("nav_item_home")
        )

        NavigationBarItem(
            selected = currentTab == AppTab.EDITOR,
            onClick = { onTabSelected(AppTab.EDITOR) },
            icon = {
                Icon(
                    imageVector = Icons.Default.Mic,
                    contentDescription = "Vocal editor tab"
                )
            },
            label = { Text("Editor") },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = MaterialTheme.colorScheme.primary,
                selectedTextColor = MaterialTheme.colorScheme.primary,
                indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
            ),
            modifier = Modifier.testTag("nav_item_editor")
        )

        NavigationBarItem(
            selected = currentTab == AppTab.SAVED_FILES,
            onClick = { onTabSelected(AppTab.SAVED_FILES) },
            icon = {
                Icon(
                    imageVector = Icons.Default.Folder,
                    contentDescription = "Library recordings tab"
                )
            },
            label = { Text("Saved") },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = MaterialTheme.colorScheme.primary,
                selectedTextColor = MaterialTheme.colorScheme.primary,
                indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
            ),
            modifier = Modifier.testTag("nav_item_saved")
        )

        NavigationBarItem(
            selected = currentTab == AppTab.SETTINGS,
            onClick = { onTabSelected(AppTab.SETTINGS) },
            icon = {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = "Settings developer tab"
                )
            },
            label = { Text("Settings") },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = MaterialTheme.colorScheme.primary,
                selectedTextColor = MaterialTheme.colorScheme.primary,
                indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
            ),
            modifier = Modifier.testTag("nav_item_settings")
        )
    }
}
