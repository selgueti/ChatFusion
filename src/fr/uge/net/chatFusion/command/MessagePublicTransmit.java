package fr.uge.net.chatFusion.command;

import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

public record MessagePublicTransmit(String server, String login, String msg){
    private static final byte OPCODE = 5;
    private final static Charset UTF8 = StandardCharsets.UTF_8;

    public MessagePublicTransmit{
        Objects.requireNonNull(server);
        Objects.requireNonNull(login);
        Objects.requireNonNull(msg);


        if (server.isEmpty()) {
            throw new IllegalArgumentException("server src name should not be empty");
        }

        if (login.isEmpty()) {
            throw new IllegalArgumentException("login src should not be empty");
        }
    }

    public ByteBuffer toBuffer(){
        ByteBuffer bbServerName = UTF8.encode(server);
        ByteBuffer bbLogin = UTF8.encode(login);
        ByteBuffer bbMsg = UTF8.encode(msg);

        ByteBuffer buffer = ByteBuffer.allocate(bbServerName.remaining()
                                                + bbLogin.remaining()
                                                + bbMsg.remaining()
                                                + 3 * Integer.BYTES + Byte.BYTES);
        buffer.put(OPCODE);
        buffer.putInt(bbServerName.remaining()).put(bbServerName);
        buffer.putInt(bbLogin.remaining()).put(bbLogin);
        buffer.putInt(bbMsg.remaining()).put(bbMsg);
        return buffer;
    }
}
