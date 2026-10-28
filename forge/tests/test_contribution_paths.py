# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

"""The contribution's closed file set: what the one resolver admits and what it rejects."""
from __future__ import annotations

import json
import os
import shutil
import subprocess
import tempfile
import unittest
from typing import Any

from utility_scripts.contribution_paths import contribution_paths, stray_paths

COORDINATE: str = "org.example:lib:2.0.0"
OWNER_METADATA: str = "metadata/org.example/owner/3.0.0/reachability-metadata.json"
IMAGE: str = "tests/tck-build-logic/src/main/resources/allowed-docker-images/Dockerfile-postgres"
ROUTED: dict[str, Any] = {"type": "org.example.owner.Codec", "condition": {"typeReached": "org.example.owner.Api"}}
KEPT: dict[str, Any] = {"type": "org.example.owner.Kept"}


def _git(*arguments: str, cwd: str) -> str:
    return subprocess.run(["git", "-c", "user.name=T", "-c", "user.email=t@example.com", "-c", "commit.gpgsign=false",
                           *arguments], cwd=cwd, check=True, stdout=subprocess.PIPE, text=True).stdout.strip()


class ContributionPathsTest(unittest.TestCase):

    def setUp(self) -> None:
        self.repo: str = tempfile.mkdtemp(prefix="forge-contribution-paths-")
        self.addCleanup(shutil.rmtree, self.repo, True)
        _git("init", "-q", "-b", "master", cwd=self.repo)
        # 2.0.0's suite is shared: 2.1.0 tests with it and ships 2.0.0's metadata.
        self.write("metadata/org.example/lib/index.json", json.dumps([
            {"metadata-version": "2.0.0", "tested-versions": ["2.0.0", "2.1.0"], "allowed-packages": ["org.example"]},
            {"metadata-version": "1.0.0", "allowed-packages": ["org.example"]}]))
        self.write("metadata/org.example/lib/2.0.0/reachability-metadata.json", json.dumps({"reflection": []}))
        self.write("tests/src/org.example/lib/2.0.0/build.gradle", "// 2.0.0\n")
        self.write("tests/src/org.example/lib/1.0.0/build.gradle", "// 1.0.0\n")
        self.write("metadata/org.example/owner/index.json",
                   json.dumps([{"metadata-version": "3.0.0", "allowed-packages": ["org.example.owner"]}]))
        self.write(OWNER_METADATA, json.dumps({"reflection": [KEPT]}))
        _git("add", "-A", cwd=self.repo)
        _git("commit", "-q", "-m", "base", cwd=self.repo)
        self.base: str = _git("rev-parse", "HEAD", cwd=self.repo)

    def write(self, relative: str, text: str) -> None:
        path: str = os.path.join(self.repo, relative)
        os.makedirs(os.path.dirname(path), exist_ok=True)
        with open(path, "w", encoding="utf-8") as handle:
            handle.write(text)

    def stray(self, *paths: str) -> list[str]:
        return stray_paths(self.repo, COORDINATE, self.base, paths)

    def test_the_version_s_own_files_belong(self) -> None:
        self.assertEqual([], self.stray(
            "metadata/org.example/lib/index.json", "metadata/org.example/lib/2.0.0/reachability-metadata.json",
            "tests/src/org.example/lib/2.0.0/src/test/java/LibTest.java", "stats/org.example/lib/2.0.0/stats.json"))

    def test_another_version_and_a_shared_file_do_not(self) -> None:
        paths: tuple[str, ...] = ("tests/src/org.example/lib/1.0.0/build.gradle",
                                  "metadata/org.example/lib/1.0.0/reachability-metadata.json",
                                  "stats/org.example/lib/1.0.0/stats.json", "build.gradle", "README.md")
        self.assertEqual(sorted(paths), self.stray(*paths))

    def test_a_legacy_config_never_belongs(self) -> None:
        legacy: str = "metadata/org.example/lib/2.0.0/reflect-config.json"
        self.assertEqual([legacy], self.stray(legacy))

    def test_routed_owner_metadata_belongs_when_it_only_adds_conditioned_entries(self) -> None:
        self.write(OWNER_METADATA, json.dumps({"reflection": [KEPT, ROUTED]}))
        owned: tuple[str, ...] = (OWNER_METADATA, "metadata/org.example/owner/index.json",
                                  "stats/org.example/owner/3.0.0/stats.json")
        self.assertEqual([], self.stray(*owned))
        self.assertIn(OWNER_METADATA, contribution_paths(self.repo, COORDINATE, self.base, owned))

    def test_owner_metadata_that_changes_an_entry_or_leaves_the_owner_s_packages_does_not(self) -> None:
        self.write(OWNER_METADATA, json.dumps({"reflection": [ROUTED]}))
        self.assertEqual([OWNER_METADATA], self.stray(OWNER_METADATA))
        unconditioned: dict[str, Any] = {"type": "org.example.owner.Codec", "condition": {"typeReached": "org.other.A"}}
        self.write(OWNER_METADATA, json.dumps({"reflection": [KEPT, unconditioned]}))
        self.assertEqual([OWNER_METADATA, "metadata/org.example/owner/index.json"],
                         self.stray(OWNER_METADATA, "metadata/org.example/owner/index.json"))

    def test_metadata_of_a_library_the_repository_does_not_host_does_not_belong(self) -> None:
        foreign: str = "metadata/org.other/new/1.0.0/reachability-metadata.json"
        self.write(foreign, json.dumps({"reflection": [ROUTED]}))
        self.assertEqual([foreign], self.stray(foreign))

    def test_a_version_may_leave_the_shared_suite_with_a_copy_of_its_metadata(self) -> None:
        moved: tuple[str, ...] = ("tests/src/org.example/lib/2.1.0/build.gradle",
                                  "metadata/org.example/lib/2.1.0/reachability-metadata.json",
                                  "stats/org.example/lib/2.1.0/stats.json")
        self.write(moved[1], json.dumps({"reflection": []}))
        self.assertEqual([], self.stray(*moved))
        self.write(moved[1], json.dumps({"reflection": [ROUTED]}))
        self.assertEqual([moved[1]], self.stray(*moved))

    def test_an_allowed_image_belongs_only_when_the_test_lists_it(self) -> None:
        self.write(IMAGE, "FROM postgres:18.1\n")
        self.assertEqual([IMAGE], self.stray(IMAGE))
        self.write("tests/src/org.example/lib/2.0.0/required-docker-images.txt", "postgres:18.1\n")
        self.assertEqual([], self.stray(IMAGE))

    def test_a_commit_is_judged_by_its_own_content(self) -> None:
        self.write(OWNER_METADATA, json.dumps({"reflection": [KEPT, ROUTED]}))
        _git("commit", "-q", "-am", "route", cwd=self.repo)
        head: str = _git("rev-parse", "HEAD", cwd=self.repo)
        self.write(OWNER_METADATA, json.dumps({}))
        self.assertEqual([], stray_paths(self.repo, COORDINATE, self.base, [OWNER_METADATA], head=head))


if __name__ == "__main__":
    unittest.main()
