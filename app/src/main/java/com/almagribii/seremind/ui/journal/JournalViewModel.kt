package com.almagribii.seremind.ui.journal

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.almagribii.seremind.data.local.SeremindDatabase
import com.almagribii.seremind.data.local.entity.Article
import com.almagribii.seremind.data.local.entity.JournalEntry
import com.almagribii.seremind.data.remote.GeminiApiService
import com.almagribii.seremind.data.local.entity.UserPreference
import com.almagribii.seremind.BuildConfig
import java.util.Calendar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class JournalUiState(
    val entries: List<JournalEntry> = emptyList(),
    val articles: List<Article> = emptyList(),
    val currentPrompt: String? = null,
    val isPromptLoading: Boolean = false,
    val isLoading: Boolean = false,
    val aiJournalEnabled: Boolean = false,
    val latestFeedback: String? = null,
    val processingFeedbackFor: Int? = null
)

class JournalViewModel(application: Application) : AndroidViewModel(application) {
    private val db = SeremindDatabase.getDatabase(application)
    private val journalDao = db.journalEntryDao()
    private val articleDao = db.articleDao()
    private val geminiService = GeminiApiService(BuildConfig.GEMINI_API_KEY_JURNAL)
    private val prefDao = db.userPreferenceDao()

    private val _uiState = MutableStateFlow(JournalUiState())
    val uiState: StateFlow<JournalUiState> = _uiState.asStateFlow()

    init {
        loadJournalData()
    }

    fun loadJournalData() {
        viewModelScope.launch(Dispatchers.IO) {
            refreshJournalData()
        }
    }

    private suspend fun refreshJournalData() {
        _uiState.value = _uiState.value.copy(
            entries = journalDao.getAllEntries(),
            articles = articleDao.getAllArticles(),
            aiJournalEnabled = prefDao.getPreference("ai_journal_enabled") == "true"
        )
    }

    fun setAiJournalEnabled(enabled: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            prefDao.setPreference(UserPreference("ai_journal_enabled", enabled.toString()))
            _uiState.value = _uiState.value.copy(aiJournalEnabled = enabled)
        }
    }

    fun addJournalEntry(
        content: String,
        emotionTag: String?,
        entryType: String = "free",
        aiPrompt: String? = null,
        onComplete: () -> Unit = {}
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val aiEnabled = prefDao.getPreference("ai_journal_enabled") == "true"
            _uiState.value = _uiState.value.copy(
                isLoading = true,
                latestFeedback = null
            )

            val feedbackResult = if (aiEnabled) {
                try {
                    val startOfDay = Calendar.getInstance().apply {
                        set(Calendar.HOUR_OF_DAY, 0)
                        set(Calendar.MINUTE, 0)
                        set(Calendar.SECOND, 0)
                        set(Calendar.MILLISECOND, 0)
                    }.timeInMillis
                    geminiService.generateJournalFeedback(
                        content,
                        db.gateEventDao().getEventsSince(startOfDay)
                    )
                } catch (_: Exception) {
                    GeminiApiService.JournalFeedbackResult(
                        error = "AI tidak dapat membaca data gate hari ini. Jurnal tetap disimpan."
                    )
                }
            } else {
                null
            }

            journalDao.insert(
                JournalEntry(
                    timestamp = System.currentTimeMillis(),
                    content = content,
                    emotionTag = emotionTag,
                    entryType = entryType,
                    aiPrompt = aiPrompt,
                    aiFeedback = feedbackResult?.feedback,
                    aiFeedbackError = feedbackResult?.error
                )
            )
            refreshJournalData()
            _uiState.value = _uiState.value.copy(
                isLoading = false,
                latestFeedback = feedbackResult?.feedback
            )
            kotlinx.coroutines.withContext(Dispatchers.Main) { onComplete() }
        }
    }

    fun requestFeedback(entryId: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            val entry = journalDao.getById(entryId) ?: return@launch
            _uiState.value = _uiState.value.copy(processingFeedbackFor = entryId)
            val startOfDay = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis
            val result = geminiService.generateJournalFeedback(
                entry.content,
                db.gateEventDao().getEventsSince(startOfDay)
            )
            journalDao.update(entry.copy(aiFeedback = result.feedback, aiFeedbackError = result.error))
            _uiState.value = _uiState.value.copy(
                processingFeedbackFor = null,
                latestFeedback = result.feedback
            )
            loadJournalData()
        }
    }

    fun deleteEntry(entry: JournalEntry, onComplete: () -> Unit = {}) {
        viewModelScope.launch(Dispatchers.IO) {
            journalDao.delete(entry)
            loadJournalData()
            kotlinx.coroutines.withContext(Dispatchers.Main) { onComplete() }
        }
    }

    fun fetchNewPrompt() {
        viewModelScope.launch(Dispatchers.IO) {
            fetchNewPromptInternal()
        }
    }

    private suspend fun fetchNewPromptInternal() {
        _uiState.value = _uiState.value.copy(isPromptLoading = true)
        val startOfDay = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        val gateEventsToday = db.gateEventDao().getEventsSince(startOfDay)
        val recentEntries = journalDao.getAllEntries().take(3)

        val prompt = geminiService.generateContextualJournalPrompt(gateEventsToday, recentEntries)
        _uiState.value = _uiState.value.copy(
            currentPrompt = prompt,
            isPromptLoading = false
        )
    }

    fun markArticleAsRead(article: Article) {
        viewModelScope.launch(Dispatchers.IO) {
            val updated = article.copy(isRead = true)
            articleDao.update(updated)
            loadJournalData()
        }
    }
}
