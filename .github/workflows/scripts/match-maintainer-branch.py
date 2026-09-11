#!/usr/bin/env python3
"""Decide whether a pushed branch may be published as a bot-authored pull request.

The allowlist is the `MAINTAINER_PR_BRANCHES` repository variable: a comma-separated list
of `login:pattern` entries, where `pattern` is a glob matched against the branch name. A
push is publishable when some entry names the pusher and its pattern matches the branch,
which keeps every published branch owned by exactly one maintainer.

Patterns use GitHub's branch-filter semantics rather than shell ones, so they read the same
way as the `on.push.branches` filter in the workflow: `*` matches within one path segment,
`**` crosses `/`, and `?` matches a single character. Used by
`.github/workflows/maintainer-open-pr.yml`.
"""

from __future__ import annotations

import argparse
import re


def parse_allowlist(allowlist: str) -> list[tuple[str, str]]:
    """Parse `login:pattern` entries, ignoring blanks so trailing commas are harmless."""
    entries: list[tuple[str, str]] = []
    for raw in allowlist.split(","):
        entry: str = raw.strip()
        if not entry:
            continue
        if ":" not in entry:
            raise SystemExit(f"Allowlist entry is not `login:pattern`: {entry!r}")
        login, pattern = entry.split(":", 1)
        login, pattern = login.strip(), pattern.strip()
        if not login or not pattern:
            raise SystemExit(f"Allowlist entry has an empty login or pattern: {entry!r}")
        entries.append((login, pattern))
    return entries


def compile_pattern(pattern: str) -> re.Pattern[str]:
    """Compile a GitHub-style branch filter, where `*` stops at `/` but `**` does not."""
    regex: str = ""
    index: int = 0
    while index < len(pattern):
        character: str = pattern[index]
        if character == "*":
            if pattern.startswith("**", index):
                regex += ".*"
                index += 2
                continue
            regex += "[^/]*"
        elif character == "?":
            regex += "[^/]"
        else:
            regex += re.escape(character)
        index += 1
    return re.compile(f"^{regex}$")


def matching_pattern(allowlist: str, actor: str, branch: str) -> str | None:
    """Return the pattern that lets `actor` publish `branch`, or None if none does."""
    for login, pattern in parse_allowlist(allowlist):
        # Logins are compared case-insensitively because GitHub treats them that way;
        # branch names are case-sensitive and are compared as pushed.
        if login.lower() == actor.lower() and compile_pattern(pattern).fullmatch(branch):
            return pattern
    return None


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--allowlist", required=True)
    parser.add_argument("--actor", required=True)
    parser.add_argument("--branch", required=True)
    args = parser.parse_args()

    pattern: str | None = matching_pattern(args.allowlist, args.actor, args.branch)
    if pattern is None:
        print(f"No MAINTAINER_PR_BRANCHES entry lets {args.actor} publish {args.branch}.")
        print("allowed=false")
        return
    print(f"{args.branch} matches `{pattern}` for {args.actor}.")
    print("allowed=true")


if __name__ == "__main__":
    main()
