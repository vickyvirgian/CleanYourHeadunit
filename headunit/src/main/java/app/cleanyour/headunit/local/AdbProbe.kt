package app.cleanyour.headunit.local

import java.net.Socket
import java.net.InetSocketAddress
import java.io.DataInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

object AdbProbe {
    fun listening(port:Int):Boolean = try {
        Socket().use {s ->
            s.connect(InetSocketAddress("127.0.0.1",port),1200);s.soTimeout=1600
            val body="host::features=shell_v2\u0000".toByteArray(Charsets.UTF_8)
            val cmd=0x4e584e43
            val frame=ByteBuffer.allocate(24+body.size).order(ByteOrder.LITTLE_ENDIAN)
                .putInt(cmd).putInt(0x01000000).putInt(4096).putInt(body.size)
                .putInt(body.sumOf {it.toInt() and 255}).putInt(cmd.inv()).put(body).array()
            s.getOutputStream().write(frame);s.getOutputStream().flush()
            val bytes=ByteArray(24);DataInputStream(s.getInputStream()).readFully(bytes)
            val response=ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
            val command=response.int;response.int;response.int;val length=response.int;val checksum=response.int;val magic=response.int
            if(magic!=command.inv() || length !in 0..65536 || command !in setOf(0x4e584e43,0x48545541)) false
            else {val payload=ByteArray(length);DataInputStream(s.getInputStream()).readFully(payload);payload.sumOf {it.toInt() and 255}==checksum}
        }
    } catch(e:Exception) {false}
}
