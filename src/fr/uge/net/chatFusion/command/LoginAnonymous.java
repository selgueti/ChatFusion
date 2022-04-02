package fr.uge.net.chatFusion.command;

import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

public record LoginAnonymous(String login) {
    private final static byte OPCODE = 0;
    private final static Charset UTF8 = StandardCharsets.UTF_8;

    public LoginAnonymous {
        Objects.requireNonNull(login);
        if(login.isEmpty()){
            throw new IllegalArgumentException("login should not be empty");
        }
    }

    public ByteBuffer toBuffer(){
        ByteBuffer bbLogin = UTF8.encode(login);
        ByteBuffer buffer = ByteBuffer.allocate(bbLogin.remaining() + Integer.BYTES + Byte.BYTES);
        buffer.put(OPCODE).putInt(bbLogin.remaining()).put(bbLogin);
        return buffer;
    }
}
