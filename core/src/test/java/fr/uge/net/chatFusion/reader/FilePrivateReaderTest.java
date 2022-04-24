package fr.uge.net.chatFusion.reader;

import fr.uge.net.chatFusion.command.FilePrivate;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class FilePrivateReaderTest {

    private static final Charset UTF8 = StandardCharsets.UTF_8;

    @Test
    public void simple() {
        var filePrivate = new FilePrivate(
                "Server1",
                "€€_Alice_€€",
                "Server2",
                "__Bob__",
                "README.txt",
                3,
                500,
                new byte[500]
        );
        var bb = ByteBuffer.allocate(1024);
        var bytes = filePrivate.toBuffer().flip().position(1); // Because frame contains OPCODE
        bb.put(bytes);
        FilePrivateReader reader = new FilePrivateReader();
        assertEquals(Reader.ProcessStatus.DONE, reader.process(bb));
        assertEquals(filePrivate.serverSrc(), reader.get().serverSrc());
        assertEquals(filePrivate.loginSrc(), reader.get().loginSrc());
        assertEquals(filePrivate.serverDst(), reader.get().serverDst());
        assertEquals(filePrivate.loginDst(), reader.get().loginDst());
        assertEquals(filePrivate.fileName(), reader.get().fileName());
        assertEquals(filePrivate.nbBlocks(), reader.get().nbBlocks());
        assertEquals(filePrivate.blockSize(), reader.get().blockSize());
        assertEquals(filePrivate.bytes().length, reader.get().bytes().length);
        assertEquals(0, bb.position());
        assertEquals(bb.capacity(), bb.limit());
    }

    @Test
    public void reset() {
        var filePrivate = new FilePrivate(
                "Server1",
                "€€_Alice_€€",
                "Server2",
                "__Bob__",
                "README.txt",
                3,
                500,
                new byte[500]
        );
        var filePrivate2 = new FilePrivate(
                "Server1",
                "€€_Alice_€€",
                "Server2",
                "__Bob__",
                "README.txt",
                3,
                500,
                new byte[500]
        );
        var bb = ByteBuffer.allocate(2048); // 1024 is too small for the two given frame
        var bytes = filePrivate.toBuffer().flip().position(1); // Because frame contains OPCODE
        var bytes2 = filePrivate2.toBuffer().flip().position(1); // Because frame contains OPCODE
        bb.put(bytes).put(bytes2);
        FilePrivateReader reader = new FilePrivateReader();
        assertEquals(Reader.ProcessStatus.DONE, reader.process(bb));

        // File 1
        assertEquals(filePrivate.serverSrc(), reader.get().serverSrc());
        assertEquals(filePrivate.loginSrc(), reader.get().loginSrc());
        assertEquals(filePrivate.serverDst(), reader.get().serverDst());
        assertEquals(filePrivate.loginDst(), reader.get().loginDst());
        assertEquals(filePrivate.fileName(), reader.get().fileName());
        assertEquals(filePrivate.nbBlocks(), reader.get().nbBlocks());
        assertEquals(filePrivate.blockSize(), reader.get().blockSize());
        assertEquals(filePrivate.bytes().length, reader.get().bytes().length);

        assertEquals(filePrivate2.toBuffer().flip().position(1).remaining(), bb.position());
        assertEquals(bb.capacity(), bb.limit());
        reader.reset();
        assertEquals(Reader.ProcessStatus.DONE, reader.process(bb));

        // File 2
        assertEquals(filePrivate2.serverSrc(), reader.get().serverSrc());
        assertEquals(filePrivate2.loginSrc(), reader.get().loginSrc());
        assertEquals(filePrivate2.serverDst(), reader.get().serverDst());
        assertEquals(filePrivate2.loginDst(), reader.get().loginDst());
        assertEquals(filePrivate2.fileName(), reader.get().fileName());
        assertEquals(filePrivate2.nbBlocks(), reader.get().nbBlocks());
        assertEquals(filePrivate2.blockSize(), reader.get().blockSize());
        assertEquals(filePrivate2.bytes().length, reader.get().bytes().length);

        assertEquals(0, bb.position());
        assertEquals(bb.capacity(), bb.limit());
    }

    @Test
    public void smallBuffer() {
        var filePrivate = new FilePrivate(
                "Server1",
                "€€_Alice_€€",
                "Server2",
                "__Bob__",
                "README.txt",
                3,
                500,
                new byte[500]
        );

        var bb = ByteBuffer.allocate(1024);
        var bytes = filePrivate.toBuffer().flip().position(1); // Because frame contains OPCODE
        bb.put(bytes).flip();
        var bbSmall = ByteBuffer.allocate(2);
        var reader = new FilePrivateReader();
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
        //assertEquals(filePrivate, reader.get());
        assertEquals(filePrivate.serverSrc(), reader.get().serverSrc());
        assertEquals(filePrivate.loginSrc(), reader.get().loginSrc());
        assertEquals(filePrivate.serverDst(), reader.get().serverDst());
        assertEquals(filePrivate.loginDst(), reader.get().loginDst());
        assertEquals(filePrivate.fileName(), reader.get().fileName());
        assertEquals(filePrivate.nbBlocks(), reader.get().nbBlocks());
        assertEquals(filePrivate.blockSize(), reader.get().blockSize());
        assertEquals(filePrivate.bytes().length, reader.get().bytes().length);
    }

    @Test
    public void errorGet() {
        var reader = new FilePrivateReader();
        assertThrows(IllegalStateException.class, reader::get);
    }

    @Test
    public void errorNbBlockNeg() {

        var serverSrc = UTF8.encode("Server1");
        var loginSrc = UTF8.encode("€€_Alice_€€");
        var serverDst = UTF8.encode("Server2");
        var loginDst = UTF8.encode("__Bob__");
        var fileName = UTF8.encode("README.txt");

        var reader = new FilePrivateReader();
        var bb = ByteBuffer.allocate(1024);
        bb.putInt(serverSrc.remaining()).put(serverSrc)
                .putInt(loginSrc.remaining()).put(loginSrc)
                .putInt(serverDst.remaining()).put(serverSrc)
                .putInt(loginDst.remaining()).put(loginDst)
                .putInt(fileName.remaining()).put(fileName)
                .putInt(-1); // nbBlock
        assertEquals(Reader.ProcessStatus.ERROR, reader.process(bb));
    }

    @Test
    public void errorBlockSizeNeg() {

        var serverSrc = UTF8.encode("Server1");
        var loginSrc = UTF8.encode("€€_Alice_€€");
        var serverDst = UTF8.encode("Server2");
        var loginDst = UTF8.encode("__Bob__");
        var fileName = UTF8.encode("README.txt");

        var reader = new FilePrivateReader();
        var bb = ByteBuffer.allocate(1024);

        bb.putInt(serverSrc.remaining()).put(serverSrc)
                .putInt(loginSrc.remaining()).put(loginSrc)
                .putInt(serverDst.remaining()).put(serverSrc)
                .putInt(loginDst.remaining()).put(loginDst)
                .putInt(fileName.remaining()).put(fileName)
                .putInt(10)
                .putInt(-5); // sizeBlock
        assertEquals(Reader.ProcessStatus.ERROR, reader.process(bb));
    }

}
