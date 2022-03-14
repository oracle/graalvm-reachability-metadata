# Contributing
We welcome your contributions. To get started, you will need to sign the [Oracle Contributor Agreement](https://oca.opensource.oracle.com) (OCA).

Only pull requests from committers that can be verified as having signed the OCA can be accepted.

## Checklist
In order to ensure that all contributions follow the same standards of quality we have devised a following list of requirements for each new added library.
`org.example:library` project is also included as template for new libraries.

### Metadata
* [ ] Add a new folder structure in `metadata` directory in root of this repository.
Per convention this should mirror Maven central notation (`com.example:library` metadata should be located in `metadata/com/example/library`).
* [ ] Add a new entry in `metadata/index.json` that points to metadata being added. For example:
    ```json
    [
      ...
      {
        "directory": "com/example/library",
        "module": "com.example:library"
      },
      {
        "module": "com.example:dependant-library",
        "requires": [
          "com.example:library"
        ]
      }
    ]
    ```
    Note that `dependant-library` can feature its own metadata as well if `directory` key is specified.
* [ ] Add `index.json` file to the metadata directory. In aforementioned case that would be `metadata/com/example/library/index.json`.
It should contain the following entries:
    ```json
    [
      {
        "metadata-version": "1",
        "module": "org.example:library",
        "test-directory": "org/example/library/0.0.1",
        "tested-versions": [
          "0.0.1", "0.0.2"
        ]
      },
      {
        "metadata-version": "2",
        "module": "com.example:library",
        "test-directory": "com/example/library/1.0.0",
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
* [ ] Ensure that metadata is properly formatted. This can be done by running following command from root of the repository:
  ```bash
  python3 tests/run.py format
  ```

### Tests
Every submitted library must feature tests that serve as a safeguard against regressions.
We recommend that tests use our [native-gradle-plugin](https://graalvm.github.io/native-build-tools/latest/gradle-plugin.html)
and its included [JUnit Platform support](https://graalvm.github.io/native-build-tools/latest/gradle-plugin.html#testing-support).

* [ ] Add tests to the `test-directory`. For this example that would be `tests/com/example/library/0.0.1`.

  Each test directory should contain `index.json` with content as follows:
  ```json
  {
    "test-command": "gradle clean nativeTest -metadata.dir=<metadata_dir> -Plibrary.version=<version>",
    "test-environment": {
      "ENVIRONMENT_KEY": "VALUE"
    }
  }
  ```
  Supported template parameters for `test-command` are:
  * `<metadata_dir>` - absolute path to directory where metadata is stored
  * `<jar_file>` - absolute path to jar file that contains metadata in its `META-INF/native-image`
  * `<group_id>` - Maven groupID of artifact that is being tested
  * `<artifact_id>`- Maven artifactID of artifact that is being tested
  * `<version>` - Version of artifact that is being tested

* [ ] Verify locally that test is running correctly. In this example this can be done by invoking:
  ```bash
  python3 tests/run.py test com.example:library:0.0.1
  ```
