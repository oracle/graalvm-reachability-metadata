/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_tomcat_embed.tomcat_embed_core;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.file.Path;
import java.time.Duration;

import jakarta.servlet.http.HttpServlet;
import org.apache.catalina.Context;
import org.apache.catalina.Lifecycle;
import org.apache.catalina.LifecycleListener;
import org.apache.catalina.Valve;
import org.apache.catalina.Wrapper;
import org.apache.catalina.connector.Connector;
import org.apache.catalina.core.StandardHost;
import org.apache.catalina.startup.Tomcat;

final class EmbeddedTomcatSupport implements AutoCloseable {

    private final Tomcat tomcat = new Tomcat();
    private final Connector connector = new Connector();
    private final Wrapper servletWrapper;

    EmbeddedTomcatSupport(Path baseDirectory, HttpServlet servlet) throws Exception {
        tomcat.setBaseDir(baseDirectory.toString());
        connector.setPort(0);
        tomcat.setConnector(connector);
        Context context = tomcat.addContext("", baseDirectory.toString());
        configureContextWithoutWebappScanning(context);
        servletWrapper = Tomcat.addServlet(context, "test-servlet", servlet);
        context.addServletMappingDecoded("/test", "test-servlet");
    }

    static void configureContextWithoutWebappScanning(Context context) {
        for (LifecycleListener listener : context.findLifecycleListeners()) {
            if (listener instanceof Tomcat.FixContextListener) {
                context.removeLifecycleListener(listener);
            }
        }
        context.addLifecycleListener(event -> {
            if (Lifecycle.CONFIGURE_START_EVENT.equals(event.getType())) {
                context.setConfigured(true);
            }
        });
    }

    Connector connector() {
        return connector;
    }

    void addServletInitParameter(String name, String value) {
        servletWrapper.addInitParameter(name, value);
    }

    void replaceErrorReportValve(Valve valve) {
        StandardHost host = (StandardHost) tomcat.getHost();
        host.setErrorReportValveClass(null);
        host.getPipeline().addValve(valve);
    }

    void start() throws Exception {
        tomcat.start();
    }

    HttpResponse<String> request(String method) throws Exception {
        try (HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build()) {
            URI uri = URI.create("http://localhost:" + connector.getLocalPort() + "/test");
            HttpRequest request = HttpRequest.newBuilder(uri).method(method, HttpRequest.BodyPublishers.noBody())
                    .timeout(Duration.ofSeconds(10)).build();
            return client.send(request, BodyHandlers.ofString());
        }
    }

    @Override
    public void close() throws Exception {
        try {
            tomcat.stop();
        } finally {
            tomcat.destroy();
        }
    }
}
