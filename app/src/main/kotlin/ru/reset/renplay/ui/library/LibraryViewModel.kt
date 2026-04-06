package ru.reset.renplay.ui.library

import android.app.Application
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import ru.reset.renplay.domain.models.Project
import java.io.File

private const val KEY_PROJECTS_CACHE = "projects_cache_json"
private const val KEY_LAST_PROJECT = "last_project"
private const val KEY_IS_GRID_VIEW = "is_grid_view"
private const val KEY_SORT_ORDER = "sort_order"

data class PlaytimeStats(
    val totalMillis: Long,
    val todayMillis: Long
)

class LibraryViewModel(application: Application, private val prefs: SharedPreferences, val engineManager: ru.reset.renplay.domain.EngineManager) : AndroidViewModel(application) {

    private val _projectsList = MutableStateFlow<List<Project>>(emptyList())
    val projectsList = _projectsList.asStateFlow()

    private val _selectedProject = MutableStateFlow<Project?>(null)
    val selectedProject = _selectedProject.asStateFlow()
    
    private val _iconCache = MutableStateFlow<Map<String, Bitmap>>(emptyMap())
    val iconCache = _iconCache.asStateFlow()

    private val _isGridView = MutableStateFlow(prefs.getBoolean(KEY_IS_GRID_VIEW, true))
    val isGridView = _isGridView.asStateFlow()

    enum class SortOrder { NEWEST_FIRST, ALPHABETICAL, MANUAL }

    private val _searchQuery = MutableStateFlow("")
    val searchQuery = _searchQuery.asStateFlow()

    private val _sortOrder = MutableStateFlow(
        SortOrder.valueOf(prefs.getString(KEY_SORT_ORDER, SortOrder.NEWEST_FIRST.name) ?: SortOrder.NEWEST_FIRST.name)
    )
    val sortOrder = _sortOrder.asStateFlow()

    val filteredProjects = combine(_projectsList, _searchQuery, _sortOrder) { projects, query, sort ->
        val filtered = if (query.isBlank()) projects else projects.filter { it.name.contains(query, ignoreCase = true) }
        when (sort) {
            SortOrder.ALPHABETICAL -> filtered.sortedBy { it.name.lowercase() }
            SortOrder.NEWEST_FIRST -> filtered.reversed()
            SortOrder.MANUAL -> filtered
        }
    }.flowOn(Dispatchers.Default)
     .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    private val jsonDecoder = Json { ignoreUnknownKeys = true }

    fun moveProject(fromId: String, toId: String) {
        val list = _projectsList.value.toMutableList()
        val fromIndex = list.indexOfFirst { it.id == fromId }
        val toIndex = list.indexOfFirst { it.id == toId }
        if (fromIndex != -1 && toIndex != -1 && fromIndex != toIndex) {
            val item = list.removeAt(fromIndex)
            list.add(toIndex, item)
            _projectsList.value = list
            saveProjects(list)
        }
    }

    init {
        loadProjects()
    }

    fun refreshProjects() {
        loadProjects()
    }

    private fun loadProjects() {
        val cachedJson = prefs.getString(KEY_PROJECTS_CACHE, null)
        if (!cachedJson.isNullOrEmpty()) {
            try {
                val projects = jsonDecoder.decodeFromString<List<Project>>(cachedJson)
                _projectsList.value = projects
                
                val lastProjectId = prefs.getString(KEY_LAST_PROJECT, null)
                val projectToSelect = projects.find { it.id == lastProjectId } ?: projects.firstOrNull()
                _selectedProject.value = projectToSelect

                updateIconCache(projects)
            } catch (e: Exception) {
                _projectsList.value = emptyList()
            }
        }
    }

    fun addProject(name: String, version: String, path: String, customIconPath: String?, engineVersion: String?, application: Application) {
        viewModelScope.launch(Dispatchers.IO) {
            var autoIconPath: String? = null
            if (customIconPath == null) {
                val assets = ru.reset.renplay.utils.GameAssetExtractor.getGameAssets(application, path)
                autoIconPath = assets.iconPath
            }

            val project = Project(
                id = java.util.UUID.randomUUID().toString(),
                name = name,
                path = path,
                version = version,
                iconPath = autoIconPath,
                customIconPath = customIconPath,
                engineVersion = engineVersion
            )

            val newList = _projectsList.value.toMutableList().apply { add(project) }
            
            withContext(Dispatchers.Main) {
                _projectsList.value = newList
                onProjectSelected(project)
                saveProjects(newList)
            }
            updateIconCache(newList)
        }
    }

    fun removeProject(project: Project) {
        val newList = _projectsList.value.filter { it.id != project.id }
        _projectsList.value = newList
        if (_selectedProject.value?.id == project.id) {
            _selectedProject.value = null
            prefs.edit().remove(KEY_LAST_PROJECT).apply()
        }
        saveProjects(newList)
    }

    fun onProjectSelected(project: Project?) {
        _selectedProject.value = project
        prefs.edit().putString(KEY_LAST_PROJECT, project?.id).apply()
    }
    
    private fun updateIconCache(projects: List<Project>) {
        viewModelScope.launch(Dispatchers.IO) {
            val newCache = mutableMapOf<String, Bitmap>()
            projects.forEach { project ->
                val pathToLoad = project.customIconPath ?: project.iconPath
                if (pathToLoad != null) {
                    try {
                        if (!_iconCache.value.containsKey(pathToLoad)) {
                            BitmapFactory.decodeFile(pathToLoad)?.let { bitmap ->
                                newCache[pathToLoad] = bitmap
                            }
                        }
                    } catch (e: Exception) {
                    }
                }
            }
            if (newCache.isNotEmpty()) {
                _iconCache.value = _iconCache.value + newCache
            }
        }
    }

    fun updateProject(updatedProject: Project) {
        val newList = _projectsList.value.map { if (it.id == updatedProject.id) updatedProject else it }
        _projectsList.value = newList
        if (_selectedProject.value?.id == updatedProject.id) {
            _selectedProject.value = updatedProject
        }
        updateIconCache(newList)
        saveProjects(newList)
    }

    private fun saveProjects(projects: List<Project>) {
        try {
            val serializedProjects = Json.encodeToString(projects)
            prefs.edit().putString(KEY_PROJECTS_CACHE, serializedProjects).apply()
        } catch (e: Exception) {
        }
    }

    fun toggleGridView() {
        val newValue = !_isGridView.value
        _isGridView.value = newValue
        prefs.edit().putBoolean(KEY_IS_GRID_VIEW, newValue).apply()
    }

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun updateSortOrder(order: SortOrder) {
        _sortOrder.value = order
        prefs.edit().putString(KEY_SORT_ORDER, order.name).apply()
    }

    fun getPlaytimeStats(projectPath: String): PlaytimeStats {
        return try {
            val context = getApplication<Application>()
            val statFile = File(context.filesDir, "playtime_${projectPath.hashCode()}.stat")
            if (statFile.exists()) {
                val content = statFile.readText().trim()
                val parts = content.split(":")
                if (parts.size >= 3) {
                    val total = parts[0].toLongOrNull() ?: 0L
                    var today = parts[1].toLongOrNull() ?: 0L
                    val lastDate = parts[2]
                    
                    val currentDate = java.text.SimpleDateFormat("yyyyMMdd", java.util.Locale.US).format(java.util.Date())
                    if (currentDate != lastDate) {
                        today = 0L
                    }
                    PlaytimeStats(total, today)
                } else PlaytimeStats(0L, 0L)
            } else PlaytimeStats(0L, 0L)
        } catch (e: Exception) {
            PlaytimeStats(0L, 0L)
        }
    }
}
