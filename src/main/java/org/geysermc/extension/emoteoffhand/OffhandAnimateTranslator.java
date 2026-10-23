package org.geysermc.extension.emoteoffhand;

import org.cloudburstmc.protocol.bedrock.packet.AnimatePacket;
import org.geysermc.geyser.session.GeyserSession;
import org.geysermc.geyser.translator.protocol.PacketTranslator;
import org.geysermc.geyser.translator.protocol.bedrock.BedrockAnimateTranslator;

/**
 * A swing makes Geyser drop the blocking shield. Java ignores left-click while a shield is up, so skip it.
 */
public final class OffhandAnimateTranslator extends PacketTranslator<AnimatePacket> {
    private final BedrockAnimateTranslator delegate;

    public OffhandAnimateTranslator(BedrockAnimateTranslator delegate) {
        this.delegate = delegate;
    }

    @Override
    public void translate(GeyserSession session, AnimatePacket packet) {
        if (packet.getAction() == AnimatePacket.Action.SWING_ARM && JavaOffhand.shieldRaisedBySneak(session)) {
            return;
        }
        delegate.translate(session, packet);
    }
}
