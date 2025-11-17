# GraalVM Reachability Metadata Repository

This repository enables users of [GraalVM Native Image](https://www.graalvm.org/latest/reference-manual/native-image/) to share and reuse metadata for libraries and frameworks in the Java ecosystem.
The repository is integrated with [GraalVM Native Build Tools](https://github.com/graalvm/native-build-tools) beginning with version `0.9.13`:
you can enable automatic use of the metadata repository for [Gradle projects](https://graalvm.github.io/native-build-tools/latest/gradle-plugin.html#metadata-support) or [Maven projects](https://graalvm.github.io/native-build-tools/latest/maven-plugin.html#metadata-support).

[This web page](https://www.graalvm.org/native-image/libraries-and-frameworks/) provides an overview of libraries and frameworks that are tested and thus ready for GraalVM Native Image.
If you would like to see your library or framework in the list too, please open a pull request and extend [this JSON file](https://github.com/oracle/graalvm-reachability-metadata/blob/master/library-and-framework-list.json).
Before submitting a pull request, please read [this guide](docs/CONTRIBUTING.md#tested-libraries-and-frameworks).

## Rationale

When you use Native Image to build native executables it only includes the elements reachable from your application entry point, its dependent libraries, and JDK classes discovered through static analysis.
However, the reachability of some elements (such as classes, methods, or fields) may not be discoverable due to Java’s dynamic features including reflection, resource access, dynamic proxies, and serialization.
If an element is not reachable, it is not included in the generated executable at build time, which can lead to failures at run time.
Native Image has built-in metadata for JDK classes but user code and dependencies may use dynamic features of Java that are undiscoverable by the Native Image analysis.
For this reason, Native Image accepts additional [reachability metadata](https://www.graalvm.org/reference-manual/native-image/metadata/) in the form of JSON files.
Since this metadata is specific to a specific code base, the JSON files providing the corresponding metadata can be shared for libraries and frameworks.
This repository is a centralized place for sharing such files for libraries and frameworks that do not provide built-in metadata yet.
It is also used to retrofit metadata for older versions of libraries and frameworks.

If you are a library or framework maintainer, the best way to make your code ready for GraalVM Native Image is to provide reachability metadata as part of your JARs.
Please visit [this web page](https://www.graalvm.org/latest/reference-manual/native-image/metadata/) for more information.

## Contributing

We welcome contributions from the community.
Before submitting a pull request, please [review our contribution guide](docs/CONTRIBUTING.md).

## Further Information

1. Continuous integration is described in [CI.md](docs/CI.md).  
2. PR Reviewing guides are described in [REVIEWING.md](docs/REVIEWING.md).
3. Development is described in [DEVELOPING.md](docs/DEVELOPING.md).  
