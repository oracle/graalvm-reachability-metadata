# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

"""Compatibility re-export for ai_workflows.core.workflow_strategy."""
import sys

from ai_workflows.core import workflow_strategy as _workflow_strategy
from ai_workflows.core.workflow_strategy import *  # noqa: F401,F403

sys.modules[__name__] = _workflow_strategy
