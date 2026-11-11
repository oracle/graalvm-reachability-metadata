/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_aspectj.aspectjweaver;

import java.io.IOException;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.List;

import org.aspectj.internal.lang.annotation.ajcDeclareAnnotation;
import org.aspectj.internal.lang.annotation.ajcDeclareEoW;
import org.aspectj.internal.lang.annotation.ajcDeclareParents;
import org.aspectj.internal.lang.annotation.ajcDeclarePrecedence;
import org.aspectj.internal.lang.annotation.ajcDeclareSoft;
import org.aspectj.internal.lang.annotation.ajcITD;
import org.aspectj.lang.annotation.After;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.AfterThrowing;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.aspectj.lang.annotation.DeclareError;
import org.aspectj.lang.annotation.DeclareWarning;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.lang.reflect.Advice;
import org.aspectj.lang.reflect.AdviceKind;
import org.aspectj.lang.reflect.AjType;
import org.aspectj.lang.reflect.AjTypeSystem;
import org.aspectj.lang.reflect.DeclareAnnotation;
import org.aspectj.lang.reflect.DeclareErrorOrWarning;
import org.aspectj.lang.reflect.DeclareParents;
import org.aspectj.lang.reflect.DeclarePrecedence;
import org.aspectj.lang.reflect.DeclareSoft;
import org.aspectj.lang.reflect.InterTypeConstructorDeclaration;
import org.aspectj.lang.reflect.InterTypeFieldDeclaration;
import org.aspectj.lang.reflect.InterTypeMethodDeclaration;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

public class AjTypeImplTest {
    private static final String WEAVED_TARGET_NAME = "org_aspectj.aspectjweaver.AjTypeImplTest$WeavedTarget";
    private static final String MIXIN_INTERFACE_NAME = "org_aspectj.aspectjweaver.AjTypeImplTest$MixinInterface";

    @Test
    void exposesJavaTypeMembersThroughAspectjReflectionApi() throws Exception {
        AjType<SampleType> type = AjTypeSystem.getAjType(SampleType.class);

        assertThat(names(type.getAjTypes())).contains(SampleType.PublicNested.class.getName());
        assertThat(names(type.getDeclaredAjTypes()))
                .contains(SampleType.PublicNested.class.getName(), SampleType.HiddenNested.class.getName());
        assertThat(type.getConstructor().getParameterCount()).isZero();
        assertThat(type.getDeclaredConstructor(AjTypeSystem.getAjType(int.class)).getParameterCount()).isOne();
        assertThat(type.getConstructors()).extracting(Constructor::getParameterCount).contains(0, 1);
        assertThat(type.getDeclaredConstructors()).extracting(Constructor::getParameterCount).contains(0, 1);

        assertThat(type.getField("publicField").getName()).isEqualTo("publicField");
        assertThat(type.getDeclaredField("secretField").getName()).isEqualTo("secretField");
        assertThat(names(type.getFields())).contains("publicField").doesNotContain("ajc$hiddenField");
        assertThat(names(type.getDeclaredFields()))
                .contains("publicField", "secretField")
                .doesNotContain("ajc$hiddenField", "declaredWarningMessage", "declaredErrorMessage");
        assertThatExceptionOfType(NoSuchFieldException.class)
                .isThrownBy(() -> type.getDeclaredField("ajc$hiddenField"));

        assertThat(type.getMethod("publicMethod", AjTypeSystem.getAjType(String.class)).getName())
                .isEqualTo("publicMethod");
        assertThat(type.getDeclaredMethod("secretMethod").getName()).isEqualTo("secretMethod");
        assertThat(names(type.getMethods())).contains("publicMethod").doesNotContain("ajc$hiddenMethod");
        assertThat(names(type.getDeclaredMethods()))
                .contains("publicMethod", "secretMethod")
                .doesNotContain("ajc$hiddenMethod");
        assertThatExceptionOfType(NoSuchMethodException.class)
                .isThrownBy(() -> type.getDeclaredMethod("ajc$hiddenMethod"));
    }

    @Test
    void discoversAspectPointcutsAdviceAndDeclareMembers() throws Exception {
        AjType<ReflectiveAspect> aspectType = AjTypeSystem.getAjType(ReflectiveAspect.class);

        assertThat(aspectType.isAspect()).isTrue();
        assertThat(aspectType.getDeclaredPointcut("declaredPointcut").getName()).isEqualTo("declaredPointcut");
        assertThat(aspectType.getPointcut("publicPointcut").getName()).isEqualTo("publicPointcut");
        assertThat(aspectType.getDeclaredPointcuts()).extracting(pointcut -> pointcut.getName())
                .contains("declaredPointcut", "publicPointcut");
        assertThat(aspectType.getPointcuts()).extracting(pointcut -> pointcut.getName()).contains("publicPointcut");

        assertThat(aspectType.getDeclaredAdvice()).extracting(Advice::getKind)
                .contains(AdviceKind.BEFORE, AdviceKind.AFTER, AdviceKind.AFTER_RETURNING,
                        AdviceKind.AFTER_THROWING, AdviceKind.AROUND);
        assertThat(aspectType.getAdvice()).extracting(Advice::getName).contains("publicBeforeAdvice");
        assertThat(aspectType.getDeclaredAdvice("beforeAdvice").getKind()).isEqualTo(AdviceKind.BEFORE);
        assertThat(aspectType.getAdvice("publicBeforeAdvice").getKind()).isEqualTo(AdviceKind.BEFORE);

        assertThat(aspectType.getDeclareErrorOrWarnings()).extracting(DeclareErrorOrWarning::getMessage)
                .contains("field warning", "field error", "method warning");
        assertThat(aspectType.getDeclareErrorOrWarnings()).extracting(DeclareErrorOrWarning::isError)
                .contains(true, false);
        assertThat(aspectType.getDeclareParents()).extracting(DeclareParents::isImplements).contains(true);
        DeclareSoft[] declareSofts = aspectType.getDeclareSofts();
        assertThat(declareSofts).hasSize(1);
        assertThat(declareSofts[0].getSoftenedExceptionType().getJavaClass()).isEqualTo(IOException.class);
        assertThat(aspectType.getDeclareAnnotations()).extracting(DeclareAnnotation::getKind)
                .contains(DeclareAnnotation.Kind.Type);
        List<String> precedencePatterns = Arrays.stream(aspectType.getDeclarePrecedence())
                .map(DeclarePrecedence::getPrecedenceOrder)
                .flatMap(Arrays::stream)
                .map(pattern -> pattern.asString())
                .toList();
        assertThat(precedencePatterns).contains("org_aspectj.aspectjweaver..*", "java.lang.String");
    }

    @Test
    void discoversInterTypeDeclarationsFromWovenStyleMembersAndDeclareParents() throws Exception {
        AjType<ReflectiveAspect> aspectType = AjTypeSystem.getAjType(ReflectiveAspect.class);
        AjType<WeavedTarget> targetType = AjTypeSystem.getAjType(WeavedTarget.class);

        assertThat(aspectType.getDeclaredITDMethods()).extracting(InterTypeMethodDeclaration::getName)
                .contains("declaredIntroducedMethod", "mixinMethod");
        assertThat(aspectType.getITDMethods()).extracting(InterTypeMethodDeclaration::getName)
                .contains("publicIntroducedMethod", "mixinMethod");
        assertThat(aspectType.getDeclaredITDMethod("declaredIntroducedMethod", targetType).getName())
                .isEqualTo("declaredIntroducedMethod");
        assertThat(aspectType.getITDMethod("publicIntroducedMethod", targetType).getName())
                .isEqualTo("publicIntroducedMethod");

        assertThat(aspectType.getDeclaredITDConstructors()).hasSize(1);
        assertThat(aspectType.getITDConstructors()).hasSize(1);
        InterTypeConstructorDeclaration declaredConstructor = aspectType.getDeclaredITDConstructor(
                targetType, AjTypeSystem.getAjType(String.class));
        InterTypeConstructorDeclaration publicConstructor = aspectType.getITDConstructor(
                targetType, AjTypeSystem.getAjType(String.class));
        assertThat(declaredConstructor.getTargetType()).isEqualTo(targetType);
        assertThat(publicConstructor.getTargetType()).isEqualTo(targetType);
        assertThat(javaClasses(declaredConstructor.getParameterTypes())).containsExactly(String.class);
        assertThat(javaClasses(publicConstructor.getParameterTypes())).containsExactly(String.class);

        assertThat(aspectType.getDeclaredITDFields()).extracting(InterTypeFieldDeclaration::getName)
                .contains("introducedField");
        assertThat(aspectType.getITDFields()).extracting(InterTypeFieldDeclaration::getName)
                .contains("introducedField");
        assertThat(aspectType.getDeclaredITDField("introducedField", targetType).getType().getJavaClass())
                .isEqualTo(String.class);
        assertThat(aspectType.getITDField("introducedField", targetType).getType().getJavaClass())
                .isEqualTo(String.class);
    }

    private static List<String> names(AjType<?>[] types) {
        return Arrays.stream(types).map(AjType::getName).toList();
    }

    private static List<Class<?>> javaClasses(AjType<?>[] types) {
        return Arrays.stream(types).<Class<?>>map(AjType::getJavaClass).toList();
    }

    private static List<String> names(Field[] fields) {
        return Arrays.stream(fields).map(Field::getName).toList();
    }

    private static List<String> names(Method[] methods) {
        return Arrays.stream(methods).map(Method::getName).toList();
    }

    public static class SampleType {
        public String publicField;
        public String ajc$hiddenField;
        @DeclareWarning("execution(* *(..))")
        public static String declaredWarningMessage = "warning hidden from normal fields";
        @DeclareError("execution(* *(..))")
        public static String declaredErrorMessage = "error hidden from normal fields";
        private int secretField;

        public SampleType() {
        }

        public SampleType(String value) {
            publicField = value;
        }

        private SampleType(int value) {
            secretField = value;
        }

        public String publicMethod(String value) {
            return value;
        }

        public void ajc$hiddenMethod() {
        }

        private int secretMethod() {
            return secretField;
        }

        public static class PublicNested {
        }

        private static class HiddenNested {
        }
    }

    @Aspect
    @org.aspectj.lang.annotation.DeclarePrecedence("org_aspectj.aspectjweaver..*")
    public static class ReflectiveAspect {
        @DeclareWarning("execution(* *(..))")
        public static String fieldWarning = "field warning";
        @DeclareError("execution(* *(..))")
        public static String fieldError = "field error";
        @org.aspectj.lang.annotation.DeclareParents(
                value = "org_aspectj.aspectjweaver.AjTypeImplTest.WeavedTarget",
                defaultImpl = MixinImplementation.class)
        public static MixinInterface mixin;

        @Pointcut("execution(* *(..))")
        public void publicPointcut() {
        }

        @Pointcut("execution(* *(..))")
        private void declaredPointcut() {
        }

        @Before("publicPointcut()")
        public void publicBeforeAdvice() {
        }

        @Before("declaredPointcut()")
        private void beforeAdvice() {
        }

        @After("publicPointcut()")
        private void afterAdvice() {
        }

        @AfterReturning("publicPointcut()")
        private void afterReturningAdvice() {
        }

        @AfterThrowing(pointcut = "publicPointcut()", throwing = "throwable")
        private void afterThrowingAdvice(Throwable throwable) {
        }

        @Around("publicPointcut()")
        private Object aroundAdvice() {
            return null;
        }

        @ajcITD(modifiers = Modifier.PUBLIC, targetType = WEAVED_TARGET_NAME, name = "publicIntroducedMethod")
        public void ajc$interMethod$publicIntroducedMethod(WeavedTarget target) {
        }

        @ajcITD(modifiers = Modifier.PRIVATE, targetType = WEAVED_TARGET_NAME, name = "declaredIntroducedMethod")
        public void ajc$interMethodDispatch1$declaredIntroducedMethod(WeavedTarget target) {
        }

        @ajcITD(modifiers = Modifier.PUBLIC, targetType = WEAVED_TARGET_NAME, name = "new")
        public static void ajc$postInterConstructor$weavedTarget(WeavedTarget target, String name) {
        }

        @ajcITD(modifiers = Modifier.PUBLIC, targetType = WEAVED_TARGET_NAME, name = "introducedField")
        public static void ajc$interFieldInit$introducedField(WeavedTarget target) {
        }

        public static String ajc$interFieldGetDispatch$introducedField(WeavedTarget target) {
            return "introduced";
        }

        @ajcDeclareEoW(message = "method warning", pointcut = "execution(* *(..))", isError = false)
        public static void ajc$declareWarning() {
        }

        @ajcDeclareParents(
                targetTypePattern = "org_aspectj.aspectjweaver.AjTypeImplTest.WeavedTarget",
                parentTypes = MIXIN_INTERFACE_NAME,
                isExtends = false)
        public static void ajc$declareParents() {
        }

        @ajcDeclareSoft(exceptionType = "java.io.IOException", pointcut = "execution(* *(..))")
        public static void ajc$declareSoft() {
        }

        @Marker
        @ajcDeclareAnnotation(
                kind = "at_type",
                pattern = "org_aspectj.aspectjweaver..*",
                annotation = "@org_aspectj.aspectjweaver.AjTypeImplTest.Marker")
        public static void ajc$declareAnnotation() {
        }

        @ajcDeclarePrecedence("java.lang.String")
        public static void ajc$declarePrecedence() {
        }
    }

    public static class WeavedTarget {
    }

    public interface MixinInterface {
        void mixinMethod();
    }

    public static class MixinImplementation implements MixinInterface {
        @Override
        public void mixinMethod() {
        }
    }

    @Retention(RetentionPolicy.RUNTIME)
    public @interface Marker {
    }
}
