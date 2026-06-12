/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_pekko.pekko_http_2_13;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.URL;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

import org.apache.pekko.actor.ActorSystem;
import org.apache.pekko.http.javadsl.Http;
import org.apache.pekko.http.javadsl.ServerBinding;
import org.apache.pekko.http.javadsl.model.ContentTypes;
import org.apache.pekko.http.javadsl.server.AllDirectives;
import org.apache.pekko.http.javadsl.server.Route;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import scala.concurrent.Await;

public class FileAndResourceDirectivesTest {
    private static final String RESOURCE_NAME = "served-resource.txt";
    private static final String RESOURCE_CONTENT = "Pekko HTTP resource directives served this "
            + "content.";

    @TempDir
    Path temporaryDirectory;

    @Test
    void getFromResourceServesResourceFromProvidedClassLoader() throws Exception {
        Path resourceFile = temporaryDirectory.resolve(RESOURCE_NAME);
        Files.writeString(resourceFile, RESOURCE_CONTENT, StandardCharsets.UTF_8);

        ActorSystem system = ActorSystem.create("file-and-resource-directives-test");
        ServerBinding binding = null;
        try {
            Route route = new ResourceRoutes().resourceRoute(resourceFile.toUri().toURL());
            binding = Http.get(system).newServerAt("127.0.0.1", 0).bind(route)
                    .toCompletableFuture().get(10, TimeUnit.SECONDS);

            HttpResponse<String> response = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(5))
                    .build()
                    .send(HttpRequest.newBuilder(serverUri(binding))
                            .timeout(Duration.ofSeconds(10))
                            .GET()
                            .build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

            assertThat(response.statusCode()).isEqualTo(200);
            assertThat(response.body()).isEqualTo(RESOURCE_CONTENT);
        } finally {
            try {
                if (binding != null) {
                    binding.unbind().toCompletableFuture().get(10, TimeUnit.SECONDS);
                }
            } finally {
                Await.result(system.terminate(),
                        scala.concurrent.duration.Duration.create(10, TimeUnit.SECONDS));
            }
        }
    }

    private static URI serverUri(ServerBinding binding) {
        return URI.create("http://127.0.0.1:" + binding.localAddress().getPort() + "/");
    }

    private static final class ResourceRoutes extends AllDirectives {
        Route resourceRoute(URL resourceUrl) {
            return getFromResource(RESOURCE_NAME, ContentTypes.TEXT_PLAIN_UTF8,
                    new SingleResourceClassLoader(resourceUrl));
        }
    }

    private static final class SingleResourceClassLoader extends ClassLoader {
        private final URL resourceUrl;

        SingleResourceClassLoader(URL resourceUrl) {
            super(FileAndResourceDirectivesTest.class.getClassLoader());
            this.resourceUrl = resourceUrl;
        }

        @Override
        public URL getResource(String name) {
            if (RESOURCE_NAME.equals(name)) {
                return resourceUrl;
            }
            return super.getResource(name);
        }
    }
}
