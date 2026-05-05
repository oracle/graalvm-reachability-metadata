# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

import unittest

from ai_workflows.agents.pi_rpc_client import PiRpcClient, PiRpcError


class PiRpcClientTests(unittest.TestCase):
    def test_toolcall_delta_guard_fails_runaway_streams(self) -> None:
        client = PiRpcClient(max_toolcall_delta_events=2, max_toolcall_delta_chars=1000)
        guard = {"events": 0, "chars": 0}

        client._update_toolcall_delta_guard({"type": "toolcall_start"}, guard, None, [])
        client._update_toolcall_delta_guard({"type": "toolcall_delta", "delta": "{"}, guard, None, [])
        client._update_toolcall_delta_guard({"type": "toolcall_delta", "delta": "\"cmd\""}, guard, None, [])

        with self.assertRaisesRegex(PiRpcError, "excessive tool-call delta stream"):
            client._update_toolcall_delta_guard({"type": "toolcall_delta", "delta": ":"}, guard, None, [])

    def test_toolcall_delta_guard_resets_between_tool_calls(self) -> None:
        client = PiRpcClient(max_toolcall_delta_events=2, max_toolcall_delta_chars=1000)
        guard = {"events": 0, "chars": 0}

        client._update_toolcall_delta_guard({"type": "toolcall_start"}, guard, None, [])
        client._update_toolcall_delta_guard({"type": "toolcall_delta", "delta": "{"}, guard, None, [])
        client._update_toolcall_delta_guard({"type": "toolcall_end"}, guard, None, [])
        client._update_toolcall_delta_guard({"type": "toolcall_delta", "delta": "{"}, guard, None, [])
        client._update_toolcall_delta_guard({"type": "text_delta", "delta": "done"}, guard, None, [])
        client._update_toolcall_delta_guard({"type": "toolcall_delta", "delta": "\"cmd\""}, guard, None, [])

        self.assertEqual(guard["events"], 2)

    def test_toolcall_delta_guard_fails_large_delta_payloads(self) -> None:
        client = PiRpcClient(max_toolcall_delta_events=100, max_toolcall_delta_chars=5)
        guard = {"events": 0, "chars": 0}

        with self.assertRaisesRegex(PiRpcError, "6 chars"):
            client._update_toolcall_delta_guard({"type": "toolcall_delta", "delta": "123456"}, guard, None, [])


if __name__ == "__main__":
    unittest.main()
