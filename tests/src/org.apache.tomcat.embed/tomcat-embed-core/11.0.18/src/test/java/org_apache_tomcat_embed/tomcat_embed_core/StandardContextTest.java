/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_tomcat_embed.tomcat_embed_core;

import org.apache.catalina.Wrapper;
import org.apache.catalina.core.StandardContext;
import org.apache.catalina.core.StandardWrapper;
import org.apache.catalina.startup.VersionLoggerListener;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class StandardContextTest {

    @Test
    void createsConfiguredWrapperWithLifecycleAndContainerListeners() {
        StandardContext context = new StandardContext();
        context.setWrapperClass(StandardWrapper.class.getName());
        context.addWrapperLifecycle(VersionLoggerListener.class.getName());

        Wrapper wrapper = context.createWrapper();

        assertThat(wrapper).isInstanceOf(StandardWrapper.class);
        assertThat(wrapper.findLifecycleListeners()).hasAtLeastOneElementOfType(VersionLoggerListener.class);
    }
}
