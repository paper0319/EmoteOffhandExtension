package org.geysermc.extension.emoteoffhand;

import org.cloudburstmc.protocol.bedrock.data.entity.EntityFlag;
import org.cloudburstmc.protocol.bedrock.data.inventory.transaction.InventoryTransactionType;
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
        // Letting go of right-click is not letting go of shift. Leave the shield up.
        if (packet.getTransactionType() == InventoryTransactionType.ITEM_RELEASE && JavaOffhand.shieldRaisedBySneak(session)) {
            return;
        }
        boolean keepShield = JavaOffhand.shieldRaisedBySneak(session);
        if (keepShield) {
            session.getPlayerEntity().setFlag(EntityFlag.USING_ITEM, true);
        }
        try {
            translateDelegate(session, packet);
        } finally {
            if (keepShield) {
                session.getPlayerEntity().setFlag(EntityFlag.USING_ITEM, false);
            }
        }
        JavaOffhand.afterTranslate(session, packet, interactionTime);
    }

    private void translateDelegate(GeyserSession session, InventoryTransactionPacket packet) {
        if (delegate != null) {
            delegate.translate(session, packet);
        } else {
            super.translate(session, packet);
        }
    }
}
