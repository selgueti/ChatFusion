package fr.uge.net.chatFusion.util;

import fr.uge.net.chatFusion.command.SocketAddressToken;

import java.util.Objects;

public record EntryRouteTable(String name, SocketAddressToken socketAddressToken) {

    public EntryRouteTable {
        Objects.requireNonNull(name);
        Objects.requireNonNull(socketAddressToken);
    }
}
