package com.myai.offline.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.myai.offline.ui.theme.AccentTeal
import com.myai.offline.ui.theme.BorderSubtle
import com.myai.offline.ui.theme.PrimaryIndigo
import com.myai.offline.ui.theme.SurfaceDark
import com.myai.offline.ui.theme.TextMuted
import com.myai.offline.ui.theme.TextPrimary
import com.myai.offline.ui.theme.TextSecondary
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

sealed class MarkdownBlock {
    data class Paragraph(val text: String) : MarkdownBlock()
    data class Heading(val level: Int, val text: String) : MarkdownBlock()
    data class CodeBlock(val language: String, val code: String) : MarkdownBlock()
    data class BulletList(val items: List<String>) : MarkdownBlock()
    data class NumberedList(val items: List<String>) : MarkdownBlock()
}

object MarkdownParser {
    fun parse(rawText: String): List<MarkdownBlock> {
        val blocks = mutableListOf<MarkdownBlock>()
        val lines = rawText.lines()
        var i = 0

        while (i < lines.size) {
            val line = lines[i]

            // Code block start ```
            if (line.trimStart().startsWith("```")) {
                val lang = line.trimStart().removePrefix("```").trim()
                val codeLines = mutableListOf<String>()
                i++
                while (i < lines.size && !lines[i].trimStart().startsWith("```")) {
                    codeLines.add(lines[i])
                    i++
                }
                blocks.add(MarkdownBlock.CodeBlock(language = lang.ifBlank { "code" }, code = codeLines.joinToString("\n")))
                i++
                continue
            }

            // Headings (#, ##, ###)
            val trimmed = line.trimStart()
            if (trimmed.startsWith("### ")) {
                blocks.add(MarkdownBlock.Heading(level = 3, text = trimmed.removePrefix("### ").trim()))
                i++
                continue
            } else if (trimmed.startsWith("## ")) {
                blocks.add(MarkdownBlock.Heading(level = 2, text = trimmed.removePrefix("## ").trim()))
                i++
                continue
            } else if (trimmed.startsWith("# ")) {
                blocks.add(MarkdownBlock.Heading(level = 1, text = trimmed.removePrefix("# ").trim()))
                i++
                continue
            }

            // Bullet list
            if (trimmed.startsWith("- ") || trimmed.startsWith("* ") || trimmed.startsWith("• ")) {
                val bulletItems = mutableListOf<String>()
                while (i < lines.size) {
                    val cur = lines[i].trimStart()
                    if (cur.startsWith("- ") || cur.startsWith("* ") || cur.startsWith("• ")) {
                        val itemText = cur.replaceFirst(Regex("^[-*•]\\s+"), "")
                        bulletItems.add(itemText)
                        i++
                    } else {
                        break
                    }
                }
                blocks.add(MarkdownBlock.BulletList(bulletItems))
                continue
            }

            // Numbered list (1. , 2. )
            if (Regex("^\\d+\\.\\s+").containsMatchIn(trimmed)) {
                val numItems = mutableListOf<String>()
                while (i < lines.size) {
                    val cur = lines[i].trimStart()
                    if (Regex("^\\d+\\.\\s+").containsMatchIn(cur)) {
                        val itemText = cur.replaceFirst(Regex("^\\d+\\.\\s+"), "")
                        numItems.add(itemText)
                        i++
                    } else {
                        break
                    }
                }
                blocks.add(MarkdownBlock.NumberedList(numItems))
                continue
            }

            // Regular paragraph or blank line
            if (line.isBlank()) {
                i++
                continue
            }

            // Group consecutive text lines into a paragraph
            val paraLines = mutableListOf<String>()
            while (i < lines.size) {
                val cur = lines[i]
                if (cur.isBlank() ||
                    cur.trimStart().startsWith("```") ||
                    cur.trimStart().startsWith("#") ||
                    cur.trimStart().startsWith("- ") ||
                    cur.trimStart().startsWith("* ") ||
                    Regex("^\\d+\\.\\s+").containsMatchIn(cur.trimStart())) {
                    break
                }
                paraLines.add(cur)
                i++
            }
            if (paraLines.isNotEmpty()) {
                blocks.add(MarkdownBlock.Paragraph(paraLines.joinToString("\n")))
            }
        }

        return blocks
    }
}

@Composable
fun MarkdownText(
    text: String,
    modifier: Modifier = Modifier,
    textColor: Color = TextPrimary
) {
    val blocks = remember(text) { MarkdownParser.parse(text) }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        blocks.forEach { block ->
            when (block) {
                is MarkdownBlock.Heading -> {
                    val (fontSize, fontWeight) = when (block.level) {
                        1 -> 18.sp to FontWeight.Bold
                        2 -> 16.sp to FontWeight.Bold
                        else -> 15.sp to FontWeight.SemiBold
                    }
                    Text(
                        text = buildAnnotatedInlineMarkdown(block.text, textColor),
                        fontSize = fontSize,
                        fontWeight = fontWeight,
                        color = textColor,
                        lineHeight = (fontSize.value + 6).sp
                    )
                }

                is MarkdownBlock.Paragraph -> {
                    Text(
                        text = buildAnnotatedInlineMarkdown(block.text, textColor),
                        color = textColor,
                        fontSize = 14.sp,
                        lineHeight = 21.sp
                    )
                }

                is MarkdownBlock.BulletList -> {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        block.items.forEach { item ->
                            Row(verticalAlignment = Alignment.Top) {
                                Text(
                                    text = "•",
                                    color = PrimaryIndigo,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(end = 8.dp)
                                )
                                Text(
                                    text = buildAnnotatedInlineMarkdown(item, textColor),
                                    color = textColor,
                                    fontSize = 14.sp,
                                    lineHeight = 20.sp,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }

                is MarkdownBlock.NumberedList -> {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        block.items.forEachIndexed { index, item ->
                            Row(verticalAlignment = Alignment.Top) {
                                Text(
                                    text = "${index + 1}.",
                                    color = PrimaryIndigo,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(end = 8.dp)
                                )
                                Text(
                                    text = buildAnnotatedInlineMarkdown(item, textColor),
                                    color = textColor,
                                    fontSize = 14.sp,
                                    lineHeight = 20.sp,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }

                is MarkdownBlock.CodeBlock -> {
                    CodeBlockItem(language = block.language, code = block.code)
                }
            }
        }
    }
}

@Composable
fun CodeBlockItem(
    language: String,
    code: String,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var copied by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val shape = RoundedCornerShape(10.dp)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(SurfaceDark)
            .border(1.dp, BorderSubtle, shape)
    ) {
        // Code Block Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF0F0F14))
                .padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = language.lowercase(),
                color = TextMuted,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.SemiBold
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .padding(2.dp)
            ) {
                IconButton(
                    onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        val clip = ClipData.newPlainText("code", code)
                        clipboard.setPrimaryClip(clip)
                        copied = true
                        Toast.makeText(context, "Code copied", Toast.LENGTH_SHORT).show()
                        scope.launch {
                            delay(2000)
                            copied = false
                        }
                    },
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        imageVector = if (copied) Icons.Default.Check else Icons.Default.ContentCopy,
                        contentDescription = "Copy code",
                        tint = if (copied) AccentTeal else TextSecondary,
                        modifier = Modifier.size(13.dp)
                    )
                }
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = if (copied) "Copied" else "Copy",
                    color = if (copied) AccentTeal else TextSecondary,
                    fontSize = 11.sp
                )
            }
        }

        // Code Content (Horizontal scrollable)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(12.dp)
        ) {
            Text(
                text = code,
                color = Color(0xFFE2E8F0),
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace,
                lineHeight = 18.sp
            )
        }
    }
}

/**
 * Builds an AnnotatedString parsing inline formatting: **bold**, *italic*, and `inline code`.
 */
fun buildAnnotatedInlineMarkdown(text: String, defaultColor: Color): androidx.compose.ui.text.AnnotatedString {
    return buildAnnotatedString {
        var cursor = 0
        val length = text.length

        while (cursor < length) {
            // Check for inline code `...`
            if (text[cursor] == '`') {
                val endIdx = text.indexOf('`', cursor + 1)
                if (endIdx != -1) {
                    val codeSnippet = text.substring(cursor + 1, endIdx)
                    withStyle(
                        SpanStyle(
                            fontFamily = FontFamily.Monospace,
                            background = Color(0x336366F1),
                            color = Color(0xFF818CF8),
                            fontSize = 13.sp
                        )
                    ) {
                        append(" $codeSnippet ")
                    }
                    cursor = endIdx + 1
                    continue
                }
            }

            // Check for bold **...**
            if (cursor + 1 < length && text[cursor] == '*' && text[cursor + 1] == '*') {
                val endIdx = text.indexOf("**", cursor + 2)
                if (endIdx != -1) {
                    val boldText = text.substring(cursor + 2, endIdx)
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = defaultColor)) {
                        append(boldText)
                    }
                    cursor = endIdx + 2
                    continue
                }
            }

            // Check for italic *...*
            if (text[cursor] == '*') {
                val endIdx = text.indexOf('*', cursor + 1)
                if (endIdx != -1 && endIdx > cursor + 1) {
                    val italicText = text.substring(cursor + 1, endIdx)
                    withStyle(SpanStyle(fontStyle = FontStyle.Italic, color = defaultColor)) {
                        append(italicText)
                    }
                    cursor = endIdx + 1
                    continue
                }
            }

            // Default character
            append(text[cursor])
            cursor++
        }
    }
}
