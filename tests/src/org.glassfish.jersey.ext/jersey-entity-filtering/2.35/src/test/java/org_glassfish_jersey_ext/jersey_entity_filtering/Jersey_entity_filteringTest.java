/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_glassfish_jersey_ext.jersey_entity_filtering;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.net.URI;
import java.security.Principal;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import javax.annotation.security.DenyAll;
import javax.annotation.security.PermitAll;
import javax.annotation.security.RolesAllowed;
import javax.inject.Inject;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.SecurityContext;

import org.glassfish.jersey.internal.MapPropertiesDelegate;
import org.glassfish.jersey.message.filtering.EntityFiltering;
import org.glassfish.jersey.message.filtering.EntityFilteringFeature;
import org.glassfish.jersey.message.filtering.SecurityEntityFilteringFeature;
import org.glassfish.jersey.message.filtering.SelectableEntityFilteringFeature;
import org.glassfish.jersey.message.filtering.spi.ObjectGraph;
import org.glassfish.jersey.message.filtering.spi.ObjectProvider;
import org.glassfish.jersey.server.ApplicationHandler;
import org.glassfish.jersey.server.ContainerRequest;
import org.glassfish.jersey.server.ContainerResponse;
import org.glassfish.jersey.server.ResourceConfig;
import org.junit.jupiter.api.Test;

public class Jersey_entity_filteringTest {

    private static final URI BASE_URI = URI.create("http://localhost/");

    @Test
    void entityFilteringFeatureUsesResourceMethodScopeToCreateObjectGraph() throws Exception {
        ResourceConfig config = new ResourceConfig(EntityFilteringResource.class)
                .register(EntityFilteringFeature.class);

        assertThat(get(config, "entity/detailed", RoleSecurityContext.anonymous()))
                .isEqualTo("Document(fields=[secret, title],subgraphs={owner=Owner(fields=[name],subgraphs={})})");
        assertThat(get(config, "entity/summary", RoleSecurityContext.anonymous()))
                .isEqualTo("Document(fields=[summary, title],subgraphs={})");
    }

    @Test
    void selectableEntityFilteringFeatureCreatesNestedObjectGraphFromQueryParameter() throws Exception {
        ResourceConfig config = new ResourceConfig(SelectableResource.class)
                .register(SelectableEntityFilteringFeature.class);

        assertThat(get(config, "selectable?select=secret,owner.name", RoleSecurityContext.anonymous()))
                .isEqualTo("Document(fields=[secret],subgraphs={owner=Owner(fields=[name],subgraphs={})})");
        assertThat(get(config, "selectable", RoleSecurityContext.anonymous()))
                .isEqualTo("Document(fields=[secret, summary, title],subgraphs="
                        + "{owner=Owner(fields=[name],subgraphs={})})");
    }

    @Test
    void selectableEntityFilteringFeatureUsesConfiguredQueryParameterName() throws Exception {
        try {
            ResourceConfig config = new ResourceConfig(SelectableResource.class)
                    .property(SelectableEntityFilteringFeature.QUERY_PARAM_NAME, "fields")
                    .register(SelectableEntityFilteringFeature.class);

            assertThat(get(config, "selectable?fields=summary", RoleSecurityContext.anonymous()))
                    .isEqualTo("Document(fields=[summary],subgraphs={})");
        } finally {
            resetSelectableQueryParameterName();
        }
    }

    @Test
    void securityEntityFilteringFeatureUsesCurrentSecurityContextRoles() throws Exception {
        ResourceConfig config = new ResourceConfig(SecurityResource.class)
                .register(SecurityEntityFilteringFeature.class);

        assertThat(get(config, "secure", new RoleSecurityContext("admin")))
                .isEqualTo("SecureDocument(fields=[adminSecret, publicInfo],subgraphs={})");
        assertThat(get(config, "secure", RoleSecurityContext.anonymous()))
                .isEqualTo("SecureDocument(fields=[publicInfo],subgraphs={})");
    }

    @Test
    void enabledDetectsRegisteredFilteringFeatures() {
        ResourceConfig noFiltering = new ResourceConfig();
        ResourceConfig entityFiltering = new ResourceConfig().register(EntityFilteringFeature.class);
        ResourceConfig selectableFiltering = new ResourceConfig().register(SelectableEntityFilteringFeature.class);
        ResourceConfig securityFiltering = new ResourceConfig().register(SecurityEntityFilteringFeature.class);

        assertThat(EntityFilteringFeature.enabled(noFiltering)).isFalse();
        assertThat(EntityFilteringFeature.enabled(entityFiltering)).isTrue();
        assertThat(EntityFilteringFeature.enabled(selectableFiltering)).isTrue();
        assertThat(EntityFilteringFeature.enabled(securityFiltering)).isTrue();
    }

    private static String get(ResourceConfig config, String path, SecurityContext securityContext) throws Exception {
        ApplicationHandler handler = new ApplicationHandler(config);
        try {
            ContainerRequest request = new ContainerRequest(
                    BASE_URI,
                    BASE_URI.resolve(path),
                    "GET",
                    securityContext,
                    new MapPropertiesDelegate(),
                    handler.getConfiguration());
            ContainerResponse response = handler.apply(request).get(10, TimeUnit.SECONDS);

            assertThat(response.getStatus()).isEqualTo(Response.Status.OK.getStatusCode());
            return (String) response.getEntity();
        } finally {
            handler.onShutdown(null);
        }
    }

    private static void resetSelectableQueryParameterName() throws Exception {
        ResourceConfig config = new ResourceConfig(SelectableResource.class)
                .property(SelectableEntityFilteringFeature.QUERY_PARAM_NAME, "select")
                .register(SelectableEntityFilteringFeature.class);

        get(config, "selectable?select=title", RoleSecurityContext.anonymous());
    }

    private static String describe(ObjectGraph graph) {
        return describe(graph, null);
    }

    private static String describe(ObjectGraph graph, String parent) {
        Set<String> fields = new TreeSet<>(graph.getFields(parent));
        Map<String, ObjectGraph> subgraphs = new TreeMap<>(graph.getSubgraphs(parent));
        String subgraphDescription = subgraphs.entrySet().stream()
                .map(entry -> entry.getKey() + "=" + describe(entry.getValue(), entry.getKey()))
                .collect(Collectors.joining(", ", "{", "}"));

        return graph.getEntityClass().getSimpleName()
                + "(fields=" + fields
                + ",subgraphs=" + subgraphDescription
                + ")";
    }

    @Target({ElementType.TYPE, ElementType.METHOD, ElementType.FIELD})
    @Retention(RetentionPolicy.RUNTIME)
    @EntityFiltering
    public @interface DetailedView {
    }

    @Target({ElementType.TYPE, ElementType.METHOD, ElementType.FIELD})
    @Retention(RetentionPolicy.RUNTIME)
    @EntityFiltering
    public @interface SummaryView {
    }

    @Path("entity")
    public static class EntityFilteringResource {
        @Inject
        private ObjectProvider<ObjectGraph> provider;

        @GET
        @Path("detailed")
        @DetailedView
        public String detailed() {
            return describe(provider.getFilteringObject(Document.class, true));
        }

        @GET
        @Path("summary")
        @SummaryView
        public String summary() {
            return describe(provider.getFilteringObject(Document.class, true));
        }
    }

    @Path("selectable")
    public static class SelectableResource {
        @Inject
        private ObjectProvider<ObjectGraph> provider;

        @GET
        public String selected() {
            return describe(provider.getFilteringObject(Document.class, true));
        }
    }

    @Path("secure")
    public static class SecurityResource {
        @Inject
        private ObjectProvider<ObjectGraph> provider;

        @GET
        public String secure() {
            return describe(provider.getFilteringObject(SecureDocument.class, true));
        }
    }

    public static class Document {
        private String title;
        private String secret;
        private String summary;
        private Owner owner;

        public String getTitle() {
            return title;
        }

        public void setTitle(String title) {
            this.title = title;
        }

        @DetailedView
        public String getSecret() {
            return secret;
        }

        public void setSecret(String secret) {
            this.secret = secret;
        }

        @SummaryView
        public String getSummary() {
            return summary;
        }

        public void setSummary(String summary) {
            this.summary = summary;
        }

        @DetailedView
        public Owner getOwner() {
            return owner;
        }

        public void setOwner(Owner owner) {
            this.owner = owner;
        }
    }

    public static class Owner {
        private String name;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }
    }

    public static class SecureDocument {
        private String publicInfo;
        private String adminSecret;
        private String userSecret;
        private String deniedSecret;

        @PermitAll
        public String getPublicInfo() {
            return publicInfo;
        }

        public void setPublicInfo(String publicInfo) {
            this.publicInfo = publicInfo;
        }

        @RolesAllowed("admin")
        public String getAdminSecret() {
            return adminSecret;
        }

        public void setAdminSecret(String adminSecret) {
            this.adminSecret = adminSecret;
        }

        @RolesAllowed("user")
        public String getUserSecret() {
            return userSecret;
        }

        public void setUserSecret(String userSecret) {
            this.userSecret = userSecret;
        }

        @DenyAll
        public String getDeniedSecret() {
            return deniedSecret;
        }

        public void setDeniedSecret(String deniedSecret) {
            this.deniedSecret = deniedSecret;
        }
    }

    private static final class RoleSecurityContext implements SecurityContext {
        private final Set<String> roles;

        private RoleSecurityContext(String... roles) {
            Set<String> configuredRoles = new HashSet<>();
            Collections.addAll(configuredRoles, roles);
            this.roles = Collections.unmodifiableSet(configuredRoles);
        }

        private static RoleSecurityContext anonymous() {
            return new RoleSecurityContext();
        }

        @Override
        public Principal getUserPrincipal() {
            return null;
        }

        @Override
        public boolean isUserInRole(String role) {
            return roles.contains(role);
        }

        @Override
        public boolean isSecure() {
            return false;
        }

        @Override
        public String getAuthenticationScheme() {
            return "test";
        }
    }
}
