package ru.reset.renplay.ui.navigation

import java.net.URLEncoder
import java.nio.charset.StandardCharsets

sealed class Screen(val route: String) {
    data object Library : Screen("library")

    data object Settings : Screen("settings_graph")
    data object SettingsList : Screen("settings_list")
    data object About : Screen("about")
    data object Appearance : Screen("appearance")
    data object EngineSettings : Screen("engine_settings")
    data object TranslationSettings : Screen("translation_settings")

    data object GameDetails : Screen("game_details/{projectId}") {
        fun createRoute(projectId: String): String {
            return "game_details/$projectId"
        }
    }

    data object FolderPicker : Screen("folder_picker/{requestKey}/{mode}") {
        fun createRoute(requestKey: String, mode: String = "game"): String {
            return "folder_picker/$requestKey/$mode"
        }
    }
}

