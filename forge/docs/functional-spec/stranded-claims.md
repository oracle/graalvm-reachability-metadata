# FS-forge-stranded-claims: A claim whose launcher died is resumed

A launcher can die in the middle of a run: the host runs out of memory, an
outage ends its session, its terminal is closed. Its issue stays as the claim
left it, assigned to the worker's user and `In Progress`, and the attempt's
workspace records no outcome. The fetch takes only unassigned issues
(§AR-forge-launcher.1) and the claim only `Todo` ones
(§FS-forge-run-requirements.2), so neither offers that issue again, and the
work would wait until a maintainer found it. A launcher therefore resumes the
stranded work of its host before it claims anything new.

A launcher that is alive leaves an issue the same way when GitHub refuses the
writes that claim or release it, so those writes wait out an outage, and a
release one still refuses is retried by the next cycle (see 4).

## 1. Stranded work

A claim is stranded when all of these hold:

1. the issue is open, assigned to the authenticated user alone, in project
   status `In Progress`, and carries none of the labels the fetch leaves out:
   `human-intervention`, `graalvm-bug` (§FS-forge-run-requirements.2.1) and
   `not-for-native-image`;
2. the issue's latest attempt workspace on this host records no outcome;
3. that attempt made the claim: it records when its claim was made, and GitHub
   shows no assignment of the issue to the user after that time; and
4. no live launcher works on that attempt (see 2).

Each condition is read again when the launcher acts, the GitHub ones against
live state, as a claim reads them. An attempt that records an outcome has
finished, and its close-out, not a resume, decides the issue. An issue assigned
to the user that has no workspace on this host may be the work of the same
user's launcher on another host, which this host cannot see die, so it is left
alone. The search starts from the host's own workspaces, so a host with no
unfinished attempt reads nothing from GitHub to find out.

An unfinished attempt does not mean the claim is still its own. The claim may
have been released since, by a maintainer or by a halt that keeps the attempt
for a later claim to resume, and the issue claimed again by a launcher of the
same user in another checkout or on another host. That launcher sees neither
this attempt nor the mark of 2, but the fetch takes only unassigned issues, so
it assigned the issue anew, after the attempt's claim, and its claim is left
alone however old this host's attempt is. An attempt that records no claim
time cannot show that the claim is its own, so it is not resumed, and its
issue is reported for a maintainer: a claim left for a maintainer costs one
hand release, while two launchers on one issue can publish it twice or close
it out under the live run.

## 2. A live launcher is never displaced

Launchers that share one checkout share its runs, so each marks the issue it
works on for as long as it works on it: from before the claim, so that no
moment exists in which a fresh claim looks stranded, until its close-out ends.
The process's death clears the mark however the process dies, `SIGKILL`
included, so a dead launcher's attempt is never taken for a live one's, nor a
live one's for a dead one's. A `rhei run` that outlives its launcher still
works on the attempt, so its attempt is live as well. A launcher in another
checkout or on another host never sees the mark, so its claim is told from the
attempt's own by the time it was made (see 1).

## 3. Resumed in place

A stranded attempt is resumed, not released. Its claim stays, its worktree and
the states it completed are kept, and the launcher runs it as it runs any
resumed attempt (§AR-forge-launcher.1), first moving aside the log of
the state it was killed in. Releasing it to `Todo` would let a launcher on
another host claim it and start over from a new base, discarding that work.
The resumed run ends as every run does: its outcome decides the close-out
(§FS-human-intervention-policy), and a resume Rhei cannot advance counts as any
no-advance halt does. A cycle resumes at most one stranded claim, whichever
queue it serves, and claims nothing new when it does.

## 4. A release an outage refuses is retried

The writes that claim and release an issue, its project status and its
assignee, meet GitHub when an outage is most likely, since an outage is often
why the run failed. Tried once, a short outage leaves the issue assigned and
`In Progress`, or `In Progress` with no assignee, which neither the fetch nor
the claim offers again, and at the claim it ends the cycle on a crash.

### 4.1 A write waits out a transient condition

Each claim and release write, and each read that confirms one, is retried with
a bounded backoff when it meets a transient condition of
§FS-forge-host-requirements.1: a rate limit, or an endpoint that cannot be
reached. A write GitHub refuses for any other reason, a 404 or a missing
permission among them, is not retried, since that will not change by itself.

### 4.2 A release an outage leaves unconfirmed is recorded

A release retries its writes a few times (§FS-forge-run-requirements) only
while one of them fails transiently. One an outage still leaves unconfirmed is
not left for a maintainer to release by hand: the launcher records it in the
runs directory, and reports that the next cycle retries it. A release GitHub
refused, and one GitHub answered but still shows claimed, are reported for a
maintainer as before, and drop any record of the issue.

### 4.3 The next cycle retries it first

A cycle retries the releases its host recorded before it resumes or claims
anything. While GitHub still refuses one, the cycle claims nothing and ends as
a transient failure, so the loop backs off rather than strand another issue in
the same outage. A retry never undoes a claim made since: it runs only while
no live launcher works on the issue, GitHub shows the issue assigned to the
user alone or to nobody, and the issue has not been updated since the release
failed. An issue updated since may have been released by hand and claimed
again, so its record is dropped and the issue named for a maintainer instead.

### 4.4 A claim an outage interrupts is released

A claim whose writes still fail transiently is released as in 4.2, and the
cycle ends as a transient failure instead of crashing, so the loop backs off
rather than claim the next candidate into the same outage.
