/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_aspectj.aspectjrt;

import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import org.aspectj.internal.lang.annotation.ajcITD;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.aspectj.lang.annotation.DeclareError;
import org.aspectj.lang.annotation.DeclarePrecedence;
import org.aspectj.lang.annotation.DeclareWarning;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.lang.reflect.AdviceKind;
import org.aspectj.lang.reflect.AjType;
import org.aspectj.lang.reflect.AjTypeSystem;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class AjTypeImplTest {

    @Test
    void exposesJavaClassMembersThroughAjTypeApi() throws Exception {
        AjType<ReflectiveSubject> type = AjTypeSystem.getAjType(ReflectiveSubject.class);
        AjType<String> stringType = AjTypeSystem.getAjType(String.class);

        assertThat(names(type.getAjTypes())).contains(ReflectiveSubject.PublicMember.class.getName());
        assertThat(names(type.getDeclaredAjTypes())).contains(
                ReflectiveSubject.PublicMember.class.getName(),
                ReflectiveSubject.PrivateMember.class.getName()
        );
        assertThat(type.getConstructor().getDeclaringClass()).isEqualTo(ReflectiveSubject.class);
        assertThat(type.getConstructors()).extracting(constructor -> constructor.getDeclaringClass())
                .contains(ReflectiveSubject.class);
        assertThat(type.getDeclaredConstructor(stringType).getParameterTypes()).containsExactly(String.class);
        assertThat(type.getDeclaredConstructors()).extracting(constructor -> constructor.getDeclaringClass())
                .contains(ReflectiveSubject.class);
        assertThat(type.getDeclaredField("privateValue").getName()).isEqualTo("privateValue");
        assertThat(type.getDeclaredFields()).extracting(field -> field.getName())
                .contains("publicValue", "privateValue");
        assertThat(type.getField("publicValue").getDeclaringClass()).isEqualTo(ReflectiveSubject.class);
        assertThat(type.getFields()).extracting(field -> field.getName()).contains("publicValue");
        assertThat(type.getDeclaredMethod("privateOperation", stringType).getName()).isEqualTo("privateOperation");
        assertThat(type.getMethod("publicOperation", stringType).getReturnType()).isEqualTo(String.class);
        assertThat(type.getDeclaredMethods()).extracting(method -> method.getName())
                .contains("publicOperation", "privateOperation");
        assertThat(type.getMethods()).extracting(method -> method.getName()).contains("publicOperation");
    }

    @Test
    void exposesAspectPointcutsAdviceAndDeclareStatements() throws Exception {
        AjType<AnnotationStyleAspect> type = AjTypeSystem.getAjType(AnnotationStyleAspect.class);

        assertThat(type.getDeclaredPointcut("subjectOperation").getName()).isEqualTo("subjectOperation");
        assertThat(type.getPointcut("subjectOperation").getPointcutExpression().asString())
                .isEqualTo("execution(* org_aspectj.aspectjrt.AjTypeImplTest.ReflectiveSubject.*(..))");
        assertThat(type.getDeclaredAdvice(AdviceKind.BEFORE)).extracting(advice -> advice.getName())
                .contains("beforeSubjectOperation");
        assertThat(type.getAdvice(AdviceKind.AFTER_RETURNING)).extracting(advice -> advice.getName())
                .contains("afterReturningSubjectOperation");
        assertThat(type.getDeclaredAdvice("beforeSubjectOperation").getKind()).isEqualTo(AdviceKind.BEFORE);
        assertThat(type.getAdvice("afterReturningSubjectOperation").getKind()).isEqualTo(AdviceKind.AFTER_RETURNING);
        assertThat(type.getDeclareErrorOrWarnings())
                .extracting(declareErrorOrWarning -> declareErrorOrWarning.getMessage())
                .contains("subject warning", "subject error");
        assertThat(type.getDeclareParents())
                .extracting(declareParents -> declareParents.getTargetTypesPattern().asString())
                .contains("org_aspectj.aspectjrt.*");
        assertThat(type.getDeclareSofts()).isEmpty();
        assertThat(type.getDeclareAnnotations()).isEmpty();
        assertThat(type.getDeclarePrecedence())
                .extracting(declarePrecedence -> declarePrecedence.getPrecedenceOrder()[0].asString())
                .contains("org_aspectj.aspectjrt.AjTypeImplTest.AnnotationStyleAspect");
    }

    @Test
    void exposesAnnotationAndCompilerStyleInterTypeDeclarations() throws Exception {
        AjType<AnnotationStyleAspect> annotationStyleType = AjTypeSystem.getAjType(AnnotationStyleAspect.class);
        AjType<InterTypeAspect> compilerStyleType = AjTypeSystem.getAjType(InterTypeAspect.class);
        AjType<InterTypeTarget> targetType = AjTypeSystem.getAjType(InterTypeTarget.class);

        assertThat(annotationStyleType.getDeclaredITDMethods()).extracting(method -> method.getName())
                .contains("introducedOperation");
        assertThat(annotationStyleType.getITDMethods()).extracting(method -> method.getName())
                .contains("introducedOperation");
        assertThat(compilerStyleType.getDeclaredITDMethods()).extracting(method -> method.getName())
                .contains("hiddenIntroducedOperation");
        assertThat(compilerStyleType.getITDMethods()).extracting(method -> method.getName())
                .contains("introducedOperation");
        assertThat(compilerStyleType.getDeclaredITDMethod("hiddenIntroducedOperation", targetType).getName())
                .isEqualTo("hiddenIntroducedOperation");
        assertThat(compilerStyleType.getITDMethod("introducedOperation", targetType).getName())
                .isEqualTo("introducedOperation");
        assertThat(compilerStyleType.getDeclaredITDFields()).extracting(field -> field.getName())
                .contains("introducedNumber");
        assertThat(compilerStyleType.getITDFields()).extracting(field -> field.getName()).contains("introducedNumber");
        assertThat(compilerStyleType.getDeclaredITDField("introducedNumber", targetType).getName())
                .isEqualTo("introducedNumber");
        assertThat(compilerStyleType.getITDField("introducedNumber", targetType).getName())
                .isEqualTo("introducedNumber");
        assertThat(compilerStyleType.getDeclaredITDConstructors()).hasSize(1);
        assertThat(compilerStyleType.getITDConstructors()).hasSize(1);
        assertThat(compilerStyleType.getDeclaredITDConstructor(targetType)).isNotNull();
        assertThat(compilerStyleType.getITDConstructor(targetType)).isNotNull();
    }

    private static List<String> names(AjType<?>[] types) {
        return Arrays.stream(types).map(AjType::getName).collect(Collectors.toList());
    }

    public static class ReflectiveSubject {
        public String publicValue = "public";
        private String privateValue = "private";

        public ReflectiveSubject() {
        }

        private ReflectiveSubject(String privateValue) {
            this.privateValue = privateValue;
        }

        public String publicOperation(String value) {
            return publicValue + value;
        }

        private String privateOperation(String value) {
            return privateValue + value;
        }

        public static class PublicMember {
        }

        private static class PrivateMember {
        }
    }

    public interface IntroducedInterface {
        void introducedOperation();
    }

    public static class IntroducedDefault implements IntroducedInterface {
        @Override
        public void introducedOperation() {
        }
    }

    @Aspect
    @DeclarePrecedence("org_aspectj.aspectjrt.AjTypeImplTest.AnnotationStyleAspect, *")
    public static class AnnotationStyleAspect {
        @DeclareWarning("execution(* org_aspectj.aspectjrt.AjTypeImplTest.ReflectiveSubject.*(..))")
        public static final String SUBJECT_WARNING = "subject warning";

        @DeclareError("execution(* org_aspectj.aspectjrt.AjTypeImplTest.ReflectiveSubject.privateOperation(..))")
        public static final String SUBJECT_ERROR = "subject error";

        @org.aspectj.lang.annotation.DeclareParents(
                value = "org_aspectj.aspectjrt.*",
                defaultImpl = IntroducedDefault.class
        )
        public static IntroducedInterface introducedInterface;

        @Pointcut("execution(* org_aspectj.aspectjrt.AjTypeImplTest.ReflectiveSubject.*(..))")
        public void subjectOperation() {
        }

        @Before("subjectOperation()")
        public void beforeSubjectOperation() {
        }

        @AfterReturning("subjectOperation()")
        public void afterReturningSubjectOperation() {
        }
    }

    public static class InterTypeTarget {
    }

    @Aspect
    public static class InterTypeAspect {
        // Checkstyle: stop method name check
        @ajcITD(
                modifiers = Modifier.PRIVATE,
                targetType = "org_aspectj.aspectjrt.AjTypeImplTest$InterTypeTarget",
                name = "hiddenIntroducedOperation"
        )
        private static void ajc$interMethodDispatch1$hiddenIntroducedOperation(InterTypeTarget target) {
        }

        @ajcITD(
                modifiers = Modifier.PUBLIC,
                targetType = "org_aspectj.aspectjrt.AjTypeImplTest$InterTypeTarget",
                name = "introducedOperation"
        )
        public static void ajc$interMethod$introducedOperation(InterTypeTarget target) {
        }

        @ajcITD(
                modifiers = Modifier.PUBLIC,
                targetType = "org_aspectj.aspectjrt.AjTypeImplTest$InterTypeTarget",
                name = "introducedNumber"
        )
        public static void ajc$interFieldInit$introducedNumber(InterTypeTarget target) {
        }

        public static int ajc$interFieldGetDispatch$introducedNumber(InterTypeTarget target) {
            return 42;
        }

        @ajcITD(
                modifiers = Modifier.PUBLIC,
                targetType = "org_aspectj.aspectjrt.AjTypeImplTest$InterTypeTarget",
                name = "new"
        )
        public static void ajc$postInterConstructor(InterTypeTarget target) {
        }
        // Checkstyle: resume method name check
    }
}
