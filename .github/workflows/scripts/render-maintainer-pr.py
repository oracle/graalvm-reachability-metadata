#!/usr/bin/env python3
"""Render the PR title and body for a maintainer branch from a Markdown template.

The template's first line that is neither blank nor part of a leading HTML comment is
the title; everything after it is the body. Used by `.github/workflows/maintainer-open-pr.yml`.
"""

from __future__ import annotations

import argparse
from pathlib import Path

PLACEHOLDER_SUBJECT = "{{SUBJECT}}"
PLACEHOLDER_BRANCH = "{{BRANCH}}"
PLACEHOLDER_AUTHOR = "{{AUTHOR}}"
PLACEHOLDER_COMMITS = "{{COMMITS}}"


def split_title_body(text: str) -> tuple[str, str]:
    """Split rendered template text into its title line and the body that follows.

    Leading blank lines and HTML comments are skipped so the template can carry authoring
    instructions above the title. Comments after the title are kept: they are invisible on
    GitHub and are how a template prompts the author to fill a section in.
    """
    lines: list[str] = text.splitlines()
    in_comment: bool = False

    for index, line in enumerate(lines):
        stripped: str = line.strip()
        if in_comment:
            in_comment = "-->" not in stripped
            continue
        if not stripped:
            continue
        if stripped.startswith("<!--"):
            in_comment = "-->" not in stripped
            continue
        return stripped, "\n".join(lines[index + 1:]).strip("\n")

    raise SystemExit("Template contains no title line outside comments")


def render(template: str, subjects: list[str], branch: str, author: str) -> tuple[str, str]:
    commits: str = "\n".join(f"- {subject}" for subject in subjects)
    rendered: str = (
        template
        .replace(PLACEHOLDER_SUBJECT, subjects[0])
        .replace(PLACEHOLDER_BRANCH, branch)
        .replace(PLACEHOLDER_AUTHOR, author)
        .replace(PLACEHOLDER_COMMITS, commits)
    )
    return split_title_body(rendered)


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--template", required=True, type=Path)
    parser.add_argument("--subjects", required=True, type=Path)
    parser.add_argument("--branch", required=True)
    parser.add_argument("--author", required=True)
    parser.add_argument("--title-out", required=True, type=Path)
    parser.add_argument("--body-out", required=True, type=Path)
    args = parser.parse_args()

    subjects: list[str] = [
        line.strip() for line in args.subjects.read_text(encoding="utf-8").splitlines() if line.strip()
    ]
    if not subjects:
        raise SystemExit("No commit subjects to render")

    title, body = render(
        template=args.template.read_text(encoding="utf-8"),
        subjects=subjects,
        branch=args.branch,
        author=args.author,
    )
    if not title:
        raise SystemExit("Rendered title is empty")

    args.title_out.write_text(title, encoding="utf-8")
    args.body_out.write_text(body + "\n", encoding="utf-8")


if __name__ == "__main__":
    main()
