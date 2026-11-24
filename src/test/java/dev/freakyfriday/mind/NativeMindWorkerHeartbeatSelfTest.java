package dev.freakyfriday.mind;

public final class NativeMindWorkerHeartbeatSelfTest {
    private NativeMindWorkerHeartbeatSelfTest() {}
    private static int checks;
    private static void check(boolean value,String message) {
        checks++;
        if(!value) throw new AssertionError(message);
    }
    private static void rejects(Runnable action,String message) {
        try { action.run(); }
        catch(RuntimeException expected) { checks++; return; }
        throw new AssertionError(message);
    }

    public static void main(String[] args) {
        var heartbeat=new NativeMindWorkerHeartbeat(100,1700,10);
        check(heartbeat.lastWorkerTick()==100 && heartbeat.lastSenseTick()==1700,
            "initial worker/sensory tick binding");
        check(!heartbeat.expire(110),"exact silence deadline remains live");
        heartbeat.received(101,1701);
        check(heartbeat.lastWorkerTick()==101 && heartbeat.lastSenseTick()==1701,
            "adjacent next sensory frame admitted");
        check(!heartbeat.expire(111),"heartbeat refresh resets silence deadline");
        check(heartbeat.expire(112),"missed sensory heartbeat retires session");
        check(heartbeat.failed(),"timeout is terminal");
        rejects(()->heartbeat.received(113,1702),"failed heartbeat cannot be revived");

        var skipped=new NativeMindWorkerHeartbeat(20,50,10);
        rejects(()->skipped.received(21,52),"skipped recipient tick rejected");
        check(skipped.failed(),"skipped sensory tick faults session");

        var replay=new NativeMindWorkerHeartbeat(20,50,10);
        rejects(()->replay.received(21,50),"replayed recipient tick rejected");
        check(replay.failed(),"replayed sensory tick faults session");

        var stale=new NativeMindWorkerHeartbeat(20,50,10);
        rejects(()->stale.received(20,51),"same worker tick cannot accept a second snapshot");
        check(stale.failed(),"nonadvancing worker tick faults session");

        var delayed=new NativeMindWorkerHeartbeat(20,50,10);
        rejects(()->delayed.received(31,51),"late frame cannot revive a timed-out stream");
        check(delayed.failed(),"late frame faults session");

        var backwards=new NativeMindWorkerHeartbeat(20,50,10);
        check(backwards.expire(19),"worker clock moving backwards retires session");

        rejects(()->new NativeMindWorkerHeartbeat(-1,50,10),"negative worker tick rejected");
        rejects(()->new NativeMindWorkerHeartbeat(1,50,0),"zero timeout rejected");
        rejects(()->new NativeMindWorkerHeartbeat(1,50,201),"unbounded timeout rejected");
        System.out.println("PASS NativeMindWorkerHeartbeatSelfTest: "+checks
            +" assertions; bounded silence deadline, strict consecutive sensory ticks, "
            +"replay rejection, terminal expiry and no late-session revival");
    }
}
