# Repository status

This directory contains the issue progress reporter specified by
§FS-repository-status-report. It uses the authenticated GitHub CLI and has no
third-party Python dependencies.

## Run it

Create the self-contained human dashboard:

```bash
./repository_status/repository_status.py --human
```

The default destination is `build/reports/repository-status.html`. Choose a
different file or write HTML to stdout:

```bash
./repository_status/repository_status.py --human --output /tmp/repository-status.html
./repository_status/repository_status.py --human --output -
```

Write the agent contract as JSON to stdout:

```bash
./repository_status/repository_status.py --agent
```

The command requires `gh` to be authenticated for GitHub. Authentication and
API failures are written to stderr and return a non-zero status. Agent mode
never mixes diagnostics into its JSON stdout stream.

Use another recent-flow window when needed:

```bash
./repository_status/repository_status.py --human --window-days 90
```

## Measurements

Priority is label-derived and independent from age:

| Tier | Label rule | Weight |
| --- | --- | ---: |
| High | `high-priority` | 10 |
| Priority | `priority`, without `high-priority` | 5 |
| Normal | neither priority label | 1 |

The current-state measurements are:

```text
weighted backlog = sum(priority weight)
weighted age debt = sum(priority weight × complete days since creation)
```

The attention queue always orders priority tier first, then oldest first. A very
old normal issue therefore contributes visible age debt but never moves above a
high-priority issue.

The default age bands are fresh (0–7 days), aging (8–30), old (31–90), and stale
(91+). The versioned values, labels, project title, and flow window live in
[`policy.json`](policy.json).

## Test it

Tests use fixed GitHub-shaped fixtures and do not access the network:

```bash
python3 -m unittest repository_status.test_repository_status
```
