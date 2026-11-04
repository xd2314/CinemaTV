#!/usr/bin/env python3
# SPDX-License-Identifier: MIT
"""Build/test Cinema TV locally. Never connects to a device, reads Keychain or publishes anything."""
from pathlib import Path
import argparse,os,subprocess,sys
ROOT=Path(__file__).resolve().parents[1]
p=argparse.ArgumentParser(description=__doc__);p.add_argument('--offline',action='store_true');p.add_argument('--unsigned',action='store_true',help='Explicitly ignore release signing environment variables');p.add_argument('tasks',nargs='*',default=[])
a=p.parse_args();allowed={'testReleaseUnitTest','lintRelease','assembleRelease','assembleDebug'}
if any(t not in allowed for t in a.tasks):p.error('Only local test, lint and assemble tasks are supported')
env=os.environ.copy()
if a.unsigned:
 for key in ('CINEMA_KEYSTORE_PATH','CINEMA_KEYSTORE_PASSWORD','CINEMA_KEY_ALIAS','CINEMA_KEY_PASSWORD'):env.pop(key,None)
if sys.platform=='darwin':
 if not env.get('JAVA_HOME'):
  java=subprocess.run(['/usr/libexec/java_home','-v','21'],capture_output=True,text=True)
  if java.returncode==0:env['JAVA_HOME']=java.stdout.strip()
 sdk=Path.home()/'Library/Android/sdk'
 if not env.get('ANDROID_HOME') and not env.get('ANDROID_SDK_ROOT') and sdk.is_dir():env['ANDROID_HOME']=str(sdk)
launcher=ROOT/'launcher';folder=launcher/'build';folder.mkdir(exist_ok=True)
cmd=(['cmd','/c','gradlew.bat'] if os.name=='nt' else ['/bin/sh','./gradlew'])+['--no-daemon']
if a.offline:cmd.append('--offline')
cmd.extend(a.tasks or ['testReleaseUnitTest','lintRelease','assembleRelease'])
log=folder/'local-build.log';print('Local build only. Log:',log,flush=True)
with log.open('w') as output:result=subprocess.run(cmd,cwd=launcher,env=env,stdout=output,stderr=subprocess.STDOUT)
print('Build exit:',result.returncode);print('APK output: launcher/app/build/outputs/apk/ (release is unsigned unless signing was explicitly configured)');sys.exit(result.returncode)
