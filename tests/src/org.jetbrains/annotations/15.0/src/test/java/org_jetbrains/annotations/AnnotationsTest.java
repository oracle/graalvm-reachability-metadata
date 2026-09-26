/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jetbrains.annotations;

import java.awt.Adjustable;
import java.awt.Cursor;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.event.InputEvent;
import java.lang.annotation.Annotation;
import java.util.Calendar;
import java.util.Locale;
import java.util.regex.PatternSyntaxException;

import javax.swing.BoxLayout;
import javax.swing.JTabbedPane;
import javax.swing.ListSelectionModel;
import javax.swing.SwingConstants;
import javax.swing.border.TitledBorder;
import javax.swing.tree.TreeSelectionModel;

import org.intellij.lang.annotations.Flow;
import org.intellij.lang.annotations.Identifier;
import org.intellij.lang.annotations.JdkConstants;
import org.intellij.lang.annotations.Language;
import org.intellij.lang.annotations.MagicConstant;
import org.intellij.lang.annotations.Pattern;
import org.intellij.lang.annotations.PrintFormat;
import org.intellij.lang.annotations.RegExp;
import org.intellij.lang.annotations.Subst;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.PropertyKey;
import org.jetbrains.annotations.TestOnly;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AnnotationsTest {
    @Test
    void publicTypesAndConstantsAreUsable() {
        assertThat(NotNull.class).isNotNull();
        assertThat(Nullable.class).isNotNull();
        assertThat(Contract.class).isNotNull();
        assertThat(Nls.class).isNotNull();
        assertThat(NonNls.class).isNotNull();
        assertThat(PropertyKey.class).isNotNull();
        assertThat(TestOnly.class).isNotNull();

        assertThat(Flow.class).isNotNull();
        assertThat(Identifier.class).isNotNull();
        assertThat(JdkConstants.class).isNotNull();
        assertThat(JdkConstants.AdjustableOrientation.class).isNotNull();
        assertThat(JdkConstants.BoxLayoutAxis.class).isNotNull();
        assertThat(JdkConstants.CalendarMonth.class).isNotNull();
        assertThat(JdkConstants.CursorType.class).isNotNull();
        assertThat(JdkConstants.FlowLayoutAlignment.class).isNotNull();
        assertThat(JdkConstants.FontStyle.class).isNotNull();
        assertThat(JdkConstants.HorizontalAlignment.class).isNotNull();
        assertThat(JdkConstants.InputEventMask.class).isNotNull();
        assertThat(JdkConstants.ListSelectionMode.class).isNotNull();
        assertThat(JdkConstants.PatternFlags.class).isNotNull();
        assertThat(JdkConstants.TabLayoutPolicy.class).isNotNull();
        assertThat(JdkConstants.TabPlacement.class).isNotNull();
        assertThat(JdkConstants.TitledBorderJustification.class).isNotNull();
        assertThat(JdkConstants.TitledBorderTitlePosition.class).isNotNull();
        assertThat(JdkConstants.TreeSelectionMode.class).isNotNull();
        assertThat(Language.class).isNotNull();
        assertThat(MagicConstant.class).isNotNull();
        assertThat(Pattern.class).isNotNull();
        assertThat(PrintFormat.class).isNotNull();
        assertThat(RegExp.class).isNotNull();
        assertThat(Subst.class).isNotNull();

        assertThat(new JdkConstants()).isNotNull();

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
            public Class<? extends Annotation> annotationType() {
                return NotNull.class;
            }
        };
        Nullable nullable = new Nullable() {
            @Override
            public String value() {
                return "optional value";
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
            public Class<? extends Annotation> annotationType() {
                return Contract.class;
            }
        };
        Nls nls = new Nls() {
            @Override
            public Class<? extends Annotation> annotationType() {
                return Nls.class;
            }
        };
        NonNls nonNls = new NonNls() {
            @Override
            public Class<? extends Annotation> annotationType() {
                return NonNls.class;
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
        TestOnly testOnly = new TestOnly() {
            @Override
            public Class<? extends Annotation> annotationType() {
                return TestOnly.class;
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
        Identifier identifier = new Identifier() {
            @Override
            public Class<? extends Annotation> annotationType() {
                return Identifier.class;
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
        PrintFormat printFormat = new PrintFormat() {
            @Override
            public Class<? extends Annotation> annotationType() {
                return PrintFormat.class;
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

        assertThat(notNull.value()).isEqualTo("result");
        assertThat(notNull.annotationType()).isSameAs(NotNull.class);

        assertThat(nullable.value()).isEqualTo("optional value");
        assertThat(nullable.annotationType()).isSameAs(Nullable.class);

        assertThat(contract.value()).isEqualTo("null -> false");
        assertThat(contract.pure()).isTrue();
        assertThat(contract.annotationType()).isSameAs(Contract.class);

        assertThat(nls.annotationType()).isSameAs(Nls.class);
        assertThat(nonNls.annotationType()).isSameAs(NonNls.class);

        assertThat(propertyKey.resourceBundle()).isEqualTo("messages");
        assertThat(propertyKey.annotationType()).isSameAs(PropertyKey.class);

        assertThat(testOnly.annotationType()).isSameAs(TestOnly.class);

        assertThat(flow.source()).isEqualTo(Flow.THIS_SOURCE);
        assertThat(flow.sourceIsContainer()).isTrue();
        assertThat(flow.target()).isEqualTo(Flow.RETURN_METHOD_TARGET);
        assertThat(flow.targetIsContainer()).isFalse();
        assertThat(flow.annotationType()).isSameAs(Flow.class);

        assertThat(identifier.annotationType()).isSameAs(Identifier.class);

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

        assertThat(pattern.value()).isEqualTo("[A-Z][A-Za-z]+");
        assertThat(pattern.annotationType()).isSameAs(Pattern.class);

        assertThat(printFormat.annotationType()).isSameAs(PrintFormat.class);

        assertThat(regExp.prefix()).isEqualTo("^");
        assertThat(regExp.suffix()).isEqualTo("$");
        assertThat(regExp.annotationType()).isSameAs(RegExp.class);

        assertThat(subst.value()).isEqualTo("fallback");
        assertThat(subst.annotationType()).isSameAs(Subst.class);
    }

    @Test
    void annotatedCodePathsExecuteNormally() {
        AnnotatedFormatter formatter = new AnnotatedFormatter("buildPlan", "messages.title");

        assertThat(formatter.format("Task %s", null)).isEqualTo("Task buildPlan fallback");
        assertThat(formatter.format("Task %s", "ready")).isEqualTo("Task buildPlan ready");
        assertThat(formatter.matches("buildPlan42", "[a-z]+[A-Z]?[a-z]*\\d+")).isTrue();
        assertThat(formatter.matches(null, ".+")).isFalse();
        assertThat(formatter.appendSuffix("-done")).isEqualTo("buildPlan-done");
        assertThat(formatter.defaultFontStyle()).isEqualTo(StyleConstants.BOLD);
        assertThat(formatter.mergeStyles(StyleConstants.BOLD, StyleConstants.ITALIC))
                .isEqualTo(StyleConstants.BOLD | StyleConstants.ITALIC);
        assertThat(formatter.javaSnippet("buildPlan")).isEqualTo("buildPlan");
        assertThat(formatter.key()).isEqualTo("messages.title");
    }

    @Test
    void jdkConstantsAnnotatedCodePathsExecuteNormally() {
        JdkConstantExamples examples = new JdkConstantExamples();
        int shortcutMask = examples.shortcutMask();
        int shortcutWithAlt = shortcutMask | InputEvent.ALT_DOWN_MASK;

        assertThat(examples.normalizedOrientation(Adjustable.HORIZONTAL)).isEqualTo(Adjustable.VERTICAL);
        assertThat(examples.preferredAxis(BoxLayout.Y_AXIS)).isEqualTo(BoxLayout.X_AXIS);
        assertThat(examples.nextMonth(Calendar.NOVEMBER)).isEqualTo(Calendar.DECEMBER);
        assertThat(examples.nextMonth(Calendar.DECEMBER)).isEqualTo(Calendar.JANUARY);
        assertThat(examples.pointerCursor()).isEqualTo(Cursor.HAND_CURSOR);
        assertThat(examples.centerAlignment()).isEqualTo(FlowLayout.CENTER);
        assertThat(examples.boldItalicStyle()).isEqualTo(Font.BOLD | Font.ITALIC);
        assertThat(examples.trailingAlignment()).isEqualTo(SwingConstants.TRAILING);
        assertThat(shortcutMask).isEqualTo(InputEvent.CTRL_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK);
        assertThat(examples.hasRequiredModifiers(shortcutWithAlt, shortcutMask)).isTrue();
        assertThat(examples.hasRequiredModifiers(InputEvent.CTRL_DOWN_MASK, shortcutMask)).isFalse();
        assertThat(examples.clearAltModifier(shortcutWithAlt)).isEqualTo(shortcutMask);
        assertThat(examples.singleSelection()).isEqualTo(ListSelectionModel.SINGLE_SELECTION);
        assertThat(examples.multilineCaseInsensitiveFlags())
                .isEqualTo(java.util.regex.Pattern.CASE_INSENSITIVE | java.util.regex.Pattern.MULTILINE);
        assertThat(examples.containsRegexMatch("BUILD\nplan", "^build\\s+plan$", examples.multilineCaseInsensitiveFlags()))
                .isTrue();
        assertThat(examples.containsRegexMatch("BUILD PLAN", "^build\\s+plan$", 0)).isFalse();
        assertThat(examples.scrollTabLayout()).isEqualTo(JTabbedPane.SCROLL_TAB_LAYOUT);
        assertThat(examples.bottomPlacement()).isEqualTo(JTabbedPane.BOTTOM);
        assertThat(examples.centerJustification()).isEqualTo(TitledBorder.CENTER);
        assertThat(examples.belowBottomPosition()).isEqualTo(TitledBorder.BELOW_BOTTOM);
        assertThat(examples.discontiguousSelection()).isEqualTo(TreeSelectionModel.DISCONTIGUOUS_TREE_SELECTION);
        assertThat(examples.invalidExpressionMessage("[")).contains("Unclosed character class");
    }

    @Test
    void magicConstantClassBackedValuesAndFlagsDriveOperations() {
        MagicConstantCatalog catalog = new MagicConstantCatalog();

        int titledMode = catalog.preferredMode(true, false);
        int loudMode = catalog.preferredMode(false, true);
        int decoratedOptions = catalog.options(true, true);

        assertThat(titledMode).isEqualTo(RenderModes.TITLED);
        assertThat(loudMode).isEqualTo(RenderModes.LOUD);
        assertThat(decoratedOptions).isEqualTo(RenderOptions.TRIM | RenderOptions.BRACKETS);
        assertThat(catalog.render("  build plan  ", titledMode, decoratedOptions)).isEqualTo("[Build Plan]");
        assertThat(catalog.render("native image", loudMode, catalog.options(false, false))).isEqualTo("NATIVE IMAGE");
    }

    @Test
    void magicConstantStringValuesDriveFormattingChoices() {
        StringStyleCatalog catalog = new StringStyleCatalog();

        String boldStyle = catalog.canonicalStyle("  strong emphasis  ");
        String italicStyle = catalog.canonicalStyle("light emphasis");
        String plainStyle = catalog.canonicalStyle("plain text");

        assertThat(boldStyle).isEqualTo("bold");
        assertThat(italicStyle).isEqualTo("italic");
        assertThat(plainStyle).isEqualTo("plain");
        assertThat(catalog.render("build plan", boldStyle)).isEqualTo("**build plan**");
        assertThat(catalog.render("native image", italicStyle)).isEqualTo("*native image*");
        assertThat(catalog.render("metadata", plainStyle)).isEqualTo("metadata");
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

    private static final class AnnotatedFormatter {
        @NonNls
        @Identifier
        private final String identifier;

        @PropertyKey(resourceBundle = "messages")
        private final String bundleKey;

        @TestOnly
        private AnnotatedFormatter(
                @Identifier String identifier,
                @PropertyKey(resourceBundle = "messages") String bundleKey) {
            this.identifier = identifier;
            this.bundleKey = bundleKey;
        }

        @NotNull("formatted message")
        @Nls
        private String format(@PrintFormat String template, @Subst("fallback") @Nullable("suffix") String suffix) {
            @Subst("fallback") String resolvedSuffix = suffix == null ? "fallback" : suffix;
            return String.format(Locale.ROOT, template + " %s", identifier, resolvedSuffix);
        }

        @Contract(pure = true, value = "null -> false")
        private boolean matches(@Nullable("candidate") String candidate, @RegExp(prefix = "^", suffix = "$") String expression) {
            return candidate != null && java.util.regex.Pattern.compile(expression).matcher(candidate).matches();
        }

        @Flow(source = Flow.THIS_SOURCE, target = Flow.RETURN_METHOD_TARGET)
        private String appendSuffix(@NotNull("suffix") String suffix) {
            return identifier + suffix;
        }

        @MagicConstant(intValues = {StyleConstants.PLAIN, StyleConstants.BOLD, StyleConstants.ITALIC})
        private int defaultFontStyle() {
            return StyleConstants.BOLD;
        }

        @MagicConstant(flags = {StyleFlags.BOLD, StyleFlags.ITALIC})
        private int mergeStyles(int primary, int secondary) {
            return primary | secondary;
        }

        @JavaExpression
        @Language(value = "JAVA", prefix = "class Snippet { Object run() { return ", suffix = "; } }")
        private String javaSnippet(@TitleToken @StyleName @Identifier String variableName) {
            return variableName;
        }

        private String key() {
            return bundleKey;
        }
    }

    private static final class JdkConstantExamples {
        private @JdkConstants.AdjustableOrientation int normalizedOrientation(
                @JdkConstants.AdjustableOrientation int orientation) {
            return orientation == Adjustable.HORIZONTAL ? Adjustable.VERTICAL : Adjustable.HORIZONTAL;
        }

        private @JdkConstants.BoxLayoutAxis int preferredAxis(@JdkConstants.BoxLayoutAxis int axis) {
            return axis == BoxLayout.X_AXIS ? BoxLayout.Y_AXIS : BoxLayout.X_AXIS;
        }

        private @JdkConstants.CalendarMonth int nextMonth(@JdkConstants.CalendarMonth int month) {
            return month == Calendar.DECEMBER ? Calendar.JANUARY : month + 1;
        }

        private @JdkConstants.CursorType int pointerCursor() {
            return Cursor.HAND_CURSOR;
        }

        private @JdkConstants.FlowLayoutAlignment int centerAlignment() {
            return FlowLayout.CENTER;
        }

        private @JdkConstants.FontStyle int boldItalicStyle() {
            return Font.BOLD | Font.ITALIC;
        }

        private @JdkConstants.HorizontalAlignment int trailingAlignment() {
            return SwingConstants.TRAILING;
        }

        private @JdkConstants.InputEventMask int shortcutMask() {
            return InputEvent.CTRL_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK;
        }

        private boolean hasRequiredModifiers(
                @JdkConstants.InputEventMask int eventMask,
                @JdkConstants.InputEventMask int requiredMask) {
            return (eventMask & requiredMask) == requiredMask;
        }

        private @JdkConstants.InputEventMask int clearAltModifier(@JdkConstants.InputEventMask int eventMask) {
            return eventMask & ~InputEvent.ALT_DOWN_MASK;
        }

        private @JdkConstants.ListSelectionMode int singleSelection() {
            return ListSelectionModel.SINGLE_SELECTION;
        }

        private @JdkConstants.PatternFlags int multilineCaseInsensitiveFlags() {
            return java.util.regex.Pattern.CASE_INSENSITIVE | java.util.regex.Pattern.MULTILINE;
        }

        private boolean containsRegexMatch(String text, String expression, @JdkConstants.PatternFlags int flags) {
            return java.util.regex.Pattern.compile(expression, flags).matcher(text).find();
        }

        private String invalidExpressionMessage(String expression) {
            try {
                java.util.regex.Pattern.compile(expression, multilineCaseInsensitiveFlags());
                return "compiled";
            } catch (PatternSyntaxException exception) {
                return exception.getDescription();
            }
        }

        private @JdkConstants.TabLayoutPolicy int scrollTabLayout() {
            return JTabbedPane.SCROLL_TAB_LAYOUT;
        }

        private @JdkConstants.TabPlacement int bottomPlacement() {
            return JTabbedPane.BOTTOM;
        }

        private @JdkConstants.TitledBorderJustification int centerJustification() {
            return TitledBorder.CENTER;
        }

        private @JdkConstants.TitledBorderTitlePosition int belowBottomPosition() {
            return TitledBorder.BELOW_BOTTOM;
        }

        private @JdkConstants.TreeSelectionMode int discontiguousSelection() {
            return TreeSelectionModel.DISCONTIGUOUS_TREE_SELECTION;
        }
    }

    private static final class StringStyleCatalog {
        private @MagicConstant(stringValues = {"plain", "bold", "italic"}) String canonicalStyle(String description) {
            String normalizedDescription = description.trim().toLowerCase(Locale.ROOT);
            if (normalizedDescription.contains("strong")) {
                return "bold";
            }
            if (normalizedDescription.contains("emphasis")) {
                return "italic";
            }
            return "plain";
        }

        private String render(
                String text,
                @MagicConstant(stringValues = {"plain", "bold", "italic"}) String style) {
            if ("bold".equals(style)) {
                return "**" + text + "**";
            }
            if ("italic".equals(style)) {
                return "*" + text + "*";
            }
            return text;
        }
    }

    private static final class RenderModes {
        private static final int PLAIN = 0;
        private static final int TITLED = 1;
        private static final int LOUD = 2;

        private RenderModes() {
        }
    }

    private static final class RenderOptions {
        private static final int TRIM = 1;
        private static final int BRACKETS = 2;

        private RenderOptions() {
        }
    }

    private static final class MagicConstantCatalog {
        private @MagicConstant(valuesFromClass = RenderModes.class) int preferredMode(boolean titleCase, boolean loud) {
            if (loud) {
                return RenderModes.LOUD;
            }
            return titleCase ? RenderModes.TITLED : RenderModes.PLAIN;
        }

        private @MagicConstant(flagsFromClass = RenderOptions.class) int options(boolean trim, boolean brackets) {
            int value = 0;
            if (trim) {
                value |= RenderOptions.TRIM;
            }
            if (brackets) {
                value |= RenderOptions.BRACKETS;
            }
            return value;
        }

        private String render(
                String text,
                @MagicConstant(valuesFromClass = RenderModes.class) int mode,
                @MagicConstant(flagsFromClass = RenderOptions.class) int options) {
            String rendered = (options & RenderOptions.TRIM) != 0 ? text.trim() : text;
            if (mode == RenderModes.TITLED) {
                rendered = titleCase(rendered);
            } else if (mode == RenderModes.LOUD) {
                rendered = rendered.toUpperCase(Locale.ROOT);
            }
            if ((options & RenderOptions.BRACKETS) != 0) {
                rendered = '[' + rendered + ']';
            }
            return rendered;
        }

        private String titleCase(String text) {
            String[] words = text.split("\\s+");
            StringBuilder builder = new StringBuilder();
            for (int index = 0; index < words.length; index++) {
                String word = words[index];
                if (builder.length() > 0) {
                    builder.append(' ');
                }
                builder.append(Character.toUpperCase(word.charAt(0)));
                if (word.length() > 1) {
                    builder.append(word.substring(1).toLowerCase(Locale.ROOT));
                }
            }
            return builder.toString();
        }
    }
}
