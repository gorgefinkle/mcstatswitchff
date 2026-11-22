package dev.freakyfriday.mind;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Real binary/HMAC code, fake physical BodyPort; no native donor AI executed.
 */
public final class CapturedIntentWireCodecSelfTest {
    static int assertions;
    static void check(boolean yes, String reason) {
        assertions++;
        if (!yes) throw new AssertionError(reason);
    }
    static void reject(Runnable action, String reason) {
        try {
            action.run();
            throw new AssertionError(reason);
        } catch (IllegalArgumentException expected) {
            assertions++;
        }
    }

    private static final class Body implements BodyActionGate.BodyPort {
        final UUID id;
        int executed;
        Body(UUID id) { this.id = id; }
        @Override public UUID id() { return id; }
        @Override public boolean supports(String name) { return name.equals("test:look"); }
        @Override public BodyActionGate.Result perform(BodyActionGate.Intent action) {
            executed++;
            return BodyActionGate.Result.executed();
        }
    }

    public static void main(String[] args) throws Exception {
        byte[] key = CapturedIntentWireCodec.generateKey();
        byte[] wrongKey = CapturedIntentWireCodec.generateKey();
        UUID session = UUID.randomUUID();
        UUID bodyId = UUID.randomUUID();
        UUID mindId = UUID.randomUUID();
        var original = new CapturedIntentBridge.Frame(bodyId, mindId, 90,
            List.of(new BodyActionGate.Intent("test:look",
                Map.of("x","5.25","z","-3","y","80"))));
        var envelope = new CapturedIntentWireCodec.Envelope(session, original);

        byte[] packet = CapturedIntentWireCodec.encode(envelope,key);
        check(packet.length < CapturedIntentWireCodec.MAX_WIRE_BYTES,
            "bound permits normal authenticated packet");
        var unpacked = CapturedIntentWireCodec.decode(packet,key,session,90);
        check(unpacked.session().equals(session), "session preserved");
        check(unpacked.frame().bodyId().equals(bodyId), "body preserved");
        check(unpacked.frame().mindId().equals(mindId), "controller preserved");
        check(unpacked.frame().tick()==90, "tick preserved");
        check(unpacked.frame().actions().equals(original.actions()),
            "actions, params and strings preserved exactly");
        check(Arrays.equals(packet,CapturedIntentWireCodec.encode(envelope,key)),
            "canonical field ordering produces deterministic packet");
        check(packet.length >= 32+4+1+16*3+8+1, "fixed binary header");

        var bridged = new CapturedIntentBridge();
        var body = new Body(bodyId);
        bridged.register(body,new MindLedger.Mind(mindId,
            MindLedger.Kind.NPC,"test:data_only"));
        var first = bridged.deliverAuthenticated(packet,key,session,90);
        check(first.status()==CapturedIntentBridge.Status.DISPATCHED
                && body.executed==1,
            "authenticated data frame routes to the sole physical body");
        check(bridged.deliverAuthenticated(packet,key,session,90).status()
                == CapturedIntentBridge.Status.DUPLICATE_OR_STALE_TICK
                && body.executed==1,
            "replay attack has no physical side effect");

        byte[] modified = packet.clone();
        modified[25] ^= 1;
        reject(() -> bridged.deliverAuthenticated(modified,key,session,90),
            "tampered signed payload should fail closed");
        check(body.executed==1, "tampered packet cannot call body");
        byte[] badSignature = packet.clone();
        badSignature[badSignature.length-1] ^= 1;
        reject(() -> bridged.deliverAuthenticated(badSignature,key,session,90),
            "tampered HMAC must fail");
        reject(() -> bridged.deliverAuthenticated(packet,wrongKey,session,90),
            "wrong secret must fail");
        reject(() -> bridged.deliverAuthenticated(packet,key,UUID.randomUUID(),90),
            "cross-session injection must fail");
        reject(() -> bridged.deliverAuthenticated(packet,key,session,89),
            "future frame must fail the live server tick check");
        reject(() -> bridged.deliverAuthenticated(packet,key,session,91),
            "stale frame must fail the live server tick check");
        reject(() -> bridged.deliverAuthenticated(
            Arrays.copyOf(packet,packet.length-1),key,session,90),
            "truncated signed packet must fail");
        reject(() -> bridged.deliverAuthenticated(
            new byte[CapturedIntentWireCodec.MAX_WIRE_BYTES+1],key,session,90),
            "oversized packet must fail before parsing");
        reject(() -> CapturedIntentWireCodec.encode(envelope,new byte[8]),
            "weak key length must be rejected");
        check(body.executed==1, "every rejected wire packet leaves body unchanged");

        // Wrong mind is still rejected AFTER authenticating its data packet.
        UUID otherMind = UUID.randomUUID();
        var wrongMind = new CapturedIntentBridge.Frame(bodyId,otherMind,91,
            List.of(new BodyActionGate.Intent("test:look")));
        byte[] signedWrongMind = CapturedIntentWireCodec.encode(
            new CapturedIntentWireCodec.Envelope(session,wrongMind),key);
        var wrong = bridged.deliverAuthenticated(signedWrongMind,key,session,91);
        check(wrong.status() == CapturedIntentBridge.Status.WRONG_CONTROLLER,
            "authenticated but unauthorized controller may not act");
        check(body.executed==1,"wrong controller cannot execute physical actions");

        byte[] sameTick = CapturedIntentWireCodec.encode(
            new CapturedIntentWireCodec.Envelope(session,
                new CapturedIntentBridge.Frame(bodyId,mindId,91,
                    List.of(new BodyActionGate.Intent("test:look")))),key);
        check(bridged.deliverAuthenticated(sameTick,key,session,91).status()
                == CapturedIntentBridge.Status.DISPATCHED
                && body.executed==2,
            "rejected unauthorized frame does not lock out correct current controller");

        System.out.println("PASS CapturedIntentWireCodecSelfTest: "+assertions+
            " assertions; authenticated binary data only, strict session/tick,"+
            " tamper resistance and replay rejection");
    }
}
