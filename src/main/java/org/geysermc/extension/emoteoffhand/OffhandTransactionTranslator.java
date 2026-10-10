package org.geysermc.extension.emoteoffhand;

import org.cloudburstmc.protocol.bedrock.packet.InventoryTransactionPacket;
import org.geysermc.geyser.session.GeyserSession;
import org.geysermc.geyser.translator.protocol.bedrock.BedrockInventoryTransactionTranslator;

/**
 * Runs whatever click translator is already installed, then adds the offhand
 * follow-up Java Edition would send when the main hand does not consume the click.
 */
public final class OffhandTransactionTranslator extends BedrockInventoryTransactionTranslator {
    private final BedrockInventoryTransactionTranslator delegate;

    public OffhandTransactionTranslator(BedrockInventoryTransactionTranslator delegate) {
        this.delegate = delegate;
    }

    @Override
    public void translate(GeyserSession session, InventoryTransactionPacket packet) {
        long interactionTime = session.getLastInteractionTime();
        if (delegate != null) {
            delegate.translate(session, packet);
        } else {
            super.translate(session, packet);
        }
        JavaOffhand.afterTranslate(session, packet, interactionTime);
    }
}
