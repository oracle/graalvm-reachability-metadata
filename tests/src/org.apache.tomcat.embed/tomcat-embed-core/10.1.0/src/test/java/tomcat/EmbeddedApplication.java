/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package tomcat;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import jakarta.servlet.http.HttpServlet;
import org.apache.catalina.Context;
import org.apache.catalina.Wrapper;
import org.apache.catalina.startup.Tomcat;

final class EmbeddedApplication implements AutoCloseable {
    final Tomcat server = new Tomcat();
    final Context context;

    EmbeddedApplication(File base) throws IOException {
        File webapp = new File(base, "webapp");
        if (!webapp.mkdir()) {
            throw new IOException("Cannot create web application directory");
        }
        server.setBaseDir(base.getAbsolutePath());
        server.setPort(0);
        server.getConnector().setProperty("address", "127.0.0.1");
        context = server.addContext("", webapp.getAbsolutePath());
        context.setParentClassLoader(EmbeddedApplication.class.getClassLoader());
    }

    Wrapper addServlet(HttpServlet servlet) {
        Wrapper wrapper = Tomcat.addServlet(context, "application", servlet);
        context.addServletMappingDecoded("/*", "application");
        return wrapper;
    }

    void start() throws Exception {
        server.start();
        if (!context.getState().isAvailable()) {
            throw new IllegalStateException("Web application did not start");
        }
    }

    HttpResponse<String> request(String method) throws Exception {
        try (HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build()) {
            HttpRequest request = HttpRequest.newBuilder(uri()).timeout(Duration.ofSeconds(10))
                    .method(method, HttpRequest.BodyPublishers.noBody()).build();
            return client.send(request, HttpResponse.BodyHandlers.ofString());
        }
    }

    URI uri() {
        return URI.create("http://127.0.0.1:" + server.getConnector().getLocalPort() + "/resource");
    }

    @Override
    public void close() throws Exception {
        try {
            server.stop();
        } finally {
            server.destroy();
        }
    }
}
