# FS-forge-stranded-claims: A claim whose launcher died is resumed

A launcher can die in the middle of a run: the host runs out of memory, an
outage ends its session, its terminal is closed. Its issue stays as the claim
left it, assigned to the worker's user and `In Progress`, and the attempt's
workspace records no outcome. The fetch takes only unassigned issues
(§AR-forge-launcher.1) and the claim only `Todo` ones
(§FS-forge-run-requirements.2), so neither offers that issue again, and the
work would wait until a maintainer found it. A launcher therefore resumes the
stranded work of its host before it claims anything new.

## 1. Stranded work

A claim is stranded when all of these hold:

1. the issue is open, assigned to the authenticated user alone, in project
   status `In Progress`, and carries none of the labels the fetch leaves out:
   `human-intervention`, `graalvm-bug` (§FS-forge-run-requirements.2.1) and
   `not-for-native-image`;
2. the issue's latest attempt workspace on this host records no outcome; and
3. no live launcher works on that attempt (see 2).

Each condition is read again when the launcher acts, the GitHub ones against
live state, as a claim reads them. An attempt that records an outcome has
finished, and its close-out, not a resume, decides the issue. An issue assigned
to the user that has no workspace on this host may be the work of the same
user's launcher on another host, which this host cannot see die, so it is left
alone. The search starts from the host's own workspaces, so a host with no
unfinished attempt reads nothing from GitHub to find out.

## 2. A live launcher is never displaced

Several launchers share one checkout and its runs, so each marks the issue it
works on for as long as it works on it: from before the claim, so that no
moment exists in which a fresh claim looks stranded, until its close-out ends.
The process's death clears the mark however the process dies, `SIGKILL`
included, so a dead launcher's attempt is never taken for a live one's, nor a
live one's for a dead one's. A `rhei run` that outlives its launcher still
works on the attempt, so its attempt is live as well.

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
