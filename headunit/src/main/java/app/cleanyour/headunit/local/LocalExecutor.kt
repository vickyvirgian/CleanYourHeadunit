package app.cleanyour.headunit.local

import app.cleanyour.headunit.Catalog
import app.cleanyour.headunit.LocalPolicy
import org.json.JSONObject
import java.util.concurrent.*

object LocalExecutor {
    private fun shell(root: Boolean, script: String): String {
        val process=ProcessBuilder(if(root) listOf("su","-c",script) else listOf("sh","-c",script)).redirectErrorStream(true).start()
        val reader=Executors.newSingleThreadExecutor()
        val output=reader.submit<String> { process.inputStream.bufferedReader().use { input ->
            val result=StringBuilder();val buffer=CharArray(4096)
            while(true) {val n=input.read(buffer);if(n<0)break;check(result.length+n<1048576){"Output terlalu besar."};result.append(buffer,0,n)}
            result.toString()
        } }
        try {
            check(process.waitFor(30,TimeUnit.SECONDS)) { "Waktu akses habis. Periksa dialog izin root." }
            val text=output.get(2,TimeUnit.SECONDS)
            check(process.exitValue()==0) {text.take(600).ifBlank {"Sistem menolak perintah."}}
            return text
        } finally {process.destroy();output.cancel(true);reader.shutdownNow()}
    }
    fun execute(root:Boolean,op:String,arg:String):String = try {
        LocalPolicy.command(op,arg) // Validate before running anything privileged.
        val uid=shell(root,LocalPolicy.command("identity")).trim()
        check(uid=="0" || uid=="2000") {"Akses root atau shell diperlukan; izin aplikasi biasa tidak cukup."}
        if(op=="disable" || op=="enable") {
            val device=shell(root,LocalPolicy.command("device"))
            val c=Catalog.entries.first {it.pkg==arg}
            check(!c.vendor || device.contains("8227",true)) {"Profil vendor tidak cocok."}
            val home=shell(root,LocalPolicy.command("home")).lineSequence().map {it.trim()}.firstOrNull {'/' in it}?.substringBefore('/')
            check(!home.isNullOrBlank()) {"Launcher aktif tidak bisa dipastikan."}
            val ime=shell(root,LocalPolicy.command("ime")).trim().substringBefore('/')
            check(arg !in Catalog.protected && arg!=home && arg!=ime) {"Aplikasi sedang dipakai sistem dan dilindungi."}
            val state=if(op=="disable")"enabled" else "disabled"
            check(shell(root,LocalPolicy.command(state)).lineSequence().any {it.trim()=="package:$arg"}) {"Status aplikasi berubah. Periksa ulang."}
        }
        val text=shell(root,LocalPolicy.command(op,arg))
        if(op=="disable" || op=="enable") {
            val state=if(op=="disable")"disabled" else "enabled"
            check(shell(root,LocalPolicy.command(state)).lineSequence().any {it.trim()=="package:$arg"}) {"Perubahan tidak bertahan; firmware mungkin mengaktifkan ulang aplikasi."}
        }
        JSONObject().put("ok",true).put("text",text).toString()
    } catch(e:Exception) {JSONObject().put("ok",false).put("text",e.message ?: "Operasi gagal.").toString()}
}

class PrivilegedService : IHeadunitService.Stub() {
    override fun execute(operation:String,argument:String):String = LocalExecutor.execute(false,operation,argument)
    override fun destroy() { kotlin.system.exitProcess(0) }
}
