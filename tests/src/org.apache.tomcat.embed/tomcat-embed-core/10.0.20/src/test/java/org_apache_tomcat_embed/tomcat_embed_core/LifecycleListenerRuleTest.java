/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_tomcat_embed.tomcat_embed_core;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import org.apache.catalina.core.StandardHost;
import org.apache.catalina.startup.HostConfig;
import org.apache.catalina.startup.LifecycleListenerRule;
import org.apache.tomcat.util.digester.Digester;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class LifecycleListenerRuleTest {

    @Test
    void createsAndAttachesConfiguredLifecycleListener() throws Exception {
        StandardHost host = new StandardHost();
        Digester digester = new Digester();
        digester.push(host);
        digester.addRule("Host", new LifecycleListenerRule(HostConfig.class.getName(), "configClass"));

        digester.parse(new ByteArrayInputStream("<Host/>".getBytes(StandardCharsets.UTF_8)));

        assertThat(Arrays.stream(host.findLifecycleListeners())).anyMatch(HostConfig.class::isInstance);
    }
}
