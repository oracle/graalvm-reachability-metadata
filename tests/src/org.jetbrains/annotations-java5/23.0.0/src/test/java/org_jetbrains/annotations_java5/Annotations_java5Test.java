/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jetbrains.annotations_java5;

import java.lang.annotation.Annotation;
import java.util.Locale;

import org.intellij.lang.annotations.Flow;
import org.intellij.lang.annotations.Identifier;
import org.intellij.lang.annotations.Language;
import org.intellij.lang.annotations.MagicConstant;
import org.intellij.lang.annotations.Pattern;
import org.intellij.lang.annotations.PrintFormat;
import org.intellij.lang.annotations.RegExp;
import org.intellij.lang.annotations.Subst;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Async;
import org.jetbrains.annotations.Blocking;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.Debug;
import org.jetbrains.annotations.MustBeInvokedByOverriders;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonBlocking;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.PropertyKey;
import org.jetbrains.annotations.TestOnly;
import org.jetbrains.annotations.VisibleForTesting;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class Annotations_java5Test {
    @Test
    void holderTypesAndEnumConstantsAreUsable() {
        assertThat(ApiStatus.class).isNotNull();
        assertThat(Async.class).isNotNull();
        assertThat(Debug.class).isNotNull();
        assertThat(NotNull.class).isNotNull();
        assertThat(Nullable.class).isNotNull();
        assertThat(Contract.class).isNotNull();
        assertThat(Language.class).isNotNull();
        assertThat(MagicConstant.class).isNotNull();
        assertThat(PrintFormat.class).isNotNull();
        assertThat(Identifier.class).isNotNull();

        assertThat(Nls.Capitalization.values())
                .containsExactly(
                        Nls.Capitalization.NotSpecified,
                        Nls.Capitalization.Title,
                        Nls.Capitalization.Sentence);
        assertThat(Nls.Capitalization.valueOf("Title")).isSameAs(Nls.Capitalization.Title);
        assertThat(Nls.Capitalization.valueOf("Sentence")).isSameAs(Nls.Capitalization.Sentence);

        assertThat(Flow.DEFAULT_SOURCE).contains("method argument");
        assertThat(Flow.THIS_SOURCE).isEqualTo("this");
        assertThat(Flow.DEFAULT_TARGET).contains("return value");
        assertThat(Flow.RETURN_METHOD_TARGET).contains("return value");
        assertThat(Flow.THIS_TARGET).isEqualTo("this");
    }

    @Test
    void manualAnnotationImplementationsExposeConfiguredMembers() {
        NotNull notNull = new NotNull() {
            @Override
            public String value() {
                return "result";
            }

            @Override
            public Class<? extends Exception> exception() {
                return IllegalStateException.class;
            }

            @Override
            public Class<? extends Annotation> annotationType() {
                return NotNull.class;
            }
        };
        Nullable nullable = new Nullable() {
            @Override
            public String value() {
                return "optional parameter";
            }

            @Override
            public Class<? extends Annotation> annotationType() {
                return Nullable.class;
            }
        };
        Contract contract = new Contract() {
            @Override
            public String value() {
                return "null -> false";
            }

            @Override
            public boolean pure() {
                return true;
            }

            @Override
            public String mutates() {
                return "this";
            }

            @Override
            public Class<? extends Annotation> annotationType() {
                return Contract.class;
            }
        };
        Nls nls = new Nls() {
            @Override
            public Nls.Capitalization capitalization() {
                return Nls.Capitalization.Sentence;
            }

            @Override
            public Class<? extends Annotation> annotationType() {
                return Nls.class;
            }
        };
        ApiStatus.AvailableSince availableSince = new ApiStatus.AvailableSince() {
            @Override
            public String value() {
                return "23.0.0";
            }

            @Override
            public Class<? extends Annotation> annotationType() {
                return ApiStatus.AvailableSince.class;
            }
        };
        ApiStatus.ScheduledForRemoval scheduledForRemoval = new ApiStatus.ScheduledForRemoval() {
            @Override
            public String inVersion() {
                return "99.0";
            }

            @Override
            public Class<? extends Annotation> annotationType() {
                return ApiStatus.ScheduledForRemoval.class;
            }
        };
        Debug.Renderer renderer = new Debug.Renderer() {
            @Override
            public String text() {
                return "\"renderer\"";
            }

            @Override
            public String childrenArray() {
                return "new Object[] {\"child\"}";
            }

            @Override
            public String hasChildren() {
                return "true";
            }

            @Override
            public Class<? extends Annotation> annotationType() {
                return Debug.Renderer.class;
            }
        };
        Language language = new Language() {
            @Override
            public String value() {
                return "JAVA";
            }

            @Override
            public String prefix() {
                return "class Sample { Object run() { return ";
            }

            @Override
            public String suffix() {
                return "; } }";
            }

            @Override
            public Class<? extends Annotation> annotationType() {
                return Language.class;
            }
        };
        MagicConstant magicConstant = new MagicConstant() {
            @Override
            public long[] intValues() {
                return new long[] {StyleConstants.PLAIN, StyleConstants.BOLD, StyleConstants.ITALIC};
            }

            @Override
            public String[] stringValues() {
                return new String[] {"plain", "bold", "italic"};
            }

            @Override
            public long[] flags() {
                return new long[] {StyleFlags.BOLD, StyleFlags.ITALIC};
            }

            @Override
            public Class<?> valuesFromClass() {
                return StyleConstants.class;
            }

            @Override
            public Class<?> flagsFromClass() {
                return StyleFlags.class;
            }

            @Override
            public Class<? extends Annotation> annotationType() {
                return MagicConstant.class;
            }
        };
        Flow flow = new Flow() {
            @Override
            public String source() {
                return Flow.THIS_SOURCE;
            }

            @Override
            public boolean sourceIsContainer() {
                return true;
            }

            @Override
            public String target() {
                return Flow.RETURN_METHOD_TARGET;
            }

            @Override
            public boolean targetIsContainer() {
                return false;
            }

            @Override
            public Class<? extends Annotation> annotationType() {
                return Flow.class;
            }
        };
        Pattern pattern = new Pattern() {
            @Override
            public String value() {
                return "[A-Z][A-Za-z]+";
            }

            @Override
            public Class<? extends Annotation> annotationType() {
                return Pattern.class;
            }
        };
        RegExp regExp = new RegExp() {
            @Override
            public String prefix() {
                return "^";
            }

            @Override
            public String suffix() {
                return "$";
            }

            @Override
            public Class<? extends Annotation> annotationType() {
                return RegExp.class;
            }
        };
        Subst subst = new Subst() {
            @Override
            public String value() {
                return "fallback";
            }

            @Override
            public Class<? extends Annotation> annotationType() {
                return Subst.class;
            }
        };
        PropertyKey propertyKey = new PropertyKey() {
            @Override
            public String resourceBundle() {
                return "messages";
            }

            @Override
            public Class<? extends Annotation> annotationType() {
                return PropertyKey.class;
            }
        };

        assertThat(notNull.value()).isEqualTo("result");
        assertThat(notNull.exception()).isSameAs(IllegalStateException.class);
        assertThat(notNull.annotationType()).isSameAs(NotNull.class);

        assertThat(nullable.value()).isEqualTo("optional parameter");
        assertThat(nullable.annotationType()).isSameAs(Nullable.class);

        assertThat(contract.value()).isEqualTo("null -> false");
        assertThat(contract.pure()).isTrue();
        assertThat(contract.mutates()).isEqualTo("this");
        assertThat(contract.annotationType()).isSameAs(Contract.class);

        assertThat(nls.capitalization()).isSameAs(Nls.Capitalization.Sentence);
        assertThat(nls.annotationType()).isSameAs(Nls.class);

        assertThat(availableSince.value()).isEqualTo("23.0.0");
        assertThat(availableSince.annotationType()).isSameAs(ApiStatus.AvailableSince.class);

        assertThat(scheduledForRemoval.inVersion()).isEqualTo("99.0");
        assertThat(scheduledForRemoval.annotationType()).isSameAs(ApiStatus.ScheduledForRemoval.class);

        assertThat(renderer.text()).isEqualTo("\"renderer\"");
        assertThat(renderer.childrenArray()).isEqualTo("new Object[] {\"child\"}");
        assertThat(renderer.hasChildren()).isEqualTo("true");
        assertThat(renderer.annotationType()).isSameAs(Debug.Renderer.class);

        assertThat(language.value()).isEqualTo("JAVA");
        assertThat(language.prefix()).contains("class Sample");
        assertThat(language.suffix()).contains("; } }");
        assertThat(language.annotationType()).isSameAs(Language.class);

        assertThat(magicConstant.intValues())
                .containsExactly(StyleConstants.PLAIN, StyleConstants.BOLD, StyleConstants.ITALIC);
        assertThat(magicConstant.stringValues()).containsExactly("plain", "bold", "italic");
        assertThat(magicConstant.flags()).containsExactly(StyleFlags.BOLD, StyleFlags.ITALIC);
        assertThat(magicConstant.valuesFromClass()).isSameAs(StyleConstants.class);
        assertThat(magicConstant.flagsFromClass()).isSameAs(StyleFlags.class);
        assertThat(magicConstant.annotationType()).isSameAs(MagicConstant.class);

        assertThat(flow.source()).isEqualTo(Flow.THIS_SOURCE);
        assertThat(flow.sourceIsContainer()).isTrue();
        assertThat(flow.target()).isEqualTo(Flow.RETURN_METHOD_TARGET);
        assertThat(flow.targetIsContainer()).isFalse();
        assertThat(flow.annotationType()).isSameAs(Flow.class);

        assertThat(pattern.value()).isEqualTo("[A-Z][A-Za-z]+");
        assertThat(pattern.annotationType()).isSameAs(Pattern.class);

        assertThat(regExp.prefix()).isEqualTo("^");
        assertThat(regExp.suffix()).isEqualTo("$");
        assertThat(regExp.annotationType()).isSameAs(RegExp.class);

        assertThat(subst.value()).isEqualTo("fallback");
        assertThat(subst.annotationType()).isSameAs(Subst.class);

        assertThat(propertyKey.resourceBundle()).isEqualTo("messages");
        assertThat(propertyKey.annotationType()).isSameAs(PropertyKey.class);
    }

    @Test
    void annotatedCodePathsExecuteNormally() {
        AnnotatedFormatter formatter = new AnnotatedFormatter("buildPlan", "messages.title");
        UpperCaseNormalizer normalizer = new UpperCaseNormalizer();

        assertThat(formatter.format("Task %s", null)).isEqualTo("Task buildPlan fallback");
        assertThat(formatter.format("Task %s", "ready")).isEqualTo("Task buildPlan ready");
        assertThat(formatter.matches("buildPlan42", "[a-z]+[A-Z]?[a-z]*\\d+")).isTrue();
        assertThat(formatter.matches(null, ".+")).isFalse();
        assertThat(formatter.appendSuffix("-done")).isEqualTo("buildPlan-done");
        assertThat(formatter.defaultFontStyle()).isEqualTo(StyleConstants.BOLD);
        assertThat(formatter.mergeStyles(StyleConstants.BOLD, StyleConstants.ITALIC))
                .isEqualTo(StyleConstants.BOLD | StyleConstants.ITALIC);
        assertThat(formatter.cursorType(CursorConstants.HAND)).isEqualTo(CursorConstants.HAND);
        assertThat(formatter.javaSnippet("buildPlan")).isEqualTo("buildPlan");
        assertThat(formatter.key()).isEqualTo("messages.title");
        assertThat(formatter.legacyKey()).isEqualTo("MESSAGES.TITLE");
        assertThat(normalizer.normalize("  annotated text  ")).isEqualTo("ANNOTATED TEXT");
    }

    @Language("JAVA")
    private @interface JavaExpression {
    }

    @Pattern("[A-Z][A-Za-z]+")
    private @interface TitleToken {
    }

    @MagicConstant(stringValues = {"plain", "bold", "italic"})
    private @interface StyleName {
    }

    private static final class StyleConstants {
        private static final int PLAIN = 0;
        private static final int BOLD = 1;
        private static final int ITALIC = 2;

        private StyleConstants() {
        }
    }

    private static final class StyleFlags {
        private static final int BOLD = 1;
        private static final int ITALIC = 2;

        private StyleFlags() {
        }
    }

    private static final class CursorConstants {
        private static final int HAND = 12;

        private CursorConstants() {
        }
    }

    @NonBlocking
    @VisibleForTesting
    @ApiStatus.Experimental
    @Debug.Renderer(
            text = "\"Formatter(\" + identifier + \")\"",
            childrenArray = "new Object[] { bundleKey }",
            hasChildren = "true")
    private static final class AnnotatedFormatter {
        @NonNls
        @Identifier
        private final String identifier;

        @PropertyKey(resourceBundle = "messages")
        private final String bundleKey;

        @Async.Schedule
        @TestOnly
        private AnnotatedFormatter(
                @Identifier String identifier,
                @PropertyKey(resourceBundle = "messages") String bundleKey) {
            this.identifier = identifier;
            this.bundleKey = bundleKey;
        }

        @NotNull("formatted message")
        @Nls(capitalization = Nls.Capitalization.Sentence)
        @Async.Execute
        private String format(@PrintFormat String template, @Subst("fallback") @Nullable("suffix") String suffix) {
            @Subst("fallback") String resolvedSuffix = suffix == null ? "fallback" : suffix;
            return String.format(Locale.ROOT, template + " %s", identifier, resolvedSuffix);
        }

        @Contract(pure = true, value = "null -> false")
        private boolean matches(@Nullable("candidate") String candidate, @RegExp(prefix = "^", suffix = "$") String expression) {
            return candidate != null && java.util.regex.Pattern.compile(expression).matcher(candidate).matches();
        }

        @Blocking
        @Flow(source = Flow.THIS_SOURCE, target = Flow.RETURN_METHOD_TARGET)
        private String appendSuffix(@NotNull("suffix") String suffix) {
            @NonNls String separator = "";
            return identifier + separator + suffix;
        }

        @MagicConstant(intValues = {StyleConstants.PLAIN, StyleConstants.BOLD, StyleConstants.ITALIC})
        private int defaultFontStyle() {
            return StyleConstants.BOLD;
        }

        @MagicConstant(flags = {StyleFlags.BOLD, StyleFlags.ITALIC})
        private int mergeStyles(int primary, int secondary) {
            return primary | secondary;
        }

        private int cursorType(int cursorType) {
            return cursorType;
        }

        @JavaExpression
        @Language(value = "JAVA", prefix = "class Snippet { Object run() { return ", suffix = "; } }")
        private String javaSnippet(@TitleToken @StyleName @Identifier String variableName) {
            return variableName;
        }

        @VisibleForTesting
        @ApiStatus.AvailableSince("23.0.0")
        private String key() {
            return bundleKey;
        }

        @ApiStatus.ScheduledForRemoval(inVersion = "99.0")
        private String legacyKey() {
            return bundleKey.toUpperCase(Locale.ROOT);
        }
    }

    @ApiStatus.Internal
    private static class BaseNormalizer {
        @MustBeInvokedByOverriders
        @ApiStatus.OverrideOnly
        @NotNull("trimmed value")
        protected String normalize(@NotNull("input") String value) {
            return value.trim();
        }
    }

    @ApiStatus.NonExtendable
    private static final class UpperCaseNormalizer extends BaseNormalizer {
        @Override
        protected String normalize(String value) {
            return super.normalize(value).toUpperCase(Locale.ROOT);
        }
    }
}
