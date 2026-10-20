/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_httpcomponents.httpclient;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;

/// Exercises HttpClient's URI construction subsystem to search a catalog with encoded paths and multi-valued filters.
public class CatalogSearchUriTest {
    @Test
    @Timeout(60)
    public void searchesReservedCharactersAndKeepsFiltersWhenMovingToTheNextPage()
            throws IOException, URISyntaxException {
        String category = "Caf\u00e9 & tea";
        String searchTerm = "green tea + honey & lemon";
        Map<String, String> pages = Map.of(
                "1", "sku,name\nTEA-21,Green tea with honey\n",
                "2", "sku,name\nTEA-36,Green tea with lemon\n");
        List<String> requestedPages = new CopyOnWriteArrayList<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/catalog/", exchange -> {
            Map<String, List<String>> query = decodeQuery(exchange.getRequestURI().getRawQuery());
            List<String> pageValues = query.getOrDefault("page", List.of());
            if (!"GET".equals(exchange.getRequestMethod())
                    || !("/catalog/" + category + "/items").equals(exchange.getRequestURI().getPath())
                    || !List.of(searchTerm).equals(query.get("q"))
                    || !List.of("fair trade", "organic+certified").equals(query.get("tag"))
                    || pageValues.size() != 1 || !pages.containsKey(pageValues.getFirst())
                    || query.size() != 3) {
                respond(exchange, 400, "Invalid catalog search");
                return;
            }
            String page = pageValues.getFirst();
            requestedPages.add(page);
            respond(exchange, 200, pages.get(page));
        });
        server.start();

        RequestConfig config = RequestConfig.custom()
                .setConnectTimeout(10_000)
                .setSocketTimeout(10_000)
                .setConnectionRequestTimeout(10_000)
                .build();
        try (CloseableHttpClient client = HttpClients.custom().setDefaultRequestConfig(config).build()) {
            URI searchUri = new URIBuilder()
                    .setScheme("http")
                    .setHost("127.0.0.1")
                    .setPort(server.getAddress().getPort())
                    .setCharset(StandardCharsets.UTF_8)
                    .setPathSegments("catalog", category, "items")
                    .addParameter("q", searchTerm)
                    .addParameter("tag", "fair trade")
                    .addParameter("tag", "organic+certified")
                    .addParameter("page", "1")
                    .build();
            assertEquals(pages.get("1"), downloadPage(client, searchUri));

            URI nextPageUri = new URIBuilder(searchUri)
                    .setCharset(StandardCharsets.UTF_8)
                    .setParameter("page", "2")
                    .build();
            assertEquals(pages.get("2"), downloadPage(client, nextPageUri));
            assertEquals(List.of("1", "2"), requestedPages);
        } finally {
            server.stop(0);
        }
    }

    private static String downloadPage(CloseableHttpClient client, URI uri) throws IOException {
        try (CloseableHttpResponse response = client.execute(new HttpGet(uri))) {
            assertEquals(200, response.getStatusLine().getStatusCode());
            return EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8);
        }
    }

    private static Map<String, List<String>> decodeQuery(String rawQuery) {
        Map<String, List<String>> parameters = new LinkedHashMap<>();
        if (rawQuery == null) {
            return parameters;
        }
        for (String part : rawQuery.split("&")) {
            String[] pair = part.split("=", 2);
            String name = URLDecoder.decode(pair[0], StandardCharsets.UTF_8);
            String value = pair.length == 2 ? URLDecoder.decode(pair[1], StandardCharsets.UTF_8) : "";
            parameters.computeIfAbsent(name, key -> new ArrayList<>()).add(value);
        }
        return parameters;
    }

    private static void respond(HttpExchange exchange, int status, String body) throws IOException {
        try (exchange) {
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "text/csv; charset=UTF-8");
            exchange.sendResponseHeaders(status, bytes.length);
            exchange.getResponseBody().write(bytes);
        }
    }
}
