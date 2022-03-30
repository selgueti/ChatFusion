package fr.uge.net.chatFusion;

import java.nio.ByteBuffer;

public interface Command {
    ProcessStatus writeIn(ByteBuffer bufferOut);
    ProcessStatus readFrom(ByteBuffer bufferIn);
    void reset();
    boolean isTotalyWritten();
    byte getOpcode();

    enum ProcessStatus {DONE, REFILL_INPUT, KEEP_WRITING, ERROR}
}
