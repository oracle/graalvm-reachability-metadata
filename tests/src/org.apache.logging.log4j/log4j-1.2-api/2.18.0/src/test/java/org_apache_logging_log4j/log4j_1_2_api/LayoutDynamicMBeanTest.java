/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_logging_log4j.log4j_1_2_api;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;

import javax.management.Attribute;
import javax.management.MBeanAttributeInfo;
import javax.management.MBeanInfo;

import org.apache.log4j.Layout;
import org.apache.log4j.jmx.LayoutDynamicMBean;
import org.apache.log4j.spi.LoggingEvent;
import org.apache.log4j.spi.OptionHandler;
import org.junit.jupiter.api.Test;

public class LayoutDynamicMBeanTest {

    @Test
    void exposesAndUpdatesLayoutBeanProperties() throws Exception {
        ConfigurableLayout layout = new ConfigurableLayout();
        layout.setPattern("initial-pattern");

        LayoutDynamicMBean mbean = new LayoutDynamicMBean(layout);

        MBeanInfo mbeanInfo = mbean.getMBeanInfo();
        assertThat(Arrays.stream(mbeanInfo.getAttributes())
            .map(MBeanAttributeInfo::getName))
            .contains("pattern");

        assertThat(mbean.getAttribute("pattern")).isEqualTo("initial-pattern");

        mbean.setAttribute(new Attribute("pattern", "updated-pattern"));

        assertThat(layout.getPattern()).isEqualTo("updated-pattern");
        assertThat(mbean.getAttribute("pattern")).isEqualTo("updated-pattern");
    }

    @Test
    void invokesLayoutOptionActivation() throws Exception {
        ConfigurableLayout layout = new ConfigurableLayout();
        LayoutDynamicMBean mbean = new LayoutDynamicMBean(layout);

        Object result = mbean.invoke("activateOptions", new Object[0], new String[0]);

        assertThat(result).isEqualTo("Options activated.");
        assertThat(layout.isActivated()).isTrue();
    }

    public static final class ConfigurableLayout extends Layout implements OptionHandler {

        private String pattern;
        private boolean activated;

        public String getPattern() {
            return pattern;
        }

        public void setPattern(String pattern) {
            this.pattern = pattern;
        }

        public boolean isActivated() {
            return activated;
        }

        @Override
        public void activateOptions() {
            activated = true;
        }

        @Override
        public String format(LoggingEvent event) {
            return pattern;
        }

        @Override
        public boolean ignoresThrowable() {
            return true;
        }
    }
}
