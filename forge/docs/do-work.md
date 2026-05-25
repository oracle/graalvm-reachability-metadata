# DW-do-work-loop: do-work loop architecture

The do-work loop is Forge's long-running worker shell, the local entry path
into Forge's issue-resolution responsibility defined in
§FS-forge-issue-resolution-goal. `do-work.sh` is the stable entrypoint and
stays intentionally small: it
forwards `argv` unchanged to `do_up_to_date_work.sh`. `do_up_to_date_work.sh`
owns branch selection, self-update, work limits, review limits, stop-file
handling, sleep timing, and re-execing the latest worker script before the
next cycle.

The loop does not own issue semantics. It converts command-line flags and
environment variables into one bounded worker cycle, then delegates queue
selection and workflow routing to the orchestration layer described in
§ORCH-forge-orchestration-spec. This keeps
local operator controls outside of the Python issue dispatcher
(§AR-forge-control-plane) while preventing individual workflows from learning
about worker sleep, branch monitoring, or stop markers.

The do-work loop is architectural rather than behavioral: it does not need a
separate component functional spec unless worker semantics grow beyond
bootstrap, self-update, and cycle scheduling.
