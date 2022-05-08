package fr.uge.net.chatFusion.util;

import java.util.Objects;

/**
 * This record, stores which server asked to be linked with whom.
 * @param initiator the server contacting the Fusion manager to initiate a Fusion.
 * @param contacted the server they want to be linked with.
 */
public record FusionAsk(EntryRouteTable initiator, EntryRouteTable contacted) {
    public FusionAsk {
        Objects.requireNonNull(initiator);
        Objects.requireNonNull(contacted);
    }
}
