package fr.uge.net.chatFusion.util;

import java.nio.ByteBuffer;
import java.util.Objects;

public class Writer {
    private final ByteBuffer internalBuffer;

    public Writer(ByteBuffer internalBuffer) {
        Objects.requireNonNull(internalBuffer);
        this.internalBuffer = internalBuffer;
    }

    /**
     * The convention is that buffer is in write-mode before the call to fillBuffer and
     * after the call
     */
    public void fillBuffer(ByteBuffer buffer) {
        Objects.requireNonNull(buffer);
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
