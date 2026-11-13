package com.vknxworks.paperctrl.data.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

/**
 * Lenient JSON for everything the server sends: unknown fields are ignored, nulls and missing
 * fields fall back to defaults, so a newer Paperless version never crashes the app.
 */
val ApiJson = Json {
    ignoreUnknownKeys = true
    isLenient = true
    coerceInputValues = true
    explicitNulls = false
    encodeDefaults = true
}

@Serializable
data class TokenResponse(val token: String = "")

/** GET /api/ui_settings/: who you are, your permissions and the server's UI settings. */
@Serializable
data class UiSettingsResponse(
    val user: UiUser = UiUser(),
    val settings: JsonObject = JsonObject(emptyMap()),
    val permissions: List<String> = emptyList(),
)

@Serializable
data class UiUser(
    val id: Int = 0,
    val username: String = "",
    @SerialName("is_staff") val isStaff: Boolean = false,
    @SerialName("is_superuser") val isSuperuser: Boolean = false,
    @SerialName("first_name") val firstName: String? = null,
    @SerialName("last_name") val lastName: String? = null,
    val groups: List<Int> = emptyList(),
) {
    val displayName: String
        get() = listOfNotNull(firstName, lastName).joinToString(" ").trim().ifEmpty { username }
}
