/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.classfile.ClassFile;
import java.lang.classfile.ClassModel;
import java.lang.classfile.MethodModel;
import java.lang.classfile.instruction.InvokeDynamicInstruction;
import java.lang.classfile.instruction.InvokeInstruction;
import java.lang.constant.DirectMethodHandleDesc;
import java.lang.constant.MethodTypeDesc;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.lang.reflect.AccessFlag;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/// Bytecode call-graph extractor for the code coverage improvement workflow
/// (§WF-code-coverage-improvement.3.1.1, §WF-code-coverage-improvement-architecture.1).
///
/// Reads library jars with the JDK Class-File API and writes three CSV files:
/// one row per declared method, one row per call edge, and one row per declared
/// type. Method identities are the canonical `owner#name(params):ret` form
/// produced by `utility_scripts/code_coverage_model.py`, so extractor output
/// joins directly with JaCoCo evidence and the API inventory.
///
/// `types.csv` carries the declared supertypes that receiver-obtainability
/// analysis needs (§WF-code-coverage-improvement.3.1.2). The hierarchy is
/// already parsed here for class-hierarchy analysis; writing it out saves the
/// ranking step a second pass over the jars. The join key both ways is the
/// owner: everything before `#` in a canonical id is a `types.csv` name.
///
/// Class files are read directly rather than parsed out of `javap` text: raw
/// descriptors stay exact and `invokedynamic` lambda bodies stay reachable
/// through their bootstrap method handle.
///
/// Virtual and interface call sites are resolved by class-hierarchy analysis
/// over the classes present in the supplied jars, so edges over-approximate
/// dispatch. That is sufficient for ranking and is never coverage evidence.
///
/// Usage:
///   java --source 25 CallGraphExtractor.java --output-dir DIR JAR [JAR...]
public final class CallGraphExtractor {

    /// One declared method: its canonical id and whether it carries a body.
    /// `isStatic` is what tells eligibility analysis that an entry needs no
    /// receiver at all (§WF-code-coverage-improvement.3.1.2).
    private record MethodNode(String id, boolean hasCode, boolean isPublicApi, boolean isStatic) {
    }

    /// Declared supertypes of one class, used for hierarchy analysis.
    private record ClassNode(String superName, List<String> interfaceNames, boolean isPublic) {
    }

    /// One call edge. Canonical ids contain commas, so caller and callee are
    /// kept apart rather than joined into one delimited string.
    private record Edge(String caller, String callee) implements Comparable<Edge> {
        @Override
        public int compareTo(Edge other) {
            int byCaller = caller.compareTo(other.caller);
            return byCaller != 0 ? byCaller : callee.compareTo(other.callee);
        }
    }

    private final Map<String, ClassNode> classes = new HashMap<>();
    private final Map<String, List<MethodNode>> methodsByOwner = new TreeMap<>();
    /// `owner#name(params):ret` of every declared method, for edge filtering.
    private final Set<String> declaredIds = new LinkedHashSet<>();
    /// `owner` -> direct subtypes, inverted from `classes` after parsing.
    private final Map<String, List<String>> subtypes = new HashMap<>();
    private final Set<Edge> edges = new TreeSet<>();

    public static void main(String[] args) throws IOException {
        Path outputDir = null;
        List<Path> jars = new ArrayList<>();
        for (int index = 0; index < args.length; index++) {
            if ("--output-dir".equals(args[index]) && index + 1 < args.length) {
                outputDir = Path.of(args[++index]);
            } else {
                jars.add(Path.of(args[index]));
            }
        }
        if (outputDir == null || jars.isEmpty()) {
            System.err.println("usage: CallGraphExtractor --output-dir DIR JAR [JAR...]");
            System.exit(2);
        }

        CallGraphExtractor extractor = new CallGraphExtractor();
        // Two passes over the jars: edge filtering and hierarchy analysis both
        // need the complete method and class universe, which only exists after
        // every jar has been read.
        for (Path jar : jars) {
            extractor.eachClass(jar, extractor::declare);
        }
        extractor.indexSubtypes();
        for (Path jar : jars) {
            extractor.eachClass(jar, extractor::collectEdges);
        }
        extractor.write(outputDir);
    }

    /// Parse every class entry in one jar and hand each model to `action`.
    private void eachClass(Path jar, java.util.function.Consumer<ClassModel> action)
            throws IOException {
        try (ZipFile zip = new ZipFile(jar.toFile())) {
            Enumeration<? extends ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                if (entry.isDirectory() || !entry.getName().endsWith(".class")) {
                    continue;
                }
                byte[] bytes;
                try (var stream = zip.getInputStream(entry)) {
                    bytes = stream.readAllBytes();
                }
                try {
                    action.accept(ClassFile.of().parse(bytes));
                } catch (IllegalArgumentException error) {
                    // A malformed or unsupported-version entry must not abort the
                    // whole extraction; ranking degrades, correctness does not.
                    System.err.printf("warning: skipped %s in %s: %s%n",
                            entry.getName(), jar.getFileName(), error.getMessage());
                }
            }
        }
    }

    /// Record one class's methods and declared supertypes.
    private void declare(ClassModel model) {
        String owner = binaryName(model.thisClass().asInternalName());
        boolean isPublicClass = model.flags().has(AccessFlag.PUBLIC);
        classes.put(owner, new ClassNode(
                model.superclass().map(entry -> binaryName(entry.asInternalName())).orElse(null),
                model.interfaces().stream().map(entry -> binaryName(entry.asInternalName())).toList(),
                isPublicClass));

        List<MethodNode> declared = new ArrayList<>();
        for (MethodModel method : model.methods()) {
            String id = canonicalId(owner, method.methodName().stringValue(),
                    method.methodTypeSymbol());
            boolean isPublicMethod = isPublicClass && method.flags().has(AccessFlag.PUBLIC);
            declared.add(new MethodNode(id, method.code().isPresent(), isPublicMethod,
                    method.flags().has(AccessFlag.STATIC)));
            declaredIds.add(id);
        }
        methodsByOwner.put(owner, declared);
    }

    /// Record the call edges of every method body in one class.
    private void collectEdges(ClassModel model) {
        String owner = binaryName(model.thisClass().asInternalName());
        for (MethodModel method : model.methods()) {
            if (method.code().isEmpty()) {
                continue;
            }
            String callerId = canonicalId(owner, method.methodName().stringValue(),
                    method.methodTypeSymbol());
            collectEdges(callerId, method.code().get());
        }
    }

    /// Invert the declared-supertype map so hierarchy analysis can walk down.
    private void indexSubtypes() {
        classes.forEach((owner, node) -> {
            if (node.superName() != null) {
                subtypes.computeIfAbsent(node.superName(), key -> new ArrayList<>()).add(owner);
            }
            for (String interfaceName : node.interfaceNames()) {
                subtypes.computeIfAbsent(interfaceName, key -> new ArrayList<>()).add(owner);
            }
        });
    }

    /// Record the edges of one method body, expanding virtual dispatch by CHA.
    private void collectEdges(String callerId, java.lang.classfile.CodeModel code) {
        code.elementStream().forEach(element -> {
            if (element instanceof InvokeInstruction invoke) {
                addEdges(callerId, binaryName(invoke.owner().asInternalName()),
                        invoke.name().stringValue(), invoke.typeSymbol(), invoke.isInterface());
            } else if (element instanceof InvokeDynamicInstruction indy) {
                // The call site itself is synthetic; the useful edge is to the
                // bootstrap target, which for a lambda is its generated body.
                for (var argument : indy.bootstrapArgs()) {
                    // A bootstrap argument may be a field-kind handle, whose
                    // lookup descriptor is a field descriptor and names no call.
                    if (argument instanceof DirectMethodHandleDesc handle
                            && !isFieldKind(handle.kind())) {
                        // `displayName()` is the simple name, which would only
                        // match for classes in the default package.
                        addEdges(callerId, sourceName(handle.owner().descriptorString()),
                                handle.methodName(),
                                MethodTypeDesc.ofDescriptor(handle.lookupDescriptor()), false);
                    }
                }
            }
        });
    }

    /// A field-kind handle names a field access, whose lookup descriptor is a
    /// field descriptor rather than a method descriptor.
    private static boolean isFieldKind(DirectMethodHandleDesc.Kind kind) {
        return switch (kind) {
            case GETTER, SETTER, STATIC_GETTER, STATIC_SETTER -> true;
            default -> false;
        };
    }

    /// Add the declared-target edge plus one edge per override found by CHA.
    private void addEdges(String callerId, String owner, String name,
                          MethodTypeDesc descriptor, boolean isInterface) {
        String calleeId = canonicalId(owner, name, descriptor);
        if (declaredIds.contains(calleeId)) {
            edges.add(new Edge(callerId, calleeId));
        }
        boolean isConstructor = "<init>".equals(name);
        if (isConstructor) {
            return;
        }
        for (String subtype : allSubtypes(owner)) {
            String overrideId = canonicalId(subtype, name, descriptor);
            if (declaredIds.contains(overrideId)) {
                edges.add(new Edge(callerId, overrideId));
            }
        }
    }

    /// Every transitive subtype of `owner` present in the supplied jars.
    private Set<String> allSubtypes(String owner) {
        Set<String> found = new LinkedHashSet<>();
        List<String> pending = new ArrayList<>(subtypes.getOrDefault(owner, List.of()));
        while (!pending.isEmpty()) {
            String current = pending.remove(pending.size() - 1);
            if (found.add(current)) {
                pending.addAll(subtypes.getOrDefault(current, List.of()));
            }
        }
        return found;
    }

    /// `owner#name(p1,p2):ret`, matching `MethodRef.canonical_id`.
    private static String canonicalId(String owner, String name, MethodTypeDesc descriptor) {
        StringBuilder params = new StringBuilder();
        for (int index = 0; index < descriptor.parameterCount(); index++) {
            if (index > 0) {
                params.append(',');
            }
            params.append(sourceName(descriptor.parameterType(index).descriptorString()));
        }
        return owner + "#" + name + "(" + params + "):"
                + sourceName(descriptor.returnType().descriptorString());
    }

    /// Convert one JVM type descriptor to the source form used by the identity model.
    private static String sourceName(String descriptor) {
        int dimensions = 0;
        while (dimensions < descriptor.length() && descriptor.charAt(dimensions) == '[') {
            dimensions++;
        }
        String base = descriptor.substring(dimensions);
        String name = switch (base) {
            case "V" -> "void";
            case "Z" -> "boolean";
            case "B" -> "byte";
            case "C" -> "char";
            case "S" -> "short";
            case "I" -> "int";
            case "J" -> "long";
            case "F" -> "float";
            case "D" -> "double";
            default -> binaryName(base.substring(1, base.length() - 1));
        };
        return name + "[]".repeat(dimensions);
    }

    private static String binaryName(String internalName) {
        return internalName.replace('/', '.');
    }

    private void write(Path outputDir) throws IOException {
        Files.createDirectories(outputDir);
        List<String> methodRows = new ArrayList<>();
        methodRows.add("id,hasCode,isPublicApi,isStatic");
        methodsByOwner.values().forEach(declared -> declared.forEach(node -> methodRows.add(
                quote(node.id()) + "," + node.hasCode() + "," + node.isPublicApi()
                        + "," + node.isStatic())));
        write(outputDir.resolve("methods.csv"), methodRows);

        List<String> edgeRows = new ArrayList<>();
        edgeRows.add("caller,callee");
        for (Edge edge : edges) {
            edgeRows.add(quote(edge.caller()) + "," + quote(edge.callee()));
        }
        write(outputDir.resolve("edges.csv"), edgeRows);

        // Interfaces are written as one semicolon-joined field: a type name can
        // never contain a semicolon, so the row stays a single CSV column.
        List<String> typeRows = new ArrayList<>();
        typeRows.add("name,isPublic,super,interfaces");
        new TreeMap<>(classes).forEach((owner, node) -> typeRows.add(
                quote(owner) + "," + node.isPublic() + ","
                        + quote(node.superName() == null ? "" : node.superName()) + ","
                        + quote(String.join(";", node.interfaceNames()))));
        write(outputDir.resolve("types.csv"), typeRows);

        System.out.printf("call graph: %d methods, %d edges, %d types%n",
                declaredIds.size(), edges.size(), classes.size());
    }

    private static String quote(String value) {
        return '"' + value.replace("\"", "\"\"") + '"';
    }

    private static void write(Path path, List<String> rows) {
        try {
            Files.write(path, String.join("\n", rows).concat("\n").getBytes(StandardCharsets.UTF_8));
        } catch (IOException error) {
            throw new UncheckedIOException(error);
        }
    }
}
