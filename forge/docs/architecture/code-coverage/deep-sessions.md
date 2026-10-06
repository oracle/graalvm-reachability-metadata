# AR-code-coverage-deep-sessions: Deep-pass group sessions

A deep pass prompts up to 200 targets (§AR-code-coverage-improvement.4.2).
Handing them all to one agent session wastes most of the prompt. On the h2
2.1.210 benchmark, one session per pass touched 1 to 29 of about 100 owner
classes. Inside a touched group it covered 40 to 80% of the targets, but over
the whole prompt only 14%. The agent does good work on a group it picks up and
simply never reaches the rest. A pass therefore splits its prompt into small
sessions, one per group of related routes, and runs them one after another
before it measures again. Every prompted target still gets its attempt in the
pass (§GOAL-maximize-library-coverage).

## 1. Grouping

### 1.1 Group key

A target's group is the owner class of the first method on its prompted route.
That method is where a test enters the library, so targets that share it share
a test setup, and one test class can reach many of them. The key comes from the
route the prompt already shows, so grouping needs no new evidence.

### 1.2 Session rules

The rules keep every session big enough to be worth a session and small enough
for one agent to finish:

- An owner group with at least 10 targets is a **monolith** session. A group
  above 25 targets is cut into sessions of at most 25, and a remainder below 10
  joins the pool.
- Every smaller group joins the **pool**. The pool is ordered by package and
  then by prompt order, so neighbouring classes share a session, and is packed
  into **mixed** sessions of at most 25 targets.
- Sessions run in the order of their best-ranked target.

No target is dropped. Every prompted target lands in exactly one session. On
the h2 benchmark the rules produced 9 to 10 sessions per pass.

### 1.3 Session kind

The kind follows the routes a session actually holds. A session whose routes
all enter through one owner class is a monolith, even when the pool produced
it. A monolith session drives one subsystem, so its prompt asks for one test
class for that entry. A mixed session collects routes from several unrelated entries,
so its prompt tells the agent to expect more than one test class and to put
each route in the class for its own subsystem. The kind is recorded beside the
session in the discovery report, so a run can be audited by kind.

## 2. Dispatch

Measurement writes the pass's sessions as an ordered queue and hands control to
a dispatch program state. On each visit the dispatcher removes the first
session, writes its prompt as the handoff the cover state inherits
(§AR-code-coverage-improvement.5.2), and exits 10 to run that session. When the
queue is empty it exits 0 and measurement runs again. The cover state returns
to the dispatcher, not to measurement.

The queue file is the whole loop state. The dispatcher keeps no counter, so the
number of sessions is the length of the queue and nothing else. A session is
removed before its agent runs, so a session that fails is not repeated in the
same pass. Its targets stay uncovered and come back through the next
measurement and the attempt-count rotation.

## 3. Visit budgets

Rhei visit caps are hard limits and do not steer the loop. They are set from a
ceiling the session rules guarantee. Every session except the last mixed one
holds at least 10 targets, so a pass of 200 targets has at most 200 / 10 + 1 =
21 sessions. The cover state's cap is the coverage iteration budget times 21,
and the dispatcher's cap adds one empty-queue visit per pass. A run therefore
never exhausts either cap before measurement stops the phase.

Rhei also bounds the transitions one task applies and the agent sessions one
project starts per day, and it takes the smaller of what the workspace asks for
and what the machine allows. A deep pass applies two transitions and starts one
agent per session, far more than a single-session pass did. The template's
settings therefore ask for bounds that cover the session ceiling at the default
budget, and a machine that runs the workflow must allow at least as much.

## 4. Boundaries

- A pass is still one JaCoCo measurement and one entry in the yield series, so
  the attempt counts (§AR-code-coverage-improvement.4.2) and the marginal-yield
  stop (§AR-code-coverage-improvement.4.3) see exactly what they saw before.
- The API phase is unchanged and keeps one cover session per pass.
- Grouping orders and splits the prompt. It never changes which targets are
  prompted or their coverage status.
