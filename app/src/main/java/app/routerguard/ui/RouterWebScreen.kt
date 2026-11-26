package app.routerguard.ui

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Uri
import android.net.http.SslError
import android.webkit.SslErrorHandler
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.FileProvider
import androidx.webkit.ProxyConfig
import androidx.webkit.ProxyController
import androidx.webkit.WebViewFeature
import app.routerguard.data.HarEntry
import app.routerguard.data.HarExport
import app.routerguard.data.RecorderHub
import app.routerguard.data.RecordingProxy
import app.routerguard.data.RouterClient
import org.json.JSONObject
import java.io.File
import java.util.concurrent.Executor
import javax.net.SocketFactory

private class WebHolder {
    var web: WebView? = null
    var autoTried = false
    @Volatile var ready = false
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun RouterWebScreen(host: String, user: String, password: String, visible: Boolean) {
    val context = LocalContext.current
    val scheme = MaterialTheme.colorScheme
    val holder = remember { WebHolder() }
    val recording by RecorderHub.recording.collectAsState()
    val count by RecorderHub.count.collectAsState()
    var canGoBack by remember { mutableStateOf(false) }
    var showGuide by remember { mutableStateOf(false) }
    var showReview by remember { mutableStateOf(false) }
    var note by remember { mutableStateOf<String?>(null) }

    val cleanHost = host.trim().removePrefix("http://").removePrefix("https://").trimEnd('/')
    val startUrl = if (host.trim().startsWith("https://")) "https://$cleanHost/" else "http://$cleanHost/"
    val hostOnly = cleanHost.substringBefore('/').substringBefore(':')

    // Everything the page does goes through a local proxy that is tied to Wi-Fi (so mobile data
    // never gets in the way) and, while recording, writes down each request and response.
    val proxy = remember {
        val net = RouterClient.wifiNetwork(context)
        RecordingProxy(net?.socketFactory ?: SocketFactory.getDefault(), { RecorderHub.recording.value }, RecorderHub::add)
            .also { it.start() }
    }

    DisposableEffect(Unit) {
        val cm = context.getSystemService(ConnectivityManager::class.java)
        var proxied = false
        if (WebViewFeature.isFeatureSupported(WebViewFeature.PROXY_OVERRIDE)) {
            ProxyController.getInstance().setProxyOverride(
                ProxyConfig.Builder().addProxyRule("127.0.0.1:${proxy.port}").build(),
                Executor { it.run() },
                Runnable { holder.ready = true; holder.web?.post { holder.web?.loadUrl(startUrl) } },
            )
            proxied = true
        } else {
            note = "This phone's WebView can't be recorded. The router page still works."
            cm?.bindProcessToNetwork(RouterClient.wifiNetwork(context))
            holder.ready = true
            holder.web?.post { holder.web?.loadUrl(startUrl) }
        }
        onDispose {
            if (proxied) ProxyController.getInstance().clearProxyOverride(Executor { it.run() }, Runnable { })
            else cm?.bindProcessToNetwork(null)
            proxy.stop()
            RecorderHub.recording.value = false
            holder.web?.destroy()
        }
    }

    BackHandler(enabled = visible && canGoBack) { holder.web?.goBack() }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            AssistChip(onClick = { holder.web?.reload() }, label = { Text("Reload") })
            AssistChip(
                onClick = { holder.autoTried = true; holder.web?.evaluateJavascript(autofillScript(user, password), null) },
                label = { Text("Auto-login") },
            )
            AssistChip(onClick = { holder.web?.loadUrl(startUrl) }, label = { Text("Home") })
            AssistChip(
                onClick = {
                    if (recording) {
                        RecorderHub.recording.value = false
                        showReview = true
                    } else {
                        RecorderHub.clear()
                        RecorderHub.recording.value = true
                        showGuide = true
                    }
                },
                label = { Text(if (recording) "Stop recording" else "Record") },
            )
        }
        if (note != null) {
            Text(note!!, style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 16.dp))
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    WebView(ctx).apply {
                        holder.web = this
                        if (holder.ready) post { loadUrl(startUrl) }
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.useWideViewPort = true
                        settings.loadWithOverviewMode = true
                        settings.setSupportZoom(true)
                        settings.builtInZoomControls = true
                        settings.displayZoomControls = false
                        settings.allowFileAccess = false
                        settings.allowContentAccess = false
                        settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                        webViewClient = object : WebViewClient() {
                            // Stay on the router: never follow links to other sites.
                            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean =
                                request.url.host != hostOnly

                            override fun onReceivedSslError(view: WebView, handler: SslErrorHandler, error: SslError) {
                                // Routers use self-signed certificates; trust only the router's own address.
                                if (Uri.parse(error.url).host == hostOnly) handler.proceed() else handler.cancel()
                            }

                            override fun doUpdateVisitedHistory(view: WebView, url: String?, isReload: Boolean) {
                                canGoBack = view.canGoBack()
                            }

                            override fun onPageFinished(view: WebView, url: String?) {
                                // Try the saved login once per visit, so a wrong password can't lock the router.
                                if (!holder.autoTried && password.isNotEmpty()) {
                                    holder.autoTried = true
                                    view.evaluateJavascript(autofillScript(user, password), null)
                                }
                            }
                        }
                    }
                },
            )
            if (recording) {
                Row(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .padding(12.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(scheme.surfaceContainerHigh)
                        .padding(start = 16.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(10.dp).clip(CircleShape).background(scheme.error))
                    Spacer(Modifier.width(8.dp))
                    Text("Recording · $count requests", style = MaterialTheme.typography.bodyMedium)
                    TextButton(onClick = { RecorderHub.recording.value = false; showReview = true }) { Text("Stop & review") }
                }
            }
        }
    }

    if (showGuide) {
        AlertDialog(
            onDismissRequest = { showGuide = false },
            title = { Text("Recording started") },
            text = {
                Text(
                    "Now use the router page normally:\n\n" +
                        "1. Log in (if you aren't already)\n" +
                        "2. Open the page that lists connected devices\n" +
                        "3. Block one device, then unblock it\n" +
                        "4. Optionally open the DNS settings\n\n" +
                        "Then tap Stop & review. Passwords you type are removed from the recording."
                )
            },
            confirmButton = { TextButton(onClick = { showGuide = false }) { Text("Got it") } },
        )
    }

    if (showReview) {
        ReviewDialog(context = context, onClose = { showReview = false })
    }
}

@Composable
private fun ReviewDialog(context: Context, onClose: () -> Unit) {
    var maskMacs by remember { mutableStateOf(true) }
    var entries by remember { mutableStateOf(RecorderHub.entries) }
    val scheme = MaterialTheme.colorScheme

    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("Review recording") },
        text = {
            Column {
                Text(
                    "${entries.size} requests recorded. Passwords you typed are removed, and pages that look like Wi-Fi or account settings are left out. Tap × to drop anything else you don't want to share.",
                    style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Hide device MAC addresses", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                    Switch(checked = maskMacs, onCheckedChange = { maskMacs = it })
                }
                Spacer(Modifier.height(8.dp))
                LazyColumn(Modifier.height(220.dp)) {
                    items(entries) { e ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "${e.method} ${e.url.substringAfter("//").substringAfter('/')} · ${e.status}",
                                Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.bodySmall,
                            )
                            TextButton(onClick = { RecorderHub.remove(e); entries = RecorderHub.entries }) { Text("×") }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                enabled = entries.isNotEmpty(),
                onClick = { shareRecording(context, entries, maskMacs); onClose() },
            ) { Text("Share") }
        },
        dismissButton = {
            TextButton(onClick = { RecorderHub.clear(); onClose() }) { Text("Discard", color = scheme.error) }
        },
    )
}

private fun shareRecording(context: Context, entries: List<HarEntry>, maskMacs: Boolean) {
    val dir = File(context.cacheDir, "recordings").apply { mkdirs() }
    dir.listFiles()?.forEach { it.delete() }
    val file = File(dir, "router-recording-${System.currentTimeMillis()}.har.json")
    file.writeText(HarExport.build(entries, maskMacs))
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "application/json"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(send, "Share recording").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}

/** Fills the router's login form and presses its login button. Used once per visit. */
private fun autofillScript(user: String, pass: String): String {
    val u = JSONObject.quote(user)
    val p = JSONObject.quote(pass)
    return """
(function(u,p){ try{
  var pw=document.querySelector('input[type=password]'); if(!pw) return 'nopass';
  var r=pw.getBoundingClientRect(); if(!(r.width>0&&r.height>0)) return 'hidden';
  var set=function(el,v){ var d=Object.getOwnPropertyDescriptor(HTMLInputElement.prototype,'value'); d.set.call(el,v);
    el.dispatchEvent(new Event('input',{bubbles:true})); el.dispatchEvent(new Event('change',{bubbles:true})); };
  var inputs=Array.prototype.slice.call(document.querySelectorAll('input')); var idx=inputs.indexOf(pw); var us=null;
  for(var i=idx-1;i>=0;i--){ var t=(inputs[i].type||'text').toLowerCase(); if(t==='text'||t==='email'||t==='tel'){us=inputs[i];break;} }
  if(us&&u) set(us,u); set(pw,p);
  var cands=Array.prototype.slice.call(document.querySelectorAll('input[type=submit],input[type=button],button,a,div[onclick],span[onclick]'));
  for(var j=0;j<cands.length;j++){ var c=cands[j];
    var s=((c.value||'')+' '+(c.innerText||'')+' '+(c.id||'')+' '+(c.name||'')).toLowerCase(); var rc=c.getBoundingClientRect();
    if(rc.width>0&&rc.height>0&&/log\s*in|login|sign\s*in|submit|\bok\b|apply/.test(s)){ c.click(); return 'clicked'; } }
  if(pw.form){ if(pw.form.requestSubmit) pw.form.requestSubmit(); else pw.form.submit(); return 'submitted'; }
  return 'filled';
}catch(e){ return 'error:'+e; } })($u,$p)
"""
}
