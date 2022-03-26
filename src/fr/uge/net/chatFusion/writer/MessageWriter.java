package fr.uge.net.chatFusion.writer;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

public class MessageWriter {

    private final String login;
    private final String text;
    private ByteBuffer internalBuffer = null;

    public MessageWriter(String login, String text) {
        Objects.requireNonNull(login);
        Objects.requireNonNull(text);
        this.login = login;
        this.text = text;
    }

    public MessageWriter(MessageWriter msg) {
        this(msg.login, msg.text);
    }

    @Override
    public String toString() {
        return login + " : " + text;
    }

    public String getLogin() {
        return login;
    }

    public String getText() {
        return text;
    }

    /**
     * The convention is that internalBuffer is in write-mode before the call to setBuffer and
     * after the call
     */
    private void setBuffer() {
        var UTF8login = StandardCharsets.UTF_8.encode(login);
        var UTF8Content = StandardCharsets.UTF_8.encode(text);
        internalBuffer = ByteBuffer.allocate(UTF8login.remaining() + UTF8Content.remaining() + (Integer.BYTES * 2));
        internalBuffer.putInt(UTF8login.limit()).put(UTF8login);
        internalBuffer.putInt(UTF8Content.limit()).put(UTF8Content);
    }

    /**
     * The convention is that buffer is in write-mode before the call to fillBuffer and
     * after the call
     */
    public void fillBuffer(ByteBuffer buffer) {
        if (internalBuffer == null) {
            setBuffer();
        }
        if (internalBuffer.position() == 0) {
            return;
        }
        internalBuffer.flip(); //was in WRITE, needs to be READ.
        var oldLimit = internalBuffer.limit();
        var limitNew = Math.min(buffer.remaining(), internalBuffer.remaining());

        internalBuffer.limit(limitNew); // setting limit to max byte available to send/be put in buffer
        buffer.put(internalBuffer);
        internalBuffer.limit(oldLimit);
        internalBuffer.compact(); // removing from buffer all that was read + putting it back to WRITE mode.
    }


    /**
     * Return true if message is completely send, otherwise false
     * The convention is that internalBuffer is in write-mode before the call to isDone and
     * after the call
     */
    public boolean isDone() {
        return internalBuffer.position() == 0;
    }
}
