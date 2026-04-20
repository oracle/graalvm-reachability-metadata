/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package javax_annotation.javax_annotation_api;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.ElementType.TYPE;
import static org.assertj.core.api.Assertions.assertThat;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
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

class Javax_annotation_apiTest {

    @Test
    void defaultAnnotationsExposeExpectedDefaults() throws Exception {
        ManagedBean managedBean = DefaultManagedBean.class.getAnnotation(ManagedBean.class);
        Resource resource = field(DefaultManagedBean.class, "defaultResource").getAnnotation(Resource.class);

        assertThat(managedBean.value()).isEmpty();
        assertThat(resource.name()).isEmpty();
        assertThat(resource.lookup()).isEmpty();
        assertThat(resource.type()).isEqualTo(Object.class);
        assertThat(resource.authenticationType()).isEqualTo(Resource.AuthenticationType.CONTAINER);
        assertThat(resource.shareable()).isTrue();
        assertThat(resource.mappedName()).isEmpty();
        assertThat(resource.description()).isEmpty();
        assertThat(method(DefaultManagedBean.class, "init").isAnnotationPresent(PostConstruct.class)).isTrue();
        assertThat(method(DefaultManagedBean.class, "destroy").isAnnotationPresent(PreDestroy.class)).isTrue();
    }

    @Test
    void securityAndResourceAnnotationsRetainConfiguredValues() throws Exception {
        ManagedBean managedBean = FullyAnnotatedComponent.class.getAnnotation(ManagedBean.class);
        Priority priority = FullyAnnotatedComponent.class.getAnnotation(Priority.class);
        DeclareRoles declareRoles = FullyAnnotatedComponent.class.getAnnotation(DeclareRoles.class);
        RunAs runAs = FullyAnnotatedComponent.class.getAnnotation(RunAs.class);
        Resource fieldResource = field(FullyAnnotatedComponent.class, "queueName").getAnnotation(Resource.class);
        RolesAllowed rolesAllowed = method(FullyAnnotatedComponent.class, "adminOperation")
                .getAnnotation(RolesAllowed.class);
        Method loadConfig = method(FullyAnnotatedComponent.class, "loadConfig");
        Resource methodResource = loadConfig.getAnnotation(Resource.class);

        assertThat(managedBean.value()).isEqualTo("inventoryBean");
        assertThat(priority.value()).isEqualTo(10);
        assertThat(declareRoles.value()).containsExactly("admin", "auditor");
        assertThat(runAs.value()).isEqualTo("system");
        assertThat(FullyAnnotatedComponent.class.isAnnotationPresent(DenyAll.class)).isTrue();

        assertThat(fieldResource.name()).isEqualTo("queue/orders");
        assertThat(fieldResource.type()).isEqualTo(String.class);
        assertThat(fieldResource.lookup()).isEqualTo("java:comp/env/queue/orders");

        assertThat(rolesAllowed.value()).containsExactly("admin", "operator");
        assertThat(loadConfig.isAnnotationPresent(PermitAll.class)).isTrue();
        assertThat(methodResource.name()).isEqualTo("service/config");
        assertThat(methodResource.type()).isEqualTo(Integer.class);
        assertThat(methodResource.mappedName()).isEqualTo("mapped/config");
    }

    @Test
    void resourceContainersAndEnumExposePublicApi() {
        Resources resources = FullyAnnotatedComponent.class.getAnnotation(Resources.class);

        assertThat(resources.value()).hasSize(2);
        assertThat(resources.value()[0].name()).isEqualTo("jdbc/primary");
        assertThat(resources.value()[0].type()).isEqualTo(CharSequence.class);
        assertThat(resources.value()[0].authenticationType()).isEqualTo(Resource.AuthenticationType.APPLICATION);
        assertThat(resources.value()[0].shareable()).isFalse();
        assertThat(resources.value()[0].description()).isEqualTo("Primary resource");
        assertThat(resources.value()[0].mappedName()).isEqualTo("mapped/primary");
        assertThat(resources.value()[0].lookup()).isEqualTo("java:comp/env/jdbc/primary");

        assertThat(resources.value()[1].name()).isEqualTo("mail/session");
        assertThat(resources.value()[1].type()).isEqualTo(Object.class);
        assertThat(resources.value()[1].authenticationType()).isEqualTo(Resource.AuthenticationType.CONTAINER);
        assertThat(resources.value()[1].shareable()).isTrue();
        assertThat(resources.value()[1].description()).isEqualTo("Mail session");

        assertThat(Resource.AuthenticationType.values())
                .containsExactly(Resource.AuthenticationType.CONTAINER, Resource.AuthenticationType.APPLICATION);
        assertThat(Resource.AuthenticationType.valueOf("APPLICATION"))
                .isEqualTo(Resource.AuthenticationType.APPLICATION);
    }

    @Test
    void dataSourceAnnotationsExposeConfiguredValuesAndDefaults() {
        DataSourceDefinition dataSourceDefinition = FullyAnnotatedComponent.class
                .getAnnotation(DataSourceDefinition.class);
        DataSourceDefinitions dataSourceDefinitions = MultipleDataSources.class
                .getAnnotation(DataSourceDefinitions.class);

        assertThat(dataSourceDefinition.className()).isEqualTo("org.h2.Driver");
        assertThat(dataSourceDefinition.name()).isEqualTo("jdbc/main");
        assertThat(dataSourceDefinition.description()).isEqualTo("Main datasource");
        assertThat(dataSourceDefinition.url()).isEqualTo("jdbc:h2:mem:test");
        assertThat(dataSourceDefinition.user()).isEqualTo("sa");
        assertThat(dataSourceDefinition.password()).isEqualTo("secret");
        assertThat(dataSourceDefinition.databaseName()).isEqualTo("testdb");
        assertThat(dataSourceDefinition.portNumber()).isEqualTo(9092);
        assertThat(dataSourceDefinition.serverName()).isEqualTo("db.example.test");
        assertThat(dataSourceDefinition.isolationLevel()).isEqualTo(2);
        assertThat(dataSourceDefinition.transactional()).isFalse();
        assertThat(dataSourceDefinition.initialPoolSize()).isEqualTo(1);
        assertThat(dataSourceDefinition.maxPoolSize()).isEqualTo(8);
        assertThat(dataSourceDefinition.minPoolSize()).isEqualTo(1);
        assertThat(dataSourceDefinition.maxIdleTime()).isEqualTo(60);
        assertThat(dataSourceDefinition.maxStatements()).isEqualTo(32);
        assertThat(dataSourceDefinition.properties()).containsExactly("ssl=false", "trace=true");
        assertThat(dataSourceDefinition.loginTimeout()).isEqualTo(5);

        assertThat(dataSourceDefinitions.value()).hasSize(2);
        assertThat(dataSourceDefinitions.value()[0].name()).isEqualTo("jdbc/first");
        assertThat(dataSourceDefinitions.value()[1].name()).isEqualTo("jdbc/second");
        assertThat(dataSourceDefinitions.value()[1].serverName()).isEqualTo("localhost");
        assertThat(dataSourceDefinitions.value()[1].portNumber()).isEqualTo(-1);
        assertThat(dataSourceDefinitions.value()[1].transactional()).isTrue();
        assertThat(dataSourceDefinitions.value()[1].properties()).containsExactly("cache=true");
    }

    @Test
    void generatedAnnotationIsSourceRetained() {
        assertThat(GeneratedType.class.getAnnotation(Generated.class)).isNull();
    }

    @Test
    void annotationTypesExposeRetentionPolicies() {
        assertThat(ManagedBean.class.getAnnotation(Retention.class).value()).isEqualTo(RetentionPolicy.RUNTIME);
        assertThat(PostConstruct.class.getAnnotation(Retention.class).value()).isEqualTo(RetentionPolicy.RUNTIME);
        assertThat(PreDestroy.class.getAnnotation(Retention.class).value()).isEqualTo(RetentionPolicy.RUNTIME);
        assertThat(Priority.class.getAnnotation(Retention.class).value()).isEqualTo(RetentionPolicy.RUNTIME);
        assertThat(Resource.class.getAnnotation(Retention.class).value()).isEqualTo(RetentionPolicy.RUNTIME);
        assertThat(Resources.class.getAnnotation(Retention.class).value()).isEqualTo(RetentionPolicy.RUNTIME);
        assertThat(DeclareRoles.class.getAnnotation(Retention.class).value()).isEqualTo(RetentionPolicy.RUNTIME);
        assertThat(DenyAll.class.getAnnotation(Retention.class).value()).isEqualTo(RetentionPolicy.RUNTIME);
        assertThat(PermitAll.class.getAnnotation(Retention.class).value()).isEqualTo(RetentionPolicy.RUNTIME);
        assertThat(RolesAllowed.class.getAnnotation(Retention.class).value()).isEqualTo(RetentionPolicy.RUNTIME);
        assertThat(RunAs.class.getAnnotation(Retention.class).value()).isEqualTo(RetentionPolicy.RUNTIME);
        assertThat(DataSourceDefinition.class.getAnnotation(Retention.class).value()).isEqualTo(RetentionPolicy.RUNTIME);
        assertThat(DataSourceDefinitions.class.getAnnotation(Retention.class).value()).isEqualTo(RetentionPolicy.RUNTIME);
        assertThat(Generated.class.getAnnotation(Retention.class).value()).isEqualTo(RetentionPolicy.SOURCE);
    }

    @Test
    void annotationTypesExposeTargetAndDocumentationContracts() {
        assertThat(Resource.class.getAnnotation(Target.class).value())
                .containsExactlyInAnyOrder(TYPE, METHOD, FIELD);
        assertThat(PostConstruct.class.getAnnotation(Target.class).value()).containsExactly(METHOD);
        assertThat(PreDestroy.class.getAnnotation(Target.class).value()).containsExactly(METHOD);
        assertThat(RolesAllowed.class.getAnnotation(Target.class).value())
                .containsExactlyInAnyOrder(TYPE, METHOD);
        assertThat(DataSourceDefinition.class.getAnnotation(Target.class).value()).containsExactly(TYPE);
        assertThat(Generated.class.getAnnotation(Target.class).value())
                .containsExactly(
                        ElementType.PACKAGE,
                        TYPE,
                        ElementType.ANNOTATION_TYPE,
                        METHOD,
                        ElementType.CONSTRUCTOR,
                        FIELD,
                        ElementType.LOCAL_VARIABLE,
                        ElementType.PARAMETER);

        assertThat(Priority.class.isAnnotationPresent(Documented.class)).isTrue();
        assertThat(Resources.class.isAnnotationPresent(Documented.class)).isTrue();
        assertThat(DeclareRoles.class.isAnnotationPresent(Documented.class)).isTrue();
        assertThat(DenyAll.class.isAnnotationPresent(Documented.class)).isTrue();
        assertThat(PermitAll.class.isAnnotationPresent(Documented.class)).isTrue();
        assertThat(RolesAllowed.class.isAnnotationPresent(Documented.class)).isTrue();
        assertThat(RunAs.class.isAnnotationPresent(Documented.class)).isTrue();
        assertThat(PostConstruct.class.isAnnotationPresent(Documented.class)).isTrue();
        assertThat(PreDestroy.class.isAnnotationPresent(Documented.class)).isTrue();
        assertThat(Generated.class.isAnnotationPresent(Documented.class)).isTrue();
    }

    private static Field field(Class<?> type, String name) throws NoSuchFieldException {
        return type.getDeclaredField(name);
    }

    private static Method method(Class<?> type, String name) throws NoSuchMethodException {
        return type.getDeclaredMethod(name);
    }

    @ManagedBean
    private static final class DefaultManagedBean {

        @Resource
        private Object defaultResource;

        @PostConstruct
        void init() {
        }

        @PreDestroy
        void destroy() {
        }
    }

    @ManagedBean("inventoryBean")
    @Priority(10)
    @DeclareRoles({"admin", "auditor"})
    @RunAs("system")
    @DenyAll
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
    @DataSourceDefinition(
            className = "org.h2.Driver",
            name = "jdbc/main",
            description = "Main datasource",
            url = "jdbc:h2:mem:test",
            user = "sa",
            password = "secret",
            databaseName = "testdb",
            portNumber = 9092,
            serverName = "db.example.test",
            isolationLevel = 2,
            transactional = false,
            initialPoolSize = 1,
            maxPoolSize = 8,
            minPoolSize = 1,
            maxIdleTime = 60,
            maxStatements = 32,
            properties = {"ssl=false", "trace=true"},
            loginTimeout = 5)
    private static final class FullyAnnotatedComponent {

        @Resource(name = "queue/orders", type = String.class, lookup = "java:comp/env/queue/orders")
        private String queueName;

        @RolesAllowed({"admin", "operator"})
        void adminOperation() {
        }

        @PermitAll
        @Resource(name = "service/config", type = Integer.class, mappedName = "mapped/config")
        Integer loadConfig() {
            return 42;
        }
    }

    @DataSourceDefinitions({
            @DataSourceDefinition(className = "org.h2.Driver", name = "jdbc/first"),
            @DataSourceDefinition(className = "org.h2.Driver", name = "jdbc/second", properties = {"cache=true"})
    })
    private static final class MultipleDataSources {
    }

    @Generated(value = {"metadata-forge", "generator"}, date = "2026-04-20", comments = "compile-time only")
    private static final class GeneratedType {
    }
}
