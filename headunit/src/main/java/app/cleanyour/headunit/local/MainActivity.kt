package app.cleanyour.headunit.local

import android.app.*
import android.os.*
import android.content.*
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.provider.Settings
import android.widget.*
import app.cleanyour.headunit.Catalog
import app.cleanyour.headunit.Candidate
import kotlinx.coroutines.*
import org.json.JSONObject
import rikka.shizuku.Shizuku
import java.net.NetworkInterface
import java.net.Inet4Address
import java.util.Collections

class MainActivity:Activity() {
    private val scope=CoroutineScope(SupervisorJob()+Dispatchers.Main)
    private var remote:IHeadunitService?=null
    private var root=false
    private var busy=false
    private lateinit var body:LinearLayout
    private lateinit var status:TextView
    private val buttons=mutableListOf<Button>()
    private val selected=linkedSetOf<String>()
    private val prefs by lazy {getSharedPreferences("local_changes",0)}
    private val args by lazy {Shizuku.UserServiceArgs(ComponentName(this,PrivilegedService::class.java)).daemon(false).processNameSuffix("shell").debuggable(false).version(1)}
    private val connection=object:ServiceConnection {
        override fun onServiceConnected(name:ComponentName,binder:IBinder) {
            remote=IHeadunitService.Stub.asInterface(binder);root=false
            if(!isFinishing)home("Akses Shizuku tersambung. Debloat bisa dijalankan langsung di perangkat.")
        }
        override fun onServiceDisconnected(name:ComponentName) {remote=null;if(!isFinishing)home("Akses Shizuku terputus. Jalankan lagi untuk melanjutkan.")}
    }
    private val permission=Shizuku.OnRequestPermissionResultListener {_,grant ->
        if(grant==PackageManager.PERMISSION_GRANTED)bind() else status.text="Izin Shizuku belum diberikan."
    }
    private val dead=Shizuku.OnBinderDeadListener {runOnUiThread {remote=null;if(!root && !isFinishing)home("Layanan Shizuku berhenti.")}}
    override fun onCreate(state:Bundle?) {
        super.onCreate(state);Shizuku.addRequestPermissionResultListener(permission);Shizuku.addBinderDeadListener(dead);home()
    }
    private fun dp(n:Int)=(n*resources.displayMetrics.density).toInt()
    private fun font(name:String)=runCatching {Typeface.createFromAsset(assets,"fonts/$name.ttf")}.getOrDefault(Typeface.SANS_SERIF)
    private val ui by lazy {font("Geist-Regular")}
    private val mono by lazy {font("GeistMono-Regular")}
    private val dot by lazy {font("Doto-ROND-wght")}
    private fun page(title:String) {
        buttons.clear();window.statusBarColor=Color.BLACK;window.navigationBarColor=Color.BLACK
        val scroll=ScrollView(this);scroll.setBackgroundColor(Color.BLACK)
        body=LinearLayout(this).apply {orientation=LinearLayout.VERTICAL;setPadding(dp(24),dp(20),dp(24),dp(24))}
        scroll.addView(body);setContentView(scroll)
        text("CLEAN YOUR HEADUNIT",20).typeface=dot
        text(title,28)
        status=text("",16,Color.LTGRAY)
    }
    private fun text(s:String,size:Int=16,color:Int=Color.WHITE):TextView {
        val t=TextView(this);t.text=s;t.textSize=size.toFloat();t.typeface=ui;t.setTextColor(color);t.setPadding(0,dp(8),0,dp(8));body.addView(t);return t
    }
    private fun action(label:String,run:()->Unit) {
        val b=Button(this);b.text=label;b.isAllCaps=false;b.textSize=16f;b.typeface=ui;b.setTextColor(Color.BLACK)
        b.background=GradientDrawable().apply {setColor(Color.WHITE);cornerRadius=dp(6).toFloat()}
        body.addView(b,LinearLayout.LayoutParams(-1,dp(56)).apply {topMargin=dp(12)})
        b.setOnClickListener {if(!busy)run()};buttons.add(b)
    }
    private fun work(message:String,run:suspend ()->Unit) {
        if(busy)return
        busy=true;buttons.forEach {it.isEnabled=false};status.text=message;status.setTextColor(Color.LTGRAY)
        scope.launch {try {run()}catch(e:CancellationException){throw e}catch(e:Exception){status.text=e.message ?: "Operasi gagal.";status.setTextColor(Color.rgb(255,68,56))}
            finally {busy=false;buttons.forEach {it.isEnabled=true}}}
    }
    private suspend fun op(operation:String,arg:String=""):String=withContext(Dispatchers.IO) {
        val payload=if(root)LocalExecutor.execute(true,operation,arg) else (remote ?: error("Pilih akses Shizuku atau root dahulu.")).execute(operation,arg)
        val result=JSONObject(payload);check(result.getBoolean("ok")){result.optString("text","Sistem menolak operasi.")};result.getString("text")
    }
    private fun bind() {
        try {Shizuku.bindUserService(args,connection);status.text="Menghubungkan akses Shizuku…"}catch(e:Exception){status.text=e.message}
    }
    private fun shizuku() {
        try {
            if(!Shizuku.pingBinder()){status.text="Shizuku belum berjalan. Buka dan jalankan dahulu.";return}
            if(Shizuku.isPreV11()){status.text="Perbarui Shizuku ke versi yang didukung.";return}
            if(Shizuku.checkSelfPermission()==PackageManager.PERMISSION_GRANTED)bind() else Shizuku.requestPermission(41)
        } catch(e:Exception){status.text=e.message}
    }
    private fun launch(i:Intent) {try {startActivity(i)}catch(e:Exception){status.text="Halaman ini tidak tersedia pada firmware perangkat."}}
    private fun home(message:String="") {
        page("Langsung dari head unit")
        status.text=message.ifBlank {"${Build.MANUFACTURER} ${Build.MODEL} · Android ${Build.VERSION.RELEASE} · API ${Build.VERSION.SDK_INT}\nAkses: ${if(root)"root" else if(remote!=null)"Shizuku" else "belum dipilih"}"}
        text("01 / AKSES",14,Color.GRAY).typeface=mono
        text("Debloat memakai izin root atau Shizuku yang sudah berjalan. Terminal emulator biasa tidak otomatis memiliki izin shell.")
        action("Gunakan Shizuku") {shizuku()}
        action("Buka / pasang Shizuku") {
            val i=packageManager.getLaunchIntentForPackage("moe.shizuku.privileged.api")
            if(i!=null)launch(i)else launch(Intent(Intent.ACTION_VIEW,Uri.parse("https://shizuku.rikka.app/download/")))
        }
        action("Gunakan akses root") {work("Menunggu izin root dari pengelola root perangkat…") {
            val result=withContext(Dispatchers.IO){JSONObject(LocalExecutor.execute(true,"identity",""))}
            check(result.getBoolean("ok")){result.optString("text")};check(result.getString("text").trim()=="0"){"Root tidak tersedia."}
            root=true;home("Akses root terverifikasi. Pembersihan tersedia langsung di perangkat.")
        }}
        text("02 / ADB WI-FI",14,Color.GRAY).typeface=mono
        text("Coba membuka ADB jaringan di port 8555. Root dapat mengubah layanan sistem; akses Shizuku shell tetap mengikuti pembatasan firmware.")
        action("Periksa ADB Wi-Fi port 8555") {work("Memeriksa listener ADB lokal…") {
            val ready=withContext(Dispatchers.IO){AdbProbe.listening(8555)}
            status.text=if(ready)"Layanan ADB merespons pada port 8555. IP perangkat: ${ips()}. Koneksi dari HP tetap perlu diuji dan diizinkan." else "Layanan ADB belum merespons pada port 8555."
        }}
        action("Coba aktifkan ADB Wi-Fi") {
            if(!root && remote==null){status.text="Pilih akses root atau Shizuku dahulu.";return@action}
            AlertDialog.Builder(this).setTitle("Aktifkan ADB jaringan?").setMessage("Aplikasi akan mencoba mengubah layanan ADB ke port 8555. Koneksi ADB yang sedang berjalan dapat terputus saat layanan dimulai ulang.")
                .setNegativeButton("Batal",null).setPositiveButton("Coba aktifkan"){_,_->work("Mencoba menyalakan ADB Wi-Fi…") {
                    val failure=try {op("tcp","8555");null}catch(e:Exception){e.message}
                    delay(1500)
                    val ready=withContext(Dispatchers.IO){AdbProbe.listening(8555)}
                    status.text=if(ready)"ADB merespons pada port 8555. IP: ${ips()}. Uji koneksi dari HP untuk memastikan jaringan bisa menjangkaunya." else "ADB belum aktif. ${failure ?: "Firmware belum membuka listener ADB."}"
                }}.show()
        }
        action("Buka pengaturan debugging") {launch(Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS))}
        action("Matikan ADB jaringan") {
            if(!root && remote==null){status.text="Pilih akses root atau Shizuku dahulu.";return@action}
            AlertDialog.Builder(this).setTitle("Matikan ADB jaringan?").setMessage("Koneksi ADB melalui jaringan akan diputus. USB debugging tidak dimatikan.")
                .setNegativeButton("Batal",null).setPositiveButton("Matikan"){_,_->work("Mematikan ADB jaringan…") {
                    op("tcpOff");delay(1500)
                    val active=withContext(Dispatchers.IO){AdbProbe.listening(8555) || AdbProbe.listening(5555)}
                    status.text=if(active)"ADB masih merespons. Firmware belum mematikan listener jaringan." else "Perintah mematikan ADB jaringan selesai. Port 5555 dan 8555 tidak merespons."
                }}.show()
        }
        text("03 / BERSIHKAN",14,Color.GRAY).typeface=mono
        action("Periksa aplikasi untuk debloat") {work("Membaca aplikasi perangkat…"){scan()}}
        if(changed().isNotEmpty())action("Aktifkan lagi aplikasi") {restorePage()}
        action("Pilih launcher utama") {launch(Intent(Settings.ACTION_HOME_SETTINGS))}
        text("Versi 0.2 · Pemeriksaan dan debloat lokal. Tanpa root, Shizuku aktif atau ADB yang sudah diizinkan, aplikasi tidak bisa menerobos izin sistem.",14,Color.GRAY)
    }
    private fun packages(raw:String)=raw.lineSequence().filter {it.startsWith("package:")}.map {it.removePrefix("package:").trim()}.toSet()
    private suspend fun scan() {
        val enabled=packages(op("enabled"));val vendor=op("device").contains("8227",true)
        val home=op("home").lineSequence().firstOrNull {'/' in it}?.trim()?.substringBefore('/')
        check(!home.isNullOrEmpty()){ "Launcher aktif tidak dapat dipastikan." }
        val ime=op("ime").trim().substringBefore('/')
        val list=Catalog.eligible(enabled,setOf(home,ime),vendor)
        candidates(list)
    }
    private fun candidates(list:List<Candidate>) {
        selected.clear();page("Pilih aplikasi")
        text("Radio, CAN, MCU, Bluetooth, microG, WebView, keyboard dan launcher aktif dilindungi. Pilihan awal kosong. Paket vendor hanya muncul untuk keluarga 8227.")
        if(list.isEmpty())text("Tidak ada kandidat aktif yang cocok. Paket tak dikenal tidak dimatikan otomatis.")
        list.forEach {c->
            body.addView(CheckBox(this).apply {text="${c.title}\n${c.pkg}";textSize=17f;typeface=ui;setTextColor(Color.WHITE);minHeight=dp(56);setOnCheckedChangeListener {_,yes->if(yes)selected.add(c.pkg)else selected.remove(c.pkg)}})
            text(c.note,14,Color.LTGRAY)
        }
        action("Matikan yang dipilih") {
            val plan=selected.toList();if(plan.isEmpty()){status.text="Pilih minimal satu aplikasi.";return@action}
            AlertDialog.Builder(this).setTitle("Matikan ${plan.size} aplikasi?").setMessage("Fungsi aplikasi yang dipilih berhenti. Aplikasi hanya dinonaktifkan untuk pengguna utama.").setNegativeButton("Batal",null)
                .setPositiveButton("Matikan"){_,_->work("Membersihkan…") {
                    var count=0
                    try {for(pkg in plan){remember(pkg,true);op("disable",pkg);count++}}
                    catch(e:Exception){status.text="$count aplikasi terverifikasi dimatikan. ${e.message}";return@work}
                    scan();status.text="$count aplikasi berhasil dimatikan."
                }}.show()
        }
        action("Kembali") {home()}
    }
    private fun changed()=prefs.getStringSet("packages",emptySet())!!.toSet()
    private fun remember(pkg:String,add:Boolean) {val all=changed().toMutableSet().apply {if(add)add(pkg)else remove(pkg)};check(prefs.edit().putStringSet("packages",all).commit()){ "Catatan perubahan gagal disimpan." }}
    private fun restorePage() {
        page("Aktifkan lagi")
        text("Hanya perubahan yang dicatat aplikasi ini. Status aktual diperiksa sebelum mengaktifkan kembali.")
        changed().sorted().forEach {pkg->action("Aktifkan $pkg"){work("Memeriksa aplikasi…") {
            if(pkg in packages(op("disabled")))op("enable",pkg)
            check(pkg in packages(op("enabled"))){"Aplikasi belum aktif."};remember(pkg,false);restorePage();status.text="Aplikasi sudah aktif."
        }}}
        action("Kembali") {home()}
    }
    private fun ips():String=runCatching {Collections.list(NetworkInterface.getNetworkInterfaces()).flatMap {Collections.list(it.inetAddresses)}.filter {it is Inet4Address && !it.isLoopbackAddress}.joinToString {it.hostAddress ?: ""}}.getOrDefault("lihat pengaturan Wi-Fi")
    override fun onDestroy() {
        scope.cancel();Shizuku.removeRequestPermissionResultListener(permission);Shizuku.removeBinderDeadListener(dead)
        try {if(remote!=null)Shizuku.unbindUserService(args,connection,true)}catch(_:Exception){}
        remote=null;super.onDestroy()
    }
}
