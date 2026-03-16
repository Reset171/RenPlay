package ru.reset.renplay.domain.models

import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
data class Project(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val path: String = "",
    val version: String = "",
    val iconPath: String? = null,
    val customIconPath: String? = null
)
