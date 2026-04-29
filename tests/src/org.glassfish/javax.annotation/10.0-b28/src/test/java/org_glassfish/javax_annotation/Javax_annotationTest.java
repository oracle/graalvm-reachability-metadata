/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_glassfish.javax_annotation;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.METHOD;
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

public class Javax_annotationTest {

    @Test
    void defaultLifecycleAndResourceAnnotationsExposeExpectedValues() throws Exception {
        Resource fieldResource = annotation(field(DefaultAnnotatedComponent.class, "defaultResource"), Resource.class);
        Method init = method(DefaultAnnotatedComponent.class, "init");
        Method destroy = method(DefaultAnnotatedComponent.class, "destroy");

        assertThat(fieldResource.annotationType()).isSameAs(Resource.class);
        assertThat(fieldResource.name()).isEmpty();
        assertThat(fieldResource.type()).isEqualTo(Object.class);
        assertThat(fieldResource.authenticationType()).isEqualTo(Resource.AuthenticationType.CONTAINER);
        assertThat(fieldResource.shareable()).isTrue();
        assertThat(fieldResource.mappedName()).isEmpty();
        assertThat(fieldResource.description()).isEmpty();
        assertThat(annotationPresent(init, PostConstruct.class)).isTrue();
        assertThat(annotationPresent(destroy, PreDestroy.class)).isTrue();
    }

    @Test
    void typeLevelResourceAnnotationDescribesNamedComponentDependencies() {
        Resource resource = annotation(NamedResourceComponent.class, Resource.class);

        assertThat(resource.name()).isEqualTo("jms/auditQueue");
        assertThat(resource.type()).isEqualTo(Runnable.class);
        assertThat(resource.authenticationType()).isEqualTo(Resource.AuthenticationType.APPLICATION);
        assertThat(resource.shareable()).isFalse();
    }

    @Test
    void securityAnnotationsRetainClassAndMethodRoles() throws Exception {
        DeclareRoles declareRoles = annotation(SecuredComponent.class, DeclareRoles.class);
        RunAs runAs = annotation(SecuredComponent.class, RunAs.class);
        Method adminOperation = method(SecuredComponent.class, "adminOperation");
        Method publicOperation = method(SecuredComponent.class, "publicOperation");
        Method blockedOperation = method(SecuredComponent.class, "blockedOperation");

        assertThat(declareRoles.value()).containsExactly("admin", "auditor", "operator");
        assertThat(runAs.value()).isEqualTo("system");
        assertThat(annotation(adminOperation, RolesAllowed.class).value()).containsExactly("admin", "operator");
        assertThat(annotationPresent(publicOperation, PermitAll.class)).isTrue();
        assertThat(annotationPresent(blockedOperation, DenyAll.class)).isTrue();
    }

    @Test
    void typeLevelSecurityAnnotationsRepresentDefaultAccessPolicies() {
        RolesAllowed rolesAllowed = annotation(RoleRestrictedComponent.class, RolesAllowed.class);

        assertThat(rolesAllowed.value()).containsExactly("writer", "reviewer");
        assertThat(annotationPresent(OpenComponent.class, PermitAll.class)).isTrue();
        assertThat(annotationPresent(RoleRestrictedComponent.class, PermitAll.class)).isFalse();
    }

    @Test
    void resourceContainerAndAuthenticationEnumExposePublicApi() throws Exception {
        Resources resources = annotation(ResourceConfiguredComponent.class, Resources.class);
        Resource fieldResource = annotation(field(ResourceConfiguredComponent.class, "queueName"), Resource.class);
        Resource methodResource = annotation(method(ResourceConfiguredComponent.class, "loadConfig"), Resource.class);

        assertThat(resources.value()).hasSize(2);
        assertThat(resources.value()[0].name()).isEqualTo("jdbc/primary");
        assertThat(resources.value()[0].type()).isEqualTo(CharSequence.class);
        assertThat(resources.value()[0].authenticationType()).isEqualTo(Resource.AuthenticationType.APPLICATION);
        assertThat(resources.value()[0].shareable()).isFalse();
        assertThat(resources.value()[0].description()).isEqualTo("Primary resource");
        assertThat(resources.value()[0].mappedName()).isEqualTo("mapped/primary");
        assertThat(resources.value()[1].name()).isEqualTo("mail/session");
        assertThat(resources.value()[1].type()).isEqualTo(Object.class);
        assertThat(resources.value()[1].authenticationType()).isEqualTo(Resource.AuthenticationType.CONTAINER);
        assertThat(resources.value()[1].shareable()).isTrue();
        assertThat(resources.value()[1].description()).isEqualTo("Mail session");

        assertThat(fieldResource.name()).isEqualTo("queue/orders");
        assertThat(fieldResource.type()).isEqualTo(String.class);
        assertThat(methodResource.name()).isEqualTo("service/config");
        assertThat(methodResource.type()).isEqualTo(Integer.class);
        assertThat(methodResource.mappedName()).isEqualTo("mapped/config");

        assertThat(Resource.AuthenticationType.values())
                .containsExactly(Resource.AuthenticationType.CONTAINER, Resource.AuthenticationType.APPLICATION);
        assertThat(Resource.AuthenticationType.valueOf("APPLICATION"))
                .isSameAs(Resource.AuthenticationType.APPLICATION);
    }

    @Test
    void generatedAnnotationIsSourceRetainedButDeclaresItsMembers() throws Exception {
        assertThat(annotation(GeneratedType.class, Generated.class)).isNull();

        Method value = method(Generated.class, "value");
        Method date = method(Generated.class, "date");
        Method comments = method(Generated.class, "comments");

        assertThat(value.getReturnType()).isEqualTo(String[].class);
        assertThat(value.getDefaultValue()).isNull();
        assertThat(date.getReturnType()).isEqualTo(String.class);
        assertThat(date.getDefaultValue()).isEqualTo("");
        assertThat(comments.getReturnType()).isEqualTo(String.class);
        assertThat(comments.getDefaultValue()).isEqualTo("");
    }

    @Test
    void runtimeAnnotationsAreNotInheritedBySubclassesOrOverrides() throws Exception {
        assertThat(annotation(DerivedComponent.class, RunAs.class)).isNull();
        assertThat(annotation(DerivedComponent.class, DeclareRoles.class)).isNull();

        Method adminOperation = method(DerivedComponent.class, "adminOperation");
        Method loadConfig = method(DerivedComponent.class, "loadConfig");

        assertThat(annotation(adminOperation, RolesAllowed.class)).isNull();
        assertThat(annotationPresent(loadConfig, PermitAll.class)).isFalse();
        assertThat(annotation(loadConfig, Resource.class)).isNull();
    }

    @Test
    void annotationTypesExposeRetentionPolicies() {
        assertThat(annotation(PostConstruct.class, Retention.class).value()).isEqualTo(RetentionPolicy.RUNTIME);
        assertThat(annotation(PreDestroy.class, Retention.class).value()).isEqualTo(RetentionPolicy.RUNTIME);
        assertThat(annotation(Resource.class, Retention.class).value()).isEqualTo(RetentionPolicy.RUNTIME);
        assertThat(annotation(Resources.class, Retention.class).value()).isEqualTo(RetentionPolicy.RUNTIME);
        assertThat(annotation(DeclareRoles.class, Retention.class).value()).isEqualTo(RetentionPolicy.RUNTIME);
        assertThat(annotation(DenyAll.class, Retention.class).value()).isEqualTo(RetentionPolicy.RUNTIME);
        assertThat(annotation(PermitAll.class, Retention.class).value()).isEqualTo(RetentionPolicy.RUNTIME);
        assertThat(annotation(RolesAllowed.class, Retention.class).value()).isEqualTo(RetentionPolicy.RUNTIME);
        assertThat(annotation(RunAs.class, Retention.class).value()).isEqualTo(RetentionPolicy.RUNTIME);
        assertThat(annotation(Generated.class, Retention.class).value()).isEqualTo(RetentionPolicy.SOURCE);
    }

    @Test
    void annotationTypesExposeTargetsAndDocumentationContracts() {
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
                .containsExactly(
                        ElementType.PACKAGE,
                        TYPE,
                        ElementType.ANNOTATION_TYPE,
                        METHOD,
                        ElementType.CONSTRUCTOR,
                        FIELD,
                        ElementType.LOCAL_VARIABLE,
                        ElementType.PARAMETER);

        assertThat(annotationPresent(Resources.class, Documented.class)).isTrue();
        assertThat(annotationPresent(DeclareRoles.class, Documented.class)).isTrue();
        assertThat(annotationPresent(DenyAll.class, Documented.class)).isTrue();
        assertThat(annotationPresent(PermitAll.class, Documented.class)).isTrue();
        assertThat(annotationPresent(RolesAllowed.class, Documented.class)).isTrue();
        assertThat(annotationPresent(RunAs.class, Documented.class)).isTrue();
        assertThat(annotationPresent(PostConstruct.class, Documented.class)).isTrue();
        assertThat(annotationPresent(PreDestroy.class, Documented.class)).isTrue();
        assertThat(annotationPresent(Generated.class, Documented.class)).isTrue();
        assertThat(annotationPresent(Resource.class, Documented.class)).isFalse();
    }

    private static <A extends Annotation> A annotation(AnnotatedElement element, Class<A> annotationType) {
        A[] annotations = element.getAnnotationsByType(annotationType);
        return annotations.length == 0 ? null : annotations[0];
    }

    private static boolean annotationPresent(AnnotatedElement element, Class<? extends Annotation> annotationType) {
        return element.getAnnotationsByType(annotationType).length > 0;
    }

    private static Field field(Class<?> type, String name) throws NoSuchFieldException {
        return type.getDeclaredField(name);
    }

    private static Method method(Class<?> type, String name) throws NoSuchMethodException {
        return type.getDeclaredMethod(name);
    }

    private static final class DefaultAnnotatedComponent {

        @Resource
        private Object defaultResource;

        @PostConstruct
        void init() {
        }

        @PreDestroy
        void destroy() {
        }
    }

    @Resource(
            name = "jms/auditQueue",
            type = Runnable.class,
            authenticationType = Resource.AuthenticationType.APPLICATION,
            shareable = false)
    private static final class NamedResourceComponent {
    }

    @DeclareRoles({"admin", "auditor", "operator"})
    @RunAs("system")
    private static final class SecuredComponent {

        @RolesAllowed({"admin", "operator"})
        void adminOperation() {
        }

        @PermitAll
        void publicOperation() {
        }

        @DenyAll
        void blockedOperation() {
        }
    }

    @RolesAllowed({"writer", "reviewer"})
    private static final class RoleRestrictedComponent {
    }

    @PermitAll
    private static final class OpenComponent {
    }

    @Resources({
            @Resource(
                    name = "jdbc/primary",
                    type = CharSequence.class,
                    authenticationType = Resource.AuthenticationType.APPLICATION,
                    shareable = false,
                    description = "Primary resource",
                    mappedName = "mapped/primary"),
            @Resource(name = "mail/session", description = "Mail session")
    })
    private static final class ResourceConfiguredComponent {

        @Resource(name = "queue/orders", type = String.class)
        private String queueName;

        @Resource(name = "service/config", type = Integer.class, mappedName = "mapped/config")
        Integer loadConfig() {
            return 42;
        }
    }

    @DeclareRoles("parent")
    @RunAs("system-parent")
    private static class BaseComponent {

        @RolesAllowed("admin")
        void adminOperation() {
        }

        @PermitAll
        @Resource(name = "service/parent", type = Integer.class)
        Integer loadConfig() {
            return 1;
        }
    }

    private static final class DerivedComponent extends BaseComponent {

        @Override
        void adminOperation() {
        }

        @Override
        Integer loadConfig() {
            return 2;
        }
    }

    @Generated(value = {"metadata-forge", "generator"}, date = "2026-04-28", comments = "compile-time only")
    private static final class GeneratedType {
    }
}
