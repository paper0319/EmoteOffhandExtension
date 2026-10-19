package org.geysermc.extension.emoteoffhand;

import org.cloudburstmc.protocol.bedrock.packet.InventoryTransactionPacket;
import org.cloudburstmc.protocol.bedrock.packet.PlayerAuthInputPacket;
import org.geysermc.event.subscribe.Subscribe;
import org.geysermc.geyser.GeyserImpl;
import org.geysermc.geyser.api.event.bedrock.ClientEmoteEvent;
import org.geysermc.geyser.api.event.lifecycle.GeyserPostInitializeEvent;
import org.geysermc.geyser.api.extension.Extension;
import org.geysermc.geyser.registry.Registries;
import org.geysermc.geyser.translator.protocol.bedrock.BedrockInventoryTransactionTranslator;
import org.geysermc.geyser.translator.protocol.bedrock.entity.player.input.BedrockPlayerAuthInputTranslator;

import java.util.concurrent.TimeUnit;

public class EmoteOffhand implements Extension {
    @Subscribe
    public void onPostInitialize(GeyserPostInitializeEvent ignored) {
        // Other extensions replace this translator from the same event. Wait until they have finished,
        // then wrap whoever is installed so both behaviors stay active.
        GeyserImpl.getInstance().getScheduledThread().schedule(this::installOffhandUse, 1, TimeUnit.SECONDS);
        this.logger().info("Loaded EmoteOffhand!");
    }

    private void installOffhandUse() {
        Object current = Registries.BEDROCK_PACKET_TRANSLATORS.get(InventoryTransactionPacket.class);
        if (current instanceof OffhandTransactionTranslator) {
            return;
        }
        BedrockInventoryTransactionTranslator delegate = current instanceof BedrockInventoryTransactionTranslator translator
                ? translator : null;
        Registries.BEDROCK_PACKET_TRANSLATORS.register(InventoryTransactionPacket.class, new OffhandTransactionTranslator(delegate));
        installSneakShield();
        this.logger().info("Offhand use now follows Java Edition hand order.");
    }

    private void installSneakShield() {
        Object current = Registries.BEDROCK_PACKET_TRANSLATORS.get(PlayerAuthInputPacket.class);
        if (current instanceof OffhandInputTranslator) {
            return;
        }
        if (!(current instanceof BedrockPlayerAuthInputTranslator delegate)) {
            this.logger().warning("Could not wrap player input, so a shield still depends on Geyser's own sneak block.");
            return;
        }
        Registries.BEDROCK_PACKET_TRANSLATORS.register(PlayerAuthInputPacket.class, new OffhandInputTranslator(delegate));
    }

    @Subscribe
    public void onEmote(ClientEmoteEvent event) {
        event.connection().requestOffhandSwap();
    }
}
