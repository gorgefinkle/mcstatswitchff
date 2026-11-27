#!/usr/bin/env python3
"""Compile and exercise GoalSelectorState against a minimal scheduler contract.
This is a focused regression harness, not a replacement for the real NeoForge build.
"""
from pathlib import Path
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parent.parent
SOURCE = ROOT / "src/main/java/dev/freakyfriday/mind/GoalSelectorState.java"
with tempfile.TemporaryDirectory(prefix="ff-goal-selector-") as temp:
    root = Path(temp)
    files = {
        "net/minecraft/world/entity/ai/goal/Goal.java": """
package net.minecraft.world.entity.ai.goal;
import java.util.EnumSet;
public class Goal {
 public enum Flag { MOVE, LOOK, JUMP }
 private final EnumSet<Flag> flags;
 public Goal(Flag... flags) { this.flags=EnumSet.noneOf(Flag.class); for(Flag f:flags)this.flags.add(f); }
 public EnumSet<Flag> getFlags(){return flags;}
}
""",
        "net/minecraft/world/entity/ai/goal/WrappedGoal.java": """
package net.minecraft.world.entity.ai.goal;
public class WrappedGoal {
 private final int priority; private final Goal goal; public boolean isRunning;
 public WrappedGoal(int p,Goal g){priority=p;goal=g;}
 public int getPriority(){return priority;} public Goal getGoal(){return goal;}
 public boolean isRunning(){return isRunning;} public Goal.Flag[] flags(){return goal.getFlags().toArray(Goal.Flag[]::new);}
 public java.util.EnumSet<Goal.Flag> getFlags(){return goal.getFlags();}
}
""",
        "net/minecraft/world/entity/ai/goal/GoalSelector.java": """
package net.minecraft.world.entity.ai.goal;
import java.util.*;
import java.util.function.Predicate;
public class GoalSelector {
 public final Set<WrappedGoal> availableGoals=new LinkedHashSet<>();
 public final Map<Goal.Flag,WrappedGoal> lockedFlags=new EnumMap<>(Goal.Flag.class);
 public final Set<Goal.Flag> disabledFlags=EnumSet.noneOf(Goal.Flag.class);
 public Set<WrappedGoal> getAvailableGoals(){return availableGoals;}
 public void addGoal(int p,Goal g){availableGoals.add(new WrappedGoal(p,g));}
 public void removeAllGoals(Predicate<Goal> p){availableGoals.removeIf(w->p.test(w.getGoal()));lockedFlags.clear();}
 public void setControlFlag(Goal.Flag f,boolean enabled){if(enabled)disabledFlags.remove(f);else disabledFlags.add(f);}
}
""",
        "GoalSelectorStateHarness.java": """
import java.util.*;
import net.minecraft.world.entity.ai.goal.*;
import dev.freakyfriday.mind.GoalSelectorState;
public class GoalSelectorStateHarness {
 static int checks;
 static void ok(boolean b,String m){checks++;if(!b)throw new AssertionError(m);}
 static WrappedGoal find(GoalSelector s,Goal g){return s.getAvailableGoals().stream().filter(w->w.getGoal()==g).findFirst().orElseThrow();}
 public static void main(String[] args){
  GoalSelector source=new GoalSelector();
  Goal active=new Goal(Goal.Flag.MOVE,Goal.Flag.LOOK);
  Goal idle=new Goal(Goal.Flag.JUMP);
  source.addGoal(2,active);source.addGoal(7,idle);
  WrappedGoal activeWrap=find(source,active);activeWrap.isRunning=true;
  source.lockedFlags.put(Goal.Flag.MOVE,activeWrap);source.lockedFlags.put(Goal.Flag.LOOK,activeWrap);
  source.disabledFlags.add(Goal.Flag.JUMP);
  GoalSelectorState.Snapshot captured=GoalSelectorState.capture(source);
  captured.disabledFlags().clear();
  ok(captured.disabledFlags().contains(Goal.Flag.JUMP),"snapshot disabled flags are defensively copied");
  Goal activeCopy=new Goal(Goal.Flag.MOVE,Goal.Flag.LOOK);
  Goal idleCopy=new Goal(Goal.Flag.JUMP);
  var entries=List.of(new GoalSelectorState.Entry(2,activeCopy,true),new GoalSelectorState.Entry(7,idleCopy,false));
  var locks=new EnumMap<Goal.Flag,Goal>(Goal.Flag.class);locks.put(Goal.Flag.MOVE,activeCopy);locks.put(Goal.Flag.LOOK,activeCopy);
  var incoming=new GoalSelectorState.Snapshot(entries,EnumSet.of(Goal.Flag.JUMP),locks);
  GoalSelector target=new GoalSelector();
  GoalSelectorState.install(target,incoming);
  ok(GoalSelectorState.matches(target,incoming),"active and idle wrapper state, disabled controls and locks restore exactly");
  ok(find(target,activeCopy).isRunning(),"running wrapper flag restored");
  ok(!find(target,idleCopy).isRunning(),"idle wrapper remains idle");
  ok(target.lockedFlags.get(Goal.Flag.MOVE)==find(target,activeCopy),"control lock points at recipient wrapper");
  ok(target.disabledFlags.equals(EnumSet.of(Goal.Flag.JUMP)),"disabled controls restored");

  GoalSelectorState.Snapshot roundTrip=GoalSelectorState.capture(target);
  GoalSelectorState.install(source,roundTrip);
  ok(GoalSelectorState.matches(source,roundTrip),"reverse scheduler transfer round-trips");

  GoalSelector broken=new GoalSelector();Goal brokenGoal=new Goal(Goal.Flag.MOVE,Goal.Flag.LOOK);
  broken.addGoal(1,brokenGoal);WrappedGoal bw=find(broken,brokenGoal);bw.isRunning=true;
  broken.lockedFlags.put(Goal.Flag.MOVE,bw); // LOOK lock deliberately missing
  try {GoalSelectorState.capture(broken);throw new AssertionError("missing lock must reject");}
  catch(IllegalStateException expected){ok(expected.getMessage().contains("missing its selector lock"),"inconsistent source scheduler fails closed");}

  GoalSelector collision=new GoalSelector();
  Goal one=new Goal(Goal.Flag.MOVE),two=new Goal(Goal.Flag.MOVE);
  var collisionEntries=List.of(new GoalSelectorState.Entry(1,one,true),new GoalSelectorState.Entry(2,two,true));
  try {GoalSelectorState.install(collision,new GoalSelectorState.Snapshot(collisionEntries,EnumSet.noneOf(Goal.Flag.class),Map.of()));throw new AssertionError("conflicting active locks must reject");}
  catch(IllegalStateException expected){ok(expected.getMessage().contains("Two running goals claim"),"conflicting control claims fail closed");}
  System.out.println("PASS GoalSelectorStateHarness: "+checks+" checks");
 }
}
"""
    }
    sources=[]
    for name, text in files.items():
        path=root/name
        path.parent.mkdir(parents=True,exist_ok=True)
        path.write_text(text)
        sources.append(str(path))
    classes=root/"classes"
    classes.mkdir()
    subprocess.run(["javac","-d",str(classes),*sources,str(SOURCE)],check=True)
    subprocess.run(["java","-ea","-cp",str(classes),"GoalSelectorStateHarness"],check=True)
