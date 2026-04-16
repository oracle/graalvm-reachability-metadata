/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package jakarta_annotation.jakarta_annotation_api;

import java.lang.annotation.Annotation;
import java.util.EnumSet;

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

@Generated(value = { "metadata-forge", "tests" }, comments = "source-retained usage")
@ManagedBean("jakarta-annotation-api-test")
@Priority(100)
class Jakarta_annotation_apiTest {
    @Test
    void annotatedComponentsRemainPlainJavaTypes() {
        ResourceDrivenComponent component = new ResourceDrivenComponent(7);
        ContainerManagedResources managedResources = new ContainerManagedResources();
        RepeatedDataSourceComponent repeatedDataSources = new RepeatedDataSourceComponent();
        ContainerDataSourceComponent containerDataSources = new ContainerDataSourceComponent();

        component.initialize();

        assertThat(component.publicEndpoint()).isEqualTo("ready:7");
        assertThat(component.protectedEndpoint("admin")).isEqualTo("admin:true");
        assertThat(component.hiddenEndpoint()).isEqualTo("hidden:running");
        assertThat(component.injectedStore().toString()).isEqualTo("ready");
        assertThat(managedResources.resourceNames()).containsExactly("mail/session", "queue/orders");
        assertThat(repeatedDataSources.urls()).containsExactly("jdbc:h2:mem:primary", "jdbc:h2:mem:audit");
        assertThat(containerDataSources.poolSummary()).isEqualTo("1:5:2:30");

        component.shutdown();

        assertThat(component.hiddenEndpoint()).isEqualTo("hidden:stopped");
    }

    @Test
    void simpleAnnotationsExposeConfiguredMembers() {
        Generated generated = new GeneratedLiteral(new String[] {"generator-a", "generator-b"}, "2026-04-16", "integration-test");
        ManagedBean managedBean = new ManagedBeanLiteral("inventoryBean");
        Priority priority = new PriorityLiteral(25);
        PostConstruct postConstruct = new PostConstructLiteral();
        PreDestroy preDestroy = new PreDestroyLiteral();

        assertThat(generated.value()).containsExactly("generator-a", "generator-b");
        assertThat(generated.date()).isEqualTo("2026-04-16");
        assertThat(generated.comments()).isEqualTo("integration-test");
        assertThat(generated.annotationType()).isSameAs(Generated.class);

        assertThat(managedBean.value()).isEqualTo("inventoryBean");
        assertThat(managedBean.annotationType()).isSameAs(ManagedBean.class);

        assertThat(priority.value()).isEqualTo(25);
        assertThat(priority.annotationType()).isSameAs(Priority.class);

        assertThat(postConstruct.annotationType()).isSameAs(PostConstruct.class);
        assertThat(preDestroy.annotationType()).isSameAs(PreDestroy.class);
    }

    @Test
    void resourceAnnotationsAndAuthenticationTypesExposeConfiguration() {
        Resource primaryResource = new ResourceLiteral(
                "jdbc/primary",
                "java:comp/env/jdbc/primary",
                StringBuilder.class,
                Resource.AuthenticationType.APPLICATION,
                false,
                "primaryStore",
                "primary in-memory store");
        Resource auditResource = new ResourceLiteral(
                "jdbc/audit",
                "java:comp/env/jdbc/audit",
                String.class,
                Resource.AuthenticationType.CONTAINER,
                true,
                "auditStore",
                "audit trail store");
        Resources resources = new ResourcesLiteral(primaryResource, auditResource);

        assertThat(EnumSet.allOf(Resource.AuthenticationType.class))
                .containsExactly(Resource.AuthenticationType.CONTAINER, Resource.AuthenticationType.APPLICATION);
        assertThat(Resource.AuthenticationType.valueOf("APPLICATION")).isSameAs(Resource.AuthenticationType.APPLICATION);

        assertThat(primaryResource.name()).isEqualTo("jdbc/primary");
        assertThat(primaryResource.lookup()).isEqualTo("java:comp/env/jdbc/primary");
        assertThat(primaryResource.type()).isSameAs(StringBuilder.class);
        assertThat(primaryResource.authenticationType()).isSameAs(Resource.AuthenticationType.APPLICATION);
        assertThat(primaryResource.shareable()).isFalse();
        assertThat(primaryResource.mappedName()).isEqualTo("primaryStore");
        assertThat(primaryResource.description()).isEqualTo("primary in-memory store");
        assertThat(primaryResource.annotationType()).isSameAs(Resource.class);

        assertThat(resources.value()).containsExactly(primaryResource, auditResource);
        assertThat(resources.annotationType()).isSameAs(Resources.class);
    }

    @Test
    void minimalResourceUsesDocumentedDefaultSettings() {
        Resource minimal = new ResourceLiteral("jdbc/default");

        assertThat(minimal.name()).isEqualTo("jdbc/default");
        assertThat(minimal.lookup()).isEmpty();
        assertThat(minimal.type()).isSameAs(Object.class);
        assertThat(minimal.authenticationType()).isSameAs(Resource.AuthenticationType.CONTAINER);
        assertThat(minimal.shareable()).isTrue();
        assertThat(minimal.mappedName()).isEmpty();
        assertThat(minimal.description()).isEmpty();
        assertThat(minimal.annotationType()).isSameAs(Resource.class);
    }

    @Test
    void securityAnnotationsExposeRoleConfiguration() {
        DeclareRoles declareRoles = new DeclareRolesLiteral("admin", "auditor");
        PermitAll permitAll = new PermitAllLiteral();
        DenyAll denyAll = new DenyAllLiteral();
        RolesAllowed rolesAllowed = new RolesAllowedLiteral("admin", "auditor");
        RunAs runAs = new RunAsLiteral("system");

        assertThat(declareRoles.value()).containsExactly("admin", "auditor");
        assertThat(declareRoles.annotationType()).isSameAs(DeclareRoles.class);

        assertThat(permitAll.annotationType()).isSameAs(PermitAll.class);
        assertThat(denyAll.annotationType()).isSameAs(DenyAll.class);

        assertThat(rolesAllowed.value()).containsExactly("admin", "auditor");
        assertThat(rolesAllowed.annotationType()).isSameAs(RolesAllowed.class);

        assertThat(runAs.value()).isEqualTo("system");
        assertThat(runAs.annotationType()).isSameAs(RunAs.class);
    }

    @Test
    void dataSourceAnnotationsExposeConnectionAndPoolSettings() {
        DataSourceDefinition primary = new DataSourceDefinitionLiteral(
                "java:app/jdbc/primary",
                "org.h2.jdbcx.JdbcDataSource",
                "primary pool",
                "jdbc:h2:mem:primary",
                "sa",
                "secret",
                "primary",
                9092,
                "db-primary",
                8,
                true,
                1,
                5,
                1,
                30,
                64,
                new String[] {"schema=PUBLIC", "mode=PostgreSQL"},
                15);
        DataSourceDefinition audit = new DataSourceDefinitionLiteral(
                "java:app/jdbc/audit",
                "org.h2.jdbcx.JdbcDataSource",
                "audit pool",
                "jdbc:h2:mem:audit",
                "audit",
                "audit-secret",
                "audit",
                9093,
                "db-audit",
                2,
                false,
                2,
                8,
                2,
                60,
                32,
                new String[] {"schema=AUDIT"},
                20);
        DataSourceDefinitions definitions = new DataSourceDefinitionsLiteral(primary, audit);

        assertThat(primary.name()).isEqualTo("java:app/jdbc/primary");
        assertThat(primary.className()).isEqualTo("org.h2.jdbcx.JdbcDataSource");
        assertThat(primary.description()).isEqualTo("primary pool");
        assertThat(primary.url()).isEqualTo("jdbc:h2:mem:primary");
        assertThat(primary.user()).isEqualTo("sa");
        assertThat(primary.password()).isEqualTo("secret");
        assertThat(primary.databaseName()).isEqualTo("primary");
        assertThat(primary.portNumber()).isEqualTo(9092);
        assertThat(primary.serverName()).isEqualTo("db-primary");
        assertThat(primary.isolationLevel()).isEqualTo(8);
        assertThat(primary.transactional()).isTrue();
        assertThat(primary.initialPoolSize()).isEqualTo(1);
        assertThat(primary.maxPoolSize()).isEqualTo(5);
        assertThat(primary.minPoolSize()).isEqualTo(1);
        assertThat(primary.maxIdleTime()).isEqualTo(30);
        assertThat(primary.maxStatements()).isEqualTo(64);
        assertThat(primary.properties()).containsExactly("schema=PUBLIC", "mode=PostgreSQL");
        assertThat(primary.loginTimeout()).isEqualTo(15);
        assertThat(primary.annotationType()).isSameAs(DataSourceDefinition.class);

        assertThat(definitions.value()).containsExactly(primary, audit);
        assertThat(definitions.annotationType()).isSameAs(DataSourceDefinitions.class);
    }

    @Test
    void minimalDataSourceDefinitionUsesDocumentedDefaultSettings() {
        DataSourceDefinition minimal = new DataSourceDefinitionLiteral(
                "java:app/jdbc/minimal",
                "org.h2.jdbcx.JdbcDataSource");

        assertThat(minimal.name()).isEqualTo("java:app/jdbc/minimal");
        assertThat(minimal.className()).isEqualTo("org.h2.jdbcx.JdbcDataSource");
        assertThat(minimal.description()).isEmpty();
        assertThat(minimal.url()).isEmpty();
        assertThat(minimal.user()).isEmpty();
        assertThat(minimal.password()).isEmpty();
        assertThat(minimal.databaseName()).isEmpty();
        assertThat(minimal.portNumber()).isEqualTo(-1);
        assertThat(minimal.serverName()).isEqualTo("localhost");
        assertThat(minimal.isolationLevel()).isEqualTo(-1);
        assertThat(minimal.transactional()).isTrue();
        assertThat(minimal.initialPoolSize()).isEqualTo(-1);
        assertThat(minimal.maxPoolSize()).isEqualTo(-1);
        assertThat(minimal.minPoolSize()).isEqualTo(-1);
        assertThat(minimal.maxIdleTime()).isEqualTo(-1);
        assertThat(minimal.maxStatements()).isEqualTo(-1);
        assertThat(minimal.properties()).isEmpty();
        assertThat(minimal.loginTimeout()).isZero();
        assertThat(minimal.annotationType()).isSameAs(DataSourceDefinition.class);
    }

    @Generated(value = "metadata-forge", comments = "source-retained constructor and method usage")
    @ManagedBean("resourceDrivenComponent")
    @DeclareRoles({ "admin", "auditor" })
    @RunAs("system")
    @Priority(20)
    @Resource(name = "jdbc/primary", type = StringBuilder.class, description = "primary store")
    @Resource(
            name = "jdbc/audit",
            lookup = "java:comp/env/jdbc/audit",
            type = String.class,
            authenticationType = Resource.AuthenticationType.APPLICATION,
            shareable = false,
            mappedName = "auditStore",
            description = "audit store")
    static final class ResourceDrivenComponent {
        @Generated("metadata-forge")
        @Resource(
                name = "store/builder",
                lookup = "java:comp/env/store/builder",
                type = StringBuilder.class,
                authenticationType = Resource.AuthenticationType.CONTAINER,
                mappedName = "builder",
                description = "builder backing field")
        private final StringBuilder store = new StringBuilder();

        private final int priority;
        private boolean initialized;
        private boolean stopped;

        @Generated("metadata-forge")
        ResourceDrivenComponent(@Priority(7) int priority) {
            this.priority = priority;
        }

        @PostConstruct
        void initialize() {
            @Generated("metadata-forge")
            String status = "ready";
            store.append(status);
            initialized = true;
        }

        @PreDestroy
        void shutdown() {
            stopped = true;
        }

        @PermitAll
        String publicEndpoint() {
            return store + ":" + priority;
        }

        @RolesAllowed({ "admin", "auditor" })
        String protectedEndpoint(String role) {
            return role + ":" + initialized;
        }

        @DenyAll
        String hiddenEndpoint() {
            return stopped ? "hidden:stopped" : "hidden:running";
        }

        @Resource(
                name = "store/accessor",
                lookup = "java:comp/env/store/accessor",
                type = StringBuilder.class,
                authenticationType = Resource.AuthenticationType.APPLICATION,
                shareable = false,
                mappedName = "storeAccessor",
                description = "builder accessor")
        StringBuilder injectedStore() {
            return store;
        }
    }

    @Resources({
            @Resource(name = "mail/session", type = Object.class, description = "mail session"),
            @Resource(name = "queue/orders", type = Object.class, description = "orders queue")
    })
    static final class ContainerManagedResources {
        String[] resourceNames() {
            return new String[] {"mail/session", "queue/orders"};
        }
    }

    @DataSourceDefinition(
            name = "java:app/jdbc/primary",
            className = "org.h2.jdbcx.JdbcDataSource",
            url = "jdbc:h2:mem:primary",
            user = "sa",
            password = "secret",
            databaseName = "primary",
            serverName = "db-primary",
            initialPoolSize = 1,
            maxPoolSize = 5,
            minPoolSize = 1,
            maxIdleTime = 30,
            properties = { "schema=PUBLIC" })
    @DataSourceDefinition(
            name = "java:app/jdbc/audit",
            className = "org.h2.jdbcx.JdbcDataSource",
            url = "jdbc:h2:mem:audit",
            user = "audit",
            password = "audit-secret",
            databaseName = "audit",
            serverName = "db-audit",
            initialPoolSize = 2,
            maxPoolSize = 8,
            minPoolSize = 2,
            maxIdleTime = 60,
            properties = { "schema=AUDIT" })
    static final class RepeatedDataSourceComponent {
        String[] urls() {
            return new String[] {"jdbc:h2:mem:primary", "jdbc:h2:mem:audit"};
        }
    }

    @DataSourceDefinitions({
            @DataSourceDefinition(
                    name = "java:app/jdbc/reporting",
                    className = "org.h2.jdbcx.JdbcDataSource",
                    initialPoolSize = 1,
                    maxPoolSize = 5,
                    minPoolSize = 2,
                    maxIdleTime = 30),
            @DataSourceDefinition(
                    name = "java:app/jdbc/warehouse",
                    className = "org.h2.jdbcx.JdbcDataSource",
                    initialPoolSize = 3,
                    maxPoolSize = 9,
                    minPoolSize = 3,
                    maxIdleTime = 45)
    })
    static final class ContainerDataSourceComponent {
        String poolSummary() {
            return "1:5:2:30";
        }
    }

    private abstract static class AnnotationLiteral<T extends Annotation> implements Annotation {
        private final Class<T> type;

        private AnnotationLiteral(Class<T> type) {
            this.type = type;
        }

        @Override
        public final Class<? extends Annotation> annotationType() {
            return type;
        }
    }

    private static final class GeneratedLiteral extends AnnotationLiteral<Generated> implements Generated {
        private final String[] value;
        private final String date;
        private final String comments;

        private GeneratedLiteral(String[] value, String date, String comments) {
            super(Generated.class);
            this.value = value.clone();
            this.date = date;
            this.comments = comments;
        }

        @Override
        public String[] value() {
            return value.clone();
        }

        @Override
        public String date() {
            return date;
        }

        @Override
        public String comments() {
            return comments;
        }
    }

    private static final class ManagedBeanLiteral extends AnnotationLiteral<ManagedBean> implements ManagedBean {
        private final String value;

        private ManagedBeanLiteral(String value) {
            super(ManagedBean.class);
            this.value = value;
        }

        @Override
        public String value() {
            return value;
        }
    }

    private static final class PriorityLiteral extends AnnotationLiteral<Priority> implements Priority {
        private final int value;

        private PriorityLiteral(int value) {
            super(Priority.class);
            this.value = value;
        }

        @Override
        public int value() {
            return value;
        }
    }

    private static final class PostConstructLiteral extends AnnotationLiteral<PostConstruct> implements PostConstruct {
        private PostConstructLiteral() {
            super(PostConstruct.class);
        }
    }

    private static final class PreDestroyLiteral extends AnnotationLiteral<PreDestroy> implements PreDestroy {
        private PreDestroyLiteral() {
            super(PreDestroy.class);
        }
    }

    private static final class ResourceLiteral extends AnnotationLiteral<Resource> implements Resource {
        private final String name;
        private final String lookup;
        private final Class<?> type;
        private final AuthenticationType authenticationType;
        private final boolean shareable;
        private final String mappedName;
        private final String description;

        private ResourceLiteral(String name) {
            this(name, "", Object.class, AuthenticationType.CONTAINER, true, "", "");
        }

        private ResourceLiteral(
                String name,
                String lookup,
                Class<?> type,
                AuthenticationType authenticationType,
                boolean shareable,
                String mappedName,
                String description) {
            super(Resource.class);
            this.name = name;
            this.lookup = lookup;
            this.type = type;
            this.authenticationType = authenticationType;
            this.shareable = shareable;
            this.mappedName = mappedName;
            this.description = description;
        }

        @Override
        public String name() {
            return name;
        }

        @Override
        public String lookup() {
            return lookup;
        }

        @Override
        public Class<?> type() {
            return type;
        }

        @Override
        public AuthenticationType authenticationType() {
            return authenticationType;
        }

        @Override
        public boolean shareable() {
            return shareable;
        }

        @Override
        public String mappedName() {
            return mappedName;
        }

        @Override
        public String description() {
            return description;
        }
    }

    private static final class ResourcesLiteral extends AnnotationLiteral<Resources> implements Resources {
        private final Resource[] value;

        private ResourcesLiteral(Resource... value) {
            super(Resources.class);
            this.value = value.clone();
        }

        @Override
        public Resource[] value() {
            return value.clone();
        }
    }

    private static final class DeclareRolesLiteral extends AnnotationLiteral<DeclareRoles> implements DeclareRoles {
        private final String[] value;

        private DeclareRolesLiteral(String... value) {
            super(DeclareRoles.class);
            this.value = value.clone();
        }

        @Override
        public String[] value() {
            return value.clone();
        }
    }

    private static final class PermitAllLiteral extends AnnotationLiteral<PermitAll> implements PermitAll {
        private PermitAllLiteral() {
            super(PermitAll.class);
        }
    }

    private static final class DenyAllLiteral extends AnnotationLiteral<DenyAll> implements DenyAll {
        private DenyAllLiteral() {
            super(DenyAll.class);
        }
    }

    private static final class RolesAllowedLiteral extends AnnotationLiteral<RolesAllowed> implements RolesAllowed {
        private final String[] value;

        private RolesAllowedLiteral(String... value) {
            super(RolesAllowed.class);
            this.value = value.clone();
        }

        @Override
        public String[] value() {
            return value.clone();
        }
    }

    private static final class RunAsLiteral extends AnnotationLiteral<RunAs> implements RunAs {
        private final String value;

        private RunAsLiteral(String value) {
            super(RunAs.class);
            this.value = value;
        }

        @Override
        public String value() {
            return value;
        }
    }

    private static final class DataSourceDefinitionLiteral extends AnnotationLiteral<DataSourceDefinition> implements DataSourceDefinition {
        private final String name;
        private final String className;
        private final String description;
        private final String url;
        private final String user;
        private final String password;
        private final String databaseName;
        private final int portNumber;
        private final String serverName;
        private final int isolationLevel;
        private final boolean transactional;
        private final int initialPoolSize;
        private final int maxPoolSize;
        private final int minPoolSize;
        private final int maxIdleTime;
        private final int maxStatements;
        private final String[] properties;
        private final int loginTimeout;

        private DataSourceDefinitionLiteral(String name, String className) {
            this(name, className, "", "", "", "", "", -1, "localhost", -1, true, -1, -1, -1, -1, -1, new String[0], 0);
        }

        private DataSourceDefinitionLiteral(
                String name,
                String className,
                String description,
                String url,
                String user,
                String password,
                String databaseName,
                int portNumber,
                String serverName,
                int isolationLevel,
                boolean transactional,
                int initialPoolSize,
                int maxPoolSize,
                int minPoolSize,
                int maxIdleTime,
                int maxStatements,
                String[] properties,
                int loginTimeout) {
            super(DataSourceDefinition.class);
            this.name = name;
            this.className = className;
            this.description = description;
            this.url = url;
            this.user = user;
            this.password = password;
            this.databaseName = databaseName;
            this.portNumber = portNumber;
            this.serverName = serverName;
            this.isolationLevel = isolationLevel;
            this.transactional = transactional;
            this.initialPoolSize = initialPoolSize;
            this.maxPoolSize = maxPoolSize;
            this.minPoolSize = minPoolSize;
            this.maxIdleTime = maxIdleTime;
            this.maxStatements = maxStatements;
            this.properties = properties.clone();
            this.loginTimeout = loginTimeout;
        }

        @Override
        public String name() {
            return name;
        }

        @Override
        public String className() {
            return className;
        }

        @Override
        public String description() {
            return description;
        }

        @Override
        public String url() {
            return url;
        }

        @Override
        public String user() {
            return user;
        }

        @Override
        public String password() {
            return password;
        }

        @Override
        public String databaseName() {
            return databaseName;
        }

        @Override
        public int portNumber() {
            return portNumber;
        }

        @Override
        public String serverName() {
            return serverName;
        }

        @Override
        public int isolationLevel() {
            return isolationLevel;
        }

        @Override
        public boolean transactional() {
            return transactional;
        }

        @Override
        public int initialPoolSize() {
            return initialPoolSize;
        }

        @Override
        public int maxPoolSize() {
            return maxPoolSize;
        }

        @Override
        public int minPoolSize() {
            return minPoolSize;
        }

        @Override
        public int maxIdleTime() {
            return maxIdleTime;
        }

        @Override
        public int maxStatements() {
            return maxStatements;
        }

        @Override
        public String[] properties() {
            return properties.clone();
        }

        @Override
        public int loginTimeout() {
            return loginTimeout;
        }
    }

    private static final class DataSourceDefinitionsLiteral extends AnnotationLiteral<DataSourceDefinitions> implements DataSourceDefinitions {
        private final DataSourceDefinition[] value;

        private DataSourceDefinitionsLiteral(DataSourceDefinition... value) {
            super(DataSourceDefinitions.class);
            this.value = value.clone();
        }

        @Override
        public DataSourceDefinition[] value() {
            return value.clone();
        }
    }
}
