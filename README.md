# GraalVM Reachability Metadata Repository

This repository provides [reachability metadata](https://www.graalvm.org/latest/reference-manual/native-image/metadata/) that lets Java libraries and frameworks work out of the box with [GraalVM Native Image](https://www.graalvm.org/latest/reference-manual/native-image/). 

To get out-of-the-box support, use the [GraalVM Gradle Plugin](https://graalvm.github.io/native-build-tools/latest/gradle-plugin.html) or the [GraalVM Maven Plugin](https://graalvm.github.io/native-build-tools/latest/maven-plugin.html); they automatically use the [reachability metadata](https://www.graalvm.org/latest/reference-manual/native-image/metadata/) from this repository.

---

### 🔎 Check if Your Library or Framework Is Supported

To see whether your library or framework is supported, visit [this page](https://www.graalvm.org/native-image/libraries-and-frameworks/). It lists libraries and frameworks that are tested and ready for GraalVM Native Image.  

If you’d like yours to appear there as well, open a pull request updating [this JSON file](https://github.com/oracle/graalvm-reachability-metadata/blob/master/library-and-framework-list.json).  
Before submitting a pull request, please read [this guide](docs/CONTRIBUTING.md#tested-libraries-and-frameworks).

You can also check if metadata for a library exists on the repository directly from your terminal without cloning it using:
```
curl -sSL https://raw.githubusercontent.com/oracle/graalvm-reachability-metadata/master/check-library-support.sh | bash -s "<groupId>:<artifactId>:<version>"
```

### 📚 Request Support for a New Library

Open a [library-request ticket](https://github.com/oracle/graalvm-reachability-metadata/issues/new?template=01_support_new_library.yml), include the Maven coordinates, and our automation will take it from there (🤖).

### 🛠️ Request an Update to an Existing Library

Open an [update ticket](https://github.com/oracle/graalvm-reachability-metadata/issues/new?template=02_update_existing_library.yml) and include the Maven coordinates of the library that needs changes.

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
