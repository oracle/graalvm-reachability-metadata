/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_tomcat_embed.tomcat_embed_core;

import org.apache.tomcat.util.IntrospectionUtils;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class IntrospectionUtilsTest {

    @Test
    void setsNamedAndFallbackPropertiesAndCallsMethods() throws Exception {
        ConfigurableComponent component = new ConfigurableComponent();
        VoidFallbackComponent voidFallback = new VoidFallbackComponent();

        boolean namedPropertySet = IntrospectionUtils.setProperty(component, "port", "8080");
        boolean fallbackPropertySet = IntrospectionUtils.setProperty(component, "mode", "strict");
        boolean voidFallbackPropertySet = IntrospectionUtils.setProperty(voidFallback, "mode", "lenient");
        Object description = IntrospectionUtils.callMethodN(component, "describe",
                new Object[] {"server", Integer.valueOf(2) }, new Class<?>[] {String.class, Integer.class });

        assertThat(namedPropertySet).isTrue();
        assertThat(fallbackPropertySet).isTrue();
        assertThat(voidFallbackPropertySet).isTrue();
        assertThat(component.getPort()).isEqualTo(8080);
        assertThat(component.getMode()).isEqualTo("strict");
        assertThat(voidFallback.getMode()).isEqualTo("lenient");
        assertThat(description).isEqualTo("server-2");
    }

    public static final class VoidFallbackComponent {
        private String mode;

        public String getMode() {
            return mode;
        }

        public void setProperty(String name, String value) {
            if ("mode".equals(name)) {
                mode = value;
            }
        }
    }

    public static final class ConfigurableComponent {
        private int port;
        private String mode;

        public int getPort() {
            return port;
        }

        public String getMode() {
            return mode;
        }

        public void setPort(int port) {
            this.port = port;
        }

        public boolean setProperty(String name, Integer value) {
            return false;
        }

        public void setProperty(String name, String value) {
            if ("mode".equals(name)) {
                mode = value;
            }
        }

        public String describe(String name, Integer count) {
            return name + "-" + count;
        }
    }
}
