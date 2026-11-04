#!/usr/bin/env python3
# SPDX-License-Identifier: MIT
"""Create an allowlisted, unsigned source ZIP outside the repository. No upload, APK signing or device commands."""
from pathlib import Path,PurePosixPath
import argparse,hashlib,json,os,stat,tempfile,zipfile
from check_public_tree import ROOT,check,public_files,IGNORED,FORBIDDEN_DIRS,PRIVATE_EXT

def safe_name(name):
    p=PurePosixPath(name)
    return bool(name) and not p.is_absolute() and '\\' not in name and ':' not in name and p.as_posix()==name and not ({'.','..'}|IGNORED|FORBIDDEN_DIRS).intersection(p.parts) and p.suffix.lower() not in PRIVATE_EXT

def verify(path):
    with zipfile.ZipFile(path) as z:
        names=z.namelist()
        if len(set(names))!=len(names) or any(not safe_name(n) for n in names):raise ValueError('Unsafe or duplicate archive path')
        if len(names)>5000 or any(i.file_size>64*1024*1024 for i in z.infolist()) or sum(i.file_size for i in z.infolist())>256*1024*1024:raise ValueError('Archive exceeds source size limits')
        if any(stat.S_ISLNK(i.external_attr>>16) for i in z.infolist()) or z.testzip():raise ValueError('Symlink or CRC failure')
        records=json.loads(z.read('SOURCE-SHA256.json'))
        if set(records)|{'SOURCE-SHA256.json'}!=set(names):raise ValueError('Archive inventory mismatch')
        for name,record in records.items():
            data=z.read(name)
            if len(data)!=record['size'] or hashlib.sha256(data).hexdigest()!=record['sha256']:raise ValueError('Archive hash mismatch: '+name)
        with tempfile.TemporaryDirectory(prefix='cinematv-verify-') as tmp:
            destination=Path(tmp)
            for name in records:
                target=destination/name;target.parent.mkdir(parents=True,exist_ok=True);target.write_bytes(z.read(name))
            errors=check(destination)
            if errors:raise ValueError('; '.join(errors))
        return len(records)

def package(root,output):
    errors=check(root)
    if errors:raise ValueError('; '.join(errors))
    if output.exists():raise ValueError('Output already exists; never overwrite a release')
    if output.resolve().is_relative_to(root.resolve()):raise ValueError('Choose an output outside the repository')
    output.parent.mkdir(parents=True,exist_ok=True)
    fd,temp=tempfile.mkstemp(prefix='.cinematv-source-',suffix='.zip',dir=output.parent);os.close(fd)
    try:
        records={}
        with zipfile.ZipFile(temp,'w',zipfile.ZIP_DEFLATED) as z:
            for name in (root/'tools/public-files.txt').read_text().splitlines():
                if not safe_name(name):raise ValueError('Unsafe allowlist entry')
                path=root/name;data=path.read_bytes();info=zipfile.ZipInfo.from_file(path,name)
                z.writestr(info,data,compress_type=zipfile.ZIP_DEFLATED);records[name]={'size':len(data),'sha256':hashlib.sha256(data).hexdigest()}
            z.writestr('SOURCE-SHA256.json',json.dumps(records,indent=2,sort_keys=True)+'\n')
        verify(Path(temp));os.link(temp,output)
    finally:Path(temp).unlink(missing_ok=True)
    return len(records)

if __name__=='__main__':
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('output',type=Path);p.add_argument('--verify',action='store_true');a=p.parse_args()
    try:
        count=verify(a.output) if a.verify else package(ROOT,a.output.expanduser().resolve())
        print('PASS:',count,'verified source files');print('SHA-256:',hashlib.sha256(a.output.read_bytes()).hexdigest())
    except (OSError,ValueError,KeyError,zipfile.BadZipFile) as e:p.exit(1,str(e)+'\n')
