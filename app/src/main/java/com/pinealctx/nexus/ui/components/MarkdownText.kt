package com.pinealctx.nexus.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun MarkdownText(
    text: String,
    modifier: Modifier = Modifier
) {
    val blocks = remember(text) { parseMarkdownBlocks(text) }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        for (block in blocks) {
            when (block) {
                is MarkdownBlock.CodeBlock -> {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = block.code,
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 13.sp
                            ),
                            modifier = Modifier
                                .horizontalScroll(rememberScrollState())
                                .padding(12.dp)
                        )
                    }
                }
                is MarkdownBlock.Heading -> {
                    Text(
                        text = renderInlineMarkdown(block.text),
                        style = when (block.level) {
                            1 -> MaterialTheme.typography.headlineSmall
                            2 -> MaterialTheme.typography.titleLarge
                            3 -> MaterialTheme.typography.titleMedium
                            else -> MaterialTheme.typography.titleSmall
                        },
                        fontWeight = FontWeight.Bold
                    )
                }
                is MarkdownBlock.BlockQuote -> {
                    Row(modifier = Modifier.fillMaxWidth()) {
                        Box(
                            modifier = Modifier
                                .width(3.dp)
                                .height(IntrinsicSize.Min)
                                .clip(RoundedCornerShape(2.dp))
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.5f))
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = renderInlineMarkdown(block.text),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontStyle = FontStyle.Italic
                        )
                    }
                }
                is MarkdownBlock.Paragraph -> {
                    Text(
                        text = renderInlineMarkdown(block.text),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
    }
}

private sealed class MarkdownBlock {
    data class CodeBlock(val code: String, val language: String?) : MarkdownBlock()
    data class Heading(val text: String, val level: Int) : MarkdownBlock()
    data class BlockQuote(val text: String) : MarkdownBlock()
    data class Paragraph(val text: String) : MarkdownBlock()
}

private fun parseMarkdownBlocks(text: String): List<MarkdownBlock> {
    val blocks = mutableListOf<MarkdownBlock>()
    val lines = text.lines()
    var i = 0

    while (i < lines.size) {
        val line = lines[i]

        // Code block (fenced)
        if (line.trimStart().startsWith("```")) {
            val language = line.trimStart().removePrefix("```").trim().ifEmpty { null }
            val codeLines = mutableListOf<String>()
            i++
            while (i < lines.size && !lines[i].trimStart().startsWith("```")) {
                codeLines.add(lines[i])
                i++
            }
            blocks.add(MarkdownBlock.CodeBlock(codeLines.joinToString("\n"), language))
            i++ // skip closing ```
            continue
        }

        // Heading
        val headingMatch = Regex("^(#{1,6})\\s+(.+)").find(line)
        if (headingMatch != null) {
            val level = headingMatch.groupValues[1].length
            val content = headingMatch.groupValues[2]
            blocks.add(MarkdownBlock.Heading(content, level))
            i++
            continue
        }

        // Block quote
        if (line.startsWith("> ")) {
            val quoteLines = mutableListOf<String>()
            while (i < lines.size && lines[i].startsWith("> ")) {
                quoteLines.add(lines[i].removePrefix("> "))
                i++
            }
            blocks.add(MarkdownBlock.BlockQuote(quoteLines.joinToString("\n")))
            continue
        }

        // Empty line — skip
        if (line.isBlank()) {
            i++
            continue
        }

        // Paragraph (collect consecutive non-empty lines)
        val paraLines = mutableListOf<String>()
        while (i < lines.size && lines[i].isNotBlank() &&
            !lines[i].startsWith("```") &&
            !lines[i].startsWith("# ") &&
            !lines[i].startsWith("> ")
        ) {
            paraLines.add(lines[i])
            i++
        }
        if (paraLines.isNotEmpty()) {
            blocks.add(MarkdownBlock.Paragraph(paraLines.joinToString("\n")))
        }
    }

    return blocks
}

private fun renderInlineMarkdown(text: String): AnnotatedString {
    return buildAnnotatedString {
        var remaining = text
        while (remaining.isNotEmpty()) {
            // Bold + Italic (***text***)
            val boldItalicMatch = Regex("\\*\\*\\*(.+?)\\*\\*\\*").find(remaining)
            // Bold (**text**)
            val boldMatch = Regex("\\*\\*(.+?)\\*\\*").find(remaining)
            // Italic (*text*)
            val italicMatch = Regex("(?<!\\*)\\*(?!\\*)(.+?)(?<!\\*)\\*(?!\\*)").find(remaining)
            // Inline code (`text`)
            val codeMatch = Regex("`(.+?)`").find(remaining)
            // Strikethrough (~~text~~)
            val strikeMatch = Regex("~~(.+?)~~").find(remaining)
            // Link [text](url)
            val linkMatch = Regex("\\[(.+?)]\\((.+?)\\)").find(remaining)

            val matches = listOfNotNull(
                boldItalicMatch?.let { it.range.first to "bolditalic" },
                boldMatch?.let { it.range.first to "bold" },
                italicMatch?.let { it.range.first to "italic" },
                codeMatch?.let { it.range.first to "code" },
                strikeMatch?.let { it.range.first to "strike" },
                linkMatch?.let { it.range.first to "link" }
            ).sortedBy { it.first }

            if (matches.isEmpty()) {
                append(remaining)
                break
            }

            val (pos, type) = matches.first()
            val match = when (type) {
                "bolditalic" -> boldItalicMatch!!
                "bold" -> boldMatch!!
                "italic" -> italicMatch!!
                "code" -> codeMatch!!
                "strike" -> strikeMatch!!
                "link" -> linkMatch!!
                else -> break
            }

            // Append text before match
            if (pos > 0) {
                append(remaining.substring(0, pos))
            }

            // Append styled text
            when (type) {
                "bolditalic" -> withStyle(SpanStyle(fontWeight = FontWeight.Bold, fontStyle = FontStyle.Italic)) {
                    append(match.groupValues[1])
                }
                "bold" -> withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                    append(match.groupValues[1])
                }
                "italic" -> withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                    append(match.groupValues[1])
                }
                "code" -> withStyle(SpanStyle(fontFamily = FontFamily.Monospace, background = androidx.compose.ui.graphics.Color(0x20000000))) {
                    append(match.groupValues[1])
                }
                "strike" -> withStyle(SpanStyle(textDecoration = TextDecoration.LineThrough)) {
                    append(match.groupValues[1])
                }
                "link" -> withStyle(SpanStyle(textDecoration = TextDecoration.Underline, color = androidx.compose.ui.graphics.Color(0xFF1976D2))) {
                    append(match.groupValues[1])
                }
            }

            remaining = remaining.substring(match.range.last + 1)
        }
    }
}
