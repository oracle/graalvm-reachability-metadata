# AR-code-coverage-prompt-handoff: Cover prompt handoff

Measurement hands the rendered cover prompt to the cover state as a Rhei state
handoff, not as a path (§AR-code-coverage-improvement.5): the measure state
declares the prompt file it writes as a `kind: handoff` output, the cover state
inherits it from `transition.previous` as required, and Rhei injects the whole
file into the agent message under `## Handoff from <measure-state>`, refusing
to spawn the agent when the prompt is missing or empty. The file on disk stays
as the audit copy.

An agent told to read a long file reads part of it or misreads its sections,
and every target it never sees is coverage the pass cannot add
(§GOAL-maximize-library-coverage). On the h2 2.1.210 benchmark run of
2026-10-05, deep pass 5 read 120 of about 1200 prompt lines and covered 6 of
200 targets, and deep pass 8 attempted only the 2 targets of the trailing
section and ignored the 198 above it.

Rhei checks a program's declared outputs only on a zero exit, so the measure
program writes its stop summary to the prompt path when it completes the phase;
the loop-continue exit is gated by the cover state's required inheritance
instead.
