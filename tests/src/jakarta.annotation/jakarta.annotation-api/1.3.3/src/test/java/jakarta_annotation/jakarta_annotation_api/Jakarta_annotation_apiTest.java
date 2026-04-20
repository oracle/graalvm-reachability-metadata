/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package jakarta_annotation.jakarta_annotation_api;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
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

class Jakarta_annotation_apiTest {

    @Test
    void runtimeAnnotationsExposeConfiguredValuesAcrossSupportedTargets() throws Exception {
        ManagedBean managedBean = AnnotatedComponent.class.getAnnotation(ManagedBean.class);
        Priority priority = AnnotatedComponent.class.getAnnotation(Priority.class);
        DeclareRoles declareRoles = AnnotatedComponent.class.getAnnotation(DeclareRoles.class);
        RunAs runAs = AnnotatedComponent.class.getAnnotation(RunAs.class);
        Resource fieldResource = field(AnnotatedComponent.class, "queueName").getAnnotation(Resource.class);
        Resource methodResource = method(AnnotatedComponent.class, "loadConfig").getAnnotation(Resource.class);
        RolesAllowed rolesAllowed = method(AnnotatedComponent.class, "adminOnly").getAnnotation(RolesAllowed.class);
        Priority parameterPriority = constructor(ParameterizedComponent.class, String.class)
                .getParameters()[0]
                .getAnnotation(Priority.class);

        assertThat(managedBean.value()).isEqualTo("inventoryBean");
        assertThat(priority.value()).isEqualTo(10);
        assertThat(declareRoles.value()).containsExactly("admin", "auditor");
        assertThat(runAs.value()).isEqualTo("system");
        assertThat(AnnotatedComponent.class.isAnnotationPresent(DenyAll.class)).isTrue();

        assertThat(fieldResource.name()).isEqualTo("queue/orders");
        assertThat(fieldResource.type()).isEqualTo(String.class);
        assertThat(fieldResource.lookup()).isEqualTo("java:comp/env/queue/orders");

        assertThat(methodResource.name()).isEqualTo("service/config");
        assertThat(methodResource.type()).isEqualTo(Integer.class);
        assertThat(methodResource.mappedName()).isEqualTo("mapped/config");
        assertThat(method(AnnotatedComponent.class, "loadConfig").isAnnotationPresent(PermitAll.class)).isTrue();

        assertThat(rolesAllowed.value()).containsExactly("admin", "operator");
        assertThat(method(LifecycleComponent.class, "init").isAnnotationPresent(PostConstruct.class)).isTrue();
        assertThat(method(LifecycleComponent.class, "destroy").isAnnotationPresent(PreDestroy.class)).isTrue();
        assertThat(parameterPriority.value()).isEqualTo(7);
    }

    @Test
    void defaultAnnotationValuesMatchThePublishedApi() throws Exception {
        ManagedBean managedBean = DefaultManagedBean.class.getAnnotation(ManagedBean.class);
        Resource resource = field(DefaultManagedBean.class, "defaultResource").getAnnotation(Resource.class);
        DataSourceDefinition dataSourceDefinition = DefaultDataSourceHolder.class
                .getAnnotation(DataSourceDefinition.class);

        assertThat(managedBean.value()).isEmpty();

        assertThat(resource.name()).isEmpty();
        assertThat(resource.lookup()).isEmpty();
        assertThat(resource.type()).isEqualTo(Object.class);
        assertThat(resource.authenticationType()).isEqualTo(Resource.AuthenticationType.CONTAINER);
        assertThat(resource.shareable()).isTrue();
        assertThat(resource.mappedName()).isEmpty();
        assertThat(resource.description()).isEmpty();

        assertThat(dataSourceDefinition.className()).isEqualTo("org.h2.Driver");
        assertThat(dataSourceDefinition.name()).isEqualTo("jdbc/default");
        assertThat(dataSourceDefinition.description()).isEmpty();
        assertThat(dataSourceDefinition.url()).isEmpty();
        assertThat(dataSourceDefinition.user()).isEmpty();
        assertThat(dataSourceDefinition.password()).isEmpty();
        assertThat(dataSourceDefinition.databaseName()).isEmpty();
        assertThat(dataSourceDefinition.portNumber()).isEqualTo(-1);
        assertThat(dataSourceDefinition.serverName()).isEqualTo("localhost");
        assertThat(dataSourceDefinition.isolationLevel()).isEqualTo(-1);
        assertThat(dataSourceDefinition.transactional()).isTrue();
        assertThat(dataSourceDefinition.initialPoolSize()).isEqualTo(-1);
        assertThat(dataSourceDefinition.maxPoolSize()).isEqualTo(-1);
        assertThat(dataSourceDefinition.minPoolSize()).isEqualTo(-1);
        assertThat(dataSourceDefinition.maxIdleTime()).isEqualTo(-1);
        assertThat(dataSourceDefinition.maxStatements()).isEqualTo(-1);
        assertThat(dataSourceDefinition.properties()).isEmpty();
        assertThat(dataSourceDefinition.loginTimeout()).isZero();
    }

    @Test
    void repeatableAnnotationsExposeBothDirectAndContainerViews() {
        Resource[] resources = RepeatableResourceComponent.class.getAnnotationsByType(Resource.class);
        Resources resourceContainer = RepeatableResourceComponent.class.getAnnotation(Resources.class);
        DataSourceDefinition[] dataSourceDefinitions = RepeatableDataSourceComponent.class
                .getAnnotationsByType(DataSourceDefinition.class);
        DataSourceDefinitions dataSourceContainer = RepeatableDataSourceComponent.class
                .getAnnotation(DataSourceDefinitions.class);

        assertThat(resources)
                .extracting(Resource::name)
                .containsExactly("jdbc/primary", "mail/session");
        assertThat(resourceContainer.value())
                .extracting(Resource::lookup)
                .containsExactly("java:comp/env/jdbc/primary", "java:comp/env/mail/session");

        assertThat(dataSourceDefinitions)
                .extracting(DataSourceDefinition::name)
                .containsExactly("jdbc/primary", "jdbc/reporting");
        assertThat(dataSourceContainer.value())
                .extracting(DataSourceDefinition::className)
                .containsExactly("org.h2.Driver", "org.h2.Driver");
    }

    @Test
    void explicitResourceContainerFlattensToRepeatableViewAndPreservesConfiguredAttributes() {
        Resources resourceContainer = ExplicitResourceContainerComponent.class.getAnnotation(Resources.class);
        Resource[] resources = ExplicitResourceContainerComponent.class.getAnnotationsByType(Resource.class);

        assertThat(resourceContainer.value()).hasSize(2);
        assertThat(resourceContainer.value()[0].name()).isEqualTo("jdbc/primary");
        assertThat(resourceContainer.value()[0].type()).isEqualTo(CharSequence.class);
        assertThat(resourceContainer.value()[0].authenticationType())
                .isEqualTo(Resource.AuthenticationType.APPLICATION);
        assertThat(resourceContainer.value()[0].shareable()).isFalse();
        assertThat(resourceContainer.value()[0].description()).isEqualTo("Primary resource");
        assertThat(resourceContainer.value()[0].mappedName()).isEqualTo("mapped/primary");
        assertThat(resourceContainer.value()[0].lookup()).isEqualTo("java:comp/env/jdbc/primary");

        assertThat(resourceContainer.value()[1].name()).isEqualTo("mail/session");
        assertThat(resourceContainer.value()[1].type()).isEqualTo(Object.class);
        assertThat(resourceContainer.value()[1].authenticationType())
                .isEqualTo(Resource.AuthenticationType.CONTAINER);
        assertThat(resourceContainer.value()[1].shareable()).isTrue();
        assertThat(resourceContainer.value()[1].description()).isEqualTo("Mail session");

        assertThat(resources)
                .extracting(Resource::name)
                .containsExactly("jdbc/primary", "mail/session");
        assertThat(resources)
                .extracting(Resource::authenticationType)
                .containsExactly(Resource.AuthenticationType.APPLICATION, Resource.AuthenticationType.CONTAINER);
        assertThat(Resource.AuthenticationType.values())
                .containsExactly(Resource.AuthenticationType.CONTAINER, Resource.AuthenticationType.APPLICATION);
        assertThat(Resource.AuthenticationType.valueOf("APPLICATION"))
                .isEqualTo(Resource.AuthenticationType.APPLICATION);
    }

    @Test
    void explicitDataSourceContainerFlattensToRepeatableViewAndPreservesConfiguredAttributes() {
        DataSourceDefinitions dataSourceContainer = ExplicitDataSourceContainerComponent.class
                .getAnnotation(DataSourceDefinitions.class);
        DataSourceDefinition[] dataSourceDefinitions = ExplicitDataSourceContainerComponent.class
                .getAnnotationsByType(DataSourceDefinition.class);

        assertThat(dataSourceContainer.value()).hasSize(2);
        assertThat(dataSourceContainer.value()[0].name()).isEqualTo("jdbc/inventory");
        assertThat(dataSourceContainer.value()[0].className()).isEqualTo("org.postgresql.ds.PGSimpleDataSource");
        assertThat(dataSourceContainer.value()[0].description()).isEqualTo("Inventory data source");
        assertThat(dataSourceContainer.value()[0].url()).isEqualTo("jdbc:postgresql://db.example.test:5432/inventory");
        assertThat(dataSourceContainer.value()[0].user()).isEqualTo("inventory_user");
        assertThat(dataSourceContainer.value()[0].password()).isEqualTo("inventory_secret");
        assertThat(dataSourceContainer.value()[0].databaseName()).isEqualTo("inventory");
        assertThat(dataSourceContainer.value()[0].portNumber()).isEqualTo(5432);
        assertThat(dataSourceContainer.value()[0].serverName()).isEqualTo("db.example.test");
        assertThat(dataSourceContainer.value()[0].isolationLevel()).isEqualTo(2);
        assertThat(dataSourceContainer.value()[0].transactional()).isFalse();
        assertThat(dataSourceContainer.value()[0].initialPoolSize()).isEqualTo(2);
        assertThat(dataSourceContainer.value()[0].maxPoolSize()).isEqualTo(8);
        assertThat(dataSourceContainer.value()[0].minPoolSize()).isEqualTo(1);
        assertThat(dataSourceContainer.value()[0].maxIdleTime()).isEqualTo(30);
        assertThat(dataSourceContainer.value()[0].maxStatements()).isEqualTo(16);
        assertThat(dataSourceContainer.value()[0].properties()).containsExactly("ssl=true", "schema=inventory");
        assertThat(dataSourceContainer.value()[0].loginTimeout()).isEqualTo(5);

        assertThat(dataSourceContainer.value()[1].name()).isEqualTo("jdbc/reporting");
        assertThat(dataSourceContainer.value()[1].className()).isEqualTo("org.h2.jdbcx.JdbcDataSource");
        assertThat(dataSourceContainer.value()[1].description()).isEqualTo("Reporting data source");
        assertThat(dataSourceContainer.value()[1].url()).isEqualTo("jdbc:h2:mem:reporting");
        assertThat(dataSourceContainer.value()[1].user()).isEqualTo("report_user");
        assertThat(dataSourceContainer.value()[1].password()).isEqualTo("report_secret");
        assertThat(dataSourceContainer.value()[1].databaseName()).isEqualTo("reporting");
        assertThat(dataSourceContainer.value()[1].portNumber()).isEqualTo(9092);
        assertThat(dataSourceContainer.value()[1].serverName()).isEqualTo("reporting-host");
        assertThat(dataSourceContainer.value()[1].isolationLevel()).isEqualTo(8);
        assertThat(dataSourceContainer.value()[1].transactional()).isTrue();
        assertThat(dataSourceContainer.value()[1].initialPoolSize()).isEqualTo(1);
        assertThat(dataSourceContainer.value()[1].maxPoolSize()).isEqualTo(4);
        assertThat(dataSourceContainer.value()[1].minPoolSize()).isEqualTo(1);
        assertThat(dataSourceContainer.value()[1].maxIdleTime()).isEqualTo(45);
        assertThat(dataSourceContainer.value()[1].maxStatements()).isEqualTo(32);
        assertThat(dataSourceContainer.value()[1].properties()).containsExactly("MODE=PostgreSQL", "TRACE_LEVEL_FILE=0");
        assertThat(dataSourceContainer.value()[1].loginTimeout()).isEqualTo(10);

        assertThat(dataSourceDefinitions)
                .extracting(DataSourceDefinition::name)
                .containsExactly("jdbc/inventory", "jdbc/reporting");
        assertThat(dataSourceDefinitions)
                .extracting(DataSourceDefinition::serverName)
                .containsExactly("db.example.test", "reporting-host");
        assertThat(dataSourceDefinitions[0].properties()).containsExactly("ssl=true", "schema=inventory");
        assertThat(dataSourceDefinitions[1].properties()).containsExactly("MODE=PostgreSQL", "TRACE_LEVEL_FILE=0");
    }

    @Test
    void generatedIsAvailableAtCompileTimeButNotRetainedAtRuntime() {
        assertThat(GeneratedMarker.class.getAnnotation(Generated.class)).isNull();
    }

    @Test
    void annotationTypesDeclareExpectedMetaAnnotations() {
        assertThat(retention(ManagedBean.class)).isEqualTo(RetentionPolicy.RUNTIME);
        assertThat(retention(Priority.class)).isEqualTo(RetentionPolicy.RUNTIME);
        assertThat(retention(Resource.class)).isEqualTo(RetentionPolicy.RUNTIME);
        assertThat(retention(Resources.class)).isEqualTo(RetentionPolicy.RUNTIME);
        assertThat(retention(PostConstruct.class)).isEqualTo(RetentionPolicy.RUNTIME);
        assertThat(retention(PreDestroy.class)).isEqualTo(RetentionPolicy.RUNTIME);
        assertThat(retention(DeclareRoles.class)).isEqualTo(RetentionPolicy.RUNTIME);
        assertThat(retention(DenyAll.class)).isEqualTo(RetentionPolicy.RUNTIME);
        assertThat(retention(PermitAll.class)).isEqualTo(RetentionPolicy.RUNTIME);
        assertThat(retention(RolesAllowed.class)).isEqualTo(RetentionPolicy.RUNTIME);
        assertThat(retention(RunAs.class)).isEqualTo(RetentionPolicy.RUNTIME);
        assertThat(retention(DataSourceDefinition.class)).isEqualTo(RetentionPolicy.RUNTIME);
        assertThat(retention(DataSourceDefinitions.class)).isEqualTo(RetentionPolicy.RUNTIME);
        assertThat(retention(Generated.class)).isEqualTo(RetentionPolicy.SOURCE);

        assertThat(target(ManagedBean.class)).containsExactly(ElementType.TYPE);
        assertThat(target(Priority.class)).containsExactly(ElementType.TYPE, ElementType.PARAMETER);
        assertThat(target(Resource.class)).containsExactlyInAnyOrder(ElementType.TYPE, ElementType.FIELD, ElementType.METHOD);
        assertThat(target(Resources.class)).containsExactly(ElementType.TYPE);
        assertThat(target(PostConstruct.class)).containsExactly(ElementType.METHOD);
        assertThat(target(PreDestroy.class)).containsExactly(ElementType.METHOD);
        assertThat(target(DeclareRoles.class)).containsExactly(ElementType.TYPE);
        assertThat(target(DenyAll.class)).containsExactly(ElementType.TYPE, ElementType.METHOD);
        assertThat(target(PermitAll.class)).containsExactly(ElementType.TYPE, ElementType.METHOD);
        assertThat(target(RolesAllowed.class)).containsExactly(ElementType.TYPE, ElementType.METHOD);
        assertThat(target(RunAs.class)).containsExactly(ElementType.TYPE);
        assertThat(target(DataSourceDefinition.class)).containsExactly(ElementType.TYPE);
        assertThat(target(DataSourceDefinitions.class)).containsExactly(ElementType.TYPE);
        assertThat(target(Generated.class)).containsExactly(
                ElementType.PACKAGE,
                ElementType.TYPE,
                ElementType.ANNOTATION_TYPE,
                ElementType.METHOD,
                ElementType.CONSTRUCTOR,
                ElementType.FIELD,
                ElementType.LOCAL_VARIABLE,
                ElementType.PARAMETER);

        assertThat(Priority.class.isAnnotationPresent(Documented.class)).isTrue();
        assertThat(Resources.class.isAnnotationPresent(Documented.class)).isTrue();
        assertThat(PostConstruct.class.isAnnotationPresent(Documented.class)).isTrue();
        assertThat(PreDestroy.class.isAnnotationPresent(Documented.class)).isTrue();
        assertThat(DeclareRoles.class.isAnnotationPresent(Documented.class)).isTrue();
        assertThat(DenyAll.class.isAnnotationPresent(Documented.class)).isTrue();
        assertThat(PermitAll.class.isAnnotationPresent(Documented.class)).isTrue();
        assertThat(RolesAllowed.class.isAnnotationPresent(Documented.class)).isTrue();
        assertThat(RunAs.class.isAnnotationPresent(Documented.class)).isTrue();
        assertThat(Generated.class.isAnnotationPresent(Documented.class)).isTrue();

        assertThat(Resource.class.getAnnotation(Repeatable.class).value()).isEqualTo(Resources.class);
        assertThat(DataSourceDefinition.class.getAnnotation(Repeatable.class).value())
                .isEqualTo(DataSourceDefinitions.class);
    }

    private static Constructor<?> constructor(Class<?> type, Class<?>... parameterTypes) throws NoSuchMethodException {
        return type.getDeclaredConstructor(parameterTypes);
    }

    private static Field field(Class<?> type, String name) throws NoSuchFieldException {
        return type.getDeclaredField(name);
    }

    private static Method method(Class<?> type, String name) throws NoSuchMethodException {
        return type.getDeclaredMethod(name);
    }

    private static RetentionPolicy retention(Class<?> annotationType) {
        return annotationType.getAnnotation(Retention.class).value();
    }

    private static ElementType[] target(Class<?> annotationType) {
        return annotationType.getAnnotation(Target.class).value();
    }

    @ManagedBean
    private static final class DefaultManagedBean {

        @Resource
        private Object defaultResource;
    }

    @DataSourceDefinition(className = "org.h2.Driver", name = "jdbc/default")
    private static final class DefaultDataSourceHolder {
    }

    @ManagedBean("inventoryBean")
    @Priority(10)
    @DeclareRoles({"admin", "auditor"})
    @RunAs("system")
    @DenyAll
    private static final class AnnotatedComponent {

        @Resource(name = "queue/orders", type = String.class, lookup = "java:comp/env/queue/orders")
        private String queueName;

        @RolesAllowed({"admin", "operator"})
        void adminOnly() {
        }

        @PermitAll
        @Resource(name = "service/config", type = Integer.class, mappedName = "mapped/config")
        Integer loadConfig() {
            return 42;
        }
    }

    private static final class LifecycleComponent {

        @PostConstruct
        void init() {
        }

        @PreDestroy
        void destroy() {
        }
    }

    private static final class ParameterizedComponent {

        private ParameterizedComponent(@Priority(7) String id) {
        }
    }

    @Resource(name = "jdbc/primary", lookup = "java:comp/env/jdbc/primary")
    @Resource(name = "mail/session", lookup = "java:comp/env/mail/session")
    private static final class RepeatableResourceComponent {
    }

    @Resources({
            @Resource(
                    name = "jdbc/primary",
                    type = CharSequence.class,
                    authenticationType = Resource.AuthenticationType.APPLICATION,
                    shareable = false,
                    description = "Primary resource",
                    mappedName = "mapped/primary",
                    lookup = "java:comp/env/jdbc/primary"),
            @Resource(name = "mail/session", description = "Mail session")
    })
    private static final class ExplicitResourceContainerComponent {
    }

    @DataSourceDefinition(className = "org.h2.Driver", name = "jdbc/primary")
    @DataSourceDefinition(className = "org.h2.Driver", name = "jdbc/reporting")
    private static final class RepeatableDataSourceComponent {
    }

    @DataSourceDefinitions({
            @DataSourceDefinition(
                    name = "jdbc/inventory",
                    className = "org.postgresql.ds.PGSimpleDataSource",
                    description = "Inventory data source",
                    url = "jdbc:postgresql://db.example.test:5432/inventory",
                    user = "inventory_user",
                    password = "inventory_secret",
                    databaseName = "inventory",
                    portNumber = 5432,
                    serverName = "db.example.test",
                    isolationLevel = 2,
                    transactional = false,
                    initialPoolSize = 2,
                    maxPoolSize = 8,
                    minPoolSize = 1,
                    maxIdleTime = 30,
                    maxStatements = 16,
                    properties = {"ssl=true", "schema=inventory"},
                    loginTimeout = 5),
            @DataSourceDefinition(
                    name = "jdbc/reporting",
                    className = "org.h2.jdbcx.JdbcDataSource",
                    description = "Reporting data source",
                    url = "jdbc:h2:mem:reporting",
                    user = "report_user",
                    password = "report_secret",
                    databaseName = "reporting",
                    portNumber = 9092,
                    serverName = "reporting-host",
                    isolationLevel = 8,
                    transactional = true,
                    initialPoolSize = 1,
                    maxPoolSize = 4,
                    minPoolSize = 1,
                    maxIdleTime = 45,
                    maxStatements = 32,
                    properties = {"MODE=PostgreSQL", "TRACE_LEVEL_FILE=0"},
                    loginTimeout = 10)
    })
    private static final class ExplicitDataSourceContainerComponent {
    }

    @Generated(value = {"metadata-forge", "generator"}, date = "2026-04-20", comments = "compile-time only")
    private static final class GeneratedMarker {
    }
}
