package dev.freakyfriday.mind;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Pure-Java regression for two independently authenticated reciprocal mind streams. */
public final class NativeMindWorkerPairSessionsSelfTest {
    private NativeMindWorkerPairSessionsSelfTest() {}
    private static int checks;

    private static void check(boolean ok,String message) {
        checks++;
        if(!ok)throw new AssertionError(message);
    }

    private static void rejects(Runnable operation,String message) {
        try { operation.run(); }
        catch(RuntimeException expected) { checks++; return; }
        throw new AssertionError(message);
    }

    private static NativeMindSenseFrame senses(UUID body,UUID mind,long tick,boolean alive) {
        return new NativeMindSenseFrame(body,mind,tick,"minecraft:overworld",
            new NativeMindSenseFrame.BodySense("minecraft:cow",0,0,0,0,0,0,
                0,0,0.9,1.4,10,10,alive,true,false,false),List.of(),List.of());
    }

    private static byte[] response(UUID session,UUID body,UUID mind,long tick,byte[] key) {
        var frame=new CapturedIntentBridge.Frame(body,mind,tick,List.of(
            new BodyActionGate.Intent(BodyActionGate.LOOK_OFFSET,
                Map.of("dx","0","dy","0","dz","1"))));
        return CapturedIntentWireCodec.encode(
            new CapturedIntentWireCodec.Envelope(session,frame),key);
    }

    public static void main(String[] args) {
        UUID pairId=UUID.randomUUID();
        UUID bodyA=UUID.randomUUID(),bodyB=UUID.randomUUID();
        UUID mindA=UUID.randomUUID(),mindB=UUID.randomUUID();
        byte[] keyA=CapturedIntentWireCodec.generateKey();
        byte[] keyB=CapturedIntentWireCodec.generateKey();

        var registry=new NativeMindWorkerPairSessions();
        var pair=registry.openPair(pairId,bodyA,mindA,keyA,bodyB,mindB,keyB,40,8);
        check(!pair.first().sessionId().equals(pair.second().sessionId()),
            "each mind has an independent session ID");
        check(registry.activePairs()==1&&registry.isBodyReserved(bodyA)
                &&registry.isBodyReserved(bodyB),
            "both original bodies are reserved by one reciprocal pair");
        check(registry.pairForBody(bodyA).equals(pairId)
                &&registry.pairForBody(bodyB).equals(pairId),
            "both physical bodies resolve to the same pair coordinator");

        var requestA=registry.begin(pairId,bodyA,senses(bodyA,mindA,40,true),100);
        var requestB=registry.begin(pairId,bodyB,senses(bodyB,mindB,40,true),100);
        check(requestA.sessionId().equals(pair.first().sessionId())
                &&requestB.sessionId().equals(pair.second().sessionId())
                &&!requestA.sessionId().equals(requestB.sessionId()),
            "both directions can have independent requests outstanding at once");
        check(registry.hasOutstandingRequest(pairId,bodyA)
                &&registry.hasOutstandingRequest(pairId,bodyB),
            "each endpoint maintains its own in-flight request");

        var resultA=registry.complete(pairId,bodyA,
            response(pair.first().sessionId(),bodyA,mindA,40,keyA),102);
        check(resultA.bodyId().equals(bodyA)&&resultA.mindId().equals(mindA)
                &&resultA.tick()==102,
            "first mind's authenticated result is routed to the first physical body");
        check(registry.hasOutstandingRequest(pairId,bodyB),
            "completing one mind does not consume its partner's outstanding request");

        var resultB=registry.complete(pairId,bodyB,
            response(pair.second().sessionId(),bodyB,mindB,40,keyB),101);
        check(resultB.bodyId().equals(bodyB)&&resultB.mindId().equals(mindB)
                &&resultB.tick()==101,
            "second mind's authenticated result is routed independently");
        check(!registry.hasOutstandingRequest(pairId,bodyA)
                &&!registry.hasOutstandingRequest(pairId,bodyB),
            "both independent channels complete their own frames");

        // Adjacent sensory sequences can advance independently after a result
        // arrives on a later host tick.
        var nextA=registry.begin(pairId,bodyA,senses(bodyA,mindA,41,true),107);
        var nextB=registry.begin(pairId,bodyB,senses(bodyB,mindB,41,true),108);
        registry.complete(pairId,bodyB,
            response(pair.second().sessionId(),bodyB,mindB,41,keyB),109);
        check(registry.hasOutstandingRequest(pairId,bodyA)
                &&!registry.hasOutstandingRequest(pairId,bodyB),
            "one stream may advance while its reciprocal partner remains in flight");
        registry.complete(pairId,bodyA,
            response(pair.first().sessionId(),bodyA,mindA,41,keyA),110);
        check(nextA.tick()==41&&nextB.tick()==41,
            "both streams advance their own consecutive sensory ticks");

        // A cross-session signed response is rejected and retires BOTH sides.
        UUID pair2=UUID.randomUUID();
        UUID bodyC=UUID.randomUUID(),bodyD=UUID.randomUUID();
        UUID mindC=UUID.randomUUID(),mindD=UUID.randomUUID();
        byte[] keyC=CapturedIntentWireCodec.generateKey();
        byte[] keyD=CapturedIntentWireCodec.generateKey();
        var pairCross=registry.openPair(pair2,bodyC,mindC,keyC,bodyD,mindD,keyD,7,8);
        registry.begin(pair2,bodyC,senses(bodyC,mindC,7,true),200);
        registry.begin(pair2,bodyD,senses(bodyD,mindD,7,true),200);
        rejects(()->registry.complete(pair2,bodyD,
            response(pairCross.first().sessionId(),bodyD,mindD,7,keyD),201),
            "a first-direction session envelope cannot be admitted by the second direction");
        check(registry.activePairs()==1&&!registry.isBodyReserved(bodyC)
                &&!registry.isBodyReserved(bodyD),
            "a cross-session response fault retires the entire pair, not just one mind");

        // Timeout of either one-in-flight channel also releases both bodies.
        UUID pair3=UUID.randomUUID();
        UUID bodyE=UUID.randomUUID(),bodyF=UUID.randomUUID();
        UUID mindE=UUID.randomUUID(),mindF=UUID.randomUUID();
        byte[] keyE=CapturedIntentWireCodec.generateKey();
        byte[] keyF=CapturedIntentWireCodec.generateKey();
        registry.openPair(pair3,bodyE,mindE,keyE,bodyF,mindF,keyF,12,2);
        registry.begin(pair3,bodyE,senses(bodyE,mindE,12,true),300);
        registry.begin(pair3,bodyF,senses(bodyF,mindF,12,true),300);
        check(registry.expire(pair3,bodyE,303),
            "one missed worker deadline is detected");
        check(registry.activePairs()==1&&!registry.isBodyReserved(bodyE)
                &&!registry.isBodyReserved(bodyF),
            "one-direction timeout cannot leave the other physical body controlled");

        // Physical bodies cannot be simultaneously reserved by two pairs.
        UUID pair4=UUID.randomUUID();
        UUID bodyG=UUID.randomUUID(),mindG=UUID.randomUUID();
        UUID mindH=UUID.randomUUID(),mindI=UUID.randomUUID();
        byte[] keyG=CapturedIntentWireCodec.generateKey();
        byte[] keyH=CapturedIntentWireCodec.generateKey();
        byte[] keyI=CapturedIntentWireCodec.generateKey();
        registry.openPair(pair4,bodyG,mindG,keyG,bodyA,mindH,keyH,1,5);
        rejects(()->registry.openPair(UUID.randomUUID(),bodyA,mindA,keyA,
            UUID.randomUUID(),mindI,keyI,1,5),
            "a physical body already reserved by another pair cannot be reused");
        check(registry.closePair(pair4)&&!registry.closePair(pair4),
            "pair cancellation is idempotent and reports one successful close");
        check(!registry.isBodyReserved(bodyG)&&!registry.isBodyReserved(bodyA),
            "pair cancellation releases both body reservations");

        registry.closePair(pairId);
        check(registry.activePairs()==0&&!registry.isBodyReserved(bodyB),
            "normal pair cancellation releases both directions");
        registry.close();
        rejects(()->registry.openPair(UUID.randomUUID(),
            UUID.randomUUID(),UUID.randomUUID(),CapturedIntentWireCodec.generateKey(),
            UUID.randomUUID(),UUID.randomUUID(),CapturedIntentWireCodec.generateKey(),0,5),
            "closed pair registry cannot accept new work");

        System.out.println("PASS NativeMindWorkerPairSessionsSelfTest: "+checks
            +" assertions; two independent authenticated streams, concurrent in-flight frames, "
            +"separate tick clocks, cross-session rejection, atomic timeout/cancel and body exclusivity");
    }
}
