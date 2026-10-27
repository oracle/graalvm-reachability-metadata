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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import okhttp3.Call;
import okhttp3.OkHttpClient;
import okhttp3.RequestBody;
import okhttp3.ResponseBody;
import okhttp3.mockwebserver.Dispatcher;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import okhttp3.mockwebserver.SocketPolicy;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import retrofit2.Converter;
import retrofit2.HttpException;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;
import retrofit2.http.GET;
import retrofit2.http.Path;
import retrofit2.http.Query;

/**
 * Retrofit's built-in Java 8 platform support: service methods returning `CompletableFuture` of a
 * body or of a `Response`, and `Optional` response bodies unwrapped by the platform converter.
 *
 * A parcel-tracking client asks a carrier backend for parcels, courier ETAs, and barcode lookups.
 * The client composes the futures with `thenCombine` and `allOf`, maps HTTP and network failures
 * to exceptional completion, cancels a pending lookup, and treats a JSON `null` lookup result as
 * an absent parcel. The backend is a `MockWebServer` whose dispatcher answers by path, so
 * concurrent requests get deterministic responses.
 */
public class ParcelTrackerJava8PlatformTest {

    private MockWebServer server;
    private CountDownLatch slowLookupArrived;
    private CountDownLatch releaseSlowLookup;
    private AtomicReference<Call> lastHttpCall;
    private List<String> requestedPaths;
    private ParcelApi api;

    @BeforeEach
    void startCarrierBackend() throws IOException {
        slowLookupArrived = new CountDownLatch(1);
        releaseSlowLookup = new CountDownLatch(1);
        lastHttpCall = new AtomicReference<>();
        requestedPaths = new CopyOnWriteArrayList<>();
        server = new MockWebServer();
        server.setDispatcher(new CarrierBackend());
        server.start();

        OkHttpClient httpClient = new OkHttpClient.Builder()
                .addInterceptor(chain -> {
                    lastHttpCall.set(chain.call());
                    return chain.proceed(chain.request());
                })
                .build();
        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl(server.url("/carrier/"))
                .client(httpClient)
                .addConverterFactory(
                        new JsonExceptOptionalConverterFactory(GsonConverterFactory.create()))
                .build();
        api = retrofit.create(ParcelApi.class);
    }

    @AfterEach
    void stopCarrierBackend() throws IOException {
        releaseSlowLookup.countDown();
        server.shutdown();
    }

    @Test
    void completesTheFutureWithTheConvertedParcel() throws Exception {
        CompletableFuture<Parcel> future = api.parcel("PX-1001");

        Parcel parcel = future.get(10, TimeUnit.SECONDS);

        assertEquals("PX-1001", parcel.id);
        assertEquals("IN_TRANSIT", parcel.status);
        assertEquals("Rotterdam hub", parcel.location);
        assertEquals(2.4, parcel.weightKg);
        assertTrue(future.isDone());
        assertFalse(future.isCompletedExceptionally());
        assertTrue(requestedPaths.contains("/carrier/parcels/PX-1001"));
    }

    @Test
    void combinesParcelAndCourierFuturesIntoATrackingSummary() throws Exception {
        CompletableFuture<String> summary = api.parcel("PX-1001")
                .thenCombine(api.eta("PX-1001", "minutes"),
                        (parcel, eta) -> parcel.id + " at " + parcel.location + ", courier "
                                + eta.courier + " in " + eta.minutes + " min");

        assertEquals("PX-1001 at Rotterdam hub, courier Ana in 35 min",
                summary.get(10, TimeUnit.SECONDS));
        assertTrue(requestedPaths.contains("/carrier/parcels/PX-1001/eta?unit=minutes"));
    }

    @Test
    void waitsForAllParcelsOfAShipmentWithAllOf() throws Exception {
        CompletableFuture<Parcel> first = api.parcel("PX-1001");
        CompletableFuture<Parcel> second = api.parcel("PX-1002");

        CompletableFuture.allOf(first, second).get(10, TimeUnit.SECONDS);

        assertEquals("IN_TRANSIT", first.join().status);
        assertEquals("DELIVERED", second.join().status);
        assertEquals(2, requestedPaths.size());
    }

    @Test
    void failsTheBodyFutureWithHttpExceptionForAnErrorStatus() throws Exception {
        CompletableFuture<Parcel> future = api.parcel("PX-0404");

        ExecutionException failure = assertThrows(ExecutionException.class,
                () -> future.get(10, TimeUnit.SECONDS));

        HttpException httpError = assertInstanceOf(HttpException.class, failure.getCause());
        assertEquals(404, httpError.code());
        assertEquals("HTTP 404 Client Error", httpError.getMessage());
        assertEquals("{\"error\":\"unknown parcel PX-0404\"}",
                httpError.response().errorBody().string());
        assertTrue(future.isCompletedExceptionally());

        String shown = future
                .thenApply(parcel -> parcel.status)
                .exceptionally(error -> error.getCause() instanceof HttpException
                        ? "not tracked yet"
                        : "carrier unreachable")
                .get(10, TimeUnit.SECONDS);
        assertEquals("not tracked yet", shown);
    }

    @Test
    void completesTheResponseFutureForSuccessAndErrorStatuses() throws Exception {
        Response<Parcel> found = api.parcelResponse("PX-1002").get(10, TimeUnit.SECONDS);
        Response<Parcel> missing = api.parcelResponse("PX-0404").get(10, TimeUnit.SECONDS);

        assertTrue(found.isSuccessful());
        assertEquals("DELIVERED", found.body().status);
        assertEquals("v3", found.headers().get("X-Carrier-Api"));
        assertFalse(missing.isSuccessful());
        assertEquals(404, missing.code());
        assertNull(missing.body());
        assertEquals("{\"error\":\"unknown parcel PX-0404\"}", missing.errorBody().string());
    }

    @Test
    void failsTheFutureWithTheNetworkErrorWhenTheBackendDropsTheConnection() throws Exception {
        CompletableFuture<Parcel> future = api.parcel("PX-DROP");

        ExecutionException failure = assertThrows(ExecutionException.class,
                () -> future.get(10, TimeUnit.SECONDS));

        assertInstanceOf(IOException.class, failure.getCause());
        assertFalse(failure.getCause() instanceof HttpException);
    }

    @Test
    void cancellingTheFutureCancelsTheHttpCall() throws Exception {
        CompletableFuture<Optional<Parcel>> future = api.lookup("SLOW-77");
        assertTrue(slowLookupArrived.await(10, TimeUnit.SECONDS));

        assertTrue(future.cancel(true));

        assertTrue(future.isCancelled());
        Call httpCall = lastHttpCall.get();
        assertNotNull(httpCall);
        assertTrue(httpCall.isCanceled());
    }

    @Test
    void unwrapsALookupResultIntoAPresentOrAbsentOptional() throws Exception {
        CompletableFuture<Optional<Parcel>> known = api.lookup("8712345678906");
        CompletableFuture<Optional<Parcel>> unknown = api.lookup("0000000000000");

        Optional<Parcel> knownParcel = known.get(10, TimeUnit.SECONDS);
        Optional<Parcel> unknownParcel = unknown.get(10, TimeUnit.SECONDS);

        assertTrue(knownParcel.isPresent());
        assertEquals("PX-1002", knownParcel.get().id);
        assertEquals("delivered to locker 12",
                knownParcel.map(parcel -> "delivered to " + parcel.location).orElse("unknown"));
        assertFalse(unknownParcel.isPresent());
        assertEquals("unknown", unknownParcel.map(parcel -> parcel.id).orElse("unknown"));
    }

    @Test
    void readsAnOptionalBodyThroughAResponseFuture() throws Exception {
        Response<Optional<Parcel>> response =
                api.lookupResponse("0000000000000").get(10, TimeUnit.SECONDS);

        assertEquals(200, response.code());
        assertNotNull(response.body());
        assertFalse(response.body().isPresent());
    }

    /** The carrier REST API as the parcel-tracking client declares it. */
    interface ParcelApi {

        @GET("parcels/{id}")
        CompletableFuture<Parcel> parcel(@Path("id") String id);

        @GET("parcels/{id}")
        CompletableFuture<Response<Parcel>> parcelResponse(@Path("id") String id);

        @GET("parcels/{id}/eta")
        CompletableFuture<CourierEta> eta(@Path("id") String id, @Query("unit") String unit);

        @GET("lookup")
        CompletableFuture<Optional<Parcel>> lookup(@Query("barcode") String barcode);

        @GET("lookup")
        CompletableFuture<Response<Optional<Parcel>>> lookupResponse(
                @Query("barcode") String barcode);
    }

    /** A parcel as the carrier reports it. */
    static final class Parcel {
        String id;
        String status;
        String location;
        double weightKg;
    }

    /** The courier's estimated arrival for a parcel. */
    static final class CourierEta {
        String courier;
        int minutes;
    }

    /**
     * Gson accepts every type, so it would claim `Optional` before Retrofit's platform converter
     * gets the chance. This factory hands `Optional` types back to the chain, which reaches the
     * platform converter; that one asks the chain again for the wrapped type and lands on Gson.
     */
    static final class JsonExceptOptionalConverterFactory extends Converter.Factory {

        private final Converter.Factory json;

        JsonExceptOptionalConverterFactory(Converter.Factory json) {
            this.json = json;
        }

        @Override
        public Converter<ResponseBody, ?> responseBodyConverter(
                Type type, Annotation[] annotations, Retrofit retrofit) {
            if (getRawType(type) == Optional.class) {
                return null;
            }
            return json.responseBodyConverter(type, annotations, retrofit);
        }

        @Override
        public Converter<?, RequestBody> requestBodyConverter(
                Type type,
                Annotation[] parameterAnnotations,
                Annotation[] methodAnnotations,
                Retrofit retrofit) {
            return json.requestBodyConverter(
                    type, parameterAnnotations, methodAnnotations, retrofit);
        }
    }

    /** The carrier backend: answers by request path so concurrent calls stay deterministic. */
    final class CarrierBackend extends Dispatcher {

        @Override
        public MockResponse dispatch(RecordedRequest request) throws InterruptedException {
            String path = request.getPath();
            requestedPaths.add(path);
            switch (path) {
                case "/carrier/parcels/PX-1001":
                    return json(200, """
                            {"id": "PX-1001", "status": "IN_TRANSIT", "location": "Rotterdam hub",
                             "weightKg": 2.4}
                            """);
                case "/carrier/parcels/PX-1002":
                    return json(200, """
                            {"id": "PX-1002", "status": "DELIVERED", "location": "locker 12",
                             "weightKg": 0.8}
                            """);
                case "/carrier/parcels/PX-1001/eta?unit=minutes":
                    return json(200, """
                            {"courier": "Ana", "minutes": 35}
                            """);
                case "/carrier/parcels/PX-0404":
                    return new MockResponse()
                            .setResponseCode(404)
                            .addHeader("Content-Type", "application/json")
                            .setBody("{\"error\":\"unknown parcel PX-0404\"}");
                case "/carrier/parcels/PX-DROP":
                    return new MockResponse()
                            .setSocketPolicy(SocketPolicy.DISCONNECT_AFTER_REQUEST);
                case "/carrier/lookup?barcode=8712345678906":
                    return json(200, """
                            {"id": "PX-1002", "status": "DELIVERED", "location": "locker 12",
                             "weightKg": 0.8}
                            """);
                case "/carrier/lookup?barcode=0000000000000":
                    return json(200, "null");
                case "/carrier/lookup?barcode=SLOW-77":
                    slowLookupArrived.countDown();
                    releaseSlowLookup.await(10, TimeUnit.SECONDS);
                    return json(200, "null");
                default:
                    return new MockResponse().setResponseCode(500).setBody("unexpected " + path);
            }
        }

        private MockResponse json(int code, String body) {
            return new MockResponse()
                    .setResponseCode(code)
                    .addHeader("Content-Type", "application/json")
                    .addHeader("X-Carrier-Api", "v3")
                    .setBody(body);
        }
    }
}
