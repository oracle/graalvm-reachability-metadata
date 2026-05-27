# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

"""Compatibility wrapper for ``ai_workflows.drivers.fix_javac_fail``."""

import os
import sys

if __package__ in (None, ""):
    sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

from ai_workflows.drivers import fix_javac_fail as _impl

if __name__ == "__main__":
    sys.exit(_impl.main())

sys.modules[__name__] = _impl
