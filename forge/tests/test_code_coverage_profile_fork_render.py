# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

"""Tests for a group's diagnosis sections: each fork once, with only the
branches that lead to a target, then the catch boundaries and dispatch sites.

§AR-code-coverage-deep-navigation.3.2, §AR-code-coverage-deep-navigation.3.3
"""

import copy
import unittest

from utility_scripts.code_coverage_profile_diagnoses import Diagnosis, diagnosis_lines
from utility_scripts.code_coverage_profile_inputs import line_at


def _fork_not_taken(fork: dict, line: int) -> dict:
    return {
        "kind": "fork-not-taken",
        "target": {"sourcePath": fork["sourcePath"], "line": line,
                   "mi": 3, "ci": 0, "mb": 0, "cb": 0},
        "fork": fork,
    }


class ForkRenderingTests(unittest.TestCase):

    # `} else if (method != NO && method != FS) {` on line 313: both
    # conditions skip to line 320 when false.
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

    def test_only_branches_that_lead_to_a_target_are_listed_without_counts(self) -> None:
        lines: list[str] = diagnosis_lines([
            Diagnosis("deleteOldTempFiles()", _fork_not_taken(self.FORK, 320)),
        ])
        self.assertEqual(lines, [
            "",
            "Branches not taken:",
            "  `Database.java:313` (2 of 4 taken)",
            "    branch 2 at line 320: deleteOldTempFiles() at line 320",
            "    branch 4 at line 320: deleteOldTempFiles() at line 320",
        ])

    def test_a_branch_onto_a_later_condition_of_the_line_names_that_condition(self) -> None:
        fork: dict = copy.deepcopy(self.FORK)
        fork["branches"][0]["successors"][0]["reachesTarget"] = True
        lines: list[str] = diagnosis_lines([Diagnosis("lock()", _fork_not_taken(fork, 316))])
        self.assertEqual(lines[3], "    branch 1 at condition 2 of line 313: lock() at line 316")

    def test_targets_behind_one_fork_share_its_header(self) -> None:
        """`skipBracketedComment` and `read_` both wait behind the switch on
        line 171: the fork is listed once, its items in route order."""
        def switch(target_bci: int) -> dict:
            return {
                "sourcePath": "org/h2/command/Tokenizer.java", "line": 171,
                "mi": 0, "ci": 40, "mb": 1, "cb": 2, "evidence": "control-flow", "reach": 5143,
                "branches": [{"bci": 20, "blockStart": 0, "reach": 5143, "successors": [
                    {"bci": 30, "line": 187, "count": 357, "reachesTarget": target_bci == 30},
                    {"bci": 40, "line": 267, "count": 0, "reachesTarget": target_bci == 40},
                    {"bci": 50, "line": 505, "count": 0, "reachesTarget": target_bci == 50},
                ]}],
            }

        lines: list[str] = diagnosis_lines([
            Diagnosis("read_(...)", _fork_not_taken(switch(50), 505)),
            Diagnosis("skipBracketedComment(...)", _fork_not_taken(switch(40), 270)),
        ])
        self.assertEqual(lines[1:], [
            "Branches not taken:",
            "  `Tokenizer.java:171` (2 of 3 taken)",
            "    branch 3 at line 505: read_(...) at line 505",
            "    branch 2 at line 267: skipBracketedComment(...) at line 270",
        ])

    def test_a_wide_switch_numbers_its_item_among_every_successor(self) -> None:
        successors: list[dict] = [
            {"bci": 20 + index, "line": 50 + index, "count": 0, "reachesTarget": index == 11}
            for index in range(12)
        ]
        successors[0]["count"] = 77
        lines: list[str] = diagnosis_lines([Diagnosis("apply()", _fork_not_taken({
            "sourcePath": "a/Op.java", "line": 49, "mi": 0, "ci": 3, "mb": 11, "cb": 1,
            "evidence": "control-flow", "reach": 77,
            "branches": [{"bci": 4, "blockStart": 0, "reach": 77, "successors": successors}],
        }, 61))])
        self.assertEqual(lines[2:], [
            "  `Op.java:49` (1 of 12 taken)",
            "    branch 12 at line 61: apply() at line 61",
        ])

    def test_a_line_order_fork_names_only_the_call(self) -> None:
        fork: dict = {**self.FORK, "evidence": "line-order", "branches": None}
        lines: list[str] = diagnosis_lines([Diagnosis("open()", _fork_not_taken(fork, 322))])
        self.assertEqual(lines[3], "    untraced branch: open() at line 322")


class SectionTests(unittest.TestCase):

    CATCH: dict = {
        "kind": "no-fork",
        "target": {"sourcePath": "a/P.java", "line": 46, "mi": 3, "ci": 0, "mb": 0, "cb": 0},
        "nearestCovered": None,
        "exception": {"handlers": [{
            "bci": 14, "line": 45, "type": "java.lang.NumberFormatException",
            "sources": [{"line": 44, "count": None, "calls": ["Integer.parseInt"]}],
            "calls": [
                {"bci": 9, "line": 43, "callee": "java.lang.String#isEmpty():boolean",
                 "ran": True},
                {"bci": 12, "line": 44,
                 "callee": "java.lang.Integer#parseInt(java.lang.String):int", "ran": False},
            ],
        }]},
    }

    def test_a_catch_boundary_lists_only_the_calls_that_ran(self) -> None:
        self.assertEqual(diagnosis_lines([Diagnosis("handle()", self.CATCH)]), [
            "",
            "Reached only through an exception:",
            "  `P.java:46`: handle() in catch (NumberFormatException) at line 45",
            "    line 43: String.isEmpty() never threw it",
        ])

    def test_each_cause_has_one_section_in_a_fixed_order(self) -> None:
        dispatched: dict = {
            "kind": "dispatched-elsewhere",
            "target": {"sourcePath": "a/Q.java", "line": 7, "mi": 0, "ci": 2, "mb": 0, "cb": 0},
            "candidates": [], "site": None,
        }
        lines: list[str] = diagnosis_lines([
            Diagnosis("Impl.run()", dispatched),
            Diagnosis("handle()", self.CATCH),
            Diagnosis("lock()", _fork_not_taken(ForkRenderingTests.FORK, 320)),
        ])
        self.assertEqual(
            [line for line in lines if line.endswith(":") and not line.startswith(" ")],
            ["Branches not taken:", "Reached only through an exception:", "Dispatched elsewhere:"],
        )
        self.assertIn(
            "  `Q.java:7`: Impl.run() — same line, different implementation answered", lines,
        )

    def test_line_table_lookup_uses_the_last_entry_at_or_before_the_bci(self) -> None:
        self.assertEqual(line_at(((0, 10), (4, 11), (9, 12)), 8), 11)
        self.assertIsNone(line_at(((5, 10),), 2))


if __name__ == "__main__":
    unittest.main()
