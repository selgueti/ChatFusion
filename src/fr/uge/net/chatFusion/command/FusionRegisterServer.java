package fr.uge.net.chatFusion.command;

import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Objects;


public record FusionRegisterServer(String name) {
    private final static byte OPCODE = 8;
    private final static Charset UTF8 = StandardCharsets.UTF_8;

    public FusionRegisterServer {
        Objects.requireNonNull(name);
        if (name.isEmpty()) {
            throw new IllegalArgumentException("server name should not be empty");
        }
    }

    public ByteBuffer toBuffer() {
        ByteBuffer bbName = UTF8.encode(name);
        ByteBuffer buffer = ByteBuffer.allocate(bbName.remaining() + Long.BYTES + Byte.BYTES);
        buffer.put(OPCODE).putLong(bbName.remaining()).put(bbName);
        return buffer;
    }
}
