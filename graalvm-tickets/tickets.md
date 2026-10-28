# GraalVM tickets

The GraalVM tickets that the repository's workarounds for GraalVM and Native
Image defects cite (§root/PRCPL-graalvm-workaround-tickets), under their Jira
IDs. Each entry below is a snapshot of the ticket's public fields, written by
`grund fetch graal/GR-<number>` from the repository root; refresh one by
fetching it again rather than editing it by hand.

## GR-48702: Resource handling is not correct due to our substitutions

- Status: In Progress
- Resolution: Unresolved
- Fix versions: none
- Public links: none

## GR-65388: Type not found during analysis when treating "name" as "type"

- Status: Open
- Resolution: Unresolved
- Fix versions: none
- Public links: none

## GR-73955: Improve Missing*RegistrationErrors for conditions

- Status: In Progress
- Resolution: Unresolved
- Fix versions: none
- Public links: none

## GR-75812: Native tracing reports type access missing when constructor metadata is the actual missing access

- Status: Open
- Resolution: Unresolved
- Fix versions: none
- Public links: none

## GR-77787: Implement java.lang.instrument for crema

- Status: Open
- Resolution: Unresolved
- Fix versions: none
- Public links: none

## GR-80250: Add CI coverage for Gradle builds using SVM libjvm-based java

- Status: Open
- Resolution: Unresolved
- Fix versions: none
- Public links: none

## GR-80923: \[Native Image\] Agent in conditional mode writes resource `bundle` entries without their inferred `typeReached` condition

- Status: Open
- Resolution: Unresolved
- Fix versions: none
- Public links: https://github.com/oracle/graal/issues/14799

## GR-80926: \[Native Image\] Conditional agent writes a method reference's hidden class (Host$$Lambda/0x...) as the typeReached condition

- Status: Open
- Resolution: Unresolved
- Fix versions: none
- Public links: https://github.com/oracle/graal/issues/14801

## GR-80927: \[Native Image\] getPlatformMBeanServer() fails under --exact-reachability-metadata, even with --enable-monitoring=jmxserver

- Status: Open
- Resolution: Unresolved
- Fix versions: none
- Public links: https://github.com/oracle/graal/issues/14802

## GR-80931: \[Native Image 25.0\] TypeNotFoundError in buildRuntimeMetadata for a linkage error of a class whose typeReached condition is never reached

- Status: Open
- Resolution: Unresolved
- Fix versions: none
- Public links: https://github.com/oracle/graal/issues/14805

## GR-80932: \[Native Image\] Unlinkable ServiceLoader provider crashes the builder: "reachable during compilation, but was not seen during Bytecode parsing"

- Status: Open
- Resolution: Unresolved
- Fix versions: none
- Public links: https://github.com/oracle/graal/issues/14717

## GR-80933: \[Native Image 25.0\] Signer certificates of a class registered for reflection are not scanned by the analysis: "Type not found during analysis: X509CertInfo"

- Status: Open
- Resolution: Unresolved
- Fix versions: none
- Public links: https://github.com/oracle/graal/issues/14806

## GR-80934: \[Native Image\] JDK methods report internal `@com.oracle.svm.shared.Uninterruptible` via reflection; `Class.forName` on it fails with `class-for-name-respects-class-loader`

- Status: Open
- Resolution: Unresolved
- Fix versions: none
- Public links: https://github.com/oracle/graal/issues/14807

## GR-80935: \[Native Image\] Hello world fails to build with -H:-SimulateClassInitializer: "The origin of the constant ... should have been registered before"

- Status: Open
- Resolution: Unresolved
- Fix versions: none
- Public links: https://github.com/oracle/graal/issues/14808
