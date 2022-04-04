package fr.uge.net.chatFusion.command;

import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

public record ServerConnexion(String name) {
    private static final byte OPCODE = 15;
    private static final Charset UTF8 = StandardCharsets.UTF_8;

    public ServerConnexion{
        Objects.requireNonNull(name);
        if(name.isEmpty()){
            throw new IllegalArgumentException();
        }
    }

    public ByteBuffer toBuffer(){
        var bbName = UTF8.encode(name);
        var buffer = ByteBuffer.allocate(Integer.BYTES + bbName.remaining() + Byte.BYTES);

        buffer.put(OPCODE).putInt(bbName.remaining()).put(bbName);
        return buffer;
    }
}
