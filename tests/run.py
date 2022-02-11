#!/usr/bin/python3

import __init__
import argparse
import json
import logging
import os
import subprocess
import sys
from logging import Logger
from tempfile import TemporaryDirectory
from typing import Dict, List, Optional, Set, Tuple, Union, Iterable
from zipfile import ZipFile

REPO_ROOT: str = os.path.realpath(os.path.join(os.path.dirname(__file__), ".."))
CONFIG_ROOT: str = os.path.join(REPO_ROOT, "config")
TEST_ROOT: str = os.path.join(REPO_ROOT, "tests")

assert __init__, "Python version checking failed!"

logging.basicConfig(stream=sys.stdout, format="%(levelname)s: %(message)s", level=logging.DEBUG)  # %(asctime)s
log: Logger = logging.getLogger("config-tests")

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
    :return: boolean if coordinates matches said group ID / artifact ID combo
    """
    test_group, test_artifact, _ = split_coordinates(coordinates)
    if group_id != test_group and group_id is not None:
        return False
    if artifact_id != test_artifact and artifact_id is not None:
        return False
    return True


def get_all_libraries_index() -> Iterable[Dict[str, Union[str, List[str]]]]:
    """
    Returns all libraries that are specified in index.json file from config directory.

    :return: list of all library definitions
    """
    with open(os.path.join(CONFIG_ROOT, "index.json")) as f:
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
                    dirs |= get_matching_dirs(dep_group, dep_artifact)

    if group_id is not None and artifact_id is not None:
        default_dir = os.path.join(CONFIG_ROOT, group_id.replace(".", "/"), artifact_id)
        if os.path.exists(os.path.join(default_dir, "index.json")):
            dirs.add(default_dir)

    return dirs


def generate_test_invocations(group_id: Optional[str], artifact_id: Optional[str], version: Optional[str]) -> \
        Iterable[Dict[str, str]]:
    """
    Generates a list of all test invocations that match given group ID, artifact ID and version combination.
    None values match every possible value (None artifact ID matches all artifacts in given group).

    :param group_id: group ID to match
    :param artifact_id: artifact ID to match
    :param version: version to match
    :return: list in which every entry holds complete information required to perform a single test invocation
    """
    invocations: List[Dict[str, str]] = []

    for directory in get_matching_dirs(group_id, artifact_id):
        full_dir: str = os.path.join(CONFIG_ROOT, directory)
        index: str = os.path.join(full_dir, "index.json")
        with open(index) as f:
            data: List[Dict[str, Union[str, List[str]]]]
            data = json.load(f)

        for library in data:
            if coordinates_match(library["module"], group_id, artifact_id):
                config_dir: str = os.path.join(full_dir, library["config-version"])

                test_index_path: str = os.path.join(TEST_ROOT, library["test-directory"], "index.json")
                with open(test_index_path) as test_index:
                    cmd = json.load(test_index)["test-command"]

                for tested in library["tested-versions"]:
                    if version is None or tested == version:
                        coordinates = f'{library["module"]}:{tested}'
                        invocations.append({
                            "coordinates": coordinates,
                            "config-directory": config_dir,
                            "test-directory": library["test-directory"],
                            "test-command": cmd,
                        })
        return invocations


def package_config_jar(config_dir: str, output_dir: str, group_id: str, artifact_id: str, version: str) -> str:
    """
    Creates a jar file that contains all configuration from given directory.

    :param config_dir: directory where configuration is located
    :param output_dir: directory where jar file should be created
    :param group_id: group ID of artifact that is being tested
    :param artifact_id: artifact ID of artifact that is being tested
    :param version: version of artifact that is being tested
    :return: path to a jar file that contains configuration
    """
    index_file: str = os.path.join(config_dir, "index.json")
    if os.path.exists(index_file):
        with open(index_file) as f:
            files: List[str] = json.load(f)
    else:
        files: List[str] = [f for f in os.listdir(config_dir) if f.endswith('.json') or f.endswith('.properties')]

    filename: str = f'{group_id}.{artifact_id}.{version}.jar'
    output_file: str = os.path.join(output_dir, filename)

    with ZipFile(output_file, "w") as zf:
        for file in files:
            file_path: str = os.path.join(config_dir, file)
            zip_name: str = f"META-INF/native-image/{group_id}/{artifact_id}/{file}"
            zf.write(file_path, zip_name)

    return output_file


def process_command(cmd: str, config_dir: str, jar_file: str, group_id: str, artifact_id: str, version: str) -> str:
    """
    Fills in template parameters in the command invocation.
    Parameters are defined as <param_name> in cmd.

    :param cmd: command line with parameters
    :param config_dir: configuration directory location
    :param jar_file: jar file location
    :param group_id: group ID
    :param artifact_id: artifact ID
    :param version: version
    :return: final command
    """
    return cmd.replace("<config_dir>", config_dir) \
             .replace("<jar_file>", jar_file) \
             .replace("<group_id>", group_id) \
             .replace("<artifact_id>", artifact_id) \
             .replace("<version>", version)


def run_invocations(invocations: Iterable[Dict[str, str]]) -> None:
    """
    Runs test invocations sequentially.
    Terminates the process with error code if test wasn't successful.

    :param invocations: all test invocations that should be executed
    :return: nothing
    """
    with TemporaryDirectory() as temp_dir:
        for inv in invocations:
            group_id, artifact_id, version = split_coordinates(inv["coordinates"])
            jar_file = package_config_jar(inv["config-directory"], temp_dir, group_id, artifact_id, version)
            try:
                log.info("====================")
                log.info("Testing library: %s", inv["coordinates"])

                cmd: str = process_command(inv["test-command"], inv["config-directory"], jar_file, \
                    group_id, artifact_id, version)
                log.info("Command: %s", cmd)

                test_directory: str = os.path.join(TEST_ROOT, inv["test-directory"])

                log.info("Executing test...")
                ret_code: int = subprocess.call(cmd, cwd=test_directory, shell=True)
                if ret_code != 0:
                    msg = f'Test for {inv["coordinates"]} failed with exit code {ret_code}.'
                    raise Exception(msg)
                log.info("Test for %s passed.", inv["coordinates"])
            except Exception as e:
                log.error(str(e))
                sys.exit(1)
            finally:
                log.info("====================")


def main() -> None:
    parser = argparse.ArgumentParser(description="tests native configuration for given artifact coordinates")
    parser.add_argument("coordinates", type=str, help="maven coordinates for argument to test or 'all'")
    args = parser.parse_args()

    group_id, artifact_id, version = split_coordinates(args.coordinates)

    if group_id in ("all", "any"):
        group_id = None

    invocations = generate_test_invocations(group_id, artifact_id, version)
    run_invocations(invocations)


if __name__ == "__main__":
    main()
