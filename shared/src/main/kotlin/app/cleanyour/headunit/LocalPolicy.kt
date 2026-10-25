package app.cleanyour.headunit

/** No caller-supplied shell text is accepted; only fixed operations and validated values. */
object LocalPolicy {
    fun command(op: String, arg: String = ""): String = when(op) {
        "identity" -> "id -u"
        "enabled" -> "pm list packages -e --user 0"
        "disabled" -> "pm list packages -d --user 0"
        "device" -> "getprop ro.product.device; getprop ro.board.platform"
        "home" -> "cmd package resolve-activity --brief --user 0 -a android.intent.action.MAIN -c android.intent.category.HOME"
        "ime" -> "settings get secure default_input_method"
        "disable", "enable" -> {
            require(Regex("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z][A-Za-z0-9_]*)+").matches(arg)) { "Nama paket tidak valid." }
            require(Catalog.entries.any {it.pkg == arg} && arg !in Catalog.protected) { "Paket dilindungi atau tidak dikenal." }
            "pm ${if(op=="disable") "disable-user" else "enable"} --user 0 $arg"
        }
        "tcp" -> {
            require(arg == "8555" || arg == "5555") { "Port tidak diizinkan." }
            "settings put global adb_enabled 1 && setprop service.adb.tcp.port $arg && setprop ctl.restart adbd"
        }
        "tcpOff" -> "setprop service.adb.tcp.port -1 && setprop ctl.restart adbd"
        else -> error("Operasi tidak dikenal.")
    }
}
