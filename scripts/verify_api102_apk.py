#!/usr/bin/env python3
"""Check the built (including R8) module against its API 102 registration contract."""
import argparse
from pathlib import Path
import re
import subprocess
import tempfile
import zipfile

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('apk', type=Path)
parser.add_argument('--build-tools', type=Path, required=True)
args = parser.parse_args()
with zipfile.ZipFile(args.apk) as apk:
    names = set(apk.namelist())
    prefix = 'META-INF/xposed/'
    assert 'assets/xposed_init' not in names, 'Old asset entry packaged'
    entries = apk.read(prefix + 'java_init.list').decode().strip().splitlines()
    assert len(entries) == 1 and entries[0].strip(), 'Exactly one Java entry required'
    entry = 'L' + entries[0].strip().replace('.', '/') + ';'
    props = dict(line.split('=', 1) for line in apk.read(prefix + 'module.prop').decode().splitlines()
                 if line and not line.startswith('#'))
    assert props['minApiVersion'] == props['targetApiVersion'] == '102'
    assert props['staticScope'] == 'true'
    assert props.get('autoHotReload', 'false') == 'false'
    assert apk.read(prefix + 'scope.list').decode().splitlines() == ['com.tencent.mm', 'com.tencent.mm.wx']
    found_entry = False
    dex_count = 0
    with tempfile.TemporaryDirectory(prefix='weiplus-apk-check-') as temp:
        for name in sorted(names):
            if not re.fullmatch(r'classes\d*\.dex', name):
                continue
            dex_count += 1
            data = apk.read(name)
            assert b'de/robv/android/xposed' not in data, 'Old framework reference in DEX'
            dex = Path(temp, name)
            dex.write_bytes(data)
            output = subprocess.check_output([str(args.build_tools / 'dexdump'), str(dex)], text=True, errors='replace')
            blocks = re.split(r'(?=Class #\d+\s)', output)
            for block in blocks:
                match = re.search(r"Class descriptor\s*:\s*'([^']+)'", block)
                if not match:
                    continue
                descriptor = match[1]
                assert not descriptor.startswith('Lio/github/libxposed/api/'), 'Framework API class packaged'
                if descriptor == entry:
                    assert re.search(r"Superclass\s*:\s*'Lio/github/libxposed/api/XposedModule;'", block)
                    assert re.search(r"name\s*:\s*'<init>'\s+type\s*:\s*'\(\)V'\s+access\s*:.*PUBLIC", block)
                    found_entry = True
    assert dex_count and found_entry, 'Registered entry missing or not a public no-arg modern module'
manifest = subprocess.check_output([str(args.build_tools / 'aapt'), 'dump', 'xmltree',
                                    str(args.apk), 'AndroidManifest.xml'], text=True)
assert not re.search(r'"xposed(module|minversion|description|scope)"', manifest), 'Old manifest metadata'
assert 'android:description' in manifest and 'android:label' in manifest
print(f'PASS API102 APK: {args.apk.name}; entry={entries[0]}; dex={dex_count}; framework API external')
