package fr.uge.net.chatFusion.reader;

import fr.uge.net.chatFusion.command.MessagePublicTransmit;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class MessagePublicTransmitReaderTest {


    @Test
    public void simple() {
        var messagePublicTransmit = new MessagePublicTransmit("Server1", "€€_Alice_€€", "HelloWorld !");
        var bb = ByteBuffer.allocate(1024);
        var bytes = messagePublicTransmit.toBuffer().flip().position(1);  // Because frame contains OPCODE
        bb.put(bytes);
        MessagePublicTransmitReader reader = new MessagePublicTransmitReader();
        assertEquals(Reader.ProcessStatus.DONE, reader.process(bb));
        assertEquals(messagePublicTransmit, reader.get());
        assertEquals(0, bb.position());
        assertEquals(bb.capacity(), bb.limit());
    }

    @Test
    public void reset() {
        var messagePublicTransmit = new MessagePublicTransmit("Server1", "€€_Alice_€€", "HelloWorld !");
        var messagePublicTransmit2 = new MessagePublicTransmit("Server2", "€€_Bob_€€", "Hi, my name is Bob !");
        var bb = ByteBuffer.allocate(1024);
        var bytes = messagePublicTransmit.toBuffer().flip().position(1);  // Because frame contains OPCODE
        var bytes2 = messagePublicTransmit2.toBuffer().flip().position(1);  // Because frame contains OPCODE
        bb.put(bytes).put(bytes2);
        MessagePublicTransmitReader reader = new MessagePublicTransmitReader();
        assertEquals(Reader.ProcessStatus.DONE, reader.process(bb));
        assertEquals(messagePublicTransmit, reader.get());
        assertEquals(messagePublicTransmit2.toBuffer().position(1).remaining(), bb.position());
        assertEquals(bb.capacity(), bb.limit());
        reader.reset();
        assertEquals(Reader.ProcessStatus.DONE, reader.process(bb));
        assertEquals(messagePublicTransmit2, reader.get());
        assertEquals(0, bb.position());
        assertEquals(bb.capacity(), bb.limit());
    }

    @Test
    public void smallBuffer() {
        var messagePublicTransmit = new MessagePublicTransmit("Server1", "€€_Alice_€€", "HelloWorld !");
        var bb = ByteBuffer.allocate(1024);
        var bytes = messagePublicTransmit.toBuffer().flip().position(1);  // Because frame contains OPCODE
        bb.put(bytes).flip();
        var bbSmall = ByteBuffer.allocate(2);
        var reader = new MessagePublicTransmitReader();
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
        assertEquals(messagePublicTransmit, reader.get());
    }

    @Test
    public void errorGet() {
        var reader = new MessagePublicTransmitReader();
        assertThrows(IllegalStateException.class,  reader::get);
    }
}
