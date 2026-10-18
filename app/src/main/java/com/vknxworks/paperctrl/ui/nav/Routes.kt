package com.vknxworks.paperctrl.ui.nav

import kotlinx.serialization.Serializable

// Tabs
@Serializable data object InboxRoute
@Serializable data object DocumentsRoute
@Serializable data object ScanRoute
@Serializable data object OrganiseRoute

// Opened from the profile picture
@Serializable data object YouRoute
@Serializable data object SettingsRoute
