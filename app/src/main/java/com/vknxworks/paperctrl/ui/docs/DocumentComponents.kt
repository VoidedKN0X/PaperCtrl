package com.vknxworks.paperctrl.ui.docs

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.StickyNote2
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.vknxworks.paperctrl.container
import com.vknxworks.paperctrl.data.api.Document
import com.vknxworks.paperctrl.data.api.Tag
import com.vknxworks.paperctrl.data.metadata.ServerMetadata
import com.vknxworks.paperctrl.ui.theme.Gutter
import com.vknxworks.paperctrl.ui.upload.formatDate
import com.vknxworks.paperctrl.ui.upload.parseColor

/** Tags, correspondents and the rest for the active server, loaded once and kept for a few minutes. */
@Composable
fun rememberMetadata(refreshKey: Any? = null): ServerMetadata {
    val c = LocalContext.current.container
    val session by c.sessions.active.collectAsStateWithLifecycle()
    val id = session?.server?.id
    var data by remember(id) { mutableStateOf(id?.let { c.metadata.cached(it) } ?: ServerMetadata()) }
    LaunchedEffect(id, refreshKey) {
        val s = session ?: return@LaunchedEffect
        runCatching { c.metadata.load(s.server.id, s.client, force = refreshKey != null) }.getOrNull()?.let { data = it }
    }
    return data
}

/** The document's thumbnail from the server (kept in memory only). */
@Composable
fun Thumbnail(doc: Document, modifier: Modifier = Modifier) {
    val c = LocalContext.current.container
    val session by c.sessions.active.collectAsStateWithLifecycle()
    val url = session?.client?.url("documents/${doc.id}/thumb/")?.toString()
    Box(modifier.background(MaterialTheme.colorScheme.surfaceContainerHighest), contentAlignment = Alignment.Center) {
        Icon(Icons.Outlined.Description, null, tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
        if (url != null) {
            AsyncImage(
                model = url,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                alignment = Alignment.TopCenter,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
fun TagChip(tag: Tag, small: Boolean = false) {
    val bg = parseColor(tag.color) ?: MaterialTheme.colorScheme.secondaryContainer
    val fg = parseColor(tag.textColor) ?: if (bg.luminance() > 0.5f) Color.Black else Color.White
    Surface(shape = CircleShape, color = bg) {
        Text(
            tag.name,
            color = fg,
            style = if (small) MaterialTheme.typography.labelSmall else MaterialTheme.typography.labelMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = if (small) 7.dp else 10.dp, vertical = if (small) 1.dp else 3.dp),
        )
    }
}

@Composable
fun TagRow(tagIds: List<Int>, md: ServerMetadata, max: Int = 4, small: Boolean = true) {
    val tags = tagIds.mapNotNull { md.tag(it) }
    if (tags.isEmpty()) return
    FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        tags.take(max).forEach { TagChip(it, small) }
        if (tags.size > max) Text("+${tags.size - max}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Paperless marks search matches with <span class="match">; shows them bold. */
fun highlights(html: String): AnnotatedString = buildAnnotatedString {
    var rest = html.replace(Regex("<(?!/?span)[^>]*>"), "")
    while (true) {
        val start = rest.indexOf("<span")
        if (start < 0) { append(rest.replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">")); break }
        append(rest.substring(0, start))
        val open = rest.indexOf('>', start)
        val close = rest.indexOf("</span>", open)
        if (open < 0 || close < 0) { append(rest.substring(start)); break }
        val s = length
        append(rest.substring(open + 1, close))
        addStyle(SpanStyle(fontWeight = FontWeight.Bold, background = Color(0x55F0C27A)), s, length)
        rest = rest.substring(close + 7)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DocumentRow(
    doc: Document,
    md: ServerMetadata,
    selected: Boolean,
    selecting: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier
            .fillMaxWidth()
            .background(if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f) else MaterialTheme.colorScheme.background)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = Gutter, vertical = 10.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box {
            Thumbnail(doc, Modifier.width(56.dp).height(74.dp).clip(RoundedCornerShape(8.dp)))
            if (selecting) {
                Icon(
                    Icons.Filled.CheckCircle,
                    null,
                    tint = if (selected) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.7f),
                    modifier = Modifier.align(Alignment.TopStart).padding(3.dp).size(20.dp).background(Color.Black.copy(alpha = 0.25f), CircleShape),
                )
            }
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(doc.title.ifBlank { doc.originalFileName ?: "#${doc.id}" }, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            val meta = listOfNotNull(
                md.correspondent(doc.correspondent)?.name,
                md.documentType(doc.documentType)?.name,
                doc.createdDate?.let(::formatDate),
                doc.asn?.let { "ASN $it" },
            ).joinToString("  ·  ")
            if (meta.isNotEmpty()) Text(meta, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
            doc.searchHit?.highlights?.takeIf { it.isNotBlank() }?.let {
                Text(highlights(it), style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) { TagRow(doc.tags, md) }
                if (doc.noteCount > 0) {
                    Icon(Icons.Outlined.StickyNote2, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(" ${doc.noteCount}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DocumentCard(
    doc: Document,
    md: ServerMetadata,
    selected: Boolean,
    selecting: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    Column(
        Modifier
            .clip(MaterialTheme.shapes.medium)
            .background(if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.16f) else MaterialTheme.colorScheme.surfaceContainer)
            .border(if (selected) 2.dp else 0.dp, MaterialTheme.colorScheme.primary, MaterialTheme.shapes.medium)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
    ) {
        Box {
            Thumbnail(doc, Modifier.fillMaxWidth().aspectRatio(0.75f))
            if (selecting) {
                Icon(
                    Icons.Filled.CheckCircle,
                    null,
                    tint = if (selected) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.7f),
                    modifier = Modifier.align(Alignment.TopStart).padding(6.dp).size(24.dp).background(Color.Black.copy(alpha = 0.25f), CircleShape),
                )
            }
        }
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(doc.title.ifBlank { "#${doc.id}" }, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(
                listOfNotNull(md.correspondent(doc.correspondent)?.name, doc.createdDate?.let(::formatDate)).joinToString("  ·  "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            TagRow(doc.tags, md, max = 2)
        }
    }
}
