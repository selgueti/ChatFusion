package fr.uge.net.chatFusion.reader;

import java.nio.ByteBuffer;

public class BytesReader implements Reader<byte[]> {
    private final int size;
    private final ByteBuffer internalBuffer; // write-mode
    private State state = State.WAITING;

    public BytesReader(int size) {
        if (size < 0) {
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
        //System.out.println("internal buffer before fill = " + internalBuffer);
        //System.out.println("bb before fill = " + bb);
        fillInternalBufferFromBuffer(bb);
        //System.out.println("internal buffer after fill = " + internalBuffer);
        //System.out.println("bb after fill = " + bb);
        if (internalBuffer.hasRemaining()) {
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
            System.out.println("internalBuffer.position() = " + internalBuffer.position() + ", size = " + size);
            return;
        }
        buffer.flip(); //was in WRITE, needs to be READ.

        var oldLimit = buffer.limit();
        //System.out.println("oldLimit = " + buffer.limit());

        var toBeRead = Math.min(size - internalBuffer.position(), buffer.remaining());
        //System.out.println("toBeRead = " + toBeRead);

        var limitNew = Math.min(internalBuffer.remaining(), toBeRead);
        //System.out.println("limitNew = " + limitNew);

        buffer.limit(limitNew); // setting limit to max byte available to send/be put in buffer
        internalBuffer.put(buffer);
        buffer.limit(oldLimit);
        buffer.compact(); // removing from buffer all that was read + putting it back to WRITE mode.
    }

    private enum State {
        DONE, WAITING, ERROR
    }
}
