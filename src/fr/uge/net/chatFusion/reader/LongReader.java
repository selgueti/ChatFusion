package fr.uge.net.chatFusion.reader;

import java.nio.ByteBuffer;

public class LongReader implements Reader<Long>{

    private final ByteBuffer internalBuffer = ByteBuffer.allocate(Long.BYTES); // write-mode
    private State state = State.WAITING;
    private long value;

    @Override
    public ProcessStatus process(ByteBuffer bb) {
        if (state == State.DONE || state == State.ERROR) {
            throw new IllegalStateException();
        }
        bb.flip();
        try {
            if (bb.remaining() <= internalBuffer.remaining()) {
                internalBuffer.put(bb);
            } else {
                var oldLimit = bb.limit();
                bb.limit(internalBuffer.remaining());
                internalBuffer.put(bb);
                bb.limit(oldLimit);
            }
        } finally {
            bb.compact();
        }
        if (internalBuffer.hasRemaining()) {
            return ProcessStatus.REFILL;
        }
        state = State.DONE;
        internalBuffer.flip();
        value = internalBuffer.getLong();
        return ProcessStatus.DONE;
    }

    @Override
    public Long get() {
        if (state != State.DONE) {
            throw new IllegalStateException();
        }
        return value;
    }

    @Override
    public void reset() {
        internalBuffer.clear();
        state = State.WAITING;

    }

    private enum State {
        DONE, WAITING, ERROR
    }
}
