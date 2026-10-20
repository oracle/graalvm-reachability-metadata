/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package dev_langchain4j.langchain4j;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.ollama.OllamaEmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingStoreIngestor;
import dev.langchain4j.store.embedding.filter.Filter;
import dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStore;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static dev.langchain4j.store.embedding.filter.MetadataFilterBuilder.metadataKey;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Runs a shop's product search on the langchain4j in-memory embedding store: ingestion through
 * an embedding model, similarity search narrowed by metadata filters, persistence of the store
 * to a JSON file, and reloading and pruning the persisted store.
 *
 * The shop embeds its product descriptions with an Ollama embedding model, keeping each SKU,
 * category and price as segment metadata. Shoppers search in free text, optionally within a
 * category and a budget. A nightly job saves the index to disk so the next start reloads it
 * without re-embedding the catalog, then drops a discontinued category. The test plays the
 * Ollama server with a small topic vector per text, so every ranking is deterministic.
 */
class ProductCatalogSemanticSearchTest {

    private static final Pattern INPUT_ARRAY = Pattern.compile("\"input\"\\s*:\\s*\\[(.*?)]", Pattern.DOTALL);

    private static final Pattern JSON_STRING = Pattern.compile("\"((?:[^\"\\\\]|\\\\.)*)\"");

    /// Word stems of the three topics the mock embedding model places on its three axes.
    private static final List<List<String>> TOPICS = List.of(
            List.of("coffee", "espresso", "brew", "cafe"),
            List.of("blender", "smoothie", "ice"),
            List.of("lamp", "light", "reading"));

    private final List<String> embedRequests = new CopyOnWriteArrayList<>();

    @TempDir
    Path dataDir;

    private HttpServer ollama;

    private EmbeddingModel embeddingModel;

    @BeforeEach
    void startModelServer() throws IOException {
        ollama = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        ollama.createContext("/api/embed", this::answerEmbed);
        ollama.start();

        embeddingModel = OllamaEmbeddingModel.builder()
                .baseUrl("http://localhost:" + ollama.getAddress().getPort())
                .modelName("nomic-embed-text")
                .timeout(Duration.ofSeconds(30))
                .maxRetries(0)
                .build();
    }

    @AfterEach
    void stopModelServer() {
        ollama.stop(0);
    }

    @Test
    void searchesFiltersPersistsAndPrunesTheProductIndex() {
        InMemoryEmbeddingStore<TextSegment> store = new InMemoryEmbeddingStore<>();
        EmbeddingStoreIngestor ingestor = EmbeddingStoreIngestor.builder()
                .embeddingModel(embeddingModel)
                .embeddingStore(store)
                .build();

        ingestor.ingest(List.of(
                product("K-100", "kitchen", 249, "Espresso machine with a steam wand for cafe style coffee at home."),
                product("K-200", "kitchen", 35, "Pour over dripper that brews a clean cup of coffee."),
                product("K-300", "kitchen", 89, "Countertop blender that crushes ice for smoothies."),
                product("L-100", "living", 59, "Dimmable floor lamp with warm light for reading.")));

        assertThat(embedRequests).hasSize(1);
        assertThat(embedRequests.get(0)).contains("nomic-embed-text", "Espresso machine", "floor lamp");

        Filter affordableKitchen = metadataKey("category").isEqualTo("kitchen")
                .and(metadataKey("price").isLessThan(100));
        List<EmbeddingMatch<TextSegment>> affordableCoffee = search(store, "coffee for my kitchen", affordableKitchen, 0.0);
        assertThat(skus(affordableCoffee)).containsExactly("K-200", "K-300");
        assertThat(affordableCoffee.get(0).embedded().text()).isEqualTo("Pour over dripper that brews a clean cup of coffee.");
        assertThat(affordableCoffee.get(0).embedded().metadata().getInteger("price")).isEqualTo(35);
        assertThat(affordableCoffee.get(0).score()).isGreaterThan(affordableCoffee.get(1).score());

        List<EmbeddingMatch<TextSegment>> relevantCoffee = search(store, "coffee for my kitchen", affordableKitchen, 0.8);
        assertThat(skus(relevantCoffee)).containsExactly("K-200");

        List<EmbeddingMatch<TextSegment>> readingLight = search(store, "a light for reading", null, 0.8);
        assertThat(skus(readingLight)).containsExactly("L-100");

        Path indexFile = dataDir.resolve("catalog-index.json");
        store.serializeToFile(indexFile);
        assertThat(indexFile).isRegularFile();
        assertThat(readString(indexFile)).contains("K-100", "Dimmable floor lamp", "kitchen");

        InMemoryEmbeddingStore<TextSegment> reloaded = InMemoryEmbeddingStore.fromFile(indexFile);
        List<EmbeddingMatch<TextSegment>> reloadedCoffee = search(reloaded, "coffee for my kitchen", affordableKitchen, 0.0);
        assertThat(skus(reloadedCoffee)).containsExactly("K-200", "K-300");
        assertThat(reloadedCoffee.get(0).embeddingId()).isEqualTo(affordableCoffee.get(0).embeddingId());
        assertThat(reloadedCoffee.get(0).score()).isEqualTo(affordableCoffee.get(0).score());
        assertThat(reloadedCoffee.get(0).embedded().metadata().getInteger("price")).isEqualTo(35);

        reloaded.removeAll(metadataKey("category").isEqualTo("living"));
        assertThat(search(reloaded, "a light for reading", null, 0.8)).isEmpty();
        assertThat(skus(search(reloaded, "a light for reading", null, 0.0)))
                .containsExactlyInAnyOrder("K-100", "K-200", "K-300");
        assertThat(skus(search(store, "a light for reading", null, 0.8))).containsExactly("L-100");
    }

    private static Document product(String sku, String category, int price, String description) {
        Metadata metadata = new Metadata()
                .put("sku", sku)
                .put("category", category)
                .put("price", price);
        return Document.from(description, metadata);
    }

    private List<EmbeddingMatch<TextSegment>> search(
            InMemoryEmbeddingStore<TextSegment> store, String query, Filter filter, double minScore) {
        Embedding queryEmbedding = embeddingModel.embed(query).content();
        EmbeddingSearchRequest request = EmbeddingSearchRequest.builder()
                .queryEmbedding(queryEmbedding)
                .maxResults(5)
                .minScore(minScore)
                .filter(filter)
                .build();
        return store.search(request).matches();
    }

    private static List<String> skus(List<EmbeddingMatch<TextSegment>> matches) {
        return matches.stream()
                .map(match -> match.embedded().metadata().getString("sku"))
                .toList();
    }

    private static String readString(Path file) {
        try {
            return Files.readString(file);
        } catch (IOException e) {
            throw new AssertionError("Cannot read " + file, e);
        }
    }

    private void answerEmbed(HttpExchange exchange) throws IOException {
        String request = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        embedRequests.add(request);

        Matcher input = INPUT_ARRAY.matcher(request);
        assertThat(input.find()).as("input of embed request %s", request).isTrue();
        String embeddings = JSON_STRING.matcher(input.group(1)).results()
                .map(text -> topicVector(text.group(1)))
                .collect(Collectors.joining(", ", "[", "]"));
        String response = """
                {"model": "nomic-embed-text", "embeddings": %s}
                """.formatted(embeddings);

        byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, bytes.length);
        try (OutputStream body = exchange.getResponseBody()) {
            body.write(bytes);
        }
    }

    /// Counts the topic words of a text on each axis, over a small floor so no vector is zero.
    private static String topicVector(String text) {
        List<String> words = Arrays.asList(text.toLowerCase(Locale.ROOT).split("\\W+"));
        return TOPICS.stream()
                .map(stems -> words.stream().filter(word -> stems.stream().anyMatch(word::startsWith)).count())
                .map(count -> String.valueOf(count + 0.1))
                .collect(Collectors.joining(", ", "[", "]"));
    }
}
