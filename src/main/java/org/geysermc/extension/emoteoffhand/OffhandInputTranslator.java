package org.geysermc.extension.emoteoffhand;

import org.cloudburstmc.protocol.bedrock.packet.PlayerAuthInputPacket;
import org.geysermc.geyser.session.GeyserSession;
import org.geysermc.geyser.translator.protocol.PacketTranslator;
import org.geysermc.geyser.translator.protocol.bedrock.entity.player.input.BedrockPlayerAuthInputTranslator;

/**
 * Runs Geyser's own input translation, then raises a shield when shift did not.
 * An attack lowers the shield as on Bedrock; it comes back on the next retry while shift is held.
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
        delegate.translate(session, packet);
        JavaOffhand.raiseOnSneak(session);
    }
}
