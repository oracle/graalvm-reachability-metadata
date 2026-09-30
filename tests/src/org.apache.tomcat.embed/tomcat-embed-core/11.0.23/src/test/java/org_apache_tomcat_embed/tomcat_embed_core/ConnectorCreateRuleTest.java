/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_tomcat_embed.tomcat_embed_core;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import org.apache.catalina.connector.Connector;
import org.apache.catalina.core.StandardService;
import org.apache.catalina.core.StandardThreadExecutor;
import org.apache.catalina.startup.ConnectorCreateRule;
import org.apache.coyote.http11.AbstractHttp11Protocol;
import org.apache.tomcat.util.digester.Digester;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ConnectorCreateRuleTest {

    @Test
    void configuresConnectorExecutorAndSslImplementationFromXml() throws Exception {
        StandardService service = new StandardService();
        StandardThreadExecutor executor = new StandardThreadExecutor();
        executor.setName("request-executor");
        service.addExecutor(executor);

        Digester digester = new Digester();
        digester.push(service);
        digester.addRule("Connector", new ConnectorCreateRule());
        digester.addSetNext("Connector", "addConnector", Connector.class.getName());
        String xml = """
                <Connector protocol="HTTP/1.1" executor="request-executor"
                           sslImplementationName="example.ssl.Implementation"/>
                """;

        digester.parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));

        assertThat(service.findConnectors()).hasSize(1);
        Connector connector = service.findConnectors()[0];
        assertThat(connector.getProtocolHandler().getExecutor()).isSameAs(executor);
        assertThat(((AbstractHttp11Protocol<?>) connector.getProtocolHandler()).getSslImplementationName())
                .isEqualTo("example.ssl.Implementation");
    }
}
