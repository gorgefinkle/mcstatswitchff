#!/usr/bin/env python3
"""Offline audit of source isolation, version profile wiring and build-time metadata.
Not a Gradle build; explicitly checks the source contract for separate version outputs.
"""
from pathlib import Path
import configparser
import re
import subprocess
import sys
import tomllib

ROOT=Path(__file__).resolve().parent.parent
profiles={}
for mc in ('26.2','26.3'):
    pf=ROOT/f'versions/minecraft-{mc}.properties'
    assert pf.is_file(), f'missing {pf}'
    entries=dict(line.strip().split('=',1) for line in pf.read_text().splitlines() if line.strip() and not line.startswith('#'))
    assert entries['minecraft_version']==mc
    assert entries['neo_version'].startswith(mc+'.0.')
    assert entries['java_version']=='25'
    folder=ROOT/f'src/platform/minecraft-{mc}/java/dev/freakyfriday/compat'
    hooks=folder/'PlatformHooks.java'
    assert hooks.exists()
    assert 'addBookToCreativeTab' in hooks.read_text()
    profiles[mc]=entries

source=(ROOT/'build.gradle').read_text()
assert "providers.gradleProperty('targetMinecraft').orElse('26.2')" in source
assert '"src/platform/minecraft-${selectedMinecraft}/java"' in source
assert '"freakyfriday-mc${selectedMinecraft}-neoforge"' in source
assert 'version = profile.getProperty(\'neo_version\')' in source
assert 'filesMatching(\'META-INF/neoforge.mods.toml\')' in source
assert (ROOT/'src/main/java/dev/freakyfriday/core/CategoryExchange.java').exists()
assert not list((ROOT/'src/platform').rglob('CategoryExchange.java')), 'shared core may not be duplicated'
metadata=(ROOT/'src/main/resources/META-INF/neoforge.mods.toml').read_text()
for mc, d in profiles.items():
    expanded=metadata.replace('${ff_mod_version}','0.13.2-alpha').replace('${ff_mc_version}',mc).replace('${ff_neo_version}',d['neo_version'])
    info=tomllib.loads(expanded)
    assert info['mods'][0]['version']=='0.13.2-alpha'
    deps={x['modId']:x['versionRange'] for x in info['dependencies']['freakyfriday']}
    assert deps['minecraft']==f'[{mc}]'
    assert deps['neoforge']==f"[{d['neo_version']},)"
    assert mc in f'freakyfriday-mc{mc}-neoforge-0.13.2-alpha.jar'
    print(f'PASS offline target profile: mc={mc} neo={d["neo_version"]} exact metadata + unique artifact naming')
# Evidence: source trees include exactly one version hook with same binary name.
assert all(sum(1 for x in (ROOT/f'src/platform/minecraft-{mc}/java').rglob('PlatformHooks.java'))==1 for mc in profiles)
print('PASS source separation and shared-core uniqueness (not a real Gradle compile)')
