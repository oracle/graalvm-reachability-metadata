# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

"""Tests for the human-intervention scanner's queue.

An item labeled `graalvm-bug` is a declared GraalVM blocker, so neither
`queue-check` nor `scan` takes it (§root/FS-graalvm-blocked-work.2).
"""
from __future__ import annotations

import json
import os
import re
import stat
import subprocess
import tempfile
import textwrap
import unittest
from typing import Any

import yaml

TEMPLATE_DIR: str = os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))),
                                 ".agent-grounds", "rhei", "templates", "human-intervention-scanner")
PLACEHOLDER: re.Pattern[str] = re.compile(r"\{\{(\w+)\}\}")
HELD: str = "-label:graalvm-bug"
# A `gh pr list` or `gh issue list` that keeps the items carrying every `--label`
# and none that a `-label:` term of `--search` excludes, as GitHub does.
FAKE_GH: str = textwrap.dedent("""\
    #!/usr/bin/env python3
    import json, os, sys
    args = sys.argv[1:]
    with open(os.environ["FAKE_GH_ITEMS"], encoding="utf-8") as handle:
        items = json.load(handle)
    required = [args[i + 1] for i, arg in enumerate(args) if arg == "--label"]
    search = args[args.index("--search") + 1].split() if "--search" in args else []
    excluded = {term[len("-label:"):] for term in search if term.startswith("-label:")}
    kept = [item for item in items
            if all(name in item["labels"] for name in required) and not excluded & set(item["labels"])]
    print("\\n".join(str(item["number"]) for item in kept[:int(args[args.index("--limit") + 1])]))
    """)


def rendered_states(source: str) -> dict[str, Any]:
    """`states.yaml` with every template input at its default, `source` aside."""
    with open(os.path.join(TEMPLATE_DIR, "template.yaml"), encoding="utf-8") as handle:
        values: dict[str, str] = {entry["name"]: str(entry["default"]) for entry in yaml.safe_load(handle)["inputs"]}
    values["source"] = source
    with open(os.path.join(TEMPLATE_DIR, "states.yaml"), encoding="utf-8") as handle:
        return yaml.safe_load(PLACEHOLDER.sub(lambda match: values[match.group(1)], handle.read()))["states"]


class QueueCheckTest(unittest.TestCase):

    def queue_check(self, source: str, items: list[tuple[int, tuple[str, ...]]], seen: tuple[int, ...] = ()) -> int:
        """The exit of `queue-check` over a queue of `(number, labels)` items."""
        with tempfile.TemporaryDirectory() as root:
            bin_dir: str = os.path.join(root, "bin")
            os.makedirs(bin_dir)
            gh: str = os.path.join(bin_dir, "gh")
            with open(gh, "w", encoding="utf-8") as handle:
                handle.write(FAKE_GH)
            os.chmod(gh, os.stat(gh).st_mode | stat.S_IXUSR)
            listed: str = os.path.join(root, "items.json")
            with open(listed, "w", encoding="utf-8") as handle:
                json.dump([{"number": number, "labels": list(labels)} for number, labels in items], handle)
            os.makedirs(os.path.join(root, "runtime", "scan"))
            with open(os.path.join(root, "runtime", "scan", "seen.txt"), "w", encoding="utf-8") as handle:
                handle.writelines(f"{number}\n" for number in seen)
            environment: dict[str, str] = {**os.environ, "PATH": f"{bin_dir}{os.pathsep}{os.environ['PATH']}",
                                           "FAKE_GH_ITEMS": listed}
            return subprocess.run(["sh", "-c", rendered_states(source)["queue-check"]["program"]],
                                  cwd=root, env=environment, check=False).returncode

    def test_a_queue_of_held_items_starts_no_scan(self) -> None:
        for source in ("prs", "issues"):
            with self.subTest(source=source):
                self.assertEqual(0, self.queue_check(source, [(1, ("human-intervention", "graalvm-bug")),
                                                              (2, ("graalvm-bug", "human-intervention"))]))

    def test_an_unseen_item_that_is_not_held_starts_a_scan(self) -> None:
        for source in ("prs", "issues"):
            with self.subTest(source=source):
                self.assertEqual(75, self.queue_check(source, [(1, ("human-intervention", "graalvm-bug")),
                                                               (2, ("human-intervention",))]))

    def test_seen_items_and_held_items_leave_the_queue_drained(self) -> None:
        self.assertEqual(0, self.queue_check("issues", [(1, ("human-intervention", "graalvm-bug")),
                                                        (2, ("human-intervention",))], seen=(2,)))


class ScanTest(unittest.TestCase):

    def test_scan_fetches_the_set_queue_check_counts(self) -> None:
        for source in ("prs", "issues"):
            with self.subTest(source=source):
                states: dict[str, Any] = rendered_states(source)
                self.assertTrue(HELD in states["queue-check"]["program"], "queue-check takes held items")
                self.assertTrue(HELD in states["scan"]["instructions"], "scan takes held items")


if __name__ == "__main__":
    unittest.main()
