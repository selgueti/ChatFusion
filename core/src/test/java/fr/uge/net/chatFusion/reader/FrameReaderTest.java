package fr.uge.net.chatFusion.reader;

import fr.uge.net.chatFusion.command.MessagePrivate;
import fr.uge.net.chatFusion.command.MessagePublicSend;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class FrameReaderTest {

    @Test
    public void simple() {
        var frame = new MessagePublicSend("Server1", "€€_Alice_€€", "HelloWorld !");
        var bb = ByteBuffer.allocate(1024);
        var bytes = frame.toBuffer().flip();
        bb.put(bytes);
        FrameReader reader = new FrameReader();
        assertEquals(Reader.ProcessStatus.DONE, reader.process(bb));
        assertEquals(frame, reader.get());
        assertEquals(0, bb.position());
        assertEquals(bb.capacity(), bb.limit());
    }

    @Test
    public void reset() {
        var frame = new MessagePublicSend("Server1", "€€_Alice_€€", "HelloWorld !");
        var frame2 = new MessagePrivate("Server1", "€€_Alice_€€", "Server2", "__Bob__", "HelloWorld !");
        var bb = ByteBuffer.allocate(1024);
        var bytes = frame.toBuffer().flip();
        var bytes2 = frame2.toBuffer().flip();
        bb.put(bytes).put(bytes2);
        FrameReader reader = new FrameReader();
        assertEquals(Reader.ProcessStatus.DONE, reader.process(bb));
        assertEquals(frame, reader.get());
        assertEquals(frame2.toBuffer().flip().remaining(), bb.position());
        assertEquals(bb.capacity(), bb.limit());
        reader.reset();
        assertEquals(Reader.ProcessStatus.DONE, reader.process(bb));
        assertEquals(frame2, reader.get());
        assertEquals(0, bb.position());
        assertEquals(bb.capacity(), bb.limit());
    }

    @Test
    public void smallBuffer() {
        var frame = new MessagePublicSend("Server1", "€€_Alice_€€", "HelloWorld !");
        var bb = ByteBuffer.allocate(1024);
        var bytes = frame.toBuffer().flip();
        bb.put(bytes).flip();
        var bbSmall = ByteBuffer.allocate(2);
        FrameReader reader = new FrameReader();
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
        assertEquals(frame, reader.get());
    }

    @Test
    public void errorGet() {
        var reader = new FrameReader();
        assertThrows(IllegalStateException.class, reader::get);
    }
}
