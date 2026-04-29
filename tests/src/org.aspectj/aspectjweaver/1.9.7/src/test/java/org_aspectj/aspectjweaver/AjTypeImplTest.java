/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_aspectj.aspectjweaver;

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
import org.aspectj.lang.reflect.DeclareErrorOrWarning;
import org.aspectj.lang.reflect.InterTypeConstructorDeclaration;
import org.aspectj.lang.reflect.InterTypeFieldDeclaration;
import org.aspectj.lang.reflect.InterTypeMethodDeclaration;
import org.junit.jupiter.api.Test;

public class AjTypeImplTest {
    @Test
    void exposesJavaMembersThroughAjTypeApi() throws Exception {
        AjType<PlainFixture> type = AjTypeSystem.getAjType(PlainFixture.class);
        AjType<?> stringType = AjTypeSystem.getAjType(String.class);
        AjType<?> intType = AjTypeSystem.getAjType(int.class);

        assertThat(type.getAjTypes())
                .extracting(ajType -> ajType.getJavaClass().getName())
                .contains(PlainFixture.PublicNested.class.getName());
        assertThat(type.getDeclaredAjTypes())
                .extracting(ajType -> ajType.getJavaClass().getName())
                .contains(PlainFixture.PublicNested.class.getName(), PlainFixture.PrivateNested.class.getName());

        Constructor<?> publicConstructor = type.getConstructor(stringType);
        assertThat(publicConstructor.getParameterCount()).isEqualTo(1);
        assertThat(type.getConstructors()).isNotEmpty();
        Constructor<?> privateConstructor = type.getDeclaredConstructor(intType);
        assertThat(privateConstructor.getParameterCount()).isEqualTo(1);
        assertThat(type.getDeclaredConstructors()).isNotEmpty();

        Field privateField = type.getDeclaredField("privateField");
        assertThat(privateField.getName()).isEqualTo("privateField");
        assertThat(type.getDeclaredFields())
                .extracting(Field::getName)
                .contains("privateField", "publicField");
        Field publicField = type.getField("publicField");
        assertThat(publicField.getName()).isEqualTo("publicField");
        assertThat(type.getFields())
                .extracting(Field::getName)
                .contains("publicField");

        Method privateMethod = type.getDeclaredMethod("privateMethod", stringType);
        assertThat(privateMethod.getName()).isEqualTo("privateMethod");
        Method publicMethod = type.getMethod("publicMethod", intType);
        assertThat(publicMethod.getName()).isEqualTo("publicMethod");
        assertThat(type.getDeclaredMethods())
                .extracting(Method::getName)
                .contains("privateMethod", "publicMethod");
        assertThat(type.getMethods())
                .extracting(Method::getName)
                .contains("publicMethod");
    }

    @Test
    void discoversAnnotationStyleAspectDeclarations() throws Exception {
        AjType<AnnotationStyleAspect> aspect = AjTypeSystem.getAjType(AnnotationStyleAspect.class);

        assertThat(aspect.isAspect()).isTrue();
        assertThat(aspect.getDeclaredPointcuts())
                .extracting(pointcut -> pointcut.getName())
                .contains("serviceOperation");
        assertThat(aspect.getPointcuts())
                .extracting(pointcut -> pointcut.getName())
                .contains("serviceOperation");
        assertThat(aspect.getDeclaredPointcut("serviceOperation").getName())
                .isEqualTo("serviceOperation");
        assertThat(aspect.getPointcut("serviceOperation").getName())
                .isEqualTo("serviceOperation");

        Advice[] declaredAdvice = aspect.getDeclaredAdvice();
        assertThat(declaredAdvice).isNotEmpty();
        assertThat(aspect.getAdvice()).isNotEmpty();

        assertThat(aspect.getDeclaredITDMethods())
                .extracting(InterTypeMethodDeclaration::getName)
                .contains("introducedMethod");
        assertThat(aspect.getITDMethods())
                .extracting(InterTypeMethodDeclaration::getName)
                .contains("introducedMethod");

        assertThat(aspect.getDeclareErrorOrWarnings())
                .extracting(DeclareErrorOrWarning::getMessage)
                .contains("avoid this call", "forbidden call");
        assertThat(aspect.getDeclareParents()).isNotEmpty();
        assertThat(aspect.getDeclareSofts()).isEmpty();
        assertThat(aspect.getDeclareAnnotations()).isEmpty();
        assertThat(aspect.getDeclarePrecedence()).isNotEmpty();
    }

    @Test
    void discoversCompilerStyleAspectDeclarations() throws Exception {
        AjType<CompilerStyleAspect> aspect = AjTypeSystem.getAjType(CompilerStyleAspect.class);
        AjType<PlainFixture> target = AjTypeSystem.getAjType(PlainFixture.class);
        AjType<?> stringType = AjTypeSystem.getAjType(String.class);

        assertThat(aspect.getDeclaredITDMethods())
                .extracting(InterTypeMethodDeclaration::getName)
                .contains("compilerIntroducedMethod");
        assertThat(aspect.getDeclaredITDMethod("compilerIntroducedMethod", target, stringType).getName())
                .isEqualTo("compilerIntroducedMethod");
        assertThat(aspect.getITDMethods())
                .extracting(InterTypeMethodDeclaration::getName)
                .contains("compilerIntroducedMethod");
        assertThat(aspect.getITDMethod("compilerIntroducedMethod", target, stringType).getName())
                .isEqualTo("compilerIntroducedMethod");

        InterTypeConstructorDeclaration[] declaredConstructors = aspect.getDeclaredITDConstructors();
        assertThat(declaredConstructors).isNotEmpty();
        assertThat(declaredConstructors[0].getTargetType()).isEqualTo(target);
        InterTypeConstructorDeclaration[] publicConstructors = aspect.getITDConstructors();
        assertThat(publicConstructors).isNotEmpty();
        assertThat(publicConstructors[0].getTargetType()).isEqualTo(target);

        assertThat(aspect.getDeclaredITDFields())
                .extracting(InterTypeFieldDeclaration::getName)
                .contains("compilerIntroducedField");
        assertThat(aspect.getDeclaredITDField("compilerIntroducedField", target).getName())
                .isEqualTo("compilerIntroducedField");
        assertThat(aspect.getITDFields())
                .extracting(InterTypeFieldDeclaration::getName)
                .contains("compilerIntroducedField");
        assertThat(aspect.getITDField("compilerIntroducedField", target).getName())
                .isEqualTo("compilerIntroducedField");

        assertThat(aspect.getDeclareErrorOrWarnings())
                .extracting(DeclareErrorOrWarning::getMessage)
                .contains("compiler warning");
        assertThat(aspect.getDeclareParents()).isNotEmpty();
        assertThat(aspect.getDeclareSofts()).isNotEmpty();
        assertThat(aspect.getDeclareAnnotations()).isNotEmpty();
        assertThat(aspect.getDeclarePrecedence()).isNotEmpty();
    }

    public static class PlainFixture {
        public String publicField = "public";
        private String privateField = "private";

        public static class PublicNested {
        }

        private static class PrivateNested {
        }

        public PlainFixture() {
        }

        public PlainFixture(String value) {
            this.publicField = value;
        }

        private PlainFixture(int value) {
            this.privateField = String.valueOf(value);
        }

        public String publicMethod(int value) {
            return publicField + value;
        }

        private String privateMethod(String value) {
            return privateField + value;
        }
    }

    public interface IntroducedOperations {
        String introducedMethod();
    }

    public static class IntroducedOperationsImpl implements IntroducedOperations {
        @Override
        public String introducedMethod() {
            return "introduced";
        }
    }

    @Aspect
    @DeclarePrecedence("org_aspectj.aspectjweaver..*, *")
    public static class AnnotationStyleAspect {
        @DeclareParents(
                value = "org_aspectj.aspectjweaver.AjTypeImplTest.PlainFixture",
                defaultImpl = IntroducedOperationsImpl.class)
        public static IntroducedOperations introducedOperations;

        @DeclareWarning("call(* org_aspectj.aspectjweaver..*(..))")
        public static String warningMessage = "avoid this call";

        @DeclareError("call(* org_aspectj.aspectjweaver..forbidden*(..))")
        public static String errorMessage = "forbidden call";

        @Pointcut("execution(* org_aspectj.aspectjweaver..*(..))")
        public void serviceOperation() {
        }

        @Before("serviceOperation()")
        public void beforeServiceOperation() {
        }
    }

    @Retention(RetentionPolicy.RUNTIME)
    public @interface Marker {
    }

    @Aspect
    public static class CompilerStyleAspect {
        @ajcITD(
                modifiers = Modifier.PUBLIC,
                targetType = "org_aspectj.aspectjweaver.AjTypeImplTest$PlainFixture",
                name = "compilerIntroducedMethod")
        public static String ajc$interMethod$CompilerStyleAspect$PlainFixture$compilerIntroducedMethod(
                PlainFixture target,
                String value) {
            return target.publicMethod(value.length());
        }

        @ajcITD(
                modifiers = Modifier.PUBLIC,
                targetType = "org_aspectj.aspectjweaver.AjTypeImplTest$PlainFixture",
                name = "compilerIntroducedMethod")
        public static String ajc$interMethodDispatch1$CompilerStyleAspect$PlainFixture$compilerIntroducedMethod(
                PlainFixture target,
                String value) {
            return ajc$interMethod$CompilerStyleAspect$PlainFixture$compilerIntroducedMethod(target, value);
        }

        @ajcITD(
                modifiers = Modifier.PUBLIC,
                targetType = "org_aspectj.aspectjweaver.AjTypeImplTest$PlainFixture",
                name = "compilerIntroducedField")
        public static void ajc$interFieldInit$CompilerStyleAspect$PlainFixture$compilerIntroducedField(
                PlainFixture target) {
            target.publicField = "field";
        }

        public static String ajc$interFieldGetDispatch$CompilerStyleAspect$PlainFixture$compilerIntroducedField(
                PlainFixture target) {
            return target.publicField;
        }

        @ajcITD(
                modifiers = Modifier.PUBLIC,
                targetType = "org_aspectj.aspectjweaver.AjTypeImplTest$PlainFixture",
                name = "compilerIntroducedConstructor")
        public static void ajc$postInterConstructor$CompilerStyleAspect$PlainFixture(PlainFixture target) {
            target.publicField = "constructed";
        }

        @ajcDeclareEoW(
                pointcut = "call(* org_aspectj.aspectjweaver..*(..))",
                message = "compiler warning",
                isError = false)
        public static void ajc$declareWarning() {
        }

        @ajcDeclareParents(
                targetTypePattern = "org_aspectj.aspectjweaver..*",
                parentTypes = "org_aspectj.aspectjweaver.AjTypeImplTest$IntroducedOperations",
                isExtends = false)
        public static void ajc$declareParents() {
        }

        @ajcDeclareSoft(
                exceptionType = "java.lang.IllegalStateException",
                pointcut = "call(* org_aspectj.aspectjweaver..*(..))")
        public static void ajc$declareSoft() {
        }

        @Marker
        @ajcDeclareAnnotation(
                kind = "at_type",
                pattern = "org_aspectj.aspectjweaver..*",
                annotation = "@org_aspectj.aspectjweaver.AjTypeImplTest.Marker")
        public static void ajc$declareAnnotation() {
        }

        @ajcDeclarePrecedence("org_aspectj.aspectjweaver..*, *")
        public static void ajc$declarePrecedence() {
        }
    }
}
