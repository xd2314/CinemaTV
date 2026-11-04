# Third-party notices

Project code uses the MIT license in LICENSE. These third-party components retain their own notices and licenses; the project MIT statement does not relicense them.

| Component | Use | License / source |
| --- | --- | --- |
| CleanYourTV | Inherited Java launcher and tests | MIT, Copyright 2026 Gathin; LICENSES/CleanYourTV-MIT.txt; https://github.com/Gathin23/cleanyourtv |
| Outfit | Bundled original font | OFL-1.1, The Outfit Project Authors; LICENSES/Outfit-OFL-1.1.txt; https://github.com/Outfitio/Outfit-Fonts |
| Microsoft Fluent Emoji | Four resized Halloween WebP derivatives; pumpkin light/dark modification documented | MIT, Microsoft Corporation; LICENSES/Fluent-Emoji-MIT.txt; https://github.com/microsoft/fluentui-emoji |
| Gradle Wrapper 8.9 | Included wrapper JAR/scripts only | Apache-2.0; LICENSES/Gradle-wrapper-LICENSE.txt and LICENSES/Apache-2.0.txt; https://github.com/gradle/gradle |
| AndroidX RecyclerView1.3.2 / DynamicAnimation1.0.0 | Direct runtime dependencies | Apache-2.0; https://android.googlesource.com/platform/frameworks/support/ |
| AndroidX / Guava listenablefuture / JetBrains Kotlin and annotations | Resolved transitive runtime modules | POM license metadata recorded per coordinate in docs/dependencies.json; Apache-2.0 notice in LICENSES/Apache-2.0.txt |
| JUnit4.13.2 | Test only | EPL-1.0; LICENSES/EPL-1.0.txt; https://github.com/junit-team/junit4 |
| Robolectric4.14.1 | Test only | MIT; https://github.com/robolectric/robolectric |
| Other resolved test modules | Test only; downloaded at build time | Exact coordinates and POM-declared licenses in docs/dependencies.json |

The source distribution does not contain dependency AAR/JAR files except the Gradle wrapper, nor the JDK, Android SDK, Gradle distribution or test runtime cache. Runtime licensing text and any additional text notices found in resolved runtime artifacts are also included as `launcher/app/src/main/assets/opensource-notices.txt`, so release APKs preserve notices.

Recreate the dependency list with the Gradle `publicDependencyReport` task, then run `python3 tools/update_dependency_notices.py` with the corresponding POMs cached. Add `--fetch-missing` to retrieve absent public POM metadata from Google/Maven Central. This records declared metadata and does not claim that one top-level license describes every third-party binary inside external build/test tools.

The precise original CleanYourTV commit was not captured in the inherited snapshot. The upstream URL is an attribution and license reference, not a claim that current upstream main exactly matches this fork.

External application names/icons displayed at runtime remain owned by their respective providers. The source ZIP includes no vendor apps, subscriptions, firmware, private user images, credentials or media content. Original default artwork is distributed under MIT based on the owner's project-generated/owned-material statement; existing upstream attribution for inherited marks is retained. Generated UI audio and optional procedural samples are project-original MIT material. See docs/ASSETS.md, assets/original-artwork-permission.json and assets/manifest.json.
