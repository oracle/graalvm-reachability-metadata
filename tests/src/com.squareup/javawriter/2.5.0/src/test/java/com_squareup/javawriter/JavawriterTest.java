/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_squareup.javawriter;

import com.squareup.javawriter.JavaWriter;
import java.io.Closeable;
import java.io.IOException;
import java.io.StringWriter;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.lang.model.element.Modifier;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class JavawriterTest {
    @Test
    void generatesCompleteClassWithImportsAnnotationsConstructorsAndControlFlow() throws IOException {
        StringWriter output = new StringWriter();
        JavaWriter writer = new JavaWriter(output);
        writer.setIndent("    ");

        Map<String, Object> annotationAttributes = new LinkedHashMap<>();
        annotationAttributes.put("value", JavaWriter.stringLiteral("native-test"));
        annotationAttributes.put(
                "tags",
                new Object[] {
                    JavaWriter.stringLiteral("integration"), JavaWriter.stringLiteral("javawriter")
                });

        writer.emitPackage("com.example")
                .emitImports(IOException.class, Closeable.class, List.class)
                .emitStaticImports("java.util.Collections.emptyList")
                .emitEmptyLine()
                .emitJavadoc("Generated for %s.", "tests")
                .emitAnnotation("com.example.Generated", annotationAttributes)
                .beginType(
                        "com.example.Sample",
                        "class",
                        EnumSet.of(Modifier.PUBLIC, Modifier.FINAL),
                        "java.lang.Object",
                        "java.io.Closeable")
                .emitField(
                        JavaWriter.type(List.class, "java.lang.String"),
                        "names",
                        EnumSet.of(Modifier.PRIVATE, Modifier.FINAL),
                        "emptyList()")
                .beginInitializer(true)
                .emitStatement("System.setProperty(%s, %s)",
                        JavaWriter.stringLiteral("sample.ready"), JavaWriter.stringLiteral("true"))
                .endInitializer()
                .beginConstructor(
                        EnumSet.of(Modifier.PUBLIC),
                        Arrays.asList(JavaWriter.type(List.class, "java.lang.String"), "names"),
                        Collections.singletonList("java.io.IOException"))
                .emitStatement("this.names = names")
                .endConstructor()
                .beginMethod(
                        "java.lang.String", "describe", EnumSet.of(Modifier.PUBLIC), "int", "count")
                .beginControlFlow("if (count > %d)", 0)
                .emitStatement("return %s + count", JavaWriter.stringLiteral("names="))
                .nextControlFlow("else")
                .emitStatement("return %s", JavaWriter.stringLiteral("empty"))
                .endControlFlow()
                .endMethod()
                .emitAnnotation(Override.class)
                .beginMethod("java.lang.String", "toString", EnumSet.of(Modifier.PUBLIC))
                .emitStatement("return describe(names.size())")
                .endMethod()
                .endType();

        assertThat(writer.getIndent()).isEqualTo("    ");
        assertThat(output).hasToString(
                """
                package com.example;

                import java.io.Closeable;
                import java.io.IOException;
                import java.util.List;
                import static java.util.Collections.emptyList;

                /**
                 * Generated for tests.
                 */
                @Generated(
                    value = "native-test",
                    tags = {
                        "integration",
                        "javawriter"
                    }
                )
                public final class Sample extends Object
                    implements Closeable {
                    private final List<String> names = emptyList();
                    static {
                        System.setProperty("sample.ready", "true");
                    }
                    public Sample(List<String> names)
                        throws IOException {
                        this.names = names;
                    }
                    public String describe(int count) {
                        if (count > 0) {
                            return "names=" + count;
                        } else {
                            return "empty";
                        }
                    }
                    @Override
                    public String toString() {
                        return describe(names.size());
                    }
                }
                """);
    }

    @Test
    void generatesEnumValuesAndInterfaceAbstractMethods() throws IOException {
        StringWriter output = new StringWriter();
        JavaWriter writer = new JavaWriter(output);

        writer.emitPackage("com.example")
                .beginType("com.example.Color", "enum", EnumSet.of(Modifier.PUBLIC))
                .emitEnumValues(Arrays.asList("RED", "GREEN", "BLUE"))
                .endType()
                .emitEmptyLine()
                .emitSingleLineComment("%s contract", "Named")
                .beginType("com.example.Named", "interface", EnumSet.of(Modifier.PUBLIC))
                .emitAnnotation(FunctionalInterface.class)
                .beginMethod("java.lang.String", "name", EnumSet.of(Modifier.PUBLIC))
                .endMethod()
                .endType();

        assertThat(output).hasToString(
                """
                package com.example;

                public enum Color {
                  RED,
                  GREEN,
                  BLUE;
                }

                // Named contract
                public interface Named {
                  @FunctionalInterface
                  public String name();
                }
                """);
    }

    @Test
    void emitsEnumValuesIndividuallyWithConstructorArguments() throws IOException {
        StringWriter output = new StringWriter();
        JavaWriter writer = new JavaWriter(output);

        writer.emitPackage("com.example")
                .beginType("com.example.HttpStatus", "enum", EnumSet.of(Modifier.PUBLIC))
                .emitEnumValue("OK(200, \"OK\")")
                .emitEnumValue("NOT_FOUND(404, \"Not Found\")", true)
                .emitField("int", "code", EnumSet.of(Modifier.PRIVATE, Modifier.FINAL))
                .emitField("java.lang.String", "reason", EnumSet.of(Modifier.PRIVATE, Modifier.FINAL))
                .beginConstructor(
                        Collections.emptySet(),
                        "int", "code",
                        "java.lang.String", "reason")
                .emitStatement("this.code = code")
                .emitStatement("this.reason = reason")
                .endConstructor()
                .beginMethod("int", "code", EnumSet.of(Modifier.PUBLIC))
                .emitStatement("return code")
                .endMethod()
                .beginMethod("java.lang.String", "reason", EnumSet.of(Modifier.PUBLIC))
                .emitStatement("return reason")
                .endMethod()
                .endType();

        assertThat(output).hasToString(
                """
                package com.example;

                public enum HttpStatus {
                  OK(200, "OK"),
                  NOT_FOUND(404, "Not Found");
                  private final int code;
                  private final String reason;
                  HttpStatus(int code, String reason) {
                    this.code = code;
                    this.reason = reason;
                  }
                  public int code() {
                    return code;
                  }
                  public String reason() {
                    return reason;
                  }
                }
                """);
    }

    @Test
    void compressesImportedPackageAndJavaLangTypes() throws IOException {
        StringWriter output = new StringWriter();
        JavaWriter writer = new JavaWriter(output);

        writer.emitPackage("com.example");
        writer.emitImports("com.other.Model", "java.util.Map", "java.util.List");

        assertThat(writer.compressType("java.util.Map<java.lang.String, com.example.Service>"))
                .isEqualTo("Map<String, Service>");
        assertThat(writer.compressType("java.util.List<java.lang.String[]>"))
                .isEqualTo("List<String[]>");
        assertThat(writer.compressType("com.example.Model"))
                .isEqualTo("com.example.Model");

        writer.setCompressingTypes(false);
        assertThat(writer.isCompressingTypes()).isFalse();
        writer.beginType("com.example.Container", "class")
                .emitField("java.util.List<java.lang.String>", "items")
                .endType();

        assertThat(output).hasToString(
                """
                package com.example;

                import com.other.Model;
                import java.util.List;
                import java.util.Map;
                class com.example.Container {
                  java.util.List<java.lang.String> items;
                }
                """);
    }

    @Test
    void rendersAnnotationAttributeForms() throws IOException {
        StringWriter output = new StringWriter();
        JavaWriter writer = new JavaWriter(output);

        Map<String, Object> compactAttributes = new LinkedHashMap<>();
        compactAttributes.put("name", JavaWriter.stringLiteral("one"));
        compactAttributes.put("priority", 3);
        compactAttributes.put("enabled", true);

        Map<String, Object> multilineAttributes = new LinkedHashMap<>();
        multilineAttributes.put("first", 1);
        multilineAttributes.put("second", 2);
        multilineAttributes.put("third", 3);
        multilineAttributes.put("fourth", 4);

        writer.emitPackage("com.example")
                .emitAnnotation("com.example.Marker")
                .emitAnnotation("com.example.Named", JavaWriter.stringLiteral("primary"))
                .emitAnnotation("com.example.Compact", compactAttributes)
                .emitAnnotation("com.example.Multiline", multilineAttributes)
                .beginType("com.example.Annotated", "class")
                .endType();

        assertThat(output).hasToString(
                """
                package com.example;

                @Marker
                @Named("primary")
                @Compact(name = "one", priority = 3, enabled = true)
                @Multiline(
                  first = 1,
                  second = 2,
                  third = 3,
                  fourth = 4
                )
                class Annotated {
                }
                """);
    }

    @Test
    void closesControlFlowWithTrailingClause() throws IOException {
        StringWriter output = new StringWriter();
        JavaWriter writer = new JavaWriter(output);

        writer.emitPackage("com.example")
                .beginType("com.example.RetryCounter", "class")
                .beginMethod("int", "countAttempts", EnumSet.of(Modifier.PUBLIC), "int", "max")
                .emitStatement("int attempts = 0")
                .beginControlFlow("do")
                .emitStatement("attempts++")
                .endControlFlow("while (attempts < max)")
                .emitStatement("return attempts")
                .endMethod()
                .endType();

        assertThat(output).hasToString(
                """
                package com.example;

                class RetryCounter {
                  public int countAttempts(int max) {
                    int attempts = 0;
                    do {
                      attempts++;
                    } while (attempts < max);
                    return attempts;
                  }
                }
                """);
    }

    @Test
    void stringAndTypeUtilitiesHandleEscapingGenericsAndRawTypes() {
        String charactersToEscape = "quote=\" slash=\\ backspace=\b tab=\t newline=\n "
                + "formfeed=\f return=\r bell=\u0007";
        String escapedCharacters = "\"quote=\\\" slash=\\\\ backspace=\\b tab=\\t newline=\\n "
                + "formfeed=\\f return=\\r bell=" + "\\u" + "0007\"";

        assertThat(JavaWriter.stringLiteral(charactersToEscape))
                .isEqualTo(escapedCharacters);
        assertThat(JavaWriter.type(Map.class, "java.lang.String", "java.lang.Integer"))
                .isEqualTo("java.util.Map<java.lang.String, java.lang.Integer>");
        assertThat(JavaWriter.type(List.class, "java.lang.String"))
                .isEqualTo("java.util.List<java.lang.String>");
        assertThat(JavaWriter.rawType("java.util.Map<java.lang.String, java.lang.Integer>"))
                .isEqualTo("java.util.Map");
        assertThat(JavaWriter.rawType("java.lang.String"))
                .isEqualTo("java.lang.String");

        assertThatThrownBy(() -> JavaWriter.type(Map.class, "java.lang.String"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsInvalidOrderingDuplicateImportsAndInvalidScopes() throws IOException {
        JavaWriter noPackageWriter = new JavaWriter(new StringWriter());
        assertThatThrownBy(() -> noPackageWriter.compressType("java.lang.String"))
                .isInstanceOf(IllegalStateException.class);

        JavaWriter packageWriter = new JavaWriter(new StringWriter());
        packageWriter.emitPackage("com.example");
        assertThatThrownBy(() -> packageWriter.emitPackage("com.example.again"))
                .isInstanceOf(IllegalStateException.class);

        JavaWriter duplicateImportWriter = new JavaWriter(new StringWriter());
        duplicateImportWriter.emitPackage("com.example");
        duplicateImportWriter.emitImports("java.util.List");
        assertThatThrownBy(() -> duplicateImportWriter.emitImports("java.util.List"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> duplicateImportWriter.emitImports("not a type"))
                .isInstanceOf(IllegalArgumentException.class);

        JavaWriter scopeWriter = new JavaWriter(new StringWriter());
        scopeWriter.emitPackage("com.example");
        scopeWriter.beginType("com.example.Invalid", "class");
        assertThatThrownBy(() -> scopeWriter.emitStatement("int count = 0"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(scopeWriter::endConstructor)
                .isInstanceOf(IllegalStateException.class);
    }
}
