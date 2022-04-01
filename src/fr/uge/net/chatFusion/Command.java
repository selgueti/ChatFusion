package fr.uge.net.chatFusion;

import java.nio.ByteBuffer;

public sealed interface Command permits PublicMessage{

    // suppose bufferOut in write mode
    ProcessStatus writeIn(ByteBuffer bufferOut);

    // suppose bufferIn in write mode
    ProcessStatus readFrom(ByteBuffer bufferIn);
    void reset();
    boolean isTotallyWritten();
    byte getOpcode();

    enum ProcessStatus {DONE, REFILL_INPUT, KEEP_WRITING, ERROR}
}
