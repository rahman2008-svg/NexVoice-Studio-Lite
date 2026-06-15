package com.example.ui

import android.content.Context
import android.media.MediaPlayer
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.AudioRecord
import com.example.data.AudioRepository
import com.example.tts.TtsManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File

enum class AppTab {
    HOME,
    EDITOR,
    SAVED_FILES,
    SETTINGS
}

class MainViewModel(
    private val repository: AudioRepository,
    private val context: Context
) : ViewModel() {

    // Bottom Navigation tab state
    private val _currentTab = MutableStateFlow(AppTab.HOME)
    val currentTab: StateFlow<AppTab> = _currentTab.asStateFlow()

    // TTS management
    private var ttsManager: TtsManager? = null
    private val _isTtsInitialized = MutableStateFlow(false)
    val isTtsInitialized: StateFlow<Boolean> = _isTtsInitialized.asStateFlow()

    // Editor Screen states
    private val _editorText = MutableStateFlow("")
    val editorText: StateFlow<String> = _editorText.asStateFlow()

    private val _selectedStyle = MutableStateFlow("Female") // Male, Female, Child, Robot
    val selectedStyle: StateFlow<String> = _selectedStyle.asStateFlow()

    private val _pitch = MutableStateFlow(1.0f)
    val pitch: StateFlow<Float> = _pitch.asStateFlow()

    private val _speed = MutableStateFlow(1.0f)
    val speed: StateFlow<Float> = _speed.asStateFlow()

    private val _fileNameInput = MutableStateFlow("")
    val fileNameInput: StateFlow<String> = _fileNameInput.asStateFlow()

    private val _isGenerating = MutableStateFlow(false)
    val isGenerating: StateFlow<Boolean> = _isGenerating.asStateFlow()

    private val _generationSuccessMessage = MutableStateFlow<String?>(null)
    val generationSuccessMessage: StateFlow<String?> = _generationSuccessMessage.asStateFlow()

    private val _generationError = MutableStateFlow<String?>(null)
    val generationError: StateFlow<String?> = _generationError.asStateFlow()

    // Saved Records Flow
    val savedRecords: StateFlow<List<AudioRecord>> = repository.allRecords
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    // Playback Engine State
    private var mediaPlayer: MediaPlayer? = null
    private var playbackProgressJob: Job? = null

    private val _playingRecordId = MutableStateFlow<Int?>(null)
    val playingRecordId: StateFlow<Int?> = _playingRecordId.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _playbackPosition = MutableStateFlow(0)
    val playbackPosition: StateFlow<Int> = _playbackPosition.asStateFlow()

    private val _playbackDuration = MutableStateFlow(0)
    val playbackDuration: StateFlow<Int> = _playbackDuration.asStateFlow()

    // Toast/Snack Events
    private val _uiNotification = MutableStateFlow<String?>(null)
    val uiNotification: StateFlow<String?> = _uiNotification.asStateFlow()

    init {
        initializeTts()
    }

    private fun initializeTts() {
        ttsManager = TtsManager(context) { success ->
            _isTtsInitialized.value = success
        }
    }

    fun selectTab(tab: AppTab) {
        _currentTab.value = tab
    }

    // Editor parameters actions
    fun updateEditorText(text: String) {
        _editorText.value = text
    }

    fun updateSelectedStyle(style: String) {
        _selectedStyle.value = style
    }

    fun updatePitch(value: Float) {
        _pitch.value = value
    }

    fun updateSpeed(value: Float) {
        _speed.value = value
    }

    fun updateFileNameInput(value: String) {
        _fileNameInput.value = value
    }

    fun clearNotification() {
        _uiNotification.value = null
    }

    fun clearGenerationStates() {
        _generationSuccessMessage.value = null
        _generationError.value = null
    }

    // Controls for Preview (speak out loud)
    fun playPreview() {
        val text = _editorText.value
        if (text.isBlank()) {
            _uiNotification.value = "Please enter some text to preview."
            return
        }
        ttsManager?.speak(
            text = text,
            voiceStyle = _selectedStyle.value,
            pitch = _pitch.value,
            speed = _speed.value
        )
    }

    fun stopPreview() {
        ttsManager?.stop()
    }

    // Primary audio files synthesizer exporter
    fun synthesizeSpeechToFile() {
        val text = _editorText.value
        if (text.trim().isEmpty()) {
            _generationError.value = "Vocal text cannot be empty!"
            return
        }

        val customTitle = _fileNameInput.value.trim()
        val title = if (customTitle.isEmpty()) "Vocal Synthesis" else customTitle
        _isGenerating.value = true
        _generationError.value = null
        _generationSuccessMessage.value = null

        viewModelScope.launch {
            try {
                // Ensure output subdirectory exists
                val audioDirectory = File(context.filesDir, "saved_audio")
                if (!audioDirectory.exists()) {
                    audioDirectory.mkdirs()
                }

                val safeTitle = title.replace("[^a-zA-Z0-9]".toRegex(), "_")
                val filename = "NexVoice_${safeTitle}_${System.currentTimeMillis()}.wav"
                val outputFile = File(audioDirectory, filename)
                val utteranceId = "SYNTH_${System.currentTimeMillis()}"

                ttsManager?.synthesizeToFile(
                    text = text,
                    voiceStyle = _selectedStyle.value,
                    pitch = _pitch.value,
                    speed = _speed.value,
                    outputFile = outputFile,
                    utteranceId = utteranceId,
                    onStart = {
                        _isGenerating.value = true
                    },
                    onComplete = { file ->
                        viewModelScope.launch {
                            val duration = getAudioDuration(file)
                            val record = AudioRecord(
                                title = title,
                                text = text,
                                filePath = file.absolutePath,
                                voiceStyle = _selectedStyle.value,
                                pitch = _pitch.value,
                                speed = _speed.value,
                                createdAt = System.currentTimeMillis(),
                                durationMs = duration
                            )
                            repository.insert(record)
                            
                            _isGenerating.value = false
                            _generationSuccessMessage.value = "Successfully generated voice file: \"$title\""
                            // Clear inputs on success
                            _editorText.value = ""
                            _fileNameInput.value = ""
                        }
                    },
                    onError = { errorMsg ->
                        _isGenerating.value = false
                        _generationError.value = errorMsg
                    }
                )
            } catch (e: Exception) {
                _isGenerating.value = false
                _generationError.value = "Failed to export: ${e.message}"
            }
        }
    }

    private fun getAudioDuration(file: File): Long {
        return try {
            val retriever = MediaMetadataRetriever()
            retriever.setDataSource(file.absolutePath)
            val time = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            retriever.release()
            time?.toLong() ?: 0L
        } catch (e: Exception) {
            Log.e("MainViewModel", "Could not capture media duration: ${e.message}")
            0L
        }
    }

    // Playback Engine Implementation
    fun playAudioRecord(record: AudioRecord) {
        if (_playingRecordId.value == record.id) {
            // Already active, toggle play states
            if (_isPlaying.value) {
                pausePlayback()
            } else {
                resumePlayback()
            }
            return
        }

        // Release current active player
        stopPlayback()

        val audioFile = File(record.filePath)
        if (!audioFile.exists()) {
            _uiNotification.value = "Audio file was removed from storage!"
            viewModelScope.launch {
                repository.deleteById(record.id)
            }
            return
        }

        try {
            mediaPlayer = MediaPlayer().apply {
                setDataSource(context, Uri.fromFile(audioFile))
                prepare()
                _playingRecordId.value = record.id
                _playbackDuration.value = duration
                _playbackPosition.value = 0
                _isPlaying.value = true
                start()
                startPlaybackUpdates()

                setOnCompletionListener {
                    _isPlaying.value = false
                    _playbackPosition.value = 0
                    _playingRecordId.value = null
                    playbackProgressJob?.cancel()
                }
            }
        } catch (e: Exception) {
            _uiNotification.value = "Error playing media: ${e.message}"
            Log.e("MainViewModel", "Failed playing path: ${e.message}")
        }
    }

    fun seekPlayback(param: Float) {
        mediaPlayer?.let { player ->
            val position = (param * _playbackDuration.value).toInt()
            player.seekTo(position)
            _playbackPosition.value = position
        }
    }

    private fun resumePlayback() {
        mediaPlayer?.let { player ->
            player.start()
            _isPlaying.value = true
            startPlaybackUpdates()
        }
    }

    fun pausePlayback() {
        mediaPlayer?.let { player ->
            if (player.isPlaying) {
                player.pause()
            }
        }
        _isPlaying.value = false
        playbackProgressJob?.cancel()
    }

    fun stopPlayback() {
        playbackProgressJob?.cancel()
        _isPlaying.value = false
        _playingRecordId.value = null
        _playbackPosition.value = 0
        mediaPlayer?.let { player ->
            try {
                if (player.isPlaying) {
                    player.stop()
                }
                player.release()
            } catch (e: Exception) {
                Log.e("MainViewModel", "Error cleaning MediaPlayer: ${e.message}")
            }
        }
        mediaPlayer = null
    }

    fun deleteAudioRecord(record: AudioRecord) {
        viewModelScope.launch {
            if (_playingRecordId.value == record.id) {
                stopPlayback()
            }
            // Delete actual raw file from local storage
            try {
                val file = File(record.filePath)
                if (file.exists()) {
                    file.delete()
                }
            } catch (e: Exception) {
                Log.e("MainViewModel", "Error deleting file: ${e.message}")
            }
            repository.deleteById(record.id)
            _uiNotification.value = "Deleted file \"${record.title}\""
        }
    }

    private fun startPlaybackUpdates() {
        playbackProgressJob?.cancel()
        playbackProgressJob = viewModelScope.launch {
            while (_isPlaying.value) {
                mediaPlayer?.let { player ->
                    if (player.isPlaying) {
                        _playbackPosition.value = player.currentPosition
                    }
                }
                delay(250)
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        stopPlayback()
        ttsManager?.shutdown()
        ttsManager = null
    }
}

class MainViewModelFactory(
    private val repository: AudioRepository,
    private val context: Context
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(MainViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return MainViewModel(repository, context) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
