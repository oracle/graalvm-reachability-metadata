/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jboss_javaee.jboss_jacc_api_JDK4;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.security.Permission;
import java.security.PermissionCollection;
import java.security.Permissions;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import javax.security.jacc.EJBMethodPermission;
import javax.security.jacc.EJBRoleRefPermission;
import javax.security.jacc.PolicyConfiguration;
import javax.security.jacc.PolicyConfigurationFactory;
import javax.security.jacc.PolicyContext;
import javax.security.jacc.PolicyContextException;
import javax.security.jacc.PolicyContextHandler;
import javax.security.jacc.WebResourcePermission;
import javax.security.jacc.WebRoleRefPermission;
import javax.security.jacc.WebUserDataPermission;

import org.junit.jupiter.api.Test;

public class Jboss_jacc_api_JDK4Test {
    private static final String FACTORY_PROVIDER_PROPERTY =
            "javax.security.jacc.PolicyConfigurationFactory.provider";

    @Test
    void ejbMethodPermissionCanonicalizesActionsAndAppliesSubsetRules() {
        EJBMethodPermission allOrderBeanMethods = new EJBMethodPermission("OrderBean", null);
        EJBMethodPermission submitRemote = new EJBMethodPermission(
                "OrderBean", "submit", "Remote", new String[] {"java.lang.String", "int"});
        EJBMethodPermission sameSubmitRemote = new EJBMethodPermission(
                "OrderBean", "submit,Remote,java.lang.String,int");
        EJBMethodPermission anyRemoteMethod = new EJBMethodPermission(
                "OrderBean", null, "Remote", null);
        EJBMethodPermission submitLocal = new EJBMethodPermission(
                "OrderBean", "submit", "Local", new String[] {"java.lang.String", "int"});
        EJBMethodPermission otherBeanSubmit = new EJBMethodPermission(
                "InventoryBean", "submit", "Remote", new String[] {"java.lang.String", "int"});

        assertThat(allOrderBeanMethods.getActions()).isNull();
        assertThat(submitRemote.getActions()).isEqualTo("submit,Remote,java.lang.String,int");
        assertThat(submitRemote).isEqualTo(sameSubmitRemote);
        assertThat(submitRemote.hashCode()).isEqualTo(sameSubmitRemote.hashCode());

        assertThat(allOrderBeanMethods.implies(submitRemote)).isTrue();
        assertThat(anyRemoteMethod.implies(submitRemote)).isTrue();
        assertThat(submitRemote.implies(allOrderBeanMethods)).isFalse();
        assertThat(submitRemote.implies(submitLocal)).isFalse();
        assertThat(submitRemote.implies(otherBeanSubmit)).isFalse();
    }

    @Test
    void roleReferencePermissionsUseExactNameAndRoleMatching() {
        EJBRoleRefPermission ejbAdmin = new EJBRoleRefPermission("OrderBean", "admin");
        EJBRoleRefPermission sameEjbAdmin = new EJBRoleRefPermission("OrderBean", "admin");
        EJBRoleRefPermission ejbUser = new EJBRoleRefPermission("OrderBean", "user");
        WebRoleRefPermission webAdmin = new WebRoleRefPermission("OrderServlet", "admin");
        WebRoleRefPermission sameWebAdmin = new WebRoleRefPermission("OrderServlet", "admin");
        WebRoleRefPermission otherServletAdmin = new WebRoleRefPermission("AuditServlet", "admin");

        assertThat(ejbAdmin.getActions()).isEqualTo("admin");
        assertThat(ejbAdmin).isEqualTo(sameEjbAdmin);
        assertThat(ejbAdmin.hashCode()).isEqualTo(sameEjbAdmin.hashCode());
        assertThat(ejbAdmin.implies(sameEjbAdmin)).isTrue();
        assertThat(ejbAdmin.implies(ejbUser)).isFalse();
        assertThat(ejbAdmin.toString()).contains("OrderBean", "role-ref=admin");

        assertThat(webAdmin.getActions()).isEqualTo("admin");
        assertThat(webAdmin).isEqualTo(sameWebAdmin);
        assertThat(webAdmin.hashCode()).isEqualTo(sameWebAdmin.hashCode());
        assertThat(webAdmin.implies(sameWebAdmin)).isTrue();
        assertThat(webAdmin.implies(otherServletAdmin)).isFalse();
    }

    @Test
    void webResourcePermissionCanonicalizesMethodsAndMatchesServletUrlPatterns() {
        WebResourcePermission orders = new WebResourcePermission(
                "/orders/*", new String[] {"POST", "GET", "POST"});
        WebResourcePermission equivalentOrders = new WebResourcePermission("/orders/*", "POST,GET,POST");
        WebResourcePermission orderDetails = new WebResourcePermission("/orders/42", "GET");
        WebResourcePermission deleteOrder = new WebResourcePermission("/orders/42", "DELETE");
        WebResourcePermission adminOrder = new WebResourcePermission("/admin/orders/42", "GET");
        WebResourcePermission defaultAllMethods = new WebResourcePermission(null, (String) null);
        WebResourcePermission jspExtension = new WebResourcePermission("*.jsp", "GET");
        WebResourcePermission concreteJsp = new WebResourcePermission("/views/index.jsp", "GET");

        assertThat(orders.getActions()).isEqualTo("GET,POST");
        assertThat(orders).isEqualTo(equivalentOrders);
        assertThat(orders.hashCode()).isEqualTo(equivalentOrders.hashCode());

        assertThat(orders.implies(orderDetails)).isTrue();
        assertThat(orders.implies(deleteOrder)).isFalse();
        assertThat(orders.implies(adminOrder)).isFalse();
        assertThat(defaultAllMethods.getName()).isEqualTo("/");
        assertThat(defaultAllMethods.getActions()).isNull();
        assertThat(defaultAllMethods.implies(deleteOrder)).isTrue();
        assertThat(jspExtension.implies(concreteJsp)).isTrue();
    }

    @Test
    void webResourcePermissionHonorsUrlPatternExclusionLists() {
        WebResourcePermission publicOrders = new WebResourcePermission("/orders/*:/orders/admin/*", "GET");
        WebResourcePermission orderDetails = new WebResourcePermission("/orders/42", "GET");
        WebResourcePermission adminOrder = new WebResourcePermission("/orders/admin/42", "GET");
        WebResourcePermission invalidExactPatternWithExclusion = new WebResourcePermission("/orders/42", "GET");

        assertThat(publicOrders.implies(orderDetails)).isTrue();
        assertThat(publicOrders.implies(adminOrder)).isFalse();
        assertThatThrownBy(() -> new WebResourcePermission("/orders/42:/orders/admin/42", "GET"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("exact pattern");
        assertThat(invalidExactPatternWithExclusion.implies(publicOrders)).isFalse();
    }

    @Test
    void webUserDataPermissionCanonicalizesMethodsTransportAndImplicationRules() {
        WebUserDataPermission secureOrders = new WebUserDataPermission(
                "/secure/*", new String[] {"POST", "GET", "GET"}, "CONFIDENTIAL");
        WebUserDataPermission sameSecureOrders = new WebUserDataPermission("/secure/*", "POST,GET:CONFIDENTIAL");
        WebUserDataPermission secureGet = new WebUserDataPermission("/secure/form", "GET:CONFIDENTIAL");
        WebUserDataPermission insecureGet = new WebUserDataPermission("/secure/form", "GET");
        WebUserDataPermission secureDelete = new WebUserDataPermission("/secure/form", "DELETE:CONFIDENTIAL");
        WebUserDataPermission unconstrainedTransport = new WebUserDataPermission("/secure/*", null, "NONE");

        assertThat(secureOrders.getActions()).isEqualTo("GET,POST:CONFIDENTIAL");
        assertThat(secureOrders).isEqualTo(sameSecureOrders);
        assertThat(secureOrders.hashCode()).isEqualTo(sameSecureOrders.hashCode());

        assertThat(secureOrders.implies(secureGet)).isTrue();
        assertThat(secureOrders.implies(insecureGet)).isFalse();
        assertThat(secureOrders.implies(secureDelete)).isFalse();
        assertThat(unconstrainedTransport.getActions()).isNull();
        assertThat(unconstrainedTransport.implies(secureGet)).isTrue();
    }

    @Test
    void policyContextDispatchesRegisteredHandlersWithThreadLocalData() throws Exception {
        String key = Jboss_jacc_api_JDK4Test.class.getName() + ".handler";
        RecordingPolicyContextHandler handler = new RecordingPolicyContextHandler(key);
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            PolicyContext.registerHandler(key, handler, true);
            PolicyContext.setContextID("main-context");
            PolicyContext.setHandlerData("main-data");

            assertThat(PolicyContext.getHandlerKeys()).contains(key);
            assertThat(PolicyContext.getContextID()).isEqualTo("main-context");
            assertThat(PolicyContext.getContext(key)).isEqualTo("context:main-data");
            assertThat(handler.seenKeys()).containsExactly(key);

            Callable<String> task = () -> {
                assertThat(PolicyContext.getContextID()).isNull();
                PolicyContext.setContextID("worker-context");
                PolicyContext.setHandlerData("worker-data");
                return PolicyContext.getContextID() + ":" + PolicyContext.getContext(key);
            };
            Future<String> workerResult = executor.submit(task);

            assertThat(workerResult.get(10, TimeUnit.SECONDS)).isEqualTo("worker-context:context:worker-data");
            assertThat(PolicyContext.getContextID()).isEqualTo("main-context");
            assertThat(PolicyContext.getContext(key)).isEqualTo("context:main-data");
            assertThatThrownBy(() -> PolicyContext.getContext(key + ".missing"))
                    .isInstanceOf(IllegalArgumentException.class);
        } finally {
            PolicyContext.setContextID(null);
            PolicyContext.setHandlerData(null);
            executor.shutdownNow();
            assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        }
    }

    @Test
    void policyContextRegisterHandlerHonorsReplaceFlag() throws Exception {
        String key = Jboss_jacc_api_JDK4Test.class.getName() + ".replacement." + System.nanoTime();
        RecordingPolicyContextHandler firstHandler = new RecordingPolicyContextHandler(key);
        RecordingPolicyContextHandler replacementHandler = new RecordingPolicyContextHandler(key);
        try {
            PolicyContext.registerHandler(key, firstHandler, false);

            assertThat(PolicyContext.getHandlerKeys()).contains(key);
            assertThatThrownBy(() -> PolicyContext.registerHandler(key, replacementHandler, false))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining(key);

            PolicyContext.registerHandler(key, replacementHandler, true);
            PolicyContext.setHandlerData("replacement-data");

            assertThat(PolicyContext.getContext(key)).isEqualTo("context:replacement-data");
            assertThat(firstHandler.seenKeys()).isEmpty();
            assertThat(replacementHandler.seenKeys()).containsExactly(key);
        } finally {
            PolicyContext.setHandlerData(null);
        }
    }

    @Test
    void policyConfigurationFactoryLoadsConfiguredProviderAndUsesConfigurationLifecycle() throws Exception {
        String previousProvider = System.getProperty(FACTORY_PROVIDER_PROPERTY);
        ClassLoader previousContextClassLoader = Thread.currentThread().getContextClassLoader();
        System.setProperty(FACTORY_PROVIDER_PROPERTY, InMemoryPolicyConfigurationFactory.class.getName());
        Thread.currentThread().setContextClassLoader(InMemoryPolicyConfigurationFactory.class.getClassLoader());
        try {
            PolicyConfigurationFactory factory = PolicyConfigurationFactory.getPolicyConfigurationFactory();
            assertThat(factory).isInstanceOf(InMemoryPolicyConfigurationFactory.class);

            PolicyConfiguration configuration = factory.getPolicyConfiguration("application", true);
            assertThat(configuration.getContextID()).isEqualTo("application");
            assertThat(configuration.inService()).isFalse();
            assertThat(factory.inService("application")).isFalse();

            configuration.addToRole("admin", new EJBRoleRefPermission("OrderBean", "admin"));
            configuration.addToRole("viewer", permissions(new WebRoleRefPermission("OrderServlet", "viewer")));
            configuration.addToExcludedPolicy(new WebResourcePermission("/internal/*", "GET"));
            configuration.addToExcludedPolicy(permissions(new WebResourcePermission("/admin/*", "POST")));
            configuration.addToUncheckedPolicy(new WebResourcePermission("/public/*", "GET"));
            configuration.addToUncheckedPolicy(permissions(new WebUserDataPermission("/public/*", "GET")));

            InMemoryPolicyConfiguration inMemoryConfiguration = (InMemoryPolicyConfiguration) configuration;
            assertThat(inMemoryConfiguration.roleNames()).containsExactlyInAnyOrder("admin", "viewer");
            assertThat(inMemoryConfiguration.excludedPolicy()).hasSize(2);
            assertThat(inMemoryConfiguration.uncheckedPolicy()).hasSize(2);

            configuration.removeRole("viewer");
            configuration.removeExcludedPolicy();
            configuration.removeUncheckedPolicy();
            assertThat(inMemoryConfiguration.roleNames()).containsExactly("admin");
            assertThat(inMemoryConfiguration.excludedPolicy()).isEmpty();
            assertThat(inMemoryConfiguration.uncheckedPolicy()).isEmpty();

            configuration.commit();
            assertThat(configuration.inService()).isTrue();
            assertThat(factory.inService("application")).isTrue();

            PolicyConfiguration reopened = factory.getPolicyConfiguration("application", false);
            assertThat(reopened).isSameAs(configuration);
            assertThat(reopened.inService()).isFalse();

            PolicyConfiguration linked = factory.getPolicyConfiguration("shared", true);
            configuration.linkConfiguration(linked);
            assertThat(inMemoryConfiguration.linkedContextIds()).containsExactly("shared");

            configuration.delete();
            assertThat(configuration.inService()).isFalse();
            assertThat(factory.inService("application")).isFalse();
        } finally {
            Thread.currentThread().setContextClassLoader(previousContextClassLoader);
            if (previousProvider == null) {
                System.clearProperty(FACTORY_PROVIDER_PROPERTY);
            } else {
                System.setProperty(FACTORY_PROVIDER_PROPERTY, previousProvider);
            }
        }
    }

    @Test
    void policyContextExceptionConstructorsExposeMessageAndCause() {
        Throwable cause = new IllegalStateException("root cause");

        assertThat(new PolicyContextException()).hasNoCause();
        assertThat(new PolicyContextException("problem")).hasMessage("problem").hasNoCause();
        assertThat(new PolicyContextException(cause)).hasCause(cause);
        assertThat(new PolicyContextException("problem", cause)).hasMessage("problem").hasCause(cause);
    }

    private static PermissionCollection permissions(Permission permission) {
        Permissions permissions = new Permissions();
        permissions.add(permission);
        return permissions;
    }

    public static final class RecordingPolicyContextHandler implements PolicyContextHandler {
        private final String key;
        private final List<String> seenKeys = new ArrayList<>();

        RecordingPolicyContextHandler(String key) {
            this.key = key;
        }

        @Override
        public Object getContext(String requestedKey, Object data) {
            seenKeys.add(requestedKey);
            return "context:" + data;
        }

        @Override
        public String[] getKeys() {
            return new String[] {key};
        }

        @Override
        public boolean supports(String requestedKey) {
            return key.equals(requestedKey);
        }

        List<String> seenKeys() {
            return seenKeys;
        }
    }

    public static final class InMemoryPolicyConfigurationFactory extends PolicyConfigurationFactory {
        private final Map<String, InMemoryPolicyConfiguration> configurations = new LinkedHashMap<>();

        public InMemoryPolicyConfigurationFactory() {
        }

        @Override
        public synchronized PolicyConfiguration getPolicyConfiguration(String contextID, boolean remove) {
            InMemoryPolicyConfiguration configuration = configurations.computeIfAbsent(
                    contextID, InMemoryPolicyConfiguration::new);
            configuration.open(remove);
            return configuration;
        }

        @Override
        public synchronized boolean inService(String contextID) {
            InMemoryPolicyConfiguration configuration = configurations.get(contextID);
            return configuration != null && configuration.inService();
        }
    }

    public static final class InMemoryPolicyConfiguration implements PolicyConfiguration {
        private final String contextID;
        private final Map<String, List<Permission>> roles = new LinkedHashMap<>();
        private final List<Permission> excludedPolicy = new ArrayList<>();
        private final List<Permission> uncheckedPolicy = new ArrayList<>();
        private final Set<String> linkedContextIds = new LinkedHashSet<>();
        private boolean inService;
        private boolean deleted;

        InMemoryPolicyConfiguration(String contextID) {
            this.contextID = contextID;
        }

        @Override
        public void addToExcludedPolicy(Permission permission) {
            excludedPolicy.add(permission);
        }

        @Override
        public void addToExcludedPolicy(PermissionCollection permissions) {
            excludedPolicy.addAll(Collections.list(permissions.elements()));
        }

        @Override
        public void addToRole(String roleName, Permission permission) {
            roles.computeIfAbsent(roleName, ignored -> new ArrayList<>()).add(permission);
        }

        @Override
        public void addToRole(String roleName, PermissionCollection permissions) {
            roles.computeIfAbsent(roleName, ignored -> new ArrayList<>())
                    .addAll(Collections.list(permissions.elements()));
        }

        @Override
        public void addToUncheckedPolicy(Permission permission) {
            uncheckedPolicy.add(permission);
        }

        @Override
        public void addToUncheckedPolicy(PermissionCollection permissions) {
            uncheckedPolicy.addAll(Collections.list(permissions.elements()));
        }

        @Override
        public void commit() {
            inService = true;
            deleted = false;
        }

        @Override
        public void delete() {
            roles.clear();
            excludedPolicy.clear();
            uncheckedPolicy.clear();
            linkedContextIds.clear();
            inService = false;
            deleted = true;
        }

        @Override
        public String getContextID() {
            return contextID;
        }

        @Override
        public boolean inService() {
            return inService && !deleted;
        }

        @Override
        public void linkConfiguration(PolicyConfiguration link) throws PolicyContextException {
            linkedContextIds.add(link.getContextID());
        }

        @Override
        public void removeExcludedPolicy() {
            excludedPolicy.clear();
        }

        @Override
        public void removeRole(String roleName) {
            roles.remove(roleName);
        }

        @Override
        public void removeUncheckedPolicy() {
            uncheckedPolicy.clear();
        }

        void open(boolean remove) {
            inService = false;
            deleted = false;
            if (remove) {
                roles.clear();
                excludedPolicy.clear();
                uncheckedPolicy.clear();
            }
        }

        Set<String> roleNames() {
            return roles.keySet();
        }

        List<Permission> excludedPolicy() {
            return excludedPolicy;
        }

        List<Permission> uncheckedPolicy() {
            return uncheckedPolicy;
        }

        Set<String> linkedContextIds() {
            return linkedContextIds;
        }
    }
}
