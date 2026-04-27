/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package log4j.log4j;

import org.apache.log4j.Layout;
import org.apache.log4j.Level;
import org.apache.log4j.Priority;
import org.apache.log4j.jmx.LayoutDynamicMBean;
import org.apache.log4j.spi.LoggingEvent;
import org.junit.jupiter.api.Test;

import javax.management.Attribute;
import javax.management.MBeanAttributeInfo;
import javax.management.MBeanInfo;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class LayoutDynamicMBeanTest {

    @Test
    void exposesAndUpdatesLayoutPropertiesThroughDynamicMBean() throws Exception {
        TrackingLayout layout = new TrackingLayout();
        layout.setPrefix("prefix:");
        layout.setThreshold(Level.INFO);

        LayoutDynamicMBean mBean = new LayoutDynamicMBean(layout);
        MBeanInfo mBeanInfo = mBean.getMBeanInfo();
        List<String> attributeNames = Arrays.stream(mBeanInfo.getAttributes())
                .map(MBeanAttributeInfo::getName)
                .toList();

        assertThat(mBeanInfo.getConstructors()).isNotEmpty();
        assertThat(attributeNames).contains("prefix", "threshold");
        assertThat(mBean.getAttribute("prefix")).isEqualTo("prefix:");
        assertThat(mBean.getAttribute("threshold")).isSameAs(Level.INFO);

        mBean.setAttribute(new Attribute("prefix", "updated:"));
        mBean.setAttribute(new Attribute("threshold", "ERROR"));

        assertThat(layout.getPrefix()).isEqualTo("updated:");
        assertThat(layout.getThreshold()).isSameAs(Level.ERROR);
        assertThat(mBean.getAttribute("prefix")).isEqualTo("updated:");
        assertThat(mBean.getAttribute("threshold")).isSameAs(Level.ERROR);
    }

    @Test
    void activatesWrappedLayoutThroughDynamicOperation() throws Exception {
        TrackingLayout layout = new TrackingLayout();
        LayoutDynamicMBean mBean = new LayoutDynamicMBean(layout);

        assertThat(layout.isActivated()).isFalse();
        assertThat(mBean.invoke("activateOptions", new Object[0], new String[0]))
                .isEqualTo("Options activated.");
        assertThat(layout.isActivated()).isTrue();
    }

    public static final class TrackingLayout extends Layout {
        private boolean activated;
        private String prefix = "";
        private Priority threshold = Level.DEBUG;

        @Override
        public void activateOptions() {
            activated = true;
        }

        @Override
        public String format(LoggingEvent event) {
            return prefix + event.getRenderedMessage();
        }

        public String getPrefix() {
            return prefix;
        }

        public Priority getThreshold() {
            return threshold;
        }

        @Override
        public boolean ignoresThrowable() {
            return true;
        }

        public boolean isActivated() {
            return activated;
        }

        public void setPrefix(String prefix) {
            this.prefix = prefix;
        }

        public void setThreshold(Priority threshold) {
            this.threshold = threshold;
        }
    }
}
