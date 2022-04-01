package fr.uge.net.chatFusion.reader;

import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

public class StringReader implements Reader<String> {

    private static final int BUFFER_SIZE = 1_024;
    private static final Charset UTF8 = StandardCharsets.UTF_8;
    private final ByteBuffer internalBuffer = ByteBuffer.allocate(BUFFER_SIZE); // write-mode
    private final IntReader intReader = new IntReader();
    private State state = State.WAITING_LENGTH;
    private int size;
    private String value;

    private void fillInternalBufferFromBuffer(ByteBuffer buffer) {
        if (internalBuffer.position() >= size) {
            return;
        }
        buffer.flip(); //was in WRITE, needs to be READ.
        var oldLimit = buffer.limit();
        var toBeRead = Math.min(size - internalBuffer.position(), buffer.remaining());
        var limitNew = Math.min(internalBuffer.remaining(), toBeRead);
        buffer.limit(limitNew); // setting limit to max byte available to send/be put in buffer
        internalBuffer.put(buffer);
        buffer.limit(oldLimit);
        buffer.compact(); // removing from buffer all that was read + putting it back to WRITE mode.
    }

    @Override
    public ProcessStatus process(ByteBuffer buffer) {
        if (state == State.DONE || state == State.ERROR) {
            throw new IllegalStateException();
        }
        if (state == State.WAITING_LENGTH) {
            switch (intReader.process(buffer)) {
                case REFILL -> {
                    return ProcessStatus.REFILL;
                }
                case ERROR -> {
                    state = State.ERROR;
                    return ProcessStatus.ERROR;
                }
                case DONE -> {
                    size = intReader.get();
                    if (size < 0 || size > BUFFER_SIZE) {
                        state = State.ERROR;
                        return ProcessStatus.ERROR;
                    }
                    state = State.WAITING_TEXT;
                    // Don't return, buffer isn't necessarily empty
                }
            }
        }
        fillInternalBufferFromBuffer(buffer);
        if (internalBuffer.position() < size) {
            return ProcessStatus.REFILL;
        }
        state = State.DONE;
        internalBuffer.flip();
        value = UTF8.decode(internalBuffer).toString();
        return ProcessStatus.DONE;
    }

    @Override
    public String get() {
        if (state != State.DONE) {
            throw new IllegalStateException();
        }
        return value;
    }

    @Override
    public void reset() {
        size = 0;
        value = null;
        intReader.reset();
        state = State.WAITING_LENGTH;
        internalBuffer.clear();
    }

    private enum State {
        DONE, WAITING_LENGTH, WAITING_TEXT, ERROR
    }
}
