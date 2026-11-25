/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package tomcat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

import jakarta.servlet.http.HttpServlet;
import org.apache.catalina.Context;
import org.apache.catalina.Wrapper;
import org.apache.catalina.startup.Tomcat;

public final class EmbeddedServer implements AutoCloseable {
    private final Tomcat tomcat = new Tomcat();
    private final Context context;

    public EmbeddedServer(Path directory) throws Exception {
        tomcat.setBaseDir(directory.toString());
        tomcat.setPort(0);
        tomcat.getConnector();
        context = tomcat.addContext("", Files.createDirectories(directory.resolve("webroot")).toString());
    }

    public Context context() {
        return context;
    }

    public Tomcat tomcat() {
        return tomcat;
    }

    public Wrapper servlet(HttpServlet servlet) {
        Wrapper wrapper = Tomcat.addServlet(context, "application", servlet);
        context.addServletMappingDecoded("/*", "application");
        return wrapper;
    }

    public void start() throws Exception {
        tomcat.start();
    }

    public HttpResponse<String> request(String method, String path) throws Exception {
        URI uri = URI.create("http://localhost:" + tomcat.getConnector().getLocalPort() + path);
        try (HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build()) {
            HttpRequest request = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(10))
                    .method(method, HttpRequest.BodyPublishers.noBody()).build();
            return client.send(request, HttpResponse.BodyHandlers.ofString());
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
