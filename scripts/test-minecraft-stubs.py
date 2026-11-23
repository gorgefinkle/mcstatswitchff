#!/usr/bin/env python3
"""Compile and exercise the REAL Minecraft-facing R0.9 classes against synthetic API mocks.
Not a substitute for NeoForge binary linkage or a live game run.
"""
from pathlib import Path
import subprocess
import shutil
ROOT=Path(__file__).resolve().parent.parent
DEST=ROOT/'build'/'stub-harness'; STUB=DEST/'stubs'; OUT=DEST/'classes'
if DEST.exists(): shutil.rmtree(DEST)
STUB.mkdir(parents=True,exist_ok=True); OUT.mkdir(parents=True,exist_ok=True)
S={
# Actual Minecraft-controlled possession, client camera and networking are
# compiled/verified by the real NeoForge jobs, not old fake API classes.
'dev/freakyfriday/PossessionNetwork.java':'''package dev.freakyfriday;
public final class PossessionNetwork {
 public static void register(Object ignored){}
}
''',
'dev/freakyfriday/mind/SelectionHudSync.java':'''package dev.freakyfriday.mind;
import net.minecraft.server.level.ServerPlayer;
/** Only NeoForge verifies the actual client-bound selection packets. */
public final class SelectionHudSync {
 public static void show(ServerPlayer p,String name,int remaining){}
 public static void clear(ServerPlayer p){}
}
''',
'dev/freakyfriday/mind/PlayerMobPossession.java':'''package dev.freakyfriday.mind;
import java.util.UUID;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;
public final class PlayerMobPossession {
 public static boolean hasSession(UUID id){return false;}
 public static String begin(ServerPlayer p,Mob body){return "synthetic harness"; }
 public static boolean end(ServerPlayer p){return false;}
 public static void clearOnLogout(UUID id){}
 public static void clearOnServerStop(){}
}
''',
'dev/freakyfriday/mind/PlayerPlayerExchange.java':'''package dev.freakyfriday.mind;
import java.util.UUID;
import net.minecraft.server.level.ServerPlayer;
/** Stand-in for legacy source harness. Real player physics compile and
 * execute in REQUIRED dedicated NeoForge GameTests on both versions. */
public final class PlayerPlayerExchange {
 public static boolean active(UUID id){return false;}
 public static String begin(ServerPlayer p,ServerPlayer target){return null;}
 public static boolean cancel(ServerPlayer p){return false;}
 public static void clearOnLogout(UUID id){}
 public static void clearOnServerStop(){}
}
''',
'dev/freakyfriday/mind/NativeBrainSwap.java':'''package dev.freakyfriday.mind;
import net.minecraft.world.entity.Mob;
public final class NativeBrainSwap {
 public static String exchange(Mob a,Mob b){return "synthetic harness"; }
}
''',
'dev/freakyfriday/FFServerSmoke.java':'''package dev.freakyfriday;
public final class FFServerSmoke {}
''',
'dev/freakyfriday/FFDiagnosticAutoTest.java':'''package dev.freakyfriday;
import net.minecraft.server.level.ServerPlayer;
public final class FFDiagnosticAutoTest {
  public static int runSuite(ServerPlayer p){ return 1; }
  public static int runPair(ServerPlayer p, String d, String r){ return 1; }
}
''',
'dev/freakyfriday/mind/ProbeTelemetry.java':'''package dev.freakyfriday.mind;
public final class ProbeTelemetry {
 public static String newTrace(){return "SYNTHETIC";}
 public static void info(String t,String p,String f,Object... v){}
 public static void warn(String t,String p,String f,Object... v){}
 public static void error(String t,String p,Throwable e,String detail){}
}''',
'net/minecraft/world/entity/Mob.java':'''package net.minecraft.world.entity;
import net.minecraft.server.level.ServerLevel;
public class Mob extends LivingEntity {
 public Mob(String name, ServerLevel level){super(name,level);}
}''',
'dev/freakyfriday/mind/DetachedHostMirrorProbe.java':'''package dev.freakyfriday.mind;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;
public final class DetachedHostMirrorProbe {
 public static String start(ServerPlayer player,Mob donor,Mob recipient) {
  return "Started 40-tick detached host MIRROR (synthetic test only)";
 }
}''',
'dev/freakyfriday/mind/DetachedMindHostProbe.java':'''package dev.freakyfriday.mind;
import net.minecraft.world.entity.Mob;
/** Headless stand-in. Only real NeoForge builds compile the detached EntityType factory. */
public final class DetachedMindHostProbe {
 public static String inspect(Mob donor,Mob recipient) {
  return "Dual-instance host feasibility: nonspawned donor type (synthetic only) donor=" + donor.name + " recipient=" + recipient.name;
 }
}''',
'dev/freakyfriday/mind/LiveBodyProbe.java':'''package dev.freakyfriday.mind;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;
/** Synthetic-only stand-in. The real 40-tick trace needs NeoForge runtime. */
public final class LiveBodyProbe {
 public static String start(ServerPlayer player, Mob body) {
  return "Starting 40-tick body-control probe for " + body.name
    + ". No donor AI was run (synthetic harness).";
 }
}''',
'dev/freakyfriday/mind/MinecraftHostProbe.java':'''package dev.freakyfriday.mind;
import net.minecraft.server.level.ServerPlayer;import net.minecraft.world.entity.Mob;
/** Headless probe stub only, real body port tested by NeoForge. */
public final class MinecraftHostProbe {
 public static String run(ServerPlayer p,Mob b){
  return "Host action probe (SYNTHETIC; not native mind swap): mock "+b.name;
 }
}''',
'dev/freakyfriday/mind/NativeAiSurfaceAudit.java':'''package dev.freakyfriday.mind;
import java.util.List;import net.minecraft.world.entity.LivingEntity;
/** Mock report only; real goal introspection is validated in NeoForge builds. */
public final class NativeAiSurfaceAudit {
 public record Report(List<String> summary) {}
 public static Report inspect(LivingEntity entity){
  return new Report(List.of("Native mind audit for "+entity.name+": synthetic stub", "ISOLATION UNVERIFIED"));
 }
}''',

'dev/freakyfriday/mind/NativeBrainSwap.java':'''package dev.freakyfriday.mind;
import net.minecraft.world.entity.LivingEntity;
/** Headless stand-in ONLY. The actual native Brain is compiled by NeoForge CI. */
public final class NativeBrainSwap {
 public static String exchange(LivingEntity a,LivingEntity b){
  return "synthetic harness";
 }
}''',
'dev/freakyfriday/CategoryPickerNetworking.java':'''package dev.freakyfriday;
import java.util.Map;
import dev.freakyfriday.core.CategoryOffers;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.Component;
public final class CategoryPickerNetworking {
  public static void register(Object event) {}
  public static void open(ServerPlayer player,String name,Map<String,CategoryOffers.Offer> offers) {
    if(offers.isEmpty())throw new AssertionError("empty GUI offer snapshot");
    player.sendSystemMessage(Component.literal("Category GUI for " + name));
  }
}''',
'net/minecraft/commands/arguments/IdentifierArgument.java':'''package net.minecraft.commands.arguments;
import net.minecraft.resources.Identifier;
public final class IdentifierArgument {
 public static Object id(){return new Object();}
 public static Identifier getId(Object context, String name){return Identifier.parse("minecraft:cow");}
}''',
'net/minecraft/resources/Identifier.java':'''package net.minecraft.resources;
public record Identifier(String id) { public static Identifier parse(String id){if(id==null||!id.contains(":"))throw new IllegalArgumentException("bad id");return new Identifier(id);} public static Identifier withDefaultNamespace(String path){return new Identifier("minecraft:"+path);} public static Identifier tryParse(String id){return parse(id);} public String toString(){return id;} }''',
'net/minecraft/core/Holder.java':'''package net.minecraft.core;
public record Holder<T>(T value) {}''',
'net/minecraft/world/entity/ai/attributes/Attribute.java':'''package net.minecraft.world.entity.ai.attributes;
public record Attribute(String name) {
public double sanitizeValue(double v){return name.equals("minecraft:movement_speed") ? Math.max(0.0,Math.min(1.0,v)):v;}
}''',
'net/minecraft/world/entity/ai/attributes/AttributeModifier.java':'''package net.minecraft.world.entity.ai.attributes;
import net.minecraft.resources.Identifier;
public record AttributeModifier(Identifier id,double amount,Operation operation) {
  public enum Operation {ADD_VALUE,ADD_MULTIPLIED_BASE,ADD_MULTIPLIED_TOTAL}
}''',
'net/minecraft/world/entity/ai/attributes/AttributeInstance.java':'''package net.minecraft.world.entity.ai.attributes;
import java.util.*;import net.minecraft.resources.Identifier;
public final class AttributeInstance {public double value;public boolean failNext, failPermanentWrite;
private final Map<Identifier,AttributeModifier> permanent=new LinkedHashMap<>(),transientMods=new LinkedHashMap<>();
public AttributeInstance(double v){value=v;}public double getBaseValue(){return value;}
public void setBaseValue(double v){if(failNext){failNext=false;throw new IllegalStateException("injected write failure");}value=v;}
public Set<AttributeModifier> getPermanentModifiers(){return Set.copyOf(permanent.values());}
public Set<AttributeModifier> getModifiers(){Set<AttributeModifier> all=new HashSet<>(permanent.values());all.addAll(transientMods.values());return all;}
public boolean removeModifier(Identifier id){return permanent.remove(id)!=null||transientMods.remove(id)!=null;}
public void addPermanentModifier(AttributeModifier m){if(failPermanentWrite){failPermanentWrite=false;throw new IllegalStateException("injected permanent modifier failure");}
 if(permanent.containsKey(m.id())||transientMods.containsKey(m.id()))throw new IllegalStateException("modifier collision");permanent.put(m.id(),m);}
public void addTransientModifier(AttributeModifier m){if(permanent.containsKey(m.id())||transientMods.containsKey(m.id()))throw new IllegalStateException("modifier collision");transientMods.put(m.id(),m);}
public double getValue(){double v=value;for(var m:getModifiers())if(m.operation()==AttributeModifier.Operation.ADD_VALUE)v+=m.amount();
 for(var m:getModifiers())if(m.operation()==AttributeModifier.Operation.ADD_MULTIPLIED_BASE)v+=value*m.amount();
 for(var m:getModifiers())if(m.operation()==AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL)v*=1+m.amount();return v;}
}''',
'net/minecraft/world/entity/ai/attributes/AttributeMap.java':'''package net.minecraft.world.entity.ai.attributes;
import java.util.*; import net.minecraft.core.Holder;
public final class AttributeMap { public final Map<Holder<Attribute>,AttributeInstance> map=new HashMap<>();
public AttributeInstance getInstance(Holder<Attribute> h){return map.get(h);} public boolean hasAttribute(Holder<Attribute> h){return map.containsKey(h);} public void put(Holder<Attribute> h,double v){map.put(h,new AttributeInstance(v));} }''',
'net/minecraft/core/registries/BuiltInRegistries.java':'''package net.minecraft.core.registries;
import java.util.*;import net.minecraft.core.Holder;import net.minecraft.resources.Identifier;import net.minecraft.world.entity.ai.attributes.Attribute;
public final class BuiltInRegistries {public static final AttributeRegistry ATTRIBUTE=new AttributeRegistry();
public static final class AttributeRegistry{private final Map<String,Holder<Attribute>> byId=new LinkedHashMap<>();
public Holder<Attribute> register(String id){return byId.computeIfAbsent(id,k->new Holder<>(new Attribute(k)));}
public java.util.Collection<Holder<Attribute>> listElements(){return byId.values();}
public Identifier getKey(Attribute v){for(var entry:byId.entrySet())if(entry.getValue().value().equals(v))return new Identifier(entry.getKey());return null;}
}}''',
'net/minecraft/ChatFormatting.java':'''package net.minecraft;public enum ChatFormatting {AQUA,GRAY}''',
'net/minecraft/network/chat/ClickEvent.java':'''package net.minecraft.network.chat;public interface ClickEvent{record RunCommand(String command) implements ClickEvent{}}''',
'net/minecraft/network/chat/Component.java':'''package net.minecraft.network.chat;
import java.util.function.UnaryOperator;import net.minecraft.ChatFormatting;
public class Component{private final String message; public Component(String msg){message=msg;}public static Component literal(String msg){return new Component(msg);}
public String getString(){return message;} public Component withStyle(ChatFormatting f){return this;}
public Component withStyle(UnaryOperator<Style> fn){fn.apply(new Style());return this;}
public static class Style{public Style withClickEvent(ClickEvent e){return this;}}
}''',
'net/minecraft/world/item/ItemStack.java':'''package net.minecraft.world.item;public record ItemStack(Object item){public boolean is(Object other){return item==other;}}''',
'net/minecraft/world/InteractionHand.java':'''package net.minecraft.world;public enum InteractionHand{MAIN_HAND,OFF_HAND}''',
'net/minecraft/world/InteractionResult.java':'''package net.minecraft.world;public enum InteractionResult{SUCCESS}''',
'net/minecraft/world/item/Item.java':'''package net.minecraft.world.item;
import net.minecraft.world.*;import net.minecraft.world.entity.*;import net.minecraft.world.entity.player.*;
public class Item {public static class Properties{public Properties stacksTo(int n){return this;}}public Item(Properties p){}
public InteractionResult interactLivingEntity(ItemStack stack,Player player,LivingEntity target, InteractionHand hand){return InteractionResult.SUCCESS;}}''',
'net/minecraft/server/level/ServerLevel.java':'''package net.minecraft.server.level;
import net.minecraft.world.entity.Entity;import net.minecraft.resources.Identifier;import java.util.*;
public final class ServerLevel {public record Dimension(Identifier identifier){} private final Dimension dim;private final Map<UUID,Entity> entities=new HashMap<>();
private long time=0; public long getGameTime(){return time;} public void tick(){++time;}
public ServerLevel(String id){dim=new Dimension(new Identifier(id));}public Dimension dimension(){return dim;}public void add(Entity e){entities.put(e.getUUID(),e);}
public Entity getEntity(UUID id){return entities.get(id);} }''',
'net/minecraft/world/entity/Entity.java':'''package net.minecraft.world.entity;
import java.util.UUID;import net.minecraft.server.level.ServerLevel;import net.minecraft.network.chat.Component;
public class Entity {private final UUID uuid=UUID.randomUUID();private final ServerLevel level;public final String name;
public Entity(String name,ServerLevel l){this.name=name;level=l;l.add(this);}public UUID getUUID(){return uuid;}public ServerLevel level(){return level;}
public Component getDisplayName(){return Component.literal(name);} public String getType(){return "mock:"+name;} }''',
'net/minecraft/world/entity/LivingEntity.java':'''package net.minecraft.world.entity;
import net.minecraft.server.level.ServerLevel;import net.minecraft.world.entity.ai.attributes.*;import net.minecraft.core.registries.BuiltInRegistries;
public class LivingEntity extends Entity{private final AttributeMap attrs=new AttributeMap();private float health=20, absorption=0; private boolean alive=true; public float maxHealthPenalty=0;
public boolean isAlive(){return alive;}public void kill(){alive=false;} public void revive(){alive=true;}
public LivingEntity(String name,ServerLevel l){super(name,l);} public AttributeMap getAttributes(){return attrs;}
public float getHealth(){return health;}public void setHealth(float value){health=Math.max(0,Math.min(value,getMaxHealth()));}
public float getMaxHealth(){var h=attrs.getInstance(BuiltInRegistries.ATTRIBUTE.register("minecraft:max_health"));return h==null?20:(float)h.getValue()-maxHealthPenalty;}
public float getAbsorptionAmount(){return absorption;}public void setAbsorptionAmount(float value){absorption=value;}
}''',
'net/minecraft/world/entity/player/Player.java':'''package net.minecraft.world.entity.player;
import net.minecraft.server.level.ServerLevel;import net.minecraft.world.entity.LivingEntity;import net.minecraft.world.item.ItemStack;
public class Player extends LivingEntity {private ItemStack main=new ItemStack(new Object()),off=new ItemStack(new Object());
public Player(String name,ServerLevel l){super(name,l);}public ItemStack getMainHandItem(){return main;}
public ItemStack getOffhandItem(){return off;}public void setMainHand(Object o){main=new ItemStack(o);}public void setOffhand(Object o){off=new ItemStack(o);} }''',
'net/minecraft/server/level/ServerPlayer.java':'''package net.minecraft.server.level;
import net.minecraft.world.entity.player.Player;import net.minecraft.network.chat.Component;import java.util.*;
public final class ServerPlayer extends Player {public final List<String> messages=new ArrayList<>();
public ServerPlayer(String name,ServerLevel l){super(name,l);}public ServerLevel serverLevel(){return level();}
public void sendSystemMessage(Component c){messages.add(c.getString());}}''',
'dev/freakyfriday/FreakyFriday.java':'''package dev.freakyfriday;public final class FreakyFriday{public static final Ref BOOK=new Ref();
public static final class Ref {private final Object item=new Object();public Object get(){return item;}}}''',
}
for n,txt in S.items():
    file=STUB/n;file.parent.mkdir(parents=True,exist_ok=True);file.write_text(txt+'\n')
harness=STUB/'dev/freakyfriday/BridgeHarness.java'
harness.write_text('''package dev.freakyfriday;
import net.minecraft.core.Holder;import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.ai.attributes.*;import net.minecraft.world.entity.LivingEntity;
import net.minecraft.server.level.*;import net.minecraft.world.InteractionHand;import net.minecraft.world.item.ItemStack;

public final class BridgeHarness {
static int checks;
static void eq(boolean v,String msg){checks++;if(!v)throw new AssertionError(msg);}
static Holder<Attribute> register(String id){return BuiltInRegistries.ATTRIBUTE.register(id);}
static void put(LivingEntity e,String id,double value){e.getAttributes().put(register(id),value);}
static double get(LivingEntity e,String id){return e.getAttributes().getInstance(register(id)).getBaseValue();}
static boolean received(ServerPlayer p,String token){return p.messages.stream().anyMatch(x->x.contains(token));}
public static void main(String[] args){
 ServerLevel world=new ServerLevel("minecraft:overworld");
 ServerPlayer p=new ServerPlayer("Player",world);p.setMainHand(FreakyFriday.BOOK.get());
 LivingEntity cow=new LivingEntity("Cow",world), bat=new LivingEntity("Bat",world);
 for(LivingEntity e:new LivingEntity[]{p,cow,bat}){
    put(e,"minecraft:max_health",20);put(e,"minecraft:movement_speed",.1);put(e,"minecraft:gravity",.06);
 }
 put(cow,"minecraft:movement_speed",.13);put(cow,"minecraft:gravity",.09);
 put(bat,"minecraft:movement_speed",.35);put(bat,"minecraft:gravity",.02);
 eq(AttributeCategorySwapper.availableCategories(cow).get("mobility")==2,"mobility has two values");
 eq(AttributeCategorySwapper.availableCategories(cow).get("vitality")==3,"vitality includes health and absorption");
 FreakyFridayBookItem book=new FreakyFridayBookItem(new net.minecraft.world.item.Item.Properties());
 SwapSessions.select(p,cow); // historical numerical-engine regression, not the new book
 eq(received(p,"Choose an entire category"),"first target selects category");
 SwapSessions.choose(p,"mobility");
 SwapSessions.select(p,bat); // old numerical transaction remains tested in isolation
 eq(get(cow,"minecraft:movement_speed")==.35 && get(cow,"minecraft:gravity")==.02,"entire mobility to cow");
 eq(get(bat,"minecraft:movement_speed")==.13 && get(bat,"minecraft:gravity")==.09,"entire mobility to bat");
 eq(received(p,"Swapped ENTIRE"),"success says category wide");
 // Reversing exchange restores all grouped attributes.
 SwapSessions.select(p,cow);SwapSessions.choose(p,"mobility");SwapSessions.select(p,bat);
 eq(get(cow,"minecraft:movement_speed")==.13 && get(bat,"minecraft:gravity")==.02,"reverse restores stats");
 // Include current health and absorption as vitality values.
 put(cow,"minecraft:max_health",40);put(bat,"minecraft:max_health",16);
 cow.setHealth(23);bat.setHealth(8);cow.setAbsorptionAmount(3);bat.setAbsorptionAmount(1);
 SwapSessions.select(p,cow);SwapSessions.choose(p,"vitality");SwapSessions.select(p,bat);
 eq(get(cow,"minecraft:max_health")==16 && cow.getHealth()==8 && cow.getAbsorptionAmount()==1,"vitality transferred to cow");
 eq(get(bat,"minecraft:max_health")==40 && bat.getHealth()==23 && bat.getAbsorptionAmount()==3,"vitality transferred to bat");
 // Different innate max-health modifiers may make a vitality exchange unsafe.
 // A rollback must restore BOTH recipients, even after a clamped health set.
 LivingEntity tall=new LivingEntity("Tall",world), shorty=new LivingEntity("Shorty",world);
 put(tall,"minecraft:max_health",30);put(shorty,"minecraft:max_health",10);
 tall.setHealth(28);shorty.maxHealthPenalty=5;shorty.setHealth(4);
 String err=AttributeCategorySwapper.exchange(tall,shorty,"vitality");
 eq(err!=null && err.contains("cannot be transferred"),"clamped health triggers whole-category rejection");
 eq(get(tall,"minecraft:max_health")==30 && tall.getHealth()==28 &&
    get(shorty,"minecraft:max_health")==10 && shorty.getHealth()==4,
    "vitality clamp restored both original categories");
 // Permanent maximum-health bonuses must move with the Vitality category,
 // and live health must remain exact after effective maximum-health changes.
 LivingEntity healthy=new LivingEntity("Healthy",world),sturdy=new LivingEntity("Sturdy",world);
 put(healthy,"minecraft:max_health",10);put(sturdy,"minecraft:max_health",30);
 var healthyMax=healthy.getAttributes().getInstance(register("minecraft:max_health"));
 var sturdyMax=sturdy.getAttributes().getInstance(register("minecraft:max_health"));
 AttributeModifier healthyBonus=new AttributeModifier(net.minecraft.resources.Identifier.parse("ff:innate_health"),10,AttributeModifier.Operation.ADD_VALUE);
 healthyMax.addPermanentModifier(healthyBonus);healthy.setHealth(15);sturdy.setHealth(24);
 err=AttributeCategorySwapper.exchange(healthy,sturdy,"vitality");
 eq(err==null,"vitality with permanent max-health bonus succeeds: "+err);
 eq(healthyMax.getBaseValue()==30&&healthyMax.getPermanentModifiers().isEmpty()&&healthy.getHealth()==24,"receiver gains base and live health from other entity");
 eq(sturdyMax.getBaseValue()==10&&sturdyMax.getPermanentModifiers().contains(healthyBonus)&&sturdy.getHealth()==15,"other entity gains permanent max-health modifier and exact live health");
 err=AttributeCategorySwapper.exchange(healthy,sturdy,"vitality");
 eq(err==null&&healthyMax.getPermanentModifiers().contains(healthyBonus)&&healthy.getHealth()==15&&sturdy.getHealth()==24,"vitality modifier reversal is lossless");
 // A different number of registered modded keys must REJECT the whole category.
 put(cow,"dragon:firepower",12);put(bat,"dragon:firepower",2);
 put(cow,"dragon:wingpower",16);
 err=AttributeCategorySwapper.exchange(cow,bat,"modded");
 eq(err!=null && err.contains("Incompatible category"),"reject asymmetry in live game rather than fake an ability");
 eq(err.contains("First entity only: dragon:wingpower"),"asymmetric schema names first-only capability");
 eq(err.contains("second entity only: none"),"asymmetric schema names second-only capability");
 eq(get(cow,"dragon:firepower")==12 && get(bat,"dragon:firepower")==2,"no partial modded exchange");
 put(bat,"dragon:wingpower",5);
 eq(AttributeCategorySwapper.exchange(cow,bat,"modded")==null,"modded full category successful when both compatible");
 eq(get(cow,"dragon:firepower")==2 && get(cow,"dragon:wingpower")==5,"modded category swaps both keys");
 // Complete numerical category must include PERMANENT modifiers, while
 // transient potion/equipment modifiers remain physically with the owner.
 LivingEntity knight=new LivingEntity("Knight",world),guard=new LivingEntity("Guard",world);
 for(LivingEntity e:new LivingEntity[]{knight,guard}) {put(e,"minecraft:armor",3);put(e,"minecraft:armor_toughness",1);}
 var knightArmor=knight.getAttributes().getInstance(register("minecraft:armor"));
 var guardArmor=guard.getAttributes().getInstance(register("minecraft:armor"));
 AttributeModifier knightPermanent=new AttributeModifier(net.minecraft.resources.Identifier.parse("ff:armor_hero"),2,AttributeModifier.Operation.ADD_VALUE);
 AttributeModifier guardPermanent=new AttributeModifier(net.minecraft.resources.Identifier.parse("ff:armor_guard"),4,AttributeModifier.Operation.ADD_VALUE);
 AttributeModifier knightSecond=new AttributeModifier(net.minecraft.resources.Identifier.parse("ff:armor_perk"),.25,AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
 AttributeModifier knightPotion=new AttributeModifier(net.minecraft.resources.Identifier.parse("ff:potion"),1,AttributeModifier.Operation.ADD_VALUE);
 AttributeModifier guardArmorItem=new AttributeModifier(net.minecraft.resources.Identifier.parse("ff:equipment"),3,AttributeModifier.Operation.ADD_VALUE);
 knightArmor.setBaseValue(4);guardArmor.setBaseValue(1);
 knightArmor.addPermanentModifier(knightPermanent);knightArmor.addPermanentModifier(knightSecond);guardArmor.addPermanentModifier(guardPermanent);
 knightArmor.addTransientModifier(knightPotion);guardArmor.addTransientModifier(guardArmorItem);
 eq(Math.abs(knightArmor.getValue()-8.0)<1e-6 && guardArmor.getValue()==8.0,"baseline effective armor counts permanent and transient bonuses");
 err=AttributeCategorySwapper.exchange(knight,guard,"defense");
 eq(err==null,"whole category with permanent modifiers transfers: "+err);
 eq(knightArmor.getPermanentModifiers().contains(guardPermanent)&&!knightArmor.getPermanentModifiers().contains(knightPermanent),"first receives second permanent bonus");
 eq(guardArmor.getPermanentModifiers().contains(knightPermanent)&&!guardArmor.getPermanentModifiers().contains(guardPermanent),"second receives first permanent bonus");
 eq(guardArmor.getPermanentModifiers().contains(knightSecond)&&guardArmor.getPermanentModifiers().size()==2,"second receives complete multi-modifier set");
 eq(Math.abs(knightArmor.getValue()-6)<1e-6 && Math.abs(guardArmor.getValue()-10)<1e-6,"effective values recomputed with received intrinsic and retained transient bonuses");
 eq(knightArmor.getModifiers().contains(knightPotion)&&guardArmor.getModifiers().contains(guardArmorItem),"transient modifiers stay with equipment/effects");
 eq(!knightArmor.getModifiers().contains(guardArmorItem)&&!guardArmor.getModifiers().contains(knightPotion),"transients never leak across category");
 eq(knightArmor.getBaseValue()==1&&guardArmor.getBaseValue()==4,"base values move with permanent modifiers");
 err=AttributeCategorySwapper.exchange(knight,guard,"defense");
 eq(err==null&&knightArmor.getPermanentModifiers().contains(knightPermanent)&&guardArmor.getPermanentModifiers().contains(guardPermanent),"permanent modifiers reverse precisely");
 eq(knightArmor.getPermanentModifiers().contains(knightSecond)&&Math.abs(knightArmor.getValue()-8)<1e-6,"modifier operations reverse with their original owner");
 eq(knightArmor.getBaseValue()==4&&guardArmor.getBaseValue()==1,"base values reverse precisely");
 // Preflight detects a modifier ID that an incoming permanent would collide
 // with on the OTHER mob's currently active transient effect.
 AttributeModifier collisionTransient=new AttributeModifier(knightPermanent.id(),5,AttributeModifier.Operation.ADD_VALUE);
 guardArmor.addTransientModifier(collisionTransient);
 err=AttributeCategorySwapper.exchange(knight,guard,"defense");
 eq(err!=null&&err.contains("Modifier collision"),"reject incoming permanent/transient collision before mutations");
 eq(knightArmor.getPermanentModifiers().contains(knightPermanent)&&guardArmor.getPermanentModifiers().contains(guardPermanent),"collision leaves both permanents intact");
 guardArmor.removeModifier(collisionTransient.id());
 // A failure halfway through the second modifier write must restore both mobs
 // including permanent and transient states.
 guardArmor.failPermanentWrite=true;
 err=AttributeCategorySwapper.exchange(knight,guard,"defense");
 eq(err!=null,"failed modifier write detected");
 eq(knightArmor.getPermanentModifiers().contains(knightPermanent)&&guardArmor.getPermanentModifiers().contains(guardPermanent),"both permanent sets rolled back after injected write failure");
 eq(knightArmor.getModifiers().contains(knightPotion)&&guardArmor.getModifiers().contains(guardArmorItem),"rollback did not strip temporary modifiers");
 // Incoming out-of-range attribute must fail PREFLIGHT before touching either side.
 put(bat,"minecraft:movement_speed",2.0);
 double beforeCow=get(cow,"minecraft:movement_speed");
 err=AttributeCategorySwapper.exchange(cow,bat,"mobility");
 eq(err!=null && err.contains("outside valid range"),"preflight detects clamped incoming values");
 eq(get(cow,"minecraft:movement_speed")==beforeCow && get(bat,"minecraft:movement_speed")==2.0,
    "preflight rejection did not mutate category");
 put(bat,"minecraft:movement_speed",.35);
 // Inject a failure during the second recipient write: the first side must be rolled back.
 double c0=get(cow,"minecraft:gravity"),b0=get(bat,"minecraft:gravity");
 bat.getAttributes().getInstance(register("minecraft:gravity")).failNext=true;
 err=AttributeCategorySwapper.exchange(cow,bat,"mobility");
 eq(err!=null,"injected fault reported");
 eq(get(cow,"minecraft:gravity")==c0 && get(bat,"minecraft:gravity")==b0,"failed exchange rolls back both recipients");
 // Ctrl+S reaches same session entrypoint; clicking a mob can select self as second.
 SwapSessions.select(p,p);SwapSessions.choose(p,"mobility");SwapSessions.select(p,cow);
 eq(get(p,"minecraft:movement_speed")==.13,"self first target receives cow movement");
 SwapSessions.select(p,bat);SwapSessions.choose(p,"mobility");SwapSessions.select(p,p);
 eq(get(p,"minecraft:movement_speed")==.35,"self second target receives bat movement");
 // A dead first selection must cancel instead of swapping stale attributes.
 SwapSessions.select(p,cow);SwapSessions.choose(p,"mobility");cow.kill();SwapSessions.select(p,bat);
 eq(received(p,"no longer alive or loaded"),"dead first target rejected");
 // Dead second entity must be refused as a target.
 SwapSessions.select(p,cow);eq(received(p,"Only living, active entities"),"dead second/first target rejected");
 cow.revive();
 // Reject same target, missing held book; multi-user session separation.
 SwapSessions.select(p,cow);SwapSessions.choose(p,"mobility");SwapSessions.select(p,cow);
 eq(received(p,"must be different"),"same target guarded");SwapSessions.cancel(p);
 p.setMainHand(new Object());SwapSessions.select(p,cow);
 eq(received(p,"Hold the Freaky Friday book"),"book prerequisite");
 ServerPlayer q=new ServerPlayer("Player2",world);q.setMainHand(FreakyFriday.BOOK.get());put(q,"minecraft:max_health",20);put(q,"minecraft:movement_speed",.6);put(q,"minecraft:gravity",.04);
 p.setMainHand(FreakyFriday.BOOK.get());SwapSessions.select(p,cow);SwapSessions.select(q,bat);
 SwapSessions.choose(p,"mobility");SwapSessions.choose(q,"vitality");
 eq(received(p,"Selected ENTIRE Movement & Body") && received(q,"Selected ENTIRE Vitality"),"independent player sessions");
 SwapSessions.cancel(p);SwapSessions.cancel(q);SwapSessions.clearOnLogout(p.getUUID());
 // No stat/ability fake for phase3 signature category.
 eq(!AttributeCategorySwapper.availableCategories(cow).containsKey("signature"),"incomplete signature category hidden");
 System.out.println("PASS BridgeHarness: "+checks+" assertions; real adapter + selection/command logic with synthetic Minecraft API");
}
}
''')
prod=[ROOT/'src/main/java/dev/freakyfriday/core/CategoryExchange.java',ROOT/'src/main/java/dev/freakyfriday/core/PermanentModifier.java',ROOT/'src/main/java/dev/freakyfriday/core/CategoryBundle.java',ROOT/'src/main/java/dev/freakyfriday/core/CategoryDefinitions.java',ROOT/'src/main/java/dev/freakyfriday/core/CategorySchema.java',ROOT/'src/main/java/dev/freakyfriday/core/CategoryOffers.java',ROOT/'src/main/java/dev/freakyfriday/AttributeModifierTransfer.java',ROOT/'src/main/java/dev/freakyfriday/AttributeCategorySwapper.java',ROOT/'src/main/java/dev/freakyfriday/SwapSessions.java',ROOT/'src/main/java/dev/freakyfriday/FreakyFridayBookItem.java',ROOT/'src/main/java/dev/freakyfriday/mind/MindSwapSessions.java']
cmd=['javac','-d',str(OUT)]+list(map(str,STUB.rglob('*.java')))+list(map(str,prod))
subprocess.run(cmd,check=True)
subprocess.run(['java','-ea','-cp',str(OUT),'dev.freakyfriday.BridgeHarness'],check=True)

# Second harness: compile EVERY production Java file with synthetic NeoForge/client APIs.
# This catches missing internal references and most Java source errors, but API signatures
# are synthetic and must NOT be represented as a real NeoForge compile.
FULL={
'dev/freakyfriday/mind/PlayerMobGameTests.java':'''package dev.freakyfriday.mind;
public final class PlayerMobGameTests {
 public static final net.neoforged.neoforge.registries.DeferredRegister.Items FUNCTIONS =
   net.neoforged.neoforge.registries.DeferredRegister.createItems("stub-player-mob-tests");
}
''',
'dev/freakyfriday/mind/PlayerPlayerGameTests.java':'''package dev.freakyfriday.mind;
public final class PlayerPlayerGameTests {
 public static final net.neoforged.neoforge.registries.DeferredRegister.Items FUNCTIONS =
   net.neoforged.neoforge.registries.DeferredRegister.createItems("stub-player-player-tests");
}
''',
# These components exercise real Brigadier/NeoForge command events and
# two-player GameTest APIs, rather than the old synthetic Minecraft stubs.
'dev/freakyfriday/mind/PlayerCommandBodyRouting.java':'''package dev.freakyfriday.mind;
public final class PlayerCommandBodyRouting {}
''',
'dev/freakyfriday/mind/PlayerCommandRouteGameTests.java':'''package dev.freakyfriday.mind;
public final class PlayerCommandRouteGameTests {
 public static final net.neoforged.neoforge.registries.DeferredRegister.Items FUNCTIONS =
   net.neoforged.neoforge.registries.DeferredRegister.createItems("stub-player-commands");
}
''',
'dev/unseenaddon/UnseenAddonCommandFixture.java':'''package dev.unseenaddon;
public final class UnseenAddonCommandFixture {}
''',
# The native AI worker executes actual NeoForge world/goal APIs. Only the
# real NeoForge build/worker integration may compile/test this implementation.
'net/neoforged/neoforge/common/world/chunk/RegisterTicketControllersEvent.java':'''package net.neoforged.neoforge.common.world.chunk;
public final class RegisterTicketControllersEvent {}
''',
'dev/freakyfriday/mind/NativeAiWorkerServer.java':'''package dev.freakyfriday.mind;
public final class NativeAiWorkerServer {
 public static void registerChunkTickets(
  net.neoforged.neoforge.common.world.chunk.RegisterTicketControllersEvent event){}
 public static void setEntityTickingChunk(
  net.minecraft.server.level.ServerLevel level,java.util.UUID owner,
  int chunkX,int chunkZ,boolean forced){}
}
''',
# Real GameTest APIs belong to the actual Minecraft server build and are not
# implemented by this legacy synthetic API harness.
'dev/freakyfriday/mind/DetachedProbeGameTests.java':'''package dev.freakyfriday.mind;
public final class DetachedProbeGameTests {
 public static final net.neoforged.neoforge.registries.DeferredRegister.Items FUNCTIONS =
   net.neoforged.neoforge.registries.DeferredRegister.createItems("stub-test-functions");
}
''',
'net/minecraft/world/entity/EntityType.java':'''package net.minecraft.world.entity;
public final class EntityType<T extends LivingEntity>{public final String name;public EntityType(String name){this.name=name;}}
''',
'net/neoforged/neoforge/event/entity/EntityAttributeModificationEvent.java':'''package net.neoforged.neoforge.event.entity;
import java.util.*;import net.minecraft.core.Holder;import net.minecraft.world.entity.*;import net.minecraft.world.entity.ai.attributes.*;
public class EntityAttributeModificationEvent {
public final Map<EntityType<? extends LivingEntity>,Map<Holder<Attribute>,Double>> types=new LinkedHashMap<>();
public int additions;
public void type(EntityType<? extends LivingEntity> type,Holder<Attribute> attr,double value){types.computeIfAbsent(type,k->new LinkedHashMap<>()).put(attr,value);}
public java.util.List<EntityType<? extends LivingEntity>> getTypes(){return new ArrayList<>(types.keySet());}
public boolean has(EntityType<? extends LivingEntity> type,Holder<Attribute> attribute){return types.get(type).containsKey(attribute);}
public void add(EntityType<? extends LivingEntity> type,Holder<Attribute> attribute){
  if(has(type,attribute))throw new IllegalArgumentException("duplicate attr");
  additions++;types.get(type).put(attribute,attribute.value().name().equals("minecraft:max_absorption")?0.0:0.0);
}
}
''',
'net/neoforged/bus/api/IEventBus.java':'''package net.neoforged.bus.api;
import java.util.function.Consumer;
public interface IEventBus {default <T> void addListener(Consumer<T> cb) {}}''',
'net/neoforged/bus/api/SubscribeEvent.java':'''package net.neoforged.bus.api;
import java.lang.annotation.*;@Retention(RetentionPolicy.RUNTIME)@Target(ElementType.METHOD)
public @interface SubscribeEvent {}''',
'net/neoforged/api/distmarker/Dist.java':'''package net.neoforged.api.distmarker;public enum Dist{CLIENT,DEDICATED_SERVER}''',
'net/neoforged/fml/common/Mod.java':'''package net.neoforged.fml.common;
import java.lang.annotation.*;@Target(ElementType.TYPE) @Retention(RetentionPolicy.RUNTIME)
public @interface Mod{String value();}''',
'net/neoforged/fml/common/EventBusSubscriber.java':'''package net.neoforged.fml.common;
import java.lang.annotation.*;import net.neoforged.api.distmarker.Dist;
@Target(ElementType.TYPE) @Retention(RetentionPolicy.RUNTIME)
public @interface EventBusSubscriber{String modid();Dist value();}''',
'net/neoforged/neoforge/common/NeoForge.java':'''package net.neoforged.neoforge.common;
public class NeoForge {public static final Bus EVENT_BUS=new Bus();public static class Bus{public void register(Class<?> c){}}}''',
'net/neoforged/neoforge/event/BuildCreativeModeTabContentsEvent.java':'''package net.neoforged.neoforge.event;
import net.minecraft.resources.*;import net.neoforged.neoforge.registries.DeferredItem;
public class BuildCreativeModeTabContentsEvent{public ResourceKey getTabKey(){return new ResourceKey(Identifier.withDefaultNamespace("tools_and_utilities"));}
public void accept(DeferredItem<?> book){}}''',
'net/minecraft/resources/ResourceKey.java':'''package net.minecraft.resources;public record ResourceKey(Identifier identifier) {}''',
'net/neoforged/neoforge/registries/DeferredItem.java':'''package net.neoforged.neoforge.registries;
public final class DeferredItem<T>{private final T item;public DeferredItem(T v){item=v;}public T get(){return item;}}''',
'net/neoforged/neoforge/registries/DeferredRegister.java':'''package net.neoforged.neoforge.registries;
import java.util.function.Function;import net.neoforged.bus.api.IEventBus;import net.minecraft.world.item.Item;
public class DeferredRegister{public static Items createItems(String modid){return new Items();}
public static class Items{public <T extends Item> DeferredItem<T> registerItem(String name,Function<Item.Properties,T> factory){return new DeferredItem<>(factory.apply(new Item.Properties()));}
public void register(IEventBus bus){}}}''',
'net/minecraft/commands/Commands.java':'''package net.minecraft.commands;
import net.minecraft.server.level.ServerPlayer;
public class Commands{public static final Object LEVEL_GAMEMASTERS=new Object();
public static java.util.function.Predicate<Source> hasPermission(Object level){return source -> true;}
public static Builder literal(String name){return new Builder();}
public static Builder argument(String name,Object type){return new Builder();}
public static class Context{public Source getSource(){return new Source();}}
public static class Source {public ServerPlayer getPlayerOrException(){return null;}}
@FunctionalInterface public interface Action{int run(Context ctx) throws Exception;}
public static class Builder{public Builder then(Builder child){return this;}public Builder requires(java.util.function.Predicate<Source> permission){return this;}public Builder executes(Action action){return this;}}}''',
'com/mojang/brigadier/arguments/StringArgumentType.java':'''package com.mojang.brigadier.arguments;
public final class StringArgumentType {public static Object word(){return new Object();}
public static String getString(Object ctx,String name){return "mobility";}}''',
'net/neoforged/neoforge/event/server/ServerStoppedEvent.java':'''package net.neoforged.neoforge.event.server;
public final class ServerStoppedEvent {}
''',
'net/neoforged/neoforge/event/RegisterCommandsEvent.java':'''package net.neoforged.neoforge.event;
import net.minecraft.commands.Commands;
public final class RegisterCommandsEvent{public Dispatcher getDispatcher(){return new Dispatcher();}
public static class Dispatcher{public void register(Commands.Builder b){}}}''',
'net/neoforged/neoforge/event/entity/player/PlayerEvent.java':'''package net.neoforged.neoforge.event.entity.player;
import net.minecraft.world.entity.player.Player;
public class PlayerEvent{public static class PlayerLoggedOutEvent{public Player getEntity(){return null;}}}''',
'com/mojang/blaze3d/platform/InputConstants.java':'''package com.mojang.blaze3d.platform;
public final class InputConstants{public enum Type{KEYSYM}}''',
'org/lwjgl/glfw/GLFW.java':'''package org.lwjgl.glfw;public class GLFW{public static final int GLFW_KEY_S=83;}''',
'net/neoforged/neoforge/client/settings/KeyConflictContext.java':'''package net.neoforged.neoforge.client.settings;public enum KeyConflictContext{IN_GAME}''',
'net/neoforged/neoforge/client/settings/KeyModifier.java':'''package net.neoforged.neoforge.client.settings;public enum KeyModifier{CONTROL}''',
'net/minecraft/client/KeyMapping.java':'''package net.minecraft.client;
import com.mojang.blaze3d.platform.InputConstants;import net.neoforged.neoforge.client.settings.*;
public class KeyMapping {public enum Category {MISC}private boolean clicked;
public final KeyModifier modifier;public final int key;
public KeyMapping(String name,KeyConflictContext ctx,KeyModifier modifier,InputConstants.Type type,int key,Category category){this.modifier=modifier;this.key=key;}
public void trigger(){clicked=true;}public boolean consumeClick(){boolean v=clicked;clicked=false;return v;}
public net.minecraft.network.chat.Component getTranslatedKeyMessage(){return net.minecraft.network.chat.Component.literal(display);}
public String display="Ctrl+S";}''',
'net/neoforged/neoforge/client/event/RegisterKeyMappingsEvent.java':'''package net.neoforged.neoforge.client.event;
import net.minecraft.client.KeyMapping;
public class RegisterKeyMappingsEvent{public KeyMapping registered;public void register(KeyMapping b){registered=b;}}''',
'net/neoforged/neoforge/client/event/ClientTickEvent.java':'''package net.neoforged.neoforge.client.event;public class ClientTickEvent{public static class Post{}}''',
'net/neoforged/neoforge/event/entity/player/PlayerInteractEvent.java':'''package net.neoforged.neoforge.event.entity.player;
import net.minecraft.world.InteractionResult;import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
public class PlayerInteractEvent {
 public static class EntityInteract {
  private final Player player;private final Entity target;private boolean canceled;
  private InteractionResult result;
  public EntityInteract(Player player,Entity target){this.player=player;this.target=target;}
  public Player getEntity(){return player;}
  public Entity getTarget(){return target;}
  public void setCanceled(boolean value){canceled=value;}
  public boolean isCanceled(){return canceled;}
  public void setCancellationResult(InteractionResult value){result=value;}
  public InteractionResult getCancellationResult(){return result;}
 }
}
''',

'net/minecraft/client/Minecraft.java':'''package net.minecraft.client;
import net.minecraft.world.item.ItemStack;import net.minecraft.network.chat.Component;
import java.util.*;
public class Minecraft {private static final Minecraft SELF=new Minecraft();public LocalPlayer player;public Object screen;
public GUI gui=new GUI();
public static Minecraft getInstance(){return SELF;}
public static class GUI{public HUD hud=new HUD();public Object screen(){return Minecraft.getInstance().screen;}}
public static class HUD{public void setOverlayMessage(Component msg,boolean actionbar){if(Minecraft.getInstance().player!=null)Minecraft.getInstance().player.hints.add(msg.getString());}}
public static class LocalPlayer{public Connection connection=new Connection();public ItemStack main=new ItemStack(new Object()),off=new ItemStack(new Object());
public final List<String> hints=new ArrayList<>();public ItemStack getMainHandItem(){return main;}public ItemStack getOffhandItem(){return off;}
public void displayClientMessage(Component msg,boolean actionbar){hints.add(msg.getString());}}
public static class Connection{public final List<String> commands=new ArrayList<>();public void sendCommand(String command){commands.add(command);}}
}''',
}
for n,txt in FULL.items():
    file=STUB/n;file.parent.mkdir(parents=True,exist_ok=True);file.write_text(txt+'\n')
(STUB/'dev/freakyfriday/PortabilityHarness.java').write_text('''package dev.freakyfriday;
import net.minecraft.world.entity.*;import net.minecraft.world.entity.ai.attributes.*;
import net.neoforged.neoforge.event.entity.EntityAttributeModificationEvent;
import net.minecraft.core.registries.BuiltInRegistries;
import java.util.*;
public class PortabilityHarness {
static int checks;
static void ok(boolean b,String msg){checks++;if(!b)throw new AssertionError(msg);}
public static void main(String[] args){
 var registry=BuiltInRegistries.ATTRIBUTE;
 var health=registry.register("minecraft:max_health");var armor=registry.register("minecraft:armor");
 var absorption=registry.register("minecraft:max_absorption");var oxygen=registry.register("minecraft:oxygen_bonus");
 var knockback=registry.register("minecraft:knockback_resistance");var toughness=registry.register("minecraft:armor_toughness");
 var explosion=registry.register("minecraft:explosion_knockback_resistance");
 var flight=registry.register("minecraft:flying_speed");var modded=registry.register("othermod:dragon_rage");
 var cow=new EntityType<LivingEntity>("cow"); var skeleton=new EntityType<LivingEntity>("skeleton");
 var event=new EntityAttributeModificationEvent();
 event.type(cow,health,10);event.type(cow,armor,7);
 event.type(skeleton,health,20);event.type(skeleton,flight,1);
 PortableAttributes.addToEntityTypes(event);
 for(var type:new EntityType[]{cow,skeleton}){
   for(var attribute:new net.minecraft.core.Holder[]{armor,absorption,oxygen,knockback,toughness,explosion})
     ok(event.has(type,attribute),type.name+" missing shared numerical attribute");
   ok(!event.has(type,modded),"unknown modded property should not be speculatively installed");
 }
 ok(event.has(skeleton,flight),"existing native flight stat unchanged");
 ok(!event.has(cow,flight),"new flight property must not pretend to grant flight");
 ok(event.types.get(cow).get(armor)==7.0,"native creature armor was preserved");
 // Simulate TWO spawned creatures built from different native type suppliers.
 // These initially differed by five defensive attributes, and that difference
 // is genuinely filled by the registered event BEFORE the instances spawn.
 var world = new net.minecraft.server.level.ServerLevel("minecraft:overworld");
 var a=new LivingEntity("Cow",world);var b=new LivingEntity("Skeleton",world);
 for(var x:event.types.get(cow).entrySet())a.getAttributes().put(x.getKey(),x.getValue());
 for(var x:event.types.get(skeleton).entrySet())b.getAttributes().put(x.getKey(),x.getValue());
 a.setHealth(8);b.setHealth(17);a.setAbsorptionAmount(2);b.setAbsorptionAmount(0);
 b.getAttributes().getInstance(explosion).setBaseValue(.55);
 ok(AttributeCategorySwapper.availableCategories(a).get("defense")==4,"defense on augmented cow is complete");
 ok(AttributeCategorySwapper.availableCategories(b).get("defense")==4,"defense on augmented skeleton is complete");
 String error=AttributeCategorySwapper.exchange(a,b,"defense");
 ok(error==null,"cross-species whole Defense category succeeds: "+error);
 ok(a.getAttributes().getInstance(armor).getBaseValue()==0,"cow received skeleton armor");
 ok(a.getAttributes().getInstance(explosion).getBaseValue()==.55,"cow received skeleton explosion resistance");
 ok(b.getAttributes().getInstance(armor).getBaseValue()==7,"skeleton received cow's native armor");
 ok(b.getAttributes().getInstance(explosion).getBaseValue()==0,"skeleton received cow explosion resistance");
 ok(AttributeCategorySwapper.exchange(a,b,"defense")==null,"reverse whole Defense succeeds");
 ok(a.getAttributes().getInstance(armor).getBaseValue()==7,"reverse restored original armor");
 ok(a.getAttributes().getInstance(explosion).getBaseValue()==0,"reverse restored original knockback");
 error=AttributeCategorySwapper.exchange(a,b,"vitality");
 ok(error==null,"cross-species whole Vitality succeeds: "+error);
 ok(a.getAttributes().getInstance(health).getBaseValue()==20 && a.getHealth()==17,"cow gains skeleton vitality");
 ok(b.getAttributes().getInstance(health).getBaseValue()==10 && b.getHealth()==8,"skeleton gains cow vitality");
 ok(a.getAbsorptionAmount()==0 && b.getAbsorptionAmount()==2,"current absorption swapped with whole category");
 ok(AttributeCategorySwapper.exchange(a,b,"vitality")==null,"reverse whole Vitality succeeds");
 error=AttributeCategorySwapper.exchange(a,b,"mobility");
 ok(error!=null && error.contains("Incompatible category"),"unsupported flight remains blocked, not reported as success");
 int additions=event.additions;
 PortableAttributes.addToEntityTypes(event);
 ok(additions==event.additions,"event hook must be idempotent");
 System.out.println("PASS PortabilityHarness: "+checks+" checks; entity-type attribute augmentation + native preservation + no speculative modded/flight additions");
}
}''')
(STUB/'dev/freakyfriday/FreakyFriday.java').unlink()
# Full source compile from project, no fake project class.
# The real NeoForge CI build compiles network codecs and native Screen classes.
# They must not be passed to the legacy headless harness whose fake APIs do not
# implement actual packet serialization, GUI rendering or client registration.
real_api_only = {'NativeBrainSwap.java', 'GoalMindRuntime.java', 'MinecraftBodyPort.java', 'MinecraftHostProbe.java', 'LiveBodyProbe.java', 'DetachedMindHostProbe.java', 'ProbeBodySnapshot.java', 'ProbeTelemetry.java', 'DetachedHostMirrorProbe.java', 'DetachedProbeGameTests.java', 'NativeAiSurfaceAudit.java', 'CategoryPickerPayload.java','CategoryPickerNetworking.java',
                 'CategoryPickerScreen.java','ClientCategoryPickerNetworking.java', 'FFDiagnosticAutoTest.java', 'FFServerSmoke.java', 'NativeAiWorkerServer.java', 'MinecraftMindSenseCapture.java', 'PlayerCommandBodyRouting.java', 'PlayerCommandRouteGameTests.java', 'UnseenAddonCommandFixture.java', 'PlayerMobGameTests.java', 'PlayerPlayerGameTests.java', 'PlayerPlayerExchange.java', 'PlayerMobPossession.java', 'PossessionNetwork.java', 'PossessionInputPayload.java', 'PossessionViewPayload.java', 'PossessionStatePayload.java', 'PossessionAttackPayload.java', 'PossessionMotion.java', 'PossessionCombat.java', 'ClientPossession.java', 'ClientOccupiedBodyUI.java', 'ClientSelectionHud.java', 'SelectionHudPayload.java', 'SelectionHudSync.java'}
all_prod=[p for p in (ROOT/'src/main/java').rglob('*.java') if p.name not in real_api_only] + list((ROOT/'src/platform/minecraft-26.2/java').rglob('*.java'))
full_cmd=['javac','-d',str(OUT)]+list(map(str,STUB.rglob('*.java')))+list(map(str,all_prod))
subprocess.run(full_cmd,check=True)
# Run the same simulation against the full mod class, not the surrogate book registrar.
subprocess.run(['java','-ea','-cp',str(OUT),'dev.freakyfriday.BridgeHarness'],check=True)
subprocess.run(['java','-ea','-cp',str(OUT),'dev.freakyfriday.PortabilityHarness'],check=True)
# Validate the EVENT path used before a villager's trading or an animal's feeding
# interaction could consume the right-click. The original Item handler is only a
# fallback when a foreign integration bypasses the event pipeline.
(STUB/'dev/freakyfriday/BookInteractionHarness.java').write_text('''package dev.freakyfriday;
import net.minecraft.server.level.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.InteractionResult;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import dev.freakyfriday.mind.MindSwapSessions;

/**
 * Synthetic source wiring checks only. All real movement, AI and book pairing
 * are validated by actual NeoForge GameTests, NOT this fake Minecraft world.
 */
public final class BookInteractionHarness {
 static int checks;
 static void ok(boolean x,String reason){checks++;if(!x)throw new AssertionError(reason);}
 static boolean received(ServerPlayer p,String phrase){
  return p.messages.stream().anyMatch(s->s.contains(phrase));
 }
 public static void main(String[] args) {
  ServerLevel world=new ServerLevel("minecraft:overworld");
  ServerPlayer p=new ServerPlayer("Player",world);
  p.setMainHand(FreakyFriday.BOOK.get());
  Mob chicken=new Mob("Chicken",world);
  Mob zombie=new Mob("Zombie",world);

  MindSwapSessions.select(p,p);
  ok(received(p,"First body: Player"),
    "Ctrl+S self must become a selectable gameplay body, not be rejected");
  MindSwapSessions.select(p,chicken);
  ok(received(p,"Cannot control that body: synthetic harness"),
    "Book pair reaches actual possession backend rather than locking fake probe");
  MindSwapSessions.cancel(p);

  var one=new PlayerInteractEvent.EntityInteract(p,chicken);
  BookInteractions.onInteract(one);
  ok(one.isCanceled() && one.getCancellationResult()==InteractionResult.SUCCESS,
    "Book consumes actual mob interaction instead of feeding/trading");
  ok(received(p,"First body: Chicken"),"Mob selected in gameplay mode");
  MindSwapSessions.select(p,p);
  ok(received(p,"Cannot control that body: synthetic harness"),
    "Chicken then self enters physical player-mob binding code");
  MindSwapSessions.cancel(p);

  MindSwapSessions.select(p,chicken);
  MindSwapSessions.select(p,zombie);
  ok(received(p,"Native mind exchange REJECTED"),
    "Mob/Mob invokes true AI exchange entrypoint and never reports fake success: "+p.messages);
  MindSwapSessions.cancel(p);

  p.setMainHand(new Object());
  var noBook=new PlayerInteractEvent.EntityInteract(p,chicken);
  BookInteractions.onInteract(noBook);
  ok(!noBook.isCanceled(),"without book, vanilla interactions still run");
  p.setOffhand(FreakyFriday.BOOK.get());
  world.tick();
  var offhand=new PlayerInteractEvent.EntityInteract(p,chicken);
  BookInteractions.onInteract(offhand);
  ok(offhand.isCanceled(),"offhand book selects gameplay body");
  ok(received(p,"First body: Chicken"),"offhand book selection works");

  long count=p.messages.stream().filter(x->x.contains("First body: Chicken")).count();
  BookInteractions.onInteract(new PlayerInteractEvent.EntityInteract(p,chicken));
  new FreakyFridayBookItem(new net.minecraft.world.item.Item.Properties())
   .interactLivingEntity(new net.minecraft.world.item.ItemStack(FreakyFriday.BOOK.get()),
     p,chicken,net.minecraft.world.InteractionHand.OFF_HAND);
  ok(p.messages.stream().filter(x->x.contains("First body: Chicken")).count()==count,
    "duplicate physical book click in same tick is deduplicated");
  MindSwapSessions.cancel(p);
  world.tick();
  BookInteractions.onInteract(new PlayerInteractEvent.EntityInteract(p,chicken));
  ok(p.messages.stream().filter(x->x.contains("First body: Chicken")).count()==count+1,
    "cancel and reselect refreshes the gameplay book stamp");
  MindSwapSessions.cancel(p);
  // Shared Pending stores game ticks in BOTH gameplay and diagnostic paths.
  MindSwapSessions.selectDiagnosticForTest(p,chicken);
  MindSwapSessions.selectDiagnosticForTest(p,zombie);
  ok(MindSwapSessions.diagnosticPairMatches(p.getUUID(),chicken.getUUID(),zombie.getUUID()),
    "diagnostic first-body timestamp invalidated fresh two-mob pairing");
  MindSwapSessions.cancel(p);
  System.out.println("PASS BookInteractionHarness: "+checks
    +" synthetic selection assertions; real movement tested by NeoForge");
 }
}
''' )
subprocess.run(['javac','-cp',str(OUT),'-d',str(OUT),str(STUB/'dev/freakyfriday/BookInteractionHarness.java')],check=True)
subprocess.run(['java','-ea','-cp',str(OUT),'dev.freakyfriday.BookInteractionHarness'],check=True)

(STUB/'dev/freakyfriday/ClientKeyHarness.java').write_text('''package dev.freakyfriday;
import net.minecraft.client.*;import net.neoforged.neoforge.client.event.*;
import net.neoforged.neoforge.client.settings.KeyModifier;
public class ClientKeyHarness{
public static void main(String[] args) throws Exception{
 Minecraft mc=Minecraft.getInstance();mc.player=new Minecraft.LocalPlayer();
 var registration=new RegisterKeyMappingsEvent();ClientBindings.registerKeys(registration);
 if(registration.registered==null||registration.registered.modifier!=KeyModifier.CONTROL||registration.registered.key!=83)
     throw new AssertionError("Ctrl+S default not registered");
 mc.player.main=new net.minecraft.world.item.ItemStack(FreakyFriday.BOOK.get());
 ClientBindings.tick(new ClientTickEvent.Post());
 if(mc.player.hints.isEmpty()||!mc.player.hints.get(0).contains("Ctrl+S"))throw new AssertionError("missing actionbar hint");
 for(int i=0;i<400;i++)ClientBindings.tick(new ClientTickEvent.Post());
 if(mc.player.hints.size()!=1)throw new AssertionError("book hint should not repeat while continuously held");
 // The hint must report a remapped key instead of hardcoding Ctrl+S.
 registration.registered.display="Alt+R";
 for(int i=0;i<201;i++)ClientBindings.tick(new ClientTickEvent.Post());
 if(!mc.player.hints.get(mc.player.hints.size()-1).contains("Alt+R"))throw new AssertionError("remapped hint not updated");
 if(mc.player.hints.size()!=2)throw new AssertionError("key remap should trigger exactly one new hint");
 registration.registered.trigger();ClientBindings.tick(new ClientTickEvent.Post());
 if(!mc.player.connection.commands.contains("freakyfriday self")) throw new AssertionError("Ctrl+S did not select self");
 mc.player.connection.commands.clear();mc.player.main=new net.minecraft.world.item.ItemStack(new Object());
 ClientBindings.tick(new ClientTickEvent.Post());
 mc.player.main=new net.minecraft.world.item.ItemStack(FreakyFriday.BOOK.get());
 ClientBindings.tick(new ClientTickEvent.Post());
 if(mc.player.hints.size()!=3)throw new AssertionError("re-equipping the book should show the hint again");
 mc.player.main=new net.minecraft.world.item.ItemStack(new Object());
 registration.registered.trigger();ClientBindings.tick(new ClientTickEvent.Post());
 if(!mc.player.connection.commands.isEmpty())throw new AssertionError("shortcut active without book");
 mc.player=null; ClientBindings.tick(new ClientTickEvent.Post());
 mc.player=new Minecraft.LocalPlayer();mc.player.main=new net.minecraft.world.item.ItemStack(FreakyFriday.BOOK.get());
 ClientBindings.tick(new ClientTickEvent.Post());
 if(mc.player.hints.isEmpty())throw new AssertionError("new client session hint missing");
 System.out.println("PASS ClientKeyHarness: mapping, remappable hint, self command, no-book guard, reconnect");
}
}''')
subprocess.run(['javac','-cp',str(OUT),'-d',str(OUT),str(STUB/'dev/freakyfriday/ClientKeyHarness.java')],check=True)
subprocess.run(['java','-ea','-cp',str(OUT),'dev.freakyfriday.ClientKeyHarness'],check=True)
print('PASS full-source synthetic API compilation ('+str(len(all_prod))+' production Java files); NOT a real NeoForge build')
