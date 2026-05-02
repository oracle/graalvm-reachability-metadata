# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

import os
import tempfile
import unittest

from git_scripts.make_pr_improve_coverage import _parse_pr_number as parse_improve_pr_number
from git_scripts.make_pr_new_library_support import _parse_pr_number as parse_new_lib_pr_number
from utility_scripts.large_library_progress import (
    LargeLibraryProgressState,
    copy_progress_artifacts,
    find_progress_state_path,
)


class ParsePrNumberTests(unittest.TestCase):
    def test_pr_number_extracted_from_url(self) -> None:
        output = "https://github.com/oracle/graalvm-reachability-metadata/pull/4242\n"
        self.assertEqual(parse_new_lib_pr_number(output), 4242)
        self.assertEqual(parse_improve_pr_number(output), 4242)

    def test_pr_number_ignores_org_or_repo_with_digits(self) -> None:
        output = "https://github.com/owner123/repo456/pull/77\n"
        self.assertEqual(parse_new_lib_pr_number(output), 77)
        self.assertEqual(parse_improve_pr_number(output), 77)

    def test_pr_number_returns_none_when_url_absent(self) -> None:
        self.assertIsNone(parse_new_lib_pr_number(""))
        self.assertIsNone(parse_improve_pr_number("Pull request already exists for branch foo.\n"))


class LargeLibraryProgressStateTests(unittest.TestCase):
    def test_state_round_trip_preserves_part_and_class_sets(self) -> None:
        with tempfile.TemporaryDirectory() as tmpdir:
            state = LargeLibraryProgressState.create(
                coordinate="org.example:lib:1.0.0",
                issue_number=4241,
                request_label="library-new-request",
                strategy_name="dynamic_access_main_sources_pi_gpt-5.5",
            )
            state.mark_class_order(["a.A", "b.B"])
            state.mark_completed("a.A")
            state.mark_exhausted("b.B")
            state.update_coverage(10, 40)
            path = state.default_path(tmpdir)
            state.save(path)

            loaded = LargeLibraryProgressState.load(path)

            self.assertEqual(loaded.series_id, "org.example-lib-1.0.0-4241")
            self.assertEqual(loaded.part, 1)
            self.assertEqual(loaded.class_order, ["a.A", "b.B"])
            self.assertEqual(loaded.completed_classes, ["a.A"])
            self.assertEqual(loaded.exhausted_classes, ["b.B"])
            self.assertEqual(loaded.covered_calls, 10)
            self.assertEqual(loaded.total_calls, 40)

    def test_copy_progress_artifacts_preserves_state_before_scratch_cleanup(self) -> None:
        with tempfile.TemporaryDirectory() as scratch, tempfile.TemporaryDirectory() as canonical:
            state = LargeLibraryProgressState.create(
                coordinate="org.example:lib:1.0.0",
                issue_number=4241,
                request_label="library-update-request",
                strategy_name="coverage_only_pi_gpt-5.5",
            )
            state.save(state.default_path(scratch))

            copy_progress_artifacts(scratch, canonical, 4241)

            copied_path = find_progress_state_path(canonical, 4241)
            self.assertIsNotNone(copied_path)
            self.assertTrue(os.path.isfile(copied_path))
            self.assertEqual(LargeLibraryProgressState.load(copied_path).coordinate, "org.example:lib:1.0.0")


if __name__ == "__main__":
    unittest.main()
