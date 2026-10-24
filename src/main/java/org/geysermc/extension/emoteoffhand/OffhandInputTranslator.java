package org.geysermc.extension.emoteoffhand;

import org.cloudburstmc.protocol.bedrock.data.PlayerAuthInputData;
import org.cloudburstmc.protocol.bedrock.data.PlayerActionType;
import org.cloudburstmc.protocol.bedrock.packet.PlayerAuthInputPacket;
import org.geysermc.geyser.session.GeyserSession;
import org.geysermc.geyser.translator.protocol.PacketTranslator;
import org.geysermc.geyser.translator.protocol.bedrock.entity.player.input.BedrockPlayerAuthInputTranslator;

/**
 * Runs Geyser's own input translation, then raises a shield when shift did not.
 * The auth-input translator is final, so this wraps it instead of extending it.
 */
public final class OffhandInputTranslator extends PacketTranslator<PlayerAuthInputPacket> {
    private final BedrockPlayerAuthInputTranslator delegate;

    public OffhandInputTranslator(BedrockPlayerAuthInputTranslator delegate) {
        this.delegate = delegate;
    }

    @Override
    public void translate(GeyserSession session, PlayerAuthInputPacket packet) {
        JavaOffhand.purgeExpiredCooldowns(session);
        // Java ignores left-click while a shield is up, and Geyser would drop the shield on a swing.
        if (JavaOffhand.shieldRaisedBySneak(session)) {
            packet.getInputData().remove(PlayerAuthInputData.MISSED_SWING);
            packet.getPlayerActions().removeIf(a -> a.getAction() == PlayerActionType.START_BREAK
                    || a.getAction() == PlayerActionType.CONTINUE_BREAK);
        }
        delegate.translate(session, packet);
        JavaOffhand.raiseOnSneak(session);
    }
}
