package fr.uge.net.chatFusion.reader;

import fr.uge.net.chatFusion.command.SocketAddressToken;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.nio.ByteBuffer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class SocketAddressTokenReaderTest {

    @Test
    public void simple() {
        var socketAddressToken = new SocketAddressToken(new InetSocketAddress("localhost", 7777).getAddress(), 7777);
        var bb = ByteBuffer.allocate(1024);
        var bytes = socketAddressToken.toBuffer().flip();
        bb.put(bytes);
        SocketAddressTokenReader reader = new SocketAddressTokenReader();
        assertEquals(Reader.ProcessStatus.DONE, reader.process(bb));
        assertEquals(socketAddressToken, reader.get());
        assertEquals(0, bb.position());
        assertEquals(bb.capacity(), bb.limit());
    }

    @Test
    public void reset() {
        var socketAddressToken = new SocketAddressToken(new InetSocketAddress("localhost", 7777).getAddress(), 7777);
        var socketAddressToken2 = new SocketAddressToken(new InetSocketAddress("localhost", 7777).getAddress(), 7777);
        var bb = ByteBuffer.allocate(1024);
        var bytes = socketAddressToken.toBuffer().flip();
        var bytes2 = socketAddressToken2.toBuffer().flip();
        bb.put(bytes).put(bytes2);
        SocketAddressTokenReader reader = new SocketAddressTokenReader();
        assertEquals(Reader.ProcessStatus.DONE, reader.process(bb));
        assertEquals(socketAddressToken, reader.get());
        assertEquals(socketAddressToken2.toBuffer().flip().remaining(), bb.position());
        assertEquals(bb.capacity(), bb.limit());
        reader.reset();
        assertEquals(Reader.ProcessStatus.DONE, reader.process(bb));
        assertEquals(socketAddressToken2, reader.get());
        assertEquals(0, bb.position());
        assertEquals(bb.capacity(), bb.limit());
    }

    @Test
    public void smallBuffer() {
        var socketAddressToken = new SocketAddressToken(new InetSocketAddress("localhost", 7777).getAddress(), 7777);

        var bb = ByteBuffer.allocate(1024);
        var bytes = socketAddressToken.toBuffer().flip();
        bb.put(bytes).flip();
        var bbSmall = ByteBuffer.allocate(2);
        SocketAddressTokenReader reader = new SocketAddressTokenReader();
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
        assertEquals(socketAddressToken, reader.get());
    }

    @Test
    public void errorGet() {
        var reader = new SocketAddressTokenReader();
        assertThrows(IllegalStateException.class, reader::get);
    }
}
