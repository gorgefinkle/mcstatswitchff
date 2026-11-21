#!/usr/bin/env python3
"""Source checkpoint audit. Fails on any detectable local test/structure error."""
import json, re, subprocess, sys, pathlib, tomllib
root=pathlib.Path(__file__).resolve().parent.parent
for cmd in (['sh',str(root/'scripts/test-core.sh')],[sys.executable,str(root/'scripts/test-minecraft-stubs.py')]):
    print('+',' '.join(cmd),flush=True)
    subprocess.run(cmd,cwd=root,check=True)
resources=root/'src/main/resources'
for file in sorted(resources.rglob('*.json')):
    json.loads(file.read_text())
    print('JSON PASS',file.relative_to(root))
mods=tomllib.loads((resources/'META-INF/neoforge.mods.toml').read_text())
assert mods['mods'][0]['modId']=='freakyfriday'
assert mods['mods'][0]['version']=='${ff_mod_version}'
assert "version = '0.15.2-alpha'" in (root/'build.gradle').read_text()
assert (root/'src/platform/minecraft-26.2/java/dev/freakyfriday/compat/PlatformHooks.java').exists()
assert (root/'src/platform/minecraft-26.3/java/dev/freakyfriday/compat/PlatformHooks.java').exists()
assert 'PlatformHooks::addBookToCreativeTab' in (root/'src/main/java/dev/freakyfriday/FreakyFriday.java').read_text()
assert not (root/'src/main/java/dev/freakyfriday/AttributeSwapper.java').exists()
assert (root/'src/main/java/dev/freakyfriday/api/AbilityCategoryAdapter.java').exists()
assert 'KeyModifier.CONTROL' in (root/'src/platform/minecraft-26.2/java/dev/freakyfriday/compat/ClientKeyFactory.java').read_text()
assert 'ClientKeyFactory.create()' in (root/'src/main/java/dev/freakyfriday/ClientBindings.java').read_text()
assert 'CategoryExchange.exchange(first,second,port)' in (root/'src/main/java/dev/freakyfriday/AttributeCategorySwapper.java').read_text()
assert not (root/'src/main/java/dev/freakyfriday/core/ExchangeEngine.java').exists()
assert (root/'src/main/java/dev/freakyfriday/mind/DynamicObjectGraph.java').exists()
assert (root/'src/main/java/dev/freakyfriday/mind/BodyActionGate.java').exists()
assert (root/'src/main/java/dev/freakyfriday/mind/MinecraftBodyPort.java').exists()
assert (root/'src/main/java/dev/freakyfriday/mind/MindHostEngine.java').exists()
assert (root/'src/main/java/dev/freakyfriday/mind/BodyCapabilityRegistry.java').exists()
assert (root/'src/test/java/dev/freakyfriday/mind/BodyCapabilityRegistrySelfTest.java').exists()
assert (root/'src/main/java/dev/freakyfriday/mind/NativeAiSurfaceAudit.java').exists()
assert (root/'src/main/java/dev/freakyfriday/mind/NativeAiEffectSurvey.java').exists()
assert (root/'src/main/java/dev/freakyfriday/mind/MinecraftHostProbe.java').exists()
assert (root/'src/main/java/dev/freakyfriday/mind/LiveBodyProbe.java').exists()
assert (root/'src/main/java/dev/freakyfriday/mind/DetachedMindHostProbe.java').exists()
assert (root/'src/main/java/dev/freakyfriday/mind/DetachedHostMirrorProbe.java').exists()
assert (root/'src/main/java/dev/freakyfriday/mind/ProbeTelemetry.java').exists()
probe_source = (root/'src/main/java/dev/freakyfriday/mind/DetachedHostMirrorProbe.java').read_text()
assert 'mirror_tick' in probe_source and 'mirror_finished' in probe_source and 'registration_violation' in probe_source
assert 'EntityJoinLevelEvent' in probe_source and 'event.setCanceled(true)' in probe_source
inspect_source = (root/'src/main/java/dev/freakyfriday/mind/NativeAiSurfaceAudit.java').read_text()
assert 'ai_inspect_summary' in inspect_source and 'ai_goal' in inspect_source and 'ai_effect_candidate' in inspect_source

assert 'ProbeTelemetry.info' in (root/'src/main/java/dev/freakyfriday/mind/DetachedMindHostProbe.java').read_text()
assert 'ProbeTelemetry.info' in (root/'src/main/java/dev/freakyfriday/mind/DetachedHostMirrorProbe.java').read_text()
assert 'ProbeTelemetry.info' in (root/'src/main/java/dev/freakyfriday/mind/LiveBodyProbe.java').read_text()
diagnostic_commands = (root/'src/main/java/dev/freakyfriday/FFCommands.java').read_text()
assert diagnostic_commands.count('requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))') == 5, "Experimental host/debug commands must require game-master access"

assert 'NeoForge.EVENT_BUS.register(dev.freakyfriday.mind.DetachedHostMirrorProbe.class)' in (root/'src/main/java/dev/freakyfriday/FreakyFriday.java').read_text()
assert 'NeoForge.EVENT_BUS.register(dev.freakyfriday.mind.LiveBodyProbe.class)' in (root/'src/main/java/dev/freakyfriday/FreakyFriday.java').read_text()
assert (root/'src/test/java/dev/freakyfriday/mind/NativeAiEffectSurveySelfTest.java').exists()
assert (root/'src/test/java/dev/freakyfriday/mind/MindHostEngineSelfTest.java').exists()
assert (root/'src/test/java/dev/freakyfriday/mind/BodyActionGateSelfTest.java').exists()
dynamic_runtime=(root/'src/main/java/dev/freakyfriday/mind/GoalMindRuntime.java').read_text()
assert 'DynamicObjectGraph.capture(roots, Goal[].class' in dynamic_runtime
assert 'body.targetSelector' in dynamic_runtime
assert 'nativeProfile(' not in dynamic_runtime
assert 'enum Profile' not in dynamic_runtime
assert 'net.minecraft.world.entity.animal.pig.Pig' not in dynamic_runtime
assert 'net.minecraft.world.entity.animal.cow.Cow' not in dynamic_runtime
assert (root/'src/main/java/dev/freakyfriday/core/CategorySchema.java').exists()
assert (root/'src/main/java/dev/freakyfriday/PortableAttributes.java').exists()
assert (root/'src/main/java/dev/freakyfriday/BookInteractions.java').exists()
assert 'NeoForge.EVENT_BUS.register(BookInteractions.class)' in (root/'src/main/java/dev/freakyfriday/FreakyFriday.java').read_text()
assert 'modBus.addListener(PortableAttributes::addToEntityTypes)' in (root/'src/main/java/dev/freakyfriday/FreakyFriday.java').read_text()
assert 'EntityAttributeModificationEvent' in (root/'src/main/java/dev/freakyfriday/PortableAttributes.java').read_text()
assert 'sanitizeValue' in (root/'src/main/java/dev/freakyfriday/AttributeCategorySwapper.java').read_text()
assert (root/'src/main/java/dev/freakyfriday/core/PermanentModifier.java').exists()
assert (root/'src/main/java/dev/freakyfriday/AttributeModifierTransfer.java').exists()
assert 'AttributeModifierTransfer.replace(' in (root/'src/main/java/dev/freakyfriday/AttributeCategorySwapper.java').read_text()
assert 'getPermanentModifiers()' in (root/'src/main/java/dev/freakyfriday/AttributeModifierTransfer.java').read_text()
assert 'addPermanentModifier' in (root/'src/main/java/dev/freakyfriday/AttributeModifierTransfer.java').read_text()
assert 'removeModifiers()' not in '\n'.join(line for line in (root/'src/main/java/dev/freakyfriday/AttributeModifierTransfer.java').read_text().splitlines() if not line.strip().startswith('*') and not line.strip().startswith('//'))
assert 'Category replacement was not exact' in (root/'src/main/java/dev/freakyfriday/core/CategoryExchange.java').read_text()
subprocess.run([sys.executable, str(root/'scripts/test-version-profiles.py')], cwd=root, check=True)
print('PASS: resources, metadata, source wiring and versions')
print('NOT TESTED HERE: real NeoForge build/integrated/dedicated server runtime. Run scripts/build_doctor.py separately.')
