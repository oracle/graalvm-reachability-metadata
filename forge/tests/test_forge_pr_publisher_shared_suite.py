# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

"""Cover the shared-suite sections of library-update PR bodies (§AR-pr-body)."""

import importlib.util
import os
import sys
import unittest
from typing import Any

REPOSITORY_ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
PUBLISHER_PATH = os.path.join(
    REPOSITORY_ROOT, ".github", "scripts", "forge_pr_publisher", "publisher.py",
)


def _load_publisher() -> Any:
    """Load the trusted publisher the way Actions runs it: straight from the file."""
    spec = importlib.util.spec_from_file_location("forge_pr_publisher_shared_suite", PUBLISHER_PATH)
    module = importlib.util.module_from_spec(spec)
    sys.modules[spec.name] = module
    spec.loader.exec_module(module)
    return module


publisher = _load_publisher()


class SharedSuiteSectionTests(unittest.TestCase):
    def test_consumer_split_references_and_unblocks_its_follow_up_issue(self) -> None:
        section = publisher._format_consumer_split_section({
            "test_version": "4.17.0",
            "successor_metadata_version": "4.20.0",
            "failed_version": "4.20.0",
            "passing_consumers": [],
            "repointed_consumers": ["4.23.0"],
            "follow_up_issue_number": 1234,
        })

        self.assertIn("### Test-Version Consumer Split", section)
        self.assertIn("Consumers re-pointed to `4.20.0`: `4.23.0`", section)
        self.assertIn("Refs: #1234\n", section)
        self.assertIn("Forge-Unblocks-Issue: #1234\n", section)

    def test_native_sweep_lists_each_repair(self) -> None:
        section = publisher._format_native_sweep_section({
            "test_version": "1.0",
            "repairs": [
                {"kind": "metadata", "metadata_version": "2.0", "failed_version": "2.0"},
                {
                    "kind": "entry_split",
                    "metadata_version": "1.0",
                    "successor_metadata_version": "1.1",
                    "failed_version": "1.1",
                    "moved_versions": ["1.1", "1.2"],
                },
            ],
        })

        self.assertIn("- Metadata added to `2.0` for `2.0`\n", section)
        self.assertIn("- `1.1` split from `1.0` into a new entry with `1.1`, `1.2`\n", section)

    def test_absent_records_render_nothing(self) -> None:
        self.assertEqual("", publisher._format_consumer_split_section(None))
        self.assertEqual("", publisher._format_native_sweep_section(None))


if __name__ == "__main__":
    unittest.main()
