#!/usr/bin/env python3
"""Inspect selected NeoForge 26.3 universal classes with javap.
This does not compile our mod or substitute for full NeoForge dependencies.
"""
from pathlib import Path
import os
import subprocess
import sys

EXPECTED = {
  'net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent': ('getTabKey()',),
  'net.neoforged.neoforge.event.entity.EntityAttributeModificationEvent': ('getTypes()', 'boolean has(', 'void add('),
  'net.neoforged.neoforge.event.entity.player.PlayerInteractEvent$EntityInteract': ('getTarget()', 'getCancellationResult()', 'setCancellationResult('),
  'net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent': ('void register(net.minecraft.client.KeyMapping)',),
  'net.neoforged.neoforge.client.event.ClientTickEvent$Post': ('ClientTickEvent$Post()',),
  'net.neoforged.neoforge.registries.DeferredRegister$Items': ('registerItem(',),
}
if len(sys.argv)!=2:
  print('USAGE: python3 scripts/inspect-neoforge-api.py /path/to/neoforge-26.3.0.115-universal.jar')
  sys.exit(2)
jar=Path(sys.argv[1])
assert jar.is_file(), jar
java=Path(os.environ['JAVA_HOME'])/'bin/javap' if os.environ.get('JAVA_HOME') else 'javap'
for name, symbols in EXPECTED.items():
  r=subprocess.run([str(java),'-classpath',str(jar),name],text=True,capture_output=True)
  if r.returncode:
    print(f'FAIL {name}: {r.stderr[:350]}')
    sys.exit(1)
  for symbol in symbols:
    if symbol not in r.stdout:
      print(f'FAIL {name} missing {symbol}')
      sys.exit(1)
  print(f'PASS 26.3 universal API declaration: {name}')
print('API declarations present; real 26.3 compilation and gameplay NOT verified')
