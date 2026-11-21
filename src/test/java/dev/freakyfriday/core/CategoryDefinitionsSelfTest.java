package dev.freakyfriday.core;

public final class CategoryDefinitionsSelfTest {
    private static void eq(String actual,String expected){ if (!actual.equals(expected)) throw new AssertionError(actual+" != "+expected); }
    public static void main(String[] args) {
        eq(CategoryDefinitions.categoryFor("minecraft:movement_speed"),"mobility");
        eq(CategoryDefinitions.categoryFor("minecraft:attack_damage"),"combat");
        eq(CategoryDefinitions.categoryFor("minecraft:max_health"),"vitality");
        eq(CategoryDefinitions.categoryFor("minecraft:scale"),"mobility");
        eq(CategoryDefinitions.categoryFor("minecraft:some_future_attribute"),"modded");
        eq(CategoryDefinitions.categoryFor("othermod:dragon_strength"),"modded");
        eq(CategoryDefinitions.categoryFor("othermod:flight_speed"),"mobility");
        eq(CategoryDefinitions.categoryFor("othermod:attack_damage"),"combat");
        eq(CategoryDefinitions.categoryFor("othermod:attack_speed"),"combat");
        eq(CategoryDefinitions.categoryFor("othermod:armor_movement_speed"),"defense");
        eq(CategoryDefinitions.categoryFor("othermod:dragon_armor"),"defense");
        eq(CategoryDefinitions.categoryFor("othermod:extra_health"),"vitality");
        eq(CategoryDefinitions.categoryFor("othermod:mob_weight"),"mobility");
        eq(CategoryDefinitions.categoryFor("othermod:speed_speed"),"mobility");
        eq(CategoryDefinitions.categoryFor("othermod:custom_mana"),"modded");
        if (CategoryDefinitions.GROUPS.size() != 6) throw new AssertionError("Expected five broad numerical categories plus future abilities");
        CategoryDefinitions.classify("othermod:dragon_strength","combat");
        eq(CategoryDefinitions.categoryFor("othermod:dragon_strength"),"combat");
        try { CategoryDefinitions.classify("dragon_strength","combat"); throw new AssertionError("invalid id accepted"); }
        catch (IllegalArgumentException expected) {}
        try { CategoryDefinitions.classify("mod:flight","signature"); throw new AssertionError("numeric signature accepted"); }
        catch (IllegalArgumentException expected) {}
        System.out.println("PASS CategoryDefinitionsSelfTest: classification, auto modded discovery, explicit overrides, invalid category guard");
    }
}
