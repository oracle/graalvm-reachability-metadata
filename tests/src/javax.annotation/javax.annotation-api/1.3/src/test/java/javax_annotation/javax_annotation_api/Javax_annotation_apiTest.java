/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package javax_annotation.javax_annotation_api;

import java.lang.annotation.Annotation;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.Arrays;
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

import static org.assertj.core.api.Assertions.assertThat;

class Javax_annotation_apiTest {
    @Test
    void runtimeAnnotationsExposeExplicitValues() throws Exception {
        ManagedBean managedBean = RuntimeAnnotatedComponent.class.getAnnotation(ManagedBean.class);
        Priority typePriority = RuntimeAnnotatedComponent.class.getAnnotation(Priority.class);
        Field explicitResourceField = RuntimeAnnotatedComponent.class.getDeclaredField("explicitResource");
        Resource explicitResource = explicitResourceField.getAnnotation(Resource.class);
        Method initializeMethod = RuntimeAnnotatedComponent.class.getDeclaredMethod("initialize", String.class);
        PostConstruct postConstruct = initializeMethod.getAnnotation(PostConstruct.class);
        Parameter initializeParameter = initializeMethod.getParameters()[0];
        Priority parameterPriority = initializeParameter.getAnnotation(Priority.class);
        Method destroyMethod = RuntimeAnnotatedComponent.class.getDeclaredMethod("destroy");
        PreDestroy preDestroy = destroyMethod.getAnnotation(PreDestroy.class);

        assertThat(managedBean).isNotNull();
        assertThat(managedBean.value()).isEqualTo("managedService");
        assertThat(managedBean.annotationType()).isEqualTo(ManagedBean.class);

        assertThat(typePriority).isNotNull();
        assertThat(typePriority.value()).isEqualTo(42);

        assertThat(explicitResource).isNotNull();
        assertThat(explicitResource.name()).isEqualTo("serviceResource");
        assertThat(explicitResource.lookup()).isEqualTo("java:app/env/serviceResource");
        assertThat(explicitResource.type()).isEqualTo(CharSequence.class);
        assertThat(explicitResource.authenticationType()).isEqualTo(Resource.AuthenticationType.APPLICATION);
        assertThat(explicitResource.shareable()).isFalse();
        assertThat(explicitResource.mappedName()).isEqualTo("mapped/serviceResource");
        assertThat(explicitResource.description()).isEqualTo("Primary service resource");

        assertThat(postConstruct).isNotNull();
        assertThat(postConstruct.annotationType()).isEqualTo(PostConstruct.class);
        assertThat(parameterPriority).isNotNull();
        assertThat(parameterPriority.value()).isEqualTo(7);
        assertThat(preDestroy).isNotNull();
        assertThat(preDestroy.annotationType()).isEqualTo(PreDestroy.class);
    }

    @Test
    void runtimeAnnotationsExposeDefaultValues() throws Exception {
        ManagedBean defaultManagedBean = DefaultManagedBean.class.getAnnotation(ManagedBean.class);
        Field defaultResourceField = RuntimeAnnotatedComponent.class.getDeclaredField("defaultResource");
        Resource defaultResource = defaultResourceField.getAnnotation(Resource.class);
        DataSourceDefinition defaultDataSource = MinimalDataSourceComponent.class.getAnnotation(DataSourceDefinition.class);

        assertThat(defaultManagedBean).isNotNull();
        assertThat(defaultManagedBean.value()).isEmpty();

        assertThat(defaultResource).isNotNull();
        assertThat(defaultResource.name()).isEmpty();
        assertThat(defaultResource.lookup()).isEmpty();
        assertThat(defaultResource.type()).isEqualTo(Object.class);
        assertThat(defaultResource.authenticationType()).isEqualTo(Resource.AuthenticationType.CONTAINER);
        assertThat(defaultResource.shareable()).isTrue();
        assertThat(defaultResource.mappedName()).isEmpty();
        assertThat(defaultResource.description()).isEmpty();

        assertThat(defaultDataSource).isNotNull();
        assertThat(defaultDataSource.name()).isEqualTo("jdbc/minimal");
        assertThat(defaultDataSource.className()).isEqualTo("org.example.MinimalDataSource");
        assertThat(defaultDataSource.description()).isEmpty();
        assertThat(defaultDataSource.url()).isEmpty();
        assertThat(defaultDataSource.user()).isEmpty();
        assertThat(defaultDataSource.password()).isEmpty();
        assertThat(defaultDataSource.databaseName()).isEmpty();
        assertThat(defaultDataSource.portNumber()).isEqualTo(-1);
        assertThat(defaultDataSource.serverName()).isEqualTo("localhost");
        assertThat(defaultDataSource.isolationLevel()).isEqualTo(-1);
        assertThat(defaultDataSource.transactional()).isTrue();
        assertThat(defaultDataSource.initialPoolSize()).isEqualTo(-1);
        assertThat(defaultDataSource.maxPoolSize()).isEqualTo(-1);
        assertThat(defaultDataSource.minPoolSize()).isEqualTo(-1);
        assertThat(defaultDataSource.maxIdleTime()).isEqualTo(-1);
        assertThat(defaultDataSource.maxStatements()).isEqualTo(-1);
        assertThat(defaultDataSource.properties()).isEmpty();
        assertThat(defaultDataSource.loginTimeout()).isZero();
    }

    @Test
    void repeatableAndSecurityAnnotationsRemainDiscoverableAtRuntime() throws Exception {
        Resource[] resources = RuntimeAnnotatedComponent.class.getAnnotationsByType(Resource.class);
        Resources resourceContainer = RuntimeAnnotatedComponent.class.getAnnotation(Resources.class);
        DataSourceDefinition[] dataSourceDefinitions = RuntimeAnnotatedComponent.class.getAnnotationsByType(DataSourceDefinition.class);
        DataSourceDefinitions dataSourceContainer = RuntimeAnnotatedComponent.class.getAnnotation(DataSourceDefinitions.class);
        DeclareRoles declareRoles = RuntimeAnnotatedComponent.class.getAnnotation(DeclareRoles.class);
        RunAs runAs = RuntimeAnnotatedComponent.class.getAnnotation(RunAs.class);
        Method restrictedOperation = RuntimeAnnotatedComponent.class.getDeclaredMethod("restrictedOperation");
        RolesAllowed rolesAllowed = restrictedOperation.getAnnotation(RolesAllowed.class);
        Method openOperation = RuntimeAnnotatedComponent.class.getDeclaredMethod("openOperation");
        PermitAll permitAll = openOperation.getAnnotation(PermitAll.class);
        Method blockedOperation = RuntimeAnnotatedComponent.class.getDeclaredMethod("blockedOperation");
        DenyAll denyAll = blockedOperation.getAnnotation(DenyAll.class);

        assertThat(resources).hasSize(2);
        assertThat(Arrays.stream(resources).map(Resource::name)).containsExactly("primaryQueue", "auditQueue");
        assertThat(Arrays.stream(resources).map(Resource::lookup))
                .containsExactly("java:global/jms/primaryQueue", "java:global/jms/auditQueue");

        assertThat(resourceContainer).isNotNull();
        assertThat(resourceContainer.value()).hasSize(2);
        assertThat(Arrays.stream(resourceContainer.value()).map(Resource::description))
                .containsExactly("", "Audit events queue");

        assertThat(dataSourceDefinitions).hasSize(2);
        assertThat(Arrays.stream(dataSourceDefinitions).map(DataSourceDefinition::name))
                .containsExactly("jdbc/default", "jdbc/reporting");
        assertThat(dataSourceDefinitions[1].description()).isEqualTo("Reporting datasource");
        assertThat(dataSourceDefinitions[1].url()).isEqualTo("jdbc:example://db.internal/reporting");
        assertThat(dataSourceDefinitions[1].user()).isEqualTo("reporter");
        assertThat(dataSourceDefinitions[1].password()).isEqualTo("secret");
        assertThat(dataSourceDefinitions[1].databaseName()).isEqualTo("reporting");
        assertThat(dataSourceDefinitions[1].portNumber()).isEqualTo(1521);
        assertThat(dataSourceDefinitions[1].serverName()).isEqualTo("db.internal");
        assertThat(dataSourceDefinitions[1].isolationLevel()).isEqualTo(8);
        assertThat(dataSourceDefinitions[1].transactional()).isFalse();
        assertThat(dataSourceDefinitions[1].initialPoolSize()).isEqualTo(1);
        assertThat(dataSourceDefinitions[1].maxPoolSize()).isEqualTo(8);
        assertThat(dataSourceDefinitions[1].minPoolSize()).isEqualTo(1);
        assertThat(dataSourceDefinitions[1].maxIdleTime()).isEqualTo(30);
        assertThat(dataSourceDefinitions[1].maxStatements()).isEqualTo(25);
        assertThat(dataSourceDefinitions[1].properties()).containsExactly("ssl=true", "schema=analytics");
        assertThat(dataSourceDefinitions[1].loginTimeout()).isEqualTo(9);

        assertThat(dataSourceContainer).isNotNull();
        assertThat(dataSourceContainer.value()).hasSize(2);

        assertThat(declareRoles).isNotNull();
        assertThat(declareRoles.value()).containsExactly("reader", "writer");
        assertThat(runAs).isNotNull();
        assertThat(runAs.value()).isEqualTo("system");
        assertThat(rolesAllowed).isNotNull();
        assertThat(rolesAllowed.value()).containsExactly("admin", "operator");
        assertThat(permitAll).isNotNull();
        assertThat(denyAll).isNotNull();
    }

    @Test
    void explicitContainerAnnotationsFlattenToRepeatableViews() {
        Resources explicitResources = ExplicitContainerAnnotatedComponent.class.getAnnotation(Resources.class);
        Resource[] flattenedResources = ExplicitContainerAnnotatedComponent.class.getAnnotationsByType(Resource.class);
        DataSourceDefinitions explicitDataSources = ExplicitContainerAnnotatedComponent.class.getAnnotation(DataSourceDefinitions.class);
        DataSourceDefinition[] flattenedDataSources = ExplicitContainerAnnotatedComponent.class.getAnnotationsByType(DataSourceDefinition.class);

        assertThat(explicitResources).isNotNull();
        assertThat(explicitResources.value()).hasSize(2);
        assertThat(Arrays.stream(explicitResources.value()).map(Resource::name))
                .containsExactly("mailSession", "billingQueue");
        assertThat(flattenedResources).containsExactly(explicitResources.value());
        assertThat(Arrays.stream(flattenedResources).map(Resource::lookup))
                .containsExactly("java:comp/env/mail/default", "java:global/jms/billingQueue");

        assertThat(explicitDataSources).isNotNull();
        assertThat(explicitDataSources.value()).hasSize(2);
        assertThat(Arrays.stream(explicitDataSources.value()).map(DataSourceDefinition::name))
                .containsExactly("jdbc/audit", "jdbc/archive");
        assertThat(flattenedDataSources).containsExactly(explicitDataSources.value());
        assertThat(flattenedDataSources[0].loginTimeout()).isEqualTo(5);
        assertThat(flattenedDataSources[1].properties()).containsExactly("readOnly=true");
    }

    @Test
    void annotationTypesAdvertiseRetentionTargetsAndEnumBehavior() {
        assertRetention(Generated.class, RetentionPolicy.SOURCE);
        assertRetention(ManagedBean.class, RetentionPolicy.RUNTIME);
        assertRetention(PostConstruct.class, RetentionPolicy.RUNTIME);
        assertRetention(PreDestroy.class, RetentionPolicy.RUNTIME);
        assertRetention(Priority.class, RetentionPolicy.RUNTIME);
        assertRetention(Resource.class, RetentionPolicy.RUNTIME);
        assertRetention(Resources.class, RetentionPolicy.RUNTIME);
        assertRetention(DeclareRoles.class, RetentionPolicy.RUNTIME);
        assertRetention(DenyAll.class, RetentionPolicy.RUNTIME);
        assertRetention(PermitAll.class, RetentionPolicy.RUNTIME);
        assertRetention(RolesAllowed.class, RetentionPolicy.RUNTIME);
        assertRetention(RunAs.class, RetentionPolicy.RUNTIME);
        assertRetention(DataSourceDefinition.class, RetentionPolicy.RUNTIME);
        assertRetention(DataSourceDefinitions.class, RetentionPolicy.RUNTIME);

        assertTargets(Generated.class, ElementType.PACKAGE, ElementType.TYPE, ElementType.ANNOTATION_TYPE,
                ElementType.METHOD, ElementType.CONSTRUCTOR, ElementType.FIELD, ElementType.LOCAL_VARIABLE,
                ElementType.PARAMETER);
        assertTargets(ManagedBean.class, ElementType.TYPE);
        assertTargets(PostConstruct.class, ElementType.METHOD);
        assertTargets(PreDestroy.class, ElementType.METHOD);
        assertTargets(Priority.class, ElementType.TYPE, ElementType.PARAMETER);
        assertTargets(Resource.class, ElementType.TYPE, ElementType.FIELD, ElementType.METHOD);
        assertTargets(Resources.class, ElementType.TYPE);
        assertTargets(DeclareRoles.class, ElementType.TYPE);
        assertTargets(DenyAll.class, ElementType.TYPE, ElementType.METHOD);
        assertTargets(PermitAll.class, ElementType.TYPE, ElementType.METHOD);
        assertTargets(RolesAllowed.class, ElementType.TYPE, ElementType.METHOD);
        assertTargets(RunAs.class, ElementType.TYPE);
        assertTargets(DataSourceDefinition.class, ElementType.TYPE);
        assertTargets(DataSourceDefinitions.class, ElementType.TYPE);

        assertThat(Generated.class.isAnnotationPresent(Documented.class)).isTrue();
        assertThat(PostConstruct.class.isAnnotationPresent(Documented.class)).isTrue();
        assertThat(PreDestroy.class.isAnnotationPresent(Documented.class)).isTrue();
        assertThat(Priority.class.isAnnotationPresent(Documented.class)).isTrue();
        assertThat(Resources.class.isAnnotationPresent(Documented.class)).isTrue();
        assertThat(DeclareRoles.class.isAnnotationPresent(Documented.class)).isTrue();
        assertThat(DenyAll.class.isAnnotationPresent(Documented.class)).isTrue();
        assertThat(PermitAll.class.isAnnotationPresent(Documented.class)).isTrue();
        assertThat(RolesAllowed.class.isAnnotationPresent(Documented.class)).isTrue();
        assertThat(RunAs.class.isAnnotationPresent(Documented.class)).isTrue();

        Repeatable resourceRepeatable = Resource.class.getAnnotation(Repeatable.class);
        Repeatable dataSourceRepeatable = DataSourceDefinition.class.getAnnotation(Repeatable.class);

        assertThat(resourceRepeatable).isNotNull();
        assertThat(resourceRepeatable.value()).isEqualTo(Resources.class);
        assertThat(dataSourceRepeatable).isNotNull();
        assertThat(dataSourceRepeatable.value()).isEqualTo(DataSourceDefinitions.class);

        Resource.AuthenticationType[] authenticationTypes = Resource.AuthenticationType.values();
        authenticationTypes[0] = null;

        assertThat(Resource.AuthenticationType.values())
                .containsExactly(Resource.AuthenticationType.CONTAINER, Resource.AuthenticationType.APPLICATION);
        assertThat(Resource.AuthenticationType.valueOf("CONTAINER")).isEqualTo(Resource.AuthenticationType.CONTAINER);
        assertThat(Resource.AuthenticationType.valueOf("APPLICATION")).isEqualTo(Resource.AuthenticationType.APPLICATION);

        assertThat(GeneratedComponent.class.getAnnotation(Generated.class)).isNull();
    }

    private static void assertRetention(
            Class<? extends Annotation> annotationType,
            RetentionPolicy expectedPolicy
    ) {
        Retention retention = annotationType.getAnnotation(Retention.class);

        assertThat(retention).isNotNull();
        assertThat(retention.value()).isEqualTo(expectedPolicy);
    }

    private static void assertTargets(
            Class<? extends Annotation> annotationType,
            ElementType... expectedTargets
    ) {
        Target target = annotationType.getAnnotation(Target.class);

        assertThat(target).isNotNull();
        assertThat(target.value()).containsExactly(expectedTargets);
    }

    @ManagedBean("managedService")
    @DeclareRoles({ "reader", "writer" })
    @RunAs("system")
    @Resource(name = "primaryQueue", lookup = "java:global/jms/primaryQueue")
    @Resource(name = "auditQueue", lookup = "java:global/jms/auditQueue", description = "Audit events queue")
    @DataSourceDefinition(name = "jdbc/default", className = "org.example.DefaultDataSource")
    @DataSourceDefinition(
            name = "jdbc/reporting",
            className = "org.example.ReportingDataSource",
            description = "Reporting datasource",
            url = "jdbc:example://db.internal/reporting",
            user = "reporter",
            password = "secret",
            databaseName = "reporting",
            portNumber = 1521,
            serverName = "db.internal",
            isolationLevel = 8,
            transactional = false,
            initialPoolSize = 1,
            maxPoolSize = 8,
            minPoolSize = 1,
            maxIdleTime = 30,
            maxStatements = 25,
            properties = { "ssl=true", "schema=analytics" },
            loginTimeout = 9
    )
    @Priority(42)
    private static final class RuntimeAnnotatedComponent {
        @Resource
        private Object defaultResource;

        @Resource(
                name = "serviceResource",
                lookup = "java:app/env/serviceResource",
                type = CharSequence.class,
                authenticationType = Resource.AuthenticationType.APPLICATION,
                shareable = false,
                mappedName = "mapped/serviceResource",
                description = "Primary service resource"
        )
        private CharSequence explicitResource;

        @PostConstruct
        void initialize(@Priority(7) String ignored) {
        }

        @PreDestroy
        void destroy() {
        }

        @RolesAllowed({ "admin", "operator" })
        void restrictedOperation() {
        }

        @PermitAll
        void openOperation() {
        }

        @DenyAll
        void blockedOperation() {
        }
    }

    @Resources({
            @Resource(name = "mailSession", lookup = "java:comp/env/mail/default", description = "Outbound notifications"),
            @Resource(name = "billingQueue", lookup = "java:global/jms/billingQueue")
    })
    @DataSourceDefinitions({
            @DataSourceDefinition(name = "jdbc/audit", className = "org.example.AuditDataSource", loginTimeout = 5),
            @DataSourceDefinition(
                    name = "jdbc/archive",
                    className = "org.example.ArchiveDataSource",
                    properties = { "readOnly=true" }
            )
    })
    private static final class ExplicitContainerAnnotatedComponent {
    }

    @ManagedBean
    private static final class DefaultManagedBean {
    }

    @DataSourceDefinition(name = "jdbc/minimal", className = "org.example.MinimalDataSource")
    private static final class MinimalDataSourceComponent {
    }

    @Generated(value = { "codegen", "integration-test" }, date = "2026-04-16", comments = "compile-only marker")
    private static final class GeneratedComponent {
    }
}
