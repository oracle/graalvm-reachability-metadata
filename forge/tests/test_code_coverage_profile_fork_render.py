# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

"""Tests for the fork hint's rendering: one numbered line per successor.

§AR-code-coverage-deep-navigation.3.2
"""

import unittest

from utility_scripts.code_coverage_profile_inputs import line_at
from utility_scripts.code_coverage_profile_render import classification_lines


class ForkRenderingTests(unittest.TestCase):

    FORK: dict = {
        "sourcePath": "org/h2/engine/Database.java", "line": 313,
        "mi": 0, "ci": 9, "mb": 2, "cb": 2, "evidence": "control-flow", "reach": 40182,
        "branches": [
            {"bci": 3, "blockStart": 0, "reach": 40182, "successors": [
                {"bci": 6, "line": 313, "count": 40182, "reachesTarget": False},
                {"bci": 15, "line": 320, "count": 0, "reachesTarget": True},
            ]},
            {"bci": 7, "blockStart": 6, "reach": 40182, "successors": [
                {"bci": 10, "line": 314, "count": 40182, "reachesTarget": False},
                {"bci": 15, "line": 320, "count": None, "reachesTarget": True},
            ]},
        ],
    }

    def _lines(self, counted: bool) -> list[str]:
        return classification_lines({
            "kind": "fork-not-taken",
            "target": {"sourcePath": "org/h2/engine/Database.java", "line": 322,
                       "mi": 3, "ci": 0, "mb": 0, "cb": 0},
            "fork": self.FORK,
        }, counted)

    def test_each_branch_is_one_numbered_line_with_landing_and_count(self) -> None:
        self.assertEqual(self._lines(counted=True)[1:], [
            "  fork `Database.java:313` reached 40,182×, 2 of 4 branches taken",
            "    branch 1 → condition 2 ×40,182",
            "    branch 2 → line 320 ×0 ← target",
            "    branch 3 → line 314 ×40,182",
            "    branch 4 → line 320 (no counter) ← target",
        ])

    def test_without_counters_the_hint_keeps_landings_and_the_target_marker(self) -> None:
        self.assertEqual(
            self._lines(counted=False)[2:4],
            ["    branch 1 → condition 2", "    branch 2 → line 320 ← target"],
        )

    def test_a_single_line_conditional_keeps_its_two_branches_apart(self) -> None:
        """`return c != null ? c : parse(key)`: both sides land on the fork
        line itself, so only the number, count, and marker tell them apart."""
        lines: list[str] = classification_lines({
            "kind": "fork-not-taken",
            "target": {"sourcePath": "a/Config.java", "line": 44,
                       "mi": 2, "ci": 3, "mb": 1, "cb": 1},
            "fork": {
                "sourcePath": "a/Config.java", "line": 44,
                "mi": 2, "ci": 3, "mb": 1, "cb": 1, "evidence": "control-flow", "reach": 1204,
                "branches": [{"bci": 9, "blockStart": 0, "reach": 1204, "successors": [
                    {"bci": 12, "line": 44, "count": 1204, "reachesTarget": False},
                    {"bci": 16, "line": 44, "count": 0, "reachesTarget": True},
                ]}],
            },
        }, counted=True)
        self.assertEqual(lines[1:], [
            "  fork `Config.java:44` reached 1,204×, 1 of 2 branches taken",
            "    branch 1 → line 44 ×1,204",
            "    branch 2 → line 44 ×0 ← target",
        ])

    def test_a_wide_switch_keeps_numbering_when_it_omits_cold_cases(self) -> None:
        successors: list[dict] = [
            {"bci": 20 + index, "line": 50 + index, "count": 0, "reachesTarget": index == 11}
            for index in range(12)
        ]
        successors[0]["count"] = 77
        lines: list[str] = classification_lines({
            "kind": "fork-not-taken",
            "target": {"sourcePath": "a/Op.java", "line": 61,
                       "mi": 2, "ci": 0, "mb": 0, "cb": 0},
            "fork": {
                "sourcePath": "a/Op.java", "line": 49, "mi": 0, "ci": 3, "mb": 11, "cb": 1,
                "evidence": "control-flow", "reach": 77,
                "branches": [{"bci": 4, "blockStart": 0, "reach": 77, "successors": successors}],
            },
        }, counted=True)
        self.assertEqual(lines[2], "    branch 1 → line 50 ×77")
        self.assertEqual(lines[-2], "    branch 12 → line 61 ×0 ← target")
        self.assertEqual(lines[-1], "    … 4 more")

    def test_a_catch_boundary_without_counters_still_names_the_raising_lines(self) -> None:
        lines: list[str] = classification_lines({
            "kind": "no-fork",
            "target": {"sourcePath": "a/P.java", "line": 46, "mi": 3, "ci": 0, "mb": 0, "cb": 0},
            "nearestCovered": None,
            "exception": {"handlers": [{
                "bci": 14, "line": 45, "type": "java.lang.NumberFormatException",
                "sources": [{"line": 44, "count": None, "calls": ["Integer.parseInt", "Integer.parseInt"]}],
            }]},
        }, counted=False)
        self.assertEqual(lines[1:], [
            "  reached only through catch (NumberFormatException) at line 45",
            "    line 44 `Integer.parseInt` ran, never threw it",
        ])

    def test_line_table_lookup_uses_the_last_entry_at_or_before_the_bci(self) -> None:
        self.assertEqual(line_at(((0, 10), (4, 11), (9, 12)), 8), 11)
        self.assertIsNone(line_at(((5, 10),), 2))


if __name__ == "__main__":
    unittest.main()
