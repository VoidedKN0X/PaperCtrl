package com.vknxworks.paperctrl.ui.organise

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vknxworks.paperctrl.R
import com.vknxworks.paperctrl.container
import com.vknxworks.paperctrl.data.api.ServerStatistics
import com.vknxworks.paperctrl.data.api.statistics
import com.vknxworks.paperctrl.ui.components.CtrlCard
import com.vknxworks.paperctrl.ui.components.CtrlHeroCard
import com.vknxworks.paperctrl.ui.components.SectionLabel
import com.vknxworks.paperctrl.ui.components.topDivider
import com.vknxworks.paperctrl.ui.theme.CtrlColors
import com.vknxworks.paperctrl.ui.theme.CtrlType
import com.vknxworks.paperctrl.ui.theme.Gutter
import kotlinx.coroutines.CancellationException
import java.text.NumberFormat

/**
 * Server statistics at the top of Organise: the document total as the hero number, then the inbox,
 * the latest ASN, label counts and file types in a card. Read again each time the tab opens; hidden
 * when the server can't be reached.
 */
@Composable
fun StatsSection() {
    val c = LocalContext.current.container
    val session by c.sessions.active.collectAsStateWithLifecycle()
    var stats by remember(session?.server?.id) { mutableStateOf<ServerStatistics?>(null) }
    LaunchedEffect(session?.server?.id, session?.address) {
        val cl = session?.client ?: return@LaunchedEffect
        stats = try {
            cl.statistics()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            stats
        }
    }
    val s = stats ?: return
    val nf = remember { NumberFormat.getIntegerInstance() }

    CtrlHeroCard(
        label = stringResource(R.string.stats_label),
        value = nf.format(s.documentsTotal),
        trailingLabel = s.currentAsn?.let { stringResource(R.string.stats_asn, it) },
        modifier = Modifier.padding(start = Gutter, end = Gutter, top = 4.dp, bottom = 8.dp),
    ) {
        Text(
            listOfNotNull(
                stringResource(R.string.stats_documents),
                s.documentsInbox?.let { stringResource(R.string.stats_in_inbox, nf.format(it)) },
                stringResource(R.string.stats_characters, compact(s.characterCount)),
            ).joinToString(" · "),
            style = CtrlType.Mono,
            color = CtrlColors.OnAccent,
        )
    }

    CtrlCard(Modifier.fillMaxWidth().padding(horizontal = Gutter, vertical = 8.dp), padding = PaddingValues(0.dp)) {
        Row(Modifier.fillMaxWidth().padding(vertical = 14.dp)) {
            StatCell(nf.format(s.tagCount), stringResource(R.string.meta_tags), Modifier.weight(1f))
            StatCell(nf.format(s.correspondentCount), stringResource(R.string.stats_correspondents), Modifier.weight(1f))
            StatCell(nf.format(s.documentTypeCount), stringResource(R.string.stats_types), Modifier.weight(1f))
            StatCell(nf.format(s.storagePathCount), stringResource(R.string.stats_paths), Modifier.weight(1f))
        }
        if (s.fileTypes.isNotEmpty() && s.documentsTotal > 0) {
            Column(Modifier.fillMaxWidth().topDivider(CtrlColors.Outline).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SectionLabel(stringResource(R.string.stats_file_types))
                val shown = s.fileTypes.take(4)
                val rest = s.fileTypes.drop(4).sumOf { it.count }
                shown.forEach { t -> TypeBar(fileTypeName(t.mimeType), t.count, s.documentsTotal, nf, accent = t == shown.first()) }
                if (rest > 0) TypeBar(stringResource(R.string.stats_other_types), rest, s.documentsTotal, nf, accent = false)
            }
        }
    }
}

@Composable
private fun StatCell(value: String, label: String, modifier: Modifier) {
    Column(modifier.padding(horizontal = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = CtrlType.Mono.copy(fontSize = 20.sp, lineHeight = 24.sp), color = CtrlColors.Text, maxLines = 1)
        Text(label.uppercase(), style = CtrlType.Label.copy(fontSize = 9.sp), color = CtrlColors.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun TypeBar(name: String, count: Int, total: Int, nf: NumberFormat, accent: Boolean) {
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(name, style = CtrlType.Mono, color = CtrlColors.Text, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            Spacer(Modifier.width(8.dp))
            Text(nf.format(count), style = CtrlType.Mono, color = CtrlColors.Muted)
        }
        Spacer(Modifier.height(4.dp))
        val shape = androidx.compose.foundation.shape.RoundedCornerShape(3.dp)
        Box(Modifier.fillMaxWidth().height(6.dp).clip(shape).background(CtrlColors.Track)) {
            Box(
                Modifier.fillMaxWidth((count.toFloat() / total).coerceIn(0.02f, 1f)).height(6.dp).clip(shape)
                    .background(if (accent) CtrlColors.Accent else CtrlColors.ProgressFill),
            )
        }
    }
}

/** "application/pdf" → "PDF", "image/jpeg" → "JPEG"; anything odd stays as Paperless sent it. */
private fun fileTypeName(mime: String): String {
    val sub = mime.substringAfter('/', mime).substringAfterLast('.').substringAfterLast('+')
    return when {
        mime == "application/pdf" -> "PDF"
        mime == "text/plain" -> "TXT"
        mime.contains("presentationml") -> "PPTX"
        mime.contains("opendocument.text") -> "ODT"
        mime.contains("wordprocessingml") || mime.endsWith("/msword") -> "DOCX"
        mime.contains("spreadsheetml") || mime.endsWith("ms-excel") -> "XLSX"
        sub.length in 1..12 -> sub.uppercase()
        else -> mime
    }
}

/** 1234 → "1.2K", 3400000 → "3.4M". */
private fun compact(n: Long): String = when {
    n >= 1_000_000_000 -> "%.1fG".format(n / 1_000_000_000.0)
    n >= 1_000_000 -> "%.1fM".format(n / 1_000_000.0)
    n >= 1_000 -> "%.1fK".format(n / 1_000.0)
    else -> "$n"
}
