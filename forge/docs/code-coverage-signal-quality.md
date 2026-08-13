# Code coverage: what left the prompt, and why

The code-coverage workflow (§WF-code-coverage-improvement) asks an agent to
write tests for a list of targets, measures the result with JaCoCo, and repeats.
Its throughput is bounded by one quantity: **how many of the offered targets the
agent actually covers**. Every row the agent cannot act on, and every path that
claims a reachability that does not exist, spends part of a fixed budget on
nothing.

This document records the changes made to that signal between 2026-07-30 and
2026-08-13, in the order they attack the problem. Each section states the
defect, the rule that replaced it, and what was measured. All measurements come
from `org.apache.kafka:kafka-streams:3.6.2`: §1–§6 replay the recorded runs of
issues 9050 and 9055 from stored artifacts, §7 reads the completed 2026-08-12
run of 9055 pass by pass.

| # | Change | What stopped reaching the agent | Measured |
| --- | --- | --- | --- |
| 1 | Rank by unlocked code | Identifier order | Prompt reordered, not resized |
| 2 | Rank orders, never shrinks | Silent truncation at first zero score | 95 → 200 targets |
| 3 | Receiver obtainability | Methods no test can invoke | 731 of 4043 entries dropped |
| 4 | Library-owned deep universe | The library's own unit tests | 15413 → 3142 denominator |
| 5 | Synthetic targets and honest routes | Compiler-owned names, fabricated paths | 97 → 0 rows, 28 → 0 hub paths |
| 6 | Fixed budget, fixed worktree base | Passes lost to drift and repair | 4 → 6 passes, 2 passes recovered |
| 7 | Foreign declared targets | Routes through `equals`, `hasNext`, `close` | 130 → 200 library route heads |

## 0. Notation

Fixed for the whole document:

- `M` — the methods the resolved library jars declare, read from bytecode.
- `J` — the methods a JaCoCo report mentions.
- `I` — the public API inventory: entries a user can call.
- `C ⊆ J` — the methods JaCoCo marks covered.
- `G = (V, E)` — the static call graph; `E` over-approximates virtual dispatch.
- `body(m)` — `m` has bytecode; abstract and interface declarations do not.

`M` and `J` are different sets on purpose. A JaCoCo report contains only classes
some test loaded, so it omits exactly the untouched code the workflow exists to
open up; the bytecode contains everything but knows nothing about execution.
Throughout, **JaCoCo is the sole authority on coverage** and the bytecode is the
sole authority on existence.

---

## 1. Ranking API targets by unlocked internal code

**Before.** The API phase offered JaCoCo-uncovered public entries in identifier
order. A pass could be spent on a family of delegating overloads that open no
implementation code, while the entry that unlocks a subsystem waited for the
alphabet.

**Now.** Selection is a budgeted greedy maximum-coverage pass over a call graph
extracted from library bytecode by `java/CallGraphExtractor.java`
(§WF-code-coverage-improvement.3.1.1).

> **Definition 1 (unlock universe).**
> `U = { m ∈ M : body(m) ∧ m ∉ C }`.
>
> **Definition 2 (reach).** `reach(e) = { m ∈ V : e →* m in G }`.
>
> **Definition 3 (marginal unlock).** With `e₁ … e_{k−1}` already selected,
> `unlockₖ(e) = |reach(e) ∩ U \ ⋃_{i<k} reach(eᵢ)|`, and
> `eₖ = argmax unlockₖ`, ties broken on canonical id.

Subtracting each winner's reachable set is what makes delegating families
collapse to zero marginal value without a special case: once the caller is
selected, the callee adds nothing, because testing the caller covers it.

**Note.** Ranking is navigation. It never changes a method's JaCoCo status, and
§7 shows how little of the outcome it explains.

---

## 2. Ranking orders the prompt; it never shrinks it

**Before.** `U` excluded public inventory entries, so an entry reaching no
internal code scored zero. A greedy pass stops at the first zero, which
truncated the prompt to whatever still opened internal code.

**Now.** Public entries belong to `U` alongside internal methods, so every
candidate holds its own bit and scores at least 1. A candidate reaches zero only
when an already-selected entry calls it. Bodiless methods stay out: JaCoCo can
never mark an abstract or interface declaration covered.

> **Rule.** Ranking decides *order*, membership is decided by eligibility (§3)
> alone.

**Measured.** The same recorded graph and JaCoCo report yield **200 targets
where the truncating version yielded 95** — roughly 3000 uncovered public
methods had never been shown to the agent.

---

## 3. Eligibility: a target a test can actually invoke

**Before.** `public` was treated as "callable". It is not: a test that cannot
obtain an instance of the declaring class can never invoke its instance methods.
On kafka-streams, `TaskManager#checkStateUpdater` held rank 1 for six
consecutive passes and was never covered — its constructor is non-public and
takes eleven internal collaborators.

**Now.** Eligibility is a membership filter applied *before* ranking
(§WF-code-coverage-improvement.3.1.2), computed as a least fixed point over the
library bytecode.

> **Definition 4 (obtainable types).** `Obt` is the least set such that a type
> `T ∈ Obt` if any holds:
> 1. `T` declares a public constructor;
> 2. `T` is the return type of an eligible method on a type in `Obt`;
> 3. `T` is the element type of an array type in `Obt`;
> 4. `T` is a supertype of a type in `Obt`.
>
> **Definition 5 (eligible entry).** An entry `O#m(...)` is eligible if
> `m` is a constructor, or `m` is static, or `O ∈ Obt`, or **there exists a
> supertype `S` of `O` such that `S ∈ Obt` and `S` declares a method with the
> same signature `m(...)`**.

The supertype rule carries the DSL implementation classes, whose interface
declarations are abstract and therefore outside `U`: without it, the only
coverable member of such a pair is ineligible while the only eligible one has no
body.

A type reachable *only* through an explicit downcast stays out of `Obt`. Casting
to an internal implementation is not the realistic public API usage the prompt
demands — and of the 150 cast-only entries in that run, not one was ever covered
after being offered.

**Measured.** 731 of 4043 uncovered entries leave the candidate set; 60 of the
200 selected enter through the supertype rule; the coverage denominator is
unchanged at 6327. Ineligible entries remain in the denominator, because they
still execute collaterally — they are simply not something an agent can target.

---

## 4. The deep universe is what the library declares

**Before.** The deep universe was `J \ I`, taking the JaCoCo report at face
value. That report covers every instrumented class on the test runtime
classpath, and Kafka publishes a `test`-classifier artifact of ~1000 classes
that the test project needs for a single helper. Gradle's
`resolveTestedLibraryJars` matches on group and name without a classifier, so
both jars reach the report — and the library's own unit tests became coverage
targets.

**Now.** The universe is intersected with the method list the bytecode extractor
already writes for the API phase, so both phases anchor to the same jars
(§WF-code-coverage-improvement.3.2).

> **Definition 6 (deep universe).** `D = (J \ I) ∩ M`.

Package prefixes cannot separate the two sets — Kafka's tests live in Kafka's
packages. Jar membership separates them exactly. Excluded methods are counted as
`nonLibraryMethodsExcluded`; a run without the method list carries an explicit
caveat rather than filtering silently.

**Measured.** 12271 of 15413 "deep targets" were Apache Kafka's own unit tests.
Reported coverage was 1525/15413 (9.89%) where the library figure was 1441/3142
(**45.9%**) — a denominator inflated fivefold, feeding both the PR body and the
phase's own budget.

**Deliberately not changed.** The Gradle matcher stays as it is: narrowing it
would also narrow the repository's dynamic-access measurement, which may
legitimately need metadata for classes in the test artifact. The workflow
defends itself instead.

---

## 5. Synthetic methods leave the prompt; routes stop lying

One lambda in source leaves three artifacts in bytecode: the enclosing method a
person wrote, the body the compiler extracts (`lambda$enclosing$0`), and the
class the image generator emits to implement the functional interface
(`Owner$$Lambda/0x…`). Only the first carries a name a test can use
(§WF-code-coverage-improvement.3.2.1).

### 5.1 Membership

**Before.** Extracted bodies were offered as targets in their own right. In one
deep pass, 97 of 200 rows were names the agent cannot write. For nearly all of
them the enclosing method was an offered target already, so the row was also a
duplicate of work the prompt was asking for anyway.

**Now.**

> **Definition 7 (synthetic).** `S = { m : simpleName(m) begins with `lambda$`
> or `access$` }`.
>
> **Definition 8 (deep prompt).** `P ⊆ (D \ C) \ S`, `|P| ≤ 200`, ordered by
> route distance, then join kind, then attempt count.

`S` stays inside `D`, and therefore inside the denominator: JaCoCo reports those
methods, and dropping them would have moved the reported figure 45.9% → 47.8%
without a single new test. The counts are published as `deepSyntheticMethods`
and `deepSyntheticUncovered` so the exclusion is visible.

What the dropped rows carried moves onto the one name the agent can act on:

> **Definition 9 (closure attribution).** For a creating method `c`,
> `closures(c) = { b ∈ S : creator(b) = c }`, and the prompt entry for `c`
> reports `(|closures(c)|, |closures(c) \ C|)`.

`creator(b)` is resolved through the generated class — the caller of its
constructor, the callee of its interface method — not by parsing the body name,
which carries the enclosing method's name without its parameter types and so
cannot separate overloads (72 of 450 bodies in this jar have an overloaded
enclosing name). When the creator is a public API entry rather than a deep
target, the note is emitted by the API phase instead.

### 5.2 Routes

**Before.** `KafkaScheduler.lambda$schedule$1` contains one `runnable.run()`,
which the analysis resolves to **207 possible receivers**. Being one hop from a
sampled frame, it headed the shortest path to anything behind any of those
receivers, and the same meaningless prefix opened 28 unrelated prompt entries.

**Now.** Two edges that the analysis dump renders alike are distinguished.

> **Definition 10 (dispatch site).** A call site `s` with target set `T(s)` is a
> *dispatch site* iff `¬direct(s)` and some `t ∈ T(s)` is declared by a
> generated lambda class. Every edge out of `s` is a dispatch edge — real
> implementations included, since they share the one call site and the one
> missing fact.
>
> **Definition 11 (creation edge).** For a generated class `L` with constructor
> caller `c` and interface-method callee `b`, `(c, b)` is a creation edge.
>
> **Definition 12 (route).** A route is a shortest path in
> `G' = (E \ dispatch) ∪ creation`.

A closure body is therefore reached through the method that captures it, never
through the method that happens to invoke the interface. Dispatch edges stay in
`G`: removing them would cut reachability through the node, which is a different
and much worse defect.

A hard fan-out threshold ("drop sites with more than 20 targets") was considered
and **rejected**: 17 such sites in this jar are genuine library architecture —
`ProcessorNode.process` with 34 implementations, `StateStore.init` with 31 — and
the rule above leaves them intact.

Finally, prompt paths render synthetic nodes as their creator and collapse
consecutive nodes that render alike; the untranslated path is retained in JSON.
A route reaching its target through a closure handed to a scheduler or executor
says so, because the body then runs on another thread and a test that does not
wait for it covers nothing.

**Measured**, replaying iteration 1 of the 9055 artifacts:

| | before | after |
| --- | ---: | ---: |
| Synthetic rows in the prompt (of 200) | 97 | 0 |
| Prompt paths through the scheduler hub | 28 | 0 |
| `lambda$` occurrences in the prompt | 126 | 0 |
| Generated class names in the prompt | 114 | 0 |
| Deep denominator / covered | 3142 / 1086 | 3142 / 1086 |

50 targets lost a route they should never have had and fall back to a longer
honest one or to none; 2 gained a route through a creation edge; public-entry
joins rise from 10 to 67; median route length rises from 2 to 3. Paths got
longer because they stopped lying.

### 5.3 The same defect without a lambda

Definition 10 fires only when a generated lambda class declares one of the
targets. Ordinary interface dispatch has the identical missing fact and escapes
it. Three call sites in the deep prompt of the 2026-08-12 run:

| call site | bci | declared target | resolved targets |
| --- | ---: | --- | ---: |
| `KafkaServer.startup()` | 255 | `java.lang.Object#equals` | **1711** |
| `Utils.closeAll(…)` | 29 | `java.io.Closeable#close` | 214 |
| `SessionTrackerImpl.run()` | 42 | `java.util.Iterator#hasNext` | 135 |

One bytecode instruction, one row per resolved target in `call_tree_targets`, so
the graph holds 1711 edges out of a single `invokevirtual`. `SessionTrackerImpl`
is ZooKeeper's session-expiry thread, sampled because the metadata test starts
an embedded broker; the iterator it actually advances is a JDK collection
iterator, and the 25 Kafka store iterators in that target set are exactly the
ones JaCoCo reports as never executed. Being one hop from a sampled frame, they
outrank every honest longer route: all 200 prompt routes in that iteration have
`stepsRemaining: 1`.

The distinguishing fact is not fan-out but what the call site declares. The
`TargetId` column of `call_tree_invokes` already carries it.

> **Definition 10′ (dispatch site, generalised).** A call site `s` is a
> *dispatch site* iff `¬direct(s)` and the owner of the statically declared
> target of `s` is not a library type. Definition 10 is the special case where
> that owner is a generated lambda class.

`ProcessorNode.process` declares `Processor#process` and `StateStore.init`
declares a library interface, so both survive with their 34 and 31
implementations intact — the fan-out threshold rejected above stays rejected.

**Measured** over the 200 rows of the first deep prompt:

| first edge of the route | rows |
| --- | ---: |
| virtual, declared on a library type | 100 |
| **virtual, declared on a JDK type** | **70** |
| direct call | 29 |
| virtual, declared on another Kafka artifact | 1 |

The 70 break down as `Closeable#close` 26, `Iterator#hasNext` 24, `Object#equals`
7, `#hashCode` 7, `#toString` 4, `Thread#run` 1, `LinkedHashMap#removeEldestEntry`
1.

**Seeds need no separate filter.** Those 70 rows are *the same rows* as the 70
whose route head is not a library method — the two sets coincide exactly, and
not by chance: a foreign frame has no other way into the library. Counting every
call site in the image whose caller is foreign and whose declared target is a
library type:

| foreign caller | such call sites |
| --- | ---: |
| `com.oracle.svm` factory holders | 310 |
| the coordinate's own test sources | 267 |
| `org.apache.zookeeper` | 0 |
| `kafka.server`, `org.apache.kafka.clients`, `org.apache.kafka.common` | 0 |

So Definition 10′ removes ZooKeeper and the broker on its own, while keeping the
test sources — which are foreign by jar and are the best seeds available, being
what the suite actually drives. A seed whitelist would have to distinguish them
from the 67 sampled frames of Kafka's own `…streams.integration.*` tests, which
share the library's package and belong to the `:test` jar: the §4 lesson again,
avoided by not writing the list. The factory holders should render as the
constructor they wrap, as §5.1 already does for lambdas.

**Measured after the rule**, replaying iteration 0 of the 9055 artifacts through
`code_coverage_profile_report.py`:

| | before | after |
| --- | ---: | ---: |
| Prompt rows whose route head is a library method (of 200) | 130 | **200** |
| Distinct route heads in the prompt | 56 | 79 |
| Prompt joins: sampled / public-entry | 200 / 0 | 158 / 42 |
| Prompt routes of 2 / 3 nodes | 200 / 0 | 174 / 26 |
| Uncovered targets holding any route (of 2098) | 1221 | 1221 |
| Deep denominator / covered | 3142 / 1044 | 3142 / 1044 |

69 of 200 prompt rows are replaced. Reachability is untouched — dispatch edges
stay in `G` and leave only `G'`, so the same 1221 targets hold a route — and the
coverage figures do not move, because the rule reaches routes alone. A dump
carrying no declared target for a site leaves that site routing and says so in a
caveat, rather than silently dropping every edge it has.

---

## 6. Loop hygiene

Three defects spent passes without producing anything. The first two are fixed.

**Budget as a function of a moving definition.** The per-phase budget scaled as
`baseline_uncovered / 500`, read from the measurement summary. When that summary
was redefined to report only methods JaCoCo mentions, entries absent from the
report moved to a separate bucket and stopped counting — dropping the API budget
from 8 passes to 4 with no change to the budget code. Phase length must not be a
function of a field whose meaning can move underneath it, so both phases now run
a constant `coverage_iterations` passes (default 6) and still stop early when
nothing actionable remains.

**Unspecified worktree base.** Conversion said only "create or reuse one
worktree". Two runs of the same coordinate picked differently — one branched
from `HEAD`, one reset to `origin/master` — and since measurement resolves this
workflow's helpers from the issue worktree, the second run hit hard failures in
the call-graph and ranking steps and spent **two of its four cover passes
repairing them**. Conversion now states the base commit explicitly.

**Missing test-support artifact — still open.** Two Kafka jars share the word
"test" and are unrelated. `kafka-streams:3.6.0:test` is declared in the test
project's `build.gradle` and is the *measurement* defect of §4 — 996 classes of
Kafka's own unit tests, now filtered out. `kafka-streams-test-utils` is a
different artifact, is **not** declared, and is where `TopologyTestDriver`
lives. Driving internal targets through public behavior needs that driver, so
deep pass 1 wrote a scenario against it, failed to compile, added the
dependency, and repaired serdes and output cardinality until the build passed:
**1.39 M tokens and 29 turns, the most expensive deep pass of the run, for zero
newly covered methods**. The cover instructions say only "never touch the
regular `src/test` sources" and never mention `build.gradle`, so the agent
discovers the gap by build failure. Prepare should resolve the library's
test-support artifact up front, and the cover states should explicitly permit an
additive `testImplementation` line.

---

## 7. What the measurements actually say

The changes above are defect removal. They are not, on the evidence, a lever on
the outcome — and the distinction matters for deciding what to do next.

**The prompt is not what produces coverage.** In the first API cover pass, 289
public methods became covered:

| Source | Methods | Share |
| --- | ---: | ---: |
| Were prompt targets | 42 | 15% |
| Not targets, but in classes the prompt named | 71 | 25% |
| In classes the prompt never mentioned | 176 | **61%** |

The agent hit 42 of 200 offered targets; the rest is collateral from writing a
realistic test of some subsystem.

**`unlocks` does not predict coverage.** Among the 200 ranked targets, median
`unlocks` was 3.0 for both covered and uncovered entries, and the *mean* was
higher among the uncovered (25.0 vs 16.2). The largest static reach belongs to
orchestration methods that need a live runtime — reachability is, in practice,
mildly anti-correlated with feasibility. Realization across the whole phase was
about 6%: internal coverage moved +287 against ~4630 nominally unlocked.

**Deep prompt hit rates** are 3.0% for synthetic rows and 4.9% for ordinary
methods. Both are low; removing the synthetic rows frees half a prompt, but it
does not change what the agent can construct per pass.

### 7.1 What the gain per pass actually tracks

The paragraphs above say what does *not* explain the outcome. This one says what
does. Call a class **newly opened** by a pass if it had zero covered methods
before and at least one after.

| pass | API new classes | API Δ | deep new classes | deep Δ |
| ---: | ---: | ---: | ---: | ---: |
| 1 | 47 | +207 | 0 | 0 |
| 2 | 27 | +102 | 33 | +202 |
| 3 | 8 | +55 | 23 | +152 |
| 4 | 6 | +55 | 0 | +11 |
| 5 | 4 | +27 | 7 | +47 |
| 6 | 1 | +26 | 0 | 0 |

Roughly 5–6 covered methods per newly opened class, in both phases and in the
earlier run. Meanwhile the prompt hit rate stays flat — 9–13% in the API phase
across all six passes — while the gain falls eightfold. Hits do not explain the
curve; opened classes do.

The mechanism is how JaCoCo credit arrives. A test that first instantiates a
class runs its constructor, a few accessors and one or two real methods in the
same motion; a class already touched yields one method per unit of new work
after that. Supply was not the constraint: the pass-6 API prompt still named 25
classes with no coverage at all, and the agent opened one, extending the
scenarios it had already built.

> **Rule.** Score a pass by the classes it opens, not by the targets it hits.

### 7.2 The API prompt goes stale; the deep prompt over-rotates

Over the 6 × 200 = 1200 API prompt slots of that run:

- 112 entries appeared in **all six** prompts; 108 of them were never covered.
- **911 of 1200 slots** went to entries already offered three or more times and
  never covered.
- Only 314 distinct entries were ever shown, out of 3312 eligible candidates.

Ranking by reach is stable by construction: an entry's score barely moves while
it stays uncovered, so the top of the list is frozen for the whole phase. The
deep prompt has the opposite defect — consecutive-pass overlap is 0 to 3 of 200,
because route distance always finds more than 200 fresh candidates at distance
1, so a target the agent nearly reached is never offered again.

**Conclusion.** Ordering optimizes a list that explains 15–25% of the outcome,
so no ordering can produce a large jump. What the per-pass numbers add is where
the rest goes: a pass is worth what it opens, and both prompts fail at that in
opposite ways — the API list is frozen on entries that have already refused to
be covered, the deep list discards every near-miss. The binding constraint
remains the number of realistic scenarios an agent can build in one pass, which
is why two runs a day apart landed within 8% of each other (+558 and +603
internal methods).

---

## 8. Open items

In the order the measurements support:

1. **Feasibility over reach.** Use attempt history as an empirical signal: an
   entry offered three times and never covered should sink. This frees the dead
   top of every prompt, which ranking by reach cannot do.
2. **Subsystems over method lists.** With 61% of the gain landing outside prompt
   classes, the agent already works subsystem-first. Prose targets ("cover the
   session-store query surface end to end") may outperform 200 signatures.
3. **More passes.** Costly, and the only lever that moves the actual constraint.
4. **Foreign targets at dispatch sites** — done differently, in §5.3. The
   observation was that 101 of the 207 receivers at the scheduler site were not
   Kafka at all (logback, Scala, AssertJ), and that the library filter of §4
   could be applied to edge target sets. Filtering *resolved* targets narrows
   the fan-out (1711 → 72 at the worst site) without making the edge true, so
   Definition 10′ filters on the *declared* target and removes the edge itself.
   Nothing is left to do here.
5. **Trivially callable targets.** 390 library methods in the call tree are
   reachable only through high fan-out sites and are overwhelmingly `equals`,
   `hashCode`, `toString`. A path is irrelevant for them; they deserve a
   "call it and assert" marking rather than a route.
