/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package net_java_dev_jets3t.jets3t;

import java.net.URL;

import org.apache.commons.httpclient.ProxyHost;
import org.apache.commons.httpclient.contrib.proxy.PluginProxyUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;

import static org.assertj.core.api.Assertions.assertThat;

public class PluginProxyUtilTest {
    private static final String PLUGIN_PROXY_CONFIG_PROPERTY = "javaplugin.proxy.config.list";

    @Test
    @ResourceLock("systemProperties")
    public void detectsProxyFromPluginProxyConfigProperty() throws Exception {
        String previousProxyConfig = System.getProperty(PLUGIN_PROXY_CONFIG_PROPERTY);
        try {
            System.setProperty(PLUGIN_PROXY_CONFIG_PROPERTY, "HTTP=proxy.example.test:8080");

            ProxyHost proxyHost = PluginProxyUtil.detectProxy(new URL("http://example.test/resource"));

            assertThat(proxyHost).isNotNull();
            assertThat(proxyHost.getHostName()).isEqualTo("PROXY.EXAMPLE.TEST");
            assertThat(proxyHost.getPort()).isEqualTo(8080);
        } finally {
            restoreProperty(previousProxyConfig);
        }
    }

    private static void restoreProperty(String previousValue) {
        if (previousValue == null) {
            System.clearProperty(PLUGIN_PROXY_CONFIG_PROPERTY);
        } else {
            System.setProperty(PLUGIN_PROXY_CONFIG_PROPERTY, previousValue);
        }
    }
}
