# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

"""Cover the skip-record body a review produces under §root/FS-contribution-contract.5.4."""

import importlib.util
import json
import os
import sys
import unittest
from typing import Any
from unittest.mock import patch

REPOSITORY_ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
PUBLISHER_PATH = os.path.join(
    REPOSITORY_ROOT, ".github", "scripts", "forge_pr_publisher", "publisher.py",
)

HEAD_SHA = "a" * 40
BASE_COMMIT = "b" * 40
COORDINATES = "org.xerial.snappy:snappy-java:1.0.5.3"
METADATA_PATH = "metadata/org.xerial.snappy/snappy-java"
REASON = "SnappyLoader defines SnappyNativeLoader at run time through ClassLoader.defineClass."


def _load_publisher() -> Any:
    """Load the trusted publisher the way Actions runs it: straight from the file."""
    spec = importlib.util.spec_from_file_location("forge_pr_publisher_skip_record", PUBLISHER_PATH)
    module = importlib.util.module_from_spec(spec)
    sys.modules[spec.name] = module
    spec.loader.exec_module(module)
    return module


publisher = _load_publisher()


def _index(skipped: list[str]) -> str:
    return json.dumps([{
        "latest": True,
        "auto-update": True,
        "metadata-version": "1.0.4.1",
        "tested-versions": ["1.0.4.1"],
        "skipped-versions": [{"version": version, "reason": REASON} for version in skipped],
    }])


def _descriptor(template_type: str = "fixes-java-run-fail") -> dict[str, Any]:
    return {
        "template_type": template_type,
        "base_commit": BASE_COMMIT,
        "issue_number": 9404,
        "library": {"coordinates": COORDINATES},
        "modifiers": {"chunked_dynamic_access": False, "chunk_final": True},
        "forge": {"monitored_branch": "master", "branch": "forge", "commit": "c" * 40},
    }


def _git(has_metadata_directory: bool, head_skipped: list[str], base_skipped: list[str]):
    """Stand in for the publisher's git reads of the head and base trees."""
    def fake_git(*args: str) -> str:
        if args[0] == "ls-tree":
            return f"{METADATA_PATH}/1.0.5.3\n" if has_metadata_directory else ""
        if args[0] == "show":
            commit = args[1].split(":", 1)[0]
            return _index(head_skipped if commit == HEAD_SHA else base_skipped)
        raise AssertionError(f"unexpected git call: {args}")
    return fake_git


class SkipRecordDetectionTests(unittest.TestCase):

    def _entries(self, **kwargs: Any) -> list[tuple[str, str]] | None:
        validated = publisher.ValidatedPublication({}, "descriptor.json", HEAD_SHA)
        with patch.object(publisher, "git", side_effect=_git(**kwargs)):
            return publisher._skip_record_entries(_descriptor(), validated)

    def test_added_skip_entries_without_metadata_are_a_skip_record(self) -> None:
        entries = self._entries(
            has_metadata_directory=False, head_skipped=["1.0.5.3", "1.0.5.4"], base_skipped=[],
        )

        self.assertEqual([("1.0.5.3", REASON), ("1.0.5.4", REASON)], entries)

    def test_a_contribution_that_still_ships_metadata_is_not_a_skip_record(self) -> None:
        entries = self._entries(
            has_metadata_directory=True, head_skipped=["1.0.5.3"], base_skipped=[],
        )

        self.assertIsNone(entries)

    def test_skip_entries_already_on_the_base_are_not_reported_again(self) -> None:
        entries = self._entries(
            has_metadata_directory=False, head_skipped=["1.0.5.3"], base_skipped=["1.0.5.3"],
        )

        self.assertIsNone(entries)

    def test_a_new_library_contribution_is_never_a_skip_record(self) -> None:
        validated = publisher.ValidatedPublication({}, "descriptor.json", HEAD_SHA)
        descriptor = _descriptor(template_type="library-new-request")

        with patch.object(publisher, "git", side_effect=_git(
                has_metadata_directory=False, head_skipped=["1.0.5.3"], base_skipped=[],
        )):
            self.assertIsNone(publisher._skip_record_entries(descriptor, validated))


class SkipRecordBodyTests(unittest.TestCase):

    def test_body_states_the_skip_and_drops_the_generated_sections(self) -> None:
        body: str = publisher._render_skip_record(
            _descriptor(), [("1.0.5.3", REASON), ("1.0.5.4", REASON)],
        )

        self.assertIn("Fixes: #9404", body)
        self.assertIn(f"records `{COORDINATES}` as a library version Native Image", body)
        self.assertIn("### Versions recorded as skipped", body)
        self.assertIn(f"| `1.0.5.3` | {REASON} |", body)
        self.assertIn(f"| `1.0.5.4` | {REASON} |", body)
        self.assertNotIn("Metadata entries:", body)
        self.assertNotIn("Library coverage percentage:", body)
        self.assertNotIn("Stats from", body)


if __name__ == "__main__":
    unittest.main()
