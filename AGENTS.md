# Development Cheat Sheet

## Prerequisites (assume exists)
- JAVA_HOME set to JDK 21 (GraalVM recommended to match CI)
- Docker
- grype v0.104.0 (install: curl -sSfL https://get.anchore.io/grype/v0.104.0/install.sh | sudo sh -s -- -b /usr/local/bin)

## Setup
- Always use Gradle wrapper from repo root:
  - Unix: ./gradlew <task> [options]
  - Windows: gradlew.bat <task> [options]
- Tip: add --stacktrace for debugging

## One command for complete testing (use at the end of the task to verify)
./gradlew testAllParallel -Pparallelism=4

## Code Style
- Always try to reuse existing code.
- Be assertive in code.
- Write type annotations in all functions and most variables.
- Document code without being too verbose.
- In java, always import classes and use them without qualified names.

## Testing individual components

- Clean previous build outputs for the selected coordinates: ./gradlew clean -Pcoordinates=[group:artifact:version|k/n|all]
- Pre-fetch Docker images allowed by metadata (used in tests) for the selected coordinates: ./gradlew pullAllowedDockerImages -Pcoordinates=[group:artifact:version|k/n|all]
- Validate reachability metadata files for the selected coordinates: ./gradlew checkMetadataFiles -Pcoordinates=[group:artifact:version|k/n|all]
- Run Checkstyle for the selected coordinates: ./gradlew checkstyle -Pcoordinates=[group:artifact:version|k/n|all]
- Compile test sources for the selected coordinates: ./gradlew compileTestJava -Pcoordinates=[group:artifact:version|k/n|all]
- Run JVM-based tests for the selected coordinates: ./gradlew javaTest -Pcoordinates=[group:artifact:version|k/n|all]
- Build native images used by native tests (compile-only) for the selected coordinates: ./gradlew nativeTestCompile -Pcoordinates=[group:artifact:version|k/n|all]
- Run all tests for the selected coordinates: ./gradlew test -Pcoordinates=[group:artifact:version|k/n|all]


## Check style and formatting
- Style check: ./gradlew checkstyle
- Format check: ./gradlew spotlessCheck

## Testing the metadata
- Single library (replace with group:artifact:version):
  - ./gradlew pullAllowedDockerImages -Pcoordinates=group:artifact:version
  - ./gradlew checkMetadataFiles -Pcoordinates=group:artifact:version
  - ./gradlew test -Pcoordinates=group:artifact:version
- Sharded example (1/64):
  - ./gradlew pullAllowedDockerImages -Pcoordinates=1/64
  - ./gradlew checkMetadataFiles -Pcoordinates=1/64
  - ./gradlew test -Pcoordinates=1/64

## Docker Image Vulnerability Scanning
- Changed images between commits:
  - ./gradlew checkAllowedDockerImages --baseCommit=$(git rev-parse origin/master) --newCommit=$(git rev-parse HEAD)
- All allowed images:
  - ./gradlew checkAllowedDockerImages

##s Compatibility Automation (latest library versions)
- List libs with newer upstream versions:
  - ./gradlew fetchExistingLibrariesWithNewerVersions --quiet
- Record a newly tested version:
  - ./gradlew addTestedVersion -Pcoordinates="group:artifact:newVersion" --lastSupportedVersion="oldVersion"
  - Example: ./gradlew addTestedVersion -Pcoordinates="org.postgresql:postgresql:42.7.4" --lastSupportedVersion="42.7.3"

## Releases and Packaging
- Package artifacts: ./gradlew package
