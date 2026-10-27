# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

"""Cover fix-PR stats comparisons rendered from exploded stats files (§AR-pr-body)."""

import importlib.util
import os
import sys
import unittest
from typing import Any
from unittest.mock import patch

REPOSITORY_ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
PUBLISHER_PATH = os.path.join(
    REPOSITORY_ROOT, ".github", "scripts", "forge_pr_publisher", "publisher.py",
)


def _load_publisher() -> Any:
    """Load the trusted publisher the way Actions runs it: straight from the file."""
    spec = importlib.util.spec_from_file_location("forge_pr_publisher_stats", PUBLISHER_PATH)
    module = importlib.util.module_from_spec(spec)
    sys.modules[spec.name] = module
    spec.loader.exec_module(module)
    return module


publisher = _load_publisher()


def _stats_document(
        version: str,
        *,
        covered: int,
        total: int,
        ratio: float,
) -> dict[str, Any]:
    return {
        "versions": [{
            "version": version,
            "dynamicAccess": {
                "breakdown": {
                    "reflection": {
                        "coveredCalls": 1,
                        "totalCalls": 1,
                        "coverageRatio": 1.0,
                    },
                },
                "coveredCalls": 1,
                "totalCalls": 1,
                "coverageRatio": 1.0,
            },
            "libraryCoverage": {
                "line": {
                    "covered": covered,
                    "total": total,
                    "ratio": ratio,
                },
            },
        }],
    }


class FixPublisherStatsTests(unittest.TestCase):

    def test_stats_diff_unwraps_coordinate_entries(self) -> None:
        validated = publisher.ValidatedPublication({}, "descriptor.json", "a" * 40)
        old_coordinates = "org.junit.jupiter:junit-jupiter-api:5.8.2"
        new_coordinates = "org.junit.jupiter:junit-jupiter-api:5.11.4"
        documents: dict[str, dict[str, Any]] = {
            "stats/org.junit.jupiter/junit-jupiter-api/5.8.2/stats.json": _stats_document(
                "5.8.2", covered=97, total=2238, ratio=0.043342,
            ),
            "stats/org.junit.jupiter/junit-jupiter-api/5.11.4/stats.json": _stats_document(
                "5.11.4", covered=110, total=2263, ratio=0.048608,
            ),
        }

        with patch.object(
                publisher,
                "read_json_at_commit",
                side_effect=lambda _commit, path: documents[path],
        ):
            comparison: str = publisher._format_stats_diff(
                validated, old_coordinates, new_coordinates,
            )

        self.assertIn("#### Dynamic access coverage", comparison)
        self.assertIn(f"- {old_coordinates}: 1/1 covered calls (100.00%)", comparison)
        self.assertIn(f"- {new_coordinates}: 1/1 covered calls (100.00%)", comparison)
        self.assertIn(f"- {old_coordinates}: 97/2238 (4.33%)", comparison)
        self.assertIn(f"- {new_coordinates}: 110/2263 (4.86%)", comparison)

    def test_stats_loader_rejects_a_document_without_the_coordinate_version(self) -> None:
        validated = publisher.ValidatedPublication({}, "descriptor.json", "a" * 40)

        with patch.object(
                publisher,
                "read_json_at_commit",
                return_value=_stats_document("2.0.0", covered=1, total=2, ratio=0.5),
        ):
            stats: dict[str, Any] | None = publisher._load_stats_at_head(
                validated, "com.example:demo:1.0.0",
            )

        self.assertIsNone(stats)


if __name__ == "__main__":
    unittest.main()
