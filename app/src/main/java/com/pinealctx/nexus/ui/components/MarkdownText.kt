package com.pinealctx.nexus.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.mikepenz.markdown.coil3.Coil3ImageTransformerImpl
import com.mikepenz.markdown.m3.Markdown

/**
 * Renders CommonMark/GFM content with native Compose components.
 *
 * Parsing stays asynchronous so long agent responses never block the UI thread. Coil 3 handles
 * remote markdown images and the renderer delegates link opening to Compose's URI handler.
 */
@Composable
fun MarkdownText(
    text: String,
    modifier: Modifier = Modifier
) {
    Markdown(
        content = text,
        modifier = modifier,
        imageTransformer = Coil3ImageTransformerImpl
    )
}
