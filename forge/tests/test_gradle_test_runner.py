# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

import unittest

from utility_scripts import gradle_test_runner


class DecodeSubprocessOutputTests(unittest.TestCase):
    def test_decodes_partial_timeout_bytes(self) -> None:
        self.assertEqual(
            gradle_test_runner._decode_subprocess_output(b"partial \xff output"),
            "partial \ufffd output",
        )

    def test_keeps_text_output(self) -> None:
        self.assertEqual(
            gradle_test_runner._decode_subprocess_output("partial output"),
            "partial output",
        )

    def test_returns_empty_text_for_missing_output(self) -> None:
        self.assertEqual(gradle_test_runner._decode_subprocess_output(None), "")


if __name__ == "__main__":
    unittest.main()
