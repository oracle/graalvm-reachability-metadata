# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

"""Tests for the `run-forge.sh` loop, driven against stub tools.

§AR-forge-workflow-pipeline.1 fixes the rotation, §FS-published-pr-review.1 the review cadence it
keeps, and §FS-forge-host-requirements.1 the waits on a transient host failure and a failed cycle.
"""
from __future__ import annotations

import json
import os
import shutil
import subprocess
import sys
import tempfile
import unittest

FORGE_DIR: str = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
NEW: str = "library-new-request"
UPDATE: str = "library-update-request"
JAVA_RUN: str = "fails-java-run"
# Records each call of the launcher; the host check exits with its next scripted status, then
# passes until its budget is spent, which ends the loop, and the claim cycle exits with the next
# scripted status, 0 once they run out.
PYTHON_STUB: str = f"""#!{sys.executable}
import json, os, sys
log = os.environ["FORGE_TEST_LOG"]
with open(log, encoding="utf-8") as handle:
    events = [json.loads(line) for line in handle]
script, arguments = os.path.basename(sys.argv[1]), sys.argv[2:]
if script == "host_requirements.py":
    event, status = "host", 0
    hosts = sum(1 for logged in events if logged["event"] == "host")
    host_statuses = json.loads(os.environ["FORGE_TEST_HOST_STATUSES"])
    if hosts < len(host_statuses):
        status = host_statuses[hosts]
    elif hosts >= int(os.environ["FORGE_TEST_HOSTS"]):
        status = 1
elif "--review" in arguments:
    event, status = "review", 0
else:
    event = arguments[arguments.index("--label") + 1] if "--label" in arguments else "every queue"
    statuses = json.loads(os.environ["FORGE_TEST_STATUSES"])
    claims = sum(1 for logged in events if logged["event"] not in ("host", "review", "sleep"))
    status = statuses[claims] if claims < len(statuses) else 0
with open(log, "a", encoding="utf-8") as handle:
    handle.write(json.dumps({{"event": event, "arguments": arguments}}) + "\\n")
sys.exit(status)
"""
SLEEP_STUB: str = """#!/usr/bin/env bash
printf '{"event": "sleep", "arguments": ["%s"]}\\n' "$1" >> "$FORGE_TEST_LOG"
"""


class LabelRotationTest(unittest.TestCase):

    def setUp(self) -> None:
        root: str = tempfile.mkdtemp()
        self.addCleanup(shutil.rmtree, root, True)
        # A copy of the launcher without a .venv, so python3 on PATH serves the host check and
        # forge_metadata.py alike.
        self.forge: str = os.path.join(root, "forge")
        os.makedirs(os.path.join(self.forge, "utility_scripts"))
        for name in ("run-forge.sh", os.path.join("utility_scripts", "rhei_launcher_host.sh")):
            shutil.copy2(os.path.join(FORGE_DIR, name), os.path.join(self.forge, name))
        self.bin: str = os.path.join(root, "bin")
        os.makedirs(self.bin)
        stubs: dict[str, str] = {
            "gh": "#!/usr/bin/env bash\nprintf 'gh version 2.39.0 (fake)\\n'\n",
            "rhei": "#!/usr/bin/env bash\nexit 0\n",
            "python3": PYTHON_STUB,
            "sleep": SLEEP_STUB,
        }
        for name, source in stubs.items():
            with open(os.path.join(self.bin, name), "w", encoding="utf-8") as handle:
                handle.write(source)
            os.chmod(os.path.join(self.bin, name), 0o755)
        self.log: str = os.path.join(root, "calls.jsonl")
        open(self.log, "w", encoding="utf-8").close()

    def launch(self, *arguments: str, iterations: int = 0, statuses: tuple[int, ...] = (),
               host_statuses: tuple[int, ...] = ()) -> tuple[subprocess.CompletedProcess, list[dict]]:
        """Run the launcher for `iterations` loop iterations; return its process and its calls."""
        environment: dict[str, str] = {
            **os.environ, "PATH": f"{self.bin}{os.pathsep}{os.environ['PATH']}", "FORGE_TEST_LOG": self.log,
            "FORGE_TEST_HOSTS": str(iterations), "FORGE_TEST_STATUSES": json.dumps(list(statuses)),
            "FORGE_TEST_HOST_STATUSES": json.dumps(list(host_statuses)),
        }
        completed = subprocess.run([os.path.join(self.forge, "run-forge.sh"), *arguments], env=environment,
                                   stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True, timeout=60)
        with open(self.log, encoding="utf-8") as handle:
            calls: list[dict] = [json.loads(line) for line in handle]
        return completed, calls

    def events(self, *arguments: str, iterations: int, statuses: tuple[int, ...] = ()) -> list[str]:
        """The calls of a loop that stopped at its host check after `iterations` iterations."""
        completed, calls = self.launch(*arguments, iterations=iterations, statuses=statuses)
        self.assertEqual(1, completed.returncode, completed.stderr)
        self.assertEqual("host", calls[-1]["event"])
        return [call["event"] for call in calls[:-1]]

    def test_labels_take_their_counts_in_turn_and_the_review_every_third_run(self) -> None:
        events: list[str] = self.events("--label", f"{NEW}:2", "--label", UPDATE, "--label", JAVA_RUN,
                                        iterations=9)
        self.assertEqual(9, events.count("host"))
        self.assertEqual([NEW, NEW, UPDATE, "review", JAVA_RUN, NEW, NEW, "review", UPDATE, JAVA_RUN, NEW, "review"],
                         [event for event in events if event != "host"])

    def test_a_count_defaults_to_one(self) -> None:
        self.assertEqual(
            ["host", JAVA_RUN, "host", UPDATE, "host", JAVA_RUN, "review"],
            self.events("--label", JAVA_RUN, "--label", UPDATE, iterations=3))

    def test_an_empty_queue_passes_its_turns_on_within_one_host_check(self) -> None:
        self.assertEqual(
            ["host", NEW, UPDATE, "host", NEW, UPDATE],
            self.events("--label", f"{NEW}:2", "--label", UPDATE, iterations=2, statuses=(1, 0, 1, 0)))

    def test_a_rotation_with_nothing_to_claim_reviews_waits_and_keeps_its_turn(self) -> None:
        # The second turn of the new-library queue comes after the idle wait, then fails-java-run.
        self.assertEqual(
            ["host", NEW, "host", NEW, JAVA_RUN, "review", "sleep", "host", NEW, "host", JAVA_RUN, "review"],
            self.events("--label", f"{NEW}:2", "--label", JAVA_RUN, iterations=4, statuses=(0, 1, 1)))

    def test_a_failed_cycle_counts_as_a_run_and_spends_its_turn(self) -> None:
        self.assertEqual(
            ["host", NEW, "sleep", "host", UPDATE, "host", NEW, "review"],
            self.events("--label", NEW, "--label", UPDATE, iterations=3, statuses=(3,)))

    def test_failed_cycles_back_off_until_one_succeeds(self) -> None:
        completed, calls = self.launch("--label", NEW, iterations=5, statuses=(3, 3, 3, 0, 3))
        self.assertEqual(1, completed.returncode, completed.stderr)
        self.assertEqual(
            ["host", NEW, "sleep", "host", NEW, "sleep", "host", NEW, "review", "sleep", "host", NEW,
             "host", NEW, "sleep", "host"],
            [call["event"] for call in calls])
        self.assertEqual(["60", "120", "240", "60"],
                         [call["arguments"][0] for call in calls if call["event"] == "sleep"])
        self.assertEqual(4, completed.stderr.count("ERROR: The cycle failed with status 3; checking the host again in"))

    def test_without_a_label_every_cycle_fetches_every_queue(self) -> None:
        self.assertEqual(["host", "every queue", "review", "sleep"],
                         self.events(iterations=1, statuses=(1,)))

    def test_the_cycle_gets_its_label_and_the_other_claim_arguments(self) -> None:
        _, calls = self.launch("--label", f"{JAVA_RUN}:2", "--take-blocked-issues", "--", "--max-steps", "3",
                               iterations=1)
        self.assertEqual(["--label", JAVA_RUN, "--take-blocked-issues", "--", "--max-steps", "3"],
                         calls[1]["arguments"][4:])

    def test_a_transient_host_failure_waits_with_a_bounded_backoff_and_claims_nothing(self) -> None:
        completed, calls = self.launch("--label", NEW, iterations=9,
                                       host_statuses=(0, 75, 75, 75, 75, 75, 75, 0, 75))
        self.assertEqual(1, completed.returncode, completed.stderr)
        self.assertEqual(
            ["host", NEW, "host", "sleep", "host", "sleep", "host", "sleep", "host", "sleep", "host", "sleep",
             "host", "sleep", "host", NEW, "host", "sleep", "host"],
            [call["event"] for call in calls])
        # One minute, doubling to fifteen; a passing check starts the next wait at one minute again.
        self.assertEqual(["60", "120", "240", "480", "900", "900", "60"],
                         [call["arguments"][0] for call in calls if call["event"] == "sleep"])
        self.assertEqual(7, completed.stdout.count("The host check failed transiently; checking again in"))

    def test_a_host_fault_stops_the_loop_without_waiting(self) -> None:
        completed, calls = self.launch("--label", NEW, iterations=3, host_statuses=(0, 75, 2))
        self.assertEqual(2, completed.returncode, completed.stderr)
        self.assertEqual(["host", NEW, "host", "sleep", "host"], [call["event"] for call in calls])

    def test_a_transient_failure_before_the_gate_ever_passed_stops_the_loop(self) -> None:
        # A new host that fails like an outage is more likely misconfigured; it stops loudly.
        completed, calls = self.launch("--label", NEW, iterations=3, host_statuses=(75,))
        self.assertEqual(75, completed.returncode, completed.stderr)
        self.assertEqual(["host"], [call["event"] for call in calls])
        self.assertIn("ERROR: The host check failed before it ever passed", completed.stderr)

    def test_a_count_must_be_a_positive_integer(self) -> None:
        for value in (f"{NEW}:0", f"{NEW}:-1", f"{NEW}:two", f"{NEW}:", f"{NEW}:2:3"):
            completed, calls = self.launch("--label", value)
            self.assertEqual(2, completed.returncode, value)
            self.assertIn(f"ERROR: --label {value}: COUNT must be a positive integer.", completed.stderr)
            self.assertEqual([], calls)

    def test_a_label_given_twice_is_rejected(self) -> None:
        completed, calls = self.launch("--label", JAVA_RUN, "--label", NEW, "--label", f"{JAVA_RUN}:2")
        self.assertEqual(2, completed.returncode)
        self.assertIn(f"ERROR: --label {JAVA_RUN}: the label is given twice.", completed.stderr)
        self.assertEqual([], calls)


if __name__ == "__main__":
    unittest.main()
