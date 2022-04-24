package fr.uge.net.chatFusion.reader;

import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class BytesReaderTest {

    @Test
    public void simple() {
        var bytes = new byte[500];
        var bb = ByteBuffer.allocate(1024);
        bb.put(bytes);
        BytesReader br = new BytesReader(500);
        assertEquals(Reader.ProcessStatus.DONE, br.process(bb));
        assertEquals(bytes.length, br.get().length);
        assertEquals(0, bb.position());
        assertEquals(bb.capacity(), bb.limit());
    }

    @Test
    public void reset() {
        var bytes1 = new byte[500];
        var bytes2 = new byte[500];
        var bb = ByteBuffer.allocate(1024);
        bb.put(bytes1).put(bytes2);
        BytesReader br = new BytesReader(500);
        assertEquals(Reader.ProcessStatus.DONE, br.process(bb));
        assertEquals(bytes1.length, br.get().length);
        assertEquals(500, bb.position());
        assertEquals(bb.capacity(), bb.limit());
        br.reset();
        assertEquals(Reader.ProcessStatus.DONE, br.process(bb));
        assertEquals(bytes2.length, br.get().length);
        assertEquals(0, bb.position());
        assertEquals(bb.capacity(), bb.limit());
    }

    @Test
    public void smallBuffer() {
        var bb = ByteBuffer.allocate(1024);
        var bytes = new byte[500];
        bb.put(bytes).flip();
        var bbSmall = ByteBuffer.allocate(2);
        var br = new BytesReader(500);
        while (bb.hasRemaining()) {
            while (bb.hasRemaining() && bbSmall.hasRemaining()) {
                bbSmall.put(bb.get());
            }
            if (bb.hasRemaining()) {
                assertEquals(Reader.ProcessStatus.REFILL, br.process(bbSmall));
            } else {
                assertEquals(Reader.ProcessStatus.DONE, br.process(bbSmall));
            }
        }
        assertEquals(bytes.length, br.get().length);
    }

    @Test
    public void errorGet() {
        var br = new BytesReader(1);
        assertThrows(IllegalStateException.class,  br::get);
    }

}
