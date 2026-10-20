/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_squareup_retrofit2.retrofit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.squareup.moshi.FromJson;
import com.squareup.moshi.Json;
import com.squareup.moshi.JsonDataException;
import com.squareup.moshi.Moshi;
import com.squareup.moshi.ToJson;
import java.io.IOException;
import java.lang.annotation.Annotation;
import java.time.Instant;
import java.util.List;
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
import retrofit2.converter.moshi.MoshiConverterFactory;
import retrofit2.converter.scalars.ScalarsConverterFactory;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.POST;
import retrofit2.http.PUT;
import retrofit2.http.Path;

/**
 * Converter factory chains on `Retrofit.Builder`, where the first factory that accepts a type
 * wins: Scalars handles plain-text and primitive bodies, Moshi handles JSON objects.
 *
 * A weather-station client reads plain-text health and counter endpoints and exchanges JSON
 * readings whose field names, enum constants, and timestamps are mapped by Moshi `@Json` names
 * and a custom `@ToJson`/`@FromJson` adapter. Every body is read back off a `MockWebServer`.
 */
public class WeatherStationConverterChainTest {

    private MockWebServer server;
    private Retrofit retrofit;
    private StationApi api;

    @BeforeEach
    void startBackend() throws IOException {
        server = new MockWebServer();
        server.start();
        Moshi moshi = new Moshi.Builder().add(new InstantAdapter()).build();
        retrofit = new Retrofit.Builder()
                .baseUrl(server.url("/stations/"))
                .addConverterFactory(ScalarsConverterFactory.create())
                .addConverterFactory(MoshiConverterFactory.create(moshi))
                .build();
        api = retrofit.create(StationApi.class);
    }

    @AfterEach
    void stopBackend() throws IOException {
        server.shutdown();
    }

    @Test
    void readsPlainTextAndPrimitiveBodiesThroughTheScalarsConverter() throws Exception {
        server.enqueue(new MockResponse().setHeader("Content-Type", "text/plain").setBody("OK"));
        server.enqueue(new MockResponse().setHeader("Content-Type", "text/plain").setBody("1287"));

        assertEquals("OK", api.health().execute().body());
        Integer count = api.readingCount("ams-01").execute().body();
        assertNotNull(count);
        assertEquals(1287, count.intValue());

        assertEquals("/stations/health", server.takeRequest(5, TimeUnit.SECONDS).getPath());
        assertEquals(
                "/stations/ams-01/readings/count", server.takeRequest(5, TimeUnit.SECONDS).getPath());
    }

    @Test
    void sendsAPlainTextCalibrationNoteAsTheRequestBody() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(204));

        Response<Void> response = api.saveNote("ams-01", "Re-levelled the rain gauge").execute();

        assertEquals(204, response.code());
        RecordedRequest request = server.takeRequest(5, TimeUnit.SECONDS);
        assertNotNull(request);
        assertEquals("PUT", request.getMethod());
        assertEquals("/stations/ams-01/note", request.getPath());
        assertEquals("text/plain; charset=UTF-8", request.getHeader("Content-Type"));
        assertEquals("Re-levelled the rain gauge", request.getBody().readUtf8());
    }

    @Test
    void decodesJsonReadingsWithRenamedFieldsEnumsAndTimestamps() throws Exception {
        server.enqueue(json(200, """
                [
                  {"station_id": "ams-01", "temperature_c": 11.5, "sky": "partly_cloudy",
                   "observed_at": "2026-10-18T06:00:00Z", "tags": ["hourly"]},
                  {"station_id": "ams-01", "temperature_c": 9.25, "sky": "rain",
                   "observed_at": "2026-10-18T07:00:00Z", "tags": []}
                ]
                """));

        List<Reading> readings = api.latestReadings("ams-01").execute().body();

        assertNotNull(readings);
        assertEquals(2, readings.size());
        Reading first = readings.get(0);
        assertEquals("ams-01", first.stationId);
        assertEquals(11.5, first.temperatureCelsius);
        assertEquals(Sky.PARTLY_CLOUDY, first.sky);
        assertEquals(Instant.parse("2026-10-18T06:00:00Z"), first.observedAt);
        assertEquals(List.of("hourly"), first.tags);
        assertEquals(Sky.RAIN, readings.get(1).sky);
        assertTrue(readings.get(1).tags.isEmpty());
    }

    @Test
    void encodesAReadingAsJsonUsingTheSameMappings() throws Exception {
        server.enqueue(json(201, """
                {"accepted": 1, "rejected": 0}
                """));
        Reading reading = new Reading();
        reading.stationId = "ams-01";
        reading.temperatureCelsius = 8.0;
        reading.sky = Sky.CLEAR;
        reading.observedAt = Instant.parse("2026-10-18T08:00:00Z");
        reading.tags = List.of("manual");

        Response<Receipt> response = api.submit("ams-01", reading).execute();

        assertEquals(201, response.code());
        Receipt receipt = response.body();
        assertNotNull(receipt);
        assertEquals(1, receipt.accepted);
        assertEquals(0, receipt.rejected);
        RecordedRequest request = server.takeRequest(5, TimeUnit.SECONDS);
        assertNotNull(request);
        assertEquals("POST", request.getMethod());
        assertEquals("application/json; charset=UTF-8", request.getHeader("Content-Type"));
        assertEquals(
                "{\"observed_at\":\"2026-10-18T08:00:00Z\",\"sky\":\"clear\",\"station_id\":"
                        + "\"ams-01\",\"tags\":[\"manual\"],\"temperature_c\":8.0}",
                request.getBody().readUtf8());
    }

    @Test
    void serializesNullsOnlyWhenTheFactoryIsConfiguredToKeepThem() throws Exception {
        Moshi moshi = new Moshi.Builder().add(new InstantAdapter()).build();
        StationApi nullKeeping = new Retrofit.Builder()
                .baseUrl(server.url("/stations/"))
                .addConverterFactory(MoshiConverterFactory.create(moshi).withNullSerialization())
                .build()
                .create(StationApi.class);
        server.enqueue(json(201, "{\"accepted\": 0, \"rejected\": 1}"));
        server.enqueue(json(201, "{\"accepted\": 0, \"rejected\": 1}"));
        Reading partial = new Reading();
        partial.stationId = "ams-02";
        partial.temperatureCelsius = -1.5;

        api.submit("ams-02", partial).execute();
        nullKeeping.submit("ams-02", partial).execute();

        assertEquals(
                "{\"station_id\":\"ams-02\",\"temperature_c\":-1.5}",
                server.takeRequest(5, TimeUnit.SECONDS).getBody().readUtf8());
        assertEquals(
                "{\"observed_at\":null,\"sky\":null,\"station_id\":\"ams-02\",\"tags\":null,"
                        + "\"temperature_c\":-1.5}",
                server.takeRequest(5, TimeUnit.SECONDS).getBody().readUtf8());
    }

    @Test
    void convertsAJsonErrorBodyAndRejectsAMalformedReading() throws Exception {
        server.enqueue(json(422, """
                {"error": "unknown_station", "detail": "Station xyz-99 is not registered"}
                """));
        server.enqueue(json(200, """
                [{"station_id": "ams-01", "temperature_c": "warm", "sky": "clear"}]
                """));

        Response<List<Reading>> failed = api.latestReadings("xyz-99").execute();
        assertEquals(422, failed.code());
        assertNull(failed.body());
        Converter<ResponseBody, StationError> errorConverter =
                retrofit.responseBodyConverter(StationError.class, new Annotation[0]);
        StationError error = errorConverter.convert(failed.errorBody());
        assertNotNull(error);
        assertEquals("unknown_station", error.error);
        assertEquals("Station xyz-99 is not registered", error.detail);

        Call<List<Reading>> malformed = api.latestReadings("ams-01");
        JsonDataException thrown = assertThrows(JsonDataException.class, malformed::execute);
        assertTrue(thrown.getMessage().contains("temperature_c"), thrown.getMessage());
    }

    private static MockResponse json(int code, String body) {
        return new MockResponse()
                .setResponseCode(code)
                .addHeader("Content-Type", "application/json")
                .setBody(body);
    }

    /** The station API, mixing plain-text and JSON endpoints. */
    interface StationApi {

        @GET("health")
        Call<String> health();

        @GET("{station}/readings/count")
        Call<Integer> readingCount(@Path("station") String station);

        @PUT("{station}/note")
        Call<Void> saveNote(@Path("station") String station, @Body String note);

        @GET("{station}/readings/latest")
        Call<List<Reading>> latestReadings(@Path("station") String station);

        @POST("{station}/readings")
        Call<Receipt> submit(@Path("station") String station, @Body Reading reading);
    }

    /** Sky conditions, spelled in snake case on the wire. */
    enum Sky {
        @Json(name = "clear") CLEAR,
        @Json(name = "partly_cloudy") PARTLY_CLOUDY,
        @Json(name = "rain") RAIN
    }

    /** One weather observation as the backend exchanges it. */
    static final class Reading {
        @Json(name = "station_id") String stationId;
        @Json(name = "temperature_c") double temperatureCelsius;
        Sky sky;
        @Json(name = "observed_at") Instant observedAt;
        List<String> tags;
    }

    /** The backend's answer to a submitted batch. */
    static final class Receipt {
        int accepted;
        int rejected;
    }

    /** The error document the backend returns with a 4xx status. */
    static final class StationError {
        String error;
        String detail;
    }

    /** Maps `Instant` to and from its ISO-8601 text, the backend's timestamp format. */
    public static final class InstantAdapter {

        @ToJson
        public String toJson(Instant instant) {
            return instant.toString();
        }

        @FromJson
        public Instant fromJson(String text) {
            return Instant.parse(text);
        }
    }
}
