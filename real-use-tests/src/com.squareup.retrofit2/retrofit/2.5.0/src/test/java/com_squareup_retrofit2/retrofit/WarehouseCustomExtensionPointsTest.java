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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.ResponseBody;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import retrofit2.Call;
import retrofit2.CallAdapter;
import retrofit2.Callback;
import retrofit2.Converter;
import retrofit2.Invocation;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.Header;
import retrofit2.http.PUT;
import retrofit2.http.Path;
import retrofit2.http.Query;

/**
 * Application-defined extension points of `Retrofit.Builder`: a custom `CallAdapter.Factory`,
 * a custom `Converter.Factory` for bodies and string parameters, the `Invocation` request tag,
 * and a callback executor.
 *
 * A warehouse client talks to a stock service that speaks a line-based `sku=quantity` text
 * format. The application teaches Retrofit that format, renders its `Sku` value type into paths,
 * queries, and headers, wraps every call in its own `Outcome` result type, audits requests in an
 * OkHttp interceptor that reads which service method produced them, and delivers enqueued
 * callbacks on its own executor thread.
 */
public class WarehouseCustomExtensionPointsTest {

    private static final MediaType STOCK_TEXT = MediaType.get("text/x-stock; charset=utf-8");

    private MockWebServer server;
    private List<String> auditLog;
    private ExecutorService callbackThread;
    private Retrofit retrofit;
    private WarehouseApi api;

    @BeforeEach
    void startBackend() throws IOException {
        server = new MockWebServer();
        server.start();
        auditLog = Collections.synchronizedList(new ArrayList<>());
        callbackThread = Executors.newSingleThreadExecutor(r -> new Thread(r, "warehouse-callbacks"));
        OkHttpClient client = new OkHttpClient.Builder()
                .addInterceptor(chain -> {
                    Request request = chain.request();
                    Invocation invocation = request.tag(Invocation.class);
                    assertNotNull(invocation, "Retrofit tags every request with its Invocation");
                    auditLog.add(invocation.method().getName() + invocation.arguments());
                    return chain.proceed(request.newBuilder()
                            .header("X-Client-Operation", invocation.method().getName())
                            .build());
                })
                .build();
        retrofit = new Retrofit.Builder()
                .baseUrl(server.url("/stock/"))
                .client(client)
                .callbackExecutor(callbackThread)
                .addCallAdapterFactory(new OutcomeCallAdapterFactory())
                .addConverterFactory(new StockTextConverterFactory())
                .build();
        api = retrofit.create(WarehouseApi.class);
    }

    @AfterEach
    void stopBackend() throws IOException {
        callbackThread.shutdownNow();
        server.shutdown();
    }

    @Test
    void readsAnInventoryThroughTheCustomBodyConverterAndCallAdapter() throws Exception {
        server.enqueue(stock(200, "AB-100=12\nCD-7=0\nEF-33=250\n"));

        Outcome<Inventory> outcome = api.inventory("north", new Sku("AB", 0)).get();

        assertTrue(outcome.succeeded());
        assertEquals(200, outcome.status);
        Inventory inventory = outcome.value;
        assertNotNull(inventory);
        assertEquals(3, inventory.quantities.size());
        assertEquals(12, inventory.quantityOf(new Sku("AB", 100)));
        assertEquals(0, inventory.quantityOf(new Sku("CD", 7)));
        assertEquals(250, inventory.quantityOf(new Sku("EF", 33)));

        RecordedRequest request = server.takeRequest(5, TimeUnit.SECONDS);
        assertNotNull(request);
        assertEquals("GET", request.getMethod());
        assertEquals("/stock/sites/north/inventory?after=AB-0", request.getPath());
        assertEquals("inventory", request.getHeader("X-Client-Operation"));
        assertEquals(List.of("inventory[north, AB-0]"), auditLog);
    }

    @Test
    void writesSkusIntoPathsAndHeadersAndARestockBodyInTheStockFormat() throws Exception {
        server.enqueue(stock(200, "CD-7=40\n"));
        Inventory delivery = new Inventory();
        delivery.quantities.put(new Sku("CD", 7), 40);

        Outcome<Inventory> outcome = api.restock("north", new Sku("CD", 7), new Sku("ZZ", 1), delivery)
                .get();

        assertTrue(outcome.succeeded());
        assertEquals(40, outcome.value.quantityOf(new Sku("CD", 7)));

        RecordedRequest request = server.takeRequest(5, TimeUnit.SECONDS);
        assertNotNull(request);
        assertEquals("PUT", request.getMethod());
        assertEquals("/stock/sites/north/skus/CD-7", request.getPath());
        assertEquals("ZZ-1", request.getHeader("X-Replaces-Sku"));
        assertEquals("text/x-stock; charset=utf-8", request.getHeader("Content-Type"));
        assertEquals("CD-7=40\n", request.getBody().readUtf8());
        assertEquals("restock", request.getHeader("X-Client-Operation"));
    }

    @Test
    void reportsAnErrorStatusAndANetworkFailureThroughTheOutcomeType() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(409).setBody("site north is frozen"));

        Outcome<Inventory> rejected = api.inventory("north", new Sku("AB", 0)).get();

        assertFalse(rejected.succeeded());
        assertEquals(409, rejected.status);
        assertNull(rejected.value);
        assertEquals("site north is frozen", rejected.errorMessage);

        server.shutdown();
        Outcome<Inventory> unreachable = api.inventory("north", new Sku("AB", 0)).get();

        assertFalse(unreachable.succeeded());
        assertEquals(-1, unreachable.status);
        assertNotNull(unreachable.failure);
        assertTrue(unreachable.failure instanceof IOException);
    }

    @Test
    void deliversEnqueuedCallbacksOnTheConfiguredCallbackExecutor() throws Exception {
        server.enqueue(stock(200, "EF-33=249\n"));
        CountDownLatch delivered = new CountDownLatch(1);
        AtomicReference<String> deliveryThread = new AtomicReference<>();
        AtomicReference<Response<Inventory>> result = new AtomicReference<>();
        AtomicReference<Throwable> failure = new AtomicReference<>();

        api.reserve("south", new Sku("EF", 33), 1).enqueue(new Callback<Inventory>() {
            @Override
            public void onResponse(Call<Inventory> call, Response<Inventory> response) {
                deliveryThread.set(Thread.currentThread().getName());
                result.set(response);
                delivered.countDown();
            }

            @Override
            public void onFailure(Call<Inventory> call, Throwable t) {
                deliveryThread.set(Thread.currentThread().getName());
                failure.set(t);
                delivered.countDown();
            }
        });

        assertTrue(delivered.await(10, TimeUnit.SECONDS));
        assertNull(failure.get());
        assertEquals("warehouse-callbacks", deliveryThread.get());
        assertEquals(249, result.get().body().quantityOf(new Sku("EF", 33)));

        RecordedRequest request = server.takeRequest(5, TimeUnit.SECONDS);
        assertNotNull(request);
        assertEquals("/stock/sites/south/reservations?sku=EF-33&count=1", request.getPath());
        assertEquals(List.of("reserve[south, EF-33, 1]"), auditLog);
    }

    @Test
    void derivesAClientForAnotherSiteThatKeepsTheCustomFactories() throws Exception {
        MockWebServer otherSite = new MockWebServer();
        otherSite.start();
        try {
            otherSite.enqueue(stock(200, "GH-5=3\n"));
            WarehouseApi secondary = retrofit.newBuilder()
                    .baseUrl(otherSite.url("/mirror/"))
                    .build()
                    .create(WarehouseApi.class);

            Outcome<Inventory> outcome = secondary.inventory("east", new Sku("GH", 0)).get();

            assertTrue(outcome.succeeded());
            assertEquals(3, outcome.value.quantityOf(new Sku("GH", 5)));
            RecordedRequest request = otherSite.takeRequest(5, TimeUnit.SECONDS);
            assertNotNull(request);
            assertEquals("/mirror/sites/east/inventory?after=GH-0", request.getPath());
            assertEquals("inventory", request.getHeader("X-Client-Operation"));
            assertEquals(0, server.getRequestCount());
        } finally {
            otherSite.shutdown();
        }
    }

    @Test
    void rejectsAServiceWhoseReturnTypeNoFactoryCanAdaptWhenValidatingEagerly() {
        Retrofit strict = retrofit.newBuilder().validateEagerly(true).build();

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class, () -> strict.create(MisdeclaredApi.class));

        assertTrue(error.getMessage().startsWith("Unable to create call adapter for"),
                error.getMessage());
        assertTrue(error.getMessage().contains("MisdeclaredApi.history"), error.getMessage());
        assertEquals(0, server.getRequestCount());
    }

    private static MockResponse stock(int code, String body) {
        return new MockResponse()
                .setResponseCode(code)
                .addHeader("Content-Type", STOCK_TEXT.toString())
                .setBody(body);
    }

    /** The stock service as the warehouse application declares it. */
    interface WarehouseApi {

        @GET("sites/{site}/inventory")
        Outcome.Pending<Inventory> inventory(@Path("site") String site, @Query("after") Sku after);

        @PUT("sites/{site}/skus/{sku}")
        Outcome.Pending<Inventory> restock(
                @Path("site") String site,
                @Path("sku") Sku sku,
                @Header("X-Replaces-Sku") Sku replaces,
                @Body Inventory delivery);

        @GET("sites/{site}/reservations")
        Call<Inventory> reserve(
                @Path("site") String site, @Query("sku") Sku sku, @Query("count") int count);
    }

    /** A service whose `history` method returns a type no call adapter accepts. */
    interface MisdeclaredApi {

        @GET("history")
        List<Inventory> history();
    }

    /** A stock-keeping unit rendered as `PREFIX-number`. */
    static final class Sku {
        final String prefix;
        final int number;

        Sku(String prefix, int number) {
            this.prefix = prefix;
            this.number = number;
        }

        static Sku parse(String text) {
            int dash = text.indexOf('-');
            return new Sku(text.substring(0, dash), Integer.parseInt(text.substring(dash + 1)));
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof Sku
                    && ((Sku) other).prefix.equals(prefix)
                    && ((Sku) other).number == number;
        }

        @Override
        public int hashCode() {
            return prefix.hashCode() * 31 + number;
        }

        @Override
        public String toString() {
            return prefix + "-" + number;
        }
    }

    /** Quantities per SKU, exchanged as `sku=quantity` lines. */
    static final class Inventory {
        final Map<Sku, Integer> quantities = new LinkedHashMap<>();

        int quantityOf(Sku sku) {
            Integer quantity = quantities.get(sku);
            assertNotNull(quantity, "no quantity for " + sku);
            return quantity;
        }
    }

    /** The application's result type: a value with its status, or the reason there is none. */
    static final class Outcome<T> {
        final int status;
        final T value;
        final String errorMessage;
        final Throwable failure;

        private Outcome(int status, T value, String errorMessage, Throwable failure) {
            this.status = status;
            this.value = value;
            this.errorMessage = errorMessage;
            this.failure = failure;
        }

        boolean succeeded() {
            return failure == null && status >= 200 && status < 300;
        }

        /** A request not yet run; `get` runs it and never throws. */
        interface Pending<T> {
            Outcome<T> get();
        }
    }

    /** Adapts `Outcome.Pending<T>` service methods onto Retrofit's `Call<T>`. */
    static final class OutcomeCallAdapterFactory extends CallAdapter.Factory {

        @Override
        public CallAdapter<?, ?> get(Type returnType, Annotation[] annotations, Retrofit retrofit) {
            if (getRawType(returnType) != Outcome.Pending.class) {
                return null;
            }
            Type valueType = getParameterUpperBound(0, (ParameterizedType) returnType);
            return new CallAdapter<Object, Outcome.Pending<Object>>() {
                @Override
                public Type responseType() {
                    return valueType;
                }

                @Override
                public Outcome.Pending<Object> adapt(Call<Object> call) {
                    return () -> {
                        try {
                            Response<Object> response = call.execute();
                            if (response.isSuccessful()) {
                                return new Outcome<>(response.code(), response.body(), null, null);
                            }
                            String message = response.errorBody().string();
                            return new Outcome<>(response.code(), null, message, null);
                        } catch (IOException e) {
                            return new Outcome<>(-1, null, null, e);
                        }
                    };
                }
            };
        }
    }

    /** Teaches Retrofit the `sku=quantity` text format and how to render a `Sku` parameter. */
    static final class StockTextConverterFactory extends Converter.Factory {

        @Override
        public Converter<ResponseBody, ?> responseBodyConverter(
                Type type, Annotation[] annotations, Retrofit retrofit) {
            if (type != Inventory.class) {
                return null;
            }
            return (ResponseBody body) -> {
                Inventory inventory = new Inventory();
                for (String line : body.string().split("\n")) {
                    if (!line.isEmpty()) {
                        int equals = line.indexOf('=');
                        inventory.quantities.put(
                                Sku.parse(line.substring(0, equals)),
                                Integer.parseInt(line.substring(equals + 1)));
                    }
                }
                return inventory;
            };
        }

        @Override
        public Converter<?, RequestBody> requestBodyConverter(
                Type type,
                Annotation[] parameterAnnotations,
                Annotation[] methodAnnotations,
                Retrofit retrofit) {
            if (type != Inventory.class) {
                return null;
            }
            return (Inventory inventory) -> {
                StringBuilder text = new StringBuilder();
                inventory.quantities.forEach(
                        (sku, quantity) -> text.append(sku).append('=').append(quantity).append('\n'));
                return RequestBody.create(STOCK_TEXT, text.toString());
            };
        }

        @Override
        public Converter<?, String> stringConverter(
                Type type, Annotation[] annotations, Retrofit retrofit) {
            if (type != Sku.class) {
                return null;
            }
            return (Sku sku) -> sku.toString();
        }
    }
}
