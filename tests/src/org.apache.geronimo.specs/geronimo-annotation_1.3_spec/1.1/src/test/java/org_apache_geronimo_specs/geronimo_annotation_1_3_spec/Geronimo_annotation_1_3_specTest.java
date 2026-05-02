/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_geronimo_specs.geronimo_annotation_1_3_spec;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.annotation.Annotation;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.AnnotatedElement;
import java.util.concurrent.Executor;

import javax.annotation.Generated;
import javax.annotation.ManagedBean;
import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.annotation.Priority;
import javax.annotation.Resource;
import javax.annotation.Resources;
import javax.annotation.security.DeclareRoles;
import javax.annotation.security.DenyAll;
import javax.annotation.security.PermitAll;
import javax.annotation.security.RolesAllowed;
import javax.annotation.security.RunAs;
import javax.annotation.sql.DataSourceDefinition;
import javax.annotation.sql.DataSourceDefinitions;

import org.junit.jupiter.api.Test;

public class Geronimo_annotation_1_3_specTest {
    @Test
    void resourceAndManagedBeanAnnotationsExposeConfiguredAndDefaultAttributes()
            throws NoSuchFieldException, NoSuchMethodException {
        assertRuntimeRetention(ManagedBean.class);
        assertTarget(ManagedBean.class, ElementType.TYPE);
        assertRuntimeRetention(Resource.class);
        assertTarget(Resource.class, ElementType.TYPE, ElementType.FIELD, ElementType.METHOD);
        assertRuntimeRetention(Resources.class);
        assertDocumented(Resources.class);
        assertTarget(Resources.class, ElementType.TYPE);

        ManagedBean managedBean = annotation(ManagedResourcesComponent.class, ManagedBean.class);
        Resource classLevelResource = annotation(ManagedResourcesComponent.class, Resource.class);
        Resource defaultResource = annotationOnField(ManagedResourcesComponent.class, "defaultInjectedResource",
                Resource.class);
        Resource configuredResource = annotationOnMethod(ManagedResourcesComponent.class, "configuredExecutor",
                Resource.class);
        Resources resources = annotation(ResourceCollectionComponent.class, Resources.class);

        assertThat(managedBean.value()).isEqualTo("orderProcessor");
        assertThat(classLevelResource.name()).isEqualTo("java:comp/env/applicationName");
        assertThat(classLevelResource.type()).isEqualTo(String.class);
        assertThat(classLevelResource.lookup()).isEqualTo("java:app/AppName");

        assertThat(defaultResource.name()).isEmpty();
        assertThat(defaultResource.type()).isEqualTo(Object.class);
        assertThat(defaultResource.authenticationType()).isEqualTo(Resource.AuthenticationType.CONTAINER);
        assertThat(defaultResource.shareable()).isTrue();
        assertThat(defaultResource.mappedName()).isEmpty();
        assertThat(defaultResource.description()).isEmpty();
        assertThat(defaultResource.lookup()).isEmpty();

        assertThat(configuredResource.name()).isEqualTo("executorService");
        assertThat(configuredResource.type()).isEqualTo(Executor.class);
        assertThat(configuredResource.authenticationType()).isEqualTo(Resource.AuthenticationType.APPLICATION);
        assertThat(configuredResource.shareable()).isFalse();
        assertThat(configuredResource.mappedName()).isEqualTo("mapped/executor");
        assertThat(configuredResource.description()).isEqualTo("task executor for background work");
        assertThat(configuredResource.lookup()).isEqualTo("java:comp/env/executor");

        assertThat(Resource.AuthenticationType.values())
                .containsExactly(Resource.AuthenticationType.CONTAINER, Resource.AuthenticationType.APPLICATION);
        assertThat(Resource.AuthenticationType.valueOf("APPLICATION"))
                .isEqualTo(Resource.AuthenticationType.APPLICATION);

        assertThat(resources.value()).hasSize(2);
        assertThat(resources.value()[0].name()).isEqualTo("jdbc/primary");
        assertThat(resources.value()[0].type()).isEqualTo(String.class);
        assertThat(resources.value()[0].description()).isEqualTo("primary resource");
        assertThat(resources.value()[1].name()).isEqualTo("jdbc/audit");
        assertThat(resources.value()[1].type()).isEqualTo(Integer.class);
        assertThat(resources.value()[1].lookup()).isEqualTo("java:global/auditCount");
    }

    @Test
    void lifecycleAndSecurityAnnotationsRemainVisibleAtRuntime() throws NoSuchMethodException {
        assertRuntimeRetention(PostConstruct.class);
        assertDocumented(PostConstruct.class);
        assertTarget(PostConstruct.class, ElementType.METHOD);
        assertRuntimeRetention(PreDestroy.class);
        assertDocumented(PreDestroy.class);
        assertTarget(PreDestroy.class, ElementType.METHOD);

        assertRuntimeRetention(DeclareRoles.class);
        assertDocumented(DeclareRoles.class);
        assertTarget(DeclareRoles.class, ElementType.TYPE);
        assertRuntimeRetention(PermitAll.class);
        assertDocumented(PermitAll.class);
        assertTarget(PermitAll.class, ElementType.TYPE, ElementType.METHOD);
        assertRuntimeRetention(DenyAll.class);
        assertDocumented(DenyAll.class);
        assertTarget(DenyAll.class, ElementType.TYPE, ElementType.METHOD);
        assertRuntimeRetention(RolesAllowed.class);
        assertDocumented(RolesAllowed.class);
        assertTarget(RolesAllowed.class, ElementType.TYPE, ElementType.METHOD);
        assertRuntimeRetention(RunAs.class);
        assertDocumented(RunAs.class);
        assertTarget(RunAs.class, ElementType.TYPE);

        assertThat(annotationOnMethod(SecurityLifecycleComponent.class, "initialize", PostConstruct.class)).isNotNull();
        assertThat(annotationOnMethod(SecurityLifecycleComponent.class, "destroy", PreDestroy.class)).isNotNull();
        assertThat(annotation(SecurityLifecycleComponent.class, DeclareRoles.class).value())
                .containsExactly("admin", "auditor");
        assertThat(annotation(SecurityLifecycleComponent.class, RunAs.class).value()).isEqualTo("system");
        assertThat(annotation(SecurityLifecycleComponent.class, PermitAll.class)).isNotNull();
        assertThat(annotationOnMethod(SecurityLifecycleComponent.class, "adminOnly", RolesAllowed.class).value())
                .containsExactly("admin", "support");
        assertThat(annotationOnMethod(SecurityLifecycleComponent.class, "blocked", DenyAll.class)).isNotNull();
        assertThat(annotationOnMethod(SecurityLifecycleComponent.class, "health", PermitAll.class)).isNotNull();
    }

    @Test
    void securityAnnotationsSupportClassLevelAccessPolicies() {
        RolesAllowed rolesAllowed = annotation(ClassLevelRolesComponent.class, RolesAllowed.class);
        DenyAll denyAll = annotation(ShutdownOnlyComponent.class, DenyAll.class);

        assertThat(rolesAllowed.value()).containsExactly("operator", "supervisor");
        assertThat(denyAll).isNotNull();
    }

    @Test
    void priorityAnnotationSupportsTypeAndParameterUseCases() throws NoSuchMethodException {
        assertRuntimeRetention(Priority.class);
        assertDocumented(Priority.class);
        assertTarget(Priority.class, ElementType.TYPE, ElementType.PARAMETER);

        assertThat(annotation(HighPriorityHandler.class, Priority.class).value()).isEqualTo(10);
        assertThat(HighPriorityHandler.class.getDeclaredMethod("handle", String.class)
                .getParameters()[0]
                .getAnnotation(Priority.class)
                .value())
                .isEqualTo(20);
    }

    @Test
    void dataSourceDefinitionAnnotationsExposeExplicitAndDefaultAttributes() {
        assertRuntimeRetention(DataSourceDefinition.class);
        assertTarget(DataSourceDefinition.class, ElementType.TYPE);
        assertRuntimeRetention(DataSourceDefinitions.class);
        assertTarget(DataSourceDefinitions.class, ElementType.TYPE);

        DataSourceDefinition defaults = annotation(SingleDataSourceComponent.class, DataSourceDefinition.class);
        DataSourceDefinitions dataSourceDefinitions = annotation(MultipleDataSourcesComponent.class,
                DataSourceDefinitions.class);

        assertThat(defaults.name()).isEqualTo("jdbc/defaults");
        assertThat(defaults.className()).isEqualTo("org.example.DefaultSource");
        assertThat(defaults.transactional()).isTrue();
        assertThat(defaults.initialPoolSize()).isEqualTo(-1);
        assertThat(defaults.isolationLevel()).isEqualTo(-1);
        assertThat(defaults.loginTimeout()).isZero();
        assertThat(defaults.maxIdleTime()).isEqualTo(-1);
        assertThat(defaults.maxPoolSize()).isEqualTo(-1);
        assertThat(defaults.maxStatements()).isEqualTo(-1);
        assertThat(defaults.minPoolSize()).isEqualTo(-1);
        assertThat(defaults.portNumber()).isEqualTo(-1);
        assertThat(defaults.databaseName()).isEmpty();
        assertThat(defaults.description()).isEmpty();
        assertThat(defaults.password()).isEmpty();
        assertThat(defaults.serverName()).isEqualTo("localhost");
        assertThat(defaults.url()).isEmpty();
        assertThat(defaults.user()).isEmpty();
        assertThat(defaults.properties()).isEmpty();

        assertThat(dataSourceDefinitions.value()).hasSize(2);

        DataSourceDefinition analytics = dataSourceDefinitions.value()[0];
        assertThat(analytics.name()).isEqualTo("jdbc/analytics");
        assertThat(analytics.className()).isEqualTo("org.example.AnalyticsDataSource");
        assertThat(analytics.description()).isEqualTo("analytics reporting datasource");
        assertThat(analytics.url()).isEqualTo("jdbc:postgresql://db-host:5432/analytics");
        assertThat(analytics.user()).isEqualTo("analytics-user");
        assertThat(analytics.password()).isEqualTo("analytics-secret");
        assertThat(analytics.databaseName()).isEqualTo("analytics");
        assertThat(analytics.serverName()).isEqualTo("db-host");
        assertThat(analytics.portNumber()).isEqualTo(5432);
        assertThat(analytics.transactional()).isFalse();
        assertThat(analytics.initialPoolSize()).isEqualTo(1);
        assertThat(analytics.minPoolSize()).isEqualTo(1);
        assertThat(analytics.maxPoolSize()).isEqualTo(8);
        assertThat(analytics.maxIdleTime()).isEqualTo(60);
        assertThat(analytics.maxStatements()).isEqualTo(32);
        assertThat(analytics.loginTimeout()).isEqualTo(5);
        assertThat(analytics.isolationLevel()).isEqualTo(2);
        assertThat(analytics.properties()).containsExactly("ssl=true", "currentSchema=analytics");

        DataSourceDefinition archive = dataSourceDefinitions.value()[1];
        assertThat(archive.name()).isEqualTo("jdbc/archive");
        assertThat(archive.className()).isEqualTo("org.example.ArchiveDataSource");
        assertThat(archive.serverName()).isEqualTo("localhost");
        assertThat(archive.transactional()).isTrue();
        assertThat(archive.properties()).isEmpty();
    }

    @Test
    void managedBeanAndTypedResourceDefaultsRemainUnresolvedInAnnotationMetadata()
            throws NoSuchFieldException, NoSuchMethodException {
        ManagedBean managedBean = annotation(DefaultManagedResourceComponent.class, ManagedBean.class);
        Resource typedFieldResource = annotationOnField(DefaultManagedResourceComponent.class, "typedExecutor",
                Resource.class);
        Resource typedMethodResource = annotationOnMethod(DefaultManagedResourceComponent.class,
                "typedExecutorFactory", Resource.class);

        assertThat(managedBean.value()).isEmpty();
        assertThat(typedFieldResource.name()).isEmpty();
        assertThat(typedFieldResource.type()).isEqualTo(Object.class);
        assertThat(typedMethodResource.name()).isEmpty();
        assertThat(typedMethodResource.type()).isEqualTo(Object.class);
    }

    @Test
    void generatedAnnotationIsSourceRetainedAndDescribesSupportedTargets()
            throws NoSuchFieldException, NoSuchMethodException {
        assertSourceRetention(Generated.class);
        assertDocumented(Generated.class);
        assertTarget(Generated.class, ElementType.PACKAGE, ElementType.TYPE, ElementType.ANNOTATION_TYPE,
                ElementType.METHOD, ElementType.CONSTRUCTOR, ElementType.FIELD, ElementType.LOCAL_VARIABLE,
                ElementType.PARAMETER);

        assertThat(annotation(GeneratedArtifact.class, Generated.class)).isNull();
        assertThat(annotation(GeneratedMarker.class, Generated.class)).isNull();
        assertThat(annotation(GeneratedArtifact.class.getDeclaredField("generatedField"), Generated.class)).isNull();
        assertThat(annotation(GeneratedArtifact.class.getDeclaredConstructor(String.class), Generated.class)).isNull();
        assertThat(GeneratedArtifact.class.getDeclaredConstructor(String.class)
                .getParameters()[0]
                .getAnnotation(Generated.class))
                .isNull();
        assertThat(annotation(GeneratedArtifact.class.getDeclaredMethod("generatedMethod"), Generated.class)).isNull();
    }

    @Test
    void generatedAnnotationElementsSupportToolNamesAndOptionalDetails() {
        Generated defaultDetails = annotation(DefaultGeneratedMetadataComponent.class, GeneratedMetadata.class).value();
        Generated explicitDetails = annotation(ExplicitGeneratedMetadataComponent.class, GeneratedMetadata.class)
                .value();

        assertThat(defaultDetails.value()).containsExactly("schema-generator", "client-generator");
        assertThat(defaultDetails.date()).isEmpty();
        assertThat(defaultDetails.comments()).isEmpty();

        assertThat(explicitDetails.value()).containsExactly("component-generator");
        assertThat(explicitDetails.date()).isEqualTo("2026-05-02T10:15:30Z");
        assertThat(explicitDetails.comments()).isEqualTo("created for native-image metadata");
    }

    // Checkstyle: allow direct annotation access
    private static <A extends Annotation> A annotation(AnnotatedElement element, Class<A> annotationType) {
        A[] annotations = element.getAnnotationsByType(annotationType);
        return annotations.length == 0 ? null : annotations[0];
    }

    private static boolean annotationPresent(AnnotatedElement element, Class<? extends Annotation> annotationType) {
        return element.getAnnotationsByType(annotationType).length > 0;
    }
    // Checkstyle: disallow direct annotation access

    private static <A extends Annotation> A annotationOnField(Class<?> declaringType, String fieldName,
            Class<A> annotationType) throws NoSuchFieldException {
        return annotation(declaringType.getDeclaredField(fieldName), annotationType);
    }

    private static <A extends Annotation> A annotationOnMethod(Class<?> declaringType, String methodName,
            Class<A> annotationType) throws NoSuchMethodException {
        return annotation(declaringType.getDeclaredMethod(methodName), annotationType);
    }

    private static void assertRuntimeRetention(Class<? extends Annotation> annotationType) {
        Retention retention = annotation(annotationType, Retention.class);
        assertThat(retention).isNotNull();
        assertThat(retention.value()).isEqualTo(RetentionPolicy.RUNTIME);
    }

    private static void assertSourceRetention(Class<? extends Annotation> annotationType) {
        Retention retention = annotation(annotationType, Retention.class);
        assertThat(retention).isNotNull();
        assertThat(retention.value()).isEqualTo(RetentionPolicy.SOURCE);
    }

    private static void assertDocumented(Class<? extends Annotation> annotationType) {
        assertThat(annotationPresent(annotationType, Documented.class)).isTrue();
    }

    private static void assertTarget(Class<? extends Annotation> annotationType, ElementType... expectedTargets) {
        Target target = annotation(annotationType, Target.class);
        assertThat(target).isNotNull();
        assertThat(target.value()).containsExactlyInAnyOrder(expectedTargets);
    }

    @ManagedBean("orderProcessor")
    @Resource(name = "java:comp/env/applicationName", type = String.class, lookup = "java:app/AppName")
    private static final class ManagedResourcesComponent {
        @Resource
        private Object defaultInjectedResource;

        @Resource(name = "executorService", type = Executor.class,
                authenticationType = Resource.AuthenticationType.APPLICATION, shareable = false,
                mappedName = "mapped/executor", description = "task executor for background work",
                lookup = "java:comp/env/executor")
        private Executor configuredExecutor() {
            return Runnable::run;
        }
    }

    @ManagedBean
    private static final class DefaultManagedResourceComponent {
        @Resource
        private Executor typedExecutor;

        @Resource
        private Executor typedExecutorFactory() {
            return Runnable::run;
        }
    }

    @Resources({
            @Resource(name = "jdbc/primary", type = String.class, description = "primary resource"),
            @Resource(name = "jdbc/audit", type = Integer.class, lookup = "java:global/auditCount")
    })
    private static final class ResourceCollectionComponent {
    }

    @DeclareRoles({ "admin", "auditor" })
    @RunAs("system")
    @PermitAll
    private static final class SecurityLifecycleComponent {
        @PostConstruct
        private void initialize() {
        }

        @PreDestroy
        private void destroy() {
        }

        @RolesAllowed({ "admin", "support" })
        private void adminOnly() {
        }

        @DenyAll
        private void blocked() {
        }

        @PermitAll
        private void health() {
        }
    }

    @RolesAllowed({ "operator", "supervisor" })
    private static final class ClassLevelRolesComponent {
    }

    @DenyAll
    private static final class ShutdownOnlyComponent {
    }

    @Priority(10)
    private static final class HighPriorityHandler {
        private void handle(@Priority(20) String message) {
        }
    }

    @DataSourceDefinition(name = "jdbc/defaults", className = "org.example.DefaultSource")
    private static final class SingleDataSourceComponent {
    }

    @DataSourceDefinitions({
            @DataSourceDefinition(name = "jdbc/analytics", className = "org.example.AnalyticsDataSource",
                    databaseName = "analytics", description = "analytics reporting datasource",
                    password = "analytics-secret", serverName = "db-host",
                    url = "jdbc:postgresql://db-host:5432/analytics", user = "analytics-user",
                    properties = { "ssl=true", "currentSchema=analytics" }, transactional = false,
                    initialPoolSize = 1, isolationLevel = 2, loginTimeout = 5, maxIdleTime = 60,
                    maxPoolSize = 8, maxStatements = 32, minPoolSize = 1, portNumber = 5432),
            @DataSourceDefinition(name = "jdbc/archive", className = "org.example.ArchiveDataSource")
    })
    private static final class MultipleDataSourcesComponent {
    }

    @Generated(value = "generated-annotation-type", date = "2026-05-02", comments = "marker")
    private @interface GeneratedMarker {
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.TYPE)
    private @interface GeneratedMetadata {
        Generated value();
    }

    @GeneratedMetadata(@Generated(value = { "schema-generator", "client-generator" }))
    private static final class DefaultGeneratedMetadataComponent {
    }

    @GeneratedMetadata(@Generated(value = "component-generator", date = "2026-05-02T10:15:30Z",
            comments = "created for native-image metadata"))
    private static final class ExplicitGeneratedMetadataComponent {
    }

    @Generated(value = "generated-type", date = "2026-05-02", comments = "created by test")
    private static final class GeneratedArtifact {
        @Generated(value = "generated-field", date = "2026-05-02", comments = "field")
        private final String generatedField;

        @Generated(value = "generated-constructor", date = "2026-05-02", comments = "constructor")
        private GeneratedArtifact(@Generated(value = "generated-parameter", date = "2026-05-02",
                comments = "parameter") String value) {
            @Generated(value = "generated-local-variable", date = "2026-05-02", comments = "local variable")
            String localValue = value;
            this.generatedField = localValue;
        }

        @Generated(value = "generated-method", date = "2026-05-02", comments = "method")
        private String generatedMethod() {
            return generatedField;
        }
    }
}
