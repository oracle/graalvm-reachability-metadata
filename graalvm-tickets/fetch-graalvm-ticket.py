#!/usr/bin/env python3
# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

# The `GR` kind's fetch integration (§root/PRCPL-graalvm-workaround-tickets.3): GR citations
# resolve against what it prints (§root/FS-repository-functional-spec.5.6, §root/AR-grund-check).

"""Print the public snapshot of one GraalVM ticket for `grund fetch`.

`grund fetch graal/GR-<number>` runs this script with the ticket ID and stores
the Markdown declaration it prints in `tickets.md`. The ticket is read
from Oracle's Jira through `gdev-cli`, which only a maintainer with Jira access
has; CI never fetches, it checks the committed snapshots.

The tracker is internal and this repository is public, so the snapshot keeps
only fields that are safe to publish: the summary as the title, the status, the
resolution, the release-numbered fix versions, and the links the ticket records
to public GitHub issues and pull requests. The description, comments, people,
and every other URL never leave the tracker. A security-restricted ticket, a
summary carrying a URL, or a status or resolution that is not a plain word is
refused with exit 1 and nothing printed.
"""

from __future__ import annotations

import json
import re
import shutil
import subprocess
import sys
from typing import Any

NAME: str = "fetch-graalvm-ticket"
TICKET_ID: re.Pattern[str] = re.compile(r"GR-[1-9][0-9]*")
# Only these repositories are known to be public; a link elsewhere may name a private one.
PUBLIC_LINK: re.Pattern[str] = re.compile(
    r"https://github\.com/oracle/(?:graal|graalvm-reachability-metadata)"
    r"/(?:issues|pull)/[0-9]+(?=$|[/?#])"
)
# A Jira status or resolution name; anything else is not published.
STATE: re.Pattern[str] = re.compile(r"[A-Za-z][A-Za-z -]*")
# A GraalVM release number, as Jira names it with or without the `graalvm-` prefix; any other
# fix version, such as an internal patch name, is not published.
FIX_VERSION: re.Pattern[str] = re.compile(r"(?:graalvm-)?[0-9]+(?:\.[0-9]+)*")
# HTML and Markdown link syntax in a summary is escaped so the title renders as plain text.
SUMMARY_ESCAPES: dict[int, str] = str.maketrans(
    {"&": "&amp;", "<": "&lt;", ">": "&gt;", "[": "\\[", "]": "\\]"}
)
# A JMESPath projection, so only these fields reach this script; `--no-cache` keeps gdev-cli
# from storing the full issue (description, comments) it fetched in its local cache.
ISSUE_FIELDS: str = (
    "{summary: fields.summary, status: fields.status.name,"
    " resolution: fields.resolution.name, fixVersions: fields.fixVersions[].name,"
    " security: fields.security.name}"
)


def gdev_jira(*arguments: str) -> Any:
    """Run one `gdev-cli jira` query and return its JSON, exiting with gdev-cli's error."""
    result: subprocess.CompletedProcess[str] = subprocess.run(
        ["gdev-cli", "jira", *arguments], capture_output=True, text=True, check=False
    )
    if result.returncode != 0:
        output: str = result.stderr.strip() or result.stdout.strip()
        try:
            message: str = str(json.loads(output)["error"])
        except (ValueError, KeyError, TypeError):
            message = output or f"gdev-cli exited with {result.returncode}"
        raise SystemExit(f"{NAME}: gdev-cli jira {arguments[0]}: {message}")
    return json.loads(result.stdout)


def published(value: Any) -> str:
    """One field as published: whitespace collapsed, and a citation marker escaped."""
    return " ".join(str(value or "").split()).replace("\u00a7", "<\u00a7>")


def public_links(urls: Any) -> list[str]:
    """The public GitHub issue and pull request URLs among a ticket's remote links."""
    links: set[str] = set()
    for url in urls or []:
        match: re.Match[str] | None = PUBLIC_LINK.match(url) if isinstance(url, str) else None
        if match is not None:
            links.add(match.group(0))
    return sorted(links)


def state(ticket_id: str, field: str, value: str) -> str:
    """A status or resolution name, refused unless it is a plain word."""
    if STATE.fullmatch(value) is None:
        raise SystemExit(f"{NAME}: {ticket_id} has a {field} that is not a plain name: {value!r}")
    return value


def render(ticket_id: str, issue: dict[str, Any], links: list[str]) -> str:
    """The ticket as one H2 declaration, the shape a `file` home accepts."""
    raw_summary: str = str(issue.get("summary") or "")
    if "://" in raw_summary:
        raise SystemExit(f"{NAME}: {ticket_id} has a URL in its summary; refusing to publish it")
    summary: str = published(raw_summary.translate(SUMMARY_ESCAPES))
    if not summary:
        raise SystemExit(f"{NAME}: {ticket_id} has no summary to use as its title")
    status: str = state(ticket_id, "status", published(issue.get("status")))
    resolution: str = state(
        ticket_id, "resolution", published(issue.get("resolution") or "Unresolved")
    )
    fix_versions: list[str] = []
    for version in map(published, issue.get("fixVersions") or []):
        if FIX_VERSION.fullmatch(version) is None:
            print(f"{NAME}: {ticket_id}: not publishing fix version {version!r}", file=sys.stderr)
        else:
            fix_versions.append(version)
    return "\n".join([
        f"## {ticket_id}: {summary}",
        "",
        f"- Status: {status}",
        f"- Resolution: {resolution}",
        f"- Fix versions: {', '.join(fix_versions) or 'none'}",
        f"- Public links: {', '.join(links) or 'none'}",
    ]) + "\n"


def main(argv: list[str]) -> int:
    """Print the snapshot of `argv[0]`; exit 2 on a bad argument, 1 when it cannot be published."""
    if len(argv) != 1 or TICKET_ID.fullmatch(argv[0]) is None:
        print(f"usage: {NAME}.py GR-<number>", file=sys.stderr)
        return 2
    ticket_id: str = argv[0]
    if shutil.which("gdev-cli") is None:
        print(
            f"{NAME}: gdev-cli is not on PATH; fetching {ticket_id} needs Oracle's gdev-cli"
            " with access to the GraalVM Jira",
            file=sys.stderr,
        )
        return 1
    issue: dict[str, Any] = gdev_jira(
        "get-issue", f"-id={ticket_id}", "--refresh-cache", "--no-cache", "--json",
        f"--jp={ISSUE_FIELDS}",
    )
    if issue.get("security"):
        print(
            f"{NAME}: {ticket_id} is security-restricted ({published(issue['security'])});"
            " a restricted ticket is never published",
            file=sys.stderr,
        )
        return 1
    urls: Any = gdev_jira(
        "get", f"-path=/rest/api/2/issue/{ticket_id}/remotelink", "--json", "--jp=[].object.url"
    )
    sys.stdout.write(render(ticket_id, issue, public_links(urls)))
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
