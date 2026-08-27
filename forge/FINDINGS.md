# Forge pre-push review findings

Rendered by Forge from the pre-push branch review of §FS-local-branch-review.
Newest entry first; every non-approval is recorded, including one a repair later cleared.

## 2026-08-28 — org.junit.platform:junit-platform-commons:1.11.0 (#9209)

**Resource dynamic-access coverage regresses beyond the fixes-javac limit**

In `stats/org.junit.platform/junit-platform-commons/1.11.0/stats.json`, resource dynamic-access coverage is 66.67% (2/3), down from 100% (2/2) for 1.8.2: a 33.33 percentage-point drop. The `fixes-javac-fail` rule applies the 20-point limit to present breakdown entries as well as the overall report. Restore coverage for the uncovered `ModuleUtils$ModuleReferenceResourceScanner.loadResourceUnchecked` call site or provide a concrete, credible explanation of why the changed upstream API surface makes that call site unsuitable for coverage.
## 2026-08-27 — org.junit.jupiter:junit-jupiter-api:5.11.4 (#9206)

**Repair drops below the metadata-entry guardrail**

The resolved evidence reports 4 library metadata entries plus 13 test-only entries for 5.11.4 (17 total), versus 70 for 5.8.2. Since 17 is below 25% of 70 (17.5), this violates the fixes-java-run-fail metadata-entry guardrail. Regenerate enough justified metadata to clear the threshold or provide a concrete, credible explanation of the changed API/runtime surface.
