package fr.uge.net.chatFusion.command;

import fr.uge.net.chatFusion.util.FrameVisitor;

import java.nio.ByteBuffer;

/**
 * This interface represent the basic generic frame behaviour common to all frames,
 * as wellas the necessary method to implemnt a visitor partern on frames.
 */
public interface Frame {
    /**
     * Turns the frames inforamtion into a ByteBuffer ready to be written on the channel.
     * @return the resulting ByteBuffer.
     */
    ByteBuffer toBuffer();

    /**
     * Base method of the 2 way Visitor patern.
     * @param visitor the frame visitor (allows us to destinguish beween several general states)
     */
    void accept (FrameVisitor visitor);

}
