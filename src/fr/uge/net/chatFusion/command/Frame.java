package fr.uge.net.chatFusion.command;

import fr.uge.net.chatFusion.FrameVisitor.*;
import fr.uge.net.chatFusion.fr.uge.net.FrameVisitor.*;

import java.nio.ByteBuffer;

public interface Frame {

    ByteBuffer toBuffer();
    void accept (ServerInterlocutorUnknownFrameVisitor serverFrameVisitor);
    void accept (ServerToClientFrameVisitor serverFrameVisitor);
    void accept (ServerToSFMFrameVisitor serverFrameVisitor);
    void accept (ServerToServerFrameVisitor serverFrameVisitor);
    void accept (SFMUnregisteredFrameVisitor serverFrameVisitor);
    void accept (SFMRegisteredFrameVisitor serverFrameVisitor);
    void accept (ClientUnregisteredFrameVisitor serverFrameVisitor);
    void accept (ClientLoggedFrameVisitor serverFrameVisitor);
}
