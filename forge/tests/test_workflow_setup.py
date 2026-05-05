# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

import os
import tempfile
import unittest

from utility_scripts.workflow_setup import has_native_image_tooling


class WorkflowSetupTests(unittest.TestCase):
    def test_native_image_tooling_requires_agent_library(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            bin_dir = os.path.join(temp_dir, "bin")
            lib_dir = os.path.join(temp_dir, "lib")
            os.makedirs(bin_dir)
            os.makedirs(lib_dir)
            open(os.path.join(bin_dir, "native-image"), "w", encoding="utf-8").close()

            self.assertFalse(has_native_image_tooling(temp_dir))

            open(os.path.join(lib_dir, "libnative-image-agent.so"), "w", encoding="utf-8").close()

            self.assertTrue(has_native_image_tooling(temp_dir))


if __name__ == "__main__":
    unittest.main()
