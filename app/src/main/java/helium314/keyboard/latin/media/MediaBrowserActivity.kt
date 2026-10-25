// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.media

import android.content.ClipData
import android.content.Intent
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.graphics.drawable.AnimatedImageDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Bundle
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.FileProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import helium314.keyboard.latin.R
import helium314.keyboard.latin.utils.Theme
import helium314.keyboard.latin.utils.prefs
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.withContext
import java.io.File

class MediaBrowserActivity : ComponentActivity() {
    override fun onResume() {
        super.onResume()
        if (!MediaPrefs.enabled(this)) finish()
    }

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!MediaPrefs.enabled(this)) { finish(); return }
        enableEdgeToEdge()
        setContent {
            Theme {
                val state by lifecycle.currentStateFlow.collectAsState()
                var kindName by rememberSaveable { mutableStateOf(intent.getStringExtra("kind") ?: "GIFS") }
                var query by rememberSaveable { mutableStateOf("") }
                Scaffold(topBar = {
                    TopAppBar(title = { Text(stringResource(R.string.media_title)) }, navigationIcon = {
                        TextButton(onClick = { finish() }) { Text(stringResource(R.string.media_back)) }
                    })
                }) { padding ->
                    Browser(MediaKind.entries.firstOrNull { it.name == kindName } ?: MediaKind.GIFS, { kindName = it.name }, query, { query = it },
                        Modifier.padding(padding), state.isAtLeast(Lifecycle.State.STARTED))
                }
            }
        }
    }

    @Composable
    private fun Browser(kind: MediaKind, setKind: (MediaKind) -> Unit, query: String, setQuery: (String) -> Unit, modifier: Modifier, active: Boolean) {
        val repository = remember { MediaRepository(this) }
        val scope = rememberCoroutineScope()
        DisposableEffect(Unit) {
            val observer = LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_STOP) scope.coroutineContext.cancelChildren()
            }
            lifecycle.addObserver(observer)
            onDispose { lifecycle.removeObserver(observer) }
        }
        var submitted by rememberSaveable(kind) { mutableStateOf("") }
        var page by rememberSaveable(kind) { mutableIntStateOf(0) }
        var revision by remember { mutableIntStateOf(0) }
        var results by remember(kind) { mutableStateOf(emptyList<MediaItem>()) }
        var status by remember { mutableStateOf("") }
        var loading by remember { mutableStateOf(false) }
        var hasMore by remember(kind) { mutableStateOf(true) }
        var selected by remember { mutableStateOf<MediaItem?>(null) }
        var selectedFile by remember { mutableStateOf<File?>(null) }
        var busy by remember { mutableStateOf(false) }
        var error by remember { mutableStateOf("") }
        LaunchedEffect(kind, submitted, page, revision, active) {
            if (!active) return@LaunchedEffect
            loading = true
            status = ""
            if (page == 0) results = emptyList()
            try {
                val next = repository.search(kind, submitted, page)
                results = if (page == 0) next else (results + next).distinctBy { it.id }
                hasMore = kind != MediaKind.LOCAL && next.size == 24
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { status = getString(R.string.media_connection_error, e.message.orEmpty()) }
            finally { loading = false }
        }
        fun select(item: MediaItem) {
            selected = item; selectedFile = null; error = ""
            scope.launch {
                busy = true
                try { selectedFile = repository.download(item) }
                catch (e: CancellationException) { throw e }
                catch (e: Exception) { error = getString(R.string.media_failed, e.message.orEmpty()) }
                finally { busy = false }
            }
        }
        val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) scope.launch {
                try { repository.import(uri); page = 0; revision++ }
                catch (e: CancellationException) { throw e }
                catch (e: Exception) { status = getString(R.string.media_failed, e.message.orEmpty()) }
            }
        }
        Column(modifier.fillMaxSize().padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for ((tab, label) in listOf(MediaKind.GIFS to R.string.media_gifs, MediaKind.STICKERS to R.string.media_stickers,
                    MediaKind.LOCAL to R.string.media_local)) {
                    FilterChip(kind == tab, { setKind(tab); setQuery("") }, label = { Text(stringResource(label)) })
                }
            }
            if (kind != MediaKind.LOCAL) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(query, setQuery, Modifier.weight(1f), singleLine = true,
                        placeholder = { Text(stringResource(R.string.media_search_hint)) },
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { submitted = query; page = 0; revision++ }))
                    TextButton(onClick = { submitted = query; page = 0; revision++ }, enabled = !loading) {
                        Text(stringResource(R.string.media_search))
                    }
                }
            }
            Text(stringResource(when (kind) {
                MediaKind.STICKERS -> R.string.media_sticker_note
                else -> R.string.media_browse_note
            }), style = MaterialTheme.typography.bodySmall)
            if (kind == MediaKind.GIFS || kind == MediaKind.STICKERS)
                Text(stringResource(if (MediaPrefs.useApi(this@MediaBrowserActivity)) R.string.media_attribution else R.string.media_web_note),
                    style = MaterialTheme.typography.labelMedium)
            if (kind == MediaKind.LOCAL) {
                OutlinedButton(onClick = { importer.launch(arrayOf("image/gif", "image/webp", "image/png", "image/jpeg")) }) {
                    Text(stringResource(R.string.media_import))
                }
                Text(stringResource(R.string.media_pack_note), style = MaterialTheme.typography.bodySmall)
                OutlinedButton(onClick = {
                    scope.launch {
                        busy = true
                        try {
                            withContext(Dispatchers.IO) { WhatsAppStickers.createPack(this@MediaBrowserActivity) }
                            startActivity(WhatsAppStickers.addIntent(this@MediaBrowserActivity))
                        } catch (e: CancellationException) { throw e }
                        catch (e: Exception) { status = getString(R.string.media_failed, e.message.orEmpty()) }
                        finally { busy = false }
                    }
                }, enabled = !busy) { Text(stringResource(R.string.media_pack)) }
            }
            if (status.isNotEmpty()) Text(status, color = MaterialTheme.colorScheme.error)
            if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
            if (!loading && results.isEmpty()) Text(stringResource(R.string.media_empty))
            LazyVerticalGrid(GridCells.Adaptive(130.dp), Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(results, key = { it.id }) { item ->
                    Card(Modifier.height(136.dp).clickable(enabled = !busy) { select(item) }) {
                        Thumbnail(item, repository, Modifier.fillMaxWidth().weight(1f), active)
                        Text(item.title.ifBlank { item.source }, Modifier.padding(6.dp), maxLines = 1, style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
            if (hasMore && results.isNotEmpty()) TextButton(onClick = { page++ }, enabled = !loading) {
                Text(stringResource(R.string.media_load_more))
            }
        }
        val item = selected
        if (item != null) AlertDialog(onDismissRequest = { if (!busy) { selected = null; selectedFile = null } },
            title = { Text(item.source) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    val file = selectedFile
                    if (busy) Text(stringResource(R.string.media_loading))
                    if (error.isNotEmpty()) Text(error, color = MaterialTheme.colorScheme.error)
                    if (file != null) {
                        MediaPreview(item, file, repository, active)
                        val insert = MediaInsertion.canInsert(intent.getStringExtra("session"), item.mime)
                        if (!insert) Text(stringResource(R.string.media_no_insert), style = MaterialTheme.typography.bodySmall)
                        if (insert) Button(onClick = {
                            if (MediaPrefs.enabled(this@MediaBrowserActivity) &&
                                MediaInsertion.prepare(intent.getStringExtra("session"), file, item.mime)) finish()
                        }) { Text(stringResource(R.string.media_insert)) }
                        OutlinedButton(onClick = { share(file, item) }) { Text(stringResource(R.string.media_share)) }
                        if (item.source != "Local") OutlinedButton(onClick = {
                            scope.launch {
                                busy = true
                                try { repository.save(item, file); error = getString(R.string.media_saved) }
                                catch (e: CancellationException) { throw e }
                                catch (e: Exception) { error = getString(R.string.media_failed, e.message.orEmpty()) }
                                finally { busy = false }
                            }
                        }, enabled = !busy) { Text(stringResource(R.string.media_save)) }
                        if (item.source == "Local") OutlinedButton(onClick = {
                            scope.launch {
                                withContext(Dispatchers.IO) { file.delete() }
                                selected = null; selectedFile = null; revision++
                            }
                        }) { Text(stringResource(R.string.media_remove)) }
                    }
                }
            }, confirmButton = { TextButton(onClick = { selected = null; selectedFile = null }, enabled = !busy) {
                Text(stringResource(R.string.media_close))
            } })
    }

    private fun share(file: File, item: MediaItem) {
        if (!MediaPrefs.enabled(this)) return
        runCatching {
            val uri = FileProvider.getUriForFile(this, "$packageName.media", file)
            val share = Intent(Intent.ACTION_SEND).setType(item.mime).putExtra(Intent.EXTRA_STREAM, uri)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION).apply { clipData = ClipData.newRawUri("media", uri) }
            startActivity(Intent.createChooser(share, getString(R.string.media_share)))
        }.onFailure { Toast.makeText(this, getString(R.string.media_failed, it.message.orEmpty()), Toast.LENGTH_LONG).show() }
    }
}

@Composable
private fun MediaPreview(item: MediaItem, file: File, repository: MediaRepository, active: Boolean) {
    var play by remember(file) { mutableStateOf(false) }
    val canPlay = Build.VERSION.SDK_INT >= 28 && item.mime in listOf("image/gif", "image/webp")
    if (!play || !active) {
        Thumbnail(item, repository, Modifier.fillMaxWidth().height(180.dp), active)
        if (canPlay) TextButton(onClick = { play = true }) { Text(stringResource(R.string.media_play)) }
    } else if (Build.VERSION.SDK_INT >= 28) {
        val drawable by produceState<Drawable?>(null, file) {
            value = withContext(Dispatchers.IO) {
                runCatching {
                    ImageDecoder.decodeDrawable(ImageDecoder.createSource(file)) { decoder, info, _ ->
                        val scale = minOf(1f, 320f / maxOf(info.size.width, info.size.height))
                        decoder.setTargetSize((info.size.width * scale).toInt().coerceAtLeast(1),
                            (info.size.height * scale).toInt().coerceAtLeast(1))
                    }
                }.getOrNull()
            }
        }
        DisposableEffect(drawable) {
            (drawable as? AnimatedImageDrawable)?.start()
            onDispose { (drawable as? AnimatedImageDrawable)?.stop() }
        }
        AndroidView(factory = { ImageView(it).apply { scaleType = ImageView.ScaleType.FIT_CENTER } },
            update = { it.setImageDrawable(drawable) }, modifier = Modifier.fillMaxWidth().height(180.dp))
    }
}

@Composable
private fun Thumbnail(item: MediaItem, repository: MediaRepository, modifier: Modifier, active: Boolean) {
    val bitmap by produceState<android.graphics.Bitmap?>(null, item.id, active) {
        if (!active) { value = null; return@produceState }
        value = withContext(Dispatchers.IO) {
            try {
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                val local = item.preview.startsWith("file:")
                val path = if (local) android.net.Uri.parse(item.preview).path else null
                val bytes = if (local) null else repository.thumbnail(item)
                if (path != null) BitmapFactory.decodeFile(path, bounds)
                else BitmapFactory.decodeByteArray(bytes!!, 0, bytes.size, bounds)
                if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@withContext null
                val options = BitmapFactory.Options()
                while (maxOf(bounds.outWidth, bounds.outHeight) / options.inSampleSize.coerceAtLeast(1) > 384) {
                    options.inSampleSize = options.inSampleSize.coerceAtLeast(1) * 2
                }
                if (path != null) BitmapFactory.decodeFile(path, options)
                else BitmapFactory.decodeByteArray(bytes!!, 0, bytes.size, options)
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { null }
        }
    }
    val image = bitmap
    if (image == null) Box(modifier) { Text(stringResource(R.string.media_preview_unavailable), style = MaterialTheme.typography.labelSmall) }
    else Image(image.asImageBitmap(), item.title, modifier, contentScale = ContentScale.Fit)
}
