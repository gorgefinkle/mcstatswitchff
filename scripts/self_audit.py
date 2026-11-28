#!/usr/bin/env python3
"""Source checkpoint audit. Fails on any detectable local test/structure error."""
import json, re, subprocess, sys, pathlib, tomllib
root=pathlib.Path(__file__).resolve().parent.parent
for cmd in (['sh',str(root/'scripts/test-core.sh')],[sys.executable,str(root/'scripts/test-minecraft-stubs.py')],[sys.executable,str(root/'scripts/test-goal-selector-state.py')]):
    print('+',' '.join(cmd),flush=True)
    subprocess.run(cmd,cwd=root,check=True)
resources=root/'src/main/resources'
for file in sorted(resources.rglob('*.json')):
    json.loads(file.read_text())
    print('JSON PASS',file.relative_to(root))
mods=tomllib.loads((resources/'META-INF/neoforge.mods.toml').read_text())
assert mods['mods'][0]['modId']=='freakyfriday'
assert mods['mods'][0]['version']=='${ff_mod_version}'
assert mods['mixins'][0]['config']=='freakyfriday.mixins.json'
assert (resources/'freakyfriday.mixins.json').exists()
assert (root/'src/main/java/dev/freakyfriday/mixin/MobServerAiStepMixin.java').exists()
assert (root/'src/main/java/dev/freakyfriday/mixin/ServerLevelMobTickDispatchMixin.java').exists()
assert (root/'src/main/java/dev/freakyfriday/mixin/MobTickBodyBridgeMixin.java').exists()
assert (root/'src/main/java/dev/freakyfriday/mind/LivingEntityTickInvoker.java').exists()
assert (root/'src/main/java/dev/freakyfriday/mixin/LivingEntityAiStepBodyBridgeMixin.java').exists()
assert (root/'src/main/java/dev/freakyfriday/mixin/MobBodyServicesMixin.java').exists()
assert (root/'src/main/java/dev/freakyfriday/mixin/EntityBodyContextMixin.java').exists()
assert (root/'src/main/java/dev/freakyfriday/mixin/LivingEntityBodyContextMixin.java').exists()
mixin_config=json.loads((resources/'freakyfriday.mixins.json').read_text())
for mixin in ('ServerLevelMobTickDispatchMixin','MobTickBodyBridgeMixin',
              'LivingEntityAiStepBodyBridgeMixin','MobBodyServicesMixin','MobServerAiStepMixin',
              'EntityBodyContextMixin','LivingEntityBodyContextMixin'):
    assert mixin in mixin_config['mixins'], f'mixin not registered: {mixin}'
assert (root/'src/main/java/dev/freakyfriday/mind/MindHostRegistry.java').exists()
assert 'debug-exchange' in (root/'src/main/java/dev/freakyfriday/FFCommands.java').read_text()
assert 'debug-tick' in (root/'src/main/java/dev/freakyfriday/FFCommands.java').read_text()
assert 'debugExchangeControllers' in (root/'src/main/java/dev/freakyfriday/mind/MindHostRegistry.java').read_text()
assert 'debugTickBody' in (root/'src/main/java/dev/freakyfriday/mind/MindHostRegistry.java').read_text()
assert 'debug-prime-creeper-loaded' in (root/'src/main/java/dev/freakyfriday/FFCommands.java').read_text()
assert 'debug-exchange-zombie-creeper-loaded' in (root/'src/main/java/dev/freakyfriday/FFCommands.java').read_text()
assert 'debug-creeper-progress-loaded' in (root/'src/main/java/dev/freakyfriday/FFCommands.java').read_text()
assert 'getAllLevels()' in (root/'src/main/java/dev/freakyfriday/FFCommands.java').read_text()
assert '--runtime-smoke-test' in (root/'scripts/verify-build-artifact.py').read_text()
assert (root/'src/main/java/dev/freakyfriday/mind/MindBodyBindings.java').exists()
assert (root/'src/test/java/dev/freakyfriday/mind/MindBodyBindingsSelfTest.java').exists()
assert (root/'src/main/java/dev/freakyfriday/mind/MindBodyContext.java').exists()
assert 'npcBodyForPlayer' in (root/'src/main/java/dev/freakyfriday/mind/PlayerPossessionState.java').read_text()
assert '@WrapMethod(method = "handleAttack")' in (root/'src/main/java/dev/freakyfriday/mixin/ServerPlayerPossessionPacketMixin.java').read_text()
assert 'Commands.literal("end")' in (root/'src/main/java/dev/freakyfriday/FFCommands.java').read_text()
assert 'PlayerPossessionState.end(player)' in (root/'src/main/java/dev/freakyfriday/FFCommands.java').read_text()
assert (root/'src/main/java/dev/freakyfriday/mixin/LivingEntityPhysicalAiStepMixin.java').exists()
assert 'LivingEntityPhysicalAiStepMixin' in mixin_config['mixins']
assert 'physicalBodyAiStep' in (root/'src/main/java/dev/freakyfriday/mind/MindBodyContext.java').read_text()
for source in ('PlayerPossessionState.java','PlayerInputBridge.java','MindExchangeRuntime.java'):
    assert (root/'src/main/java/dev/freakyfriday/mind'/source).exists(), f'missing player possession source: {source}'
for mixin in ('PlayerPossessionTickMixin.java','ServerPlayerPossessionCameraMixin.java',
              'ServerPlayerPossessionPacketMixin.java'):
    assert (root/'src/main/java/dev/freakyfriday/mixin'/mixin).exists(), f'missing possession Mixin: {mixin}'
for mixin in ('PlayerPossessionTickMixin','ServerPlayerPossessionCameraMixin',
              'ServerPlayerPossessionPacketMixin'):
    assert mixin in mixin_config['mixins'], f'possession mixin not registered: {mixin}'
assert (root/'src/test/java/dev/freakyfriday/mind/MindBodyContextSelfTest.java').exists()
assert "version = '0.13.2-alpha'" in (root/'build.gradle').read_text()
assert (root/'src/platform/minecraft-26.2/java/dev/freakyfriday/compat/PlatformHooks.java').exists()
assert 'src/platform/minecraft-26.2/java' in (root/'build.gradle').read_text()
assert '26.3' not in (root/'build.gradle').read_text(), 'later Minecraft versions must remain deferred'
assert 'PlatformHooks::addBookToCreativeTab' in (root/'src/main/java/dev/freakyfriday/FreakyFriday.java').read_text()
assert not (root/'src/main/java/dev/freakyfriday/AttributeSwapper.java').exists()
assert (root/'src/main/java/dev/freakyfriday/api/AbilityCategoryAdapter.java').exists()
assert 'KeyModifier.CONTROL' in (root/'src/platform/minecraft-26.2/java/dev/freakyfriday/compat/ClientKeyFactory.java').read_text()
assert 'ClientKeyFactory.create()' in (root/'src/main/java/dev/freakyfriday/ClientBindings.java').read_text()
assert 'CategoryExchange.exchange(first,second,port)' in (root/'src/main/java/dev/freakyfriday/AttributeCategorySwapper.java').read_text()
assert not (root/'src/main/java/dev/freakyfriday/core/ExchangeEngine.java').exists()
assert (root/'src/main/java/dev/freakyfriday/mind/DynamicObjectGraph.java').exists()
assert (root/'src/main/java/dev/freakyfriday/mind/GoalSelectorState.java').exists()
scheduler_at=(resources/'META-INF/accesstransformer.cfg').read_text()
for scheduler_field in ('GoalSelector lockedFlags','GoalSelector disabledFlags','WrappedGoal isRunning'):
    assert scheduler_field in scheduler_at, f'missing scheduler access transformer: {scheduler_field}'
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
print('PASS: resources, metadata, 26.2 mixin registration, and AI host hook source wiring')
print('NOT TESTED HERE: real NeoForge build/integrated/dedicated server runtime. Run scripts/build_doctor.py separately.')
