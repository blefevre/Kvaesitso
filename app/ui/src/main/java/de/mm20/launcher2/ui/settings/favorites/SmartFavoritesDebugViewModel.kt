package de.mm20.launcher2.ui.settings.favorites

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.mm20.launcher2.searchable.SavableSearchableRepository
import de.mm20.launcher2.searchable.debug.AppDebugInfo
import de.mm20.launcher2.searchable.debug.AppSuggestionDebugInfo
import de.mm20.launcher2.searchable.debug.ContextDebugInfo
import de.mm20.launcher2.searchable.debug.SmartFavoritesAnalytics
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class SmartFavoritesDebugViewModel : ViewModel(), KoinComponent {
    
    private val repository: SavableSearchableRepository by inject()
    
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()
    
    private val _currentContext = MutableStateFlow<ContextDebugInfo?>(null)
    val currentContext: StateFlow<ContextDebugInfo?> = _currentContext.asStateFlow()
    
    private val _analytics = MutableStateFlow<SmartFavoritesAnalytics?>(null)
    val analytics: StateFlow<SmartFavoritesAnalytics?> = _analytics.asStateFlow()
    
    private val _allAppsDebugInfo = MutableStateFlow<List<AppDebugInfo>>(emptyList())
    val allAppsDebugInfo: StateFlow<List<AppDebugInfo>> = _allAppsDebugInfo.asStateFlow()
    
    // Use reactive app suggestions that update with context changes
    val appSuggestions: StateFlow<List<AppSuggestionDebugInfo>> = repository.getReactiveAppSuggestions()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
    
    private val _selectedApp = MutableStateFlow<AppDebugInfo?>(null)
    val selectedApp: StateFlow<AppDebugInfo?> = _selectedApp.asStateFlow()
    
    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()
    
    private val _knnConfiguration = MutableStateFlow<Pair<Int, Double>>(Pair(10, 0.7))
    val knnConfiguration: StateFlow<Pair<Int, Double>> = _knnConfiguration.asStateFlow()
    
    init {
        loadDebugData()
    }
    
    fun loadDebugData() {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            
            try {
                // Load all debug data concurrently
                val currentContext = repository.getCurrentContextDebugInfo()
                val analytics = repository.getSmartFavoritesAnalytics()
                val allApps = repository.getAllAppsDebugInfo()
                val knnConfig = repository.getKnnConfiguration()
                
                _currentContext.value = currentContext
                _analytics.value = analytics
                _allAppsDebugInfo.value = allApps
                _knnConfiguration.value = knnConfig
                
            } catch (e: Exception) {
                _errorMessage.value = "Failed to load debug data: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }
    
    fun selectApp(appDebugInfo: AppDebugInfo) {
        _selectedApp.value = appDebugInfo
    }
    
    fun clearSelectedApp() {
        _selectedApp.value = null
    }
    
    fun refreshCurrentContext() {
        viewModelScope.launch {
            try {
                val context = repository.getCurrentContextDebugInfo()
                _currentContext.value = context
                
                // Note: appSuggestions are now reactive and will update automatically
                // when context changes via repository.getReactiveAppSuggestions()
            } catch (e: Exception) {
                _errorMessage.value = "Failed to refresh context: ${e.message}"
            }
        }
    }
    
    fun dismissError() {
        _errorMessage.value = null
    }
    
    fun getAppsByPattern(pattern: String): List<AppDebugInfo> {
        return _allAppsDebugInfo.value.filter { app ->
            app.patterns.any { it.description.contains(pattern, ignoreCase = true) }
        }
    }
    
    fun getTopAppsForCurrentContext(): List<AppDebugInfo> {
        val currentCtx = _currentContext.value?.currentContext ?: return emptyList()
        
        return _allAppsDebugInfo.value
            .filter { it.contextHistory.isNotEmpty() }
            .sortedByDescending { app ->
                app.contextHistory.maxOfOrNull { historicalContext ->
                    currentCtx.similarityScore(historicalContext)
                } ?: 0.0
            }
            .take(10)
    }
}