/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package tomcat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.Principal;
import java.util.Map;
import java.util.Objects;

import javax.security.auth.Subject;
import javax.security.auth.callback.CallbackHandler;
import javax.security.auth.login.LoginException;
import javax.security.auth.spi.LoginModule;

import org.apache.catalina.LifecycleState;
import org.apache.catalina.TomcatPrincipal;
import org.apache.catalina.realm.GenericPrincipal;
import org.apache.catalina.realm.JAASRealm;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

public class JAASRealmTest {

    private static final String APP_NAME = "ReachabilityTest";
    private static final String RESOURCE_CONFIG = "tomcat/jaas-realm-resource.config";

    @TempDir
    Path temporaryDirectory;

    @Test
    void authenticatesWithJaasConfigurationLoadedFromClasspathResource() throws Exception {
        TestableJAASRealm realm = newRealm(RESOURCE_CONFIG, "resource");

        try {
            realm.start();

            Principal principal = realm.authenticate("alice", "secret");

            assertAuthenticatedPrincipal(principal, "alice", "admin");
        } finally {
            destroy(realm);
        }
    }

    @Test
    void authenticatesWithJaasConfigurationLoadedFromFileSource() throws Exception {
        Path configFile = temporaryDirectory.resolve("jaas-realm-file.config");
        Files.writeString(configFile, jaasConfig(), StandardCharsets.UTF_8);
        TestableJAASRealm realm = newRealm(configFile.toString(), "file");

        try {
            realm.start();

            Principal principal = realm.authenticate("bob", "secret");

            assertAuthenticatedPrincipal(principal, "bob", "admin");
        } finally {
            destroy(realm);
        }
    }

    private static TestableJAASRealm newRealm(String configFile, String realmPathSuffix) {
        TestableJAASRealm realm = new TestableJAASRealm();
        realm.setAppName(APP_NAME);
        realm.setConfigFile(configFile);
        realm.setRealmPath("/" + realmPathSuffix);
        realm.setUserClassNames(UserPrincipal.class.getName());
        realm.setRoleClassNames(RolePrincipal.class.getName());
        return realm;
    }

    private static String jaasConfig() {
        return """
                ReachabilityTest {
                    tomcat.JAASRealmTest$AcceptingLoginModule required;
                };
                """;
    }

    private static void assertAuthenticatedPrincipal(Principal principal, String username, String role)
            throws Exception {
        assertThat(principal).isInstanceOf(GenericPrincipal.class);
        GenericPrincipal genericPrincipal = (GenericPrincipal) principal;
        try {
            assertThat(genericPrincipal.getName()).isEqualTo(username);
            assertThat(genericPrincipal.getUserPrincipal()).isInstanceOf(UserPrincipal.class);
            assertThat(genericPrincipal.hasRole(role)).isTrue();
        } finally {
            ((TomcatPrincipal) genericPrincipal).logout();
        }
    }

    private static void destroy(JAASRealm realm) throws Exception {
        try {
            if (realm.getState().isAvailable()) {
                realm.stop();
            }
        } finally {
            if (realm.getState() != LifecycleState.DESTROYED) {
                realm.destroy();
            }
        }
    }

    public static class TestableJAASRealm extends JAASRealm {

        @Override
        public String getObjectNameKeyProperties() {
            return "type=Realm,realmPath=" + getRealmPath();
        }

        @Override
        public String getDomainInternal() {
            return "JAASRealmTest";
        }
    }

    public static class AcceptingLoginModule implements LoginModule {

        private Subject subject;
        private UserPrincipal userPrincipal;
        private RolePrincipal rolePrincipal;

        @Override
        public void initialize(Subject subject, CallbackHandler callbackHandler, Map<String, ?> sharedState,
                Map<String, ?> options) {
            this.subject = subject;
        }

        @Override
        public boolean login() {
            userPrincipal = new UserPrincipal("authenticated-user");
            rolePrincipal = new RolePrincipal("admin");
            return true;
        }

        @Override
        public boolean commit() {
            subject.getPrincipals().add(userPrincipal);
            subject.getPrincipals().add(rolePrincipal);
            return true;
        }

        @Override
        public boolean abort() throws LoginException {
            return logout();
        }

        @Override
        public boolean logout() {
            subject.getPrincipals().remove(userPrincipal);
            subject.getPrincipals().remove(rolePrincipal);
            userPrincipal = null;
            rolePrincipal = null;
            return true;
        }
    }

    public static class UserPrincipal extends NamedPrincipal {

        public UserPrincipal(String name) {
            super(name);
        }
    }

    public static class RolePrincipal extends NamedPrincipal {

        public RolePrincipal(String name) {
            super(name);
        }
    }

    public abstract static class NamedPrincipal implements Principal {

        private final String name;

        protected NamedPrincipal(String name) {
            this.name = name;
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (other == null || getClass() != other.getClass()) {
                return false;
            }
            NamedPrincipal principal = (NamedPrincipal) other;
            return Objects.equals(name, principal.name);
        }

        @Override
        public int hashCode() {
            return Objects.hash(getClass(), name);
        }
    }
}
