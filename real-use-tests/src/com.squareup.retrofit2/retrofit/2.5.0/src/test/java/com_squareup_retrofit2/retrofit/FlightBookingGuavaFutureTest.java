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

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import okhttp3.mockwebserver.Dispatcher;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import okhttp3.mockwebserver.SocketPolicy;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import retrofit2.HttpException;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.adapter.guava.GuavaCallAdapterFactory;
import retrofit2.converter.gson.GsonConverterFactory;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.POST;
import retrofit2.http.Path;
import retrofit2.http.Query;

/**
 * Guava call adapter: service methods returning `ListenableFuture` of a body or of a `Response`,
 * composed with `Futures`, failing with `HttpException` or the network error, and cancelled
 * through `Future.cancel`.
 *
 * A flight-booking client searches flights, books a seat, and combines fares from several
 * routes, all through futures that `GuavaCallAdapterFactory` completes from enqueued calls
 * against a `MockWebServer`.
 */
public class FlightBookingGuavaFutureTest {

    private MockWebServer server;
    private FlightApi api;

    @BeforeEach
    void startBackend() throws IOException {
        server = new MockWebServer();
        server.start();
        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl(server.url("/air/"))
                .addCallAdapterFactory(GuavaCallAdapterFactory.create())
                .addConverterFactory(GsonConverterFactory.create())
                .build();
        api = retrofit.create(FlightApi.class);
    }

    @AfterEach
    void stopBackend() throws IOException {
        server.shutdown();
    }

    @Test
    void completesABodyFutureWithTheDecodedFlights() throws Exception {
        server.enqueue(json(200, """
                [
                  {"number": "KL1001", "from": "AMS", "to": "LHR", "fareCents": 12900},
                  {"number": "BA431", "from": "AMS", "to": "LHR", "fareCents": 14550}
                ]
                """));

        ListenableFuture<List<Flight>> future = api.search("AMS", "LHR", "2026-11-02");
        List<Flight> flights = future.get(5, TimeUnit.SECONDS);

        assertTrue(future.isDone());
        assertEquals(2, flights.size());
        assertEquals("KL1001", flights.get(0).number);
        assertEquals(14550, flights.get(1).fareCents);
        RecordedRequest request = server.takeRequest(5, TimeUnit.SECONDS);
        assertNotNull(request);
        assertEquals("/air/flights?from=AMS&to=LHR&date=2026-11-02", request.getPath());
    }

    @Test
    void transformsAndCombinesFuturesFromSeveralRoutes() throws Exception {
        MockResponse london = json(200, """
                [{"number": "KL1001", "from": "AMS", "to": "LHR", "fareCents": 12900},
                 {"number": "BA431", "from": "AMS", "to": "LHR", "fareCents": 14550}]
                """);
        MockResponse paris = json(200, """
                [{"number": "AF1241", "from": "AMS", "to": "CDG", "fareCents": 9900}]
                """);
        // Both searches run concurrently, so answer by destination rather than arrival order.
        server.setDispatcher(new Dispatcher() {
            @Override
            public MockResponse dispatch(RecordedRequest request) {
                return request.getRequestUrl().queryParameter("to").equals("LHR") ? london : paris;
            }
        });

        ListenableFuture<Integer> cheapestToLondon = Futures.transform(
                api.search("AMS", "LHR", "2026-11-02"),
                FlightBookingGuavaFutureTest::cheapestFare,
                MoreExecutors.directExecutor());
        ListenableFuture<Integer> cheapestToParis = Futures.transform(
                api.search("AMS", "CDG", "2026-11-02"),
                FlightBookingGuavaFutureTest::cheapestFare,
                MoreExecutors.directExecutor());

        List<Integer> fares = Futures.allAsList(cheapestToLondon, cheapestToParis)
                .get(5, TimeUnit.SECONDS);

        assertEquals(List.of(12900, 9900), fares);
        assertEquals(2, server.getRequestCount());
    }

    @Test
    void completesAResponseFutureWithStatusHeadersAndBody() throws Exception {
        server.enqueue(json(201, """
                {"reference": "PNR7Q2", "seat": "14C", "status": "CONFIRMED"}
                """).setHeader("Location", "/air/bookings/PNR7Q2"));
        BookingRequest booking = new BookingRequest();
        booking.flight = "KL1001";
        booking.passenger = "Ada Lovelace";
        booking.seat = "14C";

        Response<Booking> response = api.book(booking).get(5, TimeUnit.SECONDS);

        assertEquals(201, response.code());
        assertEquals("/air/bookings/PNR7Q2", response.headers().get("Location"));
        Booking confirmed = response.body();
        assertNotNull(confirmed);
        assertEquals("PNR7Q2", confirmed.reference);
        assertEquals("CONFIRMED", confirmed.status);
        RecordedRequest request = server.takeRequest(5, TimeUnit.SECONDS);
        assertNotNull(request);
        assertEquals("POST", request.getMethod());
        assertEquals(
                "{\"flight\":\"KL1001\",\"passenger\":\"Ada Lovelace\",\"seat\":\"14C\"}",
                request.getBody().readUtf8());
    }

    @Test
    void keepsAnErrorStatusInsideAResponseFuture() throws Exception {
        server.enqueue(json(409, """
                {"error": "seat 14C already taken"}
                """));
        BookingRequest booking = new BookingRequest();
        booking.flight = "KL1001";
        booking.passenger = "Grace Hopper";
        booking.seat = "14C";

        Response<Booking> response = api.book(booking).get(5, TimeUnit.SECONDS);

        assertFalse(response.isSuccessful());
        assertEquals(409, response.code());
        assertNull(response.body());
        assertNotNull(response.errorBody());
        assertEquals("{\"error\": \"seat 14C already taken\"}", response.errorBody().string().trim());
    }

    @Test
    void failsABodyFutureWithHttpExceptionOnAnErrorStatus() throws Exception {
        server.enqueue(json(404, """
                {"error": "no such booking"}
                """));

        ListenableFuture<Booking> future = api.booking("ZZZ999");
        ExecutionException failure =
                assertThrows(ExecutionException.class, () -> future.get(5, TimeUnit.SECONDS));

        HttpException httpError = assertInstanceOf(HttpException.class, failure.getCause());
        assertEquals(404, httpError.code());
        assertEquals("HTTP 404 Client Error", httpError.getMessage());
        Response<?> response = httpError.response();
        assertNotNull(response);
        assertEquals("/air/bookings/ZZZ999", response.raw().request().url().encodedPath());
    }

    @Test
    void deliversNetworkFailuresToAFutureCallback() throws Exception {
        server.enqueue(new MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START));
        CountDownLatch delivered = new CountDownLatch(1);
        AtomicReference<Throwable> received = new AtomicReference<>();

        Futures.addCallback(api.booking("PNR7Q2"), new FutureCallback<Booking>() {
            @Override
            public void onSuccess(Booking result) {
                delivered.countDown();
            }

            @Override
            public void onFailure(Throwable error) {
                received.set(error);
                delivered.countDown();
            }
        }, MoreExecutors.directExecutor());

        assertTrue(delivered.await(5, TimeUnit.SECONDS));
        assertInstanceOf(IOException.class, received.get());
    }

    @Test
    void cancellingTheFutureCancelsThePendingCall() throws Exception {
        server.enqueue(new MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE));
        server.enqueue(json(200, """
                {"reference": "PNR7Q2", "seat": "14C", "status": "CONFIRMED"}
                """));

        ListenableFuture<Booking> pending = api.booking("SLOW01");
        assertNotNull(server.takeRequest(5, TimeUnit.SECONDS));
        assertTrue(pending.cancel(true));

        assertTrue(pending.isCancelled());
        assertThrows(CancellationException.class, () -> pending.get(5, TimeUnit.SECONDS));
        Booking next = api.booking("PNR7Q2").get(5, TimeUnit.SECONDS);
        assertEquals("14C", next.seat);
    }

    private static int cheapestFare(List<Flight> flights) {
        return flights.stream().mapToInt(flight -> flight.fareCents).min().orElseThrow();
    }

    private static MockResponse json(int code, String body) {
        return new MockResponse()
                .setResponseCode(code)
                .setHeader("Content-Type", "application/json")
                .setBody(body);
    }

    /** The airline backend, with every call surfaced as a Guava future. */
    interface FlightApi {

        @GET("flights")
        ListenableFuture<List<Flight>> search(
                @Query("from") String from, @Query("to") String to, @Query("date") String date);

        @POST("bookings")
        ListenableFuture<Response<Booking>> book(@Body BookingRequest request);

        @GET("bookings/{reference}")
        ListenableFuture<Booking> booking(@Path("reference") String reference);
    }

    /** One flight offered on a route. */
    static final class Flight {
        String number;
        String from;
        String to;
        int fareCents;
    }

    /** The seat a passenger asks to book. */
    static final class BookingRequest {
        String flight;
        String passenger;
        String seat;
    }

    /** A booking as the backend stores it. */
    static final class Booking {
        String reference;
        String seat;
        String status;
    }
}
