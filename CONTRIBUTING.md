# Contributing / 參與開發

Small, reviewable changes are welcome. This repository is the independent Cinema TV fork; do not file its device-specific behavior as an upstream CleanYourTV defect without reproducing it there.

1. Read README.md and docs/BUILDING.md. Use JDK21 and the included wrapper.
2. Keep one concrete change per branch/PR. Preserve TV remote focus, small-memory behavior and OEM launcher fallback.
3. Run `python3 -m unittest discover -s tests -v`, `python3 tools/check_public_tree.py` and `python3 tools/build.py --unsigned`.
4. Describe what changed, validation performed and remaining gaps. A simulator/native screenshot is not an actual-device result. Device tests need the owner's separate approval.
5. Add screenshots made with licensed assets and synthetic sample apps. Remove identifiers and accounts. Never include signing keys, backups, partitions, user receipts or media credentials.
6. New artwork/audio must have documented origin, license and hashes in assets/manifest.json. Regenerate project-owned artwork with tools/regenerate_assets.py.

By submitting your contribution, you agree to license your own contribution under this repository's MIT license, while preserving applicable third-party notices. Do not submit material you cannot license. No CLA or transfer of copyright is required.

Root code changes must preserve explicit confirmation, readback, backup export, path/UID binding, once-only dispatch and PENDING/UNKNOWN stop conditions. A synthetically passing test does not prove hardware safety. No automatic device test is included in CI.

Issue templates support both English and Chinese. Feature requests should explain the user problem and TV remote flow before proposing dependencies or new services.
