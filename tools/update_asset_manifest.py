#!/usr/bin/env python3
# SPDX-License-Identifier: MIT
"""Hash only explicitly reviewed asset entries. Never infers a license for an unknown file."""
from pathlib import Path
import hashlib,json
ROOT=Path(__file__).resolve().parents[1]
if __name__=='__main__':
    provenance=json.loads((ROOT/'assets/provenance.json').read_text())['files'];records={}
    for name,entry in sorted(provenance.items()):
        file=ROOT/name
        if not file.is_file() or file.is_symlink() or not file.resolve().is_relative_to(ROOT):raise SystemExit('Invalid reviewed asset path: '+name)
        if not (ROOT/entry['licenseFile']).is_file():raise SystemExit('Missing reviewed license: '+name)
        records[name]={**entry,'size':file.stat().st_size,'sha256':hashlib.sha256(file.read_bytes()).hexdigest()}
    (ROOT/'assets/manifest.json').write_text(json.dumps({'schema':1,'files':records},indent=2,ensure_ascii=False)+'\n');print('Reviewed asset hashes updated:',len(records))
