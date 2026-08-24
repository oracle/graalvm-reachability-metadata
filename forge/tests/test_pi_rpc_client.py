# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

import unittest

from ai_workflows.agents.pi_rpc_client import PiRpcClient


class PiRpcClientTests(unittest.TestCase):
    def test_build_command_includes_explicit_thinking_level(self) -> None:
        client = PiRpcClient(
            provider="openai-codex",
            model="gpt-5.6-sol",
            thinking_level="medium",
        )

        self.assertEqual(
            client._build_command([]),
            [
                "pi",
                "--mode",
                "rpc",
                "--no-extensions",
                "--tools",
                "read,edit,write,grep,find,ls",
                "--provider",
                "openai-codex",
                "--model",
                "gpt-5.6-sol",
                "--thinking",
                "medium",
            ],
        )


if __name__ == "__main__":
    unittest.main()
