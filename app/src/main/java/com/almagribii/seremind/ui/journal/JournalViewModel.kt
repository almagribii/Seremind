package com.almagribii.seremind.ui.journal

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.almagribii.seremind.data.local.SeremindDatabase
import com.almagribii.seremind.data.local.entity.Article
import com.almagribii.seremind.data.local.entity.JournalEntry
import com.almagribii.seremind.data.remote.GeminiApiService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class JournalUiState(
    val entries: List<JournalEntry> = emptyList(),
    val articles: List<Article> = emptyList(),
    val currentPrompt: String? = null,
    val isLoading: Boolean = false
)

class JournalViewModel(application: Application) : AndroidViewModel(application) {
    private val db = SeremindDatabase.getDatabase(application)
    private val journalDao = db.journalEntryDao()
    private val articleDao = db.articleDao()
    private val geminiService = GeminiApiService()

    private val _uiState = MutableStateFlow(JournalUiState())
    val uiState: StateFlow<JournalUiState> = _uiState.asStateFlow()

    init {
        loadJournalData()
    }

    fun loadJournalData() {
        viewModelScope.launch(Dispatchers.IO) {
            val entries = journalDao.getAllEntries()
            val articles = articleDao.getAllArticles()
            _uiState.value = _uiState.value.copy(entries = entries, articles = articles)
        }
    }

    fun addJournalEntry(content: String, emotionTag: String?, entryType: String = "free", aiPrompt: String? = null) {
        viewModelScope.launch(Dispatchers.IO) {
            val entry = JournalEntry(
                timestamp = System.currentTimeMillis(),
                content = content,
                emotionTag = emotionTag,
                entryType = entryType,
                aiPrompt = aiPrompt
            )
            journalDao.insert(entry)
            loadJournalData()
        }
    }

    fun fetchNewPrompt() {
        viewModelScope.launch(Dispatchers.IO) {
            val prompt = geminiService.generateJournalPrompt()
            _uiState.value = _uiState.value.copy(currentPrompt = prompt)
        }
    }

    fun markArticleAsRead(article: Article) {
        viewModelScope.launch(Dispatchers.IO) {
            val updated = article.copy(isRead = true)
            articleDao.update(updated)
            loadJournalData()
        }
    }
}
