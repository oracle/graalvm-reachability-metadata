# Maintainer agent queues

This folder holds what the repository ships for the agent queues of
§FS-maintainer-agent-queues: an implement queue for issues assigned to you, and
a drain queue for generated pull requests stopped on `human-intervention`. They
run on [ephor](https://github.com/agent-grounds/ephor) and its runtime
[rhei](https://github.com/vjovanov/rhei).

| File | What it is |
| --- | --- |
| `states.yaml` | The state machine both queues run under |
| `implement.md` | The implement queue's instructions, read as a recipe `brief_file` |
| `hi-collect.sh` | The drain queue's first state: reads the gate before any model is spent |
| `hi-checkouts.sh` | Makes the branch workspaces the drain queue needs; run before each sweep |
| `ci-failures.sh`, `ci-restart.sh` | The `failures` and `restart` gate verbs, bound in `../ephor.json` |

The repository decides how the work is done. Whether agents spend your budget
on it is yours to decide, so nothing here runs until you adopt the queues in
your own ephor configuration (§FS-maintainer-agent-queues.1).

## Before you start

- `ephor`, a `rhei` whose `rhei run --help` lists `--headless`, the `claude`
  CLI, `gh` logged in with `repo` scope, `jq`, and `grund`.
- An entry `<your-login>:<your-prefix>/auto-*` in the repository variable
  `MAINTAINER_PR_BRANCHES`, so a push to your queue's branches makes the bot
  open the pull request and you stay eligible to review it.
- The project registered in your ephor registry with branch workspaces, for
  example:

  ```json
  { "id": "reachability-metadata", "organization": "<you>", "type": "monorepo",
    "root": "$HOME/git/graalvm-reachability-metadata", "main_branch": "master",
    "branch_root_template": "{project_root}/.agent-worktrees/{branch}",
    "clone_mode": "worktree", "tags": [], "branches": [] }
  ```

## Adopting the queues

Add this to `~/.config/ephor/status.json`, replacing `<your-login>`,
`<your-prefix>` and the clone path. The `autorun` and `dispatch` keys are the
opt-in: drop them and the recipes stay available by hand through
`ephor work dispatch --recipe <id>`, but nothing starts on its own.

```jsonc
{
  "projects": {
    "reachability-metadata": {
      "providers": [
        { "provider": "github-prs", "repos": ["oracle/graalvm-reachability-metadata"], "reviews": true },
        { "provider": "github-ci", "repos": ["oracle/graalvm-reachability-metadata"] },
        { "provider": "github-issues", "repos": ["oracle/graalvm-reachability-metadata"],
          "participating": true, "updated_within_days": 7, "limit": 100 }
      ],
      "work": {
        "states": "~/git/graalvm-reachability-metadata/.ephor/states.yaml",
        "max_spend": { "amount": 20, "currency": "USD", "per": "24h" },
        "recipes": [
          {
            "id": "implement", "icon": "🔧", "description": "implement the issue",
            "state": "fix", "needs_checkout": true,
            "branch": "<your-prefix>/auto-issue-{number}",
            "brief_file": "{root}/.ephor/implement.md",
            "brief": "The issue: {title} ({url}), number {number}, on branch {branch}.",
            "when": {
              "kinds": ["issue"], "assignees": ["<your-login>"],
              "labels": ["!library-new-request", "!library-update-request",
                         "!library-unsupported-version", "!fails-javac-compile",
                         "!fails-java-run", "!fails-native-image-build",
                         "!fails-native-image-run", "!chunked-dynamic-access",
                         "!human-intervention", "!resumable"]
            },
            "autorun": true, "dispatch": "2h"
          },
          {
            "id": "drain-human-intervention", "icon": "🧹",
            "description": "drain the human-intervention label",
            "state": "hi-collect", "needs_checkout": true,
            "brief": "Drain the human-intervention label from {title} ({url}).",
            "when": { "kinds": ["pr"], "labels": ["human-intervention", "!human-intervention-fixed"] },
            "autorun": true, "dispatch": "2h"
          },
          {
            "id": "review", "icon": "👓", "description": "review this change",
            "state": "fix", "needs_checkout": false,
            "when": { "kinds": ["pr"], "roles": ["reviewer"], "labels": ["!human-intervention"] },
            "brief": "{title} is a change I am reviewing. Review it: correctness first, each point with its file and line, saying which would block a merge. Do not post anything."
          },
          {
            "id": "rebase", "icon": "⤴", "description": "rebase onto the main branch, handing over what conflicts",
            "state": "fix", "needs_checkout": true, "opens_with": "rebase",
            "when": { "behind": true, "labels": ["!human-intervention"] },
            "brief": "{title} is on {branch}, behind its main branch; the replay stopped at the conflict above. Resolve it as the change would have been written against the new base, check the result builds, and do not push."
          }
        ]
      }
    }
  }
}
```

The last two entries replace ephor's shipped `review` and `rebase` recipes for
this project only. Without them, a sweep offers a labelled pull request to one
of those first and never reaches the drain queue.

## Running it unattended

Install ephor's `systemd/` units as its manual describes, then add a drop-in
to the sync unit so the agents are on its `PATH` and the drain queue's
workspaces exist before each sweep:

```ini
# ~/.config/systemd/user/ephor-work-sync.service.d/queues.conf
[Service]
Environment=PATH=%h/.cargo/bin:<directory holding claude>:/usr/local/bin:/usr/bin:/bin
ExecStartPre=%h/git/graalvm-reachability-metadata/.ephor/hi-checkouts.sh reachability-metadata
```

Run `ephor work sync --dry-run` after any change to see what the next
unattended sweep would open or reopen; without `--dry-run` it acts. Stop
everything with `systemctl --user stop ephor-work-sync.timer`.
