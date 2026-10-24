package app.cleanyour.headunit

import android.app.*
import android.os.Bundle
import android.content.Intent
import android.net.Uri
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.widget.*
import android.text.InputType
import kotlinx.coroutines.*

class MainActivity : Activity() {
    private val scope=CoroutineScope(SupervisorJob()+Dispatchers.Main)
    private lateinit var client:HeadunitClient
    private lateinit var body:LinearLayout
    private lateinit var status:TextView
    private val actions=mutableListOf<Button>()
    private var busy=false
    private val accent=Color.rgb(232,168,76)
    private val selected=linkedSetOf<String>()
    override fun onCreate(state:Bundle?) {super.onCreate(state);client=HeadunitClient(applicationContext);setup()}
    private fun dp(n:Int)=(n*resources.displayMetrics.density).toInt()
    private fun page(step:String) {
        actions.clear()
        window.statusBarColor=Color.rgb(21,24,30);window.navigationBarColor=Color.rgb(21,24,30)
        val scroll=ScrollView(this);scroll.setBackgroundColor(Color.rgb(21,24,30))
        body=LinearLayout(this).apply {orientation=LinearLayout.VERTICAL;setPadding(dp(24),dp(24),dp(24),dp(32))};scroll.addView(body);setContentView(scroll)
        text("CLEAN YOUR HEADUNIT",15,accent)
        text(step,30,Color.WHITE,true)
        status=text("",16,Color.rgb(190,196,207))
    }
    private fun text(s:String,size:Int=16,color:Int=Color.WHITE,bold:Boolean=false):TextView {
        val t=TextView(this);t.text=s;t.textSize=size.toFloat();t.setTextColor(color);if(bold)t.setTypeface(null,Typeface.BOLD)
        t.setPadding(0,dp(8),0,dp(8));body.addView(t);return t
    }
    private fun action(label:String,fn:()->Unit):Button {
        val b=Button(this);b.text=label;b.isAllCaps=false;b.textSize=16f;b.minHeight=dp(52);b.setTextColor(Color.rgb(21,24,30))
        b.background=GradientDrawable().apply {setColor(accent);cornerRadius=dp(12).toFloat()}
        body.addView(b,LinearLayout.LayoutParams(-1,dp(56)).apply {topMargin=dp(12)})
        b.setOnClickListener {if(!busy)fn()};actions.add(b);return b
    }
    private fun work(message:String,fn:suspend ()->Unit) {
        if(busy)return
        busy=true;actions.forEach {it.isEnabled=false};status.text=message
        scope.launch {
            try {fn()} catch(e:CancellationException){throw e} catch(e:Exception){status.text=e.message ?: "Koneksi gagal. Periksa alamat, port, dan izin ADB di head unit."}
            finally {busy=false;actions.forEach {it.isEnabled=true}}
        }
    }
    private fun setup() {
        page("Siapkan koneksi")
        text("Bersihkan head unit dari HP. Perubahan hanya berjalan setelah ADB dan Shizuku di head unit lolos pemeriksaan.")
        text("1  Siapkan head unit",22,Color.WHITE,true)
        text("Pasang Shizuku dan Headunit Helper di head unit. Jalankan Shizuku, buka Helper, lalu berikan izin. Hubungkan HP dan head unit ke jaringan Wi-Fi yang sama.")
        action("Panduan Shizuku") {try {startActivity(Intent(Intent.ACTION_VIEW,Uri.parse("https://shizuku.rikka.app/guide/setup/")))}catch(e:Exception){status.text="Buka shizuku.rikka.app/guide/setup di browser."}}
        text("2  Hubungkan ADB Wi-Fi",22,Color.WHITE,true)
        text("Versi 0.1 memakai port ADB TCP. Port pairing Wireless debugging Android 11+ belum didukung. Shizuku aktif tidak otomatis membuka port ADB.")
        val prefs=getSharedPreferences("connection",0)
        text("Alamat IP head unit")
        val ip=EditText(this).apply {setSingleLine();textSize=18f;setTextColor(Color.WHITE);inputType=InputType.TYPE_CLASS_PHONE;hint="192.168.1.100";setHintTextColor(Color.GRAY);setText(prefs.getString("ip",""));minHeight=dp(52)};body.addView(ip)
        text("Port ADB")
        val port=EditText(this).apply {setSingleLine();textSize=18f;setTextColor(Color.WHITE);inputType=InputType.TYPE_CLASS_NUMBER;setText(prefs.getString("port","8555"));minHeight=dp(52)};body.addView(port)
        action("Hubungkan dan periksa Shizuku") {
            val address=ip.text.toString().trim();val number=port.text.toString().toIntOrNull() ?: 0
            work("Menghubungkan… Izinkan koneksi ADB pada layar head unit jika diminta.") {
                client.connect(address,number) { fingerprint -> withContext(Dispatchers.Main) {status.text="Izinkan ADB di head unit. Sidik jari HP:\n$fingerprint"} }
                prefs.edit().putString("ip",address).putString("port",number.toString()).apply()
                showGate()
            }
        }
        text("Android 8–10 memerlukan akses ADB awal atau root untuk memulai Shizuku. Versi ini tidak membuka debugging tanpa izin pemilik perangkat.",14,Color.LTGRAY)
    }
    private fun showGate() {
        page("Periksa Shizuku")
        status.text="${client.summary}\nADB Wi-Fi tersambung dan izin shell terverifikasi."
        text("Shizuku wajib berjalan di head unit. Helper memeriksa statusnya secara langsung. Pembersihan tetap terkunci sampai pemeriksaan berhasil.")
        action("Pasang / perbarui Helper di head unit") {
            work("Mengirim Helper ke head unit…") {client.installHelper();status.text="Helper terpasang. Lihat layar head unit, jalankan Shizuku dan berikan izin ke Helper."}
        }
        action("Buka Helper di head unit") {work("Membuka Helper…"){client.openHelper();status.text="Selesaikan izin Shizuku di layar head unit."}}
        action("Periksa Shizuku dan lanjutkan") {work("Memeriksa Shizuku…"){showCandidates(client.scan())}}
        action("Kembali ke koneksi") {work("Memutuskan…"){client.close();setup()}}
    }
    private fun showCandidates(list:List<Candidate>) {
        selected.clear();page("Pilih yang dibersihkan")
        status.text="${client.summary}\nADB tersambung · Shizuku berjalan · Izin Helper diberikan"
        text("Radio, CAN bus, MCU, Bluetooth, microG, WebView, keyboard dan launcher aktif dilindungi. Daftar vendor hanya ditampilkan pada perangkat keluarga 8227. Semua pilihan dimulai kosong.")
        if(list.isEmpty())text("Tidak ada kandidat aktif yang cocok dengan daftar versi ini. Aplikasi yang tidak dikenal tidak dimatikan otomatis.")
        list.forEach {c->
            val box=CheckBox(this).apply {text="${c.title}\n${c.pkg}";textSize=17f;setTextColor(Color.WHITE);minHeight=dp(56);setOnCheckedChangeListener {_,checked->if(checked)selected.add(c.pkg)else selected.remove(c.pkg)}}
            body.addView(box);text(c.note,14,Color.LTGRAY)
        }
        action("Matikan aplikasi yang dipilih") {
            val plan=selected.toList()
            if(plan.isEmpty()){status.text="Pilih minimal satu aplikasi.";return@action}
            AlertDialog.Builder(this).setTitle("Matikan ${plan.size} aplikasi?")
                .setMessage("Aplikasi yang dipilih akan dinonaktifkan untuk pengguna utama head unit. Efek tiap aplikasi tercantum di daftar.")
                .setNegativeButton("Batal",null).setPositiveButton("Matikan") {_,_->
                    work("Membersihkan…") {
                        var done=0
                        try {for(pkg in plan){client.disable(pkg);done++;status.text="$done dari ${plan.size} aplikasi dimatikan."}}
                        catch(e:Exception){status.text="$done aplikasi terverifikasi dimatikan. ${e.message}\nHubungkan ulang untuk memeriksa hasil yang belum pasti.";return@work}
                        showCandidates(client.scan());status.text="$done aplikasi berhasil dimatikan.\n${client.summary}"
                    }
                }.show()
        }
        if(client.changed().isNotEmpty()) action("Aplikasi yang pernah dimatikan") {showChanges()}
        action("Periksa ulang") {work("Memeriksa…"){showCandidates(client.scan())}}
        action("Putuskan koneksi") {work("Memutuskan…"){client.close();setup()}}
        text("Daftar ini berisi fungsi opsional, bukan vonis malware. Mematikan pembaruan, sinkronisasi atau suara akan menghentikan fungsi tersebut.",14,Color.LTGRAY)
    }
    private fun showChanges() {
        page("Aktifkan lagi")
        text("Catatan ini hanya untuk head unit yang sedang tersambung. Jika hasil perubahan sebelumnya belum pasti, status perangkat diperiksa sebelum diaktifkan lagi.")
        client.changed().sorted().forEach {pkg->action("Aktifkan $pkg") {work("Mengaktifkan…"){client.restore(pkg);showChanges();status.text="Aplikasi sudah aktif."}}}
        action("Kembali ke daftar") {work("Memeriksa…"){showCandidates(client.scan())}}
    }
    override fun onDestroy() {scope.cancel();CoroutineScope(Dispatchers.IO).launch{client.close()};super.onDestroy()}
}
