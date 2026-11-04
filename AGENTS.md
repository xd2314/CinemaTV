# Cinema TV contributor instructions

This is an independent Cinema TV fork, not the upstream CleanYourTV repository.
Read README.md, CONTRIBUTING.md and docs/BUILDING.md before editing.

- Default work is local source, unit tests, lint and builds. Do not contact devices, install packages, change HOME, use Root, reboot, or publish without explicit current authorization.
- Preserve original Launcher, media apps and user data. Root boot-appearance tools are experimental and retain their confirmation, backup, binding, readback and UNKNOWN/PENDING guards.
- Keep the stable Carousel geometry and DPAD behavior unless the requested task specifically changes them.
- Avoid heavy rendering engines, synchronous bitmap decode on the UI thread, new network permissions, telemetry and unbounded caches.
- Never copy signing keys, device backups, identifiers or incident receipts into this repository. Test fixtures must be synthetic.
- Add assets only with a documented redistributable license and update assets/manifest.json. The original default artwork is owner-authorized MIT material; preserve it byte-for-byte unless the user explicitly requests a change. Do not replace defaults with procedural samples or regenerate/recolour them.
- Preserve upstream and third-party notices. MIT covers project code; Outfit remains OFL and bundled Fluent Emoji remains under its own MIT notice.
- For launcher changes run `python3 tools/build.py --unsigned` and `python3 -m unittest discover -s tests -v`. Run `python3 tools/check_public_tree.py` before sharing.
- Separate local tests, native previews and actual-device results. Do not claim untested standby, reboot, Root writes or recovery capability as passed.
