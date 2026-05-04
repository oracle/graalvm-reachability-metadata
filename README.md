# GraalVM Reachability Metadata Repository 🤖

[![Libraries supported](https://img.shields.io/badge/dynamic/json.svg?label=libraries%20supported&query=%24.badges.librariesSupported&url=https%3A%2F%2Fraw.githubusercontent.com%2Foracle%2Fgraalvm-reachability-metadata%2Fstats%2Fcoverage%2Flatest%2Fbadges.json&style=flat-square&color=0A7EC2)](COVERAGE.md)
[![Tested library versions](https://img.shields.io/badge/dynamic/json.svg?label=tested%20library%20versions&query=%24.badges.testedLibraryVersions&url=https%3A%2F%2Fraw.githubusercontent.com%2Foracle%2Fgraalvm-reachability-metadata%2Fstats%2Fcoverage%2Flatest%2Fbadges.json&style=flat-square&color=BF8700)](COVERAGE.md)
[![Dynamic access coverage](https://img.shields.io/badge/dynamic/json.svg?label=dynamic%20access%20coverage&query=%24.badges.dynamicAccessCoverage&url=https%3A%2F%2Fraw.githubusercontent.com%2Foracle%2Fgraalvm-reachability-metadata%2Fstats%2Fcoverage%2Flatest%2Fbadges.json&style=flat-square&color=1F9D55)](COVERAGE.md)
[![Tested lines of code](https://img.shields.io/badge/dynamic/json.svg?label=tested%20lines%20of%20code&query=%24.badges.testedLinesOfCode&url=https%3A%2F%2Fraw.githubusercontent.com%2Foracle%2Fgraalvm-reachability-metadata%2Fstats%2Fcoverage%2Flatest%2Fbadges.json&style=flat-square&color=C2410C)](COVERAGE.md)

This repository contains [reachability metadata](https://www.graalvm.org/latest/reference-manual/native-image/metadata/) for Java libraries and frameworks, enabling them to work out of the box with [GraalVM Native Image](https://www.graalvm.org/latest/reference-manual/native-image/).

The [GraalVM Gradle Plugin](https://graalvm.github.io/native-build-tools/latest/gradle-plugin.html) and [GraalVM Maven Plugin](https://graalvm.github.io/native-build-tools/latest/maven-plugin.html) use this [reachability metadata](https://www.graalvm.org/latest/reference-manual/native-image/metadata/) automatically, so most projects do not need to configure this repository directly.

If metadata is missing for a library you depend on, run the Native Build Tools task in your project to detect the gap and automatically open an issue here:

**Gradle**

```bash
./gradlew listLibrariesMissingMetadata -PcreateIssues=true
```

**Maven**

```bash
./mvnw native:list-libraries-missing-metadata -DcreateIssues=true
```

---

### Manually Request Library Support

<p align="center">
  <a href="https://github.com/oracle/graalvm-reachability-metadata/issues/new?template=01_support_new_library.yml"><img alt="Open a new library support ticket" src="docs/assets/readme/button-new-library.svg" width="340"></a>
  <a href="https://github.com/oracle/graalvm-reachability-metadata/issues/new?template=02_update_existing_library.yml"><img alt="Open an existing library update ticket" src="docs/assets/readme/button-update-metadata.svg" width="340"></a>
</p>

### 🔎 Check if Your Library Is Supported

To quickly check whether reachability metadata exists for a specific library, run this command directly from your terminal:

```bash
curl -sSL \
  https://raw.githubusercontent.com/oracle/graalvm-reachability-metadata/master/check-library-support.sh \
  | bash -s "<groupId>:<artifactId>:<version>"
```

---

### Contributing

We welcome contributions from the community. Thank you!

Before submitting a pull request, please [open a ticket](https://github.com/oracle/graalvm-reachability-metadata/issues/new?template=02_update_existing_library.yml) and [review our contribution guide](docs/CONTRIBUTING.md).

### Further Information

1. Continuous integration is described in [CI.md](docs/CI.md).
2. Pull request review guidelines are in [REVIEWING.md](docs/REVIEWING.md).
3. Development workflow is described in [DEVELOPING.md](docs/DEVELOPING.md).

---
Built with ❤️ by the community and the [GraalVM](https://www.graalvm.org/), [Spring](https://spring.io/projects/spring-boot), and [Micronaut](https://micronaut.io/) teams.
