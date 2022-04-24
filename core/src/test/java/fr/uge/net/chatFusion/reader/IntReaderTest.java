package fr.uge.net.chatFusion.reader;

import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class IntReaderTest {

    @Test
    public void simple() {
        var _int = Integer.MAX_VALUE;
        var bb = ByteBuffer.allocate(1024);
        bb.putInt(_int);
        IntReader sr = new IntReader();
        assertEquals(Reader.ProcessStatus.DONE, sr.process(bb));
        assertEquals(_int, sr.get());
        assertEquals(0, bb.position());
        assertEquals(bb.capacity(), bb.limit());
    }

    @Test
    public void reset() {
        var _int1 = Integer.MAX_VALUE;
        var _int2 = 6948516;
        var bb = ByteBuffer.allocate(1024);
        bb.putInt(_int1).putInt(_int2);
        IntReader intReader = new IntReader();
        assertEquals(Reader.ProcessStatus.DONE, intReader.process(bb));
        assertEquals(_int1, intReader.get());
        assertEquals(4, bb.position());
        assertEquals(bb.capacity(), bb.limit());
        intReader.reset();
        assertEquals(Reader.ProcessStatus.DONE, intReader.process(bb));
        assertEquals(_int2, intReader.get());
        assertEquals(0, bb.position());
        assertEquals(bb.capacity(), bb.limit());
    }

    @Test
    public void smallBuffer() {
        var _int = Integer.MAX_VALUE;
        var bb = ByteBuffer.allocate(1024);
        var bytes = ByteBuffer.allocate(Integer.BYTES).putInt(_int).flip();
        bb.put(bytes).flip();
        var bbSmall = ByteBuffer.allocate(2);
        var intReader = new IntReader();
        while (bb.hasRemaining()) {
            while (bb.hasRemaining() && bbSmall.hasRemaining()) {
                bbSmall.put(bb.get());
            }
            if (bb.hasRemaining()) {
                assertEquals(Reader.ProcessStatus.REFILL, intReader.process(bbSmall));
            } else {
                assertEquals(Reader.ProcessStatus.DONE, intReader.process(bbSmall));
            }
        }
        assertEquals(_int, intReader.get());
    }

    @Test
    public void errorGet() {
        var intReader = new IntReader();
        assertThrows(IllegalStateException.class, intReader::get);
    }

}
