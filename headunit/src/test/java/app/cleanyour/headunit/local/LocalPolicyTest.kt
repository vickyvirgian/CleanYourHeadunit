package app.cleanyour.headunit.local

import app.cleanyour.headunit.LocalPolicy
import org.junit.Assert.*
import org.junit.Test

class LocalPolicyTest {
    @Test fun onlyAllowedTcpPortsCanBeRendered() {
        assertTrue(LocalPolicy.command("tcp","8555").contains("service.adb.tcp.port 8555"))
        assertTrue(LocalPolicy.command("tcp","5555").contains("service.adb.tcp.port 5555"))
        for(arg in listOf("22","8555; reboot","$(id)","-1","65536")) assertThrows(IllegalArgumentException::class.java) {LocalPolicy.command("tcp",arg)}
    }
    @Test fun arbitraryShellAndProtectedPackagesAreRejected() {
        assertThrows(IllegalStateException::class.java) {LocalPolicy.command("reboot","")}
        for(pkg in listOf("com.google.android.gms","app.revanced.android.gms","com.hcn.autocan","com.android.systemui","com.android.vending; reboot","unknown.vendor.app")) {
            assertThrows(IllegalArgumentException::class.java) {LocalPolicy.command("disable",pkg)}
        }
    }
    @Test fun disablingAndEnablingUseFixedUserAndKnownPackages() {
        assertEquals("pm disable-user --user 0 com.google.android.feedback",LocalPolicy.command("disable","com.google.android.feedback"))
        assertEquals("pm enable --user 0 com.google.android.feedback",LocalPolicy.command("enable","com.google.android.feedback"))
        assertFalse(LocalPolicy.command("tcpOff").contains("adb_enabled"))
    }
}
