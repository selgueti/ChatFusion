package fr.uge.net.chatFusion.command;

import java.nio.ByteBuffer;

public interface Frame {

    ByteBuffer toBuffer();
}
