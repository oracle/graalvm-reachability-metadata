# GraalVM Reachability Metadata Repository <img src="docs/assets/readme/title-robot-facing-user.png" height="64" align="middle" style="vertical-align: middle; margin-bottom: 28px">

<p><a href="COVERAGE.md"><picture><source media="(prefers-color-scheme: dark)" srcset="https://img.shields.io/badge/dynamic/json.svg?label=supported%20libraries&query=%24.badges.librariesSupported&url=https%3A%2F%2Fraw.githubusercontent.com%2Foracle%2Fgraalvm-reachability-metadata%2Fstats%2Fcoverage%2Flatest%2Fbadges.json&style=flat&color=38BDF8&labelColor=1F2937"><img alt="Supported libraries" src="https://img.shields.io/badge/dynamic/json.svg?label=supported%20libraries&query=%24.badges.librariesSupported&url=https%3A%2F%2Fraw.githubusercontent.com%2Foracle%2Fgraalvm-reachability-metadata%2Fstats%2Fcoverage%2Flatest%2Fbadges.json&style=flat&color=0A7EC2&labelColor=E5E7EB"></picture></a>&nbsp;&nbsp;<a href="COVERAGE.md"><picture><source media="(prefers-color-scheme: dark)" srcset="https://img.shields.io/badge/dynamic/json.svg?label=tested%20library%20versions&query=%24.badges.testedLibraryVersions&url=https%3A%2F%2Fraw.githubusercontent.com%2Foracle%2Fgraalvm-reachability-metadata%2Fstats%2Fcoverage%2Flatest%2Fbadges.json&style=flat&color=F59E0B&labelColor=1F2937"><img alt="Tested library versions" src="https://img.shields.io/badge/dynamic/json.svg?label=tested%20library%20versions&query=%24.badges.testedLibraryVersions&url=https%3A%2F%2Fraw.githubusercontent.com%2Foracle%2Fgraalvm-reachability-metadata%2Fstats%2Fcoverage%2Flatest%2Fbadges.json&style=flat&color=BF8700&labelColor=E5E7EB"></picture></a>&nbsp;&nbsp;<a href="COVERAGE.md"><picture><source media="(prefers-color-scheme: dark)" srcset="https://img.shields.io/badge/dynamic/json.svg?label=dynamic%20access%20coverage&query=%24.badges.dynamicAccessCoverage&url=https%3A%2F%2Fraw.githubusercontent.com%2Foracle%2Fgraalvm-reachability-metadata%2Fstats%2Fcoverage%2Flatest%2Fbadges.json&style=flat&color=34D399&labelColor=1F2937"><img alt="Dynamic access coverage" src="https://img.shields.io/badge/dynamic/json.svg?label=dynamic%20access%20coverage&query=%24.badges.dynamicAccessCoverage&url=https%3A%2F%2Fraw.githubusercontent.com%2Foracle%2Fgraalvm-reachability-metadata%2Fstats%2Fcoverage%2Flatest%2Fbadges.json&style=flat&color=1F9D55&labelColor=E5E7EB"></picture></a>&nbsp;&nbsp;<a href="COVERAGE.md"><picture><source media="(prefers-color-scheme: dark)" srcset="https://img.shields.io/badge/dynamic/json.svg?label=tested%20LoC&query=%24.badges.testedLinesOfCode&url=https%3A%2F%2Fraw.githubusercontent.com%2Foracle%2Fgraalvm-reachability-metadata%2Fstats%2Fcoverage%2Flatest%2Fbadges.json&style=flat&color=FB923C&labelColor=1F2937"><img alt="Tested LoC" src="https://img.shields.io/badge/dynamic/json.svg?label=tested%20LoC&query=%24.badges.testedLinesOfCode&url=https%3A%2F%2Fraw.githubusercontent.com%2Foracle%2Fgraalvm-reachability-metadata%2Fstats%2Fcoverage%2Flatest%2Fbadges.json&style=flat&color=C2410C&labelColor=E5E7EB"></picture></a></p>

This repository contains [reachability metadata](https://www.graalvm.org/latest/reference-manual/native-image/metadata/) for JVM libraries and frameworks, enabling them to work out of the box with [GraalVM Native Image](https://www.graalvm.org/latest/reference-manual/native-image/).

The [GraalVM Gradle Plugin](https://graalvm.github.io/native-build-tools/latest/gradle-plugin.html) and [GraalVM Maven Plugin](https://graalvm.github.io/native-build-tools/latest/maven-plugin.html) automatically use the [reachability metadata](https://www.graalvm.org/latest/reference-manual/native-image/metadata/) from this repository, so most projects do not need to configure it directly.

> [!TIP]
> **Missing metadata for a library you depend on?** Run the [Native Build Tools](https://graalvm.github.io/native-build-tools/latest/) task in your project to detect the gap and automatically open an issue here.
>
> **Gradle**
>
> ```bash
> ./gradlew listLibrariesMissingMetadata -PcreateIssues=true
> ```
>
> **Maven**
>
> ```bash
> ./mvnw native:list-libraries-missing-metadata -DcreateIssues=true
> ```

---

### 🔎 Check if Your Library Is Supported

To quickly check whether reachability metadata exists for a specific library, run this command directly from your terminal:

```bash
curl -sSL \
  https://raw.githubusercontent.com/oracle/graalvm-reachability-metadata/master/check-library-support.sh \
  | bash -s "<groupId>:<artifactId>:<version>"
```

### 🙋 Manually Request Library Support

<p align="center">
  <a href="https://github.com/oracle/graalvm-reachability-metadata/issues/new?template=01_support_new_library.yml"><picture><source media="(prefers-color-scheme: dark)" srcset="docs/assets/readme/button-new-library-dark.png"><img alt="Open a new library support ticket" src="docs/assets/readme/button-new-library-light.png" width="400"></picture></a>
  <a href="https://github.com/oracle/graalvm-reachability-metadata/issues/new?template=02_update_existing_library.yml"><picture><source media="(prefers-color-scheme: dark)" srcset="docs/assets/readme/button-update-metadata-dark.png"><img alt="Open an existing library update ticket" src="docs/assets/readme/button-update-metadata-light.png" width="400"></picture></a>
</p>

---

### Contributing

We welcome contributions from the community. Thank you!

Before submitting a pull request, please [open a ticket](https://github.com/oracle/graalvm-reachability-metadata/issues/new?template=02_update_existing_library.yml) and [review our contribution guide](docs/CONTRIBUTING.md).

> [!NOTE]
> We always fix issues by changing the system, not the problem itself. Tests are updated only very rarely, and the metadata itself is never modified by hand.

### Further Information

1. Continuous integration is described in [CI.md](docs/CI.md).
2. Pull request review guidelines are in [REVIEWING.md](docs/REVIEWING.md).
3. Development workflow is described in [DEVELOPING.md](docs/DEVELOPING.md).

---
Built with ❤️ by the community and the [GraalVM](https://www.graalvm.org/), [Spring](https://spring.io/projects/spring-boot), and [Micronaut](https://micronaut.io/) teams.
