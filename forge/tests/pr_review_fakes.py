# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

"""A fake `gh` and a fake review agent for the review tests (§FS-published-pr-review).

The fake `gh` keeps GitHub in a JSON file and reads the head of a pull request
from a real bare remote; comments carry REST ids and times, so the claims on the
`in-review` lock order as GitHub orders them. A test injects a failure as a
`faults` entry, a command fragment and the error `gh` prints, and changes a pull
request as a claim lands through `on_claim`. The fake agent runs the `gh` and
`git` commands its plan names, commits the files it names, and writes the answer
it is told to.
"""
from __future__ import annotations

FAKE_GH: str = r'''#!/usr/bin/env python3
import datetime, json, os, subprocess, sys
path = os.environ["FAKE_GH_STATE"]
state = json.load(open(path))
args = sys.argv[1:]
with open(path + ".calls", "a") as log:
    log.write(json.dumps(args) + "\n")
for fragment, error in state.get("faults", []):
    if fragment in " ".join(args):
        print(error, file=sys.stderr)
        sys.exit(1)
def value(flag, default=None):
    return args[args.index(flag) + 1] if flag in args else default
def save():
    json.dump(state, open(path, "w"))
def done(text=""):
    if text:
        print(text)
    sys.exit(0)
def pr(number):
    pull = state["prs"][str(number)]
    pull["headRefOid"] = subprocess.run(["git", "ls-remote", state["remote"], "refs/heads/" + pull["headRefName"]],
                                        stdout=subprocess.PIPE, text=True).stdout.split("\t")[0]
    return pull
if args[:2] == ["pr", "list"]:
    done(json.dumps([{"number": int(n), "createdAt": p["createdAt"]} for n, p in state["prs"].items()]))
if args[:2] == ["pr", "view"]:
    pull = pr(args[2])
    done(json.dumps({field: pull.get(field) for field in value("--json").split(",")}))
if args[:2] == ["pr", "review"] and "--approve" in args:
    pr(args[2])["reviews"].append({"author": {"login": state["login"]}, "state": "APPROVED",
                                   "commit": {"oid": pr(args[2])["headRefOid"]}})
    save(); done()
if args[:2] == ["pr", "merge"] and "--auto" in args:
    pr(args[2])["autoMergeRequest"] = {"mergeMethod": "SQUASH"}
    save(); done()
if args[0] == "api" and "/check-runs" in " ".join(args):
    sha = [a for a in args if "/commits/" in a][0].split("/commits/")[1].split("/")[0]
    done("\n".join(json.dumps(run) for run in state["checks"].get(sha, [])))
if args[0] == "api" and any(a.endswith("/status") for a in args):
    done()
if args[0] == "api" and any(a.endswith("/timeline") for a in args):
    number = [a for a in args if a.endswith("/timeline")][0].split("/")[-2]
    done("\n".join(state["timeline"].get(number, [])))
if args[:2] == ["run", "view"]:
    done(state["logs"].get(value("--job"), "no log"))
if args[:2] == ["run", "list"]:
    done(json.dumps(state["runs"].get(value("--commit"), [])))
if args[:2] == ["run", "rerun"]:
    for runs in state["runs"].values():
        for run in runs:
            if str(run["databaseId"]) == args[2]:
                run["status"], run["attempt"] = "queued", run.get("attempt", 1) + 1
    save(); done()
if args[:2] == ["issue", "list"]:
    done(json.dumps([issue for issue in state["issues"] if issue["state"] == "OPEN"]))
if args[:2] == ["issue", "create"]:
    number = 500 + len(state["issues"])
    state["issues"].append({"number": number, "state": "OPEN", "title": value("--title"), "body": value("--body")})
    save(); done(f"https://github.com/{value('--repo')}/issues/{number}")
if args[:2] == ["issue", "view"]:
    done(json.dumps({"state": next(i for i in state["issues"] if str(i["number"]) == args[2])["state"]}))
def comment(number, body):
    state["next_comment"] = state.get("next_comment", 1000) + 1
    created = datetime.datetime.now(datetime.timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")
    posted = {"id": state["next_comment"], "created_at": created, "author": {"login": state["login"]}, "body": body}
    state["prs"][number]["comments"].append(posted)
    save()
    return posted
def rest(posted):
    return json.dumps({"id": posted.get("id", 1), "created_at": posted.get("created_at", "2026-01-01T00:00:00Z"),
                       "user": posted["author"], "body": posted["body"]})
if args[:2] == ["issue", "comment"]:
    comment(args[2], value("--body")); done()
if args[0] == "api" and "POST" in args and args[args.index("POST") + 1].endswith("/comments"):
    number = args[args.index("POST") + 1].split("/")[-2]
    state["prs"][number].update(state.pop("on_claim", {}))
    done(rest(comment(number, args[-1].split("=", 1)[1])))
if args[0] == "api" and any("/comments?" in a for a in args):
    number = [a for a in args if "/comments?" in a][0].split("/")[-2]
    done("\n".join(rest(posted) for posted in state["prs"][number]["comments"]))
if args[0] == "api" and "DELETE" in args and "/issues/comments/" in args[args.index("DELETE") + 1]:
    gone = int(args[args.index("DELETE") + 1].split("/")[-1])
    for pull in state["prs"].values():
        pull["comments"] = [posted for posted in pull["comments"] if posted.get("id") != gone]
    save(); done()
if args[0] == "api" and args[-1].startswith("labels[]="):
    number = args[args.index("POST") + 1].split("/")[-2]
    state["prs"][number]["labels"].append({"name": args[-1].split("=", 1)[1]})
    save(); done()
if args[0] == "api" and "DELETE" in args:
    parts = args[args.index("DELETE") + 1].split("/")
    pull = state["prs"][parts[-3]]
    pull["labels"] = [label for label in pull["labels"] if label["name"] != parts[-1]]
    save(); done()
if args[0] == "api" and "/labels" in args[-1]:
    done()
print("unexpected gh " + " ".join(args), file=sys.stderr)
sys.exit(1)
'''

FAKE_AGENT: str = r'''#!/usr/bin/env python3
import json, os, re, subprocess, sys
plan_path = os.environ["FAKE_AGENT_PLAN"]
plan = json.load(open(plan_path))
with open(plan_path + ".calls", "a") as log:
    log.write(json.dumps({"prompt": sys.argv[1], "GH_TOKEN": os.environ.get("GH_TOKEN"),
                          "push_rewrites": subprocess.run(["git", "config", "--get-regexp", "pushinsteadof"],
                                                          stdout=subprocess.PIPE, text=True).stdout}) + "\n")
for relative, text in plan.get("files", {}).items():
    os.makedirs(os.path.dirname(relative) or ".", exist_ok=True)
    open(relative, "w").write(text)
if plan.get("files"):
    subprocess.run(["git", "add", "-A"], check=True)
    subprocess.run(["git", "-c", "user.name=Agent", "-c", "user.email=a@example.com", "commit", "-q", "-m", "Fix"],
                   check=True)
for command in plan.get("commands", []):
    subprocess.run([part.replace("{branch}", plan.get("branch", "")) for part in command], check=True)
if plan.get("answer") is not None:
    target = re.search(r"write `([^`]+)` as JSON", sys.argv[1]).group(1)
    json.dump(plan["answer"], open(target, "w"))
'''
