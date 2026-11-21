package dev.freakyfriday;

import dev.freakyfriday.core.CategoryDefinitions;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

/** Server-owned selection state. The client never chooses a target entity ID by command. */
public final class SwapSessions {
    private SwapSessions() {}
    private record Session(UUID first, String dimension, String category, long started) {}
    private static final Map<UUID, Session> SESSIONS = new ConcurrentHashMap<>();
    // One physical right-click can pass through both interaction and item callbacks,
    // and Minecraft can try main/offhand interaction in the same game tick.
    // Deduplicate by player + target + server tick, not by wall clock time.
    private record InteractionStamp(UUID target, long gameTick) {}
    private static final Map<UUID, InteractionStamp> LAST_INTERACTION = new ConcurrentHashMap<>();
    private static final long EXPIRE_NANOS = 120L * 1_000_000_000L;

    private static boolean hasBook(ServerPlayer p) {
        return p.getMainHandItem().is(FreakyFriday.BOOK.get()) || p.getOffhandItem().is(FreakyFriday.BOOK.get());
    }
    private static void message(ServerPlayer p, String content) {
        p.sendSystemMessage(Component.literal("[Freaky Friday] " + content));
    }
    private static Session current(ServerPlayer player) {
        Session session = SESSIONS.get(player.getUUID());
        if (session != null && (!session.dimension.equals(player.level().dimension().identifier().toString())
                || System.nanoTime() - session.started > EXPIRE_NANOS)) {
            SESSIONS.remove(player.getUUID());
            return null;
        }
        return session;
    }
    public static void clearOnLogout(UUID playerId) {
        SESSIONS.remove(playerId);
        LAST_INTERACTION.remove(playerId);
    }
    /** The only entry point for physical mob clicks; commands use select directly. */
    public static void selectFromInteraction(ServerPlayer player, LivingEntity target) {
        if (!hasBook(player)) return;
        long tick = player.level().getGameTime();
        InteractionStamp stamp = new InteractionStamp(target.getUUID(), tick);
        if (stamp.equals(LAST_INTERACTION.put(player.getUUID(), stamp))) return;
        select(player, target);
    }
    public static void cancel(ServerPlayer player) {
        SESSIONS.remove(player.getUUID());
        message(player,"Selection cancelled.");
    }
    public static void select(ServerPlayer player, LivingEntity target) {
        if (!hasBook(player)) { message(player,"Hold the Freaky Friday book first."); return; }
        if (!target.isAlive()) {
            message(player,"Only living, active entities can be selected."); return;
        }
        if (!player.level().dimension().equals(target.level().dimension())) {
            message(player,"Both targets must be in the same dimension."); return;
        }
        Session existing = current(player);
        if (existing == null) {
            SESSIONS.put(player.getUUID(), new Session(target.getUUID(),
                player.level().dimension().identifier().toString(), null, System.nanoTime()));
            message(player,"First target: " + target.getDisplayName().getString() + ". Choose an entire category:");
            showCategories(player,target);
            return;
        }
        LivingEntity first = resolve(player, existing);
        if (first == null || !first.isAlive()) {
            cancel(player); message(player,"First target is no longer alive or loaded."); return;
        }
        if (existing.category == null) {
            message(player,"Choose a category first (one choice exchanges ALL its stats).");
            showCategories(player, first);
            return;
        }
        if (first == target) { message(player,"Second target must be different."); return; }
        String error = AttributeCategorySwapper.exchange(first,target,existing.category);
        if (error != null) { message(player,error); return; }
        SESSIONS.remove(player.getUUID());
        message(player,"Swapped ENTIRE '" + CategoryDefinitions.GROUPS.get(existing.category).label()
            + "' category between " + first.getDisplayName().getString() + " and "
            + target.getDisplayName().getString() + ".");
    }
    private static LivingEntity resolve(ServerPlayer player, Session session) {
        Entity entity = player.level().getEntity(session.first);
        return entity instanceof LivingEntity living ? living : null;
    }
    public static void showCategories(ServerPlayer player, LivingEntity selected) {
        if (selected == null) { message(player,"First target is no longer available."); return; }
        Map<String,Integer> groups = AttributeCategorySwapper.availableCategories(selected);
        if (groups.isEmpty()) { message(player,"No compatible stat categories found."); return; }
        for (var entry: groups.entrySet()) {
            String id = entry.getKey();
            String label = CategoryDefinitions.GROUPS.get(id).label();
            player.sendSystemMessage(Component.literal("  [" + label + " · " + entry.getValue() + " values]")
                .withStyle(ChatFormatting.AQUA)
                .withStyle(style -> style.withClickEvent(
                    new ClickEvent.RunCommand("/freakyfriday category " + id))));
        }
        player.sendSystemMessage(Component.literal("  [Cancel]")
            .withStyle(ChatFormatting.GRAY)
            .withStyle(style -> style.withClickEvent(new ClickEvent.RunCommand("/freakyfriday cancel"))));
    }
    public static void choose(ServerPlayer player, String category) {
        if (!hasBook(player)) { message(player,"Hold the book first."); return; }
        Session session = current(player);
        if (session == null) { message(player,"Select the first target first."); return; }
        LivingEntity first = resolve(player,session);
        if (first == null || !first.isAlive()) { cancel(player); return; }
        if (!AttributeCategorySwapper.availableCategories(first).containsKey(category)) {
            message(player,"That category is not available on the selected target."); return;
        }
        SESSIONS.put(player.getUUID(),new Session(session.first,session.dimension,category,session.started));
        message(player,"Selected ENTIRE " + CategoryDefinitions.GROUPS.get(category).label()
            + " category. Right-click second mob or Ctrl+S to choose yourself.");
    }
}
