# GraalVM Reachability Metadata Repository

When you use [GraalVM Native Image](https://www.graalvm.org/22.1/reference-manual/native-image/) to build a native executable it only includes the elements reachable from your application entry point, its dependent libraries, and the JDK classes discovered through static analysis. However, the reachability of some elements (such as classes, methods, or fields) may not be discoverable due to Java’s dynamic features including reflection, resource access, dynamic proxies, and serialization. If an element is not reachable, it is not included in the generated executable and this can lead to run time failures.
To include elements whose reachability is undiscoverable, the Native Image builder requires externally provided [reachability metadata](https://www.graalvm.org/22.2/reference-manual/native-image/ReachabilityMetadata/).

This repository exists so we can share the burden of maintaining metadata for libraries in the JVM ecosystem. The repository is integrated with [GraalVM Native Build Tools](https://github.com/graalvm/native-build-tools) beginning with version 0.9.13: you can enable automatic use of the metadata repository for [Gradle projects](https://graalvm.github.io/native-build-tools/latest/gradle-plugin.html#metadata-support)) or for [Maven projects](https://graalvm.github.io/native-build-tools/latest/maven-plugin.html#metadata-support).

### Contributing Metadata

We welcome your contributions. To get started, please take a look at [contributing docs](CONTRIBUTING.md) for more information.
