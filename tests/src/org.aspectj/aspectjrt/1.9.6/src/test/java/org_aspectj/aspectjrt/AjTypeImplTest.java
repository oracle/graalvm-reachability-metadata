/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_aspectj.aspectjrt;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

import org.aspectj.internal.lang.annotation.ajcDeclareAnnotation;
import org.aspectj.internal.lang.annotation.ajcDeclareEoW;
import org.aspectj.internal.lang.annotation.ajcDeclareParents;
import org.aspectj.internal.lang.annotation.ajcDeclarePrecedence;
import org.aspectj.internal.lang.annotation.ajcDeclareSoft;
import org.aspectj.internal.lang.annotation.ajcITD;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.After;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.AfterThrowing;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.aspectj.lang.annotation.DeclareError;
import org.aspectj.lang.annotation.DeclareParents;
import org.aspectj.lang.annotation.DeclarePrecedence;
import org.aspectj.lang.annotation.DeclareWarning;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.lang.reflect.Advice;
import org.aspectj.lang.reflect.AdviceKind;
import org.aspectj.lang.reflect.AjType;
import org.aspectj.lang.reflect.AjTypeSystem;
import org.aspectj.lang.reflect.DeclareErrorOrWarning;
import org.aspectj.lang.reflect.InterTypeFieldDeclaration;
import org.aspectj.lang.reflect.InterTypeMethodDeclaration;
import org.junit.jupiter.api.Test;

public class AjTypeImplTest {
    private static final String SYNTHETIC_TARGET_NAME = "org_aspectj.aspectjrt.AjTypeImplTest$SyntheticTarget";

    @Test
    void exposesJavaClassMembersThroughAjTypeApi() throws Exception {
        AjType<ReflectiveFixture> type = AjTypeSystem.getAjType(ReflectiveFixture.class);
        AjType<String> stringType = AjTypeSystem.getAjType(String.class);
        AjType<Integer> intType = AjTypeSystem.getAjType(int.class);

        assertThat(type.getAjTypes()).extracting(AjType::getName)
                .contains(ReflectiveFixture.PublicMember.class.getName());
        assertThat(type.getDeclaredAjTypes()).extracting(AjType::getName)
                .contains(
                        ReflectiveFixture.PublicMember.class.getName(),
                        ReflectiveFixture.PrivateMember.class.getName());

        Constructor<?> publicConstructor = type.getConstructor(stringType);
        Constructor<?> privateConstructor = type.getDeclaredConstructor(intType);
        assertThat(publicConstructor.getParameterTypes()).containsExactly(String.class);
        assertThat(privateConstructor.getParameterTypes()).containsExactly(int.class);
        assertThat(type.getConstructors()).extracting(Constructor::getParameterCount).contains(0, 1);
        assertThat(type.getDeclaredConstructors()).extracting(Constructor::getParameterCount).contains(0, 1);

        Field declaredField = type.getDeclaredField("declaredField");
        Field publicField = type.getField("publicField");
        assertThat(declaredField.getType()).isEqualTo(int.class);
        assertThat(publicField.getType()).isEqualTo(String.class);
        assertThat(type.getDeclaredFields()).extracting(Field::getName).contains("declaredField", "publicField");
        assertThat(type.getFields()).extracting(Field::getName).contains("publicField");

        Method declaredMethod = type.getDeclaredMethod("declaredMethod", stringType);
        Method publicMethod = type.getMethod("publicMethod");
        assertThat(declaredMethod.getReturnType()).isEqualTo(String.class);
        assertThat(publicMethod.getReturnType()).isEqualTo(void.class);
        assertThat(type.getDeclaredMethods()).extracting(Method::getName).contains("declaredMethod", "publicMethod");
        assertThat(type.getMethods()).extracting(Method::getName).contains("publicMethod");
    }

    @Test
    void exposesAspectAnnotationsAndSyntheticAspectMembers() throws Exception {
        AjType<AnnotationAspect> aspectType = AjTypeSystem.getAjType(AnnotationAspect.class);
        AjType<SyntheticTarget> targetType = AjTypeSystem.getAjType(SyntheticTarget.class);

        assertThat(aspectType.isAspect()).isTrue();
        assertThat(aspectType.getDeclaredPointcuts()).extracting(pointcut -> pointcut.getName())
                .contains("allExecutions");
        assertThat(aspectType.getPointcuts()).extracting(pointcut -> pointcut.getName()).contains("allExecutions");

        assertThat(aspectType.getDeclaredAdvice()).extracting(Advice::getKind)
                .contains(AdviceKind.BEFORE, AdviceKind.AFTER, AdviceKind.AFTER_RETURNING,
                        AdviceKind.AFTER_THROWING, AdviceKind.AROUND);
        assertThat(aspectType.getAdvice()).extracting(Advice::getKind)
                .contains(AdviceKind.BEFORE, AdviceKind.AFTER, AdviceKind.AFTER_RETURNING,
                        AdviceKind.AFTER_THROWING, AdviceKind.AROUND);

        assertThat(aspectType.getDeclaredITDConstructors()).hasSize(1);
        assertThat(aspectType.getITDConstructors()).hasSize(1);

        assertThat(aspectType.getDeclaredITDMethods()).extracting(InterTypeMethodDeclaration::getName)
                .contains("syntheticMethod", "introducedMethod");
        assertThat(aspectType.getITDMethods()).extracting(InterTypeMethodDeclaration::getName)
                .contains("syntheticMethod", "introducedMethod");
        assertThat(aspectType.getDeclaredITDMethod("syntheticMethod", targetType).getTargetType())
                .isEqualTo(targetType);
        assertThat(aspectType.getITDMethod("syntheticMethod", targetType).getTargetType()).isEqualTo(targetType);

        assertThat(aspectType.getDeclaredITDFields()).extracting(InterTypeFieldDeclaration::getName)
                .contains("syntheticField");
        assertThat(aspectType.getITDFields()).extracting(InterTypeFieldDeclaration::getName)
                .contains("syntheticField");
        assertThat(aspectType.getDeclaredITDField("syntheticField", targetType).getTargetType())
                .isEqualTo(targetType);
        assertThat(aspectType.getITDField("syntheticField", targetType).getTargetType()).isEqualTo(targetType);

        assertThat(aspectType.getDeclareErrorOrWarnings()).extracting(DeclareErrorOrWarning::getMessage)
                .contains("declared warning", "declared error", "synthetic warning");
        assertThat(aspectType.getDeclareParents()).hasSizeGreaterThanOrEqualTo(2);
        assertThat(aspectType.getDeclareSofts()).hasSize(1);
        assertThat(aspectType.getDeclareAnnotations()).hasSize(1);
        assertThat(aspectType.getDeclarePrecedence()).hasSize(2);
    }

    public static class ReflectiveFixture {
        public String publicField;
        private int declaredField;

        public ReflectiveFixture() {
        }

        public ReflectiveFixture(String value) {
            this.publicField = value;
        }

        private ReflectiveFixture(int value) {
            this.declaredField = value;
        }

        public void publicMethod() {
        }

        private String declaredMethod(String value) {
            return value;
        }

        public static class PublicMember {
        }

        private static class PrivateMember {
        }
    }

    public static class SyntheticTarget {
    }

    public interface IntroducedMixin {
        void introducedMethod();
    }

    public static class MixinImplementation implements IntroducedMixin {
        @Override
        public void introducedMethod() {
        }
    }

    @Retention(RetentionPolicy.RUNTIME)
    public @interface MarkerAnnotation {
    }

    @Aspect
    @DeclarePrecedence("first, second")
    public static class AnnotationAspect {
        @DeclareWarning("execution(* *(..))")
        public static final String WARNING_MESSAGE = "declared warning";

        @DeclareError("execution(* *(..))")
        public static final String ERROR_MESSAGE = "declared error";

        @DeclareParents(value = "org_aspectj.aspectjrt..*", defaultImpl = MixinImplementation.class)
        public IntroducedMixin introducedMixin;

        @Pointcut("execution(* *(..))")
        public void allExecutions() {
        }

        @Before("allExecutions()")
        public void beforeAdvice() {
        }

        @After("allExecutions()")
        public void afterAdvice() {
        }

        @AfterReturning(pointcut = "allExecutions()", returning = "result")
        public void afterReturningAdvice(Object result) {
        }

        @AfterThrowing(pointcut = "allExecutions()", throwing = "failure")
        public void afterThrowingAdvice(Throwable failure) {
        }

        @Around("allExecutions()")
        public Object aroundAdvice(ProceedingJoinPoint proceedingJoinPoint) throws Throwable {
            return proceedingJoinPoint.proceed();
        }

        @ajcITD(modifiers = Modifier.PUBLIC, targetType = SYNTHETIC_TARGET_NAME, name = "syntheticMethod")
        public static void ajc$interMethod$syntheticMethod(SyntheticTarget target) {
        }

        @ajcITD(modifiers = Modifier.PUBLIC, targetType = SYNTHETIC_TARGET_NAME, name = "syntheticMethod")
        public static void ajc$interMethodDispatch1$syntheticMethod(SyntheticTarget target) {
        }

        @ajcITD(modifiers = Modifier.PUBLIC, targetType = SYNTHETIC_TARGET_NAME, name = "syntheticField")
        public static void ajc$interFieldInit$syntheticField(SyntheticTarget target) {
        }

        public static String ajc$interFieldGetDispatch$syntheticField(SyntheticTarget target) {
            return "synthetic";
        }

        @ajcITD(modifiers = Modifier.PUBLIC, targetType = SYNTHETIC_TARGET_NAME, name = "new")
        public static void ajc$postInterConstructor$syntheticConstructor(SyntheticTarget target) {
        }

        @ajcDeclareEoW(pointcut = "execution(* *(..))", message = "synthetic warning", isError = false)
        public void ajc$declare_eow() {
        }

        @ajcDeclareParents(
                targetTypePattern = "org_aspectj.aspectjrt..*",
                parentTypes = "java.io.Serializable",
                isExtends = false)
        public void ajc$declare_parents() {
        }

        @ajcDeclareSoft(pointcut = "execution(* *(..))", exceptionType = "java.lang.IllegalStateException")
        public void ajc$declare_soft() {
        }

        @ajcDeclareAnnotation(
                pattern = "* org_aspectj.aspectjrt..*(..)",
                annotation = "@MarkerAnnotation",
                kind = "at_method")
        @MarkerAnnotation
        public void ajc$declare_annotation() {
        }

        @ajcDeclarePrecedence("first, second")
        public void ajc$declare_precedence() {
        }
    }
}
