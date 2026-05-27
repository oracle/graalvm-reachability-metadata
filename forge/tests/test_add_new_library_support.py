# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

import unittest

from ai_workflows.drivers.add_new_library_support import _should_create_failure_run_metrics
from ai_workflows.core.workflow_strategy import (
    RUN_STATUS_CHUNK_READY,
    RUN_STATUS_FAILURE,
    RUN_STATUS_SUCCESS,
    SUCCESS_WITH_INTERVENTION_STATUS,
)


class AddNewLibrarySupportTests(unittest.TestCase):
    def test_failure_after_generated_tests_uses_failure_metrics(self) -> None:
        self.assertTrue(_should_create_failure_run_metrics(RUN_STATUS_FAILURE, 1, False))

    def test_successful_statuses_with_generated_tests_use_normal_metrics(self) -> None:
        for status in (RUN_STATUS_SUCCESS, RUN_STATUS_CHUNK_READY, SUCCESS_WITH_INTERVENTION_STATUS):
            with self.subTest(status=status):
                self.assertFalse(_should_create_failure_run_metrics(status, 1, False))

    def test_no_generated_tests_uses_failure_metrics_except_intervention_status(self) -> None:
        self.assertTrue(_should_create_failure_run_metrics(RUN_STATUS_SUCCESS, 0, False))
        self.assertFalse(_should_create_failure_run_metrics(SUCCESS_WITH_INTERVENTION_STATUS, 0, False))

    def test_scaffold_placeholder_gate_uses_failure_metrics(self) -> None:
        self.assertTrue(_should_create_failure_run_metrics(RUN_STATUS_SUCCESS, 1, True))


if __name__ == "__main__":
    unittest.main()
