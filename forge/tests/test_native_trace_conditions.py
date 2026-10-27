# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

"""Unit tests for native-trace condition-package derivation."""

from __future__ import annotations

import sys
import tempfile
import unittest
from pathlib import Path

# Tests run from the forge/ directory in CI; make the package imports work
# whether the test is invoked via pytest or `python -m unittest`.
_FORGE_ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(_FORGE_ROOT))

from utility_scripts import native_trace_conditions as conditions  # noqa: E402

from tests.native_gate_support import (  # noqa: E402
    _make_complete_reachability_repo,
    _rmtree,
    _write_user_code_filter,
)


class ConditionPackageDerivationTests(unittest.TestCase):
    """Trace condition packages come from library code, not only Maven groups."""

    def setUp(self) -> None:
        self.repo = tempfile.mkdtemp(prefix="repo-")
        self.addCleanup(_rmtree, self.repo)
        _make_complete_reachability_repo(self.repo)

    def test_derives_package_roots_from_user_code_filter_in_order(self) -> None:
        _write_user_code_filter(
            self.repo,
            "org.example:demo:1.0",
            [
                {"excludeClasses": "**"},
                {"includeClasses": "org.example.demo.**"},
                {"includeClasses": "org.example.spi.*"},
                {"includeClasses": "org.example.SingleType"},
                {"includeClasses": "invalid*pattern"},
                {"includeClasses": "org.example.demo.**"},
            ],
        )

        self.assertEqual(
            conditions._condition_packages_from_user_code_filter(self.repo, "org.example:demo:1.0"),
            ["org.example.demo", "org.example.spi", "org.example"],
        )

    def test_excludes_generated_test_packages_from_condition_packages(self) -> None:
        _write_user_code_filter(
            self.repo,
            "org.apache.tomcat.embed:tomcat-embed-core:11.0.18",
            [
                {"excludeClasses": "**"},
                {"includeClasses": "org.apache.catalina.**"},
                {"includeClasses": "org.apache.tomcat.**"},
                {"includeClasses": "tomcat.**"},
            ],
        )
        test_source = Path(
            self.repo,
            "tests",
            "src",
            "org.apache.tomcat.embed",
            "tomcat-embed-core",
            "11.0.18",
            "src",
            "test",
            "java",
            "tomcat",
            "BootstrapTest.java",
        )
        test_source.parent.mkdir(parents=True, exist_ok=True)
        test_source.write_text("package tomcat;\nclass BootstrapTest {}\n", encoding="utf-8")

        self.assertEqual(
            conditions._condition_packages_from_user_code_filter(
                self.repo,
                "org.apache.tomcat.embed:tomcat-embed-core:11.0.18",
            ),
            ["org.apache.catalina", "org.apache.tomcat"],
        )

    def test_keeps_test_package_when_it_overlaps_maven_group(self) -> None:
        _write_user_code_filter(
            self.repo,
            "com.example:demo:1.0",
            [
                {"excludeClasses": "**"},
                {"includeClasses": "com.example.tests.**"},
            ],
        )
        test_source = Path(
            self.repo,
            "tests",
            "src",
            "com.example",
            "demo",
            "1.0",
            "src",
            "test",
            "java",
            "com",
            "example",
            "tests",
            "DemoTest.java",
        )
        test_source.parent.mkdir(parents=True, exist_ok=True)
        test_source.write_text("package com.example.tests;\nclass DemoTest {}\n", encoding="utf-8")

        self.assertEqual(
            conditions._condition_packages_from_user_code_filter(self.repo, "com.example:demo:1.0"),
            ["com.example.tests"],
        )


if __name__ == "__main__":
    unittest.main()
