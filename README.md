# GraalVM Reachability Metadata Repository

[![Libraries supported](https://img.shields.io/badge/dynamic/json.svg?label=libraries%20supported&query=%24.badges.librariesSupported&url=https%3A%2F%2Fraw.githubusercontent.com%2Foracle%2Fgraalvm-reachability-metadata%2Fstats%2Fcoverage%2Flatest%2Fbadges.json&style=flat-square&color=0A7EC2)](COVERAGE.md)
[![Tested library versions](https://img.shields.io/badge/dynamic/json.svg?label=tested%20library%20versions&query=%24.badges.testedLibraryVersions&url=https%3A%2F%2Fraw.githubusercontent.com%2Foracle%2Fgraalvm-reachability-metadata%2Fstats%2Fcoverage%2Flatest%2Fbadges.json&style=flat-square&color=BF8700)](COVERAGE.md)
[![Dynamic access coverage](https://img.shields.io/badge/dynamic/json.svg?label=dynamic%20access%20coverage&query=%24.badges.dynamicAccessCoverage&url=https%3A%2F%2Fraw.githubusercontent.com%2Foracle%2Fgraalvm-reachability-metadata%2Fstats%2Fcoverage%2Flatest%2Fbadges.json&style=flat-square&color=1F9D55)](COVERAGE.md)
[![Tested lines of code](https://img.shields.io/badge/dynamic/json.svg?label=tested%20lines%20of%20code&query=%24.badges.testedLinesOfCode&url=https%3A%2F%2Fraw.githubusercontent.com%2Foracle%2Fgraalvm-reachability-metadata%2Fstats%2Fcoverage%2Flatest%2Fbadges.json&style=flat-square&color=C2410C)](COVERAGE.md)

This repository provides [reachability metadata](https://www.graalvm.org/latest/reference-manual/native-image/metadata/) that lets Java libraries and frameworks work out of the box with [GraalVM Native Image](https://www.graalvm.org/latest/reference-manual/native-image/). 

To get out-of-the-box support, use the [GraalVM Gradle Plugin](https://graalvm.github.io/native-build-tools/latest/gradle-plugin.html) or the [GraalVM Maven Plugin](https://graalvm.github.io/native-build-tools/latest/maven-plugin.html); they automatically use the [reachability metadata](https://www.graalvm.org/latest/reference-manual/native-image/metadata/) from this repository.

---

## Get Support

<table>
  <tr>
    <td width="50%" valign="top">
      <h3>🔎 Check Support First</h3>
      <p><strong>Use this before opening a ticket.</strong> It checks whether a Maven coordinate is already covered by tested reachability metadata.</p>
      <pre><code>curl -sSL https://raw.githubusercontent.com/oracle/graalvm-reachability-metadata/master/check-library-support.sh | bash -s "&lt;groupId&gt;:&lt;artifactId&gt;:&lt;version&gt;"</code></pre>
      <p>
        <a href="https://github.com/oracle/graalvm-reachability-metadata/issues/new?template=01_support_new_library.yml"><strong>Missing library? Open a new metadata ticket &rarr;</strong></a><br>
        <a href="https://github.com/oracle/graalvm-reachability-metadata/issues/new?template=02_update_existing_library.yml"><strong>Missing version or behavior? Open an update ticket &rarr;</strong></a>
      </p>
    </td>
    <td width="50%" valign="top">
      <h3>📚 Request New Metadata</h3>
      <p><strong>Use this when the library is not listed.</strong> Include the full Maven coordinates and the automation will create the support workflow.</p>
      <p><code>groupId:artifactId:version</code></p>
      <p><a href="https://github.com/oracle/graalvm-reachability-metadata/issues/new?template=01_support_new_library.yml"><strong>Open a library request ticket &rarr;</strong></a></p>
    </td>
  </tr>
  <tr>
    <td width="50%" valign="top">
      <h3>🛠️ Update Existing Metadata</h3>
      <p><strong>Use this when support exists but fails.</strong> Report a failing version, missing dynamic access, stale metadata, or a Native Image runtime issue.</p>
      <p><a href="https://github.com/oracle/graalvm-reachability-metadata/issues/new?template=02_update_existing_library.yml"><strong>Open an update ticket &rarr;</strong></a></p>
    </td>
    <td width="50%" valign="top">
      <h3>✅ Contribute a Fix</h3>
      <p><strong>Use this when you want to send the patch yourself.</strong> Open the tracking ticket first, mark that you want to fix it, then follow the guide.</p>
      <p>
        <a href="https://github.com/oracle/graalvm-reachability-metadata/issues/new?template=02_update_existing_library.yml"><strong>Open the tracking ticket &rarr;</strong></a><br>
        <a href="docs/CONTRIBUTING.md"><strong>Read the contribution guide &rarr;</strong></a>
      </p>
    </td>
  </tr>
</table>

For a broader overview of supported libraries and frameworks, you can visit [this page](https://www.graalvm.org/native-image/libraries-and-frameworks/). It lists libraries and frameworks that are tested and ready for GraalVM Native Image.  

If you’d like yours to appear there as well, open a pull request updating [this JSON file](https://github.com/oracle/graalvm-reachability-metadata/blob/master/metadata/library-and-framework-list.json).
Before submitting a pull request, please read [this guide](docs/CONTRIBUTING.md#tested-libraries-and-frameworks).

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
