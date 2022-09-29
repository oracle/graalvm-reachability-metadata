# GraalVM Reachability Metadata Repository

When you use [GraalVM Native Image](https://www.graalvm.org/22.1/reference-manual/native-image/) to build a native executable it only includes the elements reachable from your application entry point, its dependent libraries, and the JDK classes discovered through static analysis. However, the reachability of some elements (such as classes, methods, or fields) may not be discoverable due to Java’s dynamic features including reflection, resource access, dynamic proxies, and serialization. If an element is not reachable, it is not included in the generated executable and this can lead to run time failures.
To include elements whose reachability is undiscoverable, the Native Image builder requires externally provided [reachability metadata](https://www.graalvm.org/reference-manual/native-image/metadata/).

The GraalVM Reachability Metadata Repository enables Native Image users to share and reuse metadata for libraries and frameworks in the Java ecosystem, and thus simplify maintaining third-party dependencies. The repository is integrated with [GraalVM Native Build Tools](https://github.com/graalvm/native-build-tools) beginning with version 0.9.13: you can enable automatic use of the metadata repository for [Gradle projects](https://graalvm.github.io/native-build-tools/latest/gradle-plugin.html#metadata-support) or for [Maven projects](https://graalvm.github.io/native-build-tools/latest/maven-plugin.html#metadata-support).

### Contributing

We welcome contributions from the community. Before submitting a pull
request, please [review our contribution guide](./CONTRIBUTING.md).
