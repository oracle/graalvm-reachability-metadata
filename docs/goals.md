# GOAL-repository-direction: Where: repository direction and outcomes

The GraalVM Reachability Metadata Repository should preserve Native Image
library compatibility by shipping only library reachability metadata: additive
registrations that describe library dynamic access without patching libraries,
changing class-initialization semantics, running hosted features, or encoding
application-specific behavior.
§GRUND-repository-motivation

The desired repository outcomes are:

- Every metadata file is justified by a corresponding test that exhibits the
  dynamic access the metadata registers.
- Supported artifacts have as many tested versions as practical, so consumers
  can rely on metadata across current and historical versions of the same
  artifact.
- Version coverage grows without weakening already-supported libraries,
  removing tested metadata, or breaking the repository's additive metadata
  contract.
