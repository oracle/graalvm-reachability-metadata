# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

import json
import sys
import os

"""
Helper module for loading prompt templates.
"""


def load_predefined_strategies():
    """Load the predefined strategies JSON file."""
    repo_root = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
    strategies_path = os.path.join(repo_root, "strategies", "predefined_strategies.json")
    with open(strategies_path, "r", encoding="utf-8") as f:
        strategies = json.load(f)

    if not isinstance(strategies, list):
        print(f"ERROR: Strategies must be a list in the {strategies_path}", file=sys.stderr)
        sys.exit(1)

    return strategies


def load_prompt_template(relative_path, **kwargs):
    """Load a prompt template file and substitute placeholders."""
    repo_root = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
    template_path = os.path.join(repo_root, relative_path)
    with open(template_path, "r", encoding="utf-8") as f:
        template = f.read()
    return template.format(**kwargs)


def list_strategy_names():
    """Return all predefined strategy names."""
    return [strategy.get("name") for strategy in load_predefined_strategies() if strategy.get("name")]


def load_strategy_by_name(name):
    """Load a strategy configuration from the predefined_strategies."""
    for strategy in load_predefined_strategies():
        if strategy.get("name") == name:
            return strategy
    return None


def require_strategy_by_name(name):
    """Load a strategy configuration or exit with a list of available strategies."""
    strategies = load_predefined_strategies()
    for strategy in strategies:
        if strategy.get("name") == name:
            return strategy

    print(f"ERROR: Strategy not found: {name}", file=sys.stderr)
    print("Available strategies:", file=sys.stderr)
    for strategy in strategies:
        strategy_name = strategy.get("name")
        if strategy_name:
            print(strategy_name, file=sys.stderr)
    sys.exit(1)
