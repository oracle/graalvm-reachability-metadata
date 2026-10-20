/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_httpcomponents.httpclient;

import com.sun.net.httpserver.HttpServer;
import org.apache.http.client.cache.CacheResponseStatus;
import org.apache.http.client.cache.HttpCacheContext;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.cache.CacheConfig;
import org.apache.http.impl.client.cache.CachingHttpClients;
import org.apache.http.util.EntityUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;

/// Exercises HttpClient's HTTP caching subsystem to reuse a product catalog and revalidate it with an ETag.
public class CachedProductCatalogTest {
    @Test
    @Timeout(60)
    public void servesRepeatedCatalogReadsLocallyAndConditionallyRefreshesOnDemand() throws IOException {
        String catalog = """
                sku,description,price
                TOOL-17,Socket wrench,29.95
                """;
        String catalogTag = "\"catalog-spring\"";
        List<String> originValidators = new CopyOnWriteArrayList<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/catalog/products", exchange -> {
            try (exchange) {
                String validator = exchange.getRequestHeaders().getFirst("If-None-Match");
                originValidators.add(validator == null ? "" : validator);
                exchange.getResponseHeaders().set("Cache-Control", "public, max-age=3600");
                exchange.getResponseHeaders().set("ETag", catalogTag);
                if (catalogTag.equals(validator)) {
                    exchange.sendResponseHeaders(304, -1);
                } else {
                    byte[] bytes = catalog.getBytes(StandardCharsets.UTF_8);
                    exchange.getResponseHeaders().set("Content-Type", "text/csv; charset=UTF-8");
                    exchange.sendResponseHeaders(200, bytes.length);
                    exchange.getResponseBody().write(bytes);
                }
            }
        });
        server.start();

        RequestConfig requests = RequestConfig.custom()
                .setConnectTimeout(10_000)
                .setSocketTimeout(10_000)
                .setConnectionRequestTimeout(10_000)
                .build();
        CacheConfig cache = CacheConfig.custom()
                .setMaxCacheEntries(20)
                .setMaxObjectSize(64 * 1024)
                .build();
        String catalogUrl = "http://127.0.0.1:" + server.getAddress().getPort() + "/catalog/products";
        try (CloseableHttpClient client = CachingHttpClients.custom()
                .setCacheConfig(cache)
                .setDefaultRequestConfig(requests)
                .build()) {
            HttpCacheContext firstRead = HttpCacheContext.create();
            assertEquals(catalog, readCatalog(client, new HttpGet(catalogUrl), firstRead));
            assertEquals(CacheResponseStatus.CACHE_MISS, firstRead.getCacheResponseStatus());
            assertEquals(List.of(""), originValidators);

            HttpCacheContext repeatedRead = HttpCacheContext.create();
            assertEquals(catalog, readCatalog(client, new HttpGet(catalogUrl), repeatedRead));
            assertEquals(CacheResponseStatus.CACHE_HIT, repeatedRead.getCacheResponseStatus());
            assertEquals(List.of(""), originValidators, "A fresh catalog should not require another origin request");

            HttpGet refresh = new HttpGet(catalogUrl);
            // A two-hour freshness requirement exceeds the stored catalog's one-hour lifetime.
            refresh.setHeader("Cache-Control", "min-fresh=7200");
            HttpCacheContext refreshedRead = HttpCacheContext.create();
            assertEquals(catalog, readCatalog(client, refresh, refreshedRead));
            assertEquals(CacheResponseStatus.VALIDATED, refreshedRead.getCacheResponseStatus());
            assertEquals(List.of("", catalogTag), originValidators,
                    "An explicit refresh should validate the stored representation instead of downloading it again");
        } finally {
            server.stop(0);
        }
    }

    private static String readCatalog(CloseableHttpClient client, HttpGet request, HttpCacheContext context)
            throws IOException {
        try (CloseableHttpResponse response = client.execute(request, context)) {
            assertEquals(200, response.getStatusLine().getStatusCode());
            return EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8);
        }
    }
}
