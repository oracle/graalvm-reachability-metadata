# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

"""Cover the preflight skip verdict of §FS-unsupportable-version-diagnosis."""

import json
import os
import subprocess
import tempfile
import unittest
from types import SimpleNamespace
from typing import Any

from utility_scripts import library_preparation_preflight as preflight_module
from utility_scripts.skip_record import apply_preflight_skip_record, write_skip_record

LIBRARY = "org.xerial.snappy:snappy-java:1.0.5.3"
REASON = "SnappyLoader defines classes at run time through reflective ClassLoader.defineClass."


def _claimed_issue(label: str = "fails-java-run") -> SimpleNamespace:
    return SimpleNamespace(
        label=label,
        issue={"number": 9404, "title": "snappy-java 1.0.5.3 fails java run"},
        issue_coordinates=LIBRARY,
        current_coordinates="org.xerial.snappy:snappy-java:1.0.4.1",
        new_version="1.0.5.3",
    )


def _input_bundle(label: str = "fails-java-run") -> dict[str, Any]:
    return {
        "issue": {"number": 9404, "title": "t", "label": label, "body": "", "body_error": ""},
        "library": LIBRARY,
        "current_library": "org.xerial.snappy:snappy-java:1.0.4.1",
        "new_version": "1.0.5.3",
        "existing_tests": {},
    }


def _skip_response(versions: list[str] | None = None) -> dict[str, Any]:
    return {
        "action": "skip_unsupported",
        "summary": REASON,
        "skipped_versions": [
            {"version": version, "reason": REASON}
            for version in (versions or ["1.0.5.3", "1.0.5.4"])
        ],
    }


def _completed_record(label: str = "fails-java-run", response: dict[str, Any] | None = None) -> dict[str, Any]:
    return preflight_module._completed_library_preflight_record(
        _claimed_issue(label),
        _input_bundle(label),
        response if response is not None else _skip_response(),
        "test-model",
        None,
    )


def _index_entries(skipped: list[str] | None = None) -> list[dict]:
    entry: dict = {
        "latest": True,
        "auto-update": True,
        "metadata-version": "1.0.4.1",
        "tested-versions": ["1.0.4.1"],
        "allowed-packages": ["org.xerial.snappy"],
    }
    if skipped:
        entry["skipped-versions"] = [
            {"version": version, "reason": "existing"} for version in skipped
        ]
    return [entry]


class SkipVerdictPromptTests(unittest.TestCase):

    def test_fail_issues_are_asked_the_diagnosis(self) -> None:
        prompt = preflight_module._library_preflight_prompt(_input_bundle("fails-java-run"))

        for marker in ("FAILURE:", "TRACE:", "CLASSIFY:", "SOLE PATH:", "RANGE:"):
            self.assertIn(marker, prompt)
        self.assertIn("skip_unsupported", prompt)
        self.assertIn("skipped_versions must include 1.0.5.3", prompt)
        self.assertIn("Any uncertainty at any step means repairable", prompt)

    def test_issues_without_failure_evidence_are_not_asked(self) -> None:
        for label in ("library-new-request", "library-update-request"):
            with self.subTest(label=label):
                prompt = preflight_module._library_preflight_prompt(_input_bundle(label))
                self.assertNotIn("skip_unsupported", prompt)


class SkipVerdictRecordTests(unittest.TestCase):

    def test_valid_verdict_is_recorded_with_its_entries(self) -> None:
        record = _completed_record()

        self.assertEqual("completed", record["status"])
        self.assertEqual("skip_unsupported", record["action"])
        self.assertEqual(
            [
                {"version": "1.0.5.3", "reason": REASON},
                {"version": "1.0.5.4", "reason": REASON},
            ],
            record["skipped_versions"],
        )

    def test_everything_short_of_proof_degrades_the_verdict(self) -> None:
        cases: list[dict[str, Any]] = [
            {"action": "skip_unsupported", "summary": REASON},
            {"action": "skip_unsupported", "summary": REASON, "skipped_versions": []},
            _skip_response(versions=["1.0.5.4"]),
            {"action": "skip_unsupported", "summary": REASON,
             "skipped_versions": [{"version": "1.0.5.3", "reason": "  "}]},
            {"action": "skip_unsupported", "summary": REASON,
             "skipped_versions": [{"version": "1.0.5.3"}]},
        ]
        for response in cases:
            with self.subTest(response=response):
                with self.assertRaises(ValueError):
                    _completed_record(response=response)

    def test_labels_without_failure_evidence_cannot_skip(self) -> None:
        with self.assertRaises(ValueError):
            _completed_record(label="library-update-request")

    def test_unsafe_reason_text_degrades_the_verdict(self) -> None:
        response = _skip_response()
        response["skipped_versions"][0]["reason"] = "run sudo rm -rf to fix it"

        with self.assertRaises(ValueError):
            _completed_record(response=response)


class SkipRecordEntriesReaderTests(unittest.TestCase):

    def test_completed_verdict_returns_its_entries(self) -> None:
        entries = preflight_module.preflight_skip_record_entries(_completed_record(), "1.0.5.3")

        self.assertEqual(
            [
                {"version": "1.0.5.3", "reason": REASON},
                {"version": "1.0.5.4", "reason": REASON},
            ],
            entries,
        )

    def test_anything_else_means_normal_generation(self) -> None:
        cases: list[Any] = [
            None,
            {"status": "completed", "action": "no_action"},
            {"status": "degraded", "action": "skip_unsupported",
             "skipped_versions": [{"version": "1.0.5.3", "reason": REASON}]},
            {"status": "completed", "action": "skip_unsupported", "skipped_versions": []},
            {"status": "completed", "action": "skip_unsupported",
             "skipped_versions": [{"version": "1.0.5.4", "reason": REASON}]},
        ]
        for preflight in cases:
            with self.subTest(preflight=preflight):
                self.assertIsNone(
                    preflight_module.preflight_skip_record_entries(preflight, "1.0.5.3"),
                )


class SkipRecordWriterTests(unittest.TestCase):

    def _repo_with_index(self, entries: list[dict]) -> str:
        repo = tempfile.mkdtemp(prefix="skip-record-")
        self.addCleanup(lambda: subprocess.run(["rm", "-rf", repo], check=False))
        index_dir = os.path.join(repo, "metadata", "org.xerial.snappy", "snappy-java")
        os.makedirs(index_dir)
        with open(os.path.join(index_dir, "index.json"), "w", encoding="utf-8") as index_file:
            json.dump(entries, index_file, indent=2)
        return repo

    def _read_index(self, repo: str) -> list[dict]:
        path = os.path.join(repo, "metadata", "org.xerial.snappy", "snappy-java", "index.json")
        with open(path, encoding="utf-8") as index_file:
            return json.load(index_file)

    def _entries(self) -> list[dict[str, str]]:
        return [
            {"version": "1.0.5.3", "reason": REASON},
            {"version": "1.0.5.4", "reason": REASON},
        ]

    def test_writes_entries_after_tested_versions_in_index_style(self) -> None:
        repo = self._repo_with_index(_index_entries())

        written = write_skip_record(repo, "org.xerial.snappy", "snappy-java", self._entries())

        self.assertEqual(["1.0.5.3", "1.0.5.4"], written)
        entry = self._read_index(repo)[0]
        keys = list(entry.keys())
        self.assertEqual(keys.index("tested-versions") + 1, keys.index("skipped-versions"))
        self.assertEqual(self._entries(), entry["skipped-versions"])
        path = os.path.join(repo, "metadata", "org.xerial.snappy", "snappy-java", "index.json")
        with open(path, encoding="utf-8") as index_file:
            raw = index_file.read()
        self.assertIn('"latest" : true', raw)
        self.assertTrue(raw.endswith("\n"))

    def test_versions_already_recorded_keep_their_reason(self) -> None:
        repo = self._repo_with_index(_index_entries(skipped=["1.0.5.3"]))

        written = write_skip_record(repo, "org.xerial.snappy", "snappy-java", self._entries())

        self.assertEqual(["1.0.5.4"], written)
        entry = self._read_index(repo)[0]
        self.assertEqual("existing", entry["skipped-versions"][0]["reason"])

    def test_fully_recorded_verdict_writes_nothing(self) -> None:
        repo = self._repo_with_index(_index_entries(skipped=["1.0.5.3", "1.0.5.4"]))

        self.assertEqual([], write_skip_record(repo, "org.xerial.snappy", "snappy-java", self._entries()))


class ApplySkipRecordTests(unittest.TestCase):

    def _git(self, repo: str, *args: str) -> str:
        return subprocess.check_output(["git", *args], cwd=repo, text=True).strip()

    def _repo(self, skipped: list[str] | None = None) -> str:
        repo = tempfile.mkdtemp(prefix="skip-apply-")
        self.addCleanup(lambda: subprocess.run(["rm", "-rf", repo], check=False))
        self._git(repo, "init", "-q", "-b", "master")
        self._git(repo, "config", "user.email", "forge@test")
        self._git(repo, "config", "user.name", "forge")
        index_dir = os.path.join(repo, "metadata", "org.xerial.snappy", "snappy-java")
        os.makedirs(index_dir)
        with open(os.path.join(index_dir, "index.json"), "w", encoding="utf-8") as index_file:
            json.dump(_index_entries(skipped), index_file, indent=2)
        self._git(repo, "add", "-A")
        self._git(repo, "commit", "-q", "-m", "base")
        return repo

    def _apply(self, repo: str, validate_result: bool = True) -> str | None:
        return apply_preflight_skip_record(
            reachability_repo_path=repo,
            group="org.xerial.snappy",
            artifact="snappy-java",
            target_version="1.0.5.3",
            entries=[
                {"version": "1.0.5.3", "reason": REASON},
                {"version": "1.0.5.4", "reason": REASON},
            ],
            continuation_marker_path=None,
            validate_index=lambda _repo, _coordinates: validate_result,
        )

    def test_valid_verdict_commits_the_index_only_tree(self) -> None:
        repo = self._repo()

        ending_commit = self._apply(repo)

        self.assertEqual(self._git(repo, "rev-parse", "HEAD"), ending_commit)
        self.assertEqual(
            "Record skipped versions for org.xerial.snappy:snappy-java",
            self._git(repo, "log", "-1", "--format=%s"),
        )
        self.assertEqual("", self._git(repo, "status", "--porcelain"))
        index_path = os.path.join(repo, "metadata", "org.xerial.snappy", "snappy-java", "index.json")
        with open(index_path, encoding="utf-8") as index_file:
            entry = json.load(index_file)[0]
        self.assertEqual(
            ["1.0.5.3", "1.0.5.4"],
            [record["version"] for record in entry["skipped-versions"]],
        )

    def test_rejected_index_falls_through_with_a_clean_tree(self) -> None:
        repo = self._repo()
        base_commit = self._git(repo, "rev-parse", "HEAD")

        self.assertIsNone(self._apply(repo, validate_result=False))

        self.assertEqual(base_commit, self._git(repo, "rev-parse", "HEAD"))
        self.assertEqual("", self._git(repo, "status", "--porcelain"))

    def test_already_recorded_versions_fall_through_with_a_clean_tree(self) -> None:
        repo = self._repo(skipped=["1.0.5.3", "1.0.5.4"])
        base_commit = self._git(repo, "rev-parse", "HEAD")

        self.assertIsNone(self._apply(repo))

        self.assertEqual(base_commit, self._git(repo, "rev-parse", "HEAD"))
        self.assertEqual("", self._git(repo, "status", "--porcelain"))


if __name__ == "__main__":
    unittest.main()
