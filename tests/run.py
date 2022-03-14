#!/usr/bin/python3
import __init__
import argparse
import json
import logging
import os
import subprocess
import sys
from json import JSONDecodeError
from logging import Logger
from pathlib import Path
from tempfile import TemporaryDirectory
from typing import Dict, List, Optional, Set, Tuple, Union, Iterable
from zipfile import ZipFile

REPO_ROOT: str = os.path.realpath(os.path.join(os.path.dirname(__file__), ".."))
METADATA_ROOT: str = os.path.join(REPO_ROOT, "metadata")
TEST_ROOT: str = os.path.join(REPO_ROOT, "tests")

assert __init__, "Python version checking failed!"

logging.basicConfig(stream=sys.stdout, format="%(levelname)s: %(message)s", level=logging.DEBUG)  # %(asctime)s
log: Logger = logging.getLogger("metadata-tests")

MAVEN_COORDINATES_PARTS = 3  # group_id, artifact_id, version


def split_coordinates(coordinates: str) -> Tuple[Optional[str], Optional[str], Optional[str]]:
    """
    Splits maven coordinates into group ID, artifact ID and version.
    If some part of coordinate is missing, return None instead.

    :param coordinates: maven coordinate
    :return: tuple containing group_id, artifact_id and version (or None values if missing)
    """
    group_id: Optional[str]
    artifact_id: Optional[str]
    version: Optional[str]

    tmp: List[Optional[str]] = coordinates.split(":")
    # Pad with None values in order to safely unpack tuple here.
    tmp += [None] * MAVEN_COORDINATES_PARTS
    tmp = tmp[:MAVEN_COORDINATES_PARTS]
    group_id, artifact_id, version = tmp

    return group_id, artifact_id, version


def coordinates_match(coordinates: str, group_id: Optional[str], artifact_id: Optional[str]) -> bool:
    """
    Checks if given coordinates string matches given group ID and artifact ID.
    None values match every possible value (None artifact ID matches all artifacts in given group).

    :param coordinates: maven coordinate
    :param group_id: group ID to match
    :param artifact_id: artifact ID to match
    :return: bool if coordinates matches said group ID / artifact ID combo
    """
    test_group, test_artifact, _ = split_coordinates(coordinates)
    if group_id != test_group and group_id is not None:
        return False
    if artifact_id != test_artifact and artifact_id is not None:
        return False
    return True


def get_all_libraries_index() -> Iterable[Dict[str, Union[str, List[str]]]]:
    """
    Returns all libraries that are specified in index.json file from metadata directory.

    :return: list of all library definitions
    """
    with open(os.path.join(METADATA_ROOT, "index.json")) as f:
        all_libraries: List[Dict[str, Union[str, List[str]]]] = json.load(f)
    return all_libraries


def get_matching_dirs(group_id: Optional[str], artifact_id: Optional[str]) -> Set[str]:
    """
    Returns a set of all directories that match given group ID and artifact ID.
    None values match every possible value (None artifact ID matches all artifacts in given group).

    :param group_id: group ID to match
    :param artifact_id: artifact ID to match
    :return: set of all directories that match given criteria
    """
    dirs: Set[str] = set()

    for library in get_all_libraries_index():
        if coordinates_match(library["module"], group_id, artifact_id):
            if "directory" in library:
                dirs.add(library["directory"])
            if "requires" in library:
                for dep in library["requires"]:
                    dep_group, dep_artifact, _ = split_coordinates(dep)
                    dirs.update(get_matching_dirs(dep_group, dep_artifact))

    if group_id is not None and artifact_id is not None:
        default_dir = os.path.join(METADATA_ROOT, group_id.replace(".", "/"), artifact_id)
        if os.path.exists(os.path.join(default_dir, "index.json")):
            dirs.add(default_dir)
    return dirs


def generate_test_invocations(group_id: Optional[str], artifact_id: Optional[str], version: Optional[str]) -> \
        Iterable[Dict[str, Union[str, Dict[str, str]]]]:
    """
    Generates a list of all test invocations that match given group ID, artifact ID and version combination.
    None values match every possible value (None artifact ID matches all artifacts in given group).

    :param group_id: group ID to match
    :param artifact_id: artifact ID to match
    :param version: version to match
    :return: list in which every entry holds complete information required to perform a single test invocation
    """
    invocations: List[Dict[str, Union[str, Dict[str, str]]]] = []

    matching_dirs = get_matching_dirs(group_id, artifact_id)
    for directory in matching_dirs:
        full_dir: str = os.path.join(METADATA_ROOT, directory)
        index: str = os.path.join(full_dir, "index.json")
        with open(index) as f:
            data: List[Dict[str, Union[str, List[str]]]]
            data = json.load(f)

        for library in data:
            if coordinates_match(library["module"], group_id, artifact_id):
                metadata_dir: str = os.path.join(full_dir, library["metadata-version"])

                test_index_path: str = os.path.join(TEST_ROOT, library["test-directory"], "index.json")
                with open(test_index_path) as test_index:
                    tests: Dict[str, Union[str, Dict[str, str]]] \
                        = json.load(test_index)
                    cmd: str = tests["test-command"]
                    env: Optional[Dict[str, str]] = None
                    if "test-environment" in tests:
                        env = tests["test-environment"]

                for tested in library["tested-versions"]:
                    if version is None or tested == version:
                        coordinates = f'{library["module"]}:{tested}'
                        invocations.append({
                            "coordinates": coordinates,
                            "metadata-directory": metadata_dir,
                            "test-directory": library["test-directory"],
                            "test-command": cmd,
                            "test-environment": env
                        })
    # Lets filter out duplicate invocations:
    invocations = {json.dumps(inv, sort_keys=True): inv for inv in invocations}.values()
    return invocations


def get_metadata_file_list(directory: str) -> List[str]:
    """
    Returns a list of metadata files in a given directory.
    :param directory:
    :return: list of json files contained in it
    """
    index_file: str = os.path.join(directory, "index.json")
    if os.path.exists(index_file):
        with open(index_file) as f:
            return json.load(f)
    else:
        return [f for f in os.listdir(directory) if f.endswith('.json') or f.endswith('.properties')]


def package_metadata_jar(metadata_dir: str, output_dir: str, group_id: str, artifact_id: str, version: str) -> str:
    """
    Creates a jar file that contains all metadata from given directory.

    :param metadata_dir: directory where metadata is located
    :param output_dir: directory where jar file should be created
    :param group_id: group ID of artifact that is being tested
    :param artifact_id: artifact ID of artifact that is being tested
    :param version: version of artifact that is being tested
    :return: path to a jar file that contains metadata
    """
    files: List[str] = get_metadata_file_list(metadata_dir)
    filename: str = f'{group_id}.{artifact_id}.{version}.jar'
    output_file: str = os.path.join(output_dir, filename)

    with ZipFile(output_file, "w") as zf:
        for file in files:
            file_path: str = os.path.join(metadata_dir, file)
            zip_name: str = f"META-INF/native-image/{group_id}/{artifact_id}/{file}"
            zf.write(file_path, zip_name)

    return output_file


def process_command(cmd: str, metadata_dir: str, jar_file: str, group_id: str, artifact_id: str, version: str) -> str:
    """
    Fills in template parameters in the command invocation.
    Parameters are defined as <param_name> in cmd.

    :param cmd: command line with parameters
    :param metadata_dir: metadata directory location
    :param jar_file: jar file location
    :param group_id: group ID
    :param artifact_id: artifact ID
    :param version: version
    :return: final command
    """
    return cmd.replace("<metadata_dir>", metadata_dir) \
        .replace("<jar_file>", jar_file) \
        .replace("<group_id>", group_id) \
        .replace("<artifact_id>", artifact_id) \
        .replace("<version>", version)


def run_invocations(invocations: Iterable[Dict[str, Union[str, Dict[str, str]]]]) -> None:
    """
    Runs test invocations sequentially.
    Terminates the process with error code if test wasn't successful.

    :param invocations: all test invocations that should be executed
    :return: nothing
    """
    with TemporaryDirectory() as temp_dir:
        for inv in invocations:
            group_id, artifact_id, version = split_coordinates(inv["coordinates"])
            jar_file = package_metadata_jar(inv["metadata-directory"], temp_dir, group_id, artifact_id, version)
            try:
                log.info("====================")
                log.info("Testing library: %s", inv["coordinates"])

                cmd: str = process_command(inv["test-command"], inv["metadata-directory"], jar_file, group_id,
                                           artifact_id, version)
                log.info("Command: %s", cmd)

                env: Dict[str, str] = os.environ.copy()
                if "test-environment" in inv and inv["test-environment"]:
                    env.update(inv["test-environment"])

                test_directory: str = os.path.join(TEST_ROOT, inv["test-directory"])

                log.info("Executing test...")
                ret_code: int = subprocess.call(cmd, cwd=test_directory, env=env, shell=True)
                if ret_code != 0:
                    msg: str = f'Test for {inv["coordinates"]} failed with exit code {ret_code}.'
                    raise Exception(msg)
                log.info("Test for %s passed.", inv["coordinates"])
            except Exception as e:
                log.error(str(e))
                sys.exit(1)
            finally:
                log.info("====================")


def json_check(fix_issues: bool = False) -> None:
    """
    Checks if JSON metadata files are formatted properly.

    :param fix_issues: fix encountered issues instead of failing
    :return: list in which every entry holds complete information required to perform a single test invocation
    """
    for path in Path(METADATA_ROOT).rglob('*.json'):
        abs_path = path.absolute()
        with open(abs_path) as f:
            original: str = f.read()
        try:
            parsed = json.loads(original)
        except JSONDecodeError as e:
            log.error("Failed parsing JSON file '%s'", abs_path)
            raise e
        formatted: str = json.dumps(parsed, sort_keys=True, indent='  ', separators=(',', ': ')) + "\n"
        if original != formatted:
            if not fix_issues:
                raise Exception(f'JSON file {abs_path} isn\'t formatted properly. Please run tests/run.py format.')
            log.info("Formatting file: '%s'", abs_path)
            with open(abs_path, "w") as f:
                f.write(formatted)


def run_tests(coordinates: str) -> None:
    group_id, artifact_id, version = split_coordinates(coordinates)

    if group_id in ("all", "any"):
        group_id = None

    invocations = generate_test_invocations(group_id, artifact_id, version)
    run_invocations(invocations)


def main() -> None:
    parser = argparse.ArgumentParser(description="tests and utilities for testing metadata")
    sub_parsers = parser.add_subparsers(help="command to execute", dest="command")
    parser_test = sub_parsers.add_parser("test", help="test metadata")
    parser_test.add_argument("coordinates", type=str, help="maven coordinates for argument to test or 'all'")
    parser_diff = sub_parsers.add_parser("diff", help="test metadata that was changed between commits")
    parser_diff.add_argument("base_commit", type=str, help="base commit hash")
    parser_diff.add_argument("new_commit", type=str, nargs='?', default='HEAD', help="new commit hash")
    sub_parsers.add_parser("format", help="properly format metadata")
    args = parser.parse_args()

    if args.command == "test":
        json_check(fix_issues=False)
        run_tests(args.coordinates)

    elif args.command == "diff":
        json_check(fix_issues=False)
        base_commit: str = args.base_commit
        new_commit: str = args.new_commit
        command: str = f"git diff --name-only --diff-filter=ACMRT {base_commit} {new_commit}"
        process = subprocess.Popen(command, shell=True, stdout=subprocess.PIPE)
        diff_files: List[str] = process.stdout.read().decode("utf-8").splitlines()

        changed_tests: Set[str] = set()
        changed_metadata: Set[str] = set()

        for line in diff_files:
            dir_abspath: str = os.path.join(REPO_ROOT, os.path.dirname(line))
            if line.startswith("tests/"):
                changed_tests.add(dir_abspath)
            elif line.startswith("metadata/"):
                changed_metadata.add(dir_abspath)

        invocations = generate_test_invocations(None, None, None)
        matching_coordinates: Set[str] = set()

        for inv in invocations:
            added: bool = False
            for metadata in changed_metadata:
                if metadata.startswith(inv["metadata-directory"]):
                    matching_coordinates.add(inv["coordinates"])
                    added = True
                    continue

            if added:
                continue

            for test in changed_tests:
                if test.startswith(inv["test-directory"]):
                    matching_coordinates.add(inv["coordinates"])
                    continue

        for coordinate in matching_coordinates:
            run_tests(coordinate)

    elif args.command == "format":
        json_check(fix_issues=True)


if __name__ == "__main__":
    main()
