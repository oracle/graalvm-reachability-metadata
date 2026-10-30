# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

"""The no-network-equivalent Docker gate on a daemon other Forge runs share.

A fake `docker` lists the images a state file holds, a test command "pulls" by
appending to it, and real launcher locks in a scratch Forge directory stand for
the other runs on the host.
"""
from __future__ import annotations

import contextlib
import io
import itertools
import os
import tempfile
import threading
import time
import unittest
from typing import Iterator
from unittest.mock import patch

from utility_scripts.docker_image_gate import names_image
from utility_scripts.issue_locks import issue_lock
from utility_scripts.local_ci_verification import (
    UNEXPECTED_DOCKER_IMAGE_FAILURE_MESSAGE,
    CommandRecord,
    LocalCIVerificationResult,
    _run_recorded_command_without_new_docker_images,
)

OWN_ISSUE: int = 4880
NGINX: str = "docker.io/library/nginx:1-alpine-slim 38ee1e3ae332"
PULL_NGINX: str = f"echo '{NGINX}' >> \"$FAKE_DOCKER_IMAGES\""
FAKE_DOCKER: str = "#!/bin/sh\ncat \"$FAKE_DOCKER_IMAGES\"\n"


class DockerImageGateTests(unittest.TestCase):
    def setUp(self) -> None:
        scratch: tempfile.TemporaryDirectory[str] = tempfile.TemporaryDirectory()
        self.addCleanup(scratch.cleanup)
        self.root: str = scratch.name
        self.forge: str = os.path.join(self.root, "forge")
        self.repo: str = os.path.join(self.root, "repo")
        bin_dir: str = os.path.join(self.root, "bin")
        for directory in (self.forge, self.repo, bin_dir):
            os.makedirs(directory)
        docker: str = os.path.join(bin_dir, "docker")
        with open(docker, "w", encoding="utf-8") as handle:
            handle.write(FAKE_DOCKER)
        os.chmod(docker, 0o755)
        images: str = os.path.join(self.root, "images.txt")
        with open(images, "w", encoding="utf-8") as handle:
            handle.write("docker.io/atmoz/sftp:alpine 4bb48d35a9eb\n")
        logs = (os.path.join(self.root, f"gate-{n}.log") for n in itertools.count())
        for patcher in (
                patch.dict(os.environ, {"PATH": f"{bin_dir}{os.pathsep}{os.environ['PATH']}",
                                        "FAKE_DOCKER_IMAGES": images}),
                patch("utility_scripts.docker_image_gate.FORGE_DIR", self.forge),
                patch("utility_scripts.local_ci_verification.build_timestamped_task_log_path",
                      side_effect=lambda *args: next(logs)),
                patch("utility_scripts.local_ci_verification.gradle_command_environment",
                      side_effect=lambda repo_path, env: env)):
            patcher.start()
            self.addCleanup(patcher.stop)
        # This run's own launcher holds its issue; that lock never counts as another run.
        own: contextlib.AbstractContextManager[bool] = issue_lock(self.forge, OWN_ISSUE)
        self.assertTrue(own.__enter__())
        self.addCleanup(own.__exit__, None, None, None)

    def gate(self, script: str) -> tuple[CommandRecord | None, LocalCIVerificationResult, str]:
        result = LocalCIVerificationResult(status="running", base_commit="base")
        log = io.StringIO()
        with contextlib.redirect_stdout(log):
            failed = _run_recorded_command_without_new_docker_images(
                self.repo, "run-consecutive-tests", ["sh", "-c", script], result, issue_number=OWN_ISSUE)
        return failed, result, log.getvalue()

    @contextlib.contextmanager
    def other_run(self, issue_number: int) -> Iterator[None]:
        with issue_lock(self.forge, issue_number) as held:
            self.assertTrue(held)
            yield

    def test_a_new_image_fails_when_no_other_run_is_live(self) -> None:
        failed, _, _ = self.gate(f"{PULL_NGINX}; echo BUILD SUCCESSFUL")
        self.assertIsNotNone(failed)
        self.assertEqual("unexpected-docker-image-pull", failed.gate)
        self.assertIn(UNEXPECTED_DOCKER_IMAGE_FAILURE_MESSAGE, failed.output_excerpt)
        self.assertIn(f"- {NGINX}", failed.output_excerpt)
        self.assertNotIn("Other Forge runs", failed.output_excerpt)

    def test_an_image_the_output_does_not_name_passes_with_a_warning_while_another_run_is_live(self) -> None:
        with self.other_run(7):
            failed, result, log = self.gate(f"{PULL_NGINX}; echo BUILD SUCCESSFUL")
        self.assertIsNone(failed)
        self.assertEqual([0], [record.returncode for record in result.commands])
        self.assertIn(f"WARNING: Docker image {NGINX} appeared", log)
        self.assertIn("a concurrent Forge run may have pulled it (live runs: #7)", log)
        self.assertNotIn(f"#{OWN_ISSUE}", log)

    def test_an_image_the_output_names_fails_while_another_run_is_live(self) -> None:
        with self.other_run(7):
            failed, _, log = self.gate(f"{PULL_NGINX}; echo \"Unable to find image 'nginx:1-alpine-slim' locally\"")
        self.assertIsNotNone(failed)
        self.assertIn(f"- {NGINX}", failed.output_excerpt)
        self.assertIn("Other Forge runs were live (#7), but the test output names each image above.",
                      failed.output_excerpt)
        self.assertNotIn("WARNING", log)

    def test_a_run_live_only_at_the_after_reading_counts_as_concurrent(self) -> None:
        started: str = os.path.join(self.root, "started")
        held: str = os.path.join(self.root, "held")
        release = threading.Event()

        def late_launcher() -> None:
            # It claims its issue only once the test command runs, after the before-reading.
            deadline: float = time.monotonic() + 30
            while not os.path.exists(started) and time.monotonic() < deadline:
                time.sleep(0.01)
            with issue_lock(self.forge, 9) as locked:
                self.assertTrue(locked)
                open(held, "w", encoding="utf-8").close()
                release.wait(30)

        launcher = threading.Thread(target=late_launcher)
        launcher.start()
        self.addCleanup(launcher.join)
        self.addCleanup(release.set)
        failed, _, log = self.gate(
            f"{PULL_NGINX}; touch '{started}'; "
            f"for _ in $(seq 600); do [ -e '{held}' ] && break; sleep 0.05; done; echo BUILD SUCCESSFUL")
        self.assertIsNone(failed)
        self.assertIn("a concurrent Forge run may have pulled it (live runs: #9)", log)

    def test_names_image_matches_the_repository_with_or_without_registry_and_tag(self) -> None:
        cases: list[tuple[str, str, bool]] = [
            (NGINX, "Unable to find image 'nginx:1-alpine-slim' locally", True),
            (NGINX, "1-alpine-slim: Pulling from library/nginx", True),
            (NGINX, "Pulling docker image: docker.io/library/nginx:1-alpine-slim. Please be patient", True),
            (NGINX, "started nginx.", True),
            ("nginx:1-alpine-slim 38ee1e3ae332", "Status: Downloaded newer image for docker.io/library/nginx", True),
            (NGINX, "reads /etc/nginx/nginx.conf", False),
            (NGINX, "docker run nginxinc/nginx-unprivileged", False),
            (NGINX, "docker run my-nginx:1", False),
            ("docker.io/atmoz/sftp:alpine 4bb48d35a9eb", "docker run --rm atmoz/sftp:alpine", True),
            ("docker.io/atmoz/sftp:alpine 4bb48d35a9eb", "ChannelSftpTest > uploads over sftp", False),
            ("quay.io/keycloak/keycloak:26.0 1a2b3c4d5e6f", "Creating container for image: keycloak/keycloak", True),
            ("quay.io/keycloak/keycloak:26.0 1a2b3c4d5e6f", "Starting keycloak", False),
            ("localhost:5000/demo:1 1a2b3c4d5e6f", "pushed localhost:5000/demo:1", True),
        ]
        for image, output, named in cases:
            with self.subTest(image=image, output=output):
                self.assertEqual(named, names_image(output, image))


if __name__ == "__main__":
    unittest.main()
