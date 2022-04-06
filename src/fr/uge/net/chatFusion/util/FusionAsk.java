package fr.uge.net.chatFusion.util;

import java.util.Objects;

public record FusionAsk(EntryRouteTable initiator, EntryRouteTable contacted) {
    public FusionAsk {
        Objects.requireNonNull(initiator);
        Objects.requireNonNull(contacted);
    }
}
