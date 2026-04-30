/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_intellij.annotations;

import org.intellij.lang.annotations.Identifier;
import org.intellij.lang.annotations.JdkConstants;
import org.intellij.lang.annotations.Language;
import org.intellij.lang.annotations.MagicConstant;
import org.intellij.lang.annotations.Pattern;
import org.intellij.lang.annotations.PrintFormat;
import org.intellij.lang.annotations.RegExp;
import org.intellij.lang.annotations.Subst;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.PropertyKey;
import org.jetbrains.annotations.TestOnly;
import org.junit.jupiter.api.Test;

import java.awt.Adjustable;
import java.awt.Cursor;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.event.InputEvent;
import java.lang.annotation.Annotation;
import java.util.Locale;
import java.util.Objects;
import java.util.StringJoiner;
import javax.swing.BoxLayout;
import javax.swing.JTabbedPane;
import javax.swing.ListSelectionModel;
import javax.swing.SwingConstants;
import javax.swing.border.TitledBorder;
import javax.swing.tree.TreeSelectionModel;

import static java.util.Calendar.APRIL;
import static java.util.regex.Pattern.CASE_INSENSITIVE;
import static java.util.regex.Pattern.MULTILINE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@NonNls
public class AnnotationsTest {
    @NotNull("service name is required")
    private static final String SERVICE_NAME = "annotations";

    @Nullable("cache is populated lazily")
    private String cachedValue;

    @Nls
    private static final String USER_VISIBLE_TITLE = "Open Project";

    @NonNls
    private static final String INTERNAL_CONFIGURATION_KEY = "test.service.name";

    @PropertyKey(resourceBundle = "com.intellij.annotations.Messages")
    private static final String WELCOME_MESSAGE_KEY = "welcome.message";

    @Language(value = "SQL", prefix = "SELECT ", suffix = " FROM metadata")
    private static final String SQL_PROJECTION = "id, display_name";

    @Pattern("[a-z]+-[0-9]+")
    private static final String ISSUE_ID_PATTERN = "task-42";

    @RegExp(prefix = "^", suffix = "$")
    private static final String IDENTIFIER_BODY_PATTERN = "[A-Za-z_][A-Za-z0-9_]*";

    @Subst("sampleIdentifier")
    private static final String GENERATED_IDENTIFIER = "serviceName";

    @PrintFormat
    private static final String SUMMARY_FORMAT = "%2$s has %,d files";

    @Identifier
    private static final String VALID_JAVA_IDENTIFIER = "metadataSupport";

    @MagicConstant(stringValues = {"debug", "release"})
    private static final String BUILD_MODE = "release";

    @Test
    public void nullabilityAnnotationsDocumentExecutableContracts() {
        AnnotatedService service = new AnnotatedService(SERVICE_NAME);

        assertThat(service.currentName()).isEqualTo("annotations");
        assertThat(service.optionalDescription(false)).isNull();
        assertThat(service.optionalDescription(true)).isEqualTo("annotations library");
        assertThatThrownBy(() -> service.rename(null)).isInstanceOf(NullPointerException.class);

        cachedValue = service.optionalDescription(true);
        assertThat(cachedValue).isEqualTo("annotations library");
    }

    @Test
    public void languagePatternRegExpIdentifierAndSubstitutionAnnotationsDescribeValidStrings() {
        String query = selectFromMetadata(SQL_PROJECTION);
        String identifier = normalizeIdentifier(GENERATED_IDENTIFIER);

        assertThat(query).isEqualTo("SELECT id, display_name FROM metadata");
        assertThat(acceptIssueId(ISSUE_ID_PATTERN)).isEqualTo("task-42");
        assertThat(matchesIdentifier(VALID_JAVA_IDENTIFIER)).isTrue();
        assertThat(matchesIdentifier("42-invalid")).isFalse();
        assertThat(identifier).isEqualTo("serviceName");
    }

    @Test
    public void printFormatNlsNonNlsAndPropertyKeyAnnotationsIntegrateWithJdkCode() {
        String formatted = formatSummary(SUMMARY_FORMAT, 12_500, "index");
        String label = labelFor(WELCOME_MESSAGE_KEY, USER_VISIBLE_TITLE);
        String configLine = configLine(INTERNAL_CONFIGURATION_KEY, SERVICE_NAME);

        assertThat(formatted).isEqualTo("index has 12,500 files");
        assertThat(label).isEqualTo("Open Project: welcome.message");
        assertThat(configLine).isEqualTo("test.service.name=annotations");
    }

    @Test
    public void manualAnnotationImplementationsExposePublicAnnotationMembers() {
        Language language = new Language() {
            @Override
            public String value() {
                return "JSON";
            }

            @Override
            public String prefix() {
                return "{";
            }

            @Override
            public String suffix() {
                return "}";
            }

            @Override
            public Class<? extends Annotation> annotationType() {
                return Language.class;
            }
        };
        MagicConstant magicConstant = new MagicConstant() {
            @Override
            public long[] intValues() {
                return new long[] {1L, 2L};
            }

            @Override
            public String[] stringValues() {
                return new String[] {"debug", "release"};
            }

            @Override
            public long[] flags() {
                return new long[] {FeatureFlags.READ, FeatureFlags.WRITE};
            }

            @Override
            public Class valuesFromClass() {
                return Cursor.class;
            }

            @Override
            public Class flagsFromClass() {
                return InputEvent.class;
            }

            @Override
            public Class<? extends Annotation> annotationType() {
                return MagicConstant.class;
            }
        };
        Pattern pattern = new Pattern() {
            @Override
            public String value() {
                return "[a-z]+";
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
        NotNull notNull = new NotNull() {
            @Override
            public String value() {
                return "required";
            }

            @Override
            public Class<? extends Annotation> annotationType() {
                return NotNull.class;
            }
        };
        Nullable nullable = new Nullable() {
            @Override
            public String value() {
                return "optional";
            }

            @Override
            public Class<? extends Annotation> annotationType() {
                return Nullable.class;
            }
        };
        PropertyKey propertyKey = new PropertyKey() {
            @Override
            public String resourceBundle() {
                return "messages.Bundle";
            }

            @Override
            public Class<? extends Annotation> annotationType() {
                return PropertyKey.class;
            }
        };
        Subst substitution = new Subst() {
            @Override
            public String value() {
                return "fallback";
            }

            @Override
            public Class<? extends Annotation> annotationType() {
                return Subst.class;
            }
        };

        assertThat(language.annotationType()).isEqualTo(Language.class);
        assertThat(language.value()).isEqualTo("JSON");
        assertThat(language.prefix()).isEqualTo("{");
        assertThat(language.suffix()).isEqualTo("}");
        assertThat(magicConstant.intValues()).containsExactly(1L, 2L);
        assertThat(magicConstant.stringValues()).containsExactly("debug", "release");
        assertThat(magicConstant.flags()).containsExactly(FeatureFlags.READ, FeatureFlags.WRITE);
        assertThat(magicConstant.valuesFromClass()).isEqualTo(Cursor.class);
        assertThat(magicConstant.flagsFromClass()).isEqualTo(InputEvent.class);
        assertThat(pattern.value()).isEqualTo("[a-z]+");
        assertThat(regExp.prefix()).isEqualTo("^");
        assertThat(regExp.suffix()).isEqualTo("$");
        assertThat(notNull.value()).isEqualTo("required");
        assertThat(nullable.value()).isEqualTo("optional");
        assertThat(propertyKey.resourceBundle()).isEqualTo("messages.Bundle");
        assertThat(substitution.value()).isEqualTo("fallback");
        assertThat(markerAnnotationTypes()).containsExactly(
                Identifier.class,
                Nls.class,
                NonNls.class,
                PrintFormat.class,
                TestOnly.class);
    }

    @Test
    public void magicConstantAnnotationsRoundTripAllowedValuesAndFlags() {
        assertThat(new JdkConstants()).isNotNull();
        assertThat(selectBuildMode(BUILD_MODE)).isEqualTo("release");
        assertThat(selectSeverity(SeverityConstants.HIGH)).isEqualTo(SeverityConstants.HIGH);
        assertThat(selectFeatureFlags(FeatureFlags.READ | FeatureFlags.WRITE)).isEqualTo(3);
        assertThat(selectCursor(Cursor.HAND_CURSOR)).isEqualTo(Cursor.HAND_CURSOR);
        assertThat(selectInputMask(InputEvent.CTRL_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK))
                .isEqualTo(InputEvent.CTRL_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK);
    }

    @Test
    public void magicConstantSupportsWideLongValuesAndFlags() {
        long allowedValue = selectWideValue(WideConstants.LARGE_VALUE);
        long selectedFlags = selectWideFlags(WideConstants.READ | WideConstants.EXPORT);

        assertThat(allowedValue).isEqualTo(WideConstants.LARGE_VALUE);
        assertThat(selectedFlags).isEqualTo(WideConstants.READ | WideConstants.EXPORT);
        assertThat(hasWideFlag(selectedFlags, WideConstants.READ)).isTrue();
        assertThat(hasWideFlag(selectedFlags, WideConstants.WRITE)).isFalse();
        assertThat(hasWideFlag(selectedFlags, WideConstants.EXPORT)).isTrue();
    }

    @Test
    public void jdkConstantCursorAndInputEventMaskAliasesDescribeAwtInputValues() {
        int cursorType = selectCursorTypeAlias(Cursor.CROSSHAIR_CURSOR);
        int mouseAndKeyboardMask = selectInputEventMaskAlias(
                InputEvent.BUTTON1_DOWN_MASK | InputEvent.ALT_DOWN_MASK);

        assertThat(isCrosshairCursor(cursorType)).isTrue();
        assertThat(hasInputEventMask(mouseAndKeyboardMask, InputEvent.BUTTON1_DOWN_MASK)).isTrue();
        assertThat(hasInputEventMask(mouseAndKeyboardMask, InputEvent.ALT_DOWN_MASK)).isTrue();
        assertThat(hasInputEventMask(mouseAndKeyboardMask, InputEvent.SHIFT_DOWN_MASK)).isFalse();
    }

    @Test
    public void jdkConstantAliasAnnotationsCoverCommonSwingAwtAndRegexConstants() {
        assertThat(selectHorizontalAlignment(SwingConstants.TRAILING)).isEqualTo(SwingConstants.TRAILING);
        assertThat(selectFlowLayoutAlignment(FlowLayout.LEADING)).isEqualTo(FlowLayout.LEADING);
        assertThat(selectAdjustableOrientation(Adjustable.VERTICAL)).isEqualTo(Adjustable.VERTICAL);
        assertThat(selectCalendarMonth(APRIL)).isEqualTo(APRIL);
        assertThat(selectPatternFlags(CASE_INSENSITIVE | MULTILINE)).isEqualTo(CASE_INSENSITIVE | MULTILINE);
        assertThat(selectBoxLayoutAxis(BoxLayout.PAGE_AXIS)).isEqualTo(BoxLayout.PAGE_AXIS);
        assertThat(selectListSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION))
                .isEqualTo(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        assertThat(selectTreeSelectionMode(TreeSelectionModel.DISCONTIGUOUS_TREE_SELECTION))
                .isEqualTo(TreeSelectionModel.DISCONTIGUOUS_TREE_SELECTION);
        assertThat(selectFontStyle(Font.BOLD | Font.ITALIC)).isEqualTo(Font.BOLD | Font.ITALIC);
        assertThat(selectTitledBorderJustification(TitledBorder.LEADING)).isEqualTo(TitledBorder.LEADING);
        assertThat(selectTitledBorderTitlePosition(TitledBorder.BELOW_TOP)).isEqualTo(TitledBorder.BELOW_TOP);
        assertThat(selectTabPlacement(SwingConstants.BOTTOM)).isEqualTo(SwingConstants.BOTTOM);
        assertThat(selectTabLayoutPolicy(JTabbedPane.SCROLL_TAB_LAYOUT)).isEqualTo(JTabbedPane.SCROLL_TAB_LAYOUT);
    }

    @NotNull
    private static String selectFromMetadata(
            @Language(value = "SQL", prefix = "SELECT ", suffix = " FROM metadata") String columns) {
        @Language("SQL") String statement = "SELECT " + columns + " FROM metadata";
        return statement;
    }

    @NotNull
    private static String acceptIssueId(@Pattern("[a-z]+-[0-9]+") String issueId) {
        assertThat(issueId.matches("[a-z]+-[0-9]+")).isTrue();
        return issueId;
    }

    private static boolean matchesIdentifier(@Identifier String identifier) {
        @RegExp(prefix = "^", suffix = "$") String identifierPattern = IDENTIFIER_BODY_PATTERN;
        return identifier.matches(identifierPattern);
    }

    @Identifier
    private static String normalizeIdentifier(@Subst("fallbackIdentifier") String candidate) {
        if (candidate == null || candidate.isEmpty()) {
            return "fallbackIdentifier";
        }
        return candidate;
    }

    @NotNull
    private static String formatSummary(@PrintFormat String format, int count, @NonNls String name) {
        return String.format(Locale.US, format, count, name);
    }

    @Nls
    private static String labelFor(
            @PropertyKey(resourceBundle = "com.intellij.annotations.Messages") String messageKey,
            @Nls String title) {
        return title + ": " + messageKey;
    }

    @NonNls
    private static String configLine(@NonNls String key, @NotNull String value) {
        return key + "=" + Objects.requireNonNull(value, "value");
    }

    private static Class<?>[] markerAnnotationTypes() {
        Identifier identifier = new Identifier() {
            @Override
            public Class<? extends Annotation> annotationType() {
                return Identifier.class;
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
        PrintFormat printFormat = new PrintFormat() {
            @Override
            public Class<? extends Annotation> annotationType() {
                return PrintFormat.class;
            }
        };
        TestOnly testOnly = new TestOnly() {
            @Override
            public Class<? extends Annotation> annotationType() {
                return TestOnly.class;
            }
        };
        return new Class<?>[] {
                identifier.annotationType(),
                nls.annotationType(),
                nonNls.annotationType(),
                printFormat.annotationType(),
                testOnly.annotationType()};
    }

    @MagicConstant(stringValues = {"debug", "release"})
    private static String selectBuildMode(@MagicConstant(stringValues = {"debug", "release"}) String mode) {
        return mode;
    }

    @MagicConstant(intValues = {SeverityConstants.LOW, SeverityConstants.MEDIUM, SeverityConstants.HIGH})
    private static int selectSeverity(
            @MagicConstant(intValues = {
                    SeverityConstants.LOW,
                    SeverityConstants.MEDIUM,
                    SeverityConstants.HIGH}) int severity) {
        return severity;
    }

    @MagicConstant(flags = {FeatureFlags.READ, FeatureFlags.WRITE, FeatureFlags.EXECUTE})
    private static int selectFeatureFlags(
            @MagicConstant(flags = {FeatureFlags.READ, FeatureFlags.WRITE, FeatureFlags.EXECUTE}) int flags) {
        return flags;
    }

    @MagicConstant(intValues = {WideConstants.SMALL_VALUE, WideConstants.LARGE_VALUE})
    private static long selectWideValue(
            @MagicConstant(intValues = {WideConstants.SMALL_VALUE, WideConstants.LARGE_VALUE}) long value) {
        return value;
    }

    @MagicConstant(flags = {WideConstants.READ, WideConstants.WRITE, WideConstants.EXPORT})
    private static long selectWideFlags(
            @MagicConstant(flags = {WideConstants.READ, WideConstants.WRITE, WideConstants.EXPORT}) long flags) {
        return flags;
    }

    private static boolean hasWideFlag(
            @MagicConstant(flags = {WideConstants.READ, WideConstants.WRITE, WideConstants.EXPORT}) long flags,
            @MagicConstant(flags = {WideConstants.READ, WideConstants.WRITE, WideConstants.EXPORT}) long expectedFlag) {
        return (flags & expectedFlag) == expectedFlag;
    }

    @MagicConstant(valuesFromClass = Cursor.class)
    private static int selectCursor(@MagicConstant(valuesFromClass = Cursor.class) int cursorType) {
        return cursorType;
    }

    @MagicConstant(flagsFromClass = InputEvent.class)
    private static int selectInputMask(@MagicConstant(flagsFromClass = InputEvent.class) int inputEventMask) {
        return inputEventMask;
    }

    @JdkConstants.CursorType
    private static int selectCursorTypeAlias(@JdkConstants.CursorType int cursorType) {
        return cursorType;
    }

    private static boolean isCrosshairCursor(@JdkConstants.CursorType int cursorType) {
        return cursorType == Cursor.CROSSHAIR_CURSOR;
    }

    @JdkConstants.InputEventMask
    private static int selectInputEventMaskAlias(@JdkConstants.InputEventMask int mask) {
        return mask;
    }

    private static boolean hasInputEventMask(
            @JdkConstants.InputEventMask int mask,
            @JdkConstants.InputEventMask int expectedMask) {
        return (mask & expectedMask) == expectedMask;
    }

    @JdkConstants.HorizontalAlignment
    private static int selectHorizontalAlignment(@JdkConstants.HorizontalAlignment int alignment) {
        return alignment;
    }

    @JdkConstants.FlowLayoutAlignment
    private static int selectFlowLayoutAlignment(@JdkConstants.FlowLayoutAlignment int alignment) {
        return alignment;
    }

    @JdkConstants.AdjustableOrientation
    private static int selectAdjustableOrientation(@JdkConstants.AdjustableOrientation int orientation) {
        return orientation;
    }

    @JdkConstants.CalendarMonth
    private static int selectCalendarMonth(@JdkConstants.CalendarMonth int month) {
        return month;
    }

    @JdkConstants.PatternFlags
    private static int selectPatternFlags(@JdkConstants.PatternFlags int flags) {
        return flags;
    }

    @JdkConstants.BoxLayoutAxis
    private static int selectBoxLayoutAxis(@JdkConstants.BoxLayoutAxis int axis) {
        return axis;
    }

    @JdkConstants.ListSelectionMode
    private static int selectListSelectionMode(@JdkConstants.ListSelectionMode int mode) {
        return mode;
    }

    @JdkConstants.TreeSelectionMode
    private static int selectTreeSelectionMode(@JdkConstants.TreeSelectionMode int mode) {
        return mode;
    }

    @JdkConstants.FontStyle
    private static int selectFontStyle(@JdkConstants.FontStyle int style) {
        return style;
    }

    @JdkConstants.TitledBorderJustification
    private static int selectTitledBorderJustification(@JdkConstants.TitledBorderJustification int justification) {
        return justification;
    }

    @JdkConstants.TitledBorderTitlePosition
    private static int selectTitledBorderTitlePosition(@JdkConstants.TitledBorderTitlePosition int position) {
        return position;
    }

    @JdkConstants.TabPlacement
    private static int selectTabPlacement(@JdkConstants.TabPlacement int placement) {
        return placement;
    }

    @JdkConstants.TabLayoutPolicy
    private static int selectTabLayoutPolicy(@JdkConstants.TabLayoutPolicy int policy) {
        return policy;
    }

    private static final class AnnotatedService {
        @NotNull
        private String name;

        @TestOnly
        private AnnotatedService(@NotNull String name) {
            this.name = Objects.requireNonNull(name, "name");
        }

        @NotNull
        private String currentName() {
            return name;
        }

        private void rename(@NotNull String newName) {
            name = Objects.requireNonNull(newName, "newName");
        }

        @Nullable
        private String optionalDescription(boolean present) {
            if (!present) {
                return null;
            }
            StringJoiner joiner = new StringJoiner(" ");
            joiner.add(name);
            joiner.add("library");
            return joiner.toString();
        }
    }

    private static final class SeverityConstants {
        private static final int LOW = 1;
        private static final int MEDIUM = 2;
        private static final int HIGH = 3;

        private SeverityConstants() {
        }
    }

    private static final class FeatureFlags {
        private static final int READ = 1;
        private static final int WRITE = 2;
        private static final int EXECUTE = 4;

        private FeatureFlags() {
        }
    }

    private static final class WideConstants {
        private static final long SMALL_VALUE = 7L;
        private static final long LARGE_VALUE = 1L << 40;
        private static final long READ = 1L << 32;
        private static final long WRITE = 1L << 33;
        private static final long EXPORT = 1L << 34;

        private WideConstants() {
        }
    }
}
