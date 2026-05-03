# GraalVM Reachability Metadata Repository

[![Libraries supported](https://img.shields.io/badge/dynamic/json.svg?label=libraries%20supported&query=%24.badges.librariesSupported&url=https%3A%2F%2Fraw.githubusercontent.com%2Foracle%2Fgraalvm-reachability-metadata%2Fstats%2Fcoverage%2Flatest%2Fbadges.json&style=flat-square&color=0A7EC2)](COVERAGE.md)
[![Tested library versions](https://img.shields.io/badge/dynamic/json.svg?label=tested%20library%20versions&query=%24.badges.testedLibraryVersions&url=https%3A%2F%2Fraw.githubusercontent.com%2Foracle%2Fgraalvm-reachability-metadata%2Fstats%2Fcoverage%2Flatest%2Fbadges.json&style=flat-square&color=BF8700)](COVERAGE.md)
[![Dynamic access coverage](https://img.shields.io/badge/dynamic/json.svg?label=dynamic%20access%20coverage&query=%24.badges.dynamicAccessCoverage&url=https%3A%2F%2Fraw.githubusercontent.com%2Foracle%2Fgraalvm-reachability-metadata%2Fstats%2Fcoverage%2Flatest%2Fbadges.json&style=flat-square&color=1F9D55)](COVERAGE.md)
[![Tested lines of code](https://img.shields.io/badge/dynamic/json.svg?label=tested%20lines%20of%20code&query=%24.badges.testedLinesOfCode&url=https%3A%2F%2Fraw.githubusercontent.com%2Foracle%2Fgraalvm-reachability-metadata%2Fstats%2Fcoverage%2Flatest%2Fbadges.json&style=flat-square&color=C2410C)](COVERAGE.md)

This repository provides [reachability metadata](https://www.graalvm.org/latest/reference-manual/native-image/metadata/) that lets Java libraries and frameworks work out of the box with [GraalVM Native Image](https://www.graalvm.org/latest/reference-manual/native-image/). 

To get out-of-the-box support, use the [GraalVM Gradle Plugin](https://graalvm.github.io/native-build-tools/latest/gradle-plugin.html) or the [GraalVM Maven Plugin](https://graalvm.github.io/native-build-tools/latest/maven-plugin.html); they automatically use the [reachability metadata](https://www.graalvm.org/latest/reference-manual/native-image/metadata/) from this repository.

---

## Get Support

<p align="center">
  <strong>Choose the fastest path to the right ticket.</strong><br>
  <sub>These GitHub buttons route to the issue template our automation expects.</sub>
</p>

<table>
  <tr>
    <td width="25%" align="center">
      <a href="#check-if-your-library-or-framework-is-supported"><img alt="Start with the support checker" src="https://img.shields.io/badge/1-Check%20support-0A7EC2?style=for-the-badge"></a><br>
      <sub>Find the right next step</sub>
    </td>
    <td width="25%" align="center">
      <a href="https://github.com/oracle/graalvm-reachability-metadata/issues/new?template=01_support_new_library.yml"><img alt="Open a new library support ticket" src="https://img.shields.io/badge/2-New%20library-1F9D55?style=for-the-badge"></a><br>
      <sub>Library is missing</sub>
    </td>
    <td width="25%" align="center">
      <a href="https://github.com/oracle/graalvm-reachability-metadata/issues/new?template=02_update_existing_library.yml"><img alt="Open an existing library update ticket" src="https://img.shields.io/badge/3-Update%20metadata-BF8700?style=for-the-badge"></a><br>
      <sub>Support exists but fails</sub>
    </td>
    <td width="25%" align="center">
      <a href="#contribute-a-fix"><img alt="Open a contribution tracking ticket" src="https://img.shields.io/badge/4-Contribute%20a%20fix-6F42C1?style=for-the-badge"></a><br>
      <sub>Send the patch yourself</sub>
    </td>
  </tr>
</table>

### 🔎 Check if Your Library or Framework Is Supported

To quickly check whether reachability metadata exists for a specific library, run this command directly from your terminal:

```bash
curl -sSL https://raw.githubusercontent.com/oracle/graalvm-reachability-metadata/master/check-library-support.sh | bash -s "<groupId>:<artifactId>:<version>"
```

<table>
  <tr>
    <td width="50%" align="center">
      <strong>Checker says the library is missing?</strong><br><br>
      <a href="https://github.com/oracle/graalvm-reachability-metadata/issues/new?template=01_support_new_library.yml">
        <img alt="Open new metadata ticket" src="https://img.shields.io/badge/Open-new%20metadata%20ticket-0A7EC2?style=for-the-badge">
      </a>
    </td>
    <td width="50%" align="center">
      <strong>Checker finds support, but your build fails?</strong><br><br>
      <a href="https://github.com/oracle/graalvm-reachability-metadata/issues/new?template=02_update_existing_library.yml">
        <img alt="Open update ticket" src="https://img.shields.io/badge/Open-update%20ticket-BF8700?style=for-the-badge">
      </a>
    </td>
  </tr>
</table>

For a broader overview of supported libraries and frameworks, you can visit [this page](https://www.graalvm.org/native-image/libraries-and-frameworks/). It lists libraries and frameworks that are tested and ready for GraalVM Native Image.  

If you’d like yours to appear there as well, open a pull request updating [this JSON file](https://github.com/oracle/graalvm-reachability-metadata/blob/master/metadata/library-and-framework-list.json).
Before submitting a pull request, please read [this guide](docs/CONTRIBUTING.md#tested-libraries-and-frameworks).

### 📚 Request Support for a New Library

Open a library-request ticket, include the Maven coordinates, and the automation will take it from there.

<p align="center">
  <a href="https://github.com/oracle/graalvm-reachability-metadata/issues/new?template=01_support_new_library.yml">
    <img alt="Request support for a new library" src="https://img.shields.io/badge/Request%20support-New%20library%20ticket-1F9D55?style=for-the-badge">
  </a>
</p>

### 🛠️ Request an Update to an Existing Library

Open an update ticket and include the Maven coordinates of the library version that needs changes.

<p align="center">
  <a href="https://github.com/oracle/graalvm-reachability-metadata/issues/new?template=02_update_existing_library.yml">
    <img alt="Request an update to existing metadata" src="https://img.shields.io/badge/Request%20an%20update-Existing%20metadata%20ticket-C2410C?style=for-the-badge">
  </a>
</p>

### ✅ Contribute a Fix

Open the tracking ticket first, mark that you want to fix it, then follow the contribution guide.

<table>
  <tr>
    <td width="50%" align="center">
      <a href="https://github.com/oracle/graalvm-reachability-metadata/issues/new?template=02_update_existing_library.yml">
        <img alt="Open tracking ticket" src="https://img.shields.io/badge/Open-tracking%20ticket-6F42C1?style=for-the-badge">
      </a>
    </td>
    <td width="50%" align="center">
      <a href="docs/CONTRIBUTING.md">
        <img alt="Read the contribution guide" src="https://img.shields.io/badge/Read-contribution%20guide-24292F?style=for-the-badge">
      </a>
    </td>
  </tr>
</table>

---

### Contributing

We welcome contributions from the community. Thank you!

Before submitting a pull request, please [open a ticket](https://github.com/oracle/graalvm-reachability-metadata/issues/new?template=02_update_existing_library.yml), mark that you want to fix it yourself, and [review our contribution guide](docs/CONTRIBUTING.md).

### Further Information

1. Continuous integration is described in [CI.md](docs/CI.md).
2. Pull request review guidelines are in [REVIEWING.md](docs/REVIEWING.md).
3. Development workflow is described in [DEVELOPING.md](docs/DEVELOPING.md).

---
Built with love by the community and the [GraalVM](https://www.graalvm.org/), [Spring](https://spring.io/projects/spring-boot), and [Micronaut](https://micronaut.io/) teams.
