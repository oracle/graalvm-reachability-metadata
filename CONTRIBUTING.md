# Contributing

Before contributing to this repository, please try to include the reachability metadata directly into the library. If that does not work, open a ticket on the target library issue tracker so the community can upvote and discuss metadata addition. Only after these steps, follow the checklist for adding the metadata to this repository.

## Checklist
In order to ensure that all contributions follow the same standards of quality we have devised a following list of requirements for each new added library.
`org.example:library` project is also included as a template for new libraries.

> ℹ️ **Note** :
>
> GraalVM Reachability Metadata in this repo only contains JSON files as described
> in [Manual Configuration](https://www.graalvm.org/22.0/reference-manual/native-image/Reflection/#manual-configuration)
> section of the Native Image documentation.
>
>  All other library tweaks (such as build time initialization through `native-image.properties`) must not be included
> here. By default, it should be assumed that all user libraries are runtime initialized. Build-time initialization can
> not be included as it does not compose and can break code in unpredictable ways.
>
> Make sure that you are
> using [Conditional Configuration](https://www.graalvm.org/22.2/reference-manual/native-image/metadata/#specifying-reflection-metadata-in-json)
> in order to precisely define the metadata scope. This is a hard requirement as it prevents unnecessary bloating of
> images.
>
> To learn more about collecting metadata, see [How To Collect Metadata](docs/CollectingMetadata.md).

### Contribute Metadata

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

#### Metadata structure

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
    ]
  }
]
```

Note that `dependant-library` can feature its own metadata as well if `directory` key is specified.

Every library metadata has another `index.json` file.
In aforementioned case that would be `metadata/org.example/library/index.json`.
It should contain the following entries:

```json
[
  {
    "latest": false,
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

Make sure that each supported version is listed in `tested-version`, as that value is used in build tools to match
metadata to a specific library.
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

### Tests

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
* `<group_id>` - Maven groupID of artifact that is being tested
* `<artifact_id>`- Maven artifactID of artifact that is being tested
* `<version>` - Version of artifact that is being tested

**Note that if `index.json` is omitted `gradle nativeTest` is executed by default.**

#### Executing the tests

In this example this can be done by invoking following command from the repository root:

```bash
./gradlew test -Pcoordinates=org.example:library:0.0.1
```

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

**Note:** To pass format and style checks, please run `./gradlew :spotlessApply` from the project root, before submitting a PR.  
**Note:** The entries you add will be validated against [library-and-framework-list-schema.json](https://github.com/oracle/graalvm-reachability-metadata/blob/master/library-and-framework-list-schema.json)

