package fr.uge.net.chatFusion.util;

import fr.uge.net.chatFusion.command.SocketAddressToken;

import java.util.Objects;

/**
 * Describes an entry on the routing table beween servers.
 * @param name the name of the server
 * @param socketAddressToken the address (IP + Port) to contact this server
 */
public record EntryRouteTable(String name, SocketAddressToken socketAddressToken) {

    public EntryRouteTable {
        Objects.requireNonNull(name);
        Objects.requireNonNull(socketAddressToken);
    }
}
