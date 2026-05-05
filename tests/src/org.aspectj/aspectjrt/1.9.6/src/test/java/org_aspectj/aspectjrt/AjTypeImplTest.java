/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_aspectj.aspectjrt;

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
import org.aspectj.lang.reflect.AjType;
import org.aspectj.lang.reflect.AjTypeSystem;
import org.aspectj.lang.reflect.DeclareAnnotation;
import org.aspectj.lang.reflect.DeclareErrorOrWarning;
import org.aspectj.lang.reflect.InterTypeConstructorDeclaration;
import org.aspectj.lang.reflect.InterTypeFieldDeclaration;
import org.aspectj.lang.reflect.InterTypeMethodDeclaration;
import org.aspectj.lang.reflect.PointcutExpression;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

import static org.assertj.core.api.Assertions.assertThat;

public class AjTypeImplTest {
    @Test
    void reflectsJavaMembersThroughAjType() throws Exception {
        AjType<ReflectiveSubject> subjectType = AjTypeSystem.getAjType(ReflectiveSubject.class);
        AjType<String> stringType = AjTypeSystem.getAjType(String.class);
        AjType<Integer> integerType = AjTypeSystem.getAjType(int.class);

        assertThat(subjectType.getAjTypes()).extracting(AjType::getName)
                .contains(ReflectiveSubject.PublicMember.class.getName());
        assertThat(subjectType.getDeclaredAjTypes()).extracting(AjType::getName)
                .contains(
                        ReflectiveSubject.PublicMember.class.getName(),
                        ReflectiveSubject.PrivateMember.class.getName());

        Constructor<?> publicConstructor = subjectType.getConstructor();
        assertThat(publicConstructor.getParameterCount()).isZero();
        assertThat(subjectType.getConstructors()).extracting(Constructor::getName)
                .contains(ReflectiveSubject.class.getName());
        Constructor<?> declaredConstructor = subjectType.getDeclaredConstructor(stringType);
        assertThat(declaredConstructor.getParameterTypes()).containsExactly(String.class);
        assertThat(subjectType.getDeclaredConstructors()).extracting(Constructor::getParameterCount)
                .contains(0, 1);

        Field declaredField = subjectType.getDeclaredField("privateField");
        assertThat(declaredField.getName()).isEqualTo("privateField");
        assertThat(subjectType.getDeclaredFields()).extracting(Field::getName)
                .contains("publicField", "privateField");
        assertThat(subjectType.getField("publicField").getType()).isEqualTo(String.class);
        assertThat(subjectType.getFields()).extracting(Field::getName).contains("publicField");

        Method declaredMethod = subjectType.getDeclaredMethod("privateMethod", stringType);
        assertThat(declaredMethod.getReturnType()).isEqualTo(String.class);
        assertThat(subjectType.getMethod("publicMethod", integerType).getReturnType()).isEqualTo(void.class);
        assertThat(subjectType.getDeclaredMethods()).extracting(Method::getName)
                .contains("publicMethod", "privateMethod");
        assertThat(subjectType.getMethods()).extracting(Method::getName).contains("publicMethod");
    }

    @Test
    void discoversAspectSpecificMembersThroughAjType() throws Exception {
        AjType<SampleAspect> aspectType = AjTypeSystem.getAjType(SampleAspect.class);
        AjType<TargetType> targetType = AjTypeSystem.getAjType(TargetType.class);

        assertThat(aspectType.isAspect()).isTrue();
        assertThat(aspectType.getDeclaredPointcuts()).extracting(org.aspectj.lang.reflect.Pointcut::getName)
                .contains("publicOperation");
        assertThat(aspectType.getPointcuts()).extracting(org.aspectj.lang.reflect.Pointcut::getName)
                .contains("publicOperation");
        assertThat(aspectType.getDeclaredPointcut("publicOperation").getPointcutExpression())
                .extracting(PointcutExpression::asString)
                .isEqualTo("execution(public * *(..))");
        assertThat(aspectType.getPointcut("publicOperation").getName()).isEqualTo("publicOperation");

        assertThat(aspectType.getDeclaredAdvice()).extracting(Advice::getName).contains("beforePublicMethod");
        assertThat(aspectType.getAdvice()).extracting(Advice::getName).contains("beforePublicMethod");
        assertThat(aspectType.getDeclaredAdvice("beforePublicMethod").getName()).isEqualTo("beforePublicMethod");
        assertThat(aspectType.getAdvice("beforePublicMethod").getName()).isEqualTo("beforePublicMethod");

        assertThat(aspectType.getDeclaredITDMethods()).extracting(InterTypeMethodDeclaration::getName)
                .contains("introducedDispatch", "mixinOperation");
        assertThat(aspectType.getITDMethods()).extracting(InterTypeMethodDeclaration::getName)
                .contains("introducedMethod", "mixinOperation");
        assertThat(aspectType.getDeclaredITDMethod("introducedDispatch", targetType, AjTypeSystem.getAjType(int.class))
                .getName()).isEqualTo("introducedDispatch");
        assertThat(aspectType.getITDMethod("introducedMethod", targetType, AjTypeSystem.getAjType(String.class))
                .getName()).isEqualTo("introducedMethod");

        InterTypeConstructorDeclaration[] declaredConstructors = aspectType.getDeclaredITDConstructors();
        assertThat(declaredConstructors).hasSize(1);
        assertThat(declaredConstructors[0].getTargetType()).isEqualTo(targetType);
        InterTypeConstructorDeclaration[] publicConstructors = aspectType.getITDConstructors();
        assertThat(publicConstructors).hasSize(1);
        assertThat(publicConstructors[0].getTargetType()).isEqualTo(targetType);
        assertThat(aspectType.getDeclaredITDConstructor(targetType, AjTypeSystem.getAjType(String.class))
                .getTargetType()).isEqualTo(targetType);
        assertThat(aspectType.getITDConstructor(targetType, AjTypeSystem.getAjType(String.class))
                .getTargetType()).isEqualTo(targetType);

        InterTypeFieldDeclaration declaredField = aspectType.getDeclaredITDField("introducedField", targetType);
        assertThat(declaredField.getType().getJavaClass()).isEqualTo(long.class);
        InterTypeFieldDeclaration publicField = aspectType.getITDField("introducedField", targetType);
        assertThat(publicField.getType().getJavaClass()).isEqualTo(long.class);

        assertThat(aspectType.getDeclareErrorOrWarnings()).extracting(DeclareErrorOrWarning::getMessage)
                .contains("warn from field", "error from field", "warn from method");
        assertThat(aspectType.getDeclareParents()).extracting(Object::toString)
                .anySatisfy(value -> assertThat(value).contains("AjTypeImplTest.TargetType"));
        assertThat(aspectType.getDeclareSofts()).hasSize(1);
        assertThat(aspectType.getDeclareSofts()[0].getSoftenedExceptionType().getJavaClass())
                .isEqualTo(Exception.class);
        assertThat(aspectType.getDeclareAnnotations()).extracting(DeclareAnnotation::getAnnotationAsText)
                .contains("@Deprecated");
        assertThat(aspectType.getDeclarePrecedence()).extracting(Object::toString)
                .anySatisfy(value -> assertThat(value).contains("first", "second"));
    }

    public static class ReflectiveSubject {
        public String publicField;
        private int privateField;

        public ReflectiveSubject() {
        }

        private ReflectiveSubject(String value) {
            this.publicField = value;
        }

        public void publicMethod(int value) {
            this.privateField = value;
        }

        private String privateMethod(String value) {
            return value + privateField;
        }

        public static class PublicMember {
        }

        private static class PrivateMember {
        }
    }

    public interface MixinContract {
        void mixinOperation();
    }

    public static class MixinImplementation implements MixinContract {
        @Override
        public void mixinOperation() {
        }
    }

    public static class TargetType {
    }

    @Aspect
    @DeclarePrecedence("first, second")
    public static class SampleAspect {
        @DeclareWarning("execution(* *(..))")
        public static final String WARNING = "warn from field";

        @DeclareError("execution(* *(..))")
        public static final String ERROR = "error from field";

        @DeclareParents(
                value = "org_aspectj.aspectjrt.AjTypeImplTest.TargetType",
                defaultImpl = MixinImplementation.class)
        public static MixinContract mixin;

        @Pointcut("execution(public * *(..))")
        public void publicOperation() {
        }

        @Before("publicOperation()")
        public void beforePublicMethod() {
        }

        @ajcITD(
                modifiers = Modifier.PUBLIC,
                targetType = "org_aspectj.aspectjrt.AjTypeImplTest$TargetType",
                name = "introducedMethod")
        public static String ajc$interMethod$TargetType$introducedMethod(TargetType target, String value) {
            return value;
        }

        @ajcITD(
                modifiers = Modifier.PUBLIC,
                targetType = "org_aspectj.aspectjrt.AjTypeImplTest$TargetType",
                name = "introducedDispatch")
        public static int ajc$interMethodDispatch1$TargetType$introducedDispatch(TargetType target, int value) {
            return value;
        }

        @ajcITD(
                modifiers = Modifier.PUBLIC,
                targetType = "org_aspectj.aspectjrt.AjTypeImplTest$TargetType",
                name = "introducedField")
        public static void ajc$interFieldInit$TargetType$introducedField(TargetType target) {
        }

        public static long ajc$interFieldGetDispatch$TargetType$introducedField(TargetType target) {
            return 42L;
        }

        @ajcITD(modifiers = Modifier.PUBLIC, targetType = "org_aspectj.aspectjrt.AjTypeImplTest$TargetType", name = "")
        public static void ajc$postInterConstructor$TargetType(TargetType target, String value) {
        }

        @ajcDeclareEoW(message = "warn from method", pointcut = "execution(* *(..))", isError = false)
        public static void ajc$declareEoW() {
        }

        @ajcDeclareParents(
                targetTypePattern = "org_aspectj.aspectjrt.AjTypeImplTest.TargetType",
                parentTypes = "java.io.Serializable",
                isExtends = false)
        public static void ajc$declareParents() {
        }

        @ajcDeclareSoft(exceptionType = "java.lang.Exception", pointcut = "execution(* *(..))")
        public static void ajc$declareSoft() {
        }

        @Deprecated
        @ajcDeclareAnnotation(kind = "at_method", pattern = "org_aspectj.aspectjrt.*", annotation = "@Deprecated")
        public static void ajc$declareAnnotation() {
        }

        @ajcDeclarePrecedence("first, second")
        public static void ajc$declarePrecedence() {
        }
    }
}
