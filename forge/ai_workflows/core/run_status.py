# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

"""Workflow run status values shared by strategies and drivers.

The terminal statuses a strategy run or finalization can report
(§AR-forge-workflow-pipeline), kept import-cycle free so both the strategy
base class and its mixin modules can depend on them.
"""

RUN_STATUS_SUCCESS = "success"
RUN_STATUS_FAILURE = "failure"
SUCCESS_WITH_INTERVENTION_STATUS = "success_with_intervention"
RUN_STATUS_CHUNK_READY = "chunk_ready"
