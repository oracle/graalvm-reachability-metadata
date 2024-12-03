# Check new versions of existing libraries in the repository

As the number of libraries in the repository grow fast, it is hard to track new library versions for every library manually.
Instead of doing this process manually, we provided a mechanism (through [a GitHub workflow](https://github.com/oracle/graalvm-reachability-metadata/blob/master/.github/workflows/check-new-library-versions.yml)) 
that automatically scans MavenCentral repository for new versions of the libraries that we currently have.

## How it works

The workflow gets triggered every two weeks automatically (alternating to the automatic release weeks). Besides that, the job can be triggered manually from the GitHub actions.
The whole process consists of the following parts:
* Scanning of the MavenCentral
* Running existing tests with newer versions of the library
* Creating a pull-request that updates `tested-versions` field of the `index.json` file for libraries that passed tests with a new version
* Creating an issue that lists all versions of libraries that failed their existing tests.

As a preparation for the whole process, we are creating a branch for all successful tests, and a single issue for all failed tests.

### Scanning the MavenCentral 

At first, the workflow runs gradle task called `fetchExistingLibrariesWithNewerVersions`.
The task itself does the following:
1. Gets the list of all existing libraries in the repository
2. For each library, it searches for the latest tested version in the corresponding library `index.json` file
3. For the given library name, it fetches `maven-metadata.xml` file from the MavenCentral repository
4. In the fetched `maven-metadata.xml` file, it finds the position of the latest tested version (gathered in the step 3) and returns all the versions after it
5. As a last step, the task returns list of maven coordinates of libraries with newer versions (alongside java version and os version required for testing)

### Running existing tests with newer versions

Now that we have coordinates list, we are spawning a new job in GitHub workflow for each coordinate in the list.
Each of the spawned jobs:
1. Extracts the following parts from the given maven coordinates:
   1. Latest version that we have tests written for 
   2. Path to the latest tests we have
   3. Maven coordinates of the latest tests
2. Sets `GVM_TCK_LV` env variable to the version we want to test. This way the executed tests will use library version specified in the env variable.
3. Run the latest test with `./gradlew test -Pcoordinates=<testCoordinates>` (with `testCoordinates` calculated in the step 1)

### Aggregating results of the tests

Based on the outcome of the test we:
* Update the list of `tested-versions` in the proper library `index.json` file and commit changes to the previously created branch, if the test passed
* Add a comment that explains which library version cannot pass the tests, in the issue we previously created

Note: since the spawned jobs run tests in parallel, we have to make some kind of synchronization to avoid merge conflicts if two tests are populating the same `index.json` file.
The whole process of synchronization is driven by the [tryPushVersionsUpdate](https://github.com/oracle/graalvm-reachability-metadata/blob/master/.github/workflows/tryPushVersionsUpdate.sh) script.

At the end, when all jobs have finished their executions, the workflow just creates a pull-request based on a branch the jobs committed to.
As a final result, we have:
* a pull-request with updates of all new tested versions
* an issue with list of all versions that doesn't work with existing metadata
