/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package h2.dynamic;

import org.h2.util.JdbcUtils;
import org.junit.jupiter.api.Test;

import java.io.Serializable;

import static org.assertj.core.api.Assertions.assertThat;

public class JdbcUtilsAnonymous1Test {
    static {
        System.setProperty("h2.useThreadContextClassLoader", "true");
    }

    @Test
    void deserializesWithThreadContextClassLoaderWhenConfigured() {
        Serializable value = "context-loader-value";
        byte[] serialized = JdbcUtils.serialize(value, null);

        Object deserialized = JdbcUtils.deserialize(serialized, null);

        assertThat(deserialized).isEqualTo(value);
    }
}
