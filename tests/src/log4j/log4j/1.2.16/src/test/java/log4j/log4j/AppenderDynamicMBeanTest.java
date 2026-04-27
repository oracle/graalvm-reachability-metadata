/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package log4j.log4j;

import org.apache.log4j.ConsoleAppender;
import org.apache.log4j.Level;
import org.apache.log4j.jmx.AppenderDynamicMBean;
import org.junit.jupiter.api.Test;

import javax.management.Attribute;
import javax.management.MBeanAttributeInfo;
import javax.management.MBeanInfo;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class AppenderDynamicMBeanTest {

    @Test
    void exposesConsoleAppenderPropertiesThroughDynamicMBean() throws Exception {
        ConsoleAppender appender = new ConsoleAppender();
        appender.setName("console-appender");
        appender.setTarget(ConsoleAppender.SYSTEM_OUT);
        appender.setFollow(true);
        appender.setThreshold(Level.INFO);

        AppenderDynamicMBean mBean = new AppenderDynamicMBean(appender);
        MBeanInfo mBeanInfo = mBean.getMBeanInfo();
        List<String> attributeNames = Arrays.stream(mBeanInfo.getAttributes())
                .map(MBeanAttributeInfo::getName)
                .toList();

        assertThat(mBeanInfo.getConstructors()).isNotEmpty();
        assertThat(attributeNames).contains("name", "target", "follow", "threshold");
        assertThat(mBean.getAttribute("name")).isEqualTo("console-appender");
        assertThat(mBean.getAttribute("target")).isEqualTo(ConsoleAppender.SYSTEM_OUT);
        assertThat(mBean.getAttribute("follow")).isEqualTo(Boolean.TRUE);
        assertThat(mBean.getAttribute("threshold")).isSameAs(Level.INFO);
    }

    @Test
    void updatesConsoleAppenderPropertiesThroughDynamicMBean() throws Exception {
        ConsoleAppender appender = new ConsoleAppender();
        appender.setName("console-appender");
        appender.setTarget(ConsoleAppender.SYSTEM_OUT);
        appender.setThreshold(Level.INFO);

        AppenderDynamicMBean mBean = new AppenderDynamicMBean(appender);

        mBean.setAttribute(new Attribute("name", "updated-console-appender"));
        mBean.setAttribute(new Attribute("target", ConsoleAppender.SYSTEM_ERR));
        mBean.setAttribute(new Attribute("follow", Boolean.TRUE));
        mBean.setAttribute(new Attribute("threshold", "WARN"));

        assertThat(appender.getName()).isEqualTo("updated-console-appender");
        assertThat(appender.getTarget()).isEqualTo(ConsoleAppender.SYSTEM_ERR);
        assertThat(appender.getFollow()).isTrue();
        assertThat(appender.getThreshold()).isSameAs(Level.WARN);
        assertThat(mBean.getAttribute("target")).isEqualTo(ConsoleAppender.SYSTEM_ERR);
        assertThat(mBean.getAttribute("threshold")).isSameAs(Level.WARN);
    }
}
