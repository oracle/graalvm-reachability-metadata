# Developing the Repository Infrastructure

This document summarizes those commands that you need to perform development tasks.

This repo is developed with IntelliJ. To initialize use:
```console
intellij-idea-community ./
```

Always use the Gradle wrapper from the repository root:
- Unix: `./gradlew <task> [options]`
- Windows: `gradlew.bat <task> [options]`

Prerequisites for most commands:
- `JAVA_HOME` should be set to JDK 21 or later. GraalVM is recommended to match CI.
- Docker (required for pulling/using allowed images during tests). Needs to work without `sudo`: `sudo usermod -aG docker $USER` and reboot.
- [`grype`](https://github.com/anchore/grype) version `0.104.0` for scanning docker images:
    ```console
    curl -sSfL https://get.anchore.io/grype/v0.104.0/install.sh | sudo sh -s -- -b /usr/local/bin
    ```

Tip: When debugging locally, add `--stacktrace` for better error output.

### End-to-end testing before the commit
```console
./gradlew testAllInfra --stacktrace
```

### Style and formatting

1. To check style use
    ```console
    ./gradlew checkstyle
   ```
   This will run Checkstyle using `gradle/checkstyle.xml`.

2. Spotless Verifies formatting (used in release workflow prior to packaging):
    ```console
   ./gradlew spotlessCheck
    ```
3. Auto-fix license headers and formatting locally:
    ```console
    ./gradlew spotlessApply
    ```
   Spotless enforces the CC0 license header on Java, Groovy, Gradle build scripts, and shell scripts. The metadata/** directory is excluded from header checks.

### Testing one library locally

For a single coordinate, CI runs three steps in this order:
1. Pull Docker images:
    ```console
   ./gradlew pullAllowedDockerImages -Pcoordinates=org.postgresql:postgresql:42.7.3
    ```
2. Validate index.json file integrity and schemas:
    ```console
   ./gradlew validateIndexFiles -Pcoordinates=org.postgresql:postgresql:42.7.3
    ```
2. Validate metadata:
    ```console
   ./gradlew checkMetadataFiles -Pcoordinates=org.postgresql:postgresql:42.7.3
    ```
3. Then run tests:
    ```console
   ./gradlew test -Pcoordinates=org.postgresql:postgresql:42.7.3
   ```

### Testing libraries in bulk

To exercise many tests at once, you can target all coordinates or a slice of the test space via the N/M shard syntax.

All coordinates:
1. Test the whole repo:
    ```console
    ./gradlew pullAllowedDockerImages -Pcoordinates=all
    ./gradlew checkMetadataFiles -Pcoordinates=all
    ./gradlew test -Pcoordinates=all
    ```

2. Splitting the tests in batches (e.g., batch 1 of 16):
    ```console
    ./gradlew pullAllowedDockerImages -Pcoordinates=1/16
    ./gradlew checkMetadataFiles -Pcoordinates=1/16
    ./gradlew test -Pcoordinates=1/16
    ```

### Listing available coordinates

To print all testable GAV coordinates while honoring the same -Pcoordinates filter semantics used by the harness:

```console
./gradlew listCoordinates -Pcoordinates=all
./gradlew listCoordinates -Pcoordinates=group:artifact
./gradlew listCoordinates -Pcoordinates=group:artifact:version
./gradlew listCoordinates -Pcoordinates=1/16
```

In GitHub Actions, this task also writes a space-separated list to the GITHUB_OUTPUT key "coordinates".

### Testing individual stages

Each stage of the testing can be run with `-Pcoordinates=[group:artifact:version|k/n|all]`. Here are the examples:
```console
./gradlew clean -Pcoordinates=[group:artifact:version|k/n|all]
./gradlew pullAllowedDockerImages -Pcoordinates=[group:artifact:version|k/n|all]
./gradlew validateIndexFiles -Pcoordinates=[group:artifact:version|k/n|all]
./gradlew checkMetadataFiles -Pcoordinates=[group:artifact:version|k/n|all]
./gradlew checkstyle -Pcoordinates=[group:artifact:version|k/n|all]
./gradlew compileTestJava -Pcoordinates=[group:artifact:version|k/n|all]
./gradlew javaTest -Pcoordinates=[group:artifact:version|k/n|all]
./gradlew nativeTestCompile -Pcoordinates=[group:artifact:version|k/n|all]
./gradlew test -Pcoordinates=[group:artifact:version|k/n|all]
```

### Coverage (JaCoCo)

Generate coverage for the library under test exercised by our tests. Report contains coverage that focus exclusively on the provided library JARs, excluding unrelated external dependencies.
Report format: XML only.

```console
./gradlew jacocoTestReport -Pcoordinates=[group:artifact:version|k/n|all]
 ```
The root jacocoTestReport is a harness wrapper that invokes the per-project task across matching coordinates.

### Generating Metadata

Generates metadata for a single library coordinate. If `agentAllowedPackages` is provided, a new user-code-filter.json will be created or updated to include those packages.

- `coordinates`: group:artifact:version (single coordinate only)
- `agentAllowedPackages`: comma-separated package list; use `-` for none

Examples:
   ```console
   ./gradlew generateMetadata -Pcoordinates=org.postgresql:postgresql:42.7.3
   ./gradlew generateMetadata -Pcoordinates=org.postgresql:postgresql:42.7.3 --agentAllowedPackages=org.example.app,com.acme.service
   ```

### Fix failing tasks

Use this when a library's new version causes native-image run test failures. The task will:
- Update the artifact's metadata index.json to mark the new version as latest
- Ensure the tests project has an agent block and a user-code-filter.json if missing
- Run the agent to collect metadata, then re-run tests (with a retry if needed)

Required properties:
- -PtestLibraryCoordinates=group:artifact:version (coordinates of an existing tested version whose tests you run)
- -PnewLibraryVersion=version (the new upstream version number only; do not include group or artifact)

Example:
```console
./gradlew fixTestNativeImageRun -PtestLibraryCoordinates=org.postgresql:postgresql:42.7.3 -PnewLibraryVersion=42.7.4
```

### Docker image vulnerability scanning

1. Scan only images affected in a commit range:
   ```console
   ./gradlew checkAllowedDockerImages --baseCommit=$(git rev-parse origin/master) --newCommit=$(git rev-parse HEAD)
   ```

2. Scan all allowed images
   ```console
   ./gradlew checkAllowedDockerImages
   ```

### Compatibility automation with latest library versions

These tasks support the scheduled workflow that checks newer upstream library versions and updates our metadata accordingly.

1. List supported libraries that have newer upstream versions
    ```console
    ./gradlew fetchExistingLibrariesWithNewerVersions --quiet
    ```

2. Mark a new tested version for a library
    ```console
    ./gradlew addTestedVersion -Pcoordinates="group:artifact:newVersion" --lastSupportedVersion="oldVersion"
    ```
    For example:
    ```console
    ./gradlew addTestedVersion -Pcoordinates="org.postgresql:postgresql:42.7.4" --lastSupportedVersion="42.7.3"
    ```

### Releases and Packaging

```console
./gradlew package
```

### Quick reference (copy/paste)

- Style: `./gradlew checkstyle`
- Format check: `./gradlew spotlessCheck`
- Format apply: `./gradlew spotlessApply`
- Pull images (single lib): `./gradlew pullAllowedDockerImages -Pcoordinates=[group:artifact:version|k/n|all]`
- Check metadata (single lib): `./gradlew checkMetadataFiles -Pcoordinates=[group:artifact:version|k/n|all]`
- Generate metadata (single lib): `./gradlew generateMetadata -Pcoordinates=group:artifact:version`
- Fix test that fails Native Image run for new library version: `./gradlew fixTestNativeImageRun -PtestLibraryCoordinates=group:artifact:version -PnewLibraryVersion=version`
- Test (single lib): `./gradlew test -Pcoordinates=[group:artifact:version|k/n|all]`
- Coverage (single lib): `./gradlew jacocoTestReport -Pcoordinates=[group:artifact:version|k/n|all]`
- List available coordinates: `./gradlew listCoordinates -Pcoordinates=[group:artifact:version|group:artifact|k/n|all]`
- Scan changed Docker images: `./gradlew checkAllowedDockerImages --baseCommit=<sha1> --newCommit=<sha2>`
- Scan all Docker images: `./gradlew checkAllowedDockerImages`
- List libs with newer versions: `./gradlew fetchExistingLibrariesWithNewerVersions --quiet`
- Record a newly tested version: `./gradlew addTestedVersion -Pcoordinates="group:artifact:newVersion" --lastSupportedVersion="oldVersion"`
- Package release artifacts: `./gradlew package`
