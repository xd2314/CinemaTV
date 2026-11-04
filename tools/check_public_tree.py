#!/usr/bin/env python3
# SPDX-License-Identifier: MIT
"""Offline publication checks. These checks do not replace human review or prove hardware safety."""
from pathlib import Path,PurePosixPath
import argparse,hashlib,json,re,sys
ROOT=Path(__file__).resolve().parents[1]
IGNORED={'.git','build','.gradle','__pycache__','.idea','.vscode'}
FORBIDDEN_DIRS={'.private-signing','backup','backups','evidence','local-results'}
BINARY_EXT={'.png','.jpg','.jpeg','.webp','.ttf','.wav','.mp4','.bin','.jar'}
OWN_VECTOR_ASSETS={'launcher/app/src/main/res/mipmap-xhdpi/ic_launcher.xml','launcher/app/src/main/res/drawable-nodpi/banner.xml'}
PRIVATE_EXT={'.keystore','.jks','.p12','.pfx','.pem','.apk','.aab','.apks','.img','.zip'}

def public_files(root):
    files=[]
    for path in sorted(root.rglob('*')):
        rel=path.relative_to(root)
        if any(p in IGNORED for p in rel.parts) or path.name=='.DS_Store' or rel.as_posix()=='SOURCE-SHA256.json':continue
        if path.is_symlink():raise ValueError('Symlink is not allowed: '+rel.as_posix())
        if path.is_file():files.append(path)
    return files

def check(root=ROOT,check_inventory=True):
    errors=[]
    try:files=public_files(root)
    except ValueError as e:return [str(e)]
    for path in files:
        rel=path.relative_to(root)
        if FORBIDDEN_DIRS.intersection(rel.parts) or path.suffix.lower() in PRIVATE_EXT or path.name in {'local.properties','key.properties','keystore.properties','signing.properties','.env'}:
            errors.append('Private/generated file: '+rel.as_posix());continue
        if path.suffix.lower() in BINARY_EXT:continue
        try:text=path.read_text('utf-8')
        except UnicodeDecodeError:errors.append('Unclassified binary: '+rel.as_posix());continue
        rules={
          'personal absolute home path':r'/(?:Users|home)/[A-Za-z0-9_.-]+/',
          'private IPv4 address':r'(?<![\d.])(?:192\.168\.\d{1,3}\.\d{1,3}|10\.\d{1,3}\.\d{1,3}\.\d{1,3}|172\.(?:1[6-9]|2\d|3[01])\.\d{1,3}\.\d{1,3})(?![\d.])',
          'private key material':r'-----BEGIN (?:RSA |EC |OPENSSH |DSA )?PRIVATE KEY-----',
          'GitHub credential':r'\b(?:gh[pousr]_[A-Za-z0-9]{30,}|github_pat_[A-Za-z0-9_]{60,})\b',
          'AWS access credential':r'\bAKIA[A-Z0-9]{16}\b',
        }
        for label,pattern in rules.items():
            if re.search(pattern,text):errors.append(label+': '+rel.as_posix())
    for name in ['LICENSE','NOTICE','THIRD_PARTY_NOTICES.md','README.md','README.zh-TW.md','CONTRIBUTING.md','SECURITY.md','docs/BUILDING.md','docs/ASSETS.md','docs/RELEASE-READINESS.md']:
        if not (root/name).is_file():errors.append('Missing required documentation: '+name)
    manifest=root/'assets/manifest.json'
    if manifest.is_file():
        try:
            records=json.loads(manifest.read_text())['files'];actual={p.relative_to(root).as_posix() for p in files if p.suffix.lower() in BINARY_EXT or p.relative_to(root).as_posix() in OWN_VECTOR_ASSETS}
            provenance=json.loads((root/'assets/provenance.json').read_text())['files']
            if set(provenance)!=set(records):errors.append('Asset provenance inventory mismatch')
            if set(records)!=actual:errors.append('Binary inventory mismatch: '+str(sorted(actual.symmetric_difference(records))))
            for name,record in records.items():
                p=root/name
                if not p.is_file() or hashlib.sha256(p.read_bytes()).hexdigest()!=record['sha256'] or p.stat().st_size!=record['size']:errors.append('Asset hash mismatch: '+name)
                if name in provenance and any(record.get(k)!=provenance[name].get(k) for k in ['license','licenseFile','source','origin']):errors.append('Asset provenance changed: '+name)
                if record.get('license') not in {'MIT','Apache-2.0','OFL-1.1'}:errors.append('Unreviewed asset license: '+name)
        except (OSError,ValueError,KeyError,TypeError):errors.append('Invalid asset manifest')
    else:errors.append('Missing asset manifest')
    wrapper=root/'launcher/gradle/wrapper/gradle-wrapper.jar';checksum=root/'tools/gradle-wrapper-sha256.txt'
    if not checksum.is_file() or not wrapper.is_file() or hashlib.sha256(wrapper.read_bytes()).hexdigest()!=checksum.read_text().strip():errors.append('Gradle wrapper checksum mismatch')
    archive_manifest=root/'SOURCE-SHA256.json'
    if archive_manifest.is_file():
        try:
            archive_records=json.loads(archive_manifest.read_text())
            names={p.relative_to(root).as_posix() for p in files}
            if set(archive_records)!=names:errors.append('Extracted source manifest inventory mismatch')
            for name,record in archive_records.items():
                if name not in names:continue
                path=root/name
                if hashlib.sha256(path.read_bytes()).hexdigest()!=record['sha256'] or path.stat().st_size!=record['size']:errors.append('Extracted source hash mismatch: '+name)
        except (OSError,ValueError,KeyError,TypeError):errors.append('Invalid extracted source manifest')
    if check_inventory:
        inventory=root/'tools/public-files.txt'
        names=[p.relative_to(root).as_posix() for p in files]
        if not inventory.is_file() or sorted(inventory.read_text().splitlines())!=sorted(names):errors.append('Public source allowlist mismatch; review changes before refreshing tools/public-files.txt')
    return errors

if __name__=='__main__':
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--no-inventory',action='store_true',help='Contributor check while explicitly updating the reviewed source allowlist');a=p.parse_args()
    errors=check(check_inventory=not a.no_inventory)
    for error in errors:print('FAIL:',error)
    if errors:sys.exit(1)
    print('PASS: offline privacy patterns, source paths, required notices, asset hashes and wrapper checksum. Human review is still required.')
