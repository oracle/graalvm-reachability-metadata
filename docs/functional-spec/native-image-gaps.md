# FS-native-image-gaps: Scenarios Native Image does not implement

Some JDK behavior does not work in a native image whatever metadata is shipped,
because Native Image does not implement it. For example, Native Image's
management support has a fixed set of platform MXBeans, and
`jdk.management.VirtualThreadSchedulerMXBean` is not one of them. A test that
exercises such behavior fails in the native lane on every attempt. The failure
is not metadata the contribution owes. It is not a builder defect either
(§FS-graalvm-blocked-work.1), and it may not be skipped or tolerated
(§FS-test-contract.4.1, §FS-test-contract.4.2). If the test keeps the scenario,
the whole version waits on a GraalVM change, and the metadata the rest of the
test justifies never ships (§GOAL-broad-version-coverage).

A test therefore does not target such a scenario. When a native failure turns
out to be one, the test drops that scenario and keeps the rest of its
coverage. At that spot it links the GraalVM issue that tracks the gap, so the
scenario comes back once Native Image implements the behavior. Coverage that
cannot run in a native image is not coverage §FS-test-contract.3.6 asks a fix
to keep.

A failure counts as a gap only when Native Image's sources or the tracking
issue show that the behavior is unimplemented. Anything else is a missing
registration the contribution owes (§FS-test-contract.2.7), or behavior that
requires runtime class definition (§FS-test-contract.4.5).

```java
// Native Image has no VirtualThreadSchedulerMXBean, so this test does not call
// getVirtualThreads(): https://github.com/oracle/graal/issues/12802 (GR-72683)
assertThat(new ProcessInfo().getPid()).isPositive();
```
