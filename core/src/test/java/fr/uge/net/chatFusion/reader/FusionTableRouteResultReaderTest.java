package fr.uge.net.chatFusion.reader;

import fr.uge.net.chatFusion.command.FusionTableRouteResult;
import fr.uge.net.chatFusion.command.SocketAddressToken;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class FusionTableRouteResultReaderTest {

    private final SocketAddressToken address = new SocketAddressToken(
            new InetSocketAddress("localhost", 7777).getAddress(),
            7777);

    @Test
    public void simple() {
        var fusionTableRouteResult = new FusionTableRouteResult(1, Map.of("Server1", address));
        var bb = ByteBuffer.allocate(1024);
        var bytes = fusionTableRouteResult.toBuffer().flip().position(1); // Because frame contains OPCODE
        bb.put(bytes);
        FusionTableRouteResultReader reader = new FusionTableRouteResultReader();
        assertEquals(Reader.ProcessStatus.DONE, reader.process(bb));
        assertEquals(fusionTableRouteResult, reader.get());
        assertEquals(0, bb.position());
        assertEquals(bb.capacity(), bb.limit());
    }

    @Test
    public void reset() {
        var fusionTableRouteResult = new FusionTableRouteResult(1, Map.of("Server1", address));
        var fusionTableRouteResult2 = new FusionTableRouteResult(1, Map.of("Server2", address));

        var bb = ByteBuffer.allocate(1024);
        var bytes = fusionTableRouteResult.toBuffer().flip().position(1); // Because frame contains OPCODE
        var bytes2 = fusionTableRouteResult2.toBuffer().flip().position(1); // Because frame contains OPCODE
        bb.put(bytes).put(bytes2);
        var reader = new FusionTableRouteResultReader();
        assertEquals(Reader.ProcessStatus.DONE, reader.process(bb));
        assertEquals(fusionTableRouteResult, reader.get());
        assertEquals(fusionTableRouteResult2.toBuffer().flip().position(1).remaining(), bb.position());
        assertEquals(bb.capacity(), bb.limit());
        reader.reset();
        assertEquals(Reader.ProcessStatus.DONE, reader.process(bb));
        assertEquals(fusionTableRouteResult2, reader.get());
        assertEquals(0, bb.position());
        assertEquals(bb.capacity(), bb.limit());
    }

    @Test
    public void smallBuffer() {
        var fusionTableRouteResult = new FusionTableRouteResult(1, Map.of("Server1", address));
        var bb = ByteBuffer.allocate(1024);
        var bytes = fusionTableRouteResult.toBuffer().flip().position(1); // Because frame contains OPCODE
        bb.put(bytes).flip();
        var bbSmall = ByteBuffer.allocate(2);
        var reader = new FusionTableRouteResultReader();
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
        assertEquals(fusionTableRouteResult, reader.get());
    }

    @Test
    public void errorGet() {
        var reader = new FusionTableRouteResultReader();
        assertThrows(IllegalStateException.class,  reader::get);
    }

    @Test
    public void errorNbMembersNeg() {
        var reader = new FusionTableRouteResultReader();
        var bb = ByteBuffer.allocate(1024);
        bb.putInt(-1);
        assertEquals(Reader.ProcessStatus.ERROR, reader.process(bb));
    }
}
