/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_atomikos.atomikos_util;

import static org.assertj.core.api.Assertions.assertThat;

import com.atomikos.beans.PropertyUtils;
import java.util.Properties;
import java.util.Set;
import org.junit.jupiter.api.Test;

public class PropertyUtilsTest {
    @Test
    void setsNestedBeanPropertiesAndCreatesMissingIntermediateBean() throws Exception {
        RootBean rootBean = new RootBean();

        PropertyUtils.setProperty(rootBean, "child.name", "transactions");
        PropertyUtils.setProperty(rootBean, "child.port", "1234");
        PropertyUtils.setProperty(rootBean, "child.enabled", "true");
        PropertyUtils.setProperty(rootBean, "child.tags", "xa,jta");

        assertThat(rootBean.getChild()).isNotNull();
        assertThat(rootBean.getChild().getName()).isEqualTo("transactions");
        assertThat(rootBean.getChild().getPort()).isEqualTo(1234);
        assertThat(rootBean.getChild().isEnabled()).isTrue();
        assertThat(rootBean.getChild().getTags()).containsExactlyInAnyOrder("xa", "jta");
    }

    @Test
    void setsNullAndPropertiesValues() throws Exception {
        RootBean rootBean = new RootBean();
        rootBean.setChild(new ChildBean());

        PropertyUtils.setProperty(rootBean, "child.name", null);
        PropertyUtils.setProperty(rootBean, "configuration.timeout", "30");

        assertThat(rootBean.getChild().getName()).isNull();
        assertThat(rootBean.getConfiguration()).containsEntry("timeout", "30");
    }

    public static class RootBean {
        private ChildBean child;
        private final Properties configuration = new Properties();

        public ChildBean getChild() {
            return child;
        }

        public void setChild(ChildBean child) {
            this.child = child;
        }

        public Properties getConfiguration() {
            return configuration;
        }
    }

    public static class ChildBean {
        private String name;
        private int port;
        private boolean enabled;
        private Set<String> tags;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public int getPort() {
            return port;
        }

        public void setPort(int port) {
            this.port = port;
        }

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public Set<String> getTags() {
            return tags;
        }

        public void setTags(Set<String> tags) {
            this.tags = tags;
        }
    }
}
