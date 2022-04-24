package fr.uge.net.chatFusion.reader;

import fr.uge.net.chatFusion.command.FusionInit;
import fr.uge.net.chatFusion.command.SocketAddressToken;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.nio.ByteBuffer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class FusionInitReaderTest {

    private final SocketAddressToken address = new SocketAddressToken(new InetSocketAddress("localhost", 7777).getAddress(), 7777);

    @Test
    public void simple() {
        var fusionInit = new FusionInit(address);
        var bb = ByteBuffer.allocate(1024);
        var bytes = fusionInit.toBuffer().flip().position(1); // Because frame contains OPCODE
        bb.put(bytes);
        FusionInitReader reader = new FusionInitReader();
        assertEquals(Reader.ProcessStatus.DONE, reader.process(bb));
        assertEquals(fusionInit, reader.get());
        assertEquals(0, bb.position());
        assertEquals(bb.capacity(), bb.limit());
    }

    @Test
    public void reset() {
        var fusionInit = new FusionInit(address);
        var fusionInit2 = new FusionInit(address);
        var bb = ByteBuffer.allocate(1024);
        var bytes = fusionInit.toBuffer().flip().position(1); // Because frame contains OPCODE
        var bytes2 = fusionInit2.toBuffer().flip().position(1); // Because frame contains OPCODE
        bb.put(bytes).put(bytes2);
        FusionInitReader reader = new FusionInitReader();
        assertEquals(Reader.ProcessStatus.DONE, reader.process(bb));
        assertEquals(fusionInit, reader.get());
        assertEquals(fusionInit2.toBuffer().position(1).remaining(), bb.position());
        assertEquals(bb.capacity(), bb.limit());
        reader.reset();
        assertEquals(Reader.ProcessStatus.DONE, reader.process(bb));
        assertEquals(fusionInit2, reader.get());
        assertEquals(0, bb.position());
        assertEquals(bb.capacity(), bb.limit());
    }

    @Test
    public void smallBuffer() {
        var fusionInit = new FusionInit(address);
        var bb = ByteBuffer.allocate(1024);
        var bytes = fusionInit.toBuffer().flip().position(1); // Because frame contains OPCODE
        bb.put(bytes).flip();
        var bbSmall = ByteBuffer.allocate(2);
        FusionInitReader reader = new FusionInitReader();
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
        assertEquals(fusionInit, reader.get());
    }

    @Test
    public void errorGet() {
        var reader = new FusionInitReader();
        assertThrows(IllegalStateException.class, reader::get);
    }

}
