package io.github.mdreader

import android.content.Intent
import android.content.Intent.ACTION_VIEW
import android.content.Intent.EXTRA_STREAM
import android.content.Context
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.IntentCompat
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
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
import io.github.mdreader.R
import org.commonmark.node.Heading
import org.commonmark.node.BulletList
import org.commonmark.node.Code
import org.commonmark.node.FencedCodeBlock
import org.commonmark.node.HardLineBreak
import org.commonmark.node.Node
import org.commonmark.node.OrderedList
import org.commonmark.node.Paragraph
import org.commonmark.node.SoftLineBreak
import org.commonmark.node.Text as CommonmarkText
import org.commonmark.parser.Parser
import java.io.IOException
import java.util.concurrent.atomic.AtomicLong

class MainActivity : ComponentActivity() {
    private val incomingRequest: MutableState<DocumentRequest?> = mutableStateOf(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        incomingRequest.value = extractDocumentRequest(intent)

        setContent {
            val systemDarkTheme = androidx.compose.foundation.isSystemInDarkTheme()
            var darkTheme by rememberSaveable { mutableStateOf(systemDarkTheme) }

            MdReaderTheme(darkTheme = darkTheme) {
                Surface {
                    MarkdownReaderApp(
                        incomingRequest = incomingRequest.value,
                        darkTheme = darkTheme,
                        onToggleTheme = { darkTheme = !darkTheme }
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        incomingRequest.value = extractDocumentRequest(intent)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MarkdownReaderApp(
    incomingRequest: DocumentRequest?,
    darkTheme: Boolean,
    onToggleTheme: () -> Unit
) {
    val context = LocalContext.current
    var documentState by rememberSaveable(stateSaver = MarkdownDocumentState.Saver) {
        mutableStateOf(MarkdownDocumentState.Empty)
    }
    var pendingRequest by remember { mutableStateOf<DocumentRequest?>(incomingRequest) }

    val openDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            pendingRequest = DocumentRequest(uri)
        }
    }

    LaunchedEffect(incomingRequest?.requestId) {
        if (incomingRequest != null) {
            pendingRequest = incomingRequest
        }
    }

    LaunchedEffect(pendingRequest?.requestId) {
        val request = pendingRequest ?: return@LaunchedEffect
        documentState = loadMarkdownDocument(context, request.uri)
        pendingRequest = null
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(text = stringResource(id = R.string.app_name)) },
                actions = {
                    TextButton(onClick = onToggleTheme) {
                        Text(
                            text = stringResource(
                                id = if (darkTheme) {
                                    R.string.theme_toggle_light
                                } else {
                                    R.string.theme_toggle_dark
                                }
                            )
                        )
                    }
                    TextButton(onClick = { openDocumentLauncher.launch(arrayOf("text/*", "*/*")) }) {
                        Text(text = stringResource(id = R.string.open_document_button))
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(20.dp)
        ) {
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
                is MarkdownBlock.ListItem -> Text(
                    text = "${block.marker} ${block.text}",
                    style = MaterialTheme.typography.bodyLarge
                )
                is MarkdownBlock.CodeBlock -> Card(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = block.text,
                        modifier = Modifier.padding(16.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }
    }
}

private fun loadMarkdownDocument(context: Context, uri: Uri): MarkdownDocumentState {
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
            MarkdownDocumentState.Loaded(
                rawMarkdown = rawMarkdown,
                blocks = parseMarkdownBlocks(rawMarkdown)
            )
        }
    } catch (error: IOException) {
        MarkdownDocumentState.Error(
            context.getString(R.string.error_prefix) + ": ${error.message.orEmpty()}"
        )
    } catch (error: SecurityException) {
        MarkdownDocumentState.Error(
            context.getString(R.string.error_prefix) + ": ${error.message.orEmpty()}"
        )
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
            is Paragraph -> {
                val text = child.extractPlainText()
                if (text.isNotBlank()) {
                    blocks += MarkdownBlock.Paragraph(text)
                }
            }
            is BulletList -> blocks += child.extractListItems()
            is OrderedList -> blocks += child.extractListItems()
            is FencedCodeBlock -> {
                val text = child.literal.trimEnd()
                if (text.isNotBlank()) {
                    blocks += MarkdownBlock.CodeBlock(text)
                }
            }
        }
        child = child.next
    }

    return blocks
}

private fun Node.extractPlainText(): String {
    val builder = StringBuilder()
    visitText(this, builder)
    return builder.toString().replace(Regex("[ \\t]+"), " ").trim()
}

private fun org.commonmark.node.ListBlock.extractListItems(): List<MarkdownBlock.ListItem> {
    val blocks = mutableListOf<MarkdownBlock.ListItem>()
    var current: Node? = firstChild
    var index = if (this is OrderedList) startNumber else 1

    while (current != null) {
        if (current is org.commonmark.node.ListItem) {
            val text = current.extractPlainText()
            if (text.isNotBlank()) {
                val marker = if (this is OrderedList) "${index}." else "-"
                blocks += MarkdownBlock.ListItem(marker = marker, text = text)
            }
            index += 1
        }
        current = current.next
    }

    return blocks
}

private fun visitText(node: Node?, builder: StringBuilder) {
    var current = node?.firstChild
    while (current != null) {
        when (current) {
            is CommonmarkText -> builder.append(current.literal)
            is Code -> builder.append(current.literal)
            is SoftLineBreak, is HardLineBreak -> builder.append('\n')
            else -> visitText(current, builder)
        }
        if (current.next != null &&
            current !is SoftLineBreak &&
            current !is HardLineBreak
        ) {
            builder.append(' ')
        }
        current = current.next
    }
}

private sealed interface MarkdownBlock {
    data class Heading(val level: Int, val text: String) : MarkdownBlock
    data class Paragraph(val text: String) : MarkdownBlock
    data class ListItem(val marker: String, val text: String) : MarkdownBlock
    data class CodeBlock(val text: String) : MarkdownBlock
}

private sealed interface MarkdownDocumentState {
    data object Empty : MarkdownDocumentState

    data class Loaded(
        val rawMarkdown: String,
        val blocks: List<MarkdownBlock>
    ) : MarkdownDocumentState

    data class Error(val message: String) : MarkdownDocumentState

    companion object {
        private const val KEY_TYPE = "type"
        private const val KEY_MESSAGE = "message"
        private const val KEY_RAW = "raw"
        private const val TYPE_EMPTY = "empty"
        private const val TYPE_ERROR = "error"
        private const val TYPE_LOADED = "loaded"

        val Saver = androidx.compose.runtime.saveable.mapSaver(
            save = { state ->
                when (state) {
                    Empty -> mapOf(KEY_TYPE to TYPE_EMPTY)
                    is Error -> mapOf(
                        KEY_TYPE to TYPE_ERROR,
                        KEY_MESSAGE to state.message
                    )
                    is Loaded -> mapOf(
                        KEY_TYPE to TYPE_LOADED,
                        KEY_RAW to state.rawMarkdown
                    )
                }
            },
            restore = { saved ->
                when (saved[KEY_TYPE]) {
                    TYPE_EMPTY -> Empty
                    TYPE_ERROR -> Error(saved[KEY_MESSAGE] as? String ?: return@mapSaver Empty)
                    TYPE_LOADED -> {
                        val rawMarkdown = saved[KEY_RAW] as? String ?: return@mapSaver Empty
                        Loaded(
                            rawMarkdown = rawMarkdown,
                            blocks = parseMarkdownBlocks(rawMarkdown)
                        )
                    }
                    else -> Empty
                }
            }
        )
    }
}

private data class DocumentRequest(
    val uri: Uri,
    val requestId: Long = requestIds.incrementAndGet()
)

private fun extractDocumentRequest(intent: Intent?): DocumentRequest? {
    if (intent == null) return null

    val uri = when (intent.action) {
        ACTION_VIEW -> intent.data
        else -> intent.data ?: IntentCompat.getParcelableExtra(intent, EXTRA_STREAM, Uri::class.java)
    } ?: return null

    return DocumentRequest(uri)
}

private val requestIds = AtomicLong()

@Preview(showBackground = true)
@Composable
private fun MarkdownReaderAppPreview() {
    MdReaderTheme {
        MarkdownReaderApp(
            incomingRequest = null,
            darkTheme = false,
            onToggleTheme = {}
        )
    }
}
