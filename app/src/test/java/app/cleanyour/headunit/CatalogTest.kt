package app.cleanyour.headunit

import org.junit.Assert.*
import org.junit.Test

class CatalogTest {
    @Test fun vehicleAndMicrogNeverBecomeCandidates() {
        val important=setOf("com.hcn.autoradio","com.hcn.autocan","com.hcn.mcuupgrade","com.hcn.canupgrade",
            "com.hcn.autosetting","com.hcn.autosource","com.android.bluetooth","app.revanced.android.gms",
            "com.google.android.gms","com.google.android.gsf","moe.shizuku.privileged.api",
            "com.google.android.webview","com.android.chrome")
        assertTrue(Catalog.protected.containsAll(important))
        assertTrue(Catalog.eligible(important,emptySet(),true).isEmpty())
        assertTrue(Catalog.entries.none {it.pkg in Catalog.protected})
    }
    @Test fun unknownFirmwareCannotReceiveVendorDebloat() {
        val all=Catalog.entries.map {it.pkg}.toSet()
        assertTrue(Catalog.eligible(all,emptySet(),false).none {it.vendor})
        assertTrue(Catalog.eligible(all,emptySet(),true).any {it.vendor})
    }
    @Test fun activeHomeOrKeyboardIsProtectedEvenIfInCatalog() {
        val all=Catalog.entries.map {it.pkg}.toSet()
        val dynamic=setOf("com.google.android.googlequicksearchbox","com.google.android.tts")
        assertTrue(Catalog.eligible(all,dynamic,true).none {it.pkg in dynamic})
    }
    @Test fun unknownAndAlreadyDisabledPackagesAreNotSelected() {
        assertTrue(Catalog.eligible(setOf("vendor.unknown.service"),emptySet(),true).isEmpty())
        assertTrue(Catalog.eligible(emptySet(),emptySet(),true).isEmpty())
        assertEquals(Catalog.entries.size,Catalog.entries.map {it.pkg}.toSet().size)
    }
}
