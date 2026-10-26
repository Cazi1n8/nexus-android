package br.com.nexus.assistant.ui

import android.text.method.LinkMovementMethod
import android.widget.TextView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.material3.MaterialTheme
import io.noties.markwon.Markwon

/** CommonMark renderer: presents the original response, never edits its wording. */
@Composable
internal fun MarkdownMessage(text: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val renderer = remember(context) { Markwon.create(context) }
    val color = MaterialTheme.colorScheme.onSurface.toArgb()
    val link = MaterialTheme.colorScheme.primary.toArgb()
    AndroidView(modifier = modifier, factory = { viewContext ->
        TextView(viewContext).apply {
            textSize = 16f
            setTextColor(color)
            setLinkTextColor(link)
            setTextIsSelectable(true)
            movementMethod = LinkMovementMethod.getInstance()
        }
    }, update = { view ->
        view.setTextColor(color)
        view.setLinkTextColor(link)
        if (view.tag != text) {
            renderer.setMarkdown(view, text)
            view.tag = text
        }
    })
}
