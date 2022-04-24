package fr.uge.net.chatFusion.reader;

import fr.uge.net.chatFusion.command.FusionRouteTableSend;
import fr.uge.net.chatFusion.command.SocketAddressToken;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class FusionRouteTableSendReaderTest {

    private final SocketAddressToken address = new SocketAddressToken(
            new InetSocketAddress("localhost", 7777).getAddress(),
            7777);

    @Test
    public void simple() {
        var fusionRouteTableSend = new FusionRouteTableSend(1, Map.of("Server1", address));
        var bb = ByteBuffer.allocate(1024);
        var bytes = fusionRouteTableSend.toBuffer().flip().position(1); // Because frame contains OPCODE
        bb.put(bytes);
        FusionRouteTableSendReader reader = new FusionRouteTableSendReader();
        assertEquals(Reader.ProcessStatus.DONE, reader.process(bb));
        assertEquals(fusionRouteTableSend, reader.get());
        assertEquals(0, bb.position());
        assertEquals(bb.capacity(), bb.limit());
    }

    @Test
    public void reset() {
        var fusionRouteTableSend = new FusionRouteTableSend(1, Map.of("Server1", address));
        var fusionRouteTableSend2 = new FusionRouteTableSend(1, Map.of("Server2", address));

        var bb = ByteBuffer.allocate(1024);
        var bytes = fusionRouteTableSend.toBuffer().flip().position(1); // Because frame contains OPCODE
        var bytes2 = fusionRouteTableSend2.toBuffer().flip().position(1); // Because frame contains OPCODE
        bb.put(bytes).put(bytes2);
        var reader = new FusionRouteTableSendReader();
        assertEquals(Reader.ProcessStatus.DONE, reader.process(bb));
        assertEquals(fusionRouteTableSend, reader.get());
        assertEquals(fusionRouteTableSend2.toBuffer().flip().position(1).remaining(), bb.position());
        assertEquals(bb.capacity(), bb.limit());
        reader.reset();
        assertEquals(Reader.ProcessStatus.DONE, reader.process(bb));
        assertEquals(fusionRouteTableSend2, reader.get());
        assertEquals(0, bb.position());
        assertEquals(bb.capacity(), bb.limit());
    }

    @Test
    public void smallBuffer() {
        var fusionRouteTableSend = new FusionRouteTableSend(1, Map.of("Server1", address));
        var bb = ByteBuffer.allocate(1024);
        var bytes = fusionRouteTableSend.toBuffer().flip().position(1); // Because frame contains OPCODE
        bb.put(bytes).flip();
        var bbSmall = ByteBuffer.allocate(2);
        var reader = new FusionRouteTableSendReader();
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
        assertEquals(fusionRouteTableSend, reader.get());
    }

    @Test
    public void errorGet() {
        var reader = new FusionRouteTableSendReader();
        assertThrows(IllegalStateException.class,  reader::get);
    }

    @Test
    public void errorNbMembersNeg() {
        var reader = new FusionRouteTableSendReader();
        var bb = ByteBuffer.allocate(1024);
        bb.putInt(-1);
        assertEquals(Reader.ProcessStatus.ERROR, reader.process(bb));
    }

}
