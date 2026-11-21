#!/usr/bin/env python3
"""Run the actual production modifier and transaction code on vanilla Minecraft 26.2 classes.

This is a headless Minecraft library-level test, NOT a NeoForge build or a server
smoke test. It reads the user-supplied vanilla 26.2 server bundler and its embedded
libraries, and uses the JDK 25 javac/java supplied by JAVA_HOME.
"""
import os
import subprocess
import sys
import tempfile
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
JAVAS = [ROOT/'src/main/java/dev/freakyfriday/AttributeModifierTransfer.java',
         ROOT/'src/main/java/dev/freakyfriday/core/CategoryExchange.java',
         ROOT/'src/main/java/dev/freakyfriday/core/CategoryBundle.java',
         ROOT/'src/main/java/dev/freakyfriday/core/PermanentModifier.java',
         ROOT/'scripts/java/MojangAttributeRuntimeTest.java']


def run():
    if len(sys.argv) != 2:
        print('USAGE: python3 scripts/test-mojang-attributes.py /path/to/mojang_26.2.jar')
        return 2
    archive = Path(sys.argv[1])
    if not archive.is_file():
        print('Missing vanilla 26.2 server bundler:', archive)
        return 2
    home = Path(os.environ['JAVA_HOME']) if os.environ.get('JAVA_HOME') else None
    javac = str(home/'bin/javac') if home else 'javac'
    java = str(home/'bin/java') if home else 'java'
    with tempfile.TemporaryDirectory(prefix='freakyfriday_mojang_runtime_') as dirpath:
        temp = Path(dirpath)
        game = temp/'server-26.2.jar'
        lib_dir = temp/'libraries'; lib_dir.mkdir()
        with zipfile.ZipFile(archive) as z:
            version = next((name for name in z.namelist() if name.startswith('META-INF/versions/') and name.endswith('server-26.2.jar')), None)
            if version is None:
                print('Expected vanilla Minecraft 26.2 server bundler')
                return 2
            game.write_bytes(z.read(version))
            for name in z.namelist():
                if name.startswith('META-INF/libraries/') and name.endswith('.jar'):
                    (lib_dir/Path(name).name).write_bytes(z.read(name))
        classpath = os.pathsep.join([str(game), *map(str,lib_dir.glob('*.jar'))])
        classes = temp/'classes'; classes.mkdir()
        subprocess.run([javac, '-cp', classpath, '-d', str(classes),
                        *map(str, JAVAS)], check=True)
        subprocess.run([java, '-cp', os.pathsep.join([str(classes),classpath]),
                        'dev.freakyfriday.MojangAttributeRuntimeTest'], check=True)
    return 0


if __name__ == '__main__':
    sys.exit(run())
