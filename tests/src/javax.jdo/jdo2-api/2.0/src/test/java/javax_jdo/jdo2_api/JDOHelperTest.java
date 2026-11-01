/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package javax_jdo.jdo2_api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Properties;

import javax.jdo.JDOFatalUserException;
import javax.jdo.JDOHelper;

import org.junit.jupiter.api.Test;

public class JDOHelperTest {
    @Test
    void requiresAFactoryClassInProperties() {
        Properties properties = new Properties();
        properties.setProperty("javax.jdo.option.ConnectionURL", "jdbc:example:accounts");

        assertThatThrownBy(() -> JDOHelper.getPersistenceManagerFactory(properties))
                .isInstanceOf(JDOFatalUserException.class)
                .hasMessageContaining("javax.jdo.PersistenceManagerFactoryClass");
    }

    @Test
    void readsFactoryConfigurationFromAnInputStream() throws IOException {
        String configuration = """
                javax.jdo.option.ConnectionURL=jdbc:example:accounts
                javax.jdo.option.Optimistic=true
                """;
        try (ByteArrayInputStream stream = new ByteArrayInputStream(configuration.getBytes(StandardCharsets.ISO_8859_1))) {
            assertThatThrownBy(() -> JDOHelper.getPersistenceManagerFactory(stream))
                    .isInstanceOf(JDOFatalUserException.class)
                    .hasMessageContaining("javax.jdo.PersistenceManagerFactoryClass");
        }
    }

    @Test
    void readsFactoryConfigurationFromAClasspathResource() {
        assertThatThrownBy(() -> JDOHelper.getPersistenceManagerFactory("jdo-incomplete.properties"))
                .isInstanceOf(JDOFatalUserException.class)
                .hasMessageContaining("javax.jdo.PersistenceManagerFactoryClass");
    }

    @Test
    void reportsAnUnavailableConfiguredProvider() {
        Properties properties = new Properties();
        properties.setProperty("javax.jdo.PersistenceManagerFactoryClass", "example.persistence.UnavailableFactory");

        assertThatThrownBy(() -> JDOHelper.getPersistenceManagerFactory(properties, getClass().getClassLoader()))
                .isInstanceOf(JDOFatalUserException.class)
                .hasMessageContaining("example.persistence.UnavailableFactory")
                .hasCauseInstanceOf(ClassNotFoundException.class);
    }

    @Test
    void treatsUnmanagedObjectsAsTransient() {
        Object account = new Object();

        JDOHelper.makeDirty(account, "balance");

        assertThat(JDOHelper.getObjectId(account)).isNull();
        assertThat(JDOHelper.getTransactionalObjectId(account)).isNull();
        assertThat(JDOHelper.getPersistenceManager(account)).isNull();
        assertThat(JDOHelper.getVersion(account)).isNull();
        assertThat(JDOHelper.isPersistent(account)).isFalse();
        assertThat(JDOHelper.isTransactional(account)).isFalse();
        assertThat(JDOHelper.isDirty(account)).isFalse();
        assertThat(JDOHelper.isNew(account)).isFalse();
        assertThat(JDOHelper.isDeleted(account)).isFalse();
        assertThat(JDOHelper.isDetached(account)).isFalse();
        assertThat(JDOHelper.getObjectIds(new Object[]{account, null})).containsExactly(null, null);
    }
}
