# Hessian test coverage

Tests use Hessian and Burlap streams, codec registration, bean serialization,
classpath codec discovery, and loopback RPC services. Each actionable
dynamic-access class has a dedicated test file, including field and annotation
member codecs. Test-only codec registrations live in `META-INF/hessian/`.

Some reported sites have no supported consumer path:

- `AnnotationSerializer$DateMethodSerializer`: Java annotation members cannot
  return `Date` or any SQL temporal type. No artificial annotation interface is
  introduced to reach this branch.
- `HessianSerializerInput.readObjectImpl`: the subclass declares this protected
  method, but its superclass never calls it. Its public stream API is tested
  without exposing or invoking the unused method.
- `JavaSerializer`'s external replacement-factory branch has no assignment to
  its factory field; normal object replacement is tested instead.
- `WriteReplaceSerializer` overwrites a discovered external replacement method
  with the object's zero-argument method, but passes one argument when invoking
  an external replacement factory. That invocation cannot succeed. Tests cover
  object replacement and explicit replacement-codec registration in preference
  to a working convention-named codec, including discovery and inspection of
  that codec. The external replacement invocation is not asserted as a failure.
- Constructor fallbacks without an instantiable class, and collection fallbacks
  requiring a non-collection type, are not exercised through invalid inputs.
