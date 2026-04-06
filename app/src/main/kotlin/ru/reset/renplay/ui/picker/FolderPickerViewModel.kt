package ru.reset.renplay.ui.picker

import android.os.Environment
import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ru.reset.renplay.utils.GameAssetExtractor
import java.io.File

data class PickerItem(
    val file: File,
    val isDirectory: Boolean,
    val name: String,
    val isGame: Boolean = false,
    val iconPath: String? = null,
    val backgroundPath: String? = null
)

data class FolderState(
    val path: File,
    val items: List<PickerItem> = emptyList(),
    val transitionId: Long = 0,
    val isValidGameFolder: Boolean = false
)

class FolderPickerViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(FolderState(path = Environment.getExternalStorageDirectory()))
    val uiState = _uiState.asStateFlow()

    private val _hasPermission = MutableStateFlow(true)
    val hasPermission = _hasPermission.asStateFlow()

    private var transitionCounter = 0L
    private var currentRequestKey = ""
    private var currentMode = "game"

    fun initMode(requestKey: String, mode: String) {
        if (currentRequestKey != requestKey || currentMode != mode) {
            currentRequestKey = requestKey
            currentMode = mode
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
            
            val isValidGame = File(dir, "game").isDirectory
            val content = withContext(Dispatchers.IO) {
                try {
                    val files = dir.listFiles() ?: emptyArray()

                    files.filter { file ->
                        if (file.isDirectory) true
                        else if (currentMode == "zip") {
                            if (file.extension.lowercase() == "zip") {
                                try {
                                    java.util.zip.ZipFile(file).use { zip ->
                                        zip.getEntry("renplay-plugin.txt") != null
                                    }
                                } catch (e: Exception) { false }
                            } else false
                        }
                        else if (currentMode == "image") file.extension.lowercase() in listOf("png", "jpg", "jpeg", "webp")
                        else false
                    }.map { file ->
                        val isGameDir = currentMode == "game" && file.isDirectory && File(file, "game").isDirectory
                        PickerItem(
                            file = file,
                            isDirectory = file.isDirectory,
                            name = file.name,
                            isGame = isGameDir
                        )
                    }.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
                } catch (e: Exception) {
                    emptyList()
                }
            }

            transitionCounter++
            _uiState.value = FolderState(dir, content, transitionCounter, isValidGame)

            val gamesToProcess = content.filter { it.isGame }
            if (gamesToProcess.isNotEmpty()) {
                viewModelScope.launch(Dispatchers.IO) {
                    for (gameItem in gamesToProcess) {
                        if (_uiState.value.transitionId != transitionCounter) break

                        val assets = GameAssetExtractor.getGameAssets(getApplication(), gameItem.file.absolutePath)

                        val currentState = _uiState.value
                        if (currentState.transitionId == transitionCounter) {
                            val updatedItems = currentState.items.map {
                                if (it.file.absolutePath == gameItem.file.absolutePath) {
                                    it.copy(iconPath = assets.iconPath, backgroundPath = assets.backgroundPath)
                                } else it
                            }
                            _uiState.value = currentState.copy(items = updatedItems)
                        }
                    }
                }
            }
        }
    }
}
