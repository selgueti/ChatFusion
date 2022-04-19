package fr.uge.net.chatFusion.command;

import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Objects;


public record LoginAccepted(String serverName) implements Frame {
    private final static byte OPCODE = 2;
    private final static Charset UTF8 = StandardCharsets.UTF_8;

    public LoginAccepted {
        Objects.requireNonNull(serverName);
        if (serverName.isEmpty()) {
            throw new IllegalArgumentException("server name should not be empty");
        }
    }

    @Override
    public ByteBuffer toBuffer() {
        ByteBuffer bbServerName = UTF8.encode(serverName);
        ByteBuffer buffer = ByteBuffer.allocate(bbServerName.remaining() + Integer.BYTES + Byte.BYTES);
        buffer.put(OPCODE).putInt(bbServerName.remaining()).put(bbServerName);
        return buffer;
    }
}


