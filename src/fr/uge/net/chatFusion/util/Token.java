package fr.uge.net.chatFusion.util;

import java.nio.ByteBuffer;

public interface Token <T>{

    ByteBuffer encode();

    void decode(ByteBuffer bufferIn);
    T get(); // think about this
    void reset(); //throws UnsupportedOperationExceptions;
}
