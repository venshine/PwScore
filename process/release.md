# Release parameters (release-seal)

The generic steps live in the global `release-seal` skill. This file only records the parameters and exceptions for this project.

## Version

- Semantic versioning `X.Y.Z`.
- **The version is bumped at release time.** Do not bump to the next version after sealing (skip the skill's "bump version" step).
- Update all of these in the release commit (`grep -rn "<old version>" build.gradle README.md README-CN.md` should hit every one):
  - `version` in `build.gradle`. The Swing sample's version chip reads it at runtime, so nothing in `sample/` needs editing.
  - The Groovy and Kotlin DSL dependency coordinates and the jar download link in `README.md` and `README-CN.md`
- Move the `CHANGELOG.md` section from "unreleased" to the released version.

## Pre-release checks

```bash
./test.sh
./gradlew build
```

If the scoring changes, regenerate `test/score-snapshot.tsv` and review every changed line in the diff before releasing.

## Tag

- The tag name is the bare version, without a `v` prefix: `2.1.0`
- Annotated tag with the message `CheckPasswordStrength X.Y.Z`, created on the release commit and pushed to `origin`
- JitPack builds from the tag; the coordinate is `com.github.venshine:CheckPasswordStrength:X.Y.Z`. After pushing, check `https://jitpack.io/api/builds/com.github.venshine/CheckPasswordStrength/X.Y.Z`

## GitHub Release

- Title: `CheckPasswordStrength X.Y.Z`. Notes are written in English, with sections such as "Scoring changes / API / Other / Installation", kept or dropped as the release requires
- Attachment: the library jar, named `CheckPasswordStrength.jar` (the README download link points to this name)
  - Build: `./gradlew jar`; output at `build/libs/CheckPasswordStrength-X.Y.Z.jar`, uploaded under the fixed name

## Afterwards

- There is no product doc or ROADMAP; the GitHub Release and `CHANGELOG.md` are the source of truth for release notes, so no other docs need syncing
