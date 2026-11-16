# Release parameters (release-seal)

The generic steps live in the global `release-seal` skill. This file only records the parameters and exceptions for this project.

## Version

- Semantic versioning `X.Y.Z`, shared by every language.
- **The version is bumped at release time.** Do not bump to the next version after sealing (skip the skill's "bump version" step).
- Update every occurrence in the release commit. Find them with:
  `grep -rn "<old version>" --exclude-dir=.tmp --exclude-dir=.private --exclude-dir=.git --exclude=CHANGELOG.md .`
  (build files of each language, `CMakeLists.txt`, version constants in the Python, Ruby and Perl sources, and the install snippets in both READMEs).
- On a major version change, also change the `/vN` suffix of the module path in `go/go.mod` and the Go snippets in both READMEs.
- Move the `CHANGELOG.md` section from "unreleased" to the released version.

## Pre-release checks

```bash
./test.sh          # every language with a local toolchain; C# runs only in CI
./gradlew build
```

CI must be green for the release commit, including the `csharp` and `apple` jobs. If the scoring changes, regenerate the snapshot with `java/test.sh --generate` and review every changed line before releasing.

## Tags

- `X.Y.Z` (no `v` prefix), annotated, message `PwScore X.Y.Z`. JitPack, SwiftPM and the git dependencies of Dart, Python, Rust and Ruby use it.
- `go/vX.Y.Z` on the same commit, for the Go module in `go/`.
- Push both to `origin`. Then check `https://jitpack.io/api/builds/com.github.venshine/PwScore/X.Y.Z`.

## GitHub Release

- Title: `PwScore X.Y.Z`. Notes in English, with sections such as "Scoring changes / API / Other / Installation", kept or dropped as the release requires.
- Attachment: the Java library jar, named `PwScore.jar`. Build with `./gradlew jar`; the output is `build/libs/PwScore-X.Y.Z.jar`, uploaded under the fixed name.

## Afterwards

- The web demo deploys from `master` through `.github/workflows/pages.yml`; check that https://venshine.github.io/PwScore/ shows the new version's scores.
- Update `ROADMAP.md`. There is no product doc; the GitHub Release and `CHANGELOG.md` are the release notes.
