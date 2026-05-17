package com.easybox.app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.easybox.app.data.model.AppItem
import com.easybox.app.data.repository.AppRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class HomeViewModel(private val repository: AppRepository) : ViewModel() {

    val appItems: StateFlow<List<AppItem>> = repository.appItems
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val nickname: StateFlow<String> = repository.nickname
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")

    private val _isReorderMode = MutableStateFlow(false)
    val isReorderMode: StateFlow<Boolean> = _isReorderMode.asStateFlow()

    private val _showNicknameDialog = MutableStateFlow(false)
    val showNicknameDialog: StateFlow<Boolean> = _showNicknameDialog.asStateFlow()
    private var promptDismissed = false

    init {
        viewModelScope.launch {
            repository.initializeBuiltInApps()
        }
    }

    fun toggleReorderMode() {
        _isReorderMode.value = !_isReorderMode.value
    }

    fun showNicknamePrompt() {
        if (!promptDismissed && nickname.value.isBlank()) {
            _showNicknameDialog.value = true
        }
    }

    fun dismissNicknameDialog() {
        _showNicknameDialog.value = false
        promptDismissed = true
        if (nickname.value.isBlank()) {
            viewModelScope.launch { repository.setNickname("玩家") }
        }
    }

    fun setNickname(name: String) {
        viewModelScope.launch {
            repository.setNickname(name.trim())
            _showNicknameDialog.value = false
        }
    }

    fun updateSortOrders(items: List<AppItem>) {
        val reordered = items.mapIndexed { index, item ->
            item.copy(sortOrder = index)
        }
        viewModelScope.launch {
            repository.updateSortOrders(reordered)
        }
    }

    fun addPlugin(item: AppItem) {
        viewModelScope.launch {
            repository.addAppItem(item)
        }
    }

    fun removeApp(id: String) {
        viewModelScope.launch {
            repository.deleteAppItem(id)
        }
    }

    class Factory(private val repository: AppRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return HomeViewModel(repository) as T
        }
    }
}
