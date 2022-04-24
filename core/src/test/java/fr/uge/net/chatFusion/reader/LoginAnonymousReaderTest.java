package fr.uge.net.chatFusion.reader;

import fr.uge.net.chatFusion.command.LoginAnonymous;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class LoginAnonymousReaderTest {

    @Test
    public void simple() {
        var loginAnonymous = new LoginAnonymous("€€_Alice_€€"); // 1 + 4 + 20 == 25
        var bb = ByteBuffer.allocate(1024);
        var bytes = loginAnonymous.toBuffer().flip().position(1); // Because frame contains OPCODE
        bb.put(bytes);
        LoginAnonymousReader reader = new LoginAnonymousReader();
        assertEquals(Reader.ProcessStatus.DONE, reader.process(bb));
        assertEquals(loginAnonymous, reader.get());
        assertEquals(0, bb.position());
        assertEquals(bb.capacity(), bb.limit());
    }

    @Test
    public void reset() {
        var loginAnonymous = new LoginAnonymous("€€_ALice_€€"); // 1 + 4 + 20 == 25
        var loginAnonymous2 = new LoginAnonymous("€€_Bob_€€"); // 1 + 4 + 18 == 23
        var bb = ByteBuffer.allocate(1024);
        var bytes = loginAnonymous.toBuffer().flip().position(1); // Because frame contains OPCODE
        var bytes2 = loginAnonymous2.toBuffer().flip().position(1); // Because frame contains OPCODE
        bb.put(bytes).put(bytes2);
        LoginAnonymousReader reader = new LoginAnonymousReader();
        assertEquals(Reader.ProcessStatus.DONE, reader.process(bb));
        assertEquals(loginAnonymous, reader.get());
        assertEquals(loginAnonymous2.toBuffer().position(1).remaining(), bb.position());
        assertEquals(bb.capacity(), bb.limit());
        reader.reset();
        assertEquals(Reader.ProcessStatus.DONE, reader.process(bb));
        assertEquals(loginAnonymous2, reader.get());
        assertEquals(0, bb.position());
        assertEquals(bb.capacity(), bb.limit());
    }

    @Test
    public void smallBuffer() {
        var loginAnonymous = new LoginAnonymous("€€_ALice_€€");
        var bb = ByteBuffer.allocate(1024);
        var bytes = loginAnonymous.toBuffer().flip().position(1); // Because frame contains OPCODE
        bb.put(bytes).flip();
        var bbSmall = ByteBuffer.allocate(2);
        LoginAnonymousReader reader = new LoginAnonymousReader();
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
        assertEquals(loginAnonymous, reader.get());
    }

    @Test
    public void errorGet() {
        var reader = new LoginAnonymousReader();
        assertThrows(IllegalStateException.class,  reader::get);
    }

}
