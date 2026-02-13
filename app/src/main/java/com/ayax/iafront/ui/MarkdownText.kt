package com.ayax.iafront.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.text.ClickableText
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp

private sealed interface MdBlock {
    data class Heading(val level: Int, val text: String) : MdBlock
    data class Paragraph(val text: String) : MdBlock
    data class BulletItem(val text: String) : MdBlock
    data class CheckItem(val checked: Boolean, val text: String) : MdBlock
    data class NumberedItem(val index: String, val text: String) : MdBlock
    data class Quote(val text: String) : MdBlock
    data class CodeBlock(val language: String, val text: String) : MdBlock
    data class TableBlock(val text: String) : MdBlock
    data object Divider : MdBlock
}

@Composable
fun MarkdownText(
    text: String,
    color: Color
) {
    val blocks = parseMarkdownBlocks(text)
    val codeBg = MaterialTheme.colorScheme.surfaceVariant
    val codeFg = MaterialTheme.colorScheme.onSurfaceVariant
    val quoteLineColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)

    Column(modifier = Modifier.fillMaxWidth()) {
        blocks.forEach { block ->
            when (block) {
                is MdBlock.Heading -> {
                    val style = when (block.level) {
                        1 -> MaterialTheme.typography.titleLarge
                        2 -> MaterialTheme.typography.titleMedium
                        else -> MaterialTheme.typography.titleSmall
                    }
                    MarkdownInlineText(
                        text = block.text,
                        color = color,
                        codeBg = codeBg,
                        codeFg = codeFg,
                        style = style
                    )
                    Spacer(Modifier.height(4.dp))
                }

                is MdBlock.Paragraph -> {
                    MarkdownInlineText(
                        text = block.text,
                        color = color,
                        codeBg = codeBg,
                        codeFg = codeFg
                    )
                    Spacer(Modifier.height(4.dp))
                }

                is MdBlock.BulletItem -> {
                    Row {
                        Text(text = "• ", color = color, fontWeight = FontWeight.Medium)
                        MarkdownInlineText(
                            text = block.text,
                            color = color,
                            codeBg = codeBg,
                            codeFg = codeFg
                        )
                    }
                    Spacer(Modifier.height(3.dp))
                }

                is MdBlock.CheckItem -> {
                    Row {
                        Text(
                            text = if (block.checked) "☑ " else "☐ ",
                            color = color,
                            fontWeight = FontWeight.Medium
                        )
                        MarkdownInlineText(
                            text = block.text,
                            color = color,
                            codeBg = codeBg,
                            codeFg = codeFg
                        )
                    }
                    Spacer(Modifier.height(3.dp))
                }

                is MdBlock.NumberedItem -> {
                    Row {
                        Text(text = "${block.index}. ", color = color, fontWeight = FontWeight.Medium)
                        MarkdownInlineText(
                            text = block.text,
                            color = color,
                            codeBg = codeBg,
                            codeFg = codeFg
                        )
                    }
                    Spacer(Modifier.height(3.dp))
                }

                is MdBlock.Quote -> {
                    Row {
                        Box(
                            modifier = Modifier
                                .padding(top = 2.dp, end = 8.dp)
                                .height(18.dp)
                                .width(3.dp)
                                .background(quoteLineColor)
                        )
                        MarkdownInlineText(
                            text = block.text,
                            color = color.copy(alpha = 0.92f),
                            codeBg = codeBg,
                            codeFg = codeFg,
                            style = MaterialTheme.typography.bodyMedium.copy(fontStyle = FontStyle.Italic)
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                }

                is MdBlock.CodeBlock -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(codeBg)
                            .padding(10.dp)
                    ) {
                        val scroll = rememberScrollState()
                        Text(
                            text = rememberCodeAnnotated(block.language, block.text),
                            color = codeFg,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.horizontalScroll(scroll)
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                }

                is MdBlock.TableBlock -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(codeBg)
                            .padding(10.dp)
                    ) {
                        val scroll = rememberScrollState()
                        Text(
                            text = block.text,
                            color = codeFg,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.horizontalScroll(scroll)
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                }

                MdBlock.Divider -> {
                    HorizontalDivider(color = color.copy(alpha = 0.25f))
                    Spacer(Modifier.height(6.dp))
                }
            }
        }
    }
}

@Composable
private fun MarkdownInlineText(
    text: String,
    color: Color,
    codeBg: Color,
    codeFg: Color,
    style: TextStyle = MaterialTheme.typography.bodyMedium
) {
    val uriHandler = LocalUriHandler.current
    val annotated = rememberInlineAnnotated(text, color, codeBg, codeFg)
    ClickableText(
        text = annotated,
        style = style.copy(color = color),
        onClick = { offset ->
            annotated
                .getStringAnnotations("URL", offset, offset)
                .firstOrNull()
                ?.let { ann ->
                    runCatching { uriHandler.openUri(ann.item) }
                }
        }
    )
}

private fun parseMarkdownBlocks(input: String): List<MdBlock> {
    val lines = input.replace("\r\n", "\n").split('\n')
    val blocks = mutableListOf<MdBlock>()
    val paragraph = mutableListOf<String>()
    var i = 0

    fun flushParagraph() {
        if (paragraph.isNotEmpty()) {
            blocks += MdBlock.Paragraph(paragraph.joinToString("\n").trim())
            paragraph.clear()
        }
    }

    while (i < lines.size) {
        val raw = lines[i]
        val line = raw.trimEnd()
        val trimmed = line.trim()

        if (trimmed.isBlank()) {
            flushParagraph()
            i++
            continue
        }

        if (trimmed.startsWith("```")) {
            flushParagraph()
            val language = trimmed.removePrefix("```").trim().lowercase()
            i++
            val codeLines = mutableListOf<String>()
            while (i < lines.size && !lines[i].trim().startsWith("```")) {
                codeLines += lines[i]
                i++
            }
            if (i < lines.size) i++
            blocks += MdBlock.CodeBlock(language, codeLines.joinToString("\n"))
            continue
        }

        if (trimmed.matches(Regex("^#{1,6}\\s+.*$"))) {
            flushParagraph()
            val level = trimmed.takeWhile { it == '#' }.length.coerceIn(1, 3)
            val text = trimmed.dropWhile { it == '#' }.trim()
            blocks += MdBlock.Heading(level, text)
            i++
            continue
        }

        if (trimmed == "---" || trimmed == "***" || trimmed == "___") {
            flushParagraph()
            blocks += MdBlock.Divider
            i++
            continue
        }

        if (trimmed.startsWith(">")) {
            flushParagraph()
            blocks += MdBlock.Quote(trimmed.removePrefix(">").trim())
            i++
            continue
        }

        val check = Regex("^[-*+]\\s+\\[( |x|X)]\\s+(.+)$").find(trimmed)
        if (check != null) {
            flushParagraph()
            blocks += MdBlock.CheckItem(
                checked = check.groupValues[1].equals("x", ignoreCase = true),
                text = check.groupValues[2]
            )
            i++
            continue
        }

        if (trimmed.matches(Regex("^[-*+]\\s+.+$"))) {
            flushParagraph()
            blocks += MdBlock.BulletItem(trimmed.drop(1).trim())
            i++
            continue
        }

        val numbered = Regex("^(\\d+)\\.\\s+(.+)$").find(trimmed)
        if (numbered != null) {
            flushParagraph()
            blocks += MdBlock.NumberedItem(
                index = numbered.groupValues[1],
                text = numbered.groupValues[2]
            )
            i++
            continue
        }

        if (looksLikeTableRow(trimmed)) {
            val tableLines = mutableListOf<String>()
            var j = i
            while (j < lines.size && looksLikeTableRow(lines[j].trim())) {
                tableLines += lines[j].trimEnd()
                j++
            }
            if (tableLines.size >= 2 && looksLikeTableSeparator(tableLines[1])) {
                flushParagraph()
                blocks += MdBlock.TableBlock(tableLines.joinToString("\n"))
                i = j
                continue
            }
        }

        paragraph += line
        i++
    }

    flushParagraph()
    return blocks
}

private fun looksLikeTableRow(line: String): Boolean = line.count { it == '|' } >= 2

private fun looksLikeTableSeparator(line: String): Boolean {
    val candidate = line.replace("|", "").replace(":", "").replace("-", "").trim()
    return candidate.isEmpty() && line.contains("---")
}

private fun rememberInlineAnnotated(
    text: String,
    baseColor: Color,
    codeBg: Color,
    codeFg: Color
) = buildAnnotatedString {
    var i = 0
    while (i < text.length) {
        if (text.startsWith("**", i)) {
            val end = text.indexOf("**", i + 2)
            if (end > i + 2) {
                pushStyle(SpanStyle(fontWeight = FontWeight.Bold))
                append(text.substring(i + 2, end))
                pop()
                i = end + 2
                continue
            }
        }

        if (text.startsWith("*", i) && !text.startsWith("**", i)) {
            val end = text.indexOf('*', i + 1)
            if (end > i + 1) {
                pushStyle(SpanStyle(fontStyle = FontStyle.Italic))
                append(text.substring(i + 1, end))
                pop()
                i = end + 1
                continue
            }
        }

        if (text[i] == '`') {
            val end = text.indexOf('`', i + 1)
            if (end > i + 1) {
                pushStyle(
                    SpanStyle(
                        fontFamily = FontFamily.Monospace,
                        background = codeBg,
                        color = codeFg
                    )
                )
                append(text.substring(i + 1, end))
                pop()
                i = end + 1
                continue
            }
        }

        val linkMatch = Regex("^\\[([^\\]]+)]\\(([^)]+)\\)").find(text.substring(i))
        if (linkMatch != null && linkMatch.range.first == 0) {
            val label = linkMatch.groupValues[1]
            val url = linkMatch.groupValues[2]
            val start = length
            pushStyle(
                SpanStyle(
                    color = baseColor,
                    textDecoration = TextDecoration.Underline,
                    fontWeight = FontWeight.Medium
                )
            )
            append(label)
            pop()
            addStringAnnotation(
                tag = "URL",
                annotation = url,
                start = start,
                end = length
            )
            i += linkMatch.value.length
            continue
        }

        val rawUrlMatch = Regex("^(https?://\\S+)").find(text.substring(i))
        if (rawUrlMatch != null && rawUrlMatch.range.first == 0) {
            val url = rawUrlMatch.groupValues[1].trimEnd(')', '.', ',', ';')
            val start = length
            pushStyle(
                SpanStyle(
                    color = baseColor,
                    textDecoration = TextDecoration.Underline
                )
            )
            append(url)
            pop()
            addStringAnnotation(
                tag = "URL",
                annotation = url,
                start = start,
                end = length
            )
            i += rawUrlMatch.value.length
            continue
        }

        append(text[i])
        i++
    }
}

private fun rememberCodeAnnotated(
    language: String,
    code: String
) = buildAnnotatedString {
    append(code)
    val keywords = when (language) {
        "kotlin", "kt", "java", "javascript", "js", "typescript", "ts", "python", "py", "sql", "bash", "sh" ->
            setOf(
                "fun", "class", "object", "interface", "data", "val", "var", "if", "else", "when",
                "for", "while", "return", "true", "false", "null", "public", "private", "protected",
                "static", "void", "new", "import", "from", "select", "where", "insert", "update", "delete"
            )
        else -> emptySet()
    }

    if (keywords.isNotEmpty()) {
        Regex("\\b[A-Za-z_][A-Za-z0-9_]*\\b").findAll(code).forEach { m ->
            if (keywords.contains(m.value.lowercase())) {
                addStyle(
                    SpanStyle(color = Color(0xFF80CBC4), fontWeight = FontWeight.SemiBold),
                    m.range.first,
                    m.range.last + 1
                )
            }
        }
    }

    Regex("\"([^\"\\\\]|\\\\.)*\"|'([^'\\\\]|\\\\.)*'").findAll(code).forEach { m ->
        addStyle(
            SpanStyle(color = Color(0xFFFFCC80)),
            m.range.first,
            m.range.last + 1
        )
    }

    Regex("//.*$|#.*$", setOf(RegexOption.MULTILINE)).findAll(code).forEach { m ->
        addStyle(
            SpanStyle(color = Color(0xFF90A4AE), fontStyle = FontStyle.Italic),
            m.range.first,
            m.range.last + 1
        )
    }

    if (language == "xml" || language == "html") {
        Regex("</?[A-Za-z0-9:_-]+").findAll(code).forEach { m ->
            addStyle(
                SpanStyle(color = Color(0xFF81D4FA), fontWeight = FontWeight.Medium),
                m.range.first,
                m.range.last + 1
            )
        }
        Regex("\\b[A-Za-z_:][-A-Za-z0-9_:.]*=").findAll(code).forEach { m ->
            addStyle(
                SpanStyle(color = Color(0xFFA5D6A7)),
                m.range.first,
                m.range.last + 1
            )
        }
    }
}
