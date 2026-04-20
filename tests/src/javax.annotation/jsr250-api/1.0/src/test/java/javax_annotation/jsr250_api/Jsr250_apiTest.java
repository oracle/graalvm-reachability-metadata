/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package javax_annotation.jsr250_api;

import static java.lang.annotation.ElementType.ANNOTATION_TYPE;
import static java.lang.annotation.ElementType.CONSTRUCTOR;
import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.LOCAL_VARIABLE;
import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.ElementType.PACKAGE;
import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.ElementType.TYPE;
import static org.assertj.core.api.Assertions.assertThat;

import java.lang.annotation.Annotation;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import javax.annotation.Generated;
import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.annotation.Resource;
import javax.annotation.Resources;
import javax.annotation.security.DeclareRoles;
import javax.annotation.security.DenyAll;
import javax.annotation.security.PermitAll;
import javax.annotation.security.RolesAllowed;
import javax.annotation.security.RunAs;
import org.junit.jupiter.api.Test;

class Jsr250_apiTest {

    @Test
    void resourceAnnotationsExposeDefaultsConfiguredValuesAndEnumConstants() throws Exception {
        Resource defaultResource = annotation(field(DefaultLifecycleComponent.class, "defaultResource"), Resource.class);
        Resource typeResource = annotation(ConfiguredResourceComponent.class, Resource.class);
        Resource fieldResource = annotation(field(ConfiguredResourceComponent.class, "queueName"), Resource.class);
        Resource methodResource = annotation(method(ConfiguredResourceComponent.class, "loadConfig"), Resource.class);
        Resources resources = annotation(ConfiguredResourceComponent.class, Resources.class);

        assertThat(defaultResource.name()).isEmpty();
        assertThat(defaultResource.type()).isEqualTo(Object.class);
        assertThat(defaultResource.authenticationType()).isEqualTo(Resource.AuthenticationType.CONTAINER);
        assertThat(defaultResource.shareable()).isTrue();
        assertThat(defaultResource.mappedName()).isEmpty();
        assertThat(defaultResource.description()).isEmpty();

        assertThat(typeResource.name()).isEqualTo("service/default");
        assertThat(typeResource.type()).isEqualTo(CharSequence.class);
        assertThat(typeResource.authenticationType()).isEqualTo(Resource.AuthenticationType.APPLICATION);
        assertThat(typeResource.shareable()).isFalse();
        assertThat(typeResource.mappedName()).isEqualTo("mapped/service/default");
        assertThat(typeResource.description()).isEqualTo("Type-level resource");

        assertThat(fieldResource.name()).isEqualTo("queue/orders");
        assertThat(fieldResource.type()).isEqualTo(String.class);
        assertThat(fieldResource.authenticationType()).isEqualTo(Resource.AuthenticationType.CONTAINER);
        assertThat(fieldResource.shareable()).isTrue();
        assertThat(fieldResource.mappedName()).isEqualTo("mapped/queue/orders");
        assertThat(fieldResource.description()).isEqualTo("Field resource");

        assertThat(methodResource.name()).isEqualTo("service/config");
        assertThat(methodResource.type()).isEqualTo(Integer.class);
        assertThat(methodResource.authenticationType()).isEqualTo(Resource.AuthenticationType.APPLICATION);
        assertThat(methodResource.shareable()).isFalse();
        assertThat(methodResource.mappedName()).isEqualTo("mapped/service/config");
        assertThat(methodResource.description()).isEqualTo("Method resource");

        assertThat(resources.value()).hasSize(2);
        assertThat(resources.value()[0].name()).isEqualTo("jdbc/primary");
        assertThat(resources.value()[0].type()).isEqualTo(CharSequence.class);
        assertThat(resources.value()[0].authenticationType()).isEqualTo(Resource.AuthenticationType.APPLICATION);
        assertThat(resources.value()[0].shareable()).isFalse();
        assertThat(resources.value()[0].mappedName()).isEqualTo("mapped/jdbc/primary");
        assertThat(resources.value()[0].description()).isEqualTo("Primary resource");
        assertThat(resources.value()[1].name()).isEqualTo("mail/session");
        assertThat(resources.value()[1].type()).isEqualTo(Object.class);
        assertThat(resources.value()[1].authenticationType()).isEqualTo(Resource.AuthenticationType.CONTAINER);
        assertThat(resources.value()[1].shareable()).isTrue();
        assertThat(resources.value()[1].mappedName()).isEmpty();
        assertThat(resources.value()[1].description()).isEqualTo("Mail session");

        assertThat(Resource.AuthenticationType.values())
                .containsExactly(Resource.AuthenticationType.CONTAINER, Resource.AuthenticationType.APPLICATION);
        assertThat(Resource.AuthenticationType.valueOf("APPLICATION"))
                .isEqualTo(Resource.AuthenticationType.APPLICATION);
    }

    @Test
    void lifecycleAndSecurityAnnotationsRetainConfiguredValues() throws Exception {
        DeclareRoles declareRoles = annotation(SecuredLifecycleComponent.class, DeclareRoles.class);
        RunAs runAs = annotation(SecuredLifecycleComponent.class, RunAs.class);
        Method initialize = method(SecuredLifecycleComponent.class, "initialize");
        Method destroy = method(SecuredLifecycleComponent.class, "destroy");
        Method adminOperation = method(SecuredLifecycleComponent.class, "adminOperation");
        Method shutdown = method(SecuredLifecycleComponent.class, "shutdown");
        Method health = method(SecuredLifecycleComponent.class, "health");

        assertThat(declareRoles.value()).containsExactly("admin", "auditor");
        assertThat(runAs.value()).isEqualTo("system");
        assertThat(annotationPresent(initialize, PostConstruct.class)).isTrue();
        assertThat(annotationPresent(destroy, PreDestroy.class)).isTrue();
        assertThat(annotation(adminOperation, RolesAllowed.class).value()).containsExactly("admin", "operator");
        assertThat(annotationPresent(shutdown, DenyAll.class)).isTrue();
        assertThat(annotationPresent(health, PermitAll.class)).isTrue();
    }

    @Test
    void generatedAnnotationIsSourceRetainedAndExposesItsMembers() throws Exception {
        Method value = method(Generated.class, "value");
        Method date = method(Generated.class, "date");
        Method comments = method(Generated.class, "comments");

        assertThat(annotation(GeneratedComponent.class, Generated.class)).isNull();
        assertThat(value.getReturnType()).isEqualTo(String[].class);
        assertThat(value.getDefaultValue()).isNull();
        assertThat(date.getReturnType()).isEqualTo(String.class);
        assertThat(date.getDefaultValue()).isEqualTo("");
        assertThat(comments.getReturnType()).isEqualTo(String.class);
        assertThat(comments.getDefaultValue()).isEqualTo("");
    }

    @Test
    void typeLevelSecurityAnnotationsAreRetainedOnClasses() {
        RolesAllowed rolesAllowed = annotation(TypeRestrictedComponent.class, RolesAllowed.class);

        assertThat(rolesAllowed.value()).containsExactly("auditor", "operator");
        assertThat(annotationPresent(OpenAccessComponent.class, PermitAll.class)).isTrue();
    }

    @Test
    void annotationTypesExposeExpectedRetentionTargetsAndDocumentation() {
        assertThat(annotation(Resource.class, Retention.class).value()).isEqualTo(RetentionPolicy.RUNTIME);
        assertThat(annotation(Resources.class, Retention.class).value()).isEqualTo(RetentionPolicy.RUNTIME);
        assertThat(annotation(PostConstruct.class, Retention.class).value()).isEqualTo(RetentionPolicy.RUNTIME);
        assertThat(annotation(PreDestroy.class, Retention.class).value()).isEqualTo(RetentionPolicy.RUNTIME);
        assertThat(annotation(DeclareRoles.class, Retention.class).value()).isEqualTo(RetentionPolicy.RUNTIME);
        assertThat(annotation(DenyAll.class, Retention.class).value()).isEqualTo(RetentionPolicy.RUNTIME);
        assertThat(annotation(PermitAll.class, Retention.class).value()).isEqualTo(RetentionPolicy.RUNTIME);
        assertThat(annotation(RolesAllowed.class, Retention.class).value()).isEqualTo(RetentionPolicy.RUNTIME);
        assertThat(annotation(RunAs.class, Retention.class).value()).isEqualTo(RetentionPolicy.RUNTIME);
        assertThat(annotation(Generated.class, Retention.class).value()).isEqualTo(RetentionPolicy.SOURCE);

        assertThat(annotation(Resource.class, Target.class).value()).containsExactlyInAnyOrder(TYPE, FIELD, METHOD);
        assertThat(annotation(Resources.class, Target.class).value()).containsExactly(TYPE);
        assertThat(annotation(PostConstruct.class, Target.class).value()).containsExactly(METHOD);
        assertThat(annotation(PreDestroy.class, Target.class).value()).containsExactly(METHOD);
        assertThat(annotation(DeclareRoles.class, Target.class).value()).containsExactly(TYPE);
        assertThat(annotation(DenyAll.class, Target.class).value()).containsExactly(METHOD);
        assertThat(annotation(PermitAll.class, Target.class).value()).containsExactlyInAnyOrder(TYPE, METHOD);
        assertThat(annotation(RolesAllowed.class, Target.class).value()).containsExactlyInAnyOrder(TYPE, METHOD);
        assertThat(annotation(RunAs.class, Target.class).value()).containsExactly(TYPE);
        assertThat(annotation(Generated.class, Target.class).value())
                .containsExactly(PACKAGE, TYPE, ANNOTATION_TYPE, METHOD, CONSTRUCTOR, FIELD, LOCAL_VARIABLE, PARAMETER);

        assertThat(annotationPresent(Generated.class, Documented.class)).isTrue();
        assertThat(annotationPresent(PostConstruct.class, Documented.class)).isTrue();
        assertThat(annotationPresent(PreDestroy.class, Documented.class)).isTrue();
        assertThat(annotationPresent(Resources.class, Documented.class)).isTrue();
        assertThat(annotationPresent(DeclareRoles.class, Documented.class)).isTrue();
        assertThat(annotationPresent(DenyAll.class, Documented.class)).isTrue();
        assertThat(annotationPresent(PermitAll.class, Documented.class)).isTrue();
        assertThat(annotationPresent(RolesAllowed.class, Documented.class)).isTrue();
        assertThat(annotationPresent(RunAs.class, Documented.class)).isTrue();
    }

    @Test
    void runtimeAnnotationsAreNotInheritedBySubclassesOrOverrides() throws Exception {
        Method adminOperation = method(DerivedSecuredComponent.class, "adminOperation");
        Method shutdown = method(DerivedSecuredComponent.class, "shutdown");
        Method health = method(DerivedSecuredComponent.class, "health");

        assertThat(annotation(DerivedSecuredComponent.class, Resource.class)).isNull();
        assertThat(annotation(DerivedSecuredComponent.class, Resources.class)).isNull();
        assertThat(annotation(DerivedSecuredComponent.class, DeclareRoles.class)).isNull();
        assertThat(annotation(DerivedSecuredComponent.class, RunAs.class)).isNull();
        assertThat(annotation(adminOperation, RolesAllowed.class)).isNull();
        assertThat(annotationPresent(shutdown, DenyAll.class)).isFalse();
        assertThat(annotationPresent(health, PermitAll.class)).isFalse();
    }

    private static <A extends Annotation> A annotation(AnnotatedElement element, Class<A> annotationType) {
        return element.getAnnotation(annotationType);
    }

    private static boolean annotationPresent(AnnotatedElement element, Class<? extends Annotation> annotationType) {
        return element.isAnnotationPresent(annotationType);
    }

    private static Field field(Class<?> type, String name) throws NoSuchFieldException {
        return type.getDeclaredField(name);
    }

    private static Method method(Class<?> type, String name) throws NoSuchMethodException {
        return type.getDeclaredMethod(name);
    }

    private static final class DefaultLifecycleComponent {

        @Resource
        private Object defaultResource;
    }

    @Resource(
            name = "service/default",
            type = CharSequence.class,
            authenticationType = Resource.AuthenticationType.APPLICATION,
            shareable = false,
            mappedName = "mapped/service/default",
            description = "Type-level resource")
    @Resources({
            @Resource(
                    name = "jdbc/primary",
                    type = CharSequence.class,
                    authenticationType = Resource.AuthenticationType.APPLICATION,
                    shareable = false,
                    mappedName = "mapped/jdbc/primary",
                    description = "Primary resource"),
            @Resource(name = "mail/session", description = "Mail session")
    })
    private static final class ConfiguredResourceComponent {

        @Resource(
                name = "queue/orders",
                type = String.class,
                mappedName = "mapped/queue/orders",
                description = "Field resource")
        private String queueName;

        @Resource(
                name = "service/config",
                type = Integer.class,
                authenticationType = Resource.AuthenticationType.APPLICATION,
                shareable = false,
                mappedName = "mapped/service/config",
                description = "Method resource")
        Integer loadConfig() {
            return 42;
        }
    }

    @DeclareRoles({"admin", "auditor"})
    @RunAs("system")
    private static class SecuredLifecycleComponent {

        @PostConstruct
        void initialize() {
        }

        @PreDestroy
        void destroy() {
        }

        @RolesAllowed({"admin", "operator"})
        void adminOperation() {
        }

        @DenyAll
        void shutdown() {
        }

        @PermitAll
        void health() {
        }
    }

    @Resource(name = "base/resource")
    @Resources(@Resource(name = "base/resources"))
    @DeclareRoles("base-role")
    @RunAs("base-system")
    private static class BaseSecuredComponent {

        @RolesAllowed("base-admin")
        void adminOperation() {
        }

        @DenyAll
        void shutdown() {
        }

        @PermitAll
        void health() {
        }
    }

    private static final class DerivedSecuredComponent extends BaseSecuredComponent {

        @Override
        void adminOperation() {
        }

        @Override
        void shutdown() {
        }

        @Override
        void health() {
        }
    }

    @Generated(value = {"metadata-forge", "generator"}, date = "2026-04-20", comments = "compile-time only")
    private static final class GeneratedComponent {
    }

    @RolesAllowed({"auditor", "operator"})
    private static final class TypeRestrictedComponent {
    }

    @PermitAll
    private static final class OpenAccessComponent {
    }
}
