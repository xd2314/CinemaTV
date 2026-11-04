# SPDX-License-Identifier: MIT
from pathlib import Path
import importlib.util,json,shutil,subprocess,sys,tempfile,unittest,zipfile
ROOT=Path(__file__).resolve().parents[1];sys.path.insert(0,str(ROOT/'tools'))
from check_public_tree import check,public_files
from make_source_release import package,verify,safe_name

class PublicationTest(unittest.TestCase):
    def copy(self,destination):
        for p in public_files(ROOT):
            q=destination/p.relative_to(ROOT);q.parent.mkdir(parents=True,exist_ok=True);shutil.copy2(p,q)
    def test_current_source_passes_publication_checks(self):self.assertEqual([],check(ROOT))
    def test_private_file_and_device_address_are_rejected(self):
        with tempfile.TemporaryDirectory() as temp:
            root=Path(temp);self.copy(root);(root/'personal.jks').write_bytes(b'synthetic-test-not-a-key')
            (root/'private.txt').write_text('.'.join(['192','168','20','5']))
            errors=check(root,False);self.assertTrue(any('Private/generated' in e for e in errors));self.assertTrue(any('private IPv4' in e for e in errors))
    def test_changed_asset_is_rejected(self):
        with tempfile.TemporaryDirectory() as temp:
            root=Path(temp);self.copy(root);file=root/'launcher/app/src/main/res/raw/cinema_move.wav';file.write_bytes(file.read_bytes()+b'changed');self.assertTrue(any('Asset hash mismatch' in e for e in check(root,False)))
    def test_unknown_binary_is_not_silently_allowed(self):
        with tempfile.TemporaryDirectory() as temp:
            root=Path(temp);self.copy(root);(root/'mystery.png').write_bytes(b'unknown');self.assertTrue(any('Binary inventory mismatch' in e for e in check(root,False)))
    def test_new_unreviewed_source_is_rejected(self):
        with tempfile.TemporaryDirectory() as temp:
            root=Path(temp);self.copy(root);(root/'extra.txt').write_text('Review me');self.assertTrue(any('allowlist mismatch' in e for e in check(root)))
    def test_symlink_is_rejected(self):
        with tempfile.TemporaryDirectory() as temp:
            root=Path(temp);(root/'escape').symlink_to(ROOT/'README.md');self.assertTrue(any('Symlink' in e for e in check(root,False)))
    def test_archive_roundtrip_and_existing_output_protection(self):
        with tempfile.TemporaryDirectory() as temp:
            out=Path(temp)/'source.zip';self.assertGreater(package(ROOT,out),380);self.assertGreater(verify(out),380);original=out.read_bytes()
            with self.assertRaises(ValueError):package(ROOT,out)
            self.assertEqual(original,out.read_bytes())
            extracted=Path(temp)/'extracted';extracted.mkdir()
            with zipfile.ZipFile(out) as z:z.extractall(extracted)
            self.assertEqual([],check(extracted))
    def test_archive_tampering_and_unsafe_paths_are_rejected(self):
        for path in ['../escape','/absolute','a/../b','a//b','a\\b','.git/config','build/hidden.txt','evidence/private.txt','private.jks']:
            self.assertFalse(safe_name(path),path)
        with tempfile.TemporaryDirectory() as temp:
            out=Path(temp)/'source.zip';package(ROOT,out);bad=Path(temp)/'bad.zip'
            with zipfile.ZipFile(out) as source,zipfile.ZipFile(bad,'w') as dest:
                for name in source.namelist():dest.writestr(name,b'changed' if name=='README.md' else source.read(name))
            with self.assertRaises(ValueError):verify(bad)
    def test_sample_generator_cannot_overwrite_bundled_artwork(self):
        original=ROOT/'launcher/app/src/main/res/raw/cinema_lake.png';before=original.read_bytes()
        for destination in [ROOT/'launcher/app/src/main/res',ROOT/'launcher/app/src/main/res/raw',ROOT]:
            result=subprocess.run([sys.executable,str(ROOT/'tools/regenerate_assets.py'),'--output',str(destination)],capture_output=True,text=True)
            self.assertEqual(2,result.returncode);self.assertIn('cannot replace',result.stderr)
        self.assertEqual(before,original.read_bytes())
    def test_build_helper_rejects_install_tasks_before_running_gradle(self):
        result=subprocess.run([sys.executable,str(ROOT/'tools/build.py'),'installRelease'],capture_output=True,text=True)
        self.assertEqual(2,result.returncode);self.assertIn('Only local',result.stderr)

if __name__=='__main__':unittest.main()
