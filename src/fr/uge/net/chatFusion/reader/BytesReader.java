package fr.uge.net.chatFusion.reader;

import java.nio.ByteBuffer;
import java.util.List;

public class BytesReader implements Reader<byte[]>{
    private final int size;
    private final ByteBuffer internalBuffer; // write-mode
    private State state = State.WAITING;

    public BytesReader(int size){
        if(size < 0){
            throw new IllegalArgumentException("size < 0");
        }
        this.size = size;
        internalBuffer = ByteBuffer.allocate(size);
    }

    @Override
    public ProcessStatus process(ByteBuffer bb) {
        if (state == State.DONE || state == State.ERROR) {
            throw new IllegalStateException();
        }
        fillInternalBufferFromBuffer(bb);
        if (internalBuffer.position() < size) {
            return ProcessStatus.REFILL;
        }
        state = State.DONE;
        return ProcessStatus.DONE;
    }

    @Override
    public byte[] get() {
        if (state != State.DONE) {
            throw new IllegalStateException();
        }
        return internalBuffer.array();
    }

    @Override
    public void reset() {
        state = State.WAITING;
        internalBuffer.clear();
    }

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

    private enum State {
        DONE, WAITING, ERROR
    }
}
