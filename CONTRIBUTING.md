# Contributing
We welcome your contributions. To get started, you will need to sign the [Oracle Contributor Agreement](https://oca.opensource.oracle.com) (OCA).

Only pull requests from committers that can be verified as having signed the OCA can be accepted.

## Checklist
In order to ensure that all contributions follow the same standards of quality we have devised a following list of requirements for each new added library.
`org.example:library` project is also included as template for new libraries.

> ℹ️ **Note** :
>
> JVM Reachability Metadata in this repo only contains JSON files as described in [Manual Configuration](https://www.graalvm.org/22.0/reference-manual/native-image/Reflection/#manual-configuration) section of the Native Image documentation.
>
>  All other library tweaks (such as build time initialization through `native-image.properties`) should not be included here. By default, it should be assumed that all user libraries are runtime initialized. Build-time can not be included here as it does not compose and can break code in unpredictable ways.
>
> Make sure that you are using [Conditional Configuration syntax](https://www.graalvm.org/22.0/reference-manual/native-image/Reflection/#conditional-configuration) in order to precisely define metadata scope. This is a hard requirement as that way we can ensure both increased compatibility and minimal image sizes.

### Metadata
* [ ] Add a new folder structure in `metadata` directory in root of this repository.
Per convention this should mirror Maven central notation (`org.example:library` metadata should be located in `metadata/org/example/library`).
* [ ] Add a new entry in `metadata/index.json` that points to metadata being added. For example:
    ```json
    [
      ...
      {
        "directory": "org/example/library",
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
* [ ] Add `index.json` file to the metadata directory. In aforementioned case that would be `metadata/org/example/library/index.json`.
It should contain the following entries:
    ```json
    [
      {
        "latest": false,
        "metadata-version": "1",
        "module": "org.example:library",
        "test-directory": "org/example/library/0.0.1",
        "tested-versions": [
          "0.0.1", "0.0.2"
        ]
      },
      {
        "latest": true,
        "metadata-version": "2",
        "module": "org.example:library",
        "test-directory": "org/example/library/1.0.0",
        "tested-versions": [
          "1.0.0", "1.1.0-M1", "1.1.0"
        ]
      },
      ...
    ]
    ```
    `metadata-version` key specifies the subdirectory where metadata for tested versions "lives".
    So, the metadata for `org.example:library:0.0.1` and `org.example:library:0.0.2` is located at `metadata/org/example/library/1`.

   Make sure that each supported version is listed in `tested-version`, as that value is used in build tools to match metadata to a specific library.
* [ ] Add `index.json` file for specific metadata. For this example `metadata/org/example/library/1/index.json` would contain:
  ```json
  [
    "jni-config.json",
    "proxy-config.json",
    "reflect-config.json",
    "resource-config.json"
  ]
  ```
* [ ] Ensure that metadata is properly formatted. This can be done by running following command from root of the repository, and then following instructions from command output if necessary:
  ```bash
  gradle check
  ```

### Tests
Every submitted library must feature tests that serve as a safeguard against regressions.
For easier test development we've provided a TCK plugin that automatically configures our [native-gradle-plugin](https://graalvm.github.io/native-build-tools/latest/gradle-plugin.html)
and its included [JUnit Platform support](https://graalvm.github.io/native-build-tools/latest/gradle-plugin.html#testing-support). 

* [ ] Add tests to the `test-directory`. In this example that would be `tests/src/org/example/library/0.0.1`.
  You should use `tests/src/org/example/library/0.0.1` as a template for your tests.

  **Optionally** test directory may contain `index.json` with content as follows:
  ```json
  {
    "test-command": ["gradle", "clean", "nativeTest", "-Pmetadata.dir=<metadata_dir>", "-Plibrary.version=<version>"]
  }
  ```
  Supported template parameters for `test-command` are:
  * `<metadata_dir>` - absolute path to directory where metadata is stored
  * `<group_id>` - Maven groupID of artifact that is being tested
  * `<artifact_id>`- Maven artifactID of artifact that is being tested
  * `<version>` - Version of artifact that is being tested

  **Note that if `index.json` is omitted `gradle nativeTest` is executed by default.**

* [ ] Verify locally that test is running correctly. In this example this can be done by invoking following command from the repository root:
  ```bash
  gradle test -Pcoordinates=org.example:library:0.0.1
  ```
