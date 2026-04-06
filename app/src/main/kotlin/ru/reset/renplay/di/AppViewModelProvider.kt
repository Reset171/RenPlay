package ru.reset.renplay.di

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import ru.reset.renplay.RenPlayApplication
import ru.reset.renplay.ui.picker.FolderPickerViewModel
import ru.reset.renplay.ui.library.LibraryViewModel
import ru.reset.renplay.ui.settings.SettingsViewModel

object AppViewModelProvider {
    val Factory = viewModelFactory {
        initializer {
            LibraryViewModel(
                renPlayApplication(),
                renPlayApplication().container.sharedPreferences,
                renPlayApplication().container.engineManager
            )
        }

        initializer {
            SettingsViewModel(
                renPlayApplication().container.sharedPreferences,
                renPlayApplication().container.engineManager
            )
        }

        initializer {
            FolderPickerViewModel(renPlayApplication())
        }
    }
}

fun CreationExtras.renPlayApplication(): RenPlayApplication =
    (this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as RenPlayApplication)

