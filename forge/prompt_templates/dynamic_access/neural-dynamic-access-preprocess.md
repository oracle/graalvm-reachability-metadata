Role: You are an expert JVM source-code analyst specializing in GraalVM Native Image reachability metadata.

Task:
Inspect the read-only source context for `{library}` and produce a source-derived dynamic-access seed report. This is a preprocessing step only.

Source Context:
{source_context_overview}

Write these files exactly:
- `{neural_dynamic_access_output_dir}/reflection-calls.json`
- `{neural_dynamic_access_output_dir}/resources-calls.json`
- `{neural_dynamic_access_output_dir}/serialization-calls.json`
- `{neural_dynamic_access_output_dir}/proxy-calls.json`

Output format:
- Each file must be a JSON object.
- Each key must be one tracked runtime API from the tables below.
- Each value must be a list of stack-frame strings for source call sites that invoke that tracked API.
- Stack frames must use this format: `fully.qualified.ClassName.methodName(SourceFile.java:line)`.
- Constructors and class initializers must use `<init>` or `<clinit>` as the method name.
- If there are no calls for a file type, write `{{}}` for that file.

Reflection runtime APIs:
| Tracked API |
|---|
| `java.lang.Class#forName(java.lang.String)` |
| `java.lang.Class#forName(java.lang.String, boolean, java.lang.ClassLoader)` |
| `java.lang.Class#getConstructor(java.lang.Class[])` |
| `java.lang.Class#getConstructors()` |
| `java.lang.Class#getDeclaredConstructor(java.lang.Class[])` |
| `java.lang.Class#getDeclaredField(java.lang.String)` |
| `java.lang.Class#getDeclaredMethod(java.lang.String, java.lang.Class[])` |
| `java.lang.Class#getDeclaredMethods()` |
| `java.lang.Class#getMethod(java.lang.String, java.lang.Class[])` |
| `java.lang.Class#getMethods()` |
| `java.lang.Class#newInstance()` |
| `java.lang.ClassLoader#findSystemClass(java.lang.String)` |
| `java.lang.ClassLoader#loadClass(java.lang.String)` |
| `java.lang.reflect.Array#newInstance(java.lang.Class, int)` |
| `java.lang.reflect.Constructor#newInstance(java.lang.Object[])` |
| `java.lang.reflect.Field#get(java.lang.Object)` |
| `java.lang.reflect.Method#invoke(java.lang.Object, java.lang.Object[])` |

Resource runtime APIs:
| Tracked API |
|---|
| `java.lang.Class#getResource(java.lang.String)` |
| `java.lang.Class#getResourceAsStream(java.lang.String)` |
| `java.lang.ClassLoader#getResource(java.lang.String)` |
| `java.lang.ClassLoader#getResourceAsStream(java.lang.String)` |
| `java.lang.ClassLoader#getResources(java.lang.String)` |

Serialization runtime APIs:
| Tracked API |
|---|
| `java.io.ObjectInputStream#readObject()` |
| `java.io.ObjectInputStream#resolveClass(java.io.ObjectStreamClass)` |
| `java.io.ObjectOutputStream#writeObject(java.lang.Object)` |

Proxy runtime APIs:
| Tracked API |
|---|
| `java.lang.reflect.Proxy#getProxyClass(java.lang.ClassLoader, java.lang.Class[])` |
| `java.lang.reflect.Proxy#newProxyInstance(java.lang.ClassLoader, java.lang.Class[], java.lang.reflect.InvocationHandler)` |

Rules:
- Do not create, edit, compile, or run tests in this preprocessing step.
- Do not add reachability metadata.
- Include only direct source call sites in the provided library source context.
- Prefer exact line numbers from the source files. Do not invent line numbers.
- Do not include JDK/internal dependency call sites unless the library source itself directly calls the tracked runtime API.
- Use the exact tracked API key from the table. Do not normalize to a different signature.
- Write only the requested JSON files; no notes, summaries, markdown, or extra files are needed.

Example `reflection-calls.json` shape:
```json
{{
  "java.lang.Class#getDeclaredMethod(java.lang.String, java.lang.Class[])": [
    "com.example.LibraryFeature.resolve(LibraryFeature.java:42)"
  ]
}}
```
