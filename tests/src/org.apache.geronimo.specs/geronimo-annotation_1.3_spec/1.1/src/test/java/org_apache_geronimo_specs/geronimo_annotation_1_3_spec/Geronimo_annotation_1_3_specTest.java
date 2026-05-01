/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_geronimo_specs.geronimo_annotation_1_3_spec;

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
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
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
    void managedBeanPriorityAndResourceAnnotationsExposeConfiguredValues()
            throws NoSuchFieldException, NoSuchMethodException {
        ManagedBean managedBean = annotation(OrderProcessor.class, ManagedBean.class);
        Priority priority = annotation(OrderProcessor.class, Priority.class);
        Resource classResource = annotation(OrderProcessor.class, Resource.class);
        Resource fieldResource = annotation(field(OrderProcessor.class, "queueName"), Resource.class);
        Resource methodResource = annotation(method(OrderProcessor.class, "executor"), Resource.class);
        Priority constructorPriority = annotation(
                constructor(PrioritizedCommand.class, String.class).getParameters()[0], Priority.class);

        assertThat(managedBean.value()).isEqualTo("orderProcessor");
        assertThat(priority.value()).isEqualTo(25);

        assertThat(classResource.name()).isEqualTo("java:comp/env/applicationName");
        assertThat(classResource.type()).isEqualTo(String.class);
        assertThat(classResource.authenticationType()).isEqualTo(Resource.AuthenticationType.CONTAINER);
        assertThat(classResource.shareable()).isTrue();
        assertThat(classResource.mappedName()).isEmpty();
        assertThat(classResource.description()).isEqualTo("application display name");
        assertThat(classResource.lookup()).isEqualTo("java:app/AppName");

        assertThat(fieldResource.name()).isEqualTo("queue/orders");
        assertThat(fieldResource.type()).isEqualTo(String.class);
        assertThat(fieldResource.lookup()).isEqualTo("java:comp/env/queue/orders");

        assertThat(methodResource.name()).isEqualTo("executorService");
        assertThat(methodResource.type()).isEqualTo(Executor.class);
        assertThat(methodResource.authenticationType()).isEqualTo(Resource.AuthenticationType.APPLICATION);
        assertThat(methodResource.shareable()).isFalse();
        assertThat(methodResource.mappedName()).isEqualTo("mapped/executor");
        assertThat(methodResource.description()).isEqualTo("task executor for background work");
        assertThat(methodResource.lookup()).isEqualTo("java:comp/env/executor");

        assertThat(constructorPriority.value()).isEqualTo(7);
        assertThat(Resource.AuthenticationType.values())
                .containsExactly(Resource.AuthenticationType.CONTAINER, Resource.AuthenticationType.APPLICATION);
        assertThat(Resource.AuthenticationType.valueOf("APPLICATION"))
                .isEqualTo(Resource.AuthenticationType.APPLICATION);
    }

    @Test
    void resourceDefaultsAndContainerAnnotationPreserveAllEntries() throws NoSuchFieldException {
        ManagedBean managedBean = annotation(DefaultManagedResource.class, ManagedBean.class);
        Resource defaultResource = annotation(field(DefaultManagedResource.class, "defaultInjectedResource"),
                Resource.class);
        Resources resources = annotation(ResourceCollection.class, Resources.class);

        assertThat(managedBean.value()).isEmpty();

        assertThat(defaultResource.name()).isEmpty();
        assertThat(defaultResource.type()).isEqualTo(Object.class);
        assertThat(defaultResource.authenticationType()).isEqualTo(Resource.AuthenticationType.CONTAINER);
        assertThat(defaultResource.shareable()).isTrue();
        assertThat(defaultResource.mappedName()).isEmpty();
        assertThat(defaultResource.description()).isEmpty();
        assertThat(defaultResource.lookup()).isEmpty();

        assertThat(resources.value()).hasSize(3);
        assertThat(resources.value()[0].name()).isEqualTo("jdbc/primary");
        assertThat(resources.value()[0].type()).isEqualTo(CharSequence.class);
        assertThat(resources.value()[0].authenticationType()).isEqualTo(Resource.AuthenticationType.APPLICATION);
        assertThat(resources.value()[0].shareable()).isFalse();
        assertThat(resources.value()[0].mappedName()).isEqualTo("mapped/primary");
        assertThat(resources.value()[0].description()).isEqualTo("Primary JDBC resource");
        assertThat(resources.value()[0].lookup()).isEqualTo("java:comp/env/jdbc/primary");

        assertThat(resources.value()[1].name()).isEqualTo("mail/session");
        assertThat(resources.value()[1].type()).isEqualTo(Object.class);
        assertThat(resources.value()[1].authenticationType()).isEqualTo(Resource.AuthenticationType.CONTAINER);
        assertThat(resources.value()[1].shareable()).isTrue();
        assertThat(resources.value()[1].description()).isEqualTo("Mail session resource");
        assertThat(resources.value()[1].lookup()).isEqualTo("java:comp/env/mail/session");

        assertThat(resources.value()[2].name()).isEqualTo("config/count");
        assertThat(resources.value()[2].type()).isEqualTo(Integer.class);
        assertThat(resources.value()[2].lookup()).isEqualTo("java:global/configCount");
    }

    @Test
    void lifecycleAndSecurityAnnotationsRemainVisibleAtRuntime() throws NoSuchMethodException {
        DeclareRoles declareRoles = annotation(SecureLifecycleService.class, DeclareRoles.class);
        RunAs runAs = annotation(SecureLifecycleService.class, RunAs.class);
        RolesAllowed rolesAllowed = annotation(method(SecureLifecycleService.class, "adminOnly"), RolesAllowed.class);

        assertThat(declareRoles.value()).containsExactly("admin", "auditor", "support");
        assertThat(runAs.value()).isEqualTo("system");
        assertThat(hasAnnotation(SecureLifecycleService.class, PermitAll.class)).isTrue();
        assertThat(hasAnnotation(method(SecureLifecycleService.class, "initialize"), PostConstruct.class)).isTrue();
        assertThat(hasAnnotation(method(SecureLifecycleService.class, "destroy"), PreDestroy.class)).isTrue();
        assertThat(rolesAllowed.value()).containsExactly("admin", "support");
        assertThat(hasAnnotation(method(SecureLifecycleService.class, "blocked"), DenyAll.class)).isTrue();
        assertThat(hasAnnotation(method(SecureLifecycleService.class, "health"), PermitAll.class)).isTrue();
    }

    @Test
    void dataSourceDefinitionExposesConfiguredAndDefaultAttributes() {
        DataSourceDefinition explicitDefinition = annotation(SingleDataSourceComponent.class,
                DataSourceDefinition.class);
        DataSourceDefinitions definitionContainer = annotation(MultipleDataSourcesComponent.class,
                DataSourceDefinitions.class);
        DataSourceDefinition defaultDefinition = definitionContainer.value()[1];

        assertThat(explicitDefinition.name()).isEqualTo("jdbc/analytics");
        assertThat(explicitDefinition.className()).isEqualTo("org.example.AnalyticsDataSource");
        assertThat(explicitDefinition.description()).isEqualTo("analytics reporting datasource");
        assertThat(explicitDefinition.url()).isEqualTo("jdbc:postgresql://db-host:5432/analytics");
        assertThat(explicitDefinition.user()).isEqualTo("analytics-user");
        assertThat(explicitDefinition.password()).isEqualTo("analytics-secret");
        assertThat(explicitDefinition.databaseName()).isEqualTo("analytics");
        assertThat(explicitDefinition.serverName()).isEqualTo("db-host");
        assertThat(explicitDefinition.portNumber()).isEqualTo(5432);
        assertThat(explicitDefinition.transactional()).isFalse();
        assertThat(explicitDefinition.initialPoolSize()).isEqualTo(1);
        assertThat(explicitDefinition.minPoolSize()).isEqualTo(1);
        assertThat(explicitDefinition.maxPoolSize()).isEqualTo(8);
        assertThat(explicitDefinition.maxIdleTime()).isEqualTo(60);
        assertThat(explicitDefinition.maxStatements()).isEqualTo(32);
        assertThat(explicitDefinition.loginTimeout()).isEqualTo(5);
        assertThat(explicitDefinition.isolationLevel()).isEqualTo(2);
        assertThat(explicitDefinition.properties()).containsExactly("ssl=true", "currentSchema=analytics");

        assertThat(definitionContainer.value()).hasSize(2);
        assertThat(definitionContainer.value()[0].name()).isEqualTo("jdbc/reporting");
        assertThat(definitionContainer.value()[0].className()).isEqualTo("org.example.ReportingDataSource");
        assertThat(definitionContainer.value()[0].url()).isEqualTo("jdbc:h2:mem:reporting");
        assertThat(definitionContainer.value()[0].properties())
                .containsExactly("MODE=PostgreSQL", "TRACE_LEVEL_FILE=0");

        assertThat(defaultDefinition.name()).isEqualTo("jdbc/defaults");
        assertThat(defaultDefinition.className()).isEqualTo("org.example.DefaultSource");
        assertThat(defaultDefinition.description()).isEmpty();
        assertThat(defaultDefinition.url()).isEmpty();
        assertThat(defaultDefinition.user()).isEmpty();
        assertThat(defaultDefinition.password()).isEmpty();
        assertThat(defaultDefinition.databaseName()).isEmpty();
        assertThat(defaultDefinition.serverName()).isEqualTo("localhost");
        assertThat(defaultDefinition.portNumber()).isEqualTo(-1);
        assertThat(defaultDefinition.transactional()).isTrue();
        assertThat(defaultDefinition.initialPoolSize()).isEqualTo(-1);
        assertThat(defaultDefinition.minPoolSize()).isEqualTo(-1);
        assertThat(defaultDefinition.maxPoolSize()).isEqualTo(-1);
        assertThat(defaultDefinition.maxIdleTime()).isEqualTo(-1);
        assertThat(defaultDefinition.maxStatements()).isEqualTo(-1);
        assertThat(defaultDefinition.loginTimeout()).isZero();
        assertThat(defaultDefinition.isolationLevel()).isEqualTo(-1);
        assertThat(defaultDefinition.properties()).isEmpty();
    }

    @Test
    void generatedAnnotationIsSourceRetainedAcrossSupportedTargets()
            throws NoSuchFieldException, NoSuchMethodException {
        assertThat(annotation(GeneratedType.class, Generated.class)).isNull();
        assertThat(annotation(GeneratedMarker.class, Generated.class)).isNull();
        assertThat(annotation(field(GeneratedType.class, "generatedField"), Generated.class)).isNull();
        assertThat(annotation(method(GeneratedType.class, "generatedMethod"), Generated.class)).isNull();
        assertThat(annotation(constructor(GeneratedType.class, String.class), Generated.class)).isNull();
    }

    @Test
    void annotationTypeMetaAnnotationsMatchTheSpecification() {
        assertRuntimeRetention(ManagedBean.class);
        assertRuntimeRetention(Priority.class);
        assertRuntimeRetention(Resource.class);
        assertRuntimeRetention(Resources.class);
        assertRuntimeRetention(PostConstruct.class);
        assertRuntimeRetention(PreDestroy.class);
        assertRuntimeRetention(DeclareRoles.class);
        assertRuntimeRetention(DenyAll.class);
        assertRuntimeRetention(PermitAll.class);
        assertRuntimeRetention(RolesAllowed.class);
        assertRuntimeRetention(RunAs.class);
        assertRuntimeRetention(DataSourceDefinition.class);
        assertRuntimeRetention(DataSourceDefinitions.class);
        assertSourceRetention(Generated.class);

        assertTarget(ManagedBean.class, TYPE);
        assertTarget(Priority.class, TYPE, PARAMETER);
        assertTarget(Resource.class, TYPE, FIELD, METHOD);
        assertTarget(Resources.class, TYPE);
        assertTarget(PostConstruct.class, METHOD);
        assertTarget(PreDestroy.class, METHOD);
        assertTarget(DeclareRoles.class, TYPE);
        assertTarget(DenyAll.class, TYPE, METHOD);
        assertTarget(PermitAll.class, TYPE, METHOD);
        assertTarget(RolesAllowed.class, TYPE, METHOD);
        assertTarget(RunAs.class, TYPE);
        assertTarget(DataSourceDefinition.class, TYPE);
        assertTarget(DataSourceDefinitions.class, TYPE);
        assertTarget(Generated.class, PACKAGE, TYPE, ANNOTATION_TYPE, METHOD, CONSTRUCTOR, FIELD,
                LOCAL_VARIABLE, PARAMETER);

        assertNotDocumented(ManagedBean.class);
        assertNotDocumented(Resource.class);
        assertNotDocumented(DataSourceDefinition.class);
        assertNotDocumented(DataSourceDefinitions.class);
        assertDocumented(Priority.class);
        assertDocumented(Resources.class);
        assertDocumented(PostConstruct.class);
        assertDocumented(PreDestroy.class);
        assertDocumented(DeclareRoles.class);
        assertDocumented(DenyAll.class);
        assertDocumented(PermitAll.class);
        assertDocumented(RolesAllowed.class);
        assertDocumented(RunAs.class);
        assertDocumented(Generated.class);
    }

    @Test
    void annotationMemberContractsExposeRequiredElementsAndDefaults() throws NoSuchMethodException {
        assertThat(method(Generated.class, "value").getReturnType()).isEqualTo(String[].class);
        assertThat(method(Generated.class, "value").getDefaultValue()).isNull();
        assertThat(method(Generated.class, "date").getReturnType()).isEqualTo(String.class);
        assertThat(method(Generated.class, "date").getDefaultValue()).isEqualTo("");
        assertThat(method(Generated.class, "comments").getReturnType()).isEqualTo(String.class);
        assertThat(method(Generated.class, "comments").getDefaultValue()).isEqualTo("");

        assertThat(method(ManagedBean.class, "value").getDefaultValue()).isEqualTo("");
        assertThat(method(Priority.class, "value").getDefaultValue()).isNull();
        assertThat(method(DeclareRoles.class, "value").getDefaultValue()).isNull();
        assertThat(method(RolesAllowed.class, "value").getDefaultValue()).isNull();
        assertThat(method(RunAs.class, "value").getDefaultValue()).isNull();

        assertThat(method(Resource.class, "name").getDefaultValue()).isEqualTo("");
        assertThat(method(Resource.class, "type").getDefaultValue()).isEqualTo(Object.class);
        assertThat(method(Resource.class, "authenticationType").getDefaultValue())
                .isEqualTo(Resource.AuthenticationType.CONTAINER);
        assertThat(method(Resource.class, "shareable").getDefaultValue()).isEqualTo(true);
        assertThat(method(Resource.class, "mappedName").getDefaultValue()).isEqualTo("");
        assertThat(method(Resource.class, "description").getDefaultValue()).isEqualTo("");
        assertThat(method(Resource.class, "lookup").getDefaultValue()).isEqualTo("");
    }

    @Test
    void runtimeAnnotationsAreNotInheritedBySubclassesOrOverrides() throws NoSuchMethodException {
        assertThat(annotation(DerivedOrderProcessor.class, ManagedBean.class)).isNull();
        assertThat(annotation(DerivedOrderProcessor.class, Priority.class)).isNull();
        assertThat(annotation(DerivedOrderProcessor.class, RunAs.class)).isNull();
        assertThat(annotation(DerivedOrderProcessor.class, DataSourceDefinition.class)).isNull();
        assertThat(hasAnnotation(DerivedOrderProcessor.class, DenyAll.class)).isFalse();

        Method adminOperation = method(DerivedOrderProcessor.class, "adminOperation");
        Method loadConfig = method(DerivedOrderProcessor.class, "loadConfig");

        assertThat(annotation(adminOperation, RolesAllowed.class)).isNull();
        assertThat(hasAnnotation(loadConfig, PermitAll.class)).isFalse();
        assertThat(annotation(loadConfig, Resource.class)).isNull();
    }

    @Test
    void arrayValuedAnnotationMembersReturnIndependentCopies() {
        DataSourceDefinition dataSourceDefinition = annotation(DefensiveCopyDataSource.class,
                DataSourceDefinition.class);
        String[] properties = dataSourceDefinition.properties();
        properties[0] = "mutated=true";

        DeclareRoles declareRoles = annotation(DefensiveCopySecurity.class, DeclareRoles.class);
        String[] roles = declareRoles.value();
        roles[1] = "guest";

        Resources resources = annotation(DefensiveCopyResources.class, Resources.class);
        Resource[] resourceEntries = resources.value();
        resourceEntries[0] = resourceEntries[1];

        assertThat(dataSourceDefinition.properties()).containsExactly("schema=inventory", "readOnly=false");
        assertThat(declareRoles.value()).containsExactly("operator", "reviewer");
        assertThat(resources.value()[0].name()).isEqualTo("cache/primary");
        assertThat(resources.value()[1].name()).isEqualTo("cache/backup");
    }

    @Test
    void runtimeAnnotationInstancesFollowTheStandardAnnotationContract() {
        Resource firstResource = annotation(EquivalentResourceOne.class, Resource.class);
        Resource secondResource = annotation(EquivalentResourceTwo.class, Resource.class);
        Resource differentResource = annotation(DifferentResource.class, Resource.class);
        DeclareRoles firstRoles = annotation(EquivalentRolesOne.class, DeclareRoles.class);
        DeclareRoles secondRoles = annotation(EquivalentRolesTwo.class, DeclareRoles.class);
        DeclareRoles reorderedRoles = annotation(ReorderedRoles.class, DeclareRoles.class);

        assertThat(firstResource.annotationType()).isEqualTo(Resource.class);
        assertThat(firstResource).isEqualTo(secondResource);
        assertThat(firstResource.hashCode()).isEqualTo(secondResource.hashCode());
        assertThat(firstResource).isNotEqualTo(differentResource);
        assertThat(firstResource.toString()).contains("@javax.annotation.Resource", "name=\"jms/orders\"");

        assertThat(firstRoles.annotationType()).isEqualTo(DeclareRoles.class);
        assertThat(firstRoles).isEqualTo(secondRoles);
        assertThat(firstRoles.hashCode()).isEqualTo(secondRoles.hashCode());
        assertThat(firstRoles).isNotEqualTo(reorderedRoles);
    }

    // Checkstyle: allow direct annotation access
    private static <A extends Annotation> A annotation(AnnotatedElement element, Class<A> annotationType) {
        A[] annotations = element.getAnnotationsByType(annotationType);
        return annotations.length == 0 ? null : annotations[0];
    }

    private static boolean hasAnnotation(AnnotatedElement element, Class<? extends Annotation> annotationType) {
        return element.getAnnotationsByType(annotationType).length > 0;
    }
    // Checkstyle: disallow direct annotation access

    private static Constructor<?> constructor(Class<?> type, Class<?>... parameterTypes) throws NoSuchMethodException {
        return type.getDeclaredConstructor(parameterTypes);
    }

    private static Field field(Class<?> type, String name) throws NoSuchFieldException {
        return type.getDeclaredField(name);
    }

    private static Method method(Class<?> type, String name) throws NoSuchMethodException {
        return type.getDeclaredMethod(name);
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

    private static void assertTarget(Class<? extends Annotation> annotationType, ElementType... expectedTargets) {
        Target target = annotation(annotationType, Target.class);
        assertThat(target).isNotNull();
        assertThat(target.value()).containsExactlyInAnyOrder(expectedTargets);
    }

    private static void assertDocumented(Class<? extends Annotation> annotationType) {
        assertThat(hasAnnotation(annotationType, Documented.class)).isTrue();
    }

    private static void assertNotDocumented(Class<? extends Annotation> annotationType) {
        assertThat(hasAnnotation(annotationType, Documented.class)).isFalse();
    }

    @ManagedBean("orderProcessor")
    @Priority(25)
    @Resource(name = "java:comp/env/applicationName", type = String.class, description = "application display name",
            lookup = "java:app/AppName")
    private static class OrderProcessor {
        @Resource(name = "queue/orders", type = String.class, lookup = "java:comp/env/queue/orders")
        private String queueName;

        @Resource(name = "executorService", type = Executor.class,
                authenticationType = Resource.AuthenticationType.APPLICATION, shareable = false,
                mappedName = "mapped/executor", description = "task executor for background work",
                lookup = "java:comp/env/executor")
        Executor executor() {
            return Runnable::run;
        }
    }

    private static final class PrioritizedCommand {
        private PrioritizedCommand(@Priority(7) String commandName) {
            assertThat(commandName).isNotEmpty();
        }
    }

    @ManagedBean
    private static final class DefaultManagedResource {
        @Resource
        private Object defaultInjectedResource;
    }

    @Resources({
            @Resource(name = "jdbc/primary", type = CharSequence.class,
                    authenticationType = Resource.AuthenticationType.APPLICATION, shareable = false,
                    description = "Primary JDBC resource", mappedName = "mapped/primary",
                    lookup = "java:comp/env/jdbc/primary"),
            @Resource(name = "mail/session", description = "Mail session resource",
                    lookup = "java:comp/env/mail/session"),
            @Resource(name = "config/count", type = Integer.class, lookup = "java:global/configCount")
    })
    private static final class ResourceCollection {
    }

    @DeclareRoles({"admin", "auditor", "support"})
    @RunAs("system")
    @PermitAll
    private static final class SecureLifecycleService {
        @PostConstruct
        void initialize() {
        }

        @PreDestroy
        void destroy() {
        }

        @RolesAllowed({"admin", "support"})
        void adminOnly() {
        }

        @DenyAll
        void blocked() {
        }

        @PermitAll
        void health() {
        }
    }

    @DataSourceDefinition(name = "jdbc/analytics", className = "org.example.AnalyticsDataSource",
            databaseName = "analytics", description = "analytics reporting datasource",
            password = "analytics-secret", serverName = "db-host",
            url = "jdbc:postgresql://db-host:5432/analytics", user = "analytics-user",
            properties = {"ssl=true", "currentSchema=analytics"}, transactional = false,
            initialPoolSize = 1, isolationLevel = 2, loginTimeout = 5, maxIdleTime = 60,
            maxPoolSize = 8, maxStatements = 32, minPoolSize = 1, portNumber = 5432)
    private static final class SingleDataSourceComponent {
    }

    @DataSourceDefinitions({
            @DataSourceDefinition(name = "jdbc/reporting", className = "org.example.ReportingDataSource",
                    url = "jdbc:h2:mem:reporting", properties = {"MODE=PostgreSQL", "TRACE_LEVEL_FILE=0"}),
            @DataSourceDefinition(name = "jdbc/defaults", className = "org.example.DefaultSource")
    })
    private static final class MultipleDataSourcesComponent {
    }

    @Generated("generated-annotation-type")
    private @interface GeneratedMarker {
    }

    @Generated(value = {"metadata-forge", "generator"}, date = "2026-05-01", comments = "compile-time only")
    private static final class GeneratedType {
        @Generated("generated-field")
        private final String generatedField;

        @Generated("generated-constructor")
        private GeneratedType(@Generated("generated-parameter") String value) {
            @Generated("generated-local-variable")
            String localValue = value;
            this.generatedField = localValue;
        }

        @Generated("generated-method")
        private String generatedMethod() {
            return generatedField;
        }
    }

    @ManagedBean("parentBean")
    @Priority(10)
    @RunAs("system-parent")
    @DenyAll
    @DataSourceDefinition(name = "jdbc/parent", className = "org.example.ParentDataSource")
    private static class BaseOrderProcessor {
        @RolesAllowed("admin")
        void adminOperation() {
        }

        @PermitAll
        @Resource(name = "service/parent", type = Integer.class)
        Integer loadConfig() {
            return 1;
        }
    }

    private static final class DerivedOrderProcessor extends BaseOrderProcessor {
        @Override
        void adminOperation() {
        }

        @Override
        Integer loadConfig() {
            return 2;
        }
    }

    @DataSourceDefinition(name = "jdbc/copy-safe", className = "org.example.CopySafeDataSource",
            properties = {"schema=inventory", "readOnly=false"})
    private static final class DefensiveCopyDataSource {
    }

    @DeclareRoles({"operator", "reviewer"})
    private static final class DefensiveCopySecurity {
    }

    @Resources({
            @Resource(name = "cache/primary", type = String.class, lookup = "java:comp/env/cache/primary"),
            @Resource(name = "cache/backup", type = String.class, lookup = "java:comp/env/cache/backup")
    })
    private static final class DefensiveCopyResources {
    }

    @Resource(name = "jms/orders", type = String.class, lookup = "java:comp/env/jms/orders")
    private static final class EquivalentResourceOne {
    }

    @Resource(name = "jms/orders", type = String.class, lookup = "java:comp/env/jms/orders")
    private static final class EquivalentResourceTwo {
    }

    @Resource(name = "jms/audit", type = String.class, lookup = "java:comp/env/jms/audit")
    private static final class DifferentResource {
    }

    @DeclareRoles({"auditor", "operator"})
    private static final class EquivalentRolesOne {
    }

    @DeclareRoles({"auditor", "operator"})
    private static final class EquivalentRolesTwo {
    }

    @DeclareRoles({"operator", "auditor"})
    private static final class ReorderedRoles {
    }
}
