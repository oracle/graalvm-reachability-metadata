/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_kylin.kylin_jdbc;

import org.apache.kylin.jdbc.shaded.org.apache.calcite.avatica.AvaticaUtils;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class AvaticaUtilsTest {
    @Test
    void instantiatesPluginsFromAClassAndAStaticField() {
        Greeting constructed = AvaticaUtils.instantiatePlugin(Greeting.class, ConstructedGreeting.class.getName());
        Greeting shared = AvaticaUtils.instantiatePlugin(
                Greeting.class, GreetingHolder.class.getName() + "#INSTANCE");

        assertThat(constructed.message()).isEqualTo("constructed");
        assertThat(shared.message()).isEqualTo("shared");
    }

    public interface Greeting {
        String message();
    }

    public static class ConstructedGreeting implements Greeting {
        public ConstructedGreeting() {
        }

        @Override
        public String message() {
            return "constructed";
        }
    }

    public static class GreetingHolder {
        public static final Greeting INSTANCE = new SharedGreeting();
    }

    public static class SharedGreeting implements Greeting {
        @Override
        public String message() {
            return "shared";
        }
    }
}
