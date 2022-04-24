package fr.uge.net.chatFusion.reader;

import fr.uge.net.chatFusion.command.MessagePrivate;
import fr.uge.net.chatFusion.command.MessagePublicSend;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class MessagePrivateReaderTest {


    @Test
    public void simple() {
        var messagePrivate = new MessagePrivate("Server1", "€€_Alice_€€", "Server2", "__Bob__", "HelloWorld !");
        var bb = ByteBuffer.allocate(1024);
        var bytes = messagePrivate.toBuffer().flip().position(1);  // Because frame contains OPCODE
        bb.put(bytes);
        MessagePrivateReader reader = new MessagePrivateReader();
        assertEquals(Reader.ProcessStatus.DONE, reader.process(bb));
        assertEquals(messagePrivate, reader.get());
        assertEquals(0, bb.position());
        assertEquals(bb.capacity(), bb.limit());
    }

    @Test
    public void reset() {
        var messagePrivate = new MessagePrivate("Server2", "__Bob__", "Server1", "€€_Alice_€€", "HelloWorld !");
        var messagePrivate2 = new MessagePrivate("Server1", "€€_Alice_€€", "Server2", "__Bob__", "HelloWorld !");
        var bb = ByteBuffer.allocate(1024);
        var bytes = messagePrivate.toBuffer().flip().position(1);  // Because frame contains OPCODE
        var bytes2 = messagePrivate2.toBuffer().flip().position(1);  // Because frame contains OPCODE
        bb.put(bytes).put(bytes2);
        MessagePrivateReader reader = new MessagePrivateReader();
        assertEquals(Reader.ProcessStatus.DONE, reader.process(bb));
        assertEquals(messagePrivate, reader.get());
        assertEquals(messagePrivate2.toBuffer().position(1).remaining(), bb.position());
        assertEquals(bb.capacity(), bb.limit());
        reader.reset();
        assertEquals(Reader.ProcessStatus.DONE, reader.process(bb));
        assertEquals(messagePrivate2, reader.get());
        assertEquals(0, bb.position());
        assertEquals(bb.capacity(), bb.limit());
    }

    @Test
    public void smallBuffer() {
        var messagePrivate = new MessagePrivate("Server1", "€€_Alice_€€", "Server2", "__Bob__", "HelloWorld !");
        var bb = ByteBuffer.allocate(1024);
        var bytes = messagePrivate.toBuffer().flip().position(1);  // Because frame contains OPCODE
        bb.put(bytes).flip();
        var bbSmall = ByteBuffer.allocate(2);
        var reader = new MessagePrivateReader();
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
        assertEquals(messagePrivate, reader.get());
    }

    @Test
    public void errorGet() {
        var reader = new MessagePrivateReader();
        assertThrows(IllegalStateException.class, reader::get);
    }
}
