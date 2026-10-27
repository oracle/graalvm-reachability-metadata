/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_squareup_retrofit2.retrofit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import com.google.gson.TypeAdapter;
import com.google.gson.annotations.JsonAdapter;
import com.google.gson.annotations.SerializedName;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;
import com.google.gson.stream.MalformedJsonException;
import java.io.IOException;
import java.lang.annotation.Annotation;
import java.math.BigDecimal;
import java.time.LocalDate;
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
import retrofit2.converter.gson.GsonConverterFactory;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.POST;
import retrofit2.http.PUT;
import retrofit2.http.Path;
import retrofit2.http.Query;

/**
 * Gson JSON conversion through `GsonConverterFactory.create(gson)` backed by an
 * application-configured `Gson` instance.
 *
 * An expense-ledger client maps lower_case_with_underscores wire names onto camelCase fields,
 * reads dates through a registered `TypeAdapter` and money through a field-level `@JsonAdapter`,
 * accepts `@SerializedName` alternates and enum names, resolves generic wrapper and map return
 * types from the service method signatures, writes explicit nulls, converts an error body, and
 * rejects malformed and trailing JSON. Every exchange runs against a `MockWebServer`.
 */
public class ExpenseLedgerGsonConverterTest {

    private MockWebServer server;
    private Retrofit retrofit;
    private LedgerApi api;

    @BeforeEach
    void startBackend() throws IOException {
        server = new MockWebServer();
        server.start();
        Gson gson = new GsonBuilder()
                .setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
                .registerTypeAdapter(LocalDate.class, new IsoDateAdapter().nullSafe())
                .serializeNulls()
                .create();
        retrofit = new Retrofit.Builder()
                .baseUrl(server.url("/ledger/"))
                .addConverterFactory(GsonConverterFactory.create(gson))
                .build();
        api = retrofit.create(LedgerApi.class);
    }

    @AfterEach
    void stopBackend() throws IOException {
        server.shutdown();
    }

    @Test
    void readsAGenericPageOfExpensesWithCustomAdapters() throws Exception {
        server.enqueue(json(200, """
                {"page_number": 2, "total_pages": 5, "items": [
                  {"expense_id": "E-100", "merchant_name": "Rail Co", "amount_cents": 4250,
                   "category": "travel", "spent_on": "2026-03-14", "receipt_attached": true},
                  {"expense_id": "E-101", "vendor": "Cafe Uno", "amount_cents": 1199,
                   "category": "meals", "spent_on": "2026-03-15", "receipt_attached": false}
                ]}
                """));

        Response<Page<Expense>> response = api.expenses("2026-03", 2).execute();

        assertTrue(response.isSuccessful());
        Page<Expense> page = response.body();
        assertNotNull(page);
        assertEquals(2, page.pageNumber);
        assertEquals(5, page.totalPages);
        assertEquals(2, page.items.size());

        Expense train = page.items.get(0);
        assertEquals("E-100", train.expenseId);
        assertEquals("Rail Co", train.merchantName);
        assertEquals(new BigDecimal("42.50"), train.amount);
        assertEquals(Category.TRAVEL, train.category);
        assertEquals(LocalDate.of(2026, 3, 14), train.spentOn);
        assertTrue(train.receiptAttached);

        Expense coffee = page.items.get(1);
        assertEquals("Cafe Uno", coffee.merchantName, "the vendor alternate must map to merchantName");
        assertEquals(new BigDecimal("11.99"), coffee.amount);
        assertEquals(Category.MEALS, coffee.category);
        assertFalse(coffee.receiptAttached);

        RecordedRequest request = server.takeRequest(5, TimeUnit.SECONDS);
        assertNotNull(request);
        assertEquals("/ledger/expenses?month=2026-03&page=2", request.getPath());
    }

    @Test
    void readsAMapOfListsKeyedByCategoryName() throws Exception {
        server.enqueue(json(200, """
                {"travel": [{"expense_id": "E-1", "merchant_name": "Air One", "amount_cents": 30000,
                             "category": "travel", "spent_on": "2026-04-01"}],
                 "lodging": [{"expense_id": "E-2", "merchant_name": "Inn", "amount_cents": 12000,
                              "category": "lodging", "spent_on": "2026-04-02"},
                             {"expense_id": "E-3", "merchant_name": "Inn", "amount_cents": 12000,
                              "category": "lodging", "spent_on": "2026-04-03"}]}
                """));

        Map<String, List<Expense>> byCategory = api.groupedByCategory("R-9").execute().body();

        assertNotNull(byCategory);
        assertEquals(List.of("travel", "lodging"), List.copyOf(byCategory.keySet()));
        assertEquals(1, byCategory.get("travel").size());
        assertEquals(new BigDecimal("300.00"), byCategory.get("travel").get(0).amount);
        assertEquals(2, byCategory.get("lodging").size());
        assertEquals(LocalDate.of(2026, 4, 3), byCategory.get("lodging").get(1).spentOn);
        assertEquals(Category.LODGING, byCategory.get("lodging").get(1).category);
    }

    @Test
    void writesUnderscoreNamesExplicitNullsAndAdapterFormattedValues() throws Exception {
        server.enqueue(json(201, """
                {"expense_id": "E-200", "merchant_name": "Book Shop", "amount_cents": 2599,
                 "category": "office", "spent_on": "2026-05-09", "receipt_attached": false}
                """));

        Expense draft = new Expense();
        draft.merchantName = "Book Shop";
        draft.amount = new BigDecimal("25.99");
        draft.category = Category.OFFICE;
        draft.spentOn = LocalDate.of(2026, 5, 9);

        Response<Expense> response = api.submit("R-9", draft).execute();

        assertEquals(201, response.code());
        assertEquals("E-200", response.body().expenseId);

        RecordedRequest request = server.takeRequest(5, TimeUnit.SECONDS);
        assertNotNull(request);
        assertEquals("POST", request.getMethod());
        assertEquals("/ledger/reports/R-9/expenses", request.getPath());
        assertEquals("application/json; charset=UTF-8", request.getHeader("Content-Type"));
        JsonObject sent = new JsonParser().parse(request.getBody().readUtf8()).getAsJsonObject();
        assertEquals("Book Shop", sent.get("merchant_name").getAsString());
        assertEquals(2599L, sent.get("amount_cents").getAsLong());
        assertEquals("office", sent.get("category").getAsString());
        assertEquals("2026-05-09", sent.get("spent_on").getAsString());
        assertTrue(sent.has("expense_id"), "serializeNulls must keep the unset id");
        assertTrue(sent.get("expense_id").isJsonNull());
        assertFalse(sent.has("merchantName"), "camelCase names must not reach the wire");
    }

    @Test
    void roundTripsANestedReportThroughPut() throws Exception {
        server.enqueue(json(200, """
                {"report_id": "R-12", "owner_email": "ana@example.com", "status": "submitted",
                 "period_start": "2026-06-01", "period_end": "2026-06-30",
                 "expenses": [{"expense_id": "E-9", "merchant_name": "Taxi", "amount_cents": 1850,
                               "category": "travel", "spent_on": "2026-06-11"}]}
                """));

        Report report = new Report();
        report.reportId = "R-12";
        report.ownerEmail = "ana@example.com";
        report.status = Status.SUBMITTED;
        report.periodStart = LocalDate.of(2026, 6, 1);
        report.periodEnd = LocalDate.of(2026, 6, 30);

        Report stored = api.replace("R-12", report).execute().body();

        assertNotNull(stored);
        assertEquals(Status.SUBMITTED, stored.status);
        assertEquals(LocalDate.of(2026, 6, 30), stored.periodEnd);
        assertEquals(1, stored.expenses.size());
        assertEquals(new BigDecimal("18.50"), stored.expenses.get(0).amount);

        RecordedRequest request = server.takeRequest(5, TimeUnit.SECONDS);
        assertNotNull(request);
        assertEquals("PUT", request.getMethod());
        JsonObject sent = new JsonParser().parse(request.getBody().readUtf8()).getAsJsonObject();
        assertEquals("submitted", sent.get("status").getAsString());
        assertEquals("2026-06-01", sent.get("period_start").getAsString());
        assertTrue(sent.get("expenses").isJsonNull(), "an unset list is written as null");
    }

    @Test
    void convertsAnErrorBodyWithTheSameGsonConfiguration() throws Exception {
        server.enqueue(json(422, """
                {"error_code": "AMOUNT_TOO_HIGH", "detail_message": "Limit is 500.00",
                 "field_errors": {"amount_cents": "exceeds limit"}}
                """));

        Expense draft = new Expense();
        draft.merchantName = "Jeweller";
        draft.amount = new BigDecimal("9999.00");
        draft.category = Category.OFFICE;
        draft.spentOn = LocalDate.of(2026, 7, 1);
        Response<Expense> response = api.submit("R-9", draft).execute();

        assertFalse(response.isSuccessful());
        assertEquals(422, response.code());
        Converter<ResponseBody, ApiError> errorConverter =
                retrofit.responseBodyConverter(ApiError.class, new Annotation[0]);
        ApiError error;
        try (ResponseBody errorBody = response.errorBody()) {
            assertNotNull(errorBody);
            error = errorConverter.convert(errorBody);
        }
        assertNotNull(error);
        assertEquals("AMOUNT_TOO_HIGH", error.errorCode);
        assertEquals("Limit is 500.00", error.detailMessage);
        assertEquals(Map.of("amount_cents", "exceeds limit"), error.fieldErrors);
    }

    @Test
    void failsOnMalformedAndOnTrailingJson() {
        server.enqueue(json(200, "{\"page_number\": \"two\", \"items\": []}"));
        Call<Page<Expense>> malformed = api.expenses("2026-08", 1);
        assertThrows(JsonSyntaxException.class, malformed::execute);
        assertTrue(malformed.isExecuted());

        server.enqueue(json(200, "{\"report_id\": \"R-1\", \"status\": \"draft\"} {\"report_id\": \"R-2\"}"));
        MalformedJsonException trailing = assertThrows(MalformedJsonException.class,
                () -> api.replace("R-1", new Report()).execute());
        assertTrue(trailing.getMessage().contains("setLenient"), trailing.getMessage());
    }

    private static MockResponse json(int code, String body) {
        return new MockResponse()
                .setResponseCode(code)
                .setHeader("Content-Type", "application/json")
                .setBody(body);
    }

    interface LedgerApi {

        @GET("expenses")
        Call<Page<Expense>> expenses(@Query("month") String month, @Query("page") int page);

        @GET("reports/{reportId}/by-category")
        Call<Map<String, List<Expense>>> groupedByCategory(@Path("reportId") String reportId);

        @POST("reports/{reportId}/expenses")
        Call<Expense> submit(@Path("reportId") String reportId, @Body Expense expense);

        @PUT("reports/{reportId}")
        Call<Report> replace(@Path("reportId") String reportId, @Body Report report);
    }

    /// A generic page wrapper whose element type Retrofit resolves from the method signature.
    static final class Page<T> {
        int pageNumber;
        int totalPages;
        List<T> items;
    }

    static final class Expense {
        String expenseId;
        @SerializedName(value = "merchant_name", alternate = {"vendor"})
        String merchantName;
        @SerializedName("amount_cents")
        @JsonAdapter(CentsAdapter.class)
        BigDecimal amount;
        Category category;
        LocalDate spentOn;
        boolean receiptAttached;
    }

    static final class Report {
        String reportId;
        String ownerEmail;
        Status status;
        LocalDate periodStart;
        LocalDate periodEnd;
        List<Expense> expenses;
    }

    static final class ApiError {
        String errorCode;
        String detailMessage;
        Map<String, String> fieldErrors;
    }

    enum Category {
        @SerializedName("travel") TRAVEL,
        @SerializedName("meals") MEALS,
        @SerializedName("lodging") LODGING,
        @SerializedName("office") OFFICE
    }

    enum Status {
        @SerializedName("draft") DRAFT,
        @SerializedName("submitted") SUBMITTED,
        @SerializedName("approved") APPROVED
    }

    /// Reads and writes `LocalDate` values as ISO-8601 strings.
    static final class IsoDateAdapter extends TypeAdapter<LocalDate> {

        @Override
        public void write(JsonWriter out, LocalDate value) throws IOException {
            out.value(value.toString());
        }

        @Override
        public LocalDate read(JsonReader in) throws IOException {
            return LocalDate.parse(in.nextString());
        }
    }

    /// Carries money as integer cents on the wire and as a two-decimal `BigDecimal` in the app.
    static final class CentsAdapter extends TypeAdapter<BigDecimal> {

        @Override
        public void write(JsonWriter out, BigDecimal value) throws IOException {
            if (value == null) {
                out.nullValue();
                return;
            }
            out.value(value.movePointRight(2).longValueExact());
        }

        @Override
        public BigDecimal read(JsonReader in) throws IOException {
            if (in.peek() == JsonToken.NULL) {
                in.nextNull();
                return null;
            }
            return BigDecimal.valueOf(in.nextLong(), 2);
        }
    }
}
