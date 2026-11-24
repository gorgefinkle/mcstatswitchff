package dev.freakyfriday.mind;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class NativeMindSessionChannelSelfTest {
    private NativeMindSessionChannelSelfTest() {}
    private static int checks;
    private static void check(boolean ok, String message) {
        checks++;
        if (!ok) throw new AssertionError(message);
    }
    private static void rejects(Runnable action, String message) {
        try { action.run(); } catch (RuntimeException expected) { checks++; return; }
        throw new AssertionError(message);
    }
    private static NativeMindSenseFrame senses(UUID body, UUID mind, long tick, boolean alive) {
        return new NativeMindSenseFrame(body,mind,tick,"minecraft:overworld",
            new NativeMindSenseFrame.BodySense("minecraft:cow",0,0,0,0,0,0,
                0,0,10,10,alive,true,false,false),List.of(),List.of());
    }
    private static byte[] response(UUID session, UUID body, UUID mind, long tick, byte[] key) {
        var frame=new CapturedIntentBridge.Frame(body,mind,tick,List.of(
            new BodyActionGate.Intent(BodyActionGate.LOOK_OFFSET,
                Map.of("dx","0","dy","0","dz","1"))));
        return CapturedIntentWireCodec.encode(
            new CapturedIntentWireCodec.Envelope(session,frame),key);
    }
    public static void main(String[] args) {
        UUID session=UUID.randomUUID(), body=UUID.randomUUID(), mind=UUID.randomUUID();
        byte[] key=CapturedIntentWireCodec.generateKey();
        var channel=new NativeMindSessionChannel(session,body,mind,key,40,2);
        var request=channel.begin(senses(body,mind,40,true));
        check(request.tick()==40 && request.deadlineTick()==42,"request tick/deadline");
        check(channel.hasOutstandingRequest(),"request remains outstanding");
        check(request.sensoryWire().length>32,"authenticated sensory wire emitted");
        rejects(()->channel.begin(senses(body,mind,41,true)),"must not pipeline requests");
        rejects(()->channel.complete(response(session,body,mind,41,key),40),
            "future/stale response tick must reject and fault");
        check(channel.state()==NativeMindSessionChannel.State.FAULTED,
            "mismatched response faults the session");
        rejects(()->channel.begin(senses(body,mind,41,true)),"faulted session cannot resume");

        byte[] key2=CapturedIntentWireCodec.generateKey();
        var healthy=new NativeMindSessionChannel(session,body,mind,key2,5,2);
        var req5=healthy.begin(senses(body,mind,5,true));
        rejects(()->healthy.complete(response(session,body,mind,5,key2),8),
            "late response must reject");
        check(healthy.state()==NativeMindSessionChannel.State.FAULTED,
            "late response closes admission");

        byte[] key3=CapturedIntentWireCodec.generateKey();
        var stream=new NativeMindSessionChannel(session,body,mind,key3,100,2);
        for(long tick=100;tick<120;) {
            var req=stream.begin(senses(body,mind,tick,true));
            var accepted=stream.complete(response(session,body,mind,tick,key3),tick+1);
            check(req.tick()==tick && accepted.tick()==tick+1,
                "sensory tick rebased to authoritative dispatch tick "+tick);
            check(stream.nextTick()==tick+2 && !stream.hasOutstandingRequest(),
                "next request follows the dispatch tick "+tick);
            tick=stream.nextTick();
        }
        check(stream.state()==NativeMindSessionChannel.State.ACTIVE,
            "healthy stream remains active");

        byte[] key4=CapturedIntentWireCodec.generateKey();
        var timeout=new NativeMindSessionChannel(session,body,mind,key4,7,1);
        timeout.begin(senses(body,mind,7,true));
        check(!timeout.expire(8),"deadline tick itself remains admissible");
        check(timeout.expire(9),"missing reply expires after deadline");
        check(timeout.state()==NativeMindSessionChannel.State.FAULTED,
            "timeout faults stream");

        byte[] key5=CapturedIntentWireCodec.generateKey();
        var dead=new NativeMindSessionChannel(session,body,mind,key5,9,2);
        rejects(()->dead.begin(senses(body,mind,9,false)),
            "dead physical body cannot start a new tick");
        check(dead.state()==NativeMindSessionChannel.State.FAULTED,
            "dead body retires its channel");

        byte[] key6=CapturedIntentWireCodec.generateKey();
        var wrong=new NativeMindSessionChannel(session,body,mind,key6,1,2);
        rejects(()->wrong.begin(senses(UUID.randomUUID(),mind,1,true)),
            "foreign body senses rejected");
        check(wrong.state()==NativeMindSessionChannel.State.ACTIVE,
            "caller identity mistake does not poison a valid channel");
        wrong.close();
        check(wrong.state()==NativeMindSessionChannel.State.CLOSED,
            "explicit close");

        System.out.println("PASS NativeMindSessionChannelSelfTest: "+checks
            +" assertions; sequential live ticks, no pipelining, authenticated identity, "
            +"deadline, timeout, death retirement and fail-closed response handling");
    }
}
