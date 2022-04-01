package fr.uge.net.chatFusion.command;

import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

public record MessagePublicSend(String serverSrc, String loginSrc, String msg) {
    private final static byte OPCODE = 4;
    private final static Charset UTF8 = StandardCharsets.UTF_8;

    public MessagePublicSend {
        Objects.requireNonNull(serverSrc);
        Objects.requireNonNull(loginSrc);
        Objects.requireNonNull(msg);

        if (serverSrc.isEmpty()) {
            throw new IllegalArgumentException("server src name should not be empty");
        }

        if (loginSrc.isEmpty()) {
            throw new IllegalArgumentException("login src should not be empty");
        }
    }

    public ByteBuffer toBuffer() {
        ByteBuffer bbServerName = UTF8.encode(serverSrc);
        ByteBuffer bbLoginSrc = UTF8.encode(loginSrc);
        ByteBuffer bbMsg = UTF8.encode(msg);

        ByteBuffer buffer = ByteBuffer.allocate(bbServerName.remaining()
                + bbLoginSrc.remaining()
                + bbMsg.remaining()
                + 3 * Long.BYTES + Byte.BYTES);

        buffer.put(OPCODE)
                .putLong(bbServerName.remaining()).put(bbServerName)
                .putLong(bbLoginSrc.remaining()).put(bbLoginSrc)
                .putLong(bbMsg.remaining()).put(bbMsg);
        return buffer;
    }
}
