/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_osgi.org_osgi_service_repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.osgi.resource.Capability;
import org.osgi.resource.Namespace;
import org.osgi.resource.Requirement;
import org.osgi.resource.Resource;
import org.osgi.service.repository.AndExpression;
import org.osgi.service.repository.ContentNamespace;
import org.osgi.service.repository.ExpressionCombiner;
import org.osgi.service.repository.IdentityExpression;
import org.osgi.service.repository.NotExpression;
import org.osgi.service.repository.OrExpression;
import org.osgi.service.repository.Repository;
import org.osgi.service.repository.RepositoryContent;
import org.osgi.service.repository.RequirementBuilder;
import org.osgi.service.repository.RequirementExpression;
import org.osgi.util.promise.Promise;
import org.osgi.util.promise.Promises;

public class Org_osgi_service_repositoryTest {
    private static final String BUNDLE_NAMESPACE = "osgi.identity";

    @Test
    void repositoryAndContentNamespaceExposeSpecificationConstants() {
        assertThat(Repository.URL).isEqualTo("repository.url");
        assertThat(ContentNamespace.CONTENT_NAMESPACE).isEqualTo("osgi.content");
        assertThat(ContentNamespace.CAPABILITY_URL_ATTRIBUTE).isEqualTo("url");
        assertThat(ContentNamespace.CAPABILITY_SIZE_ATTRIBUTE).isEqualTo("size");
        assertThat(ContentNamespace.CAPABILITY_MIME_ATTRIBUTE).isEqualTo("mime");
    }

    @Test
    void requirementBuilderCreatesRequirementsAndIdentityExpressions() {
        SimpleRepository repository = new SimpleRepository();
        ContentResource resource = contentResource(
                "bundle-a",
                "hash-a",
                "https://example.invalid/a.jar",
                "application/java-archive",
                "a");
        Map<String, Object> attributes = new LinkedHashMap<>();
        attributes.put("declaredBy", "test");
        Map<String, String> directives = new LinkedHashMap<>();
        directives.put(Namespace.REQUIREMENT_FILTER_DIRECTIVE, "(osgi.content=hash-a)");

        RequirementBuilder builder = repository.newRequirementBuilder(ContentNamespace.CONTENT_NAMESPACE)
                .setAttributes(attributes)
                .setDirectives(directives)
                .addAttribute("priority", 10L)
                .addDirective(Namespace.REQUIREMENT_CARDINALITY_DIRECTIVE, Namespace.CARDINALITY_MULTIPLE)
                .setResource(resource);
        Requirement requirement = builder.build();
        IdentityExpression expression = builder.buildExpression();

        assertThat(requirement.getNamespace()).isEqualTo(ContentNamespace.CONTENT_NAMESPACE);
        assertThat(requirement.getResource()).isSameAs(resource);
        assertThat(requirement.getAttributes())
                .containsEntry("declaredBy", "test")
                .containsEntry("priority", 10L);
        assertThat(requirement.getDirectives())
                .containsEntry(Namespace.REQUIREMENT_FILTER_DIRECTIVE, "(osgi.content=hash-a)")
                .containsEntry(Namespace.REQUIREMENT_CARDINALITY_DIRECTIVE, Namespace.CARDINALITY_MULTIPLE);
        assertThat(expression.getRequirement()).isEqualTo(requirement);
        assertThat(expression).isInstanceOf(RequirementExpression.class);
        assertThatThrownBy(() -> requirement.getAttributes().put("late", "change"))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> requirement.getDirectives().clear()).isInstanceOf(UnsupportedOperationException.class);

        attributes.put("declaredBy", "mutated");
        directives.put(Namespace.REQUIREMENT_FILTER_DIRECTIVE, "(osgi.content=changed)");
        assertThat(requirement.getAttributes()).containsEntry("declaredBy", "test");
        assertThat(requirement.getDirectives())
                .containsEntry(Namespace.REQUIREMENT_FILTER_DIRECTIVE, "(osgi.content=hash-a)");
    }

    @Test
    void requirementBuilderSetMethodsReplacePreviousState() {
        SimpleRepository repository = new SimpleRepository();
        ContentResource firstResource = contentResource(
                "bundle-a",
                "hash-a",
                "https://example.invalid/a.jar",
                "application/java-archive",
                "a");
        ContentResource secondResource = contentResource(
                "bundle-b",
                "hash-b",
                "https://example.invalid/b.jar",
                "application/java-archive",
                "b");

        Requirement requirement = repository.newRequirementBuilder(ContentNamespace.CONTENT_NAMESPACE)
                .addAttribute("stale", "attribute")
                .addDirective("stale", "directive")
                .setResource(firstResource)
                .setAttributes(Map.of("current", "attribute"))
                .setDirectives(Map.of(Namespace.REQUIREMENT_FILTER_DIRECTIVE, "(osgi.content=hash-b)"))
                .setResource(secondResource)
                .build();

        assertThat(requirement.getAttributes())
                .containsExactly(Map.entry("current", "attribute"));
        assertThat(requirement.getDirectives())
                .containsExactly(Map.entry(Namespace.REQUIREMENT_FILTER_DIRECTIVE, "(osgi.content=hash-b)"));
        assertThat(requirement.getResource()).isSameAs(secondResource);
    }

    @Test
    void expressionCombinerBuildsImmutableOrderedExpressionTrees() {
        SimpleRepository repository = new SimpleRepository();
        ExpressionCombiner combiner = repository.getExpressionCombiner();
        Requirement firstRequirement = repository.newRequirementBuilder(ContentNamespace.CONTENT_NAMESPACE)
                .addDirective(Namespace.REQUIREMENT_FILTER_DIRECTIVE, "(osgi.content=hash-a)")
                .build();
        Requirement secondRequirement = repository.newRequirementBuilder(ContentNamespace.CONTENT_NAMESPACE)
                .addDirective(Namespace.REQUIREMENT_FILTER_DIRECTIVE, "(mime=application/java-archive)")
                .build();
        Requirement thirdRequirement = repository.newRequirementBuilder(BUNDLE_NAMESPACE)
                .addDirective(Namespace.REQUIREMENT_FILTER_DIRECTIVE, "(osgi.identity=bundle-b)")
                .build();
        IdentityExpression first = combiner.identity(firstRequirement);
        IdentityExpression second = combiner.identity(secondRequirement);
        IdentityExpression third = combiner.identity(thirdRequirement);

        AndExpression andExpression = combiner.and(first, second, third);
        OrExpression orExpression = combiner.or(first, second, third);
        NotExpression notExpression = combiner.not(orExpression);

        assertThat(andExpression.getRequirementExpressions()).containsExactly(first, second, third);
        assertThat(orExpression.getRequirementExpressions()).containsExactly(first, second, third);
        assertThat(notExpression.getRequirementExpression()).isSameAs(orExpression);
        assertThat(first.getRequirement()).isSameAs(firstRequirement);
        assertThat(combiner.and(first, second).getRequirementExpressions()).containsExactly(first, second);
        assertThat(combiner.or(first, second).getRequirementExpressions()).containsExactly(first, second);
        assertThatThrownBy(() -> andExpression.getRequirementExpressions().add(first))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void repositoryFindProvidersReturnsCapabilitiesForEveryRequirement() {
        ContentResource bundleA = contentResource(
                "bundle-a",
                "hash-a",
                "https://example.invalid/a.jar",
                "application/java-archive",
                "a");
        ContentResource bundleB = contentResource(
                "bundle-b",
                "hash-b",
                "https://example.invalid/b.txt",
                "text/plain",
                "b");
        SimpleRepository repository = new SimpleRepository(bundleA, bundleB);
        Requirement javaArchiveRequirement = repository.newRequirementBuilder(ContentNamespace.CONTENT_NAMESPACE)
                .addDirective(
                        Namespace.REQUIREMENT_FILTER_DIRECTIVE,
                        "(&(osgi.content=hash-a)(mime=application/java-archive))")
                .build();
        Requirement textRequirement = repository.newRequirementBuilder(ContentNamespace.CONTENT_NAMESPACE)
                .addDirective(Namespace.REQUIREMENT_FILTER_DIRECTIVE, "(mime=text/plain)")
                .build();
        Requirement missingRequirement = repository.newRequirementBuilder(ContentNamespace.CONTENT_NAMESPACE)
                .addDirective(Namespace.REQUIREMENT_FILTER_DIRECTIVE, "(osgi.content=missing)")
                .build();

        Map<Requirement, Collection<Capability>> providers = repository.findProviders(
                List.of(javaArchiveRequirement, textRequirement, missingRequirement));

        assertThat(providers.keySet()).containsExactly(javaArchiveRequirement, textRequirement, missingRequirement);
        assertThat(providers.get(javaArchiveRequirement))
                .singleElement()
                .satisfies(capability -> assertThat(capability.getResource()).isSameAs(bundleA));
        assertThat(providers.get(textRequirement))
                .singleElement()
                .satisfies(capability -> assertThat(capability.getResource()).isSameAs(bundleB));
        assertThat(providers.get(missingRequirement)).isEmpty();
        providers.get(missingRequirement).add(bundleA.getCapabilities(ContentNamespace.CONTENT_NAMESPACE).get(0));
        assertThat(providers.get(missingRequirement)).hasSize(1);
    }

    @Test
    void contentNamespaceArbitraryAttributesParticipateInRequirementMatching() {
        ContentResource linuxBundle = contentResource(
                "bundle-linux",
                "hash-linux",
                "https://example.invalid/linux.jar",
                "application/java-archive",
                "linux-payload",
                Map.of("classifier", "linux-x86_64", "osgi.native.osname", "Linux"));
        ContentResource sourcesBundle = contentResource(
                "bundle-sources",
                "hash-sources",
                "https://example.invalid/sources.jar",
                "application/java-archive",
                "sources-payload",
                Map.of("classifier", "sources", "documentation", "true"));
        SimpleRepository repository = new SimpleRepository(linuxBundle, sourcesBundle);
        Requirement nativeRequirement = repository.newRequirementBuilder(ContentNamespace.CONTENT_NAMESPACE)
                .addDirective(
                        Namespace.REQUIREMENT_FILTER_DIRECTIVE,
                        "(&(classifier=linux-x86_64)(osgi.native.osname=Linux))")
                .build();
        Requirement documentationRequirement = repository.newRequirementBuilder(ContentNamespace.CONTENT_NAMESPACE)
                .addDirective(Namespace.REQUIREMENT_FILTER_DIRECTIVE, "(documentation=true)")
                .build();

        Map<Requirement, Collection<Capability>> providers = repository.findProviders(
                List.of(nativeRequirement, documentationRequirement));

        assertThat(providers.get(nativeRequirement))
                .singleElement()
                .satisfies(capability -> {
                    assertThat(capability.getResource()).isSameAs(linuxBundle);
                    assertThat(capability.getAttributes())
                            .containsEntry("classifier", "linux-x86_64")
                            .containsEntry("osgi.native.osname", "Linux");
                });
        assertThat(providers.get(documentationRequirement))
                .singleElement()
                .satisfies(capability -> {
                    assertThat(capability.getResource()).isSameAs(sourcesBundle);
                    assertThat(capability.getAttributes())
                            .containsEntry("classifier", "sources")
                            .containsEntry("documentation", "true");
                });
    }

    @Test
    void repositoryFindProvidersEvaluatesRequirementExpressionPromises() throws Exception {
        ContentResource bundleA = contentResource(
                "bundle-a",
                "hash-a",
                "https://example.invalid/a.jar",
                "application/java-archive",
                "a");
        ContentResource bundleB = contentResource(
                "bundle-b",
                "hash-b",
                "https://example.invalid/b.jar",
                "application/java-archive",
                "b");
        ContentResource notes = contentResource(
                "notes",
                "hash-c",
                "https://example.invalid/readme.txt",
                "text/plain",
                "readme");
        SimpleRepository repository = new SimpleRepository(bundleA, bundleB, notes);
        ExpressionCombiner combiner = repository.getExpressionCombiner();
        IdentityExpression archiveExpression = repository.newRequirementBuilder(ContentNamespace.CONTENT_NAMESPACE)
                .addDirective(Namespace.REQUIREMENT_FILTER_DIRECTIVE, "(mime=application/java-archive)")
                .buildExpression();
        IdentityExpression bundleAExpression = repository.newRequirementBuilder(BUNDLE_NAMESPACE)
                .addDirective(Namespace.REQUIREMENT_FILTER_DIRECTIVE, "(osgi.identity=bundle-a)")
                .buildExpression();
        IdentityExpression notesExpression = repository.newRequirementBuilder(BUNDLE_NAMESPACE)
                .addDirective(Namespace.REQUIREMENT_FILTER_DIRECTIVE, "(osgi.identity=notes)")
                .buildExpression();
        RequirementExpression expression = combiner.or(
                combiner.and(archiveExpression, bundleAExpression),
                notesExpression);

        Promise<Collection<Resource>> promise = repository.findProviders(expression);
        Collection<Resource> resources = promise.getValue();

        assertThat(promise.isDone()).isTrue();
        assertThat(resources).containsExactly(bundleA, notes);
        assertThat(repository.findProviders(
                        combiner.and(archiveExpression, combiner.not(bundleAExpression))).getValue())
                .containsExactly(bundleB);
        resources.clear();
        assertThat(resources).isEmpty();
    }

    @Test
    void repositoryContentProvidesFreshInputStreamsForResourceBytes() throws Exception {
        ContentResource resource = contentResource(
                "bundle-a",
                "hash-a",
                "https://example.invalid/a.jar",
                "application/java-archive",
                "payload");

        try (InputStream first = resource.getContent(); InputStream second = resource.getContent()) {
            assertThat(new String(first.readAllBytes(), StandardCharsets.UTF_8)).isEqualTo("payload");
            assertThat(new String(second.readAllBytes(), StandardCharsets.UTF_8)).isEqualTo("payload");
        }
        Capability contentCapability = resource.getCapabilities(ContentNamespace.CONTENT_NAMESPACE).get(0);
        assertThat(contentCapability.getAttributes())
                .containsEntry(ContentNamespace.CONTENT_NAMESPACE, "hash-a")
                .containsEntry(ContentNamespace.CAPABILITY_URL_ATTRIBUTE, "https://example.invalid/a.jar")
                .containsEntry(ContentNamespace.CAPABILITY_SIZE_ATTRIBUTE, 7L)
                .containsEntry(ContentNamespace.CAPABILITY_MIME_ATTRIBUTE, "application/java-archive");
        assertThat(resource).isInstanceOf(RepositoryContent.class);
    }

    private static ContentResource contentResource(
            String identity,
            String hash,
            String url,
            String mime,
            String content) {
        return contentResource(identity, hash, url, mime, content, Map.of());
    }

    private static ContentResource contentResource(
            String identity,
            String hash,
            String url,
            String mime,
            String content,
            Map<String, Object> additionalContentAttributes) {
        ContentResource resource = new ContentResource(identity, content.getBytes(StandardCharsets.UTF_8));
        resource.addCapability(
                BUNDLE_NAMESPACE,
                Map.of(),
                Map.of(BUNDLE_NAMESPACE, identity));
        Map<String, Object> contentAttributes = new LinkedHashMap<>();
        contentAttributes.put(ContentNamespace.CONTENT_NAMESPACE, hash);
        contentAttributes.put(ContentNamespace.CAPABILITY_URL_ATTRIBUTE, url);
        contentAttributes.put(ContentNamespace.CAPABILITY_SIZE_ATTRIBUTE, (long) content.length());
        contentAttributes.put(ContentNamespace.CAPABILITY_MIME_ATTRIBUTE, mime);
        contentAttributes.putAll(additionalContentAttributes);
        resource.addCapability(
                ContentNamespace.CONTENT_NAMESPACE,
                Map.of(),
                contentAttributes);
        return resource;
    }

    private static final class SimpleRepository implements Repository {
        private final List<Resource> resources;
        private final ExpressionCombiner combiner = new SimpleExpressionCombiner();

        private SimpleRepository(Resource... resources) {
            this.resources = List.of(resources);
        }

        @Override
        public Map<Requirement, Collection<Capability>> findProviders(Collection<? extends Requirement> requirements) {
            Map<Requirement, Collection<Capability>> result = new LinkedHashMap<>();
            for (Requirement requirement : requirements) {
                List<Capability> matches = resources.stream()
                        .flatMap(resource -> resource.getCapabilities(requirement.getNamespace()).stream())
                        .filter(capability -> matchesRequirement(requirement, capability))
                        .collect(Collectors.toCollection(ArrayList::new));
                result.put(requirement, matches);
            }
            return result;
        }

        @Override
        public Promise<Collection<Resource>> findProviders(RequirementExpression expression) {
            Collection<Resource> matches = resources.stream()
                    .filter(resource -> matchesExpression(expression, resource))
                    .collect(Collectors.toCollection(ArrayList::new));
            return Promises.resolved(matches);
        }

        @Override
        public ExpressionCombiner getExpressionCombiner() {
            return combiner;
        }

        @Override
        public RequirementBuilder newRequirementBuilder(String namespace) {
            return new SimpleRequirementBuilder(namespace, combiner);
        }
    }

    private static final class SimpleRequirementBuilder implements RequirementBuilder {
        private final String namespace;
        private final ExpressionCombiner combiner;
        private Map<String, Object> attributes = new LinkedHashMap<>();
        private Map<String, String> directives = new LinkedHashMap<>();
        private Resource resource;

        private SimpleRequirementBuilder(String namespace, ExpressionCombiner combiner) {
            this.namespace = namespace;
            this.combiner = combiner;
        }

        @Override
        public RequirementBuilder addAttribute(String name, Object value) {
            attributes.put(name, value);
            return this;
        }

        @Override
        public RequirementBuilder addDirective(String name, String value) {
            directives.put(name, value);
            return this;
        }

        @Override
        public RequirementBuilder setAttributes(Map<String, Object> attributes) {
            this.attributes = new LinkedHashMap<>(attributes);
            return this;
        }

        @Override
        public RequirementBuilder setDirectives(Map<String, String> directives) {
            this.directives = new LinkedHashMap<>(directives);
            return this;
        }

        @Override
        public RequirementBuilder setResource(Resource resource) {
            this.resource = resource;
            return this;
        }

        @Override
        public Requirement build() {
            return new SimpleRequirement(resource, namespace, directives, attributes);
        }

        @Override
        public IdentityExpression buildExpression() {
            return combiner.identity(build());
        }
    }

    private static final class SimpleExpressionCombiner implements ExpressionCombiner {
        @Override
        public AndExpression and(RequirementExpression expr1, RequirementExpression expr2) {
            return and(expr1, expr2, new RequirementExpression[0]);
        }

        @Override
        public AndExpression and(
                RequirementExpression expr1,
                RequirementExpression expr2,
                RequirementExpression... moreExprs) {
            return new SimpleAndExpression(combine(expr1, expr2, moreExprs));
        }

        @Override
        public IdentityExpression identity(Requirement req) {
            return new SimpleIdentityExpression(req);
        }

        @Override
        public NotExpression not(RequirementExpression expr) {
            return new SimpleNotExpression(expr);
        }

        @Override
        public OrExpression or(RequirementExpression expr1, RequirementExpression expr2) {
            return or(expr1, expr2, new RequirementExpression[0]);
        }

        @Override
        public OrExpression or(
                RequirementExpression expr1,
                RequirementExpression expr2,
                RequirementExpression... moreExprs) {
            return new SimpleOrExpression(combine(expr1, expr2, moreExprs));
        }

        private List<RequirementExpression> combine(
                RequirementExpression expr1,
                RequirementExpression expr2,
                RequirementExpression... moreExprs) {
            List<RequirementExpression> expressions = new ArrayList<>();
            expressions.add(expr1);
            expressions.add(expr2);
            expressions.addAll(Arrays.asList(moreExprs));
            return expressions;
        }
    }

    private static final class SimpleIdentityExpression implements IdentityExpression {
        private final Requirement requirement;

        private SimpleIdentityExpression(Requirement requirement) {
            this.requirement = requirement;
        }

        @Override
        public Requirement getRequirement() {
            return requirement;
        }
    }

    private static final class SimpleAndExpression implements AndExpression {
        private final List<RequirementExpression> expressions;

        private SimpleAndExpression(List<RequirementExpression> expressions) {
            this.expressions = Collections.unmodifiableList(new ArrayList<>(expressions));
        }

        @Override
        public List<RequirementExpression> getRequirementExpressions() {
            return expressions;
        }
    }

    private static final class SimpleOrExpression implements OrExpression {
        private final List<RequirementExpression> expressions;

        private SimpleOrExpression(List<RequirementExpression> expressions) {
            this.expressions = Collections.unmodifiableList(new ArrayList<>(expressions));
        }

        @Override
        public List<RequirementExpression> getRequirementExpressions() {
            return expressions;
        }
    }

    private static final class SimpleNotExpression implements NotExpression {
        private final RequirementExpression expression;

        private SimpleNotExpression(RequirementExpression expression) {
            this.expression = expression;
        }

        @Override
        public RequirementExpression getRequirementExpression() {
            return expression;
        }
    }

    private static class SimpleResource implements Resource {
        private final String id;
        private final List<Capability> capabilities = new ArrayList<>();
        private final List<Requirement> requirements = new ArrayList<>();

        SimpleResource(String id) {
            this.id = id;
        }

        SimpleCapability addCapability(
                String namespace,
                Map<String, String> directives,
                Map<String, Object> attributes) {
            SimpleCapability capability = new SimpleCapability(this, namespace, directives, attributes);
            capabilities.add(capability);
            return capability;
        }

        @Override
        public List<Capability> getCapabilities(String namespace) {
            return capabilities.stream()
                    .filter(capability -> namespace == null || namespace.equals(capability.getNamespace()))
                    .collect(Collectors.toUnmodifiableList());
        }

        @Override
        public List<Requirement> getRequirements(String namespace) {
            return requirements.stream()
                    .filter(requirement -> namespace == null || namespace.equals(requirement.getNamespace()))
                    .collect(Collectors.toUnmodifiableList());
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof SimpleResource)) {
                return false;
            }
            SimpleResource that = (SimpleResource) other;
            return Objects.equals(id, that.id);
        }

        @Override
        public int hashCode() {
            return Objects.hash(id);
        }
    }

    private static final class ContentResource extends SimpleResource implements RepositoryContent {
        private final byte[] content;

        private ContentResource(String id, byte[] content) {
            super(id);
            this.content = content.clone();
        }

        @Override
        public InputStream getContent() {
            return new ByteArrayInputStream(content);
        }
    }

    private static final class SimpleCapability implements Capability {
        private final Resource resource;
        private final String namespace;
        private final Map<String, String> directives;
        private final Map<String, Object> attributes;

        private SimpleCapability(
                Resource resource,
                String namespace,
                Map<String, String> directives,
                Map<String, Object> attributes) {
            this.resource = resource;
            this.namespace = namespace;
            this.directives = Collections.unmodifiableMap(new LinkedHashMap<>(directives));
            this.attributes = Collections.unmodifiableMap(new LinkedHashMap<>(attributes));
        }

        @Override
        public String getNamespace() {
            return namespace;
        }

        @Override
        public Map<String, String> getDirectives() {
            return directives;
        }

        @Override
        public Map<String, Object> getAttributes() {
            return attributes;
        }

        @Override
        public Resource getResource() {
            return resource;
        }
    }

    private static final class SimpleRequirement implements Requirement {
        private final Resource resource;
        private final String namespace;
        private final Map<String, String> directives;
        private final Map<String, Object> attributes;

        private SimpleRequirement(
                Resource resource,
                String namespace,
                Map<String, String> directives,
                Map<String, Object> attributes) {
            this.resource = resource;
            this.namespace = namespace;
            this.directives = Collections.unmodifiableMap(new LinkedHashMap<>(directives));
            this.attributes = Collections.unmodifiableMap(new LinkedHashMap<>(attributes));
        }

        @Override
        public String getNamespace() {
            return namespace;
        }

        @Override
        public Map<String, String> getDirectives() {
            return directives;
        }

        @Override
        public Map<String, Object> getAttributes() {
            return attributes;
        }

        @Override
        public Resource getResource() {
            return resource;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof SimpleRequirement)) {
                return false;
            }
            SimpleRequirement that = (SimpleRequirement) other;
            return Objects.equals(resource, that.resource)
                    && Objects.equals(namespace, that.namespace)
                    && Objects.equals(directives, that.directives)
                    && Objects.equals(attributes, that.attributes);
        }

        @Override
        public int hashCode() {
            return Objects.hash(resource, namespace, directives, attributes);
        }
    }

    private static boolean matchesExpression(RequirementExpression expression, Resource resource) {
        if (expression instanceof IdentityExpression) {
            Requirement requirement = ((IdentityExpression) expression).getRequirement();
            return resource.getCapabilities(requirement.getNamespace()).stream()
                    .anyMatch(capability -> matchesRequirement(requirement, capability));
        }
        if (expression instanceof AndExpression) {
            return ((AndExpression) expression).getRequirementExpressions().stream()
                    .allMatch(childExpression -> matchesExpression(childExpression, resource));
        }
        if (expression instanceof OrExpression) {
            return ((OrExpression) expression).getRequirementExpressions().stream()
                    .anyMatch(childExpression -> matchesExpression(childExpression, resource));
        }
        if (expression instanceof NotExpression) {
            return !matchesExpression(((NotExpression) expression).getRequirementExpression(), resource);
        }
        throw new IllegalArgumentException("Unsupported expression type: " + expression.getClass().getName());
    }

    private static boolean matchesRequirement(Requirement requirement, Capability capability) {
        if (!Objects.equals(requirement.getNamespace(), capability.getNamespace())) {
            return false;
        }
        String filter = requirement.getDirectives().get(Namespace.REQUIREMENT_FILTER_DIRECTIVE);
        return filter == null || matchesFilter(filter, capability.getAttributes());
    }

    private static boolean matchesFilter(String filter, Map<String, Object> attributes) {
        String expression = filter.trim();
        if (expression.startsWith("(&")) {
            return splitConjunction(expression.substring(2, expression.length() - 1)).stream()
                    .allMatch(child -> matchesFilter(child, attributes));
        }
        String body = expression.substring(1, expression.length() - 1);
        int equalsIndex = body.indexOf('=');
        String key = body.substring(0, equalsIndex);
        String expected = body.substring(equalsIndex + 1);
        return Objects.equals(String.valueOf(attributes.get(key)), expected);
    }

    private static List<String> splitConjunction(String expression) {
        List<String> children = new ArrayList<>();
        int start = -1;
        int depth = 0;
        for (int index = 0; index < expression.length(); index++) {
            char current = expression.charAt(index);
            if (current == '(') {
                if (depth == 0) {
                    start = index;
                }
                depth++;
            } else if (current == ')') {
                depth--;
                if (depth == 0) {
                    children.add(expression.substring(start, index + 1));
                }
            }
        }
        return children;
    }
}
