package dev.freakyfriday.mind;

import java.util.Map;
import java.util.UUID;

/** Deterministic whole-mind assignment tests; no Minecraft API or fake AI. */
public final class MindLedgerSelfTest {
    private static int checks;
    private static void check(boolean good, String reason) {
        checks++;
        if (!good) throw new AssertionError(reason);
    }
    private static UUID id(long n) { return new UUID(0L, n); }
    private static final MindLedger.Compatibility ALLOW_SUPPORTED = (body, incoming) -> {};

    public static void main(String[] args) throws Exception {
        // NPC <-> NPC: all state is represented by a SINGLE mind identity,
        // never by selecting attack goals or attributes independently.
        MindLedger npc = new MindLedger();
        UUID villagerBody = id(1), zombieBody = id(2);
        var villagerMind = new MindLedger.Mind(id(101), MindLedger.Kind.NPC, "minecraft:villager");
        var zombieMind = new MindLedger.Mind(id(102), MindLedger.Kind.NPC, "minecraft:zombie");
        npc.registerOriginal(villagerBody, villagerMind);
        npc.registerOriginal(zombieBody, zombieMind);
        Map<UUID, MindLedger.Mind> beforeNpcs = npc.snapshot();
        npc.exchange(villagerBody, zombieBody, ALLOW_SUPPORTED);
        check(npc.controlling(villagerBody).equals(zombieMind), "zombie mind controls villager body");
        check(npc.controlling(zombieBody).equals(villagerMind), "villager mind controls zombie body");
        check(npc.bodyControlledBy(villagerMind.identity()).orElseThrow().equals(zombieBody),
            "mind can be found by immutable identity");
        npc.exchange(villagerBody, zombieBody, ALLOW_SUPPORTED);
        check(npc.snapshot().equals(beforeNpcs), "reverse NPC swap exactly");
        try { npc.snapshot().clear(); throw new AssertionError("mutable public snapshot"); }
        catch (UnsupportedOperationException expected) { checks++; }
        try { npc.registerOriginal(id(3), villagerMind); throw new AssertionError("duplicate mind identity"); }
        catch (IllegalStateException expected) { checks++; }
        try { npc.registerOriginal(villagerBody, new MindLedger.Mind(id(103), MindLedger.Kind.NPC, "test")); throw new AssertionError("replaced original registration"); }
        catch (IllegalStateException expected) { checks++; }

        // Two authenticated player minds trade physical avatars, not accounts.
        MindLedger players = new MindLedger();
        UUID bodyA = id(11), bodyB = id(12);
        var playerA = new MindLedger.Mind(id(201), MindLedger.Kind.PLAYER, "minecraft:player");
        var playerB = new MindLedger.Mind(id(202), MindLedger.Kind.PLAYER, "minecraft:player");
        players.registerOriginal(bodyA, playerA);
        players.registerOriginal(bodyB, playerB);
        players.exchange(bodyA, bodyB, ALLOW_SUPPORTED);
        check(players.controlling(bodyA).equals(playerB), "player B controls avatar A");
        check(players.controlling(bodyB).equals(playerA), "player A controls avatar B");
        check(players.bodyControlledBy(playerA.identity()).orElseThrow().equals(bodyB),
            "server can route player A input to other avatar");

        // Player <-> mob: AI and human control swap as atomic bundles.
        MindLedger mixed = new MindLedger();
        UUID humanBody = id(21), cowBody = id(22);
        mixed.registerOriginal(humanBody, playerA);
        mixed.registerOriginal(cowBody, zombieMind);
        mixed.exchange(humanBody, cowBody, ALLOW_SUPPORTED);
        check(mixed.controlling(humanBody).equals(zombieMind), "AI controls abandoned human body");
        check(mixed.controlling(cowBody).equals(playerA), "human controls cow body");

        // Recipient cannot execute complete mind: NO part of either side changes.
        Map<UUID, MindLedger.Mind> previous = mixed.snapshot();
        try {
            mixed.exchange(humanBody, cowBody, (body, mind) -> {
                if (body.equals(cowBody) && mind.kind() == MindLedger.Kind.NPC)
                    throw new UnsupportedOperationException("Unable to rebind full NPC behavior");
            });
            throw new AssertionError("unsupported runtime should reject");
        } catch (UnsupportedOperationException expected) { checks++; }
        check(mixed.snapshot().equals(previous), "whole-mind preflight rejection leaves both intact");

        try { mixed.exchange(cowBody, cowBody, ALLOW_SUPPORTED); throw new AssertionError("self swap"); }
        catch (IllegalArgumentException expected) { checks++; }
        try { mixed.exchange(cowBody, id(9999), ALLOW_SUPPORTED); throw new AssertionError("unregistered body"); }
        catch (IllegalArgumentException expected) { checks++; }
        check(mixed.bodyControlledBy(playerA.identity()).orElseThrow().equals(cowBody),
            "original player mind never duplicated");
        check(!mixed.bodyControlledBy(id(9999)).isPresent(), "unregistered mind not invented");

        System.out.println("PASS MindLedgerSelfTest: " + checks
            + " checks; NPC/NPC, player/player, player/NPC, exact reverse, uniqueness and no-partial preflight");
    }
}
