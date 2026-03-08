package ru.reset.renplay.ui.picker

import android.os.Environment
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

data class PickerItem(
    val file: File,
    val isDirectory: Boolean,
    val name: String
)

data class FolderState(
    val path: File,
    val items: List<PickerItem> = emptyList(),
    val transitionId: Long = 0
)

class FolderPickerViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(FolderState(path = Environment.getExternalStorageDirectory()))
    val uiState = _uiState.asStateFlow()

    private val _hasPermission = MutableStateFlow(true)
    val hasPermission = _hasPermission.asStateFlow()

    private var transitionCounter = 0L
    private var currentRequestKey = ""

    fun initMode(requestKey: String) {
        if (currentRequestKey != requestKey) {
            currentRequestKey = requestKey
            loadDirectory(Environment.getExternalStorageDirectory())
        }
    }

    fun navigateTo(file: File) {
        if (file.isDirectory) {
            loadDirectory(file)
        }
    }

    fun navigateUp() {
        val parent = _uiState.value.path.parentFile
        if (parent != null && parent.canRead()) {
            loadDirectory(parent)
        }
    }

    fun canNavigateUp(): Boolean {
        val parent = _uiState.value.path.parentFile
        return parent != null && parent.canRead()
    }

    private fun loadDirectory(dir: File) {
        viewModelScope.launch {
            if (!dir.canRead()) {
                _hasPermission.value = false
                transitionCounter++
                _uiState.value = FolderState(dir, emptyList(), transitionCounter)
                return@launch
            }

            _hasPermission.value = true
            
            val content = withContext(Dispatchers.IO) {
                try {
                    val files = dir.listFiles() ?: emptyArray()
                    
                    files.filter { file ->
                        file.isDirectory
                    }.map { file ->
                        PickerItem(
                            file = file,
                            isDirectory = file.isDirectory,
                            name = file.name
                        )
                    }.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
                } catch (e: Exception) {
                    emptyList()
                }
            }
            
            transitionCounter++
            _uiState.value = FolderState(dir, content, transitionCounter)
        }
    }
}