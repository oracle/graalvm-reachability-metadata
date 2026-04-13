/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jetbrains.annotations_java5;

import org.intellij.lang.annotations.Flow;
import org.intellij.lang.annotations.Identifier;
import org.intellij.lang.annotations.JdkConstants;
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

import java.lang.annotation.Annotation;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;

import static org.assertj.core.api.Assertions.assertThat;

class Annotations_java5Test {
    @Test
    void jetbrainsAnnotationsExposeExpectedRetentionTargetsAndDefaults() throws Exception {
        assertDocumentedClassRetentionAndTargets(NotNull.class, ElementType.METHOD, ElementType.FIELD, ElementType.PARAMETER, ElementType.LOCAL_VARIABLE);
        assertDocumentedClassRetentionAndTargets(Nullable.class, ElementType.METHOD, ElementType.FIELD, ElementType.PARAMETER, ElementType.LOCAL_VARIABLE);
        assertDocumentedClassRetentionAndTargets(Contract.class, ElementType.METHOD, ElementType.CONSTRUCTOR);
        assertDocumentedClassRetentionAndTargets(Nls.class, ElementType.METHOD, ElementType.FIELD, ElementType.PARAMETER, ElementType.LOCAL_VARIABLE, ElementType.TYPE, ElementType.PACKAGE);
        assertDocumentedClassRetentionAndTargets(Blocking.class, ElementType.METHOD, ElementType.CONSTRUCTOR, ElementType.TYPE);
        assertDocumentedClassRetentionAndTargets(NonBlocking.class, ElementType.METHOD, ElementType.CONSTRUCTOR, ElementType.TYPE);
        assertDocumentedClassRetentionAndTargets(NonNls.class, ElementType.METHOD, ElementType.FIELD, ElementType.PARAMETER, ElementType.LOCAL_VARIABLE, ElementType.TYPE, ElementType.PACKAGE);
        assertDocumentedClassRetentionAndTargets(TestOnly.class, ElementType.METHOD, ElementType.CONSTRUCTOR, ElementType.FIELD, ElementType.TYPE);
        assertDocumentedClassRetentionAndTargets(VisibleForTesting.class, ElementType.METHOD, ElementType.CONSTRUCTOR, ElementType.FIELD, ElementType.TYPE);
        assertDocumentedClassRetentionAndTargets(MustBeInvokedByOverriders.class, ElementType.METHOD);
        assertDocumentedClassRetentionAndTargets(PropertyKey.class, ElementType.PARAMETER, ElementType.LOCAL_VARIABLE, ElementType.FIELD);
        assertClassRetentionAndTargets(Async.Schedule.class, ElementType.METHOD, ElementType.CONSTRUCTOR, ElementType.PARAMETER);
        assertClassRetentionAndTargets(Async.Execute.class, ElementType.METHOD, ElementType.CONSTRUCTOR, ElementType.PARAMETER);
        assertClassRetentionAndTargets(Debug.Renderer.class, ElementType.TYPE);
        assertDocumentedClassRetentionAndTargets(ApiStatus.AvailableSince.class, ElementType.TYPE, ElementType.ANNOTATION_TYPE, ElementType.METHOD, ElementType.CONSTRUCTOR, ElementType.FIELD, ElementType.PACKAGE);
        assertDocumentedClassRetentionAndTargets(ApiStatus.Experimental.class, ElementType.TYPE, ElementType.ANNOTATION_TYPE, ElementType.METHOD, ElementType.CONSTRUCTOR, ElementType.FIELD, ElementType.PACKAGE);
        assertDocumentedClassRetentionAndTargets(ApiStatus.Internal.class, ElementType.TYPE, ElementType.ANNOTATION_TYPE, ElementType.METHOD, ElementType.CONSTRUCTOR, ElementType.FIELD, ElementType.PACKAGE);
        assertDocumentedClassRetentionAndTargets(ApiStatus.NonExtendable.class, ElementType.TYPE, ElementType.METHOD);
        assertDocumentedClassRetentionAndTargets(ApiStatus.OverrideOnly.class, ElementType.TYPE, ElementType.METHOD);
        assertDocumentedClassRetentionAndTargets(ApiStatus.ScheduledForRemoval.class, ElementType.TYPE, ElementType.ANNOTATION_TYPE, ElementType.METHOD, ElementType.CONSTRUCTOR, ElementType.FIELD, ElementType.PACKAGE);

        assertThat(annotationMember(NotNull.class, "value").getDefaultValue()).isEqualTo("");
        assertThat(annotationMember(NotNull.class, "exception").getDefaultValue()).isEqualTo(Exception.class);
        assertThat(annotationMember(Nullable.class, "value").getDefaultValue()).isEqualTo("");
        assertThat(annotationMember(Contract.class, "value").getDefaultValue()).isEqualTo("");
        assertThat(annotationMember(Contract.class, "pure").getDefaultValue()).isEqualTo(false);
        assertThat(annotationMember(Contract.class, "mutates").getDefaultValue()).isEqualTo("");
        assertThat(annotationMember(Nls.class, "capitalization").getDefaultValue()).isEqualTo(Nls.Capitalization.NotSpecified);
        assertThat(annotationMember(ApiStatus.ScheduledForRemoval.class, "inVersion").getDefaultValue()).isEqualTo("");
        assertThat(annotationMember(ApiStatus.AvailableSince.class, "value").getDefaultValue()).isNull();
        assertThat(annotationMember(PropertyKey.class, "resourceBundle").getDefaultValue()).isNull();
        assertThat(annotationMember(Debug.Renderer.class, "text").getDefaultValue()).isEqualTo("");
        assertThat(annotationMember(Debug.Renderer.class, "childrenArray").getDefaultValue()).isEqualTo("");
        assertThat(annotationMember(Debug.Renderer.class, "hasChildren").getDefaultValue()).isEqualTo("");
    }

    @Test
    void intellijAnnotationsExposeExpectedRetentionTargetsAndDefaults() throws Exception {
        assertClassRetentionAndTargets(Language.class, ElementType.METHOD, ElementType.FIELD, ElementType.PARAMETER, ElementType.LOCAL_VARIABLE, ElementType.ANNOTATION_TYPE);
        assertSourceRetentionAndTargets(MagicConstant.class, ElementType.FIELD, ElementType.PARAMETER, ElementType.LOCAL_VARIABLE, ElementType.ANNOTATION_TYPE, ElementType.METHOD);
        assertClassRetentionAndTargets(Flow.class, ElementType.PARAMETER, ElementType.METHOD);
        assertClassRetentionAndTargets(RegExp.class, ElementType.METHOD, ElementType.FIELD, ElementType.PARAMETER, ElementType.LOCAL_VARIABLE, ElementType.ANNOTATION_TYPE);
        assertClassRetentionAndTargets(Pattern.class, ElementType.METHOD, ElementType.FIELD, ElementType.PARAMETER, ElementType.LOCAL_VARIABLE, ElementType.ANNOTATION_TYPE);
        assertClassRetentionAndTargets(Subst.class, ElementType.METHOD, ElementType.FIELD, ElementType.LOCAL_VARIABLE, ElementType.PARAMETER);

        assertNoExplicitRetentionOrTarget(Identifier.class);
        assertNoExplicitRetentionOrTarget(PrintFormat.class);

        assertThat(annotationMember(Language.class, "value").getDefaultValue()).isNull();
        assertThat(annotationMember(Language.class, "prefix").getDefaultValue()).isEqualTo("");
        assertThat(annotationMember(Language.class, "suffix").getDefaultValue()).isEqualTo("");
        assertThat(annotationMember(RegExp.class, "prefix").getDefaultValue()).isEqualTo("");
        assertThat(annotationMember(RegExp.class, "suffix").getDefaultValue()).isEqualTo("");
        assertThat(annotationMember(Subst.class, "value").getDefaultValue()).isNull();
        assertThat(annotationMember(Pattern.class, "value").getDefaultValue()).isNull();

        assertThat(Flow.DEFAULT_SOURCE).isEqualTo("The method argument (if parameter was annotated) or this container (if instance method was annotated)");
        assertThat(Flow.THIS_SOURCE).isEqualTo("this");
        assertThat(Flow.DEFAULT_TARGET).isEqualTo("This container (if the parameter was annotated) or the return value (if instance method was annotated)");
        assertThat(Flow.RETURN_METHOD_TARGET).isEqualTo("The return value of this method");
        assertThat(Flow.THIS_TARGET).isEqualTo("this");
        assertThat(annotationMember(Flow.class, "source").getDefaultValue()).isEqualTo(Flow.DEFAULT_SOURCE);
        assertThat(annotationMember(Flow.class, "sourceIsContainer").getDefaultValue()).isEqualTo(false);
        assertThat(annotationMember(Flow.class, "target").getDefaultValue()).isEqualTo(Flow.DEFAULT_TARGET);
        assertThat(annotationMember(Flow.class, "targetIsContainer").getDefaultValue()).isEqualTo(false);

        assertThat((long[]) annotationMember(MagicConstant.class, "intValues").getDefaultValue()).isEmpty();
        assertThat((String[]) annotationMember(MagicConstant.class, "stringValues").getDefaultValue()).isEmpty();
        assertThat((long[]) annotationMember(MagicConstant.class, "flags").getDefaultValue()).isEmpty();
        assertThat(annotationMember(MagicConstant.class, "valuesFromClass").getDefaultValue()).isEqualTo(void.class);
        assertThat(annotationMember(MagicConstant.class, "flagsFromClass").getDefaultValue()).isEqualTo(void.class);
    }

    @Test
    void nlsCapitalizationEnumExposesStableValues() {
        assertThat(Nls.Capitalization.values())
            .containsExactly(Nls.Capitalization.NotSpecified, Nls.Capitalization.Title, Nls.Capitalization.Sentence);
        assertThat(Nls.Capitalization.valueOf("NotSpecified")).isSameAs(Nls.Capitalization.NotSpecified);
        assertThat(Nls.Capitalization.valueOf("Title")).isSameAs(Nls.Capitalization.Title);
        assertThat(Nls.Capitalization.valueOf("Sentence")).isSameAs(Nls.Capitalization.Sentence);
    }

    @Test
    void representativeAnnotatedDeclarationsCompileAndRun() {
        DerivedProcessor processor = new DerivedProcessor();

        assertThat(processor.requireValue("value")).isEqualTo("value");
        assertThat(processor.findValue(true)).isEqualTo("present");
        assertThat(processor.findValue(false)).isNull();
        assertThat(processor.renderMessage("hello", "world")).isEqualTo("hello-world");
        assertThat(processor.renderCode("return 1;", "[a-z]+", "messages.key", 2L, java.util.regex.Pattern.CASE_INSENSITIVE))
            .isEqualTo("return 1;|[a-z]+|messages.key|2|2");

        processor.schedule(() -> processor.beforeOverride());
        assertThat(processor.wasBaseMethodInvoked()).isTrue();
    }

    @Test
    void jdkConstantAnnotatedApisHandlePatternFlagsAndCalendarMonths() {
        JdkConstantProcessor processor = new JdkConstantProcessor();

        assertThat(processor.findMatches("Alpha\nbeta\nALPHA", "^alpha$", java.util.regex.Pattern.CASE_INSENSITIVE | java.util.regex.Pattern.MULTILINE))
            .containsExactly("Alpha", "ALPHA");
        assertThat(processor.findMatches("abc", "^z$", 0)).isEmpty();

        assertThat(processor.advanceMonth(Calendar.JANUARY, 2)).isEqualTo(Calendar.MARCH);
        assertThat(processor.advanceMonth(Calendar.DECEMBER, 1)).isEqualTo(Calendar.JANUARY);
        assertThat(processor.advanceMonth(Calendar.MARCH, -4)).isEqualTo(Calendar.NOVEMBER);
    }

    private static Method annotationMember(Class<? extends Annotation> annotationType, String memberName) throws NoSuchMethodException {
        return annotationType.getMethod(memberName);
    }

    private static void assertDocumentedClassRetentionAndTargets(Class<? extends Annotation> annotationType, ElementType... expectedTargets) {
        assertThat(annotationType.isAnnotation()).isTrue();
        assertThat(annotationType.getAnnotation(java.lang.annotation.Documented.class)).isNotNull();
        assertRetentionAndTargets(annotationType, RetentionPolicy.CLASS, expectedTargets);
    }

    private static void assertClassRetentionAndTargets(Class<? extends Annotation> annotationType, ElementType... expectedTargets) {
        assertThat(annotationType.isAnnotation()).isTrue();
        assertRetentionAndTargets(annotationType, RetentionPolicy.CLASS, expectedTargets);
    }

    private static void assertSourceRetentionAndTargets(Class<? extends Annotation> annotationType, ElementType... expectedTargets) {
        assertThat(annotationType.isAnnotation()).isTrue();
        assertRetentionAndTargets(annotationType, RetentionPolicy.SOURCE, expectedTargets);
    }

    private static void assertRetentionAndTargets(Class<? extends Annotation> annotationType, RetentionPolicy expectedRetention, ElementType... expectedTargets) {
        Retention retention = annotationType.getAnnotation(Retention.class);
        Target target = annotationType.getAnnotation(Target.class);

        assertThat(retention).isNotNull();
        assertThat(retention.value()).isEqualTo(expectedRetention);
        assertThat(target).isNotNull();
        assertThat(Set.of(target.value())).containsExactlyInAnyOrder(expectedTargets);
    }

    private static void assertNoExplicitRetentionOrTarget(Class<? extends Annotation> annotationType) {
        assertThat(annotationType.isAnnotation()).isTrue();
        assertThat(annotationType.getAnnotation(Retention.class)).isNull();
        assertThat(annotationType.getAnnotation(Target.class)).isNull();
    }

    @Blocking
    @VisibleForTesting
    @ApiStatus.AvailableSince("23.0.0")
    @ApiStatus.NonExtendable
    @Debug.Renderer(text = "toString()", childrenArray = "new Object[0]", hasChildren = "false")
    private static class BaseProcessor {
        private boolean baseMethodInvoked;

        @MustBeInvokedByOverriders
        protected void beforeOverride() {
            this.baseMethodInvoked = true;
        }

        protected final boolean wasBaseMethodInvoked() {
            return this.baseMethodInvoked;
        }
    }

    private static final class JdkConstantProcessor {
        List<String> findMatches(String text, String expression, @JdkConstants.PatternFlags int flags) {
            java.util.regex.Pattern compiledPattern = java.util.regex.Pattern.compile(expression, flags);
            Matcher matcher = compiledPattern.matcher(text);
            List<String> matches = new ArrayList<>();
            while (matcher.find()) {
                matches.add(matcher.group());
            }
            return matches;
        }

        @JdkConstants.CalendarMonth
        int advanceMonth(@JdkConstants.CalendarMonth int month, int delta) {
            return Math.floorMod(month + delta, 12);
        }
    }

    @NonBlocking
    private static final class DerivedProcessor extends BaseProcessor {
        @Override
        protected void beforeOverride() {
            super.beforeOverride();
        }

        @Contract(value = "null -> fail; !null -> param1", pure = true)
        @NotNull
        String requireValue(@NotNull String value) {
            return value;
        }

        @Nullable
        String findValue(boolean present) {
            return present ? "present" : null;
        }

        @Nls(capitalization = Nls.Capitalization.Sentence)
        String renderMessage(@NonNls String firstPart, @Subst("sample") String secondPart) {
            @Identifier String identifier = "messageId";
            @PrintFormat String format = "%s-%s";
            assertThat(identifier).startsWith("message");
            return String.format(format, firstPart, secondPart);
        }

        @Flow(source = Flow.THIS_SOURCE, target = Flow.RETURN_METHOD_TARGET)
        String renderCode(
            @Language("JAVA") String code,
            @RegExp(prefix = "^", suffix = "$") String regex,
            @PropertyKey(resourceBundle = "messages") String key,
            @MagicConstant(intValues = {1L, 2L, 4L}) long mode,
            int flags
        ) {
            return code + '|' + regex + '|' + key + '|' + mode + '|' + flags;
        }

        @TestOnly
        @Async.Schedule
        void schedule(@Async.Execute Runnable action) {
            action.run();
        }
    }
}
