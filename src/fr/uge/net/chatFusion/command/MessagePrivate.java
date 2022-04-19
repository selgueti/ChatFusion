package fr.uge.net.chatFusion.command;


import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

public record MessagePrivate(String serverSrc, String loginSrc, String serverDst, String loginDst, String msg) implements Frame {
    private final static byte OPCODE = 6;
    private final static Charset UTF8 = StandardCharsets.UTF_8;

    public MessagePrivate {
        Objects.requireNonNull(serverSrc);
        Objects.requireNonNull(loginSrc);
        Objects.requireNonNull(serverDst);
        Objects.requireNonNull(loginDst);
        Objects.requireNonNull(msg);
        if (serverSrc.isEmpty()) {
            throw new IllegalArgumentException("server source name should not be empty");
        }
        if (loginSrc.isEmpty()) {
            throw new IllegalArgumentException("login source should not be empty");
        }
        if (serverDst.isEmpty()) {
            throw new IllegalArgumentException("server destination name should not be empty");
        }
        if (loginDst.isEmpty()) {
            throw new IllegalArgumentException("login destination should not be empty");
        }
    }

    @Override
    public ByteBuffer toBuffer() {
        ByteBuffer bbServerSrc = UTF8.encode(serverSrc);
        ByteBuffer bbLoginSrc = UTF8.encode(loginSrc);
        ByteBuffer bbServerDst = UTF8.encode(serverDst);
        ByteBuffer bbLoginDst = UTF8.encode(loginDst);
        ByteBuffer bbMsg = UTF8.encode(msg);

        ByteBuffer buffer = ByteBuffer.allocate(bbServerSrc.remaining()
                + bbLoginSrc.remaining()
                + bbServerDst.remaining()
                + bbLoginDst.remaining()
                + bbMsg.remaining()
                + 5 * Integer.BYTES + Byte.BYTES);

        buffer.put(OPCODE)
                .putInt(bbServerSrc.remaining()).put(bbServerSrc)
                .putInt(bbLoginSrc.remaining()).put(bbLoginSrc)
                .putInt(bbServerDst.remaining()).put(bbServerDst)
                .putInt(bbLoginDst.remaining()).put(bbLoginDst)
                .putInt(bbMsg.remaining()).put(bbMsg);
        return buffer;
    }
}
