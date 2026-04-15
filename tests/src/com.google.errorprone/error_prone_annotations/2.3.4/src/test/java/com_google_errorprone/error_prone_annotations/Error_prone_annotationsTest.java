/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_google_errorprone.error_prone_annotations;

import java.lang.annotation.Annotation;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

import javax.lang.model.element.Modifier;

import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.errorprone.annotations.CheckReturnValue;
import com.google.errorprone.annotations.CompatibleWith;
import com.google.errorprone.annotations.CompileTimeConstant;
import com.google.errorprone.annotations.DoNotCall;
import com.google.errorprone.annotations.DoNotMock;
import com.google.errorprone.annotations.ForOverride;
import com.google.errorprone.annotations.FormatMethod;
import com.google.errorprone.annotations.FormatString;
import com.google.errorprone.annotations.Immutable;
import com.google.errorprone.annotations.IncompatibleModifiers;
import com.google.errorprone.annotations.MustBeClosed;
import com.google.errorprone.annotations.NoAllocation;
import com.google.errorprone.annotations.OverridingMethodsMustInvokeSuper;
import com.google.errorprone.annotations.RequiredModifiers;
import com.google.errorprone.annotations.RestrictedApi;
import com.google.errorprone.annotations.SuppressPackageLocation;
import com.google.errorprone.annotations.Var;
import com.google.errorprone.annotations.concurrent.GuardedBy;
import com.google.errorprone.annotations.concurrent.LazyInit;
import com.google.errorprone.annotations.concurrent.LockMethod;
import com.google.errorprone.annotations.concurrent.UnlockMethod;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class Error_prone_annotationsTest {
    @Test
    void runtimeRetainedAnnotationsAreVisibleOnAnnotatedElements() throws Exception {
        CheckReturnValue classAnnotation = getSingleAnnotation(CheckedType.class, CheckReturnValue.class);
        CheckReturnValue constructorAnnotation = getSingleAnnotation(
                CheckedType.class.getDeclaredConstructor(String.class),
                CheckReturnValue.class);
        CheckReturnValue methodAnnotation = getSingleAnnotation(
                CheckedType.class.getDeclaredMethod("result"),
                CheckReturnValue.class);
        DoNotMock defaultDoNotMock = getSingleAnnotation(DefaultDoNotMockType.class, DoNotMock.class);
        DoNotMock inheritedDoNotMock = getSingleAnnotation(InheritedDoNotMockType.class, DoNotMock.class);
        DoNotMock explicitDoNotMock = getSingleAnnotation(BuilderOnly.class, DoNotMock.class);
        Immutable immutable = getSingleAnnotation(RuntimeImmutableType.class, Immutable.class);
        Immutable defaultImmutable = getSingleAnnotation(DefaultImmutableType.class, Immutable.class);
        Immutable inheritedImmutable = getSingleAnnotation(InheritedImmutableType.class, Immutable.class);
        Field lazyField = RuntimeFieldAndParameterAnnotations.class.getDeclaredField("lazilyInitialized");
        Field mutableField = RuntimeFieldAndParameterAnnotations.class.getDeclaredField("mutableField");
        Method acceptMethod = RuntimeFieldAndParameterAnnotations.class.getDeclaredMethod("accept", String.class);

        assertThat(classAnnotation).isNotNull();
        assertThat(constructorAnnotation).isNotNull();
        assertThat(methodAnnotation).isNotNull();

        assertThat(defaultDoNotMock).isNotNull();
        assertThat(defaultDoNotMock.value()).isEqualTo("Create a real instance instead");
        assertThat(inheritedDoNotMock).isNotNull();
        assertThat(inheritedDoNotMock.value()).isEqualTo("Create a real instance instead");
        assertThat(explicitDoNotMock).isNotNull();
        assertThat(explicitDoNotMock.value()).isEqualTo("Provide a purpose-built fake");

        assertThat(immutable).isNotNull();
        assertThat(immutable.containerOf()).containsExactly("K", "V");
        assertThat(defaultImmutable).isNotNull();
        assertThat(defaultImmutable.containerOf()).isEmpty();
        assertThat(inheritedImmutable).isNotNull();
        assertThat(inheritedImmutable.containerOf()).isEmpty();

        assertThat(getSingleAnnotation(lazyField, LazyInit.class)).isNotNull();
        assertThat(getSingleAnnotation(mutableField, Var.class)).isNotNull();
        assertThat(getSingleAnnotation(acceptMethod.getParameters()[0], Var.class)).isNotNull();
    }

    @Test
    void classRetainedAnnotationsRemainCompileTimeOnlyAtRuntime() throws Exception {
        Constructor<ClassRetentionType> constructor = ClassRetentionType.class.getDeclaredConstructor(String.class);
        Method ignoredReturnValueMethod = ClassRetentionType.class.getDeclaredMethod("ignoredReturnValue");
        Method doNotCallMethod = ClassRetentionType.class.getDeclaredMethod("doNotCall");
        Method overrideHookMethod = ClassRetentionType.class.getDeclaredMethod("overrideHook");
        Method formatMethod = ClassRetentionType.class.getDeclaredMethod("format", Object.class, String.class);
        Method guardedOperationMethod = ClassRetentionType.class.getDeclaredMethod("guardedOperation");
        Method acquireMethod = ClassRetentionType.class.getDeclaredMethod("acquire");
        Method releaseMethod = ClassRetentionType.class.getDeclaredMethod("release");
        Field constantField = ClassRetentionType.class.getDeclaredField("CONSTANT");
        Field guardedField = ClassRetentionType.class.getDeclaredField("guarded");
        Method restrictedMethod = RestrictedApiUsage.class.getDeclaredMethod("restrictedMethod");
        Constructor<RestrictedApiUsage> restrictedConstructor = RestrictedApiUsage.class.getDeclaredConstructor(boolean.class);

        assertThat(getSingleAnnotation(ClassRetentionType.class, CanIgnoreReturnValue.class)).isNull();
        assertThat(getSingleAnnotation(ignoredReturnValueMethod, CanIgnoreReturnValue.class)).isNull();
        assertThat(getSingleAnnotation(doNotCallMethod, DoNotCall.class)).isNull();
        assertThat(getSingleAnnotation(overrideHookMethod, ForOverride.class)).isNull();
        assertThat(getSingleAnnotation(overrideHookMethod, OverridingMethodsMustInvokeSuper.class)).isNull();
        assertThat(getSingleAnnotation(formatMethod, FormatMethod.class)).isNull();
        assertThat(getSingleAnnotation(formatMethod, NoAllocation.class)).isNull();
        assertThat(getSingleAnnotation(guardedOperationMethod, GuardedBy.class)).isNull();
        assertThat(getSingleAnnotation(acquireMethod, LockMethod.class)).isNull();
        assertThat(getSingleAnnotation(releaseMethod, UnlockMethod.class)).isNull();
        assertThat(getSingleAnnotation(constantField, CompileTimeConstant.class)).isNull();
        assertThat(getSingleAnnotation(guardedField, GuardedBy.class)).isNull();
        assertThat(getSingleAnnotation(constructor, MustBeClosed.class)).isNull();
        assertThat(getSingleAnnotation(constructor, FormatMethod.class)).isNull();
        assertThat(getSingleAnnotation(constructor.getParameters()[0], FormatString.class)).isNull();
        assertThat(getSingleAnnotation(formatMethod.getParameters()[0], CompatibleWith.class)).isNull();
        assertThat(getSingleAnnotation(formatMethod.getParameters()[1], CompileTimeConstant.class)).isNull();
        assertThat(getSingleAnnotation(formatMethod.getParameters()[1], FormatString.class)).isNull();
        assertThat(getSingleAnnotation(restrictedMethod, RestrictedApi.class)).isNull();
        assertThat(getSingleAnnotation(restrictedConstructor, RestrictedApi.class)).isNull();
        assertThat(getSingleAnnotation(PublicAbstractAnnotation.class, RequiredModifiers.class)).isNull();
        assertThat(getSingleAnnotation(NotFinalOrStaticAnnotation.class, IncompatibleModifiers.class)).isNull();
    }

    @Test
    void annotationContractsMatchPublishedMetaAnnotationsAndDefaults() throws Exception {
        assertAnnotationContract(
                CanIgnoreReturnValue.class,
                RetentionPolicy.CLASS,
                true,
                false,
                ElementType.METHOD,
                ElementType.TYPE);
        assertAnnotationContract(
                CheckReturnValue.class,
                RetentionPolicy.RUNTIME,
                true,
                false,
                ElementType.METHOD,
                ElementType.CONSTRUCTOR,
                ElementType.TYPE,
                ElementType.PACKAGE);
        assertAnnotationContract(
                CompatibleWith.class,
                RetentionPolicy.CLASS,
                true,
                false,
                ElementType.PARAMETER);
        assertAnnotationContract(
                CompileTimeConstant.class,
                RetentionPolicy.CLASS,
                true,
                false,
                ElementType.PARAMETER,
                ElementType.FIELD);
        assertAnnotationContract(
                GuardedBy.class,
                RetentionPolicy.CLASS,
                false,
                false,
                ElementType.FIELD,
                ElementType.METHOD);
        assertAnnotationContract(
                LazyInit.class,
                RetentionPolicy.RUNTIME,
                false,
                false,
                ElementType.FIELD);
        assertAnnotationContract(
                LockMethod.class,
                RetentionPolicy.CLASS,
                false,
                false,
                ElementType.METHOD);
        assertAnnotationContract(
                UnlockMethod.class,
                RetentionPolicy.CLASS,
                false,
                false,
                ElementType.METHOD);
        assertAnnotationContract(
                DoNotCall.class,
                RetentionPolicy.CLASS,
                false,
                false,
                ElementType.METHOD);
        assertAnnotationContract(
                DoNotMock.class,
                RetentionPolicy.RUNTIME,
                true,
                true,
                ElementType.TYPE,
                ElementType.ANNOTATION_TYPE);
        assertAnnotationContract(
                FormatMethod.class,
                RetentionPolicy.CLASS,
                true,
                false,
                ElementType.METHOD,
                ElementType.CONSTRUCTOR);
        assertAnnotationContract(
                FormatString.class,
                RetentionPolicy.CLASS,
                true,
                false,
                ElementType.PARAMETER);
        assertAnnotationContract(
                ForOverride.class,
                RetentionPolicy.CLASS,
                true,
                false,
                ElementType.METHOD);
        assertAnnotationContract(
                Immutable.class,
                RetentionPolicy.RUNTIME,
                true,
                true,
                ElementType.TYPE);
        assertAnnotationContract(
                IncompatibleModifiers.class,
                RetentionPolicy.CLASS,
                true,
                false,
                ElementType.ANNOTATION_TYPE);
        assertAnnotationContract(
                MustBeClosed.class,
                RetentionPolicy.CLASS,
                true,
                false,
                ElementType.CONSTRUCTOR,
                ElementType.METHOD);
        assertAnnotationContract(
                NoAllocation.class,
                RetentionPolicy.CLASS,
                true,
                false,
                ElementType.METHOD);
        assertAnnotationContract(
                OverridingMethodsMustInvokeSuper.class,
                RetentionPolicy.CLASS,
                true,
                false,
                ElementType.METHOD);
        assertAnnotationContract(
                RequiredModifiers.class,
                RetentionPolicy.CLASS,
                true,
                false,
                ElementType.ANNOTATION_TYPE);
        assertAnnotationContract(
                RestrictedApi.class,
                RetentionPolicy.CLASS,
                false,
                false,
                ElementType.CONSTRUCTOR,
                ElementType.METHOD);
        assertAnnotationContract(
                SuppressPackageLocation.class,
                RetentionPolicy.CLASS,
                false,
                false,
                ElementType.PACKAGE);
        assertAnnotationContract(
                Var.class,
                RetentionPolicy.RUNTIME,
                false,
                false,
                ElementType.FIELD,
                ElementType.PARAMETER,
                ElementType.LOCAL_VARIABLE);

        assertThat(DoNotCall.class.getDeclaredMethod("value").getDefaultValue()).isEqualTo("");
        assertThat(DoNotMock.class.getDeclaredMethod("value").getDefaultValue())
                .isEqualTo("Create a real instance instead");
        assertThat((Object[]) Immutable.class.getDeclaredMethod("containerOf").getDefaultValue()).isEmpty();
        assertThat(RestrictedApi.class.getDeclaredMethod("checkerName").getDefaultValue()).isEqualTo("RestrictedApi");
        assertThat(RestrictedApi.class.getDeclaredMethod("explanation").getDefaultValue()).isNull();
        assertThat(RestrictedApi.class.getDeclaredMethod("link").getDefaultValue()).isNull();
        assertThat(RestrictedApi.class.getDeclaredMethod("allowedOnPath").getDefaultValue()).isEqualTo("");
        assertThat((Object[]) RestrictedApi.class.getDeclaredMethod("whitelistAnnotations").getDefaultValue()).isEmpty();
        assertThat((Object[]) RestrictedApi.class.getDeclaredMethod("whitelistWithWarningAnnotations").getDefaultValue())
                .isEmpty();
    }

    private static void assertAnnotationContract(
            Class<? extends Annotation> annotationType,
            RetentionPolicy expectedRetention,
            boolean expectedDocumented,
            boolean expectedInherited,
            ElementType... expectedTargets) {
        Retention retention = getSingleAnnotation(annotationType, Retention.class);
        Target target = getSingleAnnotation(annotationType, Target.class);

        assertThat(retention == null ? RetentionPolicy.CLASS : retention.value()).isEqualTo(expectedRetention);
        assertThat(hasAnnotation(annotationType, Documented.class)).isEqualTo(expectedDocumented);
        assertThat(hasAnnotation(annotationType, Inherited.class)).isEqualTo(expectedInherited);
        assertThat(target).isNotNull();
        assertThat(target.value()).containsExactly(expectedTargets);
    }

    private static <T extends Annotation> T getSingleAnnotation(
            AnnotatedElement annotatedElement,
            Class<T> annotationType) {
        T[] annotations = annotatedElement.getAnnotationsByType(annotationType);
        return annotations.length == 0 ? null : annotations[0];
    }

    private static boolean hasAnnotation(
            AnnotatedElement annotatedElement,
            Class<? extends Annotation> annotationType) {
        return annotatedElement.getAnnotationsByType(annotationType).length > 0;
    }

    @RequiredModifiers({Modifier.PUBLIC, Modifier.ABSTRACT})
    private @interface PublicAbstractAnnotation {
    }

    @IncompatibleModifiers({Modifier.FINAL, Modifier.STATIC})
    private @interface NotFinalOrStaticAnnotation {
    }

    @DoNotMock("Provide a purpose-built fake")
    private @interface BuilderOnly {
    }

    @DoNotMock
    private static class DefaultDoNotMockType {
    }

    private static class InheritedDoNotMockType extends DefaultDoNotMockType {
    }

    @Immutable(containerOf = {"K", "V"})
    private static class RuntimeImmutableType<K, V> {
    }

    @Immutable
    private static class DefaultImmutableType {
    }

    private static class InheritedImmutableType extends DefaultImmutableType {
    }

    @CheckReturnValue
    private static class CheckedType {
        @CheckReturnValue
        CheckedType(String value) {
        }

        @CheckReturnValue
        String result() {
            return "value";
        }
    }

    private static class RuntimeFieldAndParameterAnnotations {
        @LazyInit
        private String lazilyInitialized;

        @Var
        private String mutableField;

        private void accept(@Var String mutableParameter) {
            @Var String mutableLocal = mutableParameter;
            mutableField = mutableLocal;
        }
    }

    @CanIgnoreReturnValue
    private static class ClassRetentionType<T> {
        @CompileTimeConstant
        private static final String CONSTANT = "constant";

        @GuardedBy("lock")
        private String guarded = CONSTANT;

        private final Object lock = new Object();

        @MustBeClosed
        @FormatMethod
        ClassRetentionType(@FormatString String template) {
            guarded = template;
        }

        @CanIgnoreReturnValue
        String ignoredReturnValue() {
            return guarded;
        }

        @DoNotCall
        String doNotCall() {
            return guarded;
        }

        @ForOverride
        @OverridingMethodsMustInvokeSuper
        protected String overrideHook() {
            return guarded;
        }

        @FormatMethod
        @NoAllocation
        String format(@CompatibleWith("T") T value, @CompileTimeConstant @FormatString String template) {
            @Var String mutableLocal = template + value;
            return mutableLocal;
        }

        @GuardedBy("lock")
        String guardedOperation() {
            synchronized (lock) {
                return guarded;
            }
        }

        @LockMethod({"lock", "secondaryLock"})
        void acquire() {
        }

        @UnlockMethod({"lock", "secondaryLock"})
        void release() {
        }
    }

    private static class RestrictedApiUsage {
        @RestrictedApi(
                explanation = "Used only from the integration fixture",
                link = "https://example.invalid/method",
                allowedOnPath = ".*/allowed/.*",
                whitelistAnnotations = {BuilderOnly.class},
                whitelistWithWarningAnnotations = {PublicAbstractAnnotation.class}
        )
        void restrictedMethod() {
        }

        @RestrictedApi(
                explanation = "Construct through the integration fixture",
                link = "https://example.invalid/constructor"
        )
        RestrictedApiUsage(boolean unused) {
        }
    }
}
