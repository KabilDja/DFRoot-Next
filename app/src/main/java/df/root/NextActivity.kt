package df.root

import android.content.ComponentName
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.foundation.Image
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Switch
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.blur.Backdrop
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.glass.GlassDialog
import top.yukonga.miuix.kmp.glass.GlassIconButton
import top.yukonga.miuix.kmp.glass.glassPanel
import top.yukonga.miuix.kmp.material.*
import top.yukonga.miuix.kmp.theme.*
import java.io.File
import java.util.concurrent.Executors

/** UI-only host. Rooting, native code and boot handling remain upstream. */
class NextActivity : ComponentActivity(), IReporter {
    private val worker = Executors.newSingleThreadExecutor()
    private lateinit var de: Context
    private val prefs get() = de.getSharedPreferences(ExploitRunner.PREFS_NAME, Context.MODE_PRIVATE)
    private var entries by mutableStateOf(listOf<Pair<String, String>>())
    private var selected by mutableStateOf<String?>(null)
    private var output by mutableStateOf("")
    private var running by mutableStateOf(false)
    private var hooked by mutableStateOf(false)
    private var outcome by mutableStateOf(0) // 0: no attempt, 1: upstream success, -1: failure
    private var settings by mutableStateOf(false)
    private var managers by mutableStateOf(false)
    private var boot by mutableStateOf(false)
    private var soft by mutableStateOf(false)
    private var disableModules by mutableStateOf(false)
    private var filterHeader = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        de = createDeviceProtectedStorageContext()
        refresh()
        setContent { Home() }
    }
    override fun onResume() { super.onResume(); if (::de.isInitialized) refresh() }
    private fun refresh() {
        entries = packageManager.getInstalledApplications(0)
            .filter { (it.flags and ApplicationInfo.FLAG_SYSTEM) == 0 && ExploitRunner.hasKsud(it) }
            .map { it.packageName to packageManager.getApplicationLabel(it).toString() }
            .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.second })
        selected = prefs.getString(ExploitRunner.PREF_SU_MANAGER, null)
        if (selected != null && entries.none { it.first == selected }) {
            prefs.edit().remove(ExploitRunner.PREF_SU_MANAGER).apply(); selected = null
        }
        hooked = File("/dev/df").exists()
        if (!running) outcome = RootUiState.resolve(hooked, File("/dev/dfm6").exists(), listOf("/dev/dfme0", "/dev/dfme1", "/dev/dfme2").any { File(it).exists() }, outcome)
        boot = packageManager.getComponentEnabledSetting(ComponentName(this, BootReceiver::class.java)) == PackageManager.COMPONENT_ENABLED_STATE_ENABLED
        soft = prefs.getBoolean("soft_reboot", false)
        disableModules = prefs.getBoolean("disable_modules", false)
    }
    override fun report(msg: String) {
        Log.i("dfroot", msg.trim())
        runOnUiThread {
            // Only suppress the runner's leading device-info block. Its real log is unchanged.
            var shown = msg
            if (filterHeader) {
                val lines = msg.split('\n')
                val kept = mutableListOf<String>()
                for (line in lines) {
                    if (filterHeader && (line.startsWith("* app version:") || line.startsWith("* manufacturer:") || line.startsWith("* model:") || line.startsWith("* android version:") || line.startsWith("* security patch:") || line.startsWith("* kernel version:") || line.isBlank())) continue
                    filterHeader = false; kept.add(line)
                }
                shown = kept.joinToString("\n")
            }
            output += shown
        }
    }
    private fun launchRoot() {
        if (BuildConfig.UI_INSPECTION || running || selected == null || File("/dev/df").exists()) return
        running = true; outcome = 0; output = ""; filterHeader = true
        worker.execute {
            try {
                val rc = ExploitRunner.run(de, this)
                val message = when(rc) {
                    0 -> "DFRoot: SUCCESS"
                    1 -> "DFRoot: Error - ksud nonzero exit"
                    2 -> "DFRoot: Error - check logcat & dmesg"
                    else -> "DFRoot: Error - failed to patch files"
                }
                report("\n$message\n")
                runOnUiThread { outcome = if (rc == 0) 1 else -1 }
            } catch (e: Exception) {
                Log.e("dfroot", "exploit exception", e)
                report("\nexception: $e\n")
                runOnUiThread { outcome = -1 }
            } finally {
                runOnUiThread { running = false; hooked = File("/dev/df").exists() }
            }
        }
    }
    @Composable private fun ManagerIcon(pkg:String,size:Int=34,spaced:Boolean=true) {
        val bitmap=remember(pkg) { runCatching { packageManager.getApplicationIcon(pkg).toBitmap(96,96).asImageBitmap() }.getOrNull() }
        if(bitmap!=null) Image(bitmap,contentDescription=null,modifier=Modifier.padding(end=if(spaced) 12.dp else 0.dp).size(size.dp))
    }
    @Composable private fun Home() {
        val dark = isSystemInDarkTheme()
        val ink = if (dark) Color(0xfff2f4f7) else Color(0xff17202a)
        val muted = if (dark) Color(0xffabb3c0) else Color(0xff596678)
        val background = if (dark) Color(0xff080b10) else Color(0xffd7dce3)
        val scene = rememberLayerBackdrop()
        val page = rememberLayerBackdrop()
        val tint = when { outcome == 1 -> Color(0xff12673f); outcome == -1 -> Color(0xff8c2536); else -> if (dark) Color(0xff303641) else Color(0xffd8dfe9) }
        val statusColor by animateColorAsState(tint)
        MiuixTheme(colors = if (dark) darkColorScheme() else lightColorScheme()) {
            Scaffold(containerColor = background) { insets ->
                Box(Modifier.fillMaxSize()) {
                    Box(Modifier.fillMaxSize().layerBackdrop(page)) {
                    Canvas(Modifier.fillMaxSize().layerBackdrop(scene)) {
                        drawRect(background)
                        drawCircle(Brush.radialGradient(listOf(Color(0xff527bb0).copy(alpha = if(dark) .28f else .13f), Color.Transparent), center = Offset(size.width*.85f, size.height*.24f), radius=size.width*.8f), radius=size.width*.8f, center=Offset(size.width*.85f,size.height*.24f))
                        drawCircle(Brush.radialGradient(listOf(Color(0xff23a88c).copy(alpha=.12f),Color.Transparent),center=Offset(0f,size.height*.7f),radius=size.width),radius=size.width,center=Offset(0f,size.height*.7f))
                        if (!dark) drawCircle(Brush.radialGradient(listOf(Color(0xff9a8ec9).copy(alpha=if(dark) .23f else .08f),Color.Transparent),center=Offset(size.width*.08f,size.height*.3f),radius=size.width*.5f),radius=size.width*.5f,center=Offset(size.width*.08f,size.height*.3f))
                    }
                    Column(Modifier.fillMaxSize().padding(insets).padding(horizontal=20.dp)) {
                        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(bottom=120.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                            Spacer(Modifier.height(70.dp))
                            Panel(scene, dark, statusColor) {
                                Row(verticalAlignment=Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f)) {
                                        Text(when { running -> "Starting root…"; outcome==1 -> "Root launch successful"; outcome == -1 -> "Root launch failed"; else -> "Not rooted" },fontSize=22.sp,fontWeight=FontWeight.Bold,color=if(dark || outcome!=0) Color.White else ink)
                                        Spacer(Modifier.height(7.dp))
                                        Text(when { running -> "Follow the log below"; outcome==1 -> "Reported by DFRoot."; outcome == -1 -> "Check the log before trying again"; hooked -> "Existing hook detected. Root access not checked."; else -> "Ready when you are" },fontSize=12.sp,color=if(dark || outcome!=0) Color.White.copy(alpha=.72f) else muted)
                                        Spacer(Modifier.height(12.dp)); Text("LKM  •  ${BuildConfig.VERSION_NAME}",fontSize=12.sp,color=if(dark || outcome!=0) Color.White.copy(alpha=.8f) else muted)
                                    }
                                    if(!running && outcome==0 && !hooked) {
                                        Column(Modifier.padding(start=12.dp).widthIn(max=104.dp).clickable { managers=true }.padding(4.dp),horizontalAlignment=Alignment.CenterHorizontally) {
                                            if(selected!=null) ManagerIcon(selected!!,42,false) else Symbol(R.drawable.ic_radio_button_unchecked,muted,42)
                                            Spacer(Modifier.height(5.dp))
                                            Text(entries.firstOrNull { it.first==selected }?.second ?: "SU manager",fontSize=11.sp,color=muted,textAlign=androidx.compose.ui.text.style.TextAlign.Center)
                                        }
                                    } else Box(Modifier.padding(start=12.dp)) { Symbol(when(outcome) { 1 -> R.drawable.ic_check_circle; -1 -> R.drawable.ic_error; else -> R.drawable.ic_radio_button_unchecked }, if(outcome==1) Color(0xff36e88e) else if(outcome == -1) Color(0xffff93a0) else muted, 48) }
                                }
                            }
                            Panel(scene,dark) {
                                Info("#", "Manager version", BuildConfig.VERSION_NAME, ink, muted)
                                Info("▧", "Kernel version", (System.getProperty("os.version") ?: "Unknown"),ink,muted)
                                Info("▯", "Device model", "${Build.MANUFACTURER.replaceFirstChar { it.uppercase() }} ${Build.MODEL}",ink,muted)
                                Info("◎", "Fingerprint", Build.FINGERPRINT,ink,muted)
                                Info("◷", "Android / security patch", "${Build.VERSION.RELEASE} / ${Build.VERSION.SECURITY_PATCH}",ink,muted,last=true)
                            }
                            Panel(scene,dark) {
                                Text("Log",fontSize=16.sp,fontWeight=FontWeight.Medium,color=ink)
                                Spacer(Modifier.height(12.dp))
                                val scroll=rememberScrollState()
                                LaunchedEffect(output) { scroll.animateScrollTo(scroll.maxValue) }
                                SelectionContainer {
                                    Box(Modifier.fillMaxWidth().heightIn(min=85.dp,max=240.dp).verticalScroll(scroll)) {
                                        Text(output.ifBlank { "Waiting for a root attempt…" },fontFamily=FontFamily.Monospace,fontSize=11.sp,color=if(output.isBlank()) muted else ink)
                                    }
                                }
                            }
                            Text("DFRoot by diabl0w  ·  Interface by DFRoot Next",fontSize=11.sp,color=muted,modifier=Modifier.padding(horizontal=8.dp,vertical=5.dp))
                        }
                    }
                    }
                    Row(Modifier.align(Alignment.TopCenter).padding(insets).padding(horizontal=20.dp,vertical=12.dp).fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                        Box(Modifier.glassPanel(page,GlassShape(28.dp),style=liquidStyle(dark),material=material(dark),stroke=liquidRim(dark),shading=false).padding(horizontal=20.dp,vertical=12.dp)) {
                            Text("DFRoot Next",fontSize=20.sp,fontWeight=FontWeight.Medium,color=ink)
                        }
                        Spacer(Modifier.weight(1f))
                        GlassIconButton(onClick={settings=true},backdrop=page,material=material(dark),style=liquidStyle(dark),stroke=liquidRim(dark),modifier=Modifier.semantics { contentDescription="Settings" }) { Symbol(R.drawable.ic_settings,ink,24) }
                    }
                        val canLaunch = !running && outcome==0 && selected!=null && !hooked && !BuildConfig.UI_INSPECTION
                        Box(Modifier.align(Alignment.BottomCenter)
                            .padding(bottom=insets.calculateBottomPadding()+28.dp,start=48.dp,end=48.dp)
                            .widthIn(max=300.dp).fillMaxWidth()
                            .glassPanel(page,GlassShape(30.dp),style=liquidStyle(dark),material=material(dark),stroke=liquidRim(dark),shadow=GlassShadow(Color.Black.copy(alpha=.32f),0f,12f,52f,.45f),shading=false)
                            .clickable(enabled=canLaunch,onClick=::launchRoot)
                            .semantics { contentDescription="Launch Root" }
                            .heightIn(min=54.dp).padding(horizontal=20.dp,vertical=14.dp),contentAlignment=Alignment.Center) {
                            Text(if(running) "Starting…" else if(hooked || outcome!=0) "Already launched" else "Launch Root",fontSize=15.sp,fontWeight=FontWeight.SemiBold,color=ink.copy(alpha=if(canLaunch) 1f else .48f))
                        }
                    GlassDialog(show=settings,onDismissRequest={ settings=false },backdrop=page,title="Settings", material=material(dark),stroke=liquidRim(dark)) {
                        Column(verticalArrangement=Arrangement.spacedBy(22.dp)) {
                            Setting("Start at Boot","Automatically run DFRoot on device boot",boot,ink,muted) {
                                packageManager.setComponentEnabledSetting(ComponentName(this@NextActivity,BootReceiver::class.java),if(it) PackageManager.COMPONENT_ENABLED_STATE_ENABLED else PackageManager.COMPONENT_ENABLED_STATE_DISABLED,PackageManager.DONT_KILL_APP); boot=it
                            }
                            Setting("Auto Soft Reboot","Applies to manual start and start at boot",soft,ink,muted) { prefs.edit().putBoolean("soft_reboot",it).apply();soft=it }
                            Setting("Disable KSU Modules","Recover from a broken module on the next launch",disableModules,ink,muted) { prefs.edit().putBoolean("disable_modules",it).apply(); disableModules=it }
                        }
                    }
                    GlassDialog(show=managers,onDismissRequest={managers=false},backdrop=page,title="SU manager",material=material(dark),stroke=liquidRim(dark)) {
                        Column(Modifier.heightIn(max=400.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(10.dp)) {
                            if(entries.isEmpty()) Text("Install a KernelSU-compatible manager first.",color=ink)
                            entries.forEach { (pkg,label) ->
                                Row(Modifier.fillMaxWidth().clickable { prefs.edit().putString(ExploitRunner.PREF_SU_MANAGER,pkg).apply(); selected=pkg; managers=false }.padding(vertical=14.dp),verticalAlignment=Alignment.CenterVertically) {
                                    ManagerIcon(pkg)
                                    Text(label,modifier=Modifier.weight(1f),fontSize=16.sp,color=ink)
                                    if(selected==pkg) Symbol(R.drawable.ic_check_circle, Color(0xff32b981), 22)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
private fun material(dark:Boolean,tint:Color?=null) = GlassMaterial(52.dp,GlassColorLayer(tint?.copy(alpha=.12f) ?: if(dark) Color(0xffc2d7f0).copy(alpha=.035f) else Color.White.copy(alpha=.10f),GlassColorBlendMode.SrcOver))
private fun liquidRim(dark:Boolean) = EdgeStroke(1.dp,4.dp,Color.White.copy(alpha=if(dark) .18f else .28f),EdgeLightSource(.05f,.05f,-.6f,intensity=1.5f),EdgeLightSource(.95f,.95f,-.4f,intensity=.9f),peak=EdgePeak.Axial)
private fun liquidStyle(dark:Boolean):GlassStyle {
    val s=GlassDefaults.style(dark)
    return s.copy(inner=s.inner.copy(tintStrength=0f,colorWhite=0f,colorMix=0f,alpha=.10f),blend=s.blend.copy(amount=0f,saturation=1.15f,brightness=0f,darker=0f),background=s.background.copy(unShade=0f,saturation=1.15f,brightness=0f,burn=1f))
}
@Composable private fun Panel(backdrop:Backdrop,dark:Boolean,tint:Color?=null,content:@Composable ColumnScope.()->Unit) {
    Column(Modifier.fillMaxWidth().glassPanel(backdrop,GlassShape(24.dp),style=liquidStyle(dark),material=material(dark,tint),stroke=liquidRim(dark),shadow=null,shading=false).padding(18.dp),content=content)
}
@Composable private fun Info(icon:String,title:String,value:String,ink:Color,muted:Color,last:Boolean=false) {
    Row(Modifier.fillMaxWidth().padding(bottom=if(last) 0.dp else 16.dp),verticalAlignment=Alignment.Top) {
        DeviceIcon(icon, ink)
        Column(Modifier.weight(1f)) { Text(title,fontSize=15.sp,fontWeight=FontWeight.Medium,color=ink);Spacer(Modifier.height(3.dp)); Text(value,fontSize=12.sp,color=muted) }
    }
}
@Composable private fun Setting(title:String,summary:String,checked:Boolean,ink:Color,muted:Color,onChange:(Boolean)->Unit) {
    Row(verticalAlignment=Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(end=12.dp)) { Text(title,fontSize=15.sp,color=ink);Spacer(Modifier.height(3.dp));Text(summary,fontSize=12.sp,color=muted) }
        Switch(checked=checked,onCheckedChange=onChange)
    }
}

@Composable private fun DeviceIcon(kind:String,color:Color) {
    Box(Modifier.width(36.dp).padding(top=3.dp)) {
        Symbol(when(kind) {
            "#" -> R.drawable.ic_tag
            "▧" -> R.drawable.ic_memory
            "▯" -> R.drawable.ic_smartphone
            "◎" -> R.drawable.ic_fingerprint
            else -> R.drawable.ic_schedule
        },color,24)
    }
}
@Composable private fun Symbol(id:Int,color:Color,size:Int) {
    Image(painterResource(id),contentDescription=null,colorFilter=ColorFilter.tint(color),modifier=Modifier.size(size.dp))
}

/** Live markers survive a soft reboot but disappear with the kernel session. No su prompt. */
object RootUiState {
    @JvmStatic fun resolve(hooked:Boolean,success:Boolean,failed:Boolean,previous:Int):Int = when {
        success && hooked -> 1
        failed -> -1
        !hooked -> if(previous == 1) 0 else previous
        else -> previous
    }
}
