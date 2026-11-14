package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.SourceLink
import com.example.ui.theme.JetBrainsMonoFamily
import kotlinx.coroutines.delay

private sealed interface MarkdownSegment {
    data class TextBlock(val text: String) : MarkdownSegment
    data class CodeBlock(val language: String, val code: String) : MarkdownSegment
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ImraxMarkdownContent(
    content: String,
    sources: List<SourceLink> = emptyList(),
    modifier: Modifier = Modifier
) {
    val segments = remember(content) { parseMarkdownSegments(content) }
    val uriHandler = LocalUriHandler.current

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        SelectionContainer {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                segments.forEach { segment ->
                    when (segment) {
                        is MarkdownSegment.TextBlock -> {
                            MarkdownProseBlock(segment.text)
                        }
                        is MarkdownSegment.CodeBlock -> {
                            CodeBlockCard(
                                language = segment.language,
                                code = segment.code
                            )
                        }
                    }
                }
            }
        }

        if (sources.isNotEmpty()) {
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "Sources",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                sources.forEach { source ->
                    AssistChip(
                        onClick = {
                            runCatching { uriHandler.openUri(source.url) }
                        },
                        label = {
                            Text(
                                text = source.title.take(36),
                                maxLines = 1,
                                style = MaterialTheme.typography.labelMedium
                            )
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Rounded.Public,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        },
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            labelColor = MaterialTheme.colorScheme.onSurface,
                            leadingIconContentColor = MaterialTheme.colorScheme.primary
                        )
                    )
                }
            }
        }
    }
}

@Composable
private fun MarkdownProseBlock(rawText: String) {
    val lines = rawText.lines()
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        lines.forEach { line ->
            val trimmed = line.trim()
            when {
                trimmed.isEmpty() -> Spacer(modifier = Modifier.height(2.dp))
                trimmed.startsWith("### ") -> {
                    Text(
                        text = formatInlineMarkdown(trimmed.removePrefix("### ").trim()),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                trimmed.startsWith("## ") -> {
                    Text(
                        text = formatInlineMarkdown(trimmed.removePrefix("## ").trim()),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                trimmed.startsWith("# ") -> {
                    Text(
                        text = formatInlineMarkdown(trimmed.removePrefix("# ").trim()),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                trimmed.startsWith("- ") || trimmed.startsWith("* ") -> {
                    Row(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = "•",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(end = 8.dp)
                        )
                        Text(
                            text = formatInlineMarkdown(trimmed.drop(2).trim()),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
                else -> {
                    Text(
                        text = formatInlineMarkdown(trimmed),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}

@Composable
private fun CodeBlockCard(language: String, code: String) {
    val clipboard = LocalClipboardManager.current
    var copied by remember { mutableStateOf(false) }

    LaunchedEffect(copied) {
        if (copied) {
            delay(2000)
            copied = false
        }
    }

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = Color(0xFF070D1F),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(14.dp))
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF0F1A38))
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Rounded.Code,
                        contentDescription = null,
                        tint = Color(0xFF00E5FF),
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = language.ifBlank { "code" }.uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFF94A3B8)
                    )
                }
                IconButton(
                    onClick = {
                        clipboard.setText(AnnotatedString(code))
                        copied = true
                    },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = if (copied) Icons.Rounded.Check else Icons.Rounded.ContentCopy,
                        contentDescription = "Copy code",
                        tint = if (copied) Color(0xFF00E5FF) else Color(0xFFCBD5E1),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(12.dp)
            ) {
                Text(
                    text = code,
                    fontFamily = JetBrainsMonoFamily,
                    fontSize = 13.sp,
                    lineHeight = 19.sp,
                    color = Color(0xFFE2E8F0)
                )
            }
        }
    }
}

private fun formatInlineMarkdown(text: String): AnnotatedString {
    return buildAnnotatedString {
        var idx = 0
        while (idx < text.length) {
            if (text.startsWith("**", idx)) {
                val end = text.indexOf("**", idx + 2)
                if (end != -1) {
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                        append(text.substring(idx + 2, end))
                    }
                    idx = end + 2
                    continue
                }
            }
            if (text[idx] == '`') {
                val end = text.indexOf('`', idx + 1)
                if (end != -1) {
                    withStyle(
                        SpanStyle(
                            fontFamily = JetBrainsMonoFamily,
                            fontWeight = FontWeight.Medium,
                            color = Color(0xFF38BDF8)
                        )
                    ) {
                        append(text.substring(idx + 1, end))
                    }
                    idx = end + 1
                    continue
                }
            }
            append(text[idx])
            idx++
        }
    }
}

private fun parseMarkdownSegments(input: String): List<MarkdownSegment> {
    val result = mutableListOf<MarkdownSegment>()
    val regex = Regex("```([a-zA-Z0-9_+#.-]*)\\n([\\s\\S]*?)```")
    var lastIndex = 0

    for (match in regex.findAll(input)) {
        if (match.range.first > lastIndex) {
            val prose = input.substring(lastIndex, match.range.first).trim()
            if (prose.isNotEmpty()) {
                result.add(MarkdownSegment.TextBlock(prose))
            }
        }
        val lang = match.groupValues[1]
        val code = match.groupValues[2].trimEnd()
        result.add(MarkdownSegment.CodeBlock(lang, code))
        lastIndex = match.range.last + 1
    }

    if (lastIndex < input.length) {
        val remaining = input.substring(lastIndex).trim()
        if (remaining.isNotEmpty()) {
            result.add(MarkdownSegment.TextBlock(remaining))
        }
    }

    if (result.isEmpty() && input.isNotBlank()) {
        result.add(MarkdownSegment.TextBlock(input))
    }
    return result
}
