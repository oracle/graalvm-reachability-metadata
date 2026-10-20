/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_squareup_retrofit2.retrofit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategy;
import com.fasterxml.jackson.databind.exc.UnrecognizedPropertyException;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.lang.annotation.Annotation;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import okhttp3.ResponseBody;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import retrofit2.Call;
import retrofit2.Converter;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.jackson.JacksonConverterFactory;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.PATCH;
import retrofit2.http.POST;
import retrofit2.http.Path;
import retrofit2.http.Query;

/**
 * Jackson JSON conversion through `JacksonConverterFactory` backed by an application-configured
 * `ObjectMapper`.
 *
 * A library-catalog client maps snake_case wire names onto camelCase POJOs, builds immutable
 * books through a `@JsonCreator` constructor, reads a polymorphic loan history keyed by a type
 * property, omits null fields on writes, sends and reads `JsonNode` trees, converts an error
 * body, and fails on unknown properties when the mapper is strict. Every exchange runs against a
 * `MockWebServer`.
 */
public class LibraryCatalogJacksonConverterTest {

    private MockWebServer server;
    private Retrofit retrofit;
    private CatalogApi api;

    @BeforeEach
    void startBackend() throws IOException {
        server = new MockWebServer();
        server.start();
        ObjectMapper mapper = new ObjectMapper()
                .setPropertyNamingStrategy(PropertyNamingStrategy.SNAKE_CASE)
                .setSerializationInclusion(JsonInclude.Include.NON_NULL)
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        retrofit = new Retrofit.Builder()
                .baseUrl(server.url("/catalog/"))
                .addConverterFactory(JacksonConverterFactory.create(mapper))
                .build();
        api = retrofit.create(CatalogApi.class);
    }

    @AfterEach
    void stopBackend() throws IOException {
        server.shutdown();
    }

    @Test
    void readsSnakeCaseBooksIntoImmutableCreatorBuiltObjects() throws Exception {
        server.enqueue(json("""
                [
                  {"isbn": "978-0134685991", "title": "Effective Java", "page_count": 412,
                   "author_names": ["Joshua Bloch"], "shelf_code": "PRG-12", "rating": 4.8},
                  {"isbn": "978-0596009205", "title": "Head First Java", "page_count": 688,
                   "author_names": ["Kathy Sierra", "Bert Bates"], "shelf_code": "PRG-03"}
                ]
                """));

        Response<List<Book>> response = api.search("java", 2).execute();

        assertTrue(response.isSuccessful());
        List<Book> books = response.body();
        assertNotNull(books);
        assertEquals(2, books.size());
        Book first = books.get(0);
        assertEquals("Effective Java", first.getTitle());
        assertEquals(412, first.getPageCount());
        assertEquals(List.of("Joshua Bloch"), first.getAuthorNames());
        assertEquals("PRG-12", first.getShelfCode());
        assertEquals(List.of("Kathy Sierra", "Bert Bates"), books.get(1).getAuthorNames());

        RecordedRequest request = server.takeRequest(5, TimeUnit.SECONDS);
        assertNotNull(request);
        assertEquals("GET", request.getMethod());
        assertEquals("/catalog/books?q=java&limit=2", request.getPath());
    }

    @Test
    void writesSnakeCaseRequestBodiesAndOmitsNullFields() throws Exception {
        server.enqueue(json("""
                {"isbn": "978-1617294945", "title": "Spring in Action", "page_count": 520,
                 "author_names": ["Craig Walls"], "shelf_code": "PRG-40"}
                """).setResponseCode(201));

        Book draft = new Book("978-1617294945", "Spring in Action", 520, List.of("Craig Walls"), null);
        Response<Book> response = api.addBook(draft).execute();

        assertEquals(201, response.code());
        Book stored = response.body();
        assertNotNull(stored);
        assertEquals("PRG-40", stored.getShelfCode());

        RecordedRequest request = server.takeRequest(5, TimeUnit.SECONDS);
        assertNotNull(request);
        assertEquals("application/json; charset=UTF-8", request.getHeader("Content-Type"));
        JsonNode sent = new ObjectMapper().readTree(request.getBody().readUtf8());
        assertEquals("Spring in Action", sent.get("title").asText());
        assertEquals(520, sent.get("page_count").asInt());
        assertEquals("Craig Walls", sent.get("author_names").get(0).asText());
        assertFalse(sent.has("shelf_code"), "null shelf code must be omitted");
        assertFalse(sent.has("pageCount"), "camelCase names must not reach the wire");
    }

    @Test
    void readsPolymorphicLoanHistoryByTypeProperty() throws Exception {
        server.enqueue(json("""
                {"member_id": "M-7", "events": [
                  {"kind": "checkout", "isbn": "978-0134685991", "due_in_days": 21},
                  {"kind": "renewal", "isbn": "978-0134685991", "extra_days": 14},
                  {"kind": "return", "isbn": "978-0134685991", "late": false}
                ]}
                """));

        LoanHistory history = api.loans("M-7").execute().body();

        assertNotNull(history);
        assertEquals("M-7", history.memberId);
        assertEquals(3, history.events.size());
        Checkout checkout = assertInstanceOf(Checkout.class, history.events.get(0));
        assertEquals(21, checkout.dueInDays);
        Renewal renewal = assertInstanceOf(Renewal.class, history.events.get(1));
        assertEquals(14, renewal.extraDays);
        Return returned = assertInstanceOf(Return.class, history.events.get(2));
        assertFalse(returned.late);
        int totalDays = checkout.dueInDays + renewal.extraDays;
        assertEquals(35, totalDays);

        RecordedRequest request = server.takeRequest(5, TimeUnit.SECONDS);
        assertNotNull(request);
        assertEquals("/catalog/members/M-7/loans", request.getPath());
    }

    @Test
    void exchangesJsonNodeTreesForSchemalessPatches() throws Exception {
        server.enqueue(json("""
                {"isbn": "978-0134685991", "changed": {"shelf_code": "PRG-99"}, "revision": 4}
                """));

        ObjectNode patch = new ObjectMapper().createObjectNode();
        patch.put("shelf_code", "PRG-99");
        patch.putArray("tags").add("classic").add("bestseller");
        JsonNode result = api.patchBook("978-0134685991", patch).execute().body();

        assertNotNull(result);
        assertEquals(4, result.get("revision").asInt());
        assertEquals("PRG-99", result.path("changed").path("shelf_code").asText());

        RecordedRequest request = server.takeRequest(5, TimeUnit.SECONDS);
        assertNotNull(request);
        assertEquals("PATCH", request.getMethod());
        JsonNode sent = new ObjectMapper().readTree(request.getBody().readUtf8());
        assertEquals("PRG-99", sent.get("shelf_code").asText());
        assertEquals("bestseller", sent.get("tags").get(1).asText());
    }

    @Test
    void convertsTheErrorBodyOfAFailedCall() throws Exception {
        server.enqueue(json("""
                {"error_code": "BOOK_NOT_FOUND", "message": "No book with ISBN 000",
                 "details": {"isbn": "000"}}
                """).setResponseCode(404));

        Response<Book> response = api.book("000").execute();

        assertFalse(response.isSuccessful());
        assertEquals(404, response.code());
        ResponseBody errorBody = response.errorBody();
        assertNotNull(errorBody);
        Converter<ResponseBody, ApiError> converter =
                retrofit.responseBodyConverter(ApiError.class, new Annotation[0]);
        ApiError error = converter.convert(errorBody);
        assertNotNull(error);
        assertEquals("BOOK_NOT_FOUND", error.errorCode);
        assertEquals("No book with ISBN 000", error.message);
        assertEquals(Map.of("isbn", "000"), error.details);
    }

    @Test
    void strictMapperRejectsUnknownPropertiesThroughTheCall() throws Exception {
        ObjectMapper strict = new ObjectMapper()
                .setPropertyNamingStrategy(PropertyNamingStrategy.SNAKE_CASE)
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, true);
        CatalogApi strictApi = new Retrofit.Builder()
                .baseUrl(retrofit.baseUrl())
                .addConverterFactory(JacksonConverterFactory.create(strict))
                .build()
                .create(CatalogApi.class);
        String withExtraField = """
                {"isbn": "978-0134685991", "title": "Effective Java", "page_count": 412,
                 "author_names": [], "binding": "hardcover"}
                """;
        server.enqueue(json(withExtraField));
        server.enqueue(json(withExtraField));

        Book lenient = api.book("978-0134685991").execute().body();
        assertNotNull(lenient);
        assertEquals("Effective Java", lenient.getTitle());

        Call<Book> strictCall = strictApi.book("978-0134685991");
        UnrecognizedPropertyException failure =
                assertThrows(UnrecognizedPropertyException.class, strictCall::execute);
        assertEquals("binding", failure.getPropertyName());
        assertTrue(strictCall.isExecuted());
        assertEquals(2, server.getRequestCount());
    }

    private static MockResponse json(String body) {
        return new MockResponse().setHeader("Content-Type", "application/json").setBody(body);
    }

    /** Catalog endpoints whose bodies are all converted by Jackson. */
    interface CatalogApi {

        @GET("books")
        Call<List<Book>> search(@Query("q") String query, @Query("limit") int limit);

        @GET("books/{isbn}")
        Call<Book> book(@Path("isbn") String isbn);

        @POST("books")
        Call<Book> addBook(@Body Book book);

        @PATCH("books/{isbn}")
        Call<JsonNode> patchBook(@Path("isbn") String isbn, @Body JsonNode patch);

        @GET("members/{memberId}/loans")
        Call<LoanHistory> loans(@Path("memberId") String memberId);
    }

    /** Immutable book built by Jackson through its annotated constructor. */
    public static final class Book {

        private final String isbn;
        private final String title;
        private final int pageCount;
        private final List<String> authorNames;
        private final String shelfCode;

        @JsonCreator
        public Book(
                @JsonProperty("isbn") String isbn,
                @JsonProperty("title") String title,
                @JsonProperty("page_count") int pageCount,
                @JsonProperty("author_names") List<String> authorNames,
                @JsonProperty("shelf_code") String shelfCode) {
            this.isbn = isbn;
            this.title = title;
            this.pageCount = pageCount;
            this.authorNames = authorNames;
            this.shelfCode = shelfCode;
        }

        public String getIsbn() {
            return isbn;
        }

        public String getTitle() {
            return title;
        }

        public int getPageCount() {
            return pageCount;
        }

        public List<String> getAuthorNames() {
            return authorNames;
        }

        public String getShelfCode() {
            return shelfCode;
        }
    }

    /** A member's loan events, each resolved to its subtype by the `kind` property. */
    public static final class LoanHistory {
        public String memberId;
        public List<LoanEvent> events;
    }

    /** Base type of the polymorphic loan events. */
    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "kind")
    @JsonSubTypes({
        @JsonSubTypes.Type(value = Checkout.class, name = "checkout"),
        @JsonSubTypes.Type(value = Renewal.class, name = "renewal"),
        @JsonSubTypes.Type(value = Return.class, name = "return")
    })
    public abstract static class LoanEvent {
        public String isbn;
    }

    /** A book leaving the library. */
    public static final class Checkout extends LoanEvent {
        public int dueInDays;
    }

    /** An extension of an open loan. */
    public static final class Renewal extends LoanEvent {
        public int extraDays;
    }

    /** A book coming back. */
    public static final class Return extends LoanEvent {
        public boolean late;
    }

    /** Error payload the catalog sends with non-2xx responses. */
    public static final class ApiError {
        public String errorCode;
        public String message;
        public Map<String, String> details;
    }
}
