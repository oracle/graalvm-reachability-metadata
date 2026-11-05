# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

"""`publish` pushes again after a transient push failure, and never after a refusal.

§FS-human-intervention-policy, §FS-forge-host-requirements.1

The run is brought up to `publish` over the fake repository of `support`; a
`git` on PATH answers the first pushes as GitHub did, then pushes for real.
"""
from __future__ import annotations

import contextlib
import io
import os
import shutil
import stat
import subprocess
import unittest
from typing import Any
from unittest import mock

from test_audit import FinalizedRun

import forge_run  # noqa: E402
import publish  # noqa: E402
from exit_codes import INFRASTRUCTURE, OK, RETURN_AUDIT  # noqa: E402
from run_state import read_json, runtime_path  # noqa: E402
from utility_scripts.host_probes import is_git_transient_failure_text  # noqa: E402

# What GitHub answered on #3300, and a connection it drops mid-push: both clear on their own.
FAILED: str = (" ! [remote rejected]       HEAD -> ai/octocat/lib (failed)\n"
               "error: failed to push some refs to 'https://github.com/oracle/graalvm-reachability-metadata.git'\n")
HUNG_UP: str = ("error: RPC failed; curl 92 HTTP/2 stream 0 was not closed cleanly: INTERNAL_ERROR (err 2)\n"
                "send-pack: unexpected disconnect while reading sideband packet\n"
                "fatal: the remote end hung up unexpectedly\n")
# A refusal with a reason, which GitHub would give again.
PROTECTED: str = ("remote: error: GH006: Protected branch update failed for refs/heads/ai/octocat/lib.\n"
                  " ! [remote rejected]       HEAD -> ai/octocat/lib (protected branch hook declined)\n"
                  "error: failed to push some refs to 'https://github.com/oracle/graalvm-reachability-metadata.git'\n")
# A `git` that fails the pushes it has an answer for, counting every push, and runs the real git otherwise.
FAKE_GIT: str = """#!/bin/sh
if [ "$1" = push ]; then
  count=$(( $(cat "{answers}/pushes" 2>/dev/null || echo 0) + 1 ))
  echo "$count" > "{answers}/pushes"
  if [ -f "{answers}/push-$count" ]; then
    cat "{answers}/push-$count" >&2
    exit 1
  fi
fi
exec "{git}" "$@"
"""


class PushRetryTest(FinalizedRun):

    def setUp(self) -> None:
        super().setUp()
        self.answers: str = os.path.join(self.fake.tmp, "push-answers")
        os.makedirs(self.answers)

    def publish(self, *answers: str) -> tuple[int, str]:
        """Bring the run to `publish`, answer its first pushes with `answers`, and return its exit and stderr."""
        self.repaired()
        self.assertEqual(RETURN_AUDIT, self.audited())
        self.assertEqual(OK, self.step("audit"))
        for number, answer in enumerate(answers, start=1):
            with open(os.path.join(self.answers, f"push-{number}"), "w", encoding="utf-8") as handle:
                handle.write(answer)
        bin_dir: str = os.path.join(self.fake.tmp, "push-bin")
        os.makedirs(bin_dir)
        fake_git: str = os.path.join(bin_dir, "git")
        with open(fake_git, "w", encoding="utf-8") as handle:
            handle.write(FAKE_GIT.format(answers=self.answers, git=shutil.which("git")))
        os.chmod(fake_git, os.stat(fake_git).st_mode | stat.S_IEXEC)
        stderr: io.StringIO = io.StringIO()
        # Only the push's backoff is skipped and recorded; `publish` sleeps for nothing else.
        with mock.patch.dict(os.environ, {"PATH": bin_dir + os.pathsep + os.environ["PATH"]}), \
                mock.patch.object(publish.time, "sleep") as self.sleep, contextlib.redirect_stderr(stderr):
            code: int = self.step("publish")
        return code, stderr.getvalue()

    def pushes(self) -> int:
        with open(os.path.join(self.answers, "pushes"), encoding="utf-8") as handle:
            return int(handle.read())

    def published_branches(self) -> str:
        return subprocess.run(["git", "ls-remote", "--heads", self.fake.remote, "ai/*"], check=True,
                              stdout=subprocess.PIPE, text=True).stdout

    def assert_infrastructure(self) -> None:
        outcome: dict[str, Any] = read_json(runtime_path(self.fake.workspace, forge_run.OUTCOME))
        self.assertEqual(("infrastructure", "publish: infrastructure failure"), (outcome["kind"], outcome["reason"]))
        self.assertEqual("", self.published_branches())

    def test_a_transient_failure_is_pushed_again_and_the_run_completes(self) -> None:
        code, stderr = self.publish(FAILED, HUNG_UP)
        self.assertEqual(OK, code)
        self.assertEqual(3, self.pushes())
        self.assertEqual([mock.call(15.0), mock.call(30.0)], self.sleep.call_args_list)
        self.assertEqual("completed", self.outcome())
        # The commit the run finished is the one GitHub has.
        head: str = subprocess.run(["git", "rev-parse", "HEAD"], cwd=self.state.worktree, check=True,
                                   stdout=subprocess.PIPE, text=True).stdout.strip()
        self.assertIn(f"{head}\trefs/heads/ai/octocat/", self.published_branches())
        retries: list[str] = [line for line in stderr.splitlines() if "failed transiently" in line]
        self.assertEqual(2, len(retries))
        self.assertIn("(failed); pushing again in 15s (attempt 2/4)", retries[0])
        self.assertIn("fatal: the remote end hung up unexpectedly; pushing again in 30s (attempt 3/4)", retries[1])

    def test_a_refusal_fails_at_once_as_infrastructure(self) -> None:
        code, stderr = self.publish(PROTECTED)
        self.assertEqual(INFRASTRUCTURE, code)
        self.assertEqual(1, self.pushes())
        self.sleep.assert_not_called()
        self.assertIn("(protected branch hook declined)", stderr)
        self.assert_infrastructure()

    def test_transient_failures_that_outlast_the_retries_fail_as_infrastructure(self) -> None:
        code, stderr = self.publish(*[FAILED] * publish.PUSH_ATTEMPTS)
        self.assertEqual(INFRASTRUCTURE, code)
        self.assertEqual(publish.PUSH_ATTEMPTS, self.pushes())
        self.assertEqual([mock.call(15.0), mock.call(30.0), mock.call(60.0)], self.sleep.call_args_list)
        self.assertEqual(publish.PUSH_ATTEMPTS - 1, stderr.count("failed transiently"))
        self.assert_infrastructure()


class GitTransientFailureTextTest(unittest.TestCase):

    def test_an_outage_is_transient(self) -> None:
        for text in (FAILED, HUNG_UP,
                     "fatal: unable to access 'https://github.com/o/r.git/': Could not resolve host: github.com",
                     "fatal: unable to access 'https://github.com/o/r.git/': Failed to connect to github.com port 443",
                     "fatal: unable to access 'https://github.com/o/r.git/': The requested URL returned error: 502",
                     "fatal: unable to access 'https://github.com/o/r.git/': The requested URL returned error: 429",
                     "error: RPC failed; HTTP 500 curl 22 The requested URL returned error: 500",
                     "ssh: connect to host github.com port 22: Connection timed out"):
            with self.subTest(text=text):
                self.assertTrue(is_git_transient_failure_text(text))

    def test_a_refusal_with_a_reason_is_not_transient(self) -> None:
        for text in (PROTECTED,
                     " ! [remote rejected] HEAD -> ai/x (push declined due to repository rule violations)",
                     "remote: hook says no\n ! [remote rejected] HEAD -> ai/x (pre-receive hook declined)",
                     "remote: Permission to o/r.git denied to bot.\n"
                     "fatal: unable to access 'https://github.com/o/r.git/': The requested URL returned error: 403",
                     "remote: Repository not found.\nfatal: repository 'https://github.com/o/r.git/' not found",
                     # A refused request names its status even when the connection then drops.
                     "error: RPC failed; HTTP 413 curl 22 The requested URL returned error: 413\n"
                     "fatal: the remote end hung up unexpectedly"):
            with self.subTest(text=text):
                self.assertFalse(is_git_transient_failure_text(text))


if __name__ == "__main__":
    unittest.main()
