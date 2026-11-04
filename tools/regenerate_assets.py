#!/usr/bin/env python3
# SPDX-License-Identifier: MIT
"""Generate optional procedural samples outside app resources; never overwrite the bundled original artwork."""
from pathlib import Path
import argparse,os,subprocess,tempfile
ROOT=Path(__file__).resolve().parents[1]
p=argparse.ArgumentParser(description=__doc__)
p.add_argument('--output',type=Path,help='Sample output directory; default launcher/build/generated-art-preview')
p.add_argument('--sounds-only',action='store_true',help='Regenerate only the original synthetic sound cues in app resources')
a=p.parse_args();resources=(ROOT/'launcher/app/src/main/res').resolve()
if a.sounds_only:
 if a.output:p.error('--sounds-only uses the existing original sound resource directory; do not combine with --output')
 subprocess.run(['python3',str(ROOT/'launcher/tools/generate_cinema_sounds.py'),'--output',str(resources/'raw')],check=True,capture_output=True)
 print('Original synthetic audio regenerated; no images changed.');raise SystemExit(0)
out=a.output.resolve() if a.output else ROOT/'launcher/build/generated-art-preview'
if out.is_relative_to(resources) or resources.is_relative_to(out):p.error('Procedural samples cannot replace or contain bundled app resource directories')
java=Path(os.environ['JAVA_HOME'])/'bin' if os.environ.get('JAVA_HOME') else None
with tempfile.TemporaryDirectory(prefix='cinematv-art-') as temp:
 subprocess.run([str(java/'javac') if java else 'javac','-d',temp,str(ROOT/'tools/GeneratePublicArt.java')],check=True)
 subprocess.run([str(java/'java') if java else 'java','-Djava.awt.headless=true','-cp',temp,'GeneratePublicArt',str(out)],check=True)
print('Optional procedural samples written to the requested preview directory. Bundled original artwork was not read or changed.')
