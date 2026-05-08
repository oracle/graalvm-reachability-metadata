/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_glassfish_jersey_ext.jersey_entity_filtering;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.annotation.Annotation;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import javax.annotation.security.DenyAll;
import javax.annotation.security.PermitAll;
import javax.annotation.security.RolesAllowed;
import javax.ws.rs.RuntimeType;
import javax.ws.rs.core.Configuration;
import javax.ws.rs.core.Feature;
import javax.ws.rs.core.FeatureContext;

import org.glassfish.jersey.message.filtering.EntityFilteringFeature;
import org.glassfish.jersey.message.filtering.SecurityAnnotations;
import org.glassfish.jersey.message.filtering.SecurityEntityFilteringFeature;
import org.glassfish.jersey.message.filtering.SelectableEntityFilteringFeature;
import org.glassfish.jersey.message.filtering.SelectableEntityProcessor;
import org.glassfish.jersey.message.filtering.SelectableScopeResolver;
import org.glassfish.jersey.message.filtering.spi.EntityGraph;
import org.glassfish.jersey.message.filtering.spi.EntityProcessor;
import org.glassfish.jersey.message.filtering.spi.FilteringHelper;
import org.junit.jupiter.api.Test;

public class Jersey_entity_filteringTest {
    @Test
    void entityFilteringFeatureRegistersServerPipelineOnce() {
        RecordingFeatureContext context = new RecordingFeatureContext(RuntimeType.SERVER);

        boolean configured = new EntityFilteringFeature().configure(context);
        boolean configuredAgain = new EntityFilteringFeature().configure(context);

        assertThat(configured).isTrue();
        assertThat(configuredAgain).isFalse();
        assertThat(context.registeredClassNames())
                .contains(
                        "org.glassfish.jersey.message.filtering.EntityFilteringBinder",
                        "org.glassfish.jersey.message.filtering.EntityFilteringProcessor",
                        "org.glassfish.jersey.message.filtering.DefaultEntityProcessor",
                        "org.glassfish.jersey.message.filtering.EntityFilteringScopeResolver",
                        "org.glassfish.jersey.message.filtering.ServerScopeProvider")
                .doesNotContain("org.glassfish.jersey.message.filtering.CommonScopeProvider");
    }

    @Test
    void entityFilteringFeatureRegistersClientPipelineAndEnabledDetectsRegisteredFeatures() {
        RecordingFeatureContext context = new RecordingFeatureContext(RuntimeType.CLIENT);
        context.register(EntityFilteringFeature.class);

        boolean configured = new EntityFilteringFeature().configure(context);

        assertThat(configured).isTrue();
        assertThat(EntityFilteringFeature.enabled(context.getConfiguration())).isTrue();
        assertThat(context.registeredClassNames())
                .contains(
                        EntityFilteringFeature.class.getName(),
                        "org.glassfish.jersey.message.filtering.EntityFilteringBinder",
                        "org.glassfish.jersey.message.filtering.EntityFilteringProcessor",
                        "org.glassfish.jersey.message.filtering.DefaultEntityProcessor",
                        "org.glassfish.jersey.message.filtering.EntityFilteringScopeResolver",
                        "org.glassfish.jersey.message.filtering.CommonScopeProvider")
                .doesNotContain("org.glassfish.jersey.message.filtering.ServerScopeProvider");
    }

    @Test
    void selectableFeatureRegistersEntityFilteringAndSelectableComponents() {
        RecordingFeatureContext context = new RecordingFeatureContext(RuntimeType.SERVER);
        context.register(SelectableEntityFilteringFeature.class);
        context.property(SelectableEntityFilteringFeature.QUERY_PARAM_NAME, "fields");

        SelectableEntityFilteringFeature feature = new SelectableEntityFilteringFeature();
        boolean configured = feature.configure(context);
        boolean configuredAgain = feature.configure(context);

        assertThat(configured).isTrue();
        assertThat(configuredAgain).isTrue();
        assertThat(EntityFilteringFeature.enabled(context.getConfiguration())).isTrue();
        assertThat(context.getConfiguration().getProperty(SelectableEntityFilteringFeature.QUERY_PARAM_NAME))
                .isEqualTo("fields");
        assertThat(context.registeredClassNames())
                .contains(
                        SelectableEntityFilteringFeature.class.getName(),
                        EntityFilteringFeature.class.getName(),
                        "org.glassfish.jersey.message.filtering.SelectableEntityProcessor",
                        "org.glassfish.jersey.message.filtering.SelectableScopeResolver");
    }

    @Test
    void securityFeatureRegistersAuthorizationAndFilteringComponents() {
        RecordingFeatureContext context = new RecordingFeatureContext(RuntimeType.SERVER);
        context.register(SecurityEntityFilteringFeature.class);

        boolean configured = new SecurityEntityFilteringFeature().configure(context);
        boolean configuredAgain = new SecurityEntityFilteringFeature().configure(context);

        assertThat(configured).isTrue();
        assertThat(configuredAgain).isFalse();
        assertThat(EntityFilteringFeature.enabled(context.getConfiguration())).isTrue();
        assertThat(context.registeredClassNames())
                .contains(
                        SecurityEntityFilteringFeature.class.getName(),
                        "org.glassfish.jersey.server.filter.RolesAllowedDynamicFeature",
                        "org.glassfish.jersey.message.filtering.EntityFilteringBinder",
                        "org.glassfish.jersey.message.filtering.SecurityEntityProcessor",
                        "org.glassfish.jersey.message.filtering.DefaultEntityProcessor",
                        "org.glassfish.jersey.message.filtering.SecurityScopeResolver",
                        "org.glassfish.jersey.message.filtering.SecurityServerScopeResolver",
                        "org.glassfish.jersey.message.filtering.SecurityServerScopeProvider");
    }

    @Test
    void selectableProcessorAddsDefaultAndFieldSpecificScopes() {
        ExposedSelectableEntityProcessor processor = new ExposedSelectableEntityProcessor();
        RecordingEntityGraph graph = new RecordingEntityGraph(SelectableEntity.class);

        EntityProcessor.Result result = processor.processField("name", String.class, graph);

        assertThat(result).isEqualTo(EntityProcessor.Result.APPLY);
        assertThat(graph.getFields(SelectableScopeResolver.DEFAULT_SCOPE)).containsExactly("name");
        assertThat(graph.getFields(SelectableScopeResolver.PREFIX + "name")).containsExactly("name");
        assertThat(graph.getFilteringScopes()).isEmpty();
    }

    @Test
    void selectableProcessorAddsFilterableTypesAsSubgraphs() {
        ExposedSelectableEntityProcessor processor = new ExposedSelectableEntityProcessor();
        RecordingEntityGraph graph = new RecordingEntityGraph(SelectableEntity.class);

        EntityProcessor.Result result = processor.processField("address", Address.class, graph);

        assertThat(result).isEqualTo(EntityProcessor.Result.APPLY);
        assertThat(graph.getSubgraphs(SelectableScopeResolver.DEFAULT_SCOPE)).containsEntry("address", Address.class);
        assertThat(graph.getSubgraphs(SelectableScopeResolver.PREFIX + "address"))
                .containsEntry("address", Address.class);
        assertThat(graph.getFields(SelectableScopeResolver.DEFAULT_SCOPE)).isEmpty();
    }

    @Test
    void securityAnnotationFactoriesCreateRuntimeAnnotations() {
        RolesAllowed rolesAllowed = SecurityAnnotations.rolesAllowed("admin", null, "auditor");
        PermitAll permitAll = SecurityAnnotations.permitAll();
        DenyAll denyAll = SecurityAnnotations.denyAll();

        assertThat(rolesAllowed.annotationType()).isEqualTo(RolesAllowed.class);
        assertThat(rolesAllowed.value()).containsExactly("admin", "auditor");
        assertThat(permitAll.annotationType()).isEqualTo(PermitAll.class);
        assertThat(denyAll.annotationType()).isEqualTo(DenyAll.class);
    }

    @Test
    void filteringHelperDiscoversGetterAndSetterPropertiesAcrossClassHierarchy() {
        Map<String, ?> getterProperties = FilteringHelper.getPropertyMethods(Address.class, true);
        Map<String, ?> setterProperties = FilteringHelper.getPropertyMethods(Address.class, false);

        assertThat(getterProperties.keySet()).contains("city", "country");
        assertThat(setterProperties.keySet()).contains("city", "country");
    }

    private static final class ExposedSelectableEntityProcessor extends SelectableEntityProcessor {
        private EntityProcessor.Result processField(String fieldName, Class<?> fieldClass, EntityGraph graph) {
            Annotation[] noAnnotations = FilteringHelper.EMPTY_ANNOTATIONS;
            return super.process(fieldName, fieldClass, noAnnotations, noAnnotations, graph);
        }
    }

    private static final class RecordingFeatureContext implements FeatureContext, Configuration {
        private final RuntimeType runtimeType;
        private final Map<String, Object> properties = new HashMap<>();
        private final Set<Class<?>> registeredClasses = new LinkedHashSet<>();
        private final Set<Object> registeredInstances = new LinkedHashSet<>();

        private RecordingFeatureContext(RuntimeType runtimeType) {
            this.runtimeType = runtimeType;
        }

        @Override
        public Configuration getConfiguration() {
            return this;
        }

        @Override
        public FeatureContext property(String name, Object value) {
            properties.put(name, value);
            return this;
        }

        @Override
        public FeatureContext register(Class<?> componentClass) {
            registeredClasses.add(componentClass);
            return this;
        }

        @Override
        public FeatureContext register(Class<?> componentClass, int priority) {
            return register(componentClass);
        }

        @Override
        public FeatureContext register(Class<?> componentClass, Class<?>... contracts) {
            return register(componentClass);
        }

        @Override
        public FeatureContext register(Class<?> componentClass, Map<Class<?>, Integer> contracts) {
            return register(componentClass);
        }

        @Override
        public FeatureContext register(Object component) {
            registeredInstances.add(component);
            registeredClasses.add(component.getClass());
            return this;
        }

        @Override
        public FeatureContext register(Object component, int priority) {
            return register(component);
        }

        @Override
        public FeatureContext register(Object component, Class<?>... contracts) {
            return register(component);
        }

        @Override
        public FeatureContext register(Object component, Map<Class<?>, Integer> contracts) {
            return register(component);
        }

        @Override
        public RuntimeType getRuntimeType() {
            return runtimeType;
        }

        @Override
        public Map<String, Object> getProperties() {
            return Collections.unmodifiableMap(properties);
        }

        @Override
        public Object getProperty(String name) {
            return properties.get(name);
        }

        @Override
        public Collection<String> getPropertyNames() {
            return Collections.unmodifiableSet(properties.keySet());
        }

        @Override
        public boolean isEnabled(Feature feature) {
            return isRegistered(feature);
        }

        @Override
        public boolean isEnabled(Class<? extends Feature> featureClass) {
            return isRegistered(featureClass);
        }

        @Override
        public boolean isRegistered(Object component) {
            return registeredInstances.contains(component) || registeredClasses.contains(component.getClass());
        }

        @Override
        public boolean isRegistered(Class<?> componentClass) {
            return registeredClasses.contains(componentClass);
        }

        @Override
        public Map<Class<?>, Integer> getContracts(Class<?> componentClass) {
            return Collections.emptyMap();
        }

        @Override
        public Set<Class<?>> getClasses() {
            return Collections.unmodifiableSet(registeredClasses);
        }

        @Override
        public Set<Object> getInstances() {
            return Collections.unmodifiableSet(registeredInstances);
        }

        private Set<String> registeredClassNames() {
            Set<String> names = new LinkedHashSet<>();
            for (Class<?> registeredClass : registeredClasses) {
                names.add(registeredClass.getName());
            }
            return names;
        }
    }

    private static final class RecordingEntityGraph implements EntityGraph {
        private final Class<?> entityClass;
        private final Map<String, Set<String>> fieldsByScope = new LinkedHashMap<>();
        private final Map<String, Map<String, Class<?>>> subgraphsByScope = new LinkedHashMap<>();
        private final Set<String> filteringScopes = new LinkedHashSet<>();

        private RecordingEntityGraph(Class<?> entityClass) {
            this.entityClass = entityClass;
        }

        @Override
        public EntityGraph addField(String field) {
            return addField(field, FilteringHelper.getDefaultFilteringScope());
        }

        @Override
        public EntityGraph addField(String field, String... scopes) {
            return addField(field, setOf(scopes));
        }

        @Override
        public EntityGraph addField(String field, Set<String> scopes) {
            for (String scope : scopes) {
                fieldsByScope.computeIfAbsent(scope, ignored -> new LinkedHashSet<>()).add(field);
            }
            return this;
        }

        @Override
        public EntityGraph addSubgraph(String field, Class<?> fieldClass) {
            return addSubgraph(field, fieldClass, FilteringHelper.getDefaultFilteringScope());
        }

        @Override
        public EntityGraph addSubgraph(String field, Class<?> fieldClass, String... scopes) {
            return addSubgraph(field, fieldClass, setOf(scopes));
        }

        @Override
        public EntityGraph addSubgraph(String field, Class<?> fieldClass, Set<String> scopes) {
            for (String scope : scopes) {
                subgraphsByScope.computeIfAbsent(scope, ignored -> new LinkedHashMap<>()).put(field, fieldClass);
            }
            return this;
        }

        @Override
        public EntityGraph addFilteringScopes(Set<String> scopes) {
            filteringScopes.addAll(scopes);
            return this;
        }

        @Override
        public boolean presentInScope(String field, String scope) {
            return getFields(scope).contains(field) || getSubgraphs(scope).containsKey(field);
        }

        @Override
        public boolean presentInScopes(String field) {
            for (String scope : filteringScopes) {
                if (presentInScope(field, scope)) {
                    return true;
                }
            }
            return false;
        }

        @Override
        public Class<?> getEntityClass() {
            return entityClass;
        }

        @Override
        public Set<String> getFields(String scope) {
            return fieldsByScope.getOrDefault(scope, Collections.emptySet());
        }

        @Override
        public Set<String> getFields(String... scopes) {
            return getFields(setOf(scopes));
        }

        @Override
        public Set<String> getFields(Set<String> scopes) {
            Set<String> fields = new LinkedHashSet<>();
            for (String scope : scopes) {
                fields.addAll(getFields(scope));
            }
            return fields;
        }

        @Override
        public Set<String> getFilteringScopes() {
            return Collections.unmodifiableSet(filteringScopes);
        }

        @Override
        public Set<String> getClassFilteringScopes() {
            return getFilteringScopes();
        }

        @Override
        public Map<String, Class<?>> getSubgraphs(String scope) {
            return subgraphsByScope.getOrDefault(scope, Collections.emptyMap());
        }

        @Override
        public Map<String, Class<?>> getSubgraphs(String... scopes) {
            return getSubgraphs(setOf(scopes));
        }

        @Override
        public Map<String, Class<?>> getSubgraphs(Set<String> scopes) {
            Map<String, Class<?>> subgraphs = new LinkedHashMap<>();
            for (String scope : scopes) {
                subgraphs.putAll(getSubgraphs(scope));
            }
            return subgraphs;
        }

        @Override
        public EntityGraph remove(String field) {
            for (Set<String> fields : fieldsByScope.values()) {
                fields.remove(field);
            }
            for (Map<String, Class<?>> subgraphs : subgraphsByScope.values()) {
                subgraphs.remove(field);
            }
            return this;
        }

        private static Set<String> setOf(String... values) {
            Set<String> set = new HashSet<>();
            Collections.addAll(set, values);
            return set;
        }
    }

    private static final class SelectableEntity {
        private String name;
        private Address address;
    }

    private static class BaseAddress {
        private String country;

        public String getCountry() {
            return country;
        }

        public void setCountry(String country) {
            this.country = country;
        }
    }

    private static final class Address extends BaseAddress {
        private String city;

        public String getCity() {
            return city;
        }

        public void setCity(String city) {
            this.city = city;
        }
    }
}
