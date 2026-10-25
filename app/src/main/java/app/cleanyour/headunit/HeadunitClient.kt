@file:OptIn(tv.cleanyour.core.model.RawShell::class)
package app.cleanyour.headunit

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import kotlinx.coroutines.*
import tv.cleanyour.adb.wire.*
import tv.cleanyour.android.platform.PlainSocketFactory
import tv.cleanyour.core.model.*
import java.io.ByteArrayOutputStream
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.interfaces.RSAPublicKey

class PhoneSigner : AdbSigner {
    private val alias = "cleanyourheadunit.adb.v1"
    private val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
    init {
        if (!store.containsAlias(alias)) {
            KeyPairGenerator.getInstance("RSA", "AndroidKeyStore").apply {
                initialize(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_SIGN)
                    .setKeySize(2048).setDigests(KeyProperties.DIGEST_NONE)
                    .setSignaturePaddings(KeyProperties.SIGNATURE_PADDING_RSA_PKCS1).build())
            }.generateKeyPair()
        }
    }
    private val publicKey get() = store.getCertificate(alias).publicKey as RSAPublicKey
    override val publicKeyLine get() = AdbPublicKeyCodec.line(publicKey)
    override val fingerprint get() = AdbPublicKeyCodec.fingerprint(publicKey)
    override val backing = KeyBacking.TEE
    override suspend fun sign(token: AuthToken) = AdbSignature.sign(store.getKey(alias, null) as java.security.PrivateKey, token)
    override suspend fun reset() { error("Reset key is not exposed in this version") }
}

class HeadunitClient(private val context: Context) {
    private var connection: AdbConnection? = null
    var identity = ""; private set
    var summary = ""; private set
    var vendor8227 = false; private set
    suspend fun connect(ip: String, port: Int, awaiting: suspend (String) -> Unit) = withContext(Dispatchers.IO) {
        require(ip.matches(Regex("(?:[0-9]{1,3}\\.){3}[0-9]{1,3}")) && ip.split('.').all { it.toInt() in 0..255 }) { "Masukkan alamat IPv4 head unit." }
        val a = ip.split('.').map(String::toInt)
        require(a[0] == 10 || (a[0] == 172 && a[1] in 16..31) || (a[0] == 192 && a[1] == 168)) { "Gunakan IP jaringan lokal head unit." }
        require(port in 1..65535) { "Port harus 1–65535." }
        close()
        val signer = PhoneSigner()
        check(signer.selfTest() == SignerSelfTest.Pass) { "Kunci ADB HP tidak bisa digunakan." }
        val socket = PlainSocketFactory().connect(Endpoint.PlainTcp(ip, port), 5000)
        try { connection = AdbConnection.open(socket, signer, null, awaiting, 45000) } catch(e: Exception) { socket.close(); throw e }
        try {
            check(Regex("uid=(0|2000)\\b").containsMatchIn(exec("id"))) { "ADB tidak memberi akses shell." }
            summary = exec("getprop ro.product.manufacturer; getprop ro.product.model; getprop ro.build.version.release").trim().replace('\n',' ')
            vendor8227 = exec("getprop ro.product.device; getprop ro.board.platform").contains("8227", true)
            identity = readIdentity()
        } catch(e: Exception) { close(); throw e }
    }
    suspend fun installHelper() = withContext(Dispatchers.IO) {
        check(identity == readIdentity()) { "Target berubah." }
        val conn = connection ?: error("Hubungkan dahulu.")
        val stream = conn.openStream(AdbService.Sync)
        try {
            val sync = SyncClient(stream, 20000, "helper")
            context.assets.open("headunit-helper.apk").use { input ->
                sync.send("/data/local/tmp/cleanyourheadunit-helper.apk", 33188, 0, {
                    val buf = ByteArray(32768); val n=input.read(buf)
                    if(n<0)null else buf.copyOf(n)
                })
            }
            sync.quit()
        } finally { stream.close() }
        val result = exec("pm install -r /data/local/tmp/cleanyourheadunit-helper.apk")
        check(result.lineSequence().any {it.trim()=="Success"}) { "Helper gagal dipasang: ${result.take(200)}" }
        exec("rm -f /data/local/tmp/cleanyourheadunit-helper.apk")
        exec("pm enable --user 0 app.cleanyour.headunit.helper")
        openHelper()
    }
    suspend fun openHelper() { exec("am start -n app.cleanyour.headunit.helper/.MainActivity") }
    private suspend fun readIdentity(): String {
        val id = exec("settings get secure android_id").trim()
        check(id.matches(Regex("[0-9a-fA-F]{8,64}"))) { "Identitas head unit tidak dapat dipastikan." }
        val facts = exec("getprop ro.build.fingerprint; getprop ro.serialno").trim()
        return java.security.MessageDigest.getInstance("SHA-256").digest("$id\n$facts".toByteArray()).joinToString("") { "%02x".format(it) }
    }
    suspend fun ensureHelper() {
        val shizuku = packages("-e")
        check("moe.shizuku.privileged.api" in shizuku) { "Shizuku wajib terpasang dan aktif di head unit." }
        val result = exec("content call --uri content://app.cleanyour.headunit.helper.status --method status")
        check(Regex("\\bprotocol=1\\b").containsMatchIn(result) && Regex("\\bready=true\\b").containsMatchIn(result)) {
            "Buka Headunit Helper di head unit. Jalankan Shizuku dan berikan izin ke Helper, lalu hubungkan lagi."
        }
    }
    suspend fun packages(flag: String) = exec("pm list packages $flag --user 0").lineSequence()
        .filter { it.startsWith("package:") }.map { it.removePrefix("package:").trim() }.toSet()
    private suspend fun protectedNow(): Set<String> {
        val home = exec("cmd package resolve-activity --brief --user 0 -a android.intent.action.MAIN -c android.intent.category.HOME")
            .lineSequence().map { it.trim() }.firstOrNull { '/' in it }?.substringBefore('/')
        check(home != null && PACKAGE_RE.matches(home)) { "Launcher aktif tidak bisa dipastikan; pembersihan diblokir." }
        val ime = exec("settings get secure default_input_method").trim().substringBefore('/')
        return Catalog.protected + setOf(home, ime)
    }
    suspend fun scan(): List<Candidate> {
        ensureHelper()
        check(identity == readIdentity()) { "Identitas head unit berubah. Hubungkan ulang." }
        val enabled = packages("-e"); val blocked = protectedNow()
        return Catalog.eligible(enabled, blocked, vendor8227)
    }
    suspend fun disable(pkg: String) {
        require(Catalog.entries.any { it.pkg == pkg && (!it.vendor || vendor8227) })
        ensureHelper()
        check(identity == readIdentity()) { "Target berubah." }
        check(pkg !in protectedNow()) { "Paket ini dilindungi." }
        check(pkg in packages("-e")) { "Status aplikasi sudah berubah. Periksa ulang." }
        remember(pkg, true) // Record intent before the mutation, including uncertain outcomes.
        exec("pm disable-user --user 0 $pkg")
        check(pkg in packages("-d")) { "Firmware mengaktifkan kembali aplikasi ini atau menolak perubahan." }
    }
    suspend fun restore(pkg: String) {
        require(PACKAGE_RE.matches(pkg) && Catalog.entries.any { it.pkg == pkg })
        check(pkg in changed())
        ensureHelper(); check(identity == readIdentity()) { "Target berubah." }
        if (pkg in packages("-d")) exec("pm enable --user 0 $pkg")
        check(pkg in packages("-e")) { "Aplikasi belum aktif." }
        remember(pkg, false)
    }
    fun changed(): Set<String> = context.getSharedPreferences("changes",0).getStringSet(identity,emptySet())!!.toSet()
    private fun remember(pkg: String, add: Boolean) {
        val next=changed().toMutableSet().apply { if(add) add(pkg) else remove(pkg) }
        check(context.getSharedPreferences("changes",0).edit().putStringSet(identity,next).commit()) { "Catatan perubahan gagal disimpan; tindakan dihentikan." }
    }
    suspend fun exec(line: String): String = withContext(Dispatchers.IO) {
        val conn = connection ?: error("Hubungkan head unit dahulu.")
        try {
            withTimeout(20000) {
                val stream = conn.openStream(AdbService.ShellV2(ShellLine(line)))
                try {
                    stream.write(ShellV2.encode(ShellV2.ID_CLOSE_STDIN))
                    val reader=AdbStreamReader(stream); val stdout=ByteArrayOutputStream();val stderr=ByteArrayOutputStream();var exit:Int?=null
                    while(exit == null) {
                        when(val p=ShellV2.next(reader) ?: error("ADB terputus sebelum hasil diterima.")) {
                            is ShellPacket.Stdout -> stdout.write(p.bytes)
                            is ShellPacket.Stderr -> stderr.write(p.bytes)
                            is ShellPacket.Exit -> exit=p.code
                        }
                        check(stdout.size()+stderr.size() <= 1024*1024) { "Output perangkat terlalu besar." }
                    }
                    check(exit==0) { stderr.toString("UTF-8").take(300).ifBlank { stdout.toString("UTF-8").take(300) }.ifBlank { "Perintah ditolak perangkat ($exit)." } }
                    stdout.toString("UTF-8")
                } finally { stream.close() }
            }
        } catch(e: Exception) { close(); throw e }
    }
    suspend fun close() { connection?.close();connection=null }
}
