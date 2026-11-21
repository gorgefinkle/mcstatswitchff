package dev.freakyfriday.mind;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Body capability and whole-mind swap model tests. These are generic mock
 * bodies; an actual Minecraft adapter still needs dedicated in-game tests.
 */
public final class BodyActionGateSelfTest {
    private static int checks;
    private static void require(boolean result, String reason) {
        checks++;
        if (!result) throw new AssertionError(reason);
    }
    private static UUID id(long n) { return new UUID(0, n); }

    /** All body-owned state stays here, never in the mind ledger. */
    private static final class Body implements BodyActionGate.BodyPort {
        final UUID id;
        final String appearance;
        final double speed, maxHealth;
        final String loot, interaction;
        final Set<String> abilities;
        final Map<String, Integer> executed = new HashMap<>();
        Body(UUID id, String appearance, double speed, double maxHealth,
             String loot, String interaction, Set<String> abilities) {
            this.id=id; this.appearance=appearance; this.speed=speed; this.maxHealth=maxHealth;
            this.loot=loot; this.interaction=interaction;
            this.abilities=new HashSet<>(abilities);
        }
        @Override public UUID id() { return id; }
        @Override public boolean supports(String action) { return abilities.contains(action); }
        @Override public BodyActionGate.Result perform(BodyActionGate.Intent intent) {
            if ("minecraft:detonate".equals(intent.actionId())
                    && "blocked".equals(intent.arguments().get("state")))
                return BodyActionGate.Result.blocked("Physical fuse not primed");
            executed.merge(intent.actionId(), 1, Integer::sum);
            return BodyActionGate.Result.executed();
        }
    }

    public static void main(String[] args) throws Exception {
        // Mock names are fixtures, NOT a species-specific production whitelist.
        Body cow = new Body(id(1), "cow", 0.2, 10, "beef/leather", "milk",
            Set.of("minecraft:walk", "minecraft:look", "minecraft:moo"));
        Body creeper = new Body(id(2), "creeper", 0.25, 20, "gunpowder", "none",
            Set.of("minecraft:walk", "minecraft:look", "minecraft:detonate"));
        var cowMind = new MindLedger.Mind(id(101), MindLedger.Kind.NPC, "test:cow_mind");
        var creeperMind = new MindLedger.Mind(id(102), MindLedger.Kind.NPC, "test:creeper_mind");
        MindLedger ledger = new MindLedger();
        ledger.registerOriginal(cow.id(), cowMind);
        ledger.registerOriginal(creeper.id(), creeperMind);

        ledger.exchange(cow.id(), creeper.id(), (body, mind) -> {});
        require(ledger.controlling(cow.id()).equals(creeperMind), "cow body now has creeper mind");
        require(ledger.controlling(creeper.id()).equals(cowMind), "creeper body now has cow mind");
        require(cow.appearance.equals("cow") && cow.maxHealth == 10 && cow.speed == 0.2,
            "cow model, health and movement speed stay on the body");
        require(cow.loot.equals("beef/leather") && cow.interaction.equals("milk"),
            "cow loot and milking remain body-owned");
        require(creeper.loot.equals("gunpowder") && creeper.maxHealth == 20,
            "creeper loot and health remain body-owned");

        var detonate = new BodyActionGate.Intent("minecraft:detonate");
        var denied = BodyActionGate.dispatch(cow, detonate);
        require(denied.status() == BodyActionGate.Status.UNAVAILABLE,
            "creeper mind can want to explode; cow body cannot physically explode");
        require(!cow.executed.containsKey("minecraft:detonate"),
            "unsupported request never activates a fake explosion");
        var walking = BodyActionGate.dispatch(cow, new BodyActionGate.Intent("minecraft:walk"));
        require(walking.status() == BodyActionGate.Status.EXECUTED,
            "creeper mind drives cow legs using cow native movement");
        require(cow.executed.get("minecraft:walk") == 1,
            "movement executed only on receiving body");
        require(!creeper.executed.containsKey("minecraft:detonate"),
            "creeper body does not automatically explode without intent from cow mind");
        var blocked = BodyActionGate.dispatch(creeper,
            new BodyActionGate.Intent("minecraft:detonate", Map.of("state", "blocked")));
        require(blocked.status() == BodyActionGate.Status.BLOCKED,
            "body may possess explosive mechanism but decline execution at this tick");
        require(!creeper.executed.containsKey("minecraft:detonate"),
            "blocked action never executes");
        require(ledger.controlling(cow.id()).equals(creeperMind),
            "missing body capability does not cancel the complete mind swap");

        var request = new BodyActionGate.Intent("minecraft:walk", Map.of("speed", "0.25"));
        try { request.arguments().put("speed", "8"); throw new AssertionError("mutable intent"); }
        catch (UnsupportedOperationException expected) { checks++; }
        require(BodyActionGate.snapshotArguments(request).get("speed").equals("0.25"),
            "intent arguments remain immutable");
        try { new BodyActionGate.Intent("detonate"); throw new AssertionError("nonregistry id"); }
        catch (IllegalArgumentException expected) { checks++; }

        ledger.exchange(cow.id(), creeper.id(), (body, mind) -> {});
        require(ledger.controlling(cow.id()).equals(cowMind)
            && ledger.controlling(creeper.id()).equals(creeperMind),
            "reversing the whole mind swap restores controllers, not body stats");
        require(cow.interaction.equals("milk") && creeper.loot.equals("gunpowder"),
            "body ownership remains stable after reversal");
        System.out.println("PASS BodyActionGateSelfTest: " + checks + " assertions");
    }
}
