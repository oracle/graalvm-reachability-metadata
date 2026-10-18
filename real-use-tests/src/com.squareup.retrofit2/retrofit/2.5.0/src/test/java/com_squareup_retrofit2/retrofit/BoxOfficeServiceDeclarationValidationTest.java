/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_squareup_retrofit2.retrofit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.TimeUnit;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import retrofit2.Call;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;
import retrofit2.http.Body;
import retrofit2.http.DELETE;
import retrofit2.http.Field;
import retrofit2.http.GET;
import retrofit2.http.HEAD;
import retrofit2.http.POST;
import retrofit2.http.Path;
import retrofit2.http.Query;
import retrofit2.http.Url;

/**
 * Service-method declaration parsing and validation: how `Retrofit.create` checks a service
 * interface and its annotated methods, lazily on first invocation or eagerly with
 * `validateEagerly`, and the descriptive `IllegalArgumentException`s it raises for misdeclared
 * methods and parameters.
 *
 * A box-office client for a concert venue declares its ticketing endpoints as a Retrofit
 * interface. While the team iterates on that interface, mistakes in it must surface as clear
 * errors naming the offending method and parameter, never as a malformed request reaching the
 * backend. The backend is a `MockWebServer`, whose request count proves that rejected
 * declarations send nothing.
 */
public class BoxOfficeServiceDeclarationValidationTest {

    private MockWebServer server;
    private Retrofit retrofit;

    @BeforeEach
    void startBackend() throws IOException {
        server = new MockWebServer();
        server.start();
        retrofit = new Retrofit.Builder()
                .baseUrl(server.url("/box-office/"))
                .addConverterFactory(GsonConverterFactory.create())
                .build();
    }

    @AfterEach
    void stopBackend() throws IOException {
        server.shutdown();
    }

    @Test
    void lazilyValidatesSoWellFormedMethodsWorkBesideAMisdeclaredOne() throws Exception {
        BoxOfficeApi api = retrofit.create(BoxOfficeApi.class);
        server.enqueue(json("{\"id\": 42, \"artist\": \"The Retrofits\", \"venue\": \"Hall A\"}"));

        Response<Concert> response = api.concert(42).execute();

        assertEquals(200, response.code());
        Concert concert = response.body();
        assertNotNull(concert);
        assertEquals("The Retrofits", concert.artist);
        RecordedRequest request = server.takeRequest(5, TimeUnit.SECONDS);
        assertNotNull(request);
        assertEquals("/box-office/concerts/42", request.getPath());

        for (int attempt = 0; attempt < 2; attempt++) {
            IllegalArgumentException error =
                    assertThrows(IllegalArgumentException.class, () -> api.seats(42));
            assertEquals("""
                    URL "concerts/{id}/seats" does not contain "{concertId}". (parameter #1)
                        for method BoxOfficeApi.seats""", error.getMessage());
        }
        assertEquals(1, server.getRequestCount());
    }

    @Test
    void eagerValidationAcceptsAWellFormedInterfaceWithoutTouchingTheNetwork() throws Exception {
        Retrofit strict = retrofit.newBuilder().validateEagerly(true).build();

        CheckoutApi api = strict.create(CheckoutApi.class);

        assertEquals(0, server.getRequestCount());
        server.enqueue(json("{\"orderId\": \"ORD-7\", \"seats\": [\"B12\", \"B13\"]}"));
        server.enqueue(new MockResponse().setResponseCode(204));
        server.enqueue(new MockResponse().setResponseCode(200).addHeader("X-Seats-Left", "118"));

        Order order = api.reserve(42, new Reservation(List.of("B12", "B13"))).execute().body();
        assertNotNull(order);
        assertEquals("ORD-7", order.orderId);
        assertEquals(List.of("B12", "B13"), order.seats);
        assertTrue(api.release("ORD-7").execute().isSuccessful());
        Response<Void> availability = api.availability(42).execute();
        assertEquals("118", availability.headers().get("X-Seats-Left"));

        assertEquals("POST", server.takeRequest(5, TimeUnit.SECONDS).getMethod());
        assertEquals("DELETE", server.takeRequest(5, TimeUnit.SECONDS).getMethod());
        assertEquals("HEAD", server.takeRequest(5, TimeUnit.SECONDS).getMethod());
    }

    @Test
    void rejectsAMethodWithoutAnHttpVerb() {
        assertRejected(MissingVerbApi.class, """
                HTTP method annotation is required (e.g., @GET, @POST, etc.).
                    for method MissingVerbApi.concert""");
    }

    @Test
    void rejectsABodyOnAVerbThatCannotCarryOne() {
        assertRejected(BodyOnGetApi.class, """
                Non-body HTTP method cannot contain @Body.
                    for method BodyOnGetApi.search""");
    }

    @Test
    void rejectsAFormFieldOutsideAFormEncodedMethod() {
        assertRejected(FieldWithoutFormApi.class, """
                @Field parameters can only be used with form encoding. (parameter #1)
                    for method FieldWithoutFormApi.redeem""");
    }

    @Test
    void rejectsAParameterWithoutARetrofitAnnotation() {
        assertRejected(UnannotatedParameterApi.class, """
                No Retrofit annotation found. (parameter #1)
                    for method UnannotatedParameterApi.concertsIn""");
    }

    @Test
    void rejectsADynamicUrlCombinedWithARelativeUrl() {
        assertRejected(ConflictingUrlApi.class, """
                @Url cannot be used with @GET URL (parameter #1)
                    for method ConflictingUrlApi.seatMap""");
    }

    @Test
    void rejectsAPathReplacementInsideTheQueryString() {
        assertRejected(PathInQueryStringApi.class, """
                URL query string "city={city}" must not have replace block. \
                For dynamic query parameters use @Query.
                    for method PathInQueryStringApi.concertsIn""");
    }

    @Test
    void rejectsAWildcardInTheReturnType() {
        assertRejected(WildcardReturnApi.class, """
                Method return type must not include a type variable or wildcard: \
                retrofit2.Call<java.util.List<? extends \
                com_squareup_retrofit2.retrofit.BoxOfficeServiceDeclarationValidationTest$Concert>>
                    for method WildcardReturnApi.upcoming""");
    }

    @Test
    void rejectsAVoidReturnType() {
        assertRejected(VoidReturnApi.class, """
                Service methods cannot return void.
                    for method VoidReturnApi.cancel""");
    }

    @Test
    void rejectsTheRawOkHttpResponseAsABodyType() {
        assertRejected(RawResponseApi.class, """
                'okhttp3.Response' is not a valid response body type. Did you mean ResponseBody?
                    for method RawResponseApi.concert""");
    }

    @Test
    void rejectsAHeadRequestThatExpectsABody() {
        assertRejected(HeadWithBodyApi.class, """
                HEAD method must use Void as response type.
                    for method HeadWithBodyApi.availability""");
    }

    @Test
    void rejectsAServiceInterfaceThatExtendsAnother() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> retrofit.create(VipBoxOfficeApi.class));

        assertEquals("API interfaces must not extend other interfaces.", error.getMessage());
        assertEquals(0, server.getRequestCount());
    }

    private void assertRejected(Class<?> service, String expectedMessage) {
        Retrofit strict = retrofit.newBuilder().validateEagerly(true).build();

        IllegalArgumentException error =
                assertThrows(IllegalArgumentException.class, () -> strict.create(service));

        assertEquals(expectedMessage, error.getMessage());
        assertEquals(0, server.getRequestCount());
    }

    private static MockResponse json(String body) {
        return new MockResponse()
                .setResponseCode(200)
                .addHeader("Content-Type", "application/json")
                .setBody(body);
    }

    /** A concert as the backend describes it. */
    static final class Concert {
        long id;
        String artist;
        String venue;
    }

    /** A request to hold seats for a concert. */
    static final class Reservation {
        final List<String> seats;

        Reservation(List<String> seats) {
            this.seats = seats;
        }
    }

    /** The order the backend opens for held seats. */
    static final class Order {
        String orderId;
        List<String> seats;
    }

    /** Ticketing endpoints with one method whose `@Path` name does not match its URL. */
    interface BoxOfficeApi {

        @GET("concerts/{id}")
        Call<Concert> concert(@Path("id") long id);

        @GET("concerts/{id}/seats")
        Call<List<String>> seats(@Path("concertId") long id);
    }

    /** A checkout interface declared correctly. */
    interface CheckoutApi {

        @POST("concerts/{id}/reservations")
        Call<Order> reserve(@Path("id") long concertId, @Body Reservation reservation);

        @DELETE("orders/{orderId}")
        Call<Void> release(@Path("orderId") String orderId);

        @HEAD("concerts/{id}/seats")
        Call<Void> availability(@Path("id") long concertId);
    }

    interface MissingVerbApi {

        Call<Concert> concert(@Query("id") long id);
    }

    interface BodyOnGetApi {

        @GET("concerts")
        Call<List<Concert>> search(@Body Concert filter);
    }

    interface FieldWithoutFormApi {

        @POST("vouchers")
        Call<Order> redeem(@Field("code") String code);
    }

    interface UnannotatedParameterApi {

        @GET("concerts")
        Call<List<Concert>> concertsIn(String city);
    }

    interface ConflictingUrlApi {

        @GET("concerts/42/seat-map")
        Call<String> seatMap(@Url String url);
    }

    interface PathInQueryStringApi {

        @GET("concerts?city={city}")
        Call<List<Concert>> concertsIn(@Path("city") String city);
    }

    interface WildcardReturnApi {

        @GET("concerts/upcoming")
        Call<List<? extends Concert>> upcoming();
    }

    interface VoidReturnApi {

        @DELETE("orders/{orderId}")
        void cancel(@Path("orderId") String orderId);
    }

    interface RawResponseApi {

        @GET("concerts/{id}")
        Call<okhttp3.Response> concert(@Path("id") long id);
    }

    interface HeadWithBodyApi {

        @HEAD("concerts/{id}/seats")
        Call<Concert> availability(@Path("id") long concertId);
    }

    /** A VIP interface that tries to inherit the regular box-office endpoints. */
    interface VipBoxOfficeApi extends BoxOfficeApi {

        @GET("concerts/{id}/lounge")
        Call<String> lounge(@Path("id") long id);
    }
}
