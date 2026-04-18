package io.github.mdreader

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.github.mdreader.ui.theme.MdReaderTheme
import org.commonmark.node.Heading
import org.commonmark.node.Node
import org.commonmark.node.Paragraph
import org.commonmark.node.Text as CommonmarkText
import org.commonmark.parser.Parser
import java.io.IOException

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            MdReaderTheme {
                Surface {
                    MarkdownReaderApp(initialUri = intent?.data)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
    }
}

@Composable
private fun MarkdownReaderApp(initialUri: Uri?) {
    val context = LocalContext.current
    var documentState by rememberSaveable(stateSaver = MarkdownDocumentState.Saver) {
        mutableStateOf(MarkdownDocumentState.Empty)
    }
    var pendingUri by remember { mutableStateOf(initialUri) }

    val openDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            pendingUri = uri
        }
    }

    LaunchedEffect(pendingUri) {
        val uri = pendingUri ?: return@LaunchedEffect
        documentState = loadMarkdownDocument(context, uri)
        pendingUri = null
    }

    Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(20.dp)
        ) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = documentState.title,
                        style = MaterialTheme.typography.headlineMedium
                    )
                    documentState.subtitle?.let { subtitle ->
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Button(onClick = { openDocumentLauncher.launch(arrayOf("text/*", "*/*")) }) {
                    Text(text = stringResource(id = R.string.open_document_button))
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            when (val state = documentState) {
                MarkdownDocumentState.Empty -> {
                    EmptyStateCard()
                }
                is MarkdownDocumentState.Error -> {
                    MessageCard(state.message)
                }
                is MarkdownDocumentState.Loaded -> {
                    MarkdownDocumentView(state)
                }
            }
        }
    }
}

@Composable
private fun EmptyStateCard() {
    MessageCard(
        message = stringResource(id = R.string.empty_state_body),
        title = stringResource(id = R.string.empty_state_title)
    )
}

@Composable
private fun MessageCard(message: String, title: String? = null) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(20.dp)) {
            title?.let {
                Text(text = it, style = MaterialTheme.typography.titleLarge)
                Spacer(modifier = Modifier.height(8.dp))
            }
            Text(text = message, style = MaterialTheme.typography.bodyLarge)
        }
    }
}

@Composable
private fun MarkdownDocumentView(state: MarkdownDocumentState.Loaded) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        if (state.blocks.isEmpty()) {
            MessageCard(message = state.rawMarkdown)
            return
        }

        state.blocks.forEach { block ->
            when (block) {
                is MarkdownBlock.Heading -> Text(
                    text = block.text,
                    style = when (block.level) {
                        1 -> MaterialTheme.typography.headlineLarge
                        2 -> MaterialTheme.typography.headlineMedium
                        else -> MaterialTheme.typography.titleLarge
                    }
                )
                is MarkdownBlock.Paragraph -> Text(
                    text = block.text,
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = stringResource(id = R.string.raw_markdown_label),
            style = MaterialTheme.typography.titleMedium
        )
        Card(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = state.rawMarkdown,
                modifier = Modifier.padding(16.dp),
                style = MaterialTheme.typography.bodyMedium,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}

private fun loadMarkdownDocument(context: android.content.Context, uri: Uri): MarkdownDocumentState {
    try {
        context.contentResolver.takePersistableUriPermission(
            uri,
            Intent.FLAG_GRANT_READ_URI_PERMISSION
        )
    } catch (_: SecurityException) {
        // Some providers do not support persistable permissions.
    }

    return try {
        val rawMarkdown = context.contentResolver.openInputStream(uri)
            ?.bufferedReader()
            ?.use { it.readText() }
            .orEmpty()

        if (rawMarkdown.isBlank()) {
            MarkdownDocumentState.Error(
                context.getString(R.string.error_prefix) + ": empty file"
            )
        } else {
            val fileName = queryDisplayName(context, uri)
                ?: context.getString(R.string.document_fallback_title)
            MarkdownDocumentState.Loaded(
                title = fileName,
                subtitle = uri.toString(),
                rawMarkdown = rawMarkdown,
                blocks = parseMarkdownBlocks(rawMarkdown)
            )
        }
    } catch (error: IOException) {
        MarkdownDocumentState.Error(
            context.getString(R.string.error_prefix) + ": ${error.message.orEmpty()}"
        )
    }
}

private fun queryDisplayName(context: android.content.Context, uri: Uri): String? {
    return context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
        ?.use { cursor ->
            val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (index >= 0 && cursor.moveToFirst()) cursor.getString(index) else null
        }
}

private fun parseMarkdownBlocks(markdown: String): List<MarkdownBlock> {
    val parser = Parser.builder().build()
    val document = parser.parse(markdown)
    val blocks = mutableListOf<MarkdownBlock>()

    var child: Node? = document.firstChild
    while (child != null) {
        when (child) {
            is Heading -> blocks += MarkdownBlock.Heading(
                level = child.level,
                text = child.extractPlainText()
            )
            is Paragraph -> blocks += MarkdownBlock.Paragraph(child.extractPlainText())
        }
        child = child.next
    }

    return blocks
}

private fun Node.extractPlainText(): String {
    val builder = StringBuilder()
    visitText(this, builder)
    return builder.toString().trim()
}

private fun visitText(node: Node?, builder: StringBuilder) {
    var current = node?.firstChild
    while (current != null) {
        when (current) {
            is CommonmarkText -> builder.append(current.literal)
            else -> visitText(current, builder)
        }
        if (current.next != null) builder.append(' ')
        current = current.next
    }
}

private sealed interface MarkdownBlock {
    data class Heading(val level: Int, val text: String) : MarkdownBlock
    data class Paragraph(val text: String) : MarkdownBlock
}

private sealed interface MarkdownDocumentState {
    val title: String
    val subtitle: String?

    data object Empty : MarkdownDocumentState {
        override val title: String = "MD Reader"
        override val subtitle: String? = null
    }

    data class Loaded(
        override val title: String,
        override val subtitle: String?,
        val rawMarkdown: String,
        val blocks: List<MarkdownBlock>
    ) : MarkdownDocumentState

    data class Error(val message: String) : MarkdownDocumentState {
        override val title: String = "MD Reader"
        override val subtitle: String? = null
    }

    companion object {
        val Saver = androidx.compose.runtime.saveable.Saver<MarkdownDocumentState, String>(
            save = { state ->
                when (state) {
                    Empty -> "EMPTY"
                    is Error -> "ERROR|${state.message}"
                    is Loaded -> "LOADED|${state.title}|${state.subtitle.orEmpty()}|${state.rawMarkdown}"
                }
            },
            restore = { saved ->
                when {
                    saved == "EMPTY" -> Empty
                    saved.startsWith("ERROR|") -> Error(saved.removePrefix("ERROR|"))
                    saved.startsWith("LOADED|") -> {
                        val parts = saved.split('|', limit = 4)
                        if (parts.size == 4) {
                            Loaded(
                                title = parts[1],
                                subtitle = parts[2].ifBlank { null },
                                rawMarkdown = parts[3],
                                blocks = parseMarkdownBlocks(parts[3])
                            )
                        } else {
                            Empty
                        }
                    }
                    else -> Empty
                }
            }
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun MarkdownReaderAppPreview() {
    MdReaderTheme {
        MarkdownReaderApp(initialUri = null)
    }
}
