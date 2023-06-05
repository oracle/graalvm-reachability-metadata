# Contributing

Before contributing to this repository, please consider including reachability metadata directly in the library or the framework.
This is the best way to provide support for GraalVM Native Image as it makes for an out-of-the-box experience for users (no additional work required) and allows you to continuously test and maintain the metadata as part of your project.
If that does not work, open a ticket on the target library issue tracker so the community can upvote and discuss metadata addition.

## How to Test or Use This Repository Locally

You can test the reachability metadata from this repository locally against your project or with additional changes.

First, you clone the repository:
```shell
git clone git@github.com:oracle/graalvm-reachability-metadata.git
```
Then, you adjust the configuration of local repository in either
[Gradle](https://graalvm.github.io/native-build-tools/latest/gradle-plugin.html#metadata-support) or
[Maven](https://graalvm.github.io/native-build-tools/latest/maven-plugin.html#metadata-support)

## Contribute Metadata

### Checklist
In order to ensure that all contributions follow the same standards of quality we have devised a following list of requirements for each new added library.
`org.example:library` project is also included as a template for new libraries.

> ℹ️ **Note** :
>
> GraalVM Reachability Metadata in this repo only contains JSON files as described
> in [Manual Configuration](https://www.graalvm.org/latest/reference-manual/native-image/dynamic-features/Reflection/#manual-configuration)
> section of the Native Image documentation.
>
>  All other library tweaks (such as build time initialization through `native-image.properties`) must not be included
> here. By default, it should be assumed that all user libraries are runtime initialized. Build-time initialization can
> not be included as it does not compose and can break code in unpredictable ways.
>
> Make sure that you are
> using [Conditional Configuration](https://www.graalvm.org/latest/reference-manual/native-image/metadata/#specifying-reflection-metadata-in-json)
> in order to precisely define the metadata scope. This is a hard requirement as it prevents unnecessary bloating of
> images.
>
> To learn more about collecting metadata, see [How To Collect Metadata](docs/CollectingMetadata.md).

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

You can also list each supported version is listed in `tested-versions`, as that value is used in build tools to match
metadata to a specific library, but this is more likely to break when new versions are released.
Every metadata for a specific library version has a `index.json`. For this
example `metadata/org.example/library/0.0.1/index.json` would contain:

```json
[
  "jni-config.json",
  "proxy-config.json",
  "reflect-config.json",
  "resource-config.json"
]
```

Metadata must be correctly formatted.
This can be done by running following command from root of the repository, and then following instructions from command
output if necessary:

```bash
./gradlew check
```

## Tests

Every submitted library must feature tests that serve as a safeguard against regressions.
For easier test development we've provided a TCK plugin that automatically configures
our [native-gradle-plugin](https://graalvm.github.io/native-build-tools/latest/gradle-plugin.html)
and its
included [JUnit Platform support](https://graalvm.github.io/native-build-tools/latest/gradle-plugin.html#testing-support)
.

All tests are referenced in `tests/src/index.json`. It should look something like this:

```json
[
  {
    "test-project-path": "org.example/library/0.0.1",
    "libraries": [
      {
        "name": "org.example:library",
        "versions": [
          "0.0.1",
          "0.0.2"
        ]
      }
    ]
  },
  {
    "test-project-path": "org.example/library/1.0.0",
    "libraries": [
      {
        "name": "org.example:library",
        "versions": [
          "1.0.0",
          "1.1.0-M1",
          "1.1.0"
        ]
      }
    ]
  },
  ...
]
```

The test code lives in `test-project-path`. In this example that would be `tests/src/org.example/library/0.0.1`.

**Optionally** test directory may contain `index.json` with content as follows:

```json
{
  "test-command": [
    "gradle",
    "clean",
    "nativeTest",
    "-Pmetadata.dir=<metadata_dir>",
    "-Plibrary.version=<version>"
  ]
}
``` 

Supported template parameters for `test-command` are:

* `<metadata_dir>` - absolute path to directory where metadata is stored
* `<group_id>` - Maven group ID of artifact that is being tested
* `<artifact_id>`- Maven artifact ID of artifact that is being tested
* `<version>` - Version of artifact that is being tested

**Note that if `index.json` is omitted `gradle nativeTest` is executed by default.**

#### Executing the tests

In this example this can be done by invoking following command from the repository root:

```bash
./gradlew test -Pcoordinates=org.example:library:0.0.1
```
 
### Providing the tests that use docker
 
If your tests use docker (either with explicit docker process invocation or through some library method call), all images 
have to be declared in `required-docker-images.txt` file. This file must be placed under `/tests/src/<groupId>/<artifactId>/<versionId>`.

Only docker images that are listed [here](https://github.com/oracle/graalvm-reachability-metadata/blob/master/tests/tck-build-logic/src/main/resources/AllowedDockerImages.txt)
can be executed. If you want to extend this list, please create separate pull request to do that, and post the result of the following command on your pull request:

```shell
grype <dockerImageName>
```

Possible scenarios:
   * If your test uses docker image, and you didn't specify it in the `required-docker-images.txt` file, the test will fail.
   * If your test uses docker image that is not listed in [allowed docker images list](https://github.com/oracle/graalvm-reachability-metadata/blob/master/tests/tck-build-logic/src/main/resources/AllowedDockerImages.txt),
   the test will fail
   * Only docker images that are in both `required-docker-images.txt` and in the `allowed docker images list`
   can be executed. 


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
 * `<groupId>` and `<artifactId>` - part of the standard Maven coordinates ([see this](https://maven.apache.org/pom.html#maven-coordinates))
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

**Note:** The entries you add will be validated against [library-and-framework-list-schema.json](https://github.com/oracle/graalvm-reachability-metadata/blob/master/library-and-framework-list-schema.json)
