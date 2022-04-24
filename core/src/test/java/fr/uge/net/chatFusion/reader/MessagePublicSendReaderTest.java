package fr.uge.net.chatFusion.reader;

import fr.uge.net.chatFusion.command.MessagePublicSend;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class MessagePublicSendReaderTest {

    @Test
    public void simple() {
        var messagePublicSend = new MessagePublicSend("Server1", "€€_Alice_€€", "HelloWorld !");
        var bb = ByteBuffer.allocate(1024);
        var bytes = messagePublicSend.toBuffer().flip().position(1);  // Because frame contains OPCODE
        bb.put(bytes);
        MessagePublicSendReader reader = new MessagePublicSendReader();
        assertEquals(Reader.ProcessStatus.DONE, reader.process(bb));
        assertEquals(messagePublicSend, reader.get());
        assertEquals(0, bb.position());
        assertEquals(bb.capacity(), bb.limit());
    }

    @Test
    public void reset() {
        var messagePublicSend = new MessagePublicSend("Server1", "€€_Alice_€€", "HelloWorld !");
        var messagePublicSend2 = new MessagePublicSend("Server2", "€€_Bob_€€", "Hi, my name is Bob !");
        var bb = ByteBuffer.allocate(1024);
        var bytes = messagePublicSend.toBuffer().flip().position(1);  // Because frame contains OPCODE
        var bytes2 = messagePublicSend2.toBuffer().flip().position(1);  // Because frame contains OPCODE
        bb.put(bytes).put(bytes2);
        MessagePublicSendReader reader = new MessagePublicSendReader();
        assertEquals(Reader.ProcessStatus.DONE, reader.process(bb));
        assertEquals(messagePublicSend, reader.get());
        assertEquals(messagePublicSend2.toBuffer().position(1).remaining(), bb.position());
        assertEquals(bb.capacity(), bb.limit());
        reader.reset();
        assertEquals(Reader.ProcessStatus.DONE, reader.process(bb));
        assertEquals(messagePublicSend2, reader.get());
        assertEquals(0, bb.position());
        assertEquals(bb.capacity(), bb.limit());
    }

    @Test
    public void smallBuffer() {
        var messagePublicSend = new MessagePublicSend("Server1", "€€_Alice_€€", "HelloWorld !");
        var bb = ByteBuffer.allocate(1024);
        var bytes = messagePublicSend.toBuffer().flip().position(1);  // Because frame contains OPCODE
        bb.put(bytes).flip();
        var bbSmall = ByteBuffer.allocate(2);
        var reader = new MessagePublicSendReader();
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
        assertEquals(messagePublicSend, reader.get());
    }

    @Test
    public void errorGet() {
        var reader = new MessagePublicSendReader();
        assertThrows(IllegalStateException.class,  reader::get);
    }

}
