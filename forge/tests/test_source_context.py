# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

import json
import os
import tempfile
import unittest
from unittest.mock import patch

from utility_scripts.source_context import (
    SourceArtifactContext,
    populate_artifact_urls_if_needed,
    validate_artifact_url_templates,
)


class SourceContextArtifactUrlTests(unittest.TestCase):
    def test_skips_agentic_population_when_templates_render_for_version(self) -> None:
        with tempfile.TemporaryDirectory() as repo:
            self._write_index(repo)
            with (
                patch("utility_scripts.source_context.require_complete_reachability_repo"),
                patch(
                    "utility_scripts.source_context.download_source_artifact",
                    side_effect=self._available_artifact,
                ),
                patch("utility_scripts.source_context.populate_artifact_urls") as populate,
            ):
                validation = populate_artifact_urls_if_needed(repo, "org.example:demo:1.2.0")

            self.assertFalse(validation.needs_population)
            self.assertFalse(validation.overwrite_existing)
            populate.assert_not_called()

    def test_requests_overwrite_when_existing_template_does_not_resolve(self) -> None:
        with tempfile.TemporaryDirectory() as repo:
            self._write_index(repo)

            def download(base_dir: str, source_type: str, url: str) -> SourceArtifactContext:
                if source_type == "main":
                    return SourceArtifactContext(source_type, url, None, [], False, "404")
                return self._available_artifact(base_dir, source_type, url)

            with (
                patch("utility_scripts.source_context.require_complete_reachability_repo"),
                patch("utility_scripts.source_context.download_source_artifact", side_effect=download),
                patch("utility_scripts.source_context.populate_artifact_urls") as populate,
            ):
                validation = populate_artifact_urls_if_needed(repo, "org.example:demo:1.2.0")

            self.assertTrue(validation.needs_population)
            self.assertTrue(validation.overwrite_existing)
            self.assertIn("broken URL templates", validation.reasons[0])
            populate.assert_called_once_with(
                reachability_repo_path=repo,
                coordinate="org.example:demo:1.2.0",
                agent_command="codex -a never exec -s danger-full-access",
                overwrite_existing=True,
                verify_artifact_sources=True,
            )

    def test_requests_fill_only_when_required_fields_are_missing(self) -> None:
        with tempfile.TemporaryDirectory() as repo:
            self._write_index(repo, source_url=None)
            with patch("utility_scripts.source_context.download_source_artifact", side_effect=self._available_artifact):
                validation = validate_artifact_url_templates(repo, "org.example:demo:1.2.0")

            self.assertTrue(validation.needs_population)
            self.assertFalse(validation.overwrite_existing)
            self.assertIn("missing fields: source-code-url", validation.reasons)

    def _write_index(
            self,
            repo: str,
            source_url: str | None = "https://example.invalid/demo/$version$/sources.jar",
    ) -> None:
        index_dir = os.path.join(repo, "metadata", "org.example", "demo")
        os.makedirs(index_dir, exist_ok=True)
        entry = {
            "metadata-version": "1.0.0",
            "repository-url": "https://example.invalid/demo",
            "test-code-url": "https://example.invalid/demo/$version$/tests.jar",
            "documentation-url": "https://example.invalid/demo/$version$/docs.jar",
            "description": "Example library. It is used in tests.",
            "tested-versions": ["1.0.0", "1.2.0"],
        }
        if source_url is not None:
            entry["source-code-url"] = source_url
        with open(os.path.join(index_dir, "index.json"), "w", encoding="utf-8") as index_file:
            json.dump([entry], index_file)

    def _available_artifact(self, base_dir: str, source_type: str, url: str) -> SourceArtifactContext:
        return SourceArtifactContext(source_type, url, base_dir, [os.path.join(base_dir, "content")], True)


if __name__ == "__main__":
    unittest.main()
