package fr.uge.net.chatFusion.command;

import fr.uge.net.chatFusion.util.FrameVisitor;

import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

public record FilePrivate(String serverSrc, String loginSrc, String serverDst, String loginDst, String fileName,
                          int nbBlocks, int blockSize, byte[] bytes) implements Frame {
    private static final byte OPCODE = 7;
    private static final Charset UTF8 = StandardCharsets.UTF_8;

    public FilePrivate {
        Objects.requireNonNull(serverSrc);
        Objects.requireNonNull(loginSrc);
        Objects.requireNonNull(serverDst);
        Objects.requireNonNull(loginDst);
        Objects.requireNonNull(fileName);
        Objects.requireNonNull(bytes);

        if (nbBlocks < 1) { // minimum 1 block exist for an empty file: the empty block.
            throw new IllegalArgumentException();
        }
        if (blockSize < 0) {
            throw new IllegalArgumentException();
        }
        if (serverSrc.isEmpty()) {
            throw new IllegalArgumentException();
        }
        if (serverDst.isEmpty()) {
            throw new IllegalArgumentException();
        }
        if (loginSrc.isEmpty()) {
            throw new IllegalArgumentException();
        }
        if (loginDst.isEmpty()) {
            throw new IllegalArgumentException();
        }
    }

    @Override
    public ByteBuffer toBuffer() {
        var bbServerSrc = UTF8.encode(serverSrc);
        var bbLoginSrc = UTF8.encode(loginSrc);
        var bbServerDst = UTF8.encode(serverDst);
        var bbLoginDst = UTF8.encode(loginDst);
        var bbFileName = UTF8.encode(fileName);

        var buffer = ByteBuffer.allocate(bbServerSrc.remaining()
                + bbLoginSrc.remaining()
                + bbServerDst.remaining()
                + bbLoginDst.remaining()
                + bbFileName.remaining()
                + Integer.BYTES * 7
                + Byte.BYTES * (bytes.length) + 1);
        buffer.put(OPCODE);
        buffer.putInt(bbServerSrc.remaining()).put(bbServerSrc);
        buffer.putInt(bbLoginSrc.remaining()).put(bbLoginSrc);
        buffer.putInt(bbServerDst.remaining()).put(bbServerDst);
        buffer.putInt(bbLoginDst.remaining()).put(bbLoginDst);
        buffer.putInt(bbFileName.remaining()).put(bbFileName);
        buffer.putInt(nbBlocks).putInt(blockSize).put(bytes);
        return buffer;
    }

    @Override
    public void accept(FrameVisitor visitor) {
        visitor.visit(this);
    }
}
