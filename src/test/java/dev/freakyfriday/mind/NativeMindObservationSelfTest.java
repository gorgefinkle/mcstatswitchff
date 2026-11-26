package dev.freakyfriday.mind;

import java.util.UUID;

/**
 * Tests the species-agnostic observation math without a fake Minecraft Mob.
 * The separate NeoForge worker test must independently prove that movement
 * really came from native AI, not this pure function.
 */
public final class NativeMindObservationSelfTest {
    public static void main(String[] args) {
        int checks=0;
        assert NativeMindObservation.observe(0,0,0,2,0,0,false)==null; checks++;
        assert NativeMindObservation.observe(0,0,0,0.30,0,0,true)==null; checks++;
        var vertical=NativeMindObservation.observe(0,0,0,0,15,0,true);
        assert vertical!=null && Math.abs(vertical.dy()-3.5)<1e-10
            && vertical.dx()==0 && vertical.dz()==0; checks++;
        var n=NativeMindObservation.observe(19,60,-2,20,60,-2,true);
        assert n!=null; checks++;
        assert Math.abs(n.dx()-3.5)<1e-10 && n.dy()==0 && n.dz()==0; checks++;
        var verticalFrame=NativeMindObservation.frame(target,mind,100,vertical);
        assert verticalFrame.actions().size()==2
            && "3.5".equals(verticalFrame.actions().get(1).arguments().get("dy"))
            && "0.0".equals(verticalFrame.actions().get(1).arguments().get("dx")); checks++;
        var reverse=NativeMindObservation.observe(0,60,0,0,60,-2,true);
        assert reverse!=null && Math.abs(reverse.dz()+3.5)<1e-10; checks++;
        var diagonal=NativeMindObservation.observe(0,0,0,1,0,1,true);
        assert diagonal!=null && Math.abs(Math.hypot(diagonal.dx(),diagonal.dz())-3.5)<1e-10;
        checks++;
        UUID target=UUID.randomUUID(),mind=UUID.randomUUID();
        var frame=NativeMindObservation.frame(target,mind,99,n);
        assert frame.bodyId().equals(target) && frame.mindId().equals(mind)
            && frame.tick()==99 && frame.actions().size()==2; checks++;
        assert frame.actions().get(0).actionId().equals(BodyActionGate.LOOK_OFFSET)
            && frame.actions().get(1).actionId().equals(BodyActionGate.MOVE_OFFSET); checks++;
        assert "3.5".equals(frame.actions().get(1).arguments().get("dx")); checks++;
        UUID targetEntity=UUID.randomUUID();
        var attackFrame=NativeMindObservation.frame(target,mind,100,n,targetEntity);
        assert attackFrame.actions().size()==3
            && attackFrame.actions().get(2).actionId().equals(BodyActionGate.ATTACK_ENTITY)
            && targetEntity.toString().equals(
                attackFrame.actions().get(2).arguments().get("targetId")); checks++;
        boolean rejected=false;
        try{new NativeMindObservation.NativeNavigation(Double.NaN,0,0,1);}
        catch(IllegalArgumentException expected){rejected=true;}
        assert rejected; checks++;
        System.out.println("PASS NativeMindObservationSelfTest: "+checks
            +" assertions; real native motion required, relative actions"
            +" are independent of donor species and worker world coordinates");
    }
}
