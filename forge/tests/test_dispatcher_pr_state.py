# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

from dispatcher_test_support import *  # noqa: F401,F403 - shared dispatcher test fixtures


class PullRequestStateTests(unittest.TestCase):
    def test_unknown_mergeability_is_repolled_until_settled(self) -> None:
        payloads = [
            {
                "data": {
                    "repository": {
                        "pullRequest": _pull_request_state(
                            9656, "SUCCESS", mergeable=mergeable,
                        ),
                    },
                },
            }
            for mergeable in ("UNKNOWN", "UNKNOWN", "CONFLICTING")
        ]
        with (
                patch.object(pr_state, "gh_json", side_effect=payloads) as gh_json,
                patch.object(pr_state.time, "sleep") as sleep,
        ):
            state = pr_state.get_pull_request_state(9656)

        self.assertEqual("CONFLICTING", state["mergeable"])
        self.assertEqual(3, gh_json.call_count)
        self.assertEqual([2, 4], [call.args[0] for call in sleep.call_args_list])

    def test_settled_mergeability_is_fetched_once(self) -> None:
        payload = {
            "data": {
                "repository": {
                    "pullRequest": _pull_request_state(9656, "SUCCESS"),
                },
            },
        }
        with (
                patch.object(pr_state, "gh_json", return_value=payload) as gh_json,
                patch.object(pr_state.time, "sleep") as sleep,
        ):
            state = pr_state.get_pull_request_state(9656)

        self.assertEqual("MERGEABLE", state["mergeable"])
        self.assertEqual(1, gh_json.call_count)
        sleep.assert_not_called()


if __name__ == "__main__":
    unittest.main()
