package fr.uge.net.chatFusion.util;

import fr.uge.net.chatFusion.command.*;

/**
 * The secound half to the 2 way visitor patern.
 */
public interface FrameVisitor {

    void visit(FilePrivate filePrivate);

    void visit(FusionInexistantServer fusionInexistantServer);

    void visit(FusionInit fusionInit);

    void visit(FusionInvalidName fusionInvalidName);

    void visit(FusionRegisterServer fusionRegisterServer);

    void visit(FusionRootTableAsk fusionRootTableAsk);

    void visit(FusionRouteTableSend fusionRouteTableSend);

    void visit(FusionTableRouteResult fusionTableRouteResult);

    void visit(LoginAccepted loginAccepted);

    void visit(LoginAnonymous loginAnonymous);

    void visit(LoginRefused loginRefused);

    void visit(MessagePrivate messagePrivate);

    void visit(MessagePublicSend messagePublicSend);

    void visit(MessagePublicTransmit messagePublicTransmit);

    void visit(ServerConnexion serverConnexion);

}
