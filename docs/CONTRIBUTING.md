# Contributing

Before contributing to this repository, please consider including reachability metadata
directly in the library or the framework (see an [example](https://github.com/netty/netty/pull/12738/files)).
This is the best way to provide support for GraalVM Native Image as it makes an out-of-the-box experience for users (no additional work required) and allows you to continuously test and maintain the metadata as part of your project.
If that is not an option (for example, you are not a maintainer), we encourage you to open a ticket on the issue tracker
of the corresponding library or framework, so that the community can up-vote and discuss the inclusion of reachability metadata
(see an [example](https://github.com/h2database/h2database/issues/3606))

## How to Test or Use This Repository Locally

You can test the reachability metadata from this repository locally against your project or with additional changes.

First, clone the repository:
```shell
git clone git@github.com:oracle/graalvm-reachability-metadata.git
```
Then, point to the local repository in the configuration of either
[Gradle](https://graalvm.github.io/native-build-tools/latest/gradle-plugin.html#metadata-support) or
[Maven](https://graalvm.github.io/native-build-tools/latest/maven-plugin.html#metadata-support)

## Contribute Metadata

The suggested way to contribute to Reachability metadata repository is by using the `contribute` task.
Before you start the task, you should have your tests implemented somewhere locally on your machine (please verify that tests are working on Java).

In order to start this task, run (we suggest using `--console=plain` to reduce amount of gradle logging):

```shell
./gradlew contribute --console=plain
```

When started, the task will ask you few simple questions (like: where are your tests implemented, do your tests need resources, do your tests need docker images...).
In case you don't understand the question, just type "help".

After it collects your answers, the task will:
- generate necessary boilerplate code
- copy your tests to the proper location
- generate metadata and store it in the proper location
- ask you if you want to create a pull request, or you want to keep working on it locally

If you already have the test project structure in this repository and need to generate or regenerate metadata, use the `generateMetadata` task:

```shell
./gradlew generateMetadata --coordinates=com.example:my-library:1.0.0
```

To change the user-code-filter used during collection, pass `--allowedPackages` with a comma-separated list of packages:

```shell
./gradlew generateMetadata --coordinates=com.example:my-library:1.0.0 --allowedPackages=com.example.pkg,org.acme.lib
```

### Checklist
In order to ensure that all contributions follow the same standards of quality we have devised a following list of requirements for each new added library.
`org.example:library` project is also included as a template for new libraries.

* GraalVM Reachability Metadata in this repo only contains JSON files as described
in [Manual Configuration](https://www.graalvm.org/latest/reference-manual/native-image/dynamic-features/Reflection/#manual-configuration)
section of the Native Image documentation.
* All other library tweaks (such as build time initialization through `native-image.properties`) must not be included
here. By default, it should be assumed that all user libraries are runtime initialized. Build-time initialization can
not be included as it does not compose and can break code in unpredictable ways.
* Make sure that you are using [Conditional Configuration](https://www.graalvm.org/latest/reference-manual/native-image/metadata/#specifying-reflection-metadata-in-json)
in order to precisely define the metadata scope. This is a hard requirement as it prevents unnecessary bloating of
images.
* Once you want to create a pull request, you will be asked to fill out the [following list](../.github/pull_request_template.md).

ℹ️ To learn more about collecting metadata, see [How To Collect Metadata](CollectingMetadata.md).

### Generate Metadata and Test

Use the `scaffold` task to generate metadata and test stubs:

```bash
./gradlew scaffold --coordinates com.example:my-library:1.0.0
```

You can now run

```bash
./gradlew test -Pcoordinates=com.example:my-library:1.0.0
```

to execute the tests.

It's expected that they fail, because the scaffold task only generated a stub which you need to implement.

### Metadata structure

Metadata lives in a folder structure in the `metadata` directory in root of this repository.
Per convention, it should be like this: `org.example:library` metadata should be located
at `metadata/org.example/library`.
Every metadata has an entry in the `metadata/index.json`. For example:

```json
[
  ...
  {
    "directory": "org.example/library",
    "module": "org.example:library"
  },
  {
    "module": "org.example:dependant-library",
    "requires": [
      "org.example:library"
    ],
    "allowed-packages": [
       "org.package.name"
     ]
  }
]
```

**Note:** `dependant-library` can feature its own metadata as well if `directory` key is specified.

**Note:** `allowed-packages` describes which packages are expected to contain metadata entries. This way you can prevent metadata from other libraries to be
pulled into your config files

Every library metadata has another `index.json` file.
In aforementioned case that would be `metadata/org.example/library/index.json`.
It should contain the following entries:

```json
[
  {
    "metadata-version": "0.0.1",
    "module": "org.example:library",
    "tested-versions": [
      "0.0.1",
      "0.0.2"
    ]
  },
  {
    "latest": true,
    "metadata-version": "1.0.0",
    "module": "org.example:library",
    "tested-versions": [
      "1.0.0",
      "1.1.0-M1",
      "1.1.0"
    ]
  },
  ...
]
```

The `metadata-version` key specifies the subdirectory where metadata for tested versions "lives".
The `override` flag allows to express the intent to exclude outdated builtin metadata when set to `true`.
So, the metadata for `org.example:library:0.0.1` and `org.example:library:0.0.2` is located
at `metadata/org.example/library/0.0.1`.

For entries without `"latest": true`, it is recommended to define the optional `default-for` key with a value containing
a regexp (Java format) matching the version pattern. For example, for the example above, the first entry could be:

```json
{
   "metadata-version": "0.0.1",
   "module": "org.example:library",
   "tested-versions": [
      "0.0.1",
      "0.0.2"
   ],
   "default-for": "0\\.0\\..*"
}
```

The optional `test-version` key can be used to explicitly define the subdirectory containing 
the test code for a library version. This is useful when multiple versions of a library's metadata 
share the same tests, allowing for code reuse and preventing test code duplication. 
An example of the library above using this field:

```json
{
  "metadata-version": "1.1.0",
  "module": "org.example:library",
  "test-version": "1.0.0",
  "tested-versions": [
    "1.1.0",
    "1.1.1",
    "1.1.2"
  ]
}
```

The optional `skipped-versions` key can be used to explicitly exclude specific
library versions from being considered as supported metadata. A skipped version
should include both the version and the reason it is excluded. This is useful
when a version exists in Maven but is known to be broken, incompatible, or
should be omitted from testing for any other reason. An example of the library
above using this field:

```json
{
  "metadata-version": "1.0.0",
  "module": "org.example:library",
  "tested-versions": [
    "1.0.0",
    "1.1.0"
  ],
  "skipped-versions": [
    {
      "version": "1.0.5",
      "reason": "Known incompatible API change."
    },
    {
      "version": "1.0.7",
      "reason": "Integrated reflect-config.json does not parse."
    }
  ]
}
```

You can also list each supported version is listed in `tested-versions`, as that value is used in build tools to match
metadata to a specific library, but this is more likely to break when new versions are released.

### Format Metadata Files

Metadata must be correctly formatted.
This can be done by running following command from root of the repository, and then following instructions from command
output if necessary:

```bash
./gradlew check
```

## Tests

> **Note:** Contributors must be original authors of all the tests provided in the pull request, or
must add a comment that proves they may publish them under the license specified in those tests.

Every submitted library must feature tests that serve as a safeguard against regressions.
For easier test development we've provided a TCK plugin that automatically configures
our [native-gradle-plugin](https://graalvm.github.io/native-build-tools/latest/gradle-plugin.html)
and its
included [JUnit Platform support](https://graalvm.github.io/native-build-tools/latest/gradle-plugin.html#testing-support)
.

The location of the test code is determined by the library version being tested:

By default, the location of the test code directory mirrors the structure of the metadata directory.
For metadata located at `metadata/<group_id>/<artifact_id>/<version>`, the corresponding test code is expected to be at `tests/src/<group_id>/<artifact_id>/<version>`.
This path can be overridden to allow multiple metadata versions to share the same set of test code. This mapping is handled by the local metadata index file (`metadata/<group_id>/<artifact_id>/index.json`).
If an entry in this index contains the optional `test-version` field, the test code is resolved to the path: `tests/src/<group_id>/<artifact_id>/<test-version>`.

### Executing the tests

In this example this can be done by invoking following command from the repository root:

```bash
./gradlew test -Pcoordinates=org.example:library:0.0.1
```

### Providing the tests that use docker

If your tests use Docker (either with explicit Docker process invocation or through some library method call), all images have to be declared in `required-docker-images` file.
This file must be placed under `/tests/src/<groupId>/<artifactId>/<versionId>`.

Only Docker images that are listed in the [`allowed-docker-images` directory](https://github.com/oracle/graalvm-reachability-metadata/blob/master/tests/tck-build-logic/src/main/resources/allowed-docker-images) can be used for testing.
If you want to extend this list, please create separate pull request to do that.
That pull request should add a new file in the [`allowed-docker-images` directory](https://github.com/oracle/graalvm-reachability-metadata/blob/master/tests/tck-build-logic/src/main/resources/allowed-docker-images)
with the name in the format `Dockerfile-<dockerImageName>` (replace all occurrence of `/` with `_`) .
The only line that this file needs to contain is `FROM <dockerImageName>`.
Once you have opened such a pull request, please post the result of the following command in your pull request description:

```shell
grype <dockerImageName>
```

Possible scenarios:
   * If your test uses Docker image, and you didn't specify it in the `required-docker-images.txt` file, the test will fail.
   * If your test uses Docker image that is not listed in [allowed docker images list](https://github.com/oracle/graalvm-reachability-metadata/blob/master/tests/tck-build-logic/src/main/resources/AllowedDockerImages.txt),
   the test will fail
   * Only docker images that are in both `required-docker-images.txt` and in the `allowed docker images list`
   can be executed.

**Note:** For images that comes from Oracle, please consider using them from the official [Oracle Container Registry](https://container-registry.oracle.com).
See an [example](https://github.com/oracle/graalvm-reachability-metadata/blob/master/tests/tck-build-logic/src/main/resources/allowed-docker-images/Dockerfile-mysql_mysql-server).

## Tested Libraries and Frameworks

If your library or framework is tested with GraalVM Native Image, consider adding it to [this list](https://github.com/oracle/graalvm-reachability-metadata/blob/master/library-and-framework-list.json).

Write an entry as follows:
```json
{
    "artifact": "<groupId>:<artifactId>",
    "description": "<artifactDescription>",
    "details": [
      {
        "minimum_version": "<minimumVersion>",
        "maximum_version": "<maximumVersion>",
        "metadata_locations": ["<metadataLocations>"],
        "tests_locations": ["<testLocations>"],
        "test_level": "<testLevel>"
      }
    ]
}
```
Where:
 * `<groupId>` and `<artifactId>` - part of the standard Maven coordinates ([see this](https://maven.apache.org/pom.html#Maven_Coordinates))
 * `<artifactDescription>` - short description of the library or framework (_not required_)
 * `<minimumVersion>` - minimal version for which this entry applies
 * `<maximumVersion>` - maximal version for which this entry applies (_not required_)
 * `<metadataLocations>` - list of web URLs providing metadata
 * `<testLocations>` - list of URLs to test sources, CI dashboards, etc.
 * `<testLevel>` - one of the following values:
   * untested (there are no provided tests that can confirm library usage with Native Image)
   * community-tested (the library is partially tested through some project, e.g. [Reachability Metadata Repository](https://github.com/oracle/graalvm-reachability-metadata/tree/master/tests/src))
   * fully-tested (the library is fully tested for each released library version)

**Note:** To pass format and style checks, please run `sorted="$(jq -s '.[] | sort_by(.artifact)' library-and-framework-list.json)" && echo -E "${sorted}" > library-and-framework-list.json` before submitting a PR.

**Note:** The entries you add will be validated against [library-and-framework-list-schema-v1.0.0.json](https://github.com/oracle/graalvm-reachability-metadata/blob/master/schemas/library-and-framework-list-schema-v1.0.0.json)
