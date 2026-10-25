package com.vknxworks.paperctrl.notify

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.color.ColorProvider
import com.vknxworks.paperctrl.ui.theme.CtrlColors
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.vknxworks.paperctrl.MainActivity
import com.vknxworks.paperctrl.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

// Ctrl / Signal colours for the widget, following the device's dark / light setting (no wallpaper colours).
private val WidgetBackground = ColorProvider(day = CtrlColors.Light.surface, night = CtrlColors.Dark.surface)
private val WidgetText = ColorProvider(day = CtrlColors.Light.text, night = CtrlColors.Dark.text)
private val WidgetAccent = ColorProvider(day = CtrlColors.Accent, night = CtrlColors.Accent)
private val WidgetOnAccent = ColorProvider(day = CtrlColors.OnAccent, night = CtrlColors.OnAccent)

/** Home screen widget: a big Scan button and how many documents wait in the inbox. */
class ScanWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val count = WidgetState.inboxCount(context)
        provideContent {
            Content(context, count)
        }
    }

    @Composable
    private fun Content(context: Context, count: Int) {
        val scan = Intent(context, MainActivity::class.java)
            .putExtra(Notifications.EXTRA_OPEN, Notifications.OPEN_SCAN)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val inbox = Intent(context, MainActivity::class.java)
            .putExtra(Notifications.EXTRA_OPEN, Notifications.OPEN_INBOX)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        Column(
            GlanceModifier.fillMaxSize().background(WidgetBackground).cornerRadius(22.dp).padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(
                GlanceModifier.fillMaxWidth().background(WidgetAccent).cornerRadius(16.dp).padding(vertical = 12.dp, horizontal = 14.dp)
                    .clickable(actionStartActivity(scan)),
                verticalAlignment = Alignment.CenterVertically,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Image(
                    ImageProvider(R.drawable.ic_scan_glyph),
                    contentDescription = null,
                    modifier = GlanceModifier.size(22.dp),
                    colorFilter = ColorFilter.tint(WidgetOnAccent),
                )
                Spacer(GlanceModifier.width(8.dp))
                Text(
                    context.getString(R.string.widget_scan),
                    style = TextStyle(color = WidgetOnAccent, fontSize = 16.sp, fontWeight = FontWeight.Bold),
                )
            }
            if (count >= 0) {
                Spacer(GlanceModifier.size(8.dp))
                Box(GlanceModifier.fillMaxWidth().clickable(actionStartActivity(inbox)), contentAlignment = Alignment.Center) {
                    Text(
                        if (count == 0) context.getString(R.string.widget_inbox_empty)
                        else context.resources.getQuantityString(R.plurals.widget_inbox, count, count),
                        style = TextStyle(color = WidgetText, fontSize = 13.sp),
                    )
                }
            }
        }
    }

    companion object {
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

        fun refresh(context: Context) {
            scope.launch {
                runCatching {
                    if (GlanceAppWidgetManager(context).getGlanceIds(ScanWidget::class.java).isNotEmpty()) ScanWidget().updateAll(context)
                }
            }
        }
    }
}

class ScanWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = ScanWidget()

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        InboxDigestWorker.scheduleWidget(context)
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        InboxDigestWorker.cancelWidget(context)
    }
}
