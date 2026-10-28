# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

"""Tests for how a model name selects its token rates."""

import unittest

from utility_scripts.token_costs import DEFAULT_INPUT_RATE_PER_1M, calc_model_session_cost, priced_model_name


class PricedModelNameTests(unittest.TestCase):

    def test_a_provider_prefix_is_dropped(self) -> None:
        self.assertEqual("gpt-5.6-sol", priced_model_name("openai-codex/gpt-5.6-sol"))

    def test_a_bare_name_is_kept(self) -> None:
        self.assertEqual("opus", priced_model_name("opus"))


class ModelSessionCostTests(unittest.TestCase):

    def test_a_prefixed_model_is_priced_as_the_bare_model(self) -> None:
        self.assertEqual(calc_model_session_cost("gpt-5.6-luna", 1_000_000, 0, 0),
                         calc_model_session_cost("openai-codex/gpt-5.6-luna", 1_000_000, 0, 0))

    def test_an_unknown_model_takes_the_default_rates(self) -> None:
        for model in ("gpt-6.1-sol", "opus"):
            self.assertEqual(DEFAULT_INPUT_RATE_PER_1M, calc_model_session_cost(model, 1_000_000, 0, 0))


if __name__ == "__main__":
    unittest.main()
