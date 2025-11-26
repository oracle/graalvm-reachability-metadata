# Reviewing

In the metadata repository, we accept contributions from 3rd parties.
Per Oracle policies, we need to do a proper review of such code, including licensing and security checks.
This document should serve as a guideline for reviewers to simplify and harmonize those reviews.

## Checklist
First step of every review is to verify the checklist from the pull request description.
Once the contributor wants to open a pull request [this checklist](../.github/pull_request_template.md) will be automatically added to the pull request description.
* If the PR does not contain such a list, it **should not be reviewed**
* If any of the items is not checked, the reviewer should ask for an explanation from the contributor

## Copyright and licences
As mentioned in the checklist, each pull request must **only** contain tests that are created by the author of the pull request.
**Every contribution that contains code from other sources must be rejected.**
In order to check that the contribution has no copyright violation, following hints can be useful:
* Unreasonably large contributions
* URLs posted, comments
* Package names that indicate a 4th party

## Security
There are many security aspects that must be checked for each test provided in the pull request.
Check for the following critical actions:
* **File Access**
  * Verify the file access is reasonable and necessary
  * Verify which file is read or written. Preferred case is a static filename in the local or temp directory.
  * Give special consideration to files in special location (/bin etc.)
  * Give special consideration to executables
  * Check what content is written or read. Is it statically known, or computed? If computed, where does the data come from.
* **Connecting to a network port**
  * Verify the network access is reasonable and necessary
  * Verify which host is connected. Prefer statically known names over computed ones or IP addresses
  * Verify what data is sent or (potentially) received.
  * Is the outgoing data statically known, or computed?
  * Is the outgoing data potentially leaking relevant information?
  * What happens to the incoming data? Asserting the content of the data is generally ok. 
  * Executing the incoming data or writing it to disk is a red flag!
* **Opening a network port**
  * Verify that opening a server is reasonable and necessary
  * Check what data the server is potentially serving. Is it statically known data, or computed? Is data read from files served?
  * Verify that no relevant information is leaking
  * Check what the server does with incoming data. Does it need to be sanitized? Could it pose a risk?
* **Start an external process**
  * Verify that the process is necessary and reasonable
  * Understand the command. Check what it is doing and assess the risk it could pose
  * Can the command manipulate data on the machine?
  * Can the command leak relevant information from the machine?
* **Usage of docker**
  * The GitHub action should automatically fail when the test is using docker image if it is not properly specified
  * Only docker images that are specified in `required-docker-images.txt` can be pulled and executed if they are listed in `AllowedDockerImages`.
  * Whole process of writing tests that are using docker images is described [here](CONTRIBUTING.md#providing-the-tests-that-use-docker)
  * **If the pull request wants to extend the list of allowed docker images, please add [@matneu](https://github.com/matneu) as the reviewer**
  * Every pull request that extends the list of allowed docker images must have results of the *grype* test in its description ([see this](CONTRIBUTING.md#providing-the-tests-that-use-docker))


## Metadata
All metadata files **must** be covered by the tests. Every metadata file should:
* have `typeReachable` in all entries (so it won't bloat image size with things that will be registered unconditionally)
* contain only metadata entries needed for the library that is the subject of the PR
* not have entries that describes classes that serve only for tests 

There are various tools that could help checking the content of all json files we are collecting.
To run these checks automatically, top-level metadata index file must contain `allowed-packages` properly set ([see this](CONTRIBUTING.md#metadata-structure)).
If this field is properly configured, our GitHub workflows will automatically check `typeReachable` and origin of all entries in all config files.
