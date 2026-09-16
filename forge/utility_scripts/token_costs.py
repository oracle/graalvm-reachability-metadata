# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

"""Per-model token pricing and session cost/usage computation."""

# Rates are attached to the workflow model name.
DEFAULT_INPUT_RATE_PER_1M = 3.00
DEFAULT_CACHED_INPUT_RATE_PER_1M = 3.00
DEFAULT_OUTPUT_RATE_PER_1M = 12.00

INPUT_TOKEN_RATE_PER_1M_BY_MODEL = {
    "gpt-5.4": 2.50,
    "gpt-5.6-sol": 5.00,
    "gpt-5.6-terra": 2.50,
    "gpt-5.6-luna": 1.00,
}

CACHED_INPUT_TOKEN_RATE_PER_1M_BY_MODEL = {
    "gpt-5.4": 0.25,
    "gpt-5.6-sol": 0.50,
    "gpt-5.6-terra": 0.25,
    "gpt-5.6-luna": 0.10,
}

OUTPUT_TOKEN_RATE_PER_1M_BY_MODEL = {
    "gpt-5.4": 15.00,
    "gpt-5.6-sol": 30.00,
    "gpt-5.6-terra": 15.00,
    "gpt-5.6-luna": 6.00,
}


def calc_token_cost(token_count: int, rate_per_1m: float) -> float:
    return round((token_count / 1_000_000.0) * rate_per_1m, 4)


def calc_input_cost(input_tokens: int, rate_per_1m: float = DEFAULT_INPUT_RATE_PER_1M) -> float:
    return calc_token_cost(input_tokens, rate_per_1m)


def calc_output_cost(output_tokens: int, rate_per_1m: float = DEFAULT_OUTPUT_RATE_PER_1M) -> float:
    return calc_token_cost(output_tokens, rate_per_1m)


def _get_model_rate(model_name: str | None, rates_by_model: dict[str, float], default_rate: float | None) -> float | None:
    if model_name and model_name in rates_by_model:
        return rates_by_model[model_name]
    return default_rate


def calc_model_session_cost(
        model_name: str | None,
        input_tokens: int,
        cached_input_tokens: int | None,
        output_tokens: int,
) -> float:
    """Return the total USD cost for a session's token usage using per-model rates.

    `input_tokens` counts full-rate input; `cached_input_tokens` counts cache
    reads billed at the cached rate. The two counters do not overlap, so their
    costs simply add up. Unknown models fall back to the default rates. Token
    counts are normalized per million by `calc_token_cost`.
    """
    cached_input_tokens = cached_input_tokens or 0
    input_rate = _get_model_rate(model_name, INPUT_TOKEN_RATE_PER_1M_BY_MODEL, DEFAULT_INPUT_RATE_PER_1M)
    cached_input_rate = _get_model_rate(
        model_name, CACHED_INPUT_TOKEN_RATE_PER_1M_BY_MODEL, DEFAULT_CACHED_INPUT_RATE_PER_1M
    )
    output_rate = _get_model_rate(model_name, OUTPUT_TOKEN_RATE_PER_1M_BY_MODEL, DEFAULT_OUTPUT_RATE_PER_1M)
    total = (
        calc_input_cost(input_tokens, input_rate)
        + calc_input_cost(cached_input_tokens, cached_input_rate)
        + calc_output_cost(output_tokens, output_rate)
    )
    return round(total, 4)


def collect_token_usage_metrics(agent, model_name: str | None) -> dict[str, int | float | None]:
    input_tokens_used = int(getattr(agent, "total_tokens_sent", 0) or 0)
    output_tokens_used = int(getattr(agent, "total_tokens_received", 0) or 0)
    cached_input_tokens_used = getattr(agent, "cached_input_tokens_used", None)
    if cached_input_tokens_used is not None:
        cached_input_tokens_used = int(cached_input_tokens_used or 0)

    input_rate = _get_model_rate(model_name, INPUT_TOKEN_RATE_PER_1M_BY_MODEL, DEFAULT_INPUT_RATE_PER_1M)
    output_rate = _get_model_rate(model_name, OUTPUT_TOKEN_RATE_PER_1M_BY_MODEL, DEFAULT_OUTPUT_RATE_PER_1M)
    cached_input_rate = _get_model_rate(
        model_name,
        CACHED_INPUT_TOKEN_RATE_PER_1M_BY_MODEL,
        DEFAULT_CACHED_INPUT_RATE_PER_1M,
    )

    # `total_tokens_sent` counts full-rate input; cached reads are a separate
    # counter billed at the cached rate. They do not overlap, so the two costs
    # simply add up.
    billable_input_tokens = input_tokens_used
    cached_input_cost_usd = 0.0
    if cached_input_tokens_used is not None:
        cached_input_cost_usd = calc_input_cost(
            cached_input_tokens_used,
            cached_input_rate or DEFAULT_CACHED_INPUT_RATE_PER_1M,
        )

    input_cost_usd = calc_input_cost(billable_input_tokens, input_rate or DEFAULT_INPUT_RATE_PER_1M)
    output_cost_usd = calc_output_cost(output_tokens_used, output_rate or DEFAULT_OUTPUT_RATE_PER_1M)

    return {
        "input_tokens_used": input_tokens_used,
        "cached_input_tokens_used": cached_input_tokens_used,
        "output_tokens_used": output_tokens_used,
        "input_cost_usd": input_cost_usd,
        "cached_input_cost_usd": cached_input_cost_usd,
        "output_cost_usd": output_cost_usd,
        "cost_usd": round(input_cost_usd + cached_input_cost_usd + output_cost_usd, 4),
    }
