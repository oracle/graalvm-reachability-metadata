# XJC test coverage

The tests compile XML Schemas into source models, validate JAXB binding documents,
exercise the property-accessor plugin, and marshal/unmarshal through the context
factory bridge. Generated Java is inspected as source, never loaded at runtime.

Two reported classes have no safe dynamic-access scenario in this suite:

- `com.sun.tools.xjc.XJCFacade`: its reflective entry point calls `Driver.main`,
  which terminates the process with `System.exit`. A subprocess would not exercise
  the native test executable's metadata. The embedded compiler APIs are tested
  instead.
- `com.sun.tools.xjc.reader.dtd.bindinfo.Messages`: the source tree contains no
  caller of `format` or its `ERR_UNDEFINED_FIELD` constant. Tests do not reflect
  into this package-private, unused formatter.
