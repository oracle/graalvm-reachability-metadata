/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_aspectj.aspectjrt;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;

import org.aspectj.internal.lang.annotation.ajcDeclareAnnotation;
import org.aspectj.internal.lang.annotation.ajcDeclareEoW;
import org.aspectj.internal.lang.annotation.ajcDeclareParents;
import org.aspectj.internal.lang.annotation.ajcDeclarePrecedence;
import org.aspectj.internal.lang.annotation.ajcDeclareSoft;
import org.aspectj.internal.lang.annotation.ajcITD;
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
import org.aspectj.lang.reflect.DeclareAnnotation;
import org.aspectj.lang.reflect.DeclareErrorOrWarning;
import org.aspectj.lang.reflect.InterTypeConstructorDeclaration;
import org.aspectj.lang.reflect.InterTypeFieldDeclaration;
import org.aspectj.lang.reflect.InterTypeMethodDeclaration;
import org.junit.jupiter.api.Test;

public class AjTypeImplTest {
    private static final String TARGET_TYPE_NAME = "org_aspectj.aspectjrt.AjTypeImplTest$TargetType";

    @Test
    void exposesJavaTypeMembersThroughAjType() throws Exception {
        AjType<SampleType> type = AjTypeSystem.getAjType(SampleType.class);
        AjType<String> stringType = AjTypeSystem.getAjType(String.class);

        assertThat(javaClassNames(type.getAjTypes())).contains(SampleType.PublicNested.class.getName());
        assertThat(javaClassNames(type.getDeclaredAjTypes()))
                .contains(SampleType.PublicNested.class.getName(), SampleType.PackageNested.class.getName());

        Constructor<?> constructor = type.getConstructor();
        assertThat(constructor.getParameterCount()).isZero();
        Constructor<?> declaredConstructor = type.getDeclaredConstructor(stringType);
        assertThat(declaredConstructor.getParameterCount()).isEqualTo(1);
        assertThat(type.getConstructors()).isNotEmpty();
        assertThat(type.getDeclaredConstructors()).hasSizeGreaterThanOrEqualTo(2);

        Field declaredField = type.getDeclaredField("privateField");
        assertThat(declaredField.getName()).isEqualTo("privateField");
        assertThat(type.getDeclaredFields()).extracting(Field::getName)
                .contains("privateField", "publicField")
                .doesNotContain("ajc$hiddenField");
        Field publicField = type.getField("publicField");
        assertThat(publicField.getName()).isEqualTo("publicField");
        assertThat(type.getFields()).extracting(Field::getName).contains("publicField");

        Method privateMethod = type.getDeclaredMethod("privateMethod");
        assertThat(privateMethod.getName()).isEqualTo("privateMethod");
        Method publicMethod = type.getMethod("publicMethod", stringType);
        assertThat(publicMethod.getReturnType()).isEqualTo(String.class);
        assertThat(type.getDeclaredMethods()).extracting(Method::getName)
                .contains("privateMethod", "publicMethod")
                .doesNotContain("ajc$hiddenMethod");
        assertThat(type.getMethods()).extracting(Method::getName).contains("publicMethod");
    }

    @Test
    void exposesAspectDeclarationsAndAnnotationStyleIntroductions() throws Exception {
        AjType<AnnotationStyleAspect> aspectType = AjTypeSystem.getAjType(AnnotationStyleAspect.class);
        AjType<TargetType> targetType = AjTypeSystem.getAjType(TargetType.class);
        AjType<String> stringType = AjTypeSystem.getAjType(String.class);

        assertThat(aspectType.isAspect()).isTrue();
        assertThat(aspectType.getDeclaredPointcuts()).extracting(pointcut -> pointcut.getName()).contains("anyOperation");
        assertThat(aspectType.getPointcuts()).extracting(pointcut -> pointcut.getName()).contains("anyOperation");

        Advice[] declaredAdvice = aspectType.getDeclaredAdvice();
        assertThat(declaredAdvice).extracting(Advice::getName).contains("beforeAnyOperation");
        assertThat(aspectType.getAdvice(AdviceKind.BEFORE)).extracting(Advice::getName).contains("beforeAnyOperation");
        assertThat(aspectType.getDeclaredAdvice("beforeAnyOperation").getKind()).isEqualTo(AdviceKind.BEFORE);
        assertThat(aspectType.getAdvice("beforeAnyOperation").getKind()).isEqualTo(AdviceKind.BEFORE);

        InterTypeMethodDeclaration[] declaredIntroducedMethods = aspectType.getDeclaredITDMethods();
        assertThat(declaredIntroducedMethods).extracting(InterTypeMethodDeclaration::getName)
                .contains("wovenMethod", "mixinMethod");
        assertThat(aspectType.getITDMethods()).extracting(InterTypeMethodDeclaration::getName)
                .contains("wovenMethod", "mixinMethod");
        InterTypeMethodDeclaration introducedMethod = aspectType.getDeclaredITDMethod("wovenMethod", targetType, stringType);
        assertThat(introducedMethod.getReturnType().getJavaClass()).isEqualTo(String.class);
        assertThat(aspectType.getITDMethod("wovenMethod", targetType, stringType).getName()).isEqualTo("wovenMethod");

        InterTypeConstructorDeclaration[] declaredConstructors = aspectType.getDeclaredITDConstructors();
        assertThat(declaredConstructors).hasSize(1);
        assertThat(aspectType.getITDConstructors()).hasSize(1);
        assertThat(aspectType.getDeclaredITDConstructor(targetType, stringType).getParameterTypes()).hasSize(1);
        assertThat(aspectType.getITDConstructor(targetType, stringType).getParameterTypes()).hasSize(1);

        InterTypeFieldDeclaration[] declaredFields = aspectType.getDeclaredITDFields();
        assertThat(declaredFields).extracting(InterTypeFieldDeclaration::getName).contains("wovenField");
        assertThat(aspectType.getITDFields()).extracting(InterTypeFieldDeclaration::getName).contains("wovenField");
        assertThat(aspectType.getDeclaredITDField("wovenField", targetType).getType().getJavaClass()).isEqualTo(int.class);
        assertThat(aspectType.getITDField("wovenField", targetType).getType().getJavaClass()).isEqualTo(int.class);

        DeclareErrorOrWarning[] declareErrorOrWarnings = aspectType.getDeclareErrorOrWarnings();
        assertThat(declareErrorOrWarnings).extracting(DeclareErrorOrWarning::getMessage)
                .contains("warning from field", "error from field", "warning from method");
        assertThat(declareErrorOrWarnings).extracting(DeclareErrorOrWarning::isError).contains(true, false);

        assertThat(aspectType.getDeclareParents()).hasSizeGreaterThanOrEqualTo(2);
        assertThat(aspectType.getDeclareSofts()).hasSize(1);
        DeclareAnnotation[] declareAnnotations = aspectType.getDeclareAnnotations();
        assertThat(declareAnnotations).hasSize(1);
        assertThat(aspectType.getDeclarePrecedence()).hasSize(2);
    }

    private static String[] javaClassNames(AjType<?>[] types) {
        return Arrays.stream(types)
                .map(AjType::getJavaClass)
                .map(Class::getName)
                .toArray(String[]::new);
    }

    public static class SampleType {
        public String publicField = "public";
        public String ajc$hiddenField = "synthetic";
        private String privateField = "private";

        public SampleType() {
        }

        private SampleType(String value) {
            privateField = value;
        }

        public String publicMethod(String value) {
            return value + publicField;
        }

        private String privateMethod() {
            return privateField;
        }

        public void ajc$hiddenMethod() {
        }

        public static class PublicNested {
        }

        static class PackageNested {
        }
    }

    public interface MixinContract {
        String mixinMethod(String value);
    }

    public static class MixinDefault implements MixinContract {
        @Override
        public String mixinMethod(String value) {
            return value;
        }
    }

    public static class TargetType {
        private int wovenField;
    }

    @Aspect
    @DeclarePrecedence("org_aspectj.aspectjrt..*, *")
    public static class AnnotationStyleAspect {
        @DeclareWarning("execution(* *(..))")
        public static String warningMessage = "warning from field";

        @DeclareError("execution(* *(..))")
        public static String errorMessage = "error from field";

        @DeclareParents(value = "org_aspectj.aspectjrt..*", defaultImpl = MixinDefault.class)
        public MixinContract mixin;

        @Pointcut("execution(* *(..))")
        public void anyOperation() {
        }

        @Before("anyOperation()")
        public void beforeAnyOperation() {
        }

        @ajcITD(modifiers = Modifier.PUBLIC, targetType = TARGET_TYPE_NAME, name = "wovenMethod")
        public static String ajc$interMethod$org_aspectj_aspectjrt_TargetType$wovenMethod(TargetType target, String value) {
            return value;
        }

        @ajcITD(modifiers = Modifier.PUBLIC, targetType = TARGET_TYPE_NAME, name = "wovenMethod")
        public static String ajc$interMethodDispatch1$org_aspectj_aspectjrt_TargetType$wovenMethod(TargetType target, String value) {
            return value;
        }

        @ajcITD(modifiers = Modifier.PUBLIC, targetType = TARGET_TYPE_NAME, name = "wovenField")
        public static void ajc$interFieldInit$org_aspectj_aspectjrt_TargetType$wovenField(TargetType target) {
            target.wovenField = 7;
        }

        public static int ajc$interFieldGetDispatch$org_aspectj_aspectjrt_TargetType$wovenField(TargetType target) {
            return target.wovenField;
        }

        @ajcITD(modifiers = Modifier.PUBLIC, targetType = TARGET_TYPE_NAME, name = "new")
        public static void ajc$postInterConstructor$org_aspectj_aspectjrt_TargetType(TargetType target, String value) {
            target.wovenField = value.length();
        }

        @ajcDeclareEoW(message = "warning from method", pointcut = "execution(* *(..))", isError = false)
        public static void declareWarningFromMethod() {
        }

        @ajcDeclareParents(targetTypePattern = "org_aspectj.aspectjrt..*", parentTypes = "java.io.Serializable", isExtends = false)
        public static void declareSerializableParent() {
        }

        @ajcDeclareSoft(exceptionType = "java.io.IOException", pointcut = "execution(* *(..))")
        public static void declareSoftIOException() throws IOException {
        }

        @Deprecated
        @ajcDeclareAnnotation(kind = "at_method", pattern = "* *(..)", annotation = "@java.lang.Deprecated")
        public static void declareDeprecatedAnnotation() {
        }

        @ajcDeclarePrecedence("org_aspectj.aspectjrt..*, *")
        public static void declareAdditionalPrecedence() {
        }
    }

}
