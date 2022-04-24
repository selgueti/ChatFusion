package fr.uge.net.chatFusion.reader;

import fr.uge.net.chatFusion.command.LoginAccepted;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class LoginAcceptedReaderTest {

    @Test
    public void simple() {
        var loginAccepted = new LoginAccepted("Server1");
        var bb = ByteBuffer.allocate(1024);
        var bytes = loginAccepted.toBuffer().flip().position(1); // Because frame contains OPCODE
        bb.put(bytes);
        LoginAcceptedReader reader = new LoginAcceptedReader();
        assertEquals(Reader.ProcessStatus.DONE, reader.process(bb));
        assertEquals(loginAccepted, reader.get());
        assertEquals(0, bb.position());
        assertEquals(bb.capacity(), bb.limit());
    }

    @Test
    public void reset() {
        var loginAccepted = new LoginAccepted("€€Server1");
        var loginAccepted2 = new LoginAccepted("€€Server2");
        var bb = ByteBuffer.allocate(1024);
        var bytes = loginAccepted.toBuffer().flip().position(1); // Because frame contains OPCODE
        var bytes2 = loginAccepted2.toBuffer().flip().position(1); // Because frame contains OPCODE
        bb.put(bytes).put(bytes2);
        LoginAcceptedReader reader = new LoginAcceptedReader();
        assertEquals(Reader.ProcessStatus.DONE, reader.process(bb));
        assertEquals(loginAccepted, reader.get());
        assertEquals(loginAccepted2.toBuffer().position(1).remaining(), bb.position());
        assertEquals(bb.capacity(), bb.limit());
        reader.reset();
        assertEquals(Reader.ProcessStatus.DONE, reader.process(bb));
        assertEquals(loginAccepted2, reader.get());
        assertEquals(0, bb.position());
        assertEquals(bb.capacity(), bb.limit());
    }

    @Test
    public void smallBuffer() {
        var loginAccepted = new LoginAccepted("Server1");
        var bb = ByteBuffer.allocate(1024);
        var bytes = loginAccepted.toBuffer().flip().position(1); // Because frame contains OPCODE
        bb.put(bytes).flip();
        var bbSmall = ByteBuffer.allocate(2);
        LoginAcceptedReader reader = new LoginAcceptedReader();
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
        assertEquals(loginAccepted, reader.get());
    }

    @Test
    public void errorGet() {
        var reader = new LoginAcceptedReader();
        assertThrows(IllegalStateException.class, reader::get);
    }
}
