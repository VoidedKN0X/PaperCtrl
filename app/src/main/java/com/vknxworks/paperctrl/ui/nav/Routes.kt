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
@Serializable data object ServersRoute
@Serializable data object AddServerRoute

@Serializable data object QueueRoute

// Scanner
@Serializable data class ScannerRoute(val retakeId: String? = null)
@Serializable data object ScanReviewRoute
@Serializable data class PageEditRoute(val pageId: String)

// Documents
@Serializable data class DocumentRoute(val id: Int)

// Organise
@Serializable data class ObjectListRoute(val kind: String)
@Serializable data object TrashRoute
@Serializable data object NtfyRoute
