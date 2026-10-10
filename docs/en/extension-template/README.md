# Extension Gradle template

This folder is documentation. It is not included in the app build, so it does not slow compilation.

1. Copy the folder out of the repository.
2. Add a `settings.gradle.kts` with `rootProject.name = "mysource"` and the Android plugin repository.
3. Point `compileOnly` at the published `dev.endlesssea:extensions-api:1.0.0` artifact, or at a `files("extensions-api.jar")` built from this project.
4. `./gradlew assembleRelease`
5. Rename the release APK to `mysource-1.0.0.esx` and publish its SHA-256 in an `index.json`.

See [../extension-dev-guide.md](../extension-dev-guide.md).
