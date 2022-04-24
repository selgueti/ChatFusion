package fr.uge.net.chatFusion.reader;

import fr.uge.net.chatFusion.command.ServerConnexion;
import fr.uge.net.chatFusion.command.SocketAddressToken;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.nio.ByteBuffer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class ServerConnexionReaderTest {
    private final SocketAddressToken address = new SocketAddressToken(new InetSocketAddress("localhost", 7777).getAddress(), 7777);

    @Test
    public void simple() {
        var serverConnexion = new ServerConnexion("Server1", address);
        var bb = ByteBuffer.allocate(1024);
        var bytes = serverConnexion.toBuffer().flip().position(1); // Because frame contains OPCODE
        bb.put(bytes);
        ServerConnexionReader reader = new ServerConnexionReader();
        assertEquals(Reader.ProcessStatus.DONE, reader.process(bb));
        assertEquals(serverConnexion, reader.get());
        assertEquals(0, bb.position());
        assertEquals(bb.capacity(), bb.limit());
    }

    @Test
    public void reset() {
        var serverConnexion = new ServerConnexion("Server1", address);
        var serverConnexion2 = new ServerConnexion("Server2", address);
        var bb = ByteBuffer.allocate(1024);
        var bytes = serverConnexion.toBuffer().flip().position(1); // Because frame contains OPCODE
        var bytes2 = serverConnexion2.toBuffer().flip().position(1); // Because frame contains OPCODE
        bb.put(bytes).put(bytes2);
        ServerConnexionReader reader = new ServerConnexionReader();
        assertEquals(Reader.ProcessStatus.DONE, reader.process(bb));
        assertEquals(serverConnexion, reader.get());
        assertEquals(serverConnexion2.toBuffer().position(1).remaining(), bb.position());
        assertEquals(bb.capacity(), bb.limit());
        reader.reset();
        assertEquals(Reader.ProcessStatus.DONE, reader.process(bb));
        assertEquals(serverConnexion2, reader.get());
        assertEquals(0, bb.position());
        assertEquals(bb.capacity(), bb.limit());
    }

    @Test
    public void smallBuffer() {
        var serverConnexion = new ServerConnexion("Server1", address);
        var bb = ByteBuffer.allocate(1024);
        var bytes = serverConnexion.toBuffer().flip().position(1); // Because frame contains OPCODE
        bb.put(bytes).flip();
        var bbSmall = ByteBuffer.allocate(2);
        ServerConnexionReader reader = new ServerConnexionReader();
        while (bb.hasRemaining()) {
            while (bb.hasRemaining() && bbSmall.hasRemaining()) {
                bbSmall.put(bb.get());
            }
            if (bb.hasRemaining()) {
                assertEquals(Reader.ProcessStatus.REFILL, reader.process(bbSmall));
            } else {
                assertEquals(Reader.ProcessStatus.DONE, reader.process(bbSmall));
            }
        }
        assertEquals(serverConnexion, reader.get());
    }

    @Test
    public void errorGet() {
        var reader = new ServerConnexionReader();
        assertThrows(IllegalStateException.class, reader::get);
    }
}
