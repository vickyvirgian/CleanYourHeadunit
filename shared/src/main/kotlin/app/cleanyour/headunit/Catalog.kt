package app.cleanyour.headunit

data class Candidate(val pkg: String, val title: String, val note: String, val vendor: Boolean = false)
object Catalog {
    val entries = listOf(
        Candidate("com.google.android.feedback", "Google Feedback", "Laporan feedback Google."),
        Candidate("com.google.android.printservice.recommendation", "Rekomendasi printer", "Saran layanan printer."),
        Candidate("com.google.android.googlequicksearchbox", "Google Search / Assistant", "Pencarian dan asisten suara Google akan berhenti."),
        Candidate("com.google.android.tts", "Google Text-to-Speech", "Suara navigasi dan pembaca teks yang memakai mesin ini dapat berhenti."),
        Candidate("com.android.vending", "Play Store", "Unduh dan pembaruan aplikasi melalui Play Store berhenti."),
        Candidate("com.google.android.backuptransport", "Google Backup", "Pencadangan akun Google berhenti."),
        Candidate("com.google.android.syncadapters.contacts", "Sinkron kontak Google", "Kontak tidak lagi disinkronkan melalui adaptor ini."),
        Candidate("com.google.android.apps.pdfviewer", "Google PDF Viewer", "Penampil PDF ini tidak bisa dibuka."),
        Candidate("com.mediatek.mtklogger", "MediaTek Logger", "Pencatatan diagnostik chipset berhenti.", true),
        Candidate("com.mediatek.mtklogger.proxy", "MediaTek Logger Proxy", "Komponen pendamping logger.", true),
        Candidate("com.hcn.autouserguide", "Panduan bawaan head unit", "Aplikasi panduan vendor tidak bisa dibuka.", true),
        Candidate("com.hcn.appupdate", "Pembaruan aplikasi vendor", "Pembaruan aplikasi vendor otomatis berhenti.", true),
        Candidate("com.hcn.autoupdate", "Pembaruan sistem vendor", "Pembaruan otomatis vendor berhenti.", true),
        Candidate("com.adups.fota", "Adups FOTA", "Pembaruan firmware OTA melalui aplikasi ini berhenti.", true),
        Candidate("com.adups.fota.sysoper", "Adups FOTA System", "Komponen pendamping pembaruan firmware.", true)
    )
    val protected = setOf("android", "com.android.systemui", "com.android.settings", "com.android.packageinstaller",
        "com.google.android.gms", "com.google.android.gsf", "app.revanced.android.gms", "org.microg.gms",
        "moe.shizuku.privileged.api", "app.cleanyour.headunit.helper", "com.android.chrome", "com.google.android.webview",
        "com.google.android.ext.services", "com.google.android.ext.shared", "com.android.bluetooth",
        "com.hcn.autoradio", "com.hcn.autocan", "com.hcn.mcuupgrade", "com.hcn.canupgrade", "com.hcn.autosetting", "com.hcn.autosource")
    fun eligible(enabled: Set<String>, dynamicProtected: Set<String>, is8227: Boolean): List<Candidate> =
        entries.filter { it.pkg in enabled && it.pkg !in protected && it.pkg !in dynamicProtected && (!it.vendor || is8227) }
}
