/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_aspectj.aspectjweaver;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.reflect.Method;

import org.aspectj.lang.annotation.AfterThrowing;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.weaver.ResolvedType;
import org.aspectj.weaver.UnresolvedType;
import org.aspectj.weaver.reflect.Java15AnnotationFinder;
import org.aspectj.weaver.reflect.ReflectionWorld;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class Java15AnnotationFinderTest {
    @Test
    void findsAnnotationOnObjectClass() {
        Java15AnnotationFinder finderAnnotationAccess = annotationFinder();
        ResolvedType markerType = markerType();

        Object annotation = finderAnnotationAccess.getAnnotation(markerType, new Java15AnnotationFinderAnnotatedComponent());

        assertThat(annotation).isInstanceOf(Java15AnnotationFinderMarker.class);
        assertThat(((Java15AnnotationFinderMarker) annotation).value()).isEqualTo("component");
    }

    @Test
    void findsAnnotationFromClass() {
        Java15AnnotationFinder finder = annotationFinder();
        ResolvedType markerType = markerType();

        Object annotation = finder.getAnnotationFromClass(markerType, Java15AnnotationFinderAnnotatedComponent.class);

        assertThat(annotation).isInstanceOf(Java15AnnotationFinderMarker.class);
        assertThat(((Java15AnnotationFinderMarker) annotation).value()).isEqualTo("component");
    }

    @Test
    void findsAnnotationFromMember() throws NoSuchMethodException {
        Java15AnnotationFinder finder = annotationFinder();
        ResolvedType markerType = markerType();
        Method method = Java15AnnotationFinderAnnotatedComponent.class.getMethod("annotatedOperation");

        Object annotation = finder.getAnnotationFromMember(markerType, method);

        assertThat(annotation).isInstanceOf(Java15AnnotationFinderMarker.class);
        assertThat(((Java15AnnotationFinderMarker) annotation).value()).isEqualTo("operation");
    }

    @Test
    void resolvesAdviceAndPointcutAnnotationsByName() throws NoSuchMethodException {
        Java15AnnotationFinder finder = annotationFinder();
        ReflectionWorld world = new ReflectionWorld(Java15AnnotationFinderTest.class.getClassLoader());
        Class<AjTypeImplTest.ReflectiveAspect> aspectClass = AjTypeImplTest.ReflectiveAspect.class;

        Aspect aspect = (Aspect) finder.getAnnotationFromClass(
                world.resolve(Aspect.class.getName()), aspectClass);
        Pointcut pointcut = (Pointcut) finder.getAnnotationFromMember(
                world.resolve(Pointcut.class.getName()), aspectClass.getMethod("publicPointcut"));
        Around around = (Around) finder.getAnnotationFromMember(
                world.resolve(Around.class.getName()), aspectClass.getDeclaredMethod("aroundAdvice"));
        AfterThrowing afterThrowing = (AfterThrowing) finder.getAnnotationFromMember(
                world.resolve(AfterThrowing.class.getName()),
                aspectClass.getDeclaredMethod("afterThrowingAdvice", Throwable.class));

        assertThat(aspect.value()).isEmpty();
        assertThat(pointcut.value()).isEqualTo("execution(* *(..))");
        assertThat(around.value()).isEqualTo("publicPointcut()");
        assertThat(afterThrowing.pointcut()).isEqualTo("publicPointcut()");
        assertThat(afterThrowing.throwing()).isEqualTo("throwable");
    }

    private static Java15AnnotationFinder annotationFinder() {
        ClassLoader classLoader = Java15AnnotationFinderTest.class.getClassLoader();
        ReflectionWorld world = new ReflectionWorld(classLoader);
        Java15AnnotationFinder finder = new Java15AnnotationFinder();
        finder.setClassLoader(classLoader);
        finder.setWorld(world);
        return finder;
    }

    private static ResolvedType markerType() {
        ReflectionWorld world = new ReflectionWorld(Java15AnnotationFinderTest.class.getClassLoader());
        return UnresolvedType.forName(Java15AnnotationFinderMarker.class.getName()).resolve(world);
    }
}

@Retention(RetentionPolicy.RUNTIME)
@interface Java15AnnotationFinderMarker {
    String value();
}

@Java15AnnotationFinderMarker("component")
class Java15AnnotationFinderAnnotatedComponent {
    @Java15AnnotationFinderMarker("operation")
    public void annotatedOperation() {
    }
}
