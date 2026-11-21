#!/usr/bin/env python3
"""Compile server-side production sources against the real Mojang 26.2 server jar.

NeoForge event/register classes are still stand-ins; this is NOT a NeoForge build.
Provide the user's legal copy of mojang_26.2.jar (server bundler) as argv[1].
ClientBindings isn't included because this JAR only contains server classes.
Requires Java 25; runs the existing synthetic test script first to generate API stubs.
"""
from pathlib import Path
import os
import subprocess
import sys
import zipfile
import tempfile

ROOT = Path(__file__).resolve().parent.parent

def main():
    if len(sys.argv) != 2:
        print('USAGE: python3 scripts/test-mojang-api.py /path/to/mojang_26.2.jar')
        return 2
    bundler = Path(sys.argv[1]).resolve()
    if not bundler.is_file():
        print('Mojang server JAR not found:', bundler)
        return 2
    subprocess.run([sys.executable, str(ROOT/'scripts/test-minecraft-stubs.py')],cwd=ROOT,check=True)
    javac = (Path(os.environ['JAVA_HOME'])/'bin'/'javac') if os.environ.get('JAVA_HOME') else 'javac'
    with tempfile.TemporaryDirectory(prefix='ff_mojang_api_') as temp:
        temp = Path(temp)
        libs = temp/'libs'; libs.mkdir()
        with zipfile.ZipFile(bundler) as container:
            names = container.namelist()
            entries = [x for x in names if x.startswith('META-INF/versions/') and x.endswith('server-26.2.jar')]
            if not entries:
                raise SystemExit('Expected the vanilla 26.2 server bundler with an embedded 26.2 JAR')
            game = temp/'server-26.2.jar'
            game.write_bytes(container.read(entries[0]))
            for name in names:
                if name.startswith('META-INF/libraries/') and name.endswith('.jar'):
                    (libs/Path(name).name).write_bytes(container.read(name))
        cp = os.pathsep.join([str(game),*[str(p) for p in libs.glob('*.jar')]])
        stubs = ROOT/'build/stub-harness/stubs'
        neo = temp/'neoforge-standins'; neo.mkdir()
        shim_sources = []
        for path in stubs.rglob('*.java'):
            if '/net/neoforged/' not in path.as_posix() or '/client/' in path.as_posix():
                continue
            if path.name == 'EventBusSubscriber.java':
                continue
            text = path.read_text()
            if path.name == 'RegisterCommandsEvent.java':
                text = '''package net.neoforged.neoforge.event;
import net.minecraft.commands.CommandSourceStack;
import com.mojang.brigadier.CommandDispatcher;
public class RegisterCommandsEvent {
  public CommandDispatcher<CommandSourceStack> getDispatcher(){return new CommandDispatcher<>();}
}'''
            elif path.name == 'EntityAttributeModificationEvent.java':
                text = text.replace('attribute.value().name().equals("minecraft:max_absorption")',
                                    'attribute.value().toString().equals("minecraft:max_absorption")')
            elif path.name == 'BuildCreativeModeTabContentsEvent.java':
                text = '''package net.neoforged.neoforge.event;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.Identifier;
import net.minecraft.core.registries.Registries;
import net.neoforged.neoforge.registries.DeferredItem;
public class BuildCreativeModeTabContentsEvent {
 public ResourceKey<CreativeModeTab> getTabKey(){return ResourceKey.create(Registries.CREATIVE_MODE_TAB, Identifier.withDefaultNamespace("tools_and_utilities"));}
 public void accept(DeferredItem<?> item){}
}'''
            target = neo/path.relative_to(stubs)
            target.parent.mkdir(parents=True, exist_ok=True)
            target.write_text(text)
            shim_sources.append(str(target))
        prod = [str(p) for p in (ROOT/'src/main/java').rglob('*.java') if p.name != 'ClientBindings.java'] + [str(p) for p in (ROOT/'src/platform/minecraft-26.2/java').rglob('*.java')]
        classes=temp/'classes'; classes.mkdir()
        print('Compiling',len(prod),'server-side production files against actual Mojang 26.2 classes;',len(shim_sources),'NeoForge shims')
        result = subprocess.run([str(javac),'-classpath',cp,'-d',str(classes),*shim_sources,*prod],check=False)
        if result.returncode:
            print('FAIL: Mojang server API compile')
            return result.returncode
        print('PASS: Mojang server 26.2 API compile (NeoForge classes STUBBED; client NOT compiled)')
        return 0

if __name__ == '__main__': sys.exit(main())
