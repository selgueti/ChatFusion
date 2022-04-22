package fr.uge.net.chatFusion.command;

import fr.uge.net.chatFusion.util.FrameVisitor;

import java.nio.ByteBuffer;

public interface Frame {

    ByteBuffer toBuffer();
    void accept (FrameVisitor visitor);

}
