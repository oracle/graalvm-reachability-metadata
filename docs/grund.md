# GRUND-repository-motivation: Why: repository motivation

The GraalVM Reachability Metadata Repository exists so application developers
can build Native Image applications with community JVM libraries without
hand-writing reachability metadata for each dependency. The repository provides
a shared, curated metadata source that fills the dynamic-access registrations
`native-image` cannot infer statically while preserving the behavior of the
user's application.

The project aims to keep community JVM libraries usable with GraalVM Native
Image through tested metadata, reproducible validation, and reviewable
automation output. Application developers should be able to depend on supported
libraries without custom reachability configuration.

The project is motivated by additivity: metadata supplied by this repository
must help Native Image see the same library behavior the user already depends
on, not patch libraries, run hosted features, change class initialization
semantics, or otherwise make repository metadata part of user program logic.
