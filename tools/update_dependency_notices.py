#!/usr/bin/env python3
# SPDX-License-Identifier: MIT
"""Record resolved coordinates and POM-declared licenses from the local Gradle cache. No device access or uploads."""
from pathlib import Path
import argparse,json,xml.etree.ElementTree as ET,zipfile,urllib.request
ROOT=Path(__file__).resolve().parents[1]
CACHE=Path.home()/'.gradle/caches/modules-2/files-2.1'
FETCH=False
def pom(group,name,version):
    files=list((CACHE/group/name/version).glob('*/*.pom'))
    if files:return ET.parse(files[0]).getroot()
    cache=ROOT/'launcher/build/license-poms'/group/name/(version+'.pom')
    if cache.is_file():return ET.parse(cache).getroot()
    if not FETCH:return None
    base='https://dl.google.com/dl/android/maven2/' if group.startswith('androidx.') else 'https://repo.maven.apache.org/maven2/'
    url=base+group.replace('.','/')+'/'+name+'/'+version+'/'+name+'-'+version+'.pom'
    try:
        data=urllib.request.urlopen(url,timeout=20).read();tree=ET.fromstring(data)
        cache.parent.mkdir(parents=True,exist_ok=True);cache.write_bytes(data);return tree
    except Exception:return None

def licenses(group,name,version,seen=None):
    seen=set() if seen is None else seen;key=(group,name,version)
    if key in seen:return []
    seen.add(key);tree=pom(group,name,version)
    if tree is None:return []
    records=[]
    for node in tree.findall('{*}licenses/{*}license'):
        records.append({'name':node.findtext('{*}name','').strip(),'url':node.findtext('{*}url','').strip()})
    if records:return records
    parent=tree.find('{*}parent')
    if parent is not None:return licenses(parent.findtext('{*}groupId',''),parent.findtext('{*}artifactId',''),parent.findtext('{*}version',''),seen)
    return []

if __name__=='__main__':
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--fetch-missing',action='store_true',help='Fetch absent public POM metadata from official Google/Maven Central repositories');FETCH=parser.parse_args().fetch_missing
    report=json.loads((ROOT/'launcher/app/build/public-dependencies.json').read_text());records={}
    for scope,modules in report.items():
        for module in modules:
            key=':'.join(module[k] for k in ('group','name','version'))
            if key not in records:records[key]={**module,'licenses':licenses(**module),'scopes':[]}
            records[key]['scopes'].append(scope)
    runtime=[k for k,v in records.items() if 'releaseRuntimeClasspath' in v['scopes']]
    unknown=[k for k in runtime if not records[k]['licenses']]
    if unknown:raise SystemExit('Runtime license metadata missing: '+', '.join(unknown))
    (ROOT/'docs/dependencies.json').write_text(json.dumps({'source':'Gradle resolution and locally cached public Maven POM license metadata','buildTool':'Gradle8.9 / AGP8.5.2','modules':dict(sorted(records.items()))},indent=2)+'\n')
    sections=[]
    for file in ['LICENSE','LICENSES/Apache-2.0.txt','LICENSES/Outfit-OFL-1.1.txt','LICENSES/Fluent-Emoji-MIT.txt']:
        sections.append('===== '+file+' =====\n'+(ROOT/file).read_text())
    sections.append('===== Runtime dependency coordinates and POM-declared licenses =====\n'+'\n'.join(k+' | '+'; '.join(x['name']+' '+x['url'] for x in records[k]['licenses']) for k in sorted(runtime)))
    # Preserve any additional text notices actually shipped by the resolved runtime artifacts.
    for key in sorted(runtime):
        v=records[key]
        for artifact in sorted((CACHE/v['group']/v['name']/v['version']).glob('*/*')):
            if artifact.suffix not in {'.aar','.jar'}:continue
            with zipfile.ZipFile(artifact) as z:
                for info in z.infolist():
                    name=Path(info.filename).name.lower()
                    if info.file_size>65536 or not any(name.startswith(prefix) for prefix in ('license','notice','copying')):continue
                    try:text=z.read(info).decode('utf-8')
                    except UnicodeDecodeError:continue
                    sections.append('===== '+key+' / '+info.filename+' =====\n'+text)
    owner=ROOT/'assets/original-artwork-permission.json'
    if owner.is_file():
        permission=json.loads(owner.read_text())
        sections.append('===== Original default artwork =====\nOwner statement ('+permission['date']+'): '+permission['ownerStatement']+'\nOriginal resource bytes are retained. See assets/original-artwork-permission.json in source. Existing upstream attribution is preserved.')
    (ROOT/'launcher/app/src/main/assets/opensource-notices.txt').write_text('\n\n'.join(sections)+'\n')
    print('Runtime modules:',len(runtime),'all resolved modules:',len(records),'unlisted test license metadata:',sum(not v['licenses'] for v in records.values()))
