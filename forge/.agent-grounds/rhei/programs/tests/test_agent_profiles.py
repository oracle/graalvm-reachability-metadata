# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

"""Every template's `pi` profile delivers the prompt on stdin.

A bulk cover prompt lists every class with uncovered calls
(§AR-forge-workflow-prompts.4), so it can outgrow the 131072 bytes Linux allows
one argument, and a prompt passed as the value of `-p` then fails to spawn.
With `stdin_prompt` Rhei pipes the prompt, keeps `-p` bare, and ends the
arguments with a `--` that `pi` rejects, so the command drops that separator.
"""
from __future__ import annotations

import json
import os
import subprocess
import tempfile
import unittest
from typing import Any

from support import FORGE

TEMPLATES: str = os.path.join(FORGE, ".agent-grounds", "rhei", "templates")
# Past the per-argument limit, as the testcontainers bulk prompt was.
PROMPT: str = "Filler line.\n" * 12_000 + "Reply with exactly the word PONG and nothing else.\n"
# Records what it was handed, one NUL-terminated argument per entry.
FAKE_PI: str = "#!/bin/sh\nprintf '%s\\0' \"$@\" > argv\ncat > stdin\n"


def pi_profiles() -> dict[str, dict[str, Any]]:
    """The `pi` profile of every template that declares one, by template name."""
    profiles: dict[str, dict[str, Any]] = {}
    for name in sorted(os.listdir(TEMPLATES)):
        path: str = os.path.join(TEMPLATES, name, "settings.json")
        if os.path.isfile(path):
            with open(path, encoding="utf-8") as handle:
                agents: dict[str, Any] = json.load(handle).get("agents", {})
            if "pi" in agents:
                profiles[name] = agents["pi"]
    return profiles


class PiProfileTest(unittest.TestCase):

    def test_every_pi_profile_pipes_the_prompt(self) -> None:
        profiles: dict[str, dict[str, Any]] = pi_profiles()
        self.assertIn("forge-metadata", profiles)
        for name, profile in profiles.items():
            with self.subTest(template=name):
                self.assertIs(True, profile.get("stdin_prompt"))
                self.assertEqual("-p", profile["prompt_flag"])

    def test_every_pi_command_hands_pi_the_flags_without_the_separator(self) -> None:
        """Spawn the command with the arguments Rhei appends, in its order, against a fake `pi`."""
        for name, profile in pi_profiles().items():
            with self.subTest(template=name), tempfile.TemporaryDirectory() as directory:
                fake: str = os.path.join(directory, "pi")
                with open(fake, "w", encoding="utf-8") as handle:
                    handle.write(FAKE_PI)
                os.chmod(fake, 0o755)
                flags: list[str] = [
                    *profile["modes"]["high"], "--mode", "json", profile["prompt_flag"],
                    profile["model_flag"], "openai-codex/gpt-5.6-luna",
                    profile["session"]["session_dir_flag"], directory,
                ]
                environment: dict[str, str] = {**os.environ, "PATH": directory + os.pathsep + os.environ["PATH"]}
                subprocess.run([*profile["command"], *flags, "--"], input=PROMPT, text=True, cwd=directory,
                               env=environment, check=True, timeout=60)
                with open(os.path.join(directory, "argv"), encoding="utf-8") as handle:
                    self.assertEqual(flags, handle.read().split("\0")[:-1])
                with open(os.path.join(directory, "stdin"), encoding="utf-8") as handle:
                    self.assertEqual(PROMPT, handle.read())


if __name__ == "__main__":
    unittest.main()
