package app.cleanyour.headunit

import org.junit.Assert.*
import org.junit.Test
import tv.cleanyour.adb.wire.*
import java.security.KeyPairGenerator
import java.security.interfaces.RSAPublicKey

class AdbAuthTest {
    @Test fun adbKeyRoundTripsAndSignatureMatchesToken() {
        val key=KeyPairGenerator.getInstance("RSA").apply {initialize(2048)}.generateKeyPair()
        val pub=key.public as RSAPublicKey
        val encoded=AdbPublicKeyCodec.encode(pub)
        assertEquals(524,encoded.size)
        assertEquals(pub.modulus,AdbPublicKeyCodec.decode(encoded).modulus)
        assertTrue(AdbPublicKeyCodec.line(pub).endsWith(" cleanyourheadunit"))
        val token=AuthToken(ByteArray(20) {it.toByte()})
        val signature=AdbSignature.sign(key.private,token)
        assertTrue(AdbSignature.verify(pub,token.bytes,signature))
        assertFalse(AdbSignature.verify(pub,ByteArray(20),signature))
    }
    @Test(expected=IllegalArgumentException::class) fun rejectsNonAdbTokenLength() {
        AuthToken(ByteArray(21))
    }
}
