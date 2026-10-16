package org.geysermc.extension.emoteoffhand;

import org.cloudburstmc.math.vector.Vector3f;
import org.cloudburstmc.math.vector.Vector3i;
import org.cloudburstmc.protocol.bedrock.data.definitions.ItemDefinition;
import org.cloudburstmc.protocol.bedrock.data.inventory.ItemData;
import org.cloudburstmc.protocol.bedrock.data.inventory.transaction.InventoryTransactionType;
import org.cloudburstmc.protocol.bedrock.packet.InventoryTransactionPacket;
import org.geysermc.geyser.inventory.GeyserItemStack;
import org.geysermc.geyser.item.type.BlockItem;
import org.geysermc.geyser.item.type.BoatItem;
import org.geysermc.geyser.item.type.Item;
import org.geysermc.geyser.item.type.SpawnEggItem;
import org.geysermc.geyser.level.block.property.Properties;
import org.geysermc.geyser.level.block.type.BlockState;
import org.geysermc.geyser.level.physics.Direction;
import org.geysermc.geyser.registry.BlockRegistries;
import org.geysermc.geyser.session.GeyserSession;
import org.geysermc.geyser.translator.item.ItemTranslator;
import org.geysermc.geyser.translator.protocol.bedrock.BedrockInventoryTransactionTranslator;
import org.geysermc.geyser.util.BlockUtils;
import org.geysermc.mcprotocollib.protocol.data.game.entity.player.GameMode;
import org.geysermc.mcprotocollib.protocol.data.game.entity.player.Hand;
import org.geysermc.mcprotocollib.protocol.data.game.item.component.DataComponentTypes;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.player.ServerboundUseItemOnPacket;

/**
 * Decides when a Bedrock use-click should also reach the offhand.
 * Java tries the main hand first. Only a click that passes there continues to the offhand.
 */
final class JavaOffhand {
    private JavaOffhand() {
    }

    static void afterTranslate(GeyserSession session, InventoryTransactionPacket packet, long interactionTimeBefore) {
        if (session.isHandsBusy() || session.getGameMode() == GameMode.SPECTATOR) {
            return;
        }
        if (packet.getTransactionType() != InventoryTransactionType.ITEM_USE) {
            return;
        }

        GeyserItemStack mainHand = session.getPlayerInventory().getItemInHand();
        GeyserItemStack offhand = session.getPlayerInventory().getOffhand();
        if (offhand.isEmpty() || hasUseAction(mainHand)) {
            return;
        }
        // A shield is raised by sneaking. Geyser already sends that for either hand.
        // Right-click must not start blocking, and a shield in the main hand must not
        // swallow the click that the offhand item still needs.
        if (isShield(offhand)) {
            return;
        }

        if (packet.getActionType() == 1) {
            if (hasUseAction(offhand)) {
                session.useItem(Hand.OFF_HAND);
            }
            return;
        }
        if (packet.getActionType() != 0 || packet.getBlockPosition() == null || packet.getClickPosition() == null) {
            return;
        }
        // Geyser leaves the timestamp alone when it drops a repeated click.
        if (session.getLastInteractionTime() == interactionTimeBefore) {
            return;
        }
        if (heldItemDesynced(session, packet) || !clickWasAccepted(session, packet)) {
            return;
        }

        BlockState clicked = session.getGeyser().getWorldManager().blockAt(session, packet.getBlockPosition());
        if (blockIntercepts(session, clicked) || useOnConsumes(mainHand, clicked)) {
            return;
        }

        // Same face and click point the outline is using. Do not recast from the eyes:
        // that misses the highlighted block and drops a wrong-sized model beside the camera.
        Direction face = Direction.getUntrusted(packet, InventoryTransactionPacket::getBlockFace);
        float cursorX = fixCursorValue(packet.getClickPosition().getX());
        float cursorY = fixCursorValue(packet.getClickPosition().getY());
        float cursorZ = fixCursorValue(packet.getClickPosition().getZ());
        Vector3i placePosition = BlockUtils.getBlockPosition(packet.getBlockPosition(), face);
        session.getWorldCache().removePrediction(placePosition);
        int sequence = session.getWorldCache().nextPredictionSequence();
        session.getWorldCache().markPositionInSequence(placePosition);
        session.sendDownstreamGamePacket(new ServerboundUseItemOnPacket(
                packet.getBlockPosition(),
                face.mcpl(),
                Hand.OFF_HAND,
                cursorX, cursorY, cursorZ,
                false,
                false,
                sequence));

        if (offhand.asItem() instanceof BlockItem blockItem) {
            session.setLastBlockPlacePosition(placePosition);
            session.setLastBlockPlaced(blockItem);
        }

        if (hasUseAction(offhand) && !useOnConsumes(offhand, clicked)) {
            session.useItem(Hand.OFF_HAND);
        }
    }

    private static boolean clickWasAccepted(GeyserSession session, InventoryTransactionPacket packet) {
        if (session.getBlockMappings().isItemFrame(packet.getBlockDefinition())) {
            return false;
        }
        if (!session.getWorldBorder().isInsideBorderBoundaries()) {
            return false;
        }
        Vector3f eye = session.getPlayerEntity().position().up(session.getEyeHeight());
        if (!BedrockInventoryTransactionTranslator.canInteractWithBlock(session, eye, packet.getBlockPosition())) {
            return false;
        }
        Vector3i clicked = packet.getBlockPosition();
        float cursorX = fixCursorValue(packet.getClickPosition().getX());
        float cursorY = fixCursorValue(packet.getClickPosition().getY());
        float cursorZ = fixCursorValue(packet.getClickPosition().getZ());
        double distanceX = clicked.getX() + (double) cursorX - (clicked.getX() + 0.5d);
        double distanceY = clicked.getY() + (double) cursorY - (clicked.getY() + 0.5d);
        double distanceZ = clicked.getZ() + (double) cursorZ - (clicked.getZ() + 0.5d);
        return Math.abs(distanceX) < 1.0000001d && Math.abs(distanceY) < 1.0000001d && Math.abs(distanceZ) < 1.0000001d;
    }

    private static boolean heldItemDesynced(GeyserSession session, InventoryTransactionPacket packet) {
        int javaSlot = session.getPlayerInventory().getOffsetForHotbar(packet.getHotbarSlot());
        ItemDefinition expected = ItemTranslator.getBedrockItemDefinition(session, session.getPlayerInventory().getItem(javaSlot));
        ItemDefinition held = packet.getItemInHand() == null ? ItemData.AIR.getDefinition() : packet.getItemInHand().getDefinition();
        return !expected.equals(held);
    }

    /**
     * Right-click actions that succeed without a block face: eating, drawing a bow, throwing, equipping.
     * Java stops at the main hand when one of these starts. Shields are raised by sneaking instead.
     */
    private static boolean hasUseAction(GeyserItemStack stack) {
        if (stack.isEmpty() || isShield(stack)) {
            return false;
        }
        if (stack.has(DataComponentTypes.CONSUMABLE) || stack.has(DataComponentTypes.INSTRUMENT) || stack.has(DataComponentTypes.BUNDLE_CONTENTS)) {
            return true;
        }
        String id = stack.asItem().javaKey().value();
        return switch (id) {
            case "bow", "crossbow", "trident", "spyglass", "brush", "fishing_rod",
                    "carrot_on_a_stick", "warped_fungus_on_a_stick", "ender_pearl", "ender_eye",
                    "snowball", "egg", "experience_bottle", "wind_charge", "firework_rocket",
                    "writable_book", "written_book", "knowledge_book", "elytra", "goat_horn" -> true;
            default -> id.endsWith("_helmet") || id.endsWith("_chestplate") || id.endsWith("_leggings") || id.endsWith("_boots");
        };
    }

    /**
     * Block clicks where the item itself does the work: placing, stripping, filling a bucket.
     */
    private static boolean useOnConsumes(GeyserItemStack stack, BlockState clicked) {
        if (stack.isEmpty()) {
            return false;
        }
        Item item = stack.asItem();
        if (item instanceof BlockItem || item instanceof BoatItem || item instanceof SpawnEggItem) {
            return true;
        }
        String id = item.javaKey().value();
        String block = clicked.block().javaIdentifier().value();
        if (id.equals("bucket") || id.endsWith("_bucket")) {
            return bucketConsumes(id, block, clicked);
        }
        if (id.endsWith("_axe")) {
            return axeConsumes(block);
        }
        if (id.endsWith("_hoe")) {
            return block.equals("grass_block") || block.equals("dirt") || block.equals("coarse_dirt") || block.equals("rooted_dirt");
        }
        if (id.endsWith("_shovel")) {
            return block.equals("grass_block") || block.equals("dirt") || block.equals("coarse_dirt")
                    || block.equals("podzol") || block.equals("mycelium") || (block.endsWith("campfire") && Boolean.TRUE.equals(clicked.getValue(Properties.LIT)));
        }
        if (id.equals("shears")) {
            return block.equals("pumpkin") || block.equals("bee_nest") || block.equals("beehive")
                    || block.contains("cave_vines") || block.contains("weeping_vines") || block.contains("twisting_vines")
                    || block.equals("kelp") || block.equals("kelp_plant");
        }
        if (id.equals("lily_pad") || id.equals("frogspawn") || id.equals("flint_and_steel") || id.equals("fire_charge")
                || id.equals("bone_meal") || id.equals("armor_stand") || id.equals("debug_stick")) {
            return true;
        }
        if (id.equals("end_crystal")) {
            return block.equals("obsidian") || block.equals("bedrock");
        }
        if (id.equals("minecart") || id.endsWith("_minecart")) {
            return block.equals("rail") || block.endsWith("_rail");
        }
        if (id.equals("glass_bottle")) {
            return block.equals("water") || block.endsWith("cauldron");
        }
        if (id.equals("honeycomb")) {
            return isSign(block) || isCopperBlock(block);
        }
        if (id.endsWith("_dye") || id.equals("ink_sac") || id.equals("glow_ink_sac")) {
            return isSign(block);
        }
        if (id.equals("lead")) {
            return block.endsWith("_fence") || block.endsWith("_fence_gate");
        }
        return false;
    }

    private static boolean bucketConsumes(String id, String block, BlockState clicked) {
        if (id.equals("milk_bucket")) {
            return false;
        }
        if (id.equals("bucket")) {
            return block.equals("water") || block.equals("lava") || block.equals("powder_snow")
                    || BlockRegistries.WATERLOGGED.get().get(clicked.javaId());
        }
        // A filled bucket places its contents, or fills a cauldron.
        return true;
    }

    private static boolean axeConsumes(String block) {
        boolean strippable = !block.startsWith("stripped_") && (block.endsWith("_log") || block.endsWith("_wood")
                || block.endsWith("_stem") || block.endsWith("_hyphae") || block.equals("bamboo_block"));
        if (strippable) {
            return true;
        }
        if (!isCopperBlock(block)) {
            return false;
        }
        return block.contains("waxed_") || block.contains("exposed_") || block.contains("weathered_") || block.contains("oxidized_");
    }

    private static boolean isCopperBlock(String block) {
        return block.contains("copper") && !block.endsWith("_ore") && !block.contains("raw_");
    }

    private static boolean isSign(String block) {
        return block.endsWith("_sign");
    }

    private static boolean isShield(GeyserItemStack stack) {
        return !stack.isEmpty() && "shield".equals(stack.asItem().javaKey().value());
    }

    private static boolean blockIntercepts(GeyserSession session, BlockState state) {
        GeyserItemStack mainHand = session.getPlayerInventory().getItemInHand();
        GeyserItemStack offhand = session.getPlayerInventory().getOffhand();
        if (session.isSneaking() && (!mainHand.isEmpty() || !offhand.isEmpty())) {
            return false;
        }
        int id = state.javaId();
        if (BlockRegistries.INTERACTIVE.get().get(id)) {
            return true;
        }
        boolean mayBuild = session.getGameMode() == GameMode.SURVIVAL || session.getGameMode() == GameMode.CREATIVE;
        return mayBuild && BlockRegistries.INTERACTIVE_MAY_BUILD.get().get(id);
    }

    private static float fixCursorValue(float value) {
        if (value <= -0.5f) {
            return -0.25f;
        }
        if (value >= 2.0f) {
            return 1.25f;
        }
        return value;
    }
}
