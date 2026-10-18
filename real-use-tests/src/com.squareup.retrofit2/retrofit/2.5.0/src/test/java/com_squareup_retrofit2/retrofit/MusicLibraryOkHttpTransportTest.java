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
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.annotations.SerializedName;
import java.io.File;
import java.io.IOException;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import okhttp3.Authenticator;
import okhttp3.Cache;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Route;
import okhttp3.logging.HttpLoggingInterceptor;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import okhttp3.mockwebserver.SocketPolicy;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import retrofit2.Call;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;
import retrofit2.http.Body;
import retrofit2.http.Field;
import retrofit2.http.FormUrlEncoded;
import retrofit2.http.GET;
import retrofit2.http.POST;
import retrofit2.http.Path;

/**
 * Retrofit's HTTP transport through an application-configured OkHttp client: `Retrofit.Builder`
 * `client` and `callFactory`, with an auth interceptor, an `Authenticator` that refreshes an
 * expired token through a second Retrofit service, `HttpLoggingInterceptor`, an on-disk response
 * `Cache`, and read timeouts surfacing from `Call.execute`.
 *
 * A music-library client signs every request with a bearer token, renews that token when the
 * backend answers 401, logs its traffic, serves repeated catalogue reads from the HTTP cache, and
 * reports a stalled backend as a timeout. The backend is a `MockWebServer`; every request that
 * reaches it is read back and checked.
 */
public class MusicLibraryOkHttpTransportTest {

    @TempDir
    File cacheDirectory;

    private MockWebServer server;
    private TokenStore tokens;
    private List<String> httpLog;
    private Cache cache;
    private OkHttpClient client;
    private Retrofit retrofit;
    private LibraryApi api;

    @BeforeEach
    void startBackend() throws IOException {
        server = new MockWebServer();
        server.start();
        tokens = new TokenStore("stale-access", "refresh-42");
        httpLog = Collections.synchronizedList(new ArrayList<>());
        cache = new Cache(cacheDirectory, 1024 * 1024);

        AuthApi authApi = new Retrofit.Builder()
                .baseUrl(server.url("/"))
                .addConverterFactory(GsonConverterFactory.create())
                .build()
                .create(AuthApi.class);

        HttpLoggingInterceptor logging = new HttpLoggingInterceptor(httpLog::add);
        logging.setLevel(HttpLoggingInterceptor.Level.BODY);

        client = new OkHttpClient.Builder()
                .addInterceptor(chain -> chain.proceed(chain.request().newBuilder()
                        .header("Authorization", "Bearer " + tokens.accessToken)
                        .build()))
                .addInterceptor(logging)
                .authenticator(new TokenRefreshAuthenticator(tokens, authApi))
                .cache(cache)
                .readTimeout(500, TimeUnit.MILLISECONDS)
                .build();
        retrofit = new Retrofit.Builder()
                .baseUrl(server.url("/library/"))
                .client(client)
                .addConverterFactory(GsonConverterFactory.create())
                .build();
        api = retrofit.create(LibraryApi.class);
    }

    @AfterEach
    void stopBackend() throws IOException {
        cache.close();
        server.shutdown();
    }

    @Test
    void refreshesAnExpiredTokenAndRetriesTheRequest() throws Exception {
        server.enqueue(json(401, """
                {"error": "token_expired"}
                """));
        server.enqueue(json(200, """
                {"access_token": "fresh-access", "refresh_token": "refresh-43"}
                """));
        server.enqueue(json(200, """
                [{"id": "pl-1", "name": "Focus", "trackCount": 24},
                 {"id": "pl-2", "name": "Road trip", "trackCount": 61}]
                """));

        Response<List<Playlist>> response = api.playlists().execute();

        assertTrue(response.isSuccessful());
        List<Playlist> playlists = response.body();
        assertNotNull(playlists);
        assertEquals(2, playlists.size());
        assertEquals("Road trip", playlists.get(1).name);
        assertEquals(61, playlists.get(1).trackCount);
        assertEquals(401, response.raw().priorResponse().code());
        assertEquals("fresh-access", tokens.accessToken);
        assertEquals("refresh-43", tokens.refreshToken);

        RecordedRequest expired = server.takeRequest(5, TimeUnit.SECONDS);
        assertEquals("/library/me/playlists", expired.getPath());
        assertEquals("Bearer stale-access", expired.getHeader("Authorization"));
        RecordedRequest refresh = server.takeRequest(5, TimeUnit.SECONDS);
        assertEquals("POST", refresh.getMethod());
        assertEquals("/oauth/token", refresh.getPath());
        assertEquals("grant_type=refresh_token&refresh_token=refresh-42", refresh.getBody().readUtf8());
        assertNull(refresh.getHeader("Authorization"));
        RecordedRequest retried = server.takeRequest(5, TimeUnit.SECONDS);
        assertEquals("/library/me/playlists", retried.getPath());
        assertEquals("Bearer fresh-access", retried.getHeader("Authorization"));
    }

    @Test
    void handsTheUnauthorizedResponseToTheCallerWhenTheRefreshIsRejected() throws Exception {
        server.enqueue(json(401, """
                {"error": "token_expired"}
                """));
        server.enqueue(json(400, """
                {"error": "invalid_grant"}
                """));

        Response<List<Playlist>> response = api.playlists().execute();

        assertFalse(response.isSuccessful());
        assertEquals(401, response.code());
        assertNull(response.body());
        assertEquals("{\"error\": \"token_expired\"}", response.errorBody().string().trim());
        assertEquals("stale-access", tokens.accessToken);
        assertEquals(2, server.getRequestCount());
    }

    @Test
    void logsRequestAndResponseBodiesOfAJsonCall() throws Exception {
        server.enqueue(json(201, """
                {"id": "pl-9", "name": "Late night", "trackCount": 0}
                """));

        Response<Playlist> response = api.createPlaylist(new Playlist(null, "Late night", 0)).execute();

        assertEquals(201, response.code());
        assertEquals("pl-9", response.body().id);
        String url = server.url("/library/me/playlists").toString();
        assertTrue(httpLog.contains("--> POST " + url), httpLog::toString);
        assertTrue(httpLog.contains("Content-Type: application/json; charset=UTF-8"), httpLog::toString);
        assertTrue(httpLog.contains("{\"name\":\"Late night\",\"trackCount\":0}"), httpLog::toString);
        assertTrue(httpLog.stream().anyMatch(line -> line.startsWith("<-- 201 OK " + url)),
                httpLog::toString);
        assertTrue(httpLog.stream().anyMatch(line -> line.contains("\"id\": \"pl-9\"")), httpLog::toString);
        assertTrue(httpLog.stream().anyMatch(line -> line.startsWith("<-- END HTTP")), httpLog::toString);

        RecordedRequest request = server.takeRequest(5, TimeUnit.SECONDS);
        assertEquals("{\"name\":\"Late night\",\"trackCount\":0}", request.getBody().readUtf8());
    }

    @Test
    void servesARepeatedCatalogueReadFromTheHttpCache() throws Exception {
        server.enqueue(json(200, """
                {"id": "al-7", "title": "Blue Train", "artist": "John Coltrane"}
                """)
                .setHeader("Cache-Control", "max-age=600")
                .setHeader("ETag", "\"al-7-v1\""));

        Response<Album> first = api.album("al-7").execute();
        Response<Album> second = api.album("al-7").execute();

        assertEquals("Blue Train", first.body().title);
        assertNotNull(first.raw().networkResponse());
        assertNull(first.raw().cacheResponse());
        assertEquals("John Coltrane", second.body().artist);
        assertNull(second.raw().networkResponse());
        assertNotNull(second.raw().cacheResponse());
        assertEquals(1, server.getRequestCount());
        assertEquals(1, cache.hitCount());
    }

    @Test
    void revalidatesACachedAlbumThatTheServerMarkedNoCache() throws Exception {
        server.enqueue(json(200, """
                {"id": "al-3", "title": "Kind of Blue", "artist": "Miles Davis"}
                """)
                .setHeader("Cache-Control", "no-cache")
                .setHeader("ETag", "\"al-3-v1\""));
        server.enqueue(new MockResponse().setResponseCode(304).setHeader("ETag", "\"al-3-v1\""));

        assertEquals("Kind of Blue", api.album("al-3").execute().body().title);
        Response<Album> revalidated = api.album("al-3").execute();

        assertEquals(200, revalidated.code());
        assertEquals("Miles Davis", revalidated.body().artist);
        assertEquals(304, revalidated.raw().networkResponse().code());
        assertNotNull(revalidated.raw().cacheResponse());

        server.takeRequest(5, TimeUnit.SECONDS);
        RecordedRequest conditional = server.takeRequest(5, TimeUnit.SECONDS);
        assertEquals("/library/albums/al-3", conditional.getPath());
        assertEquals("\"al-3-v1\"", conditional.getHeader("If-None-Match"));
    }

    @Test
    void reportsAStalledBackendAsAReadTimeout() throws Exception {
        server.enqueue(new MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE));

        Call<List<Playlist>> call = api.playlists();

        assertThrows(SocketTimeoutException.class, call::execute);
        assertTrue(call.isExecuted());
        assertEquals(1, server.getRequestCount());
    }

    @Test
    void routesCallsThroughAnApplicationSuppliedCallFactory() throws Exception {
        AtomicInteger createdCalls = new AtomicInteger();
        okhttp3.Call.Factory tracingFactory = request -> {
            createdCalls.incrementAndGet();
            Request traced = request.newBuilder()
                    .header("X-Trace-Id", "trace-" + createdCalls.get())
                    .build();
            return client.newCall(traced);
        };
        Retrofit traced = retrofit.newBuilder().callFactory(tracingFactory).build();
        LibraryApi tracedApi = traced.create(LibraryApi.class);
        server.enqueue(json(200, """
                []
                """));
        server.enqueue(json(200, """
                [{"id": "pl-4", "name": "Morning", "trackCount": 12}]
                """));

        assertSame(tracingFactory, traced.callFactory());
        assertTrue(tracedApi.playlists().execute().body().isEmpty());
        assertEquals("Morning", tracedApi.playlists().execute().body().get(0).name);

        assertEquals(2, createdCalls.get());
        RecordedRequest first = server.takeRequest(5, TimeUnit.SECONDS);
        assertEquals("trace-1", first.getHeader("X-Trace-Id"));
        assertEquals("Bearer stale-access", first.getHeader("Authorization"));
        RecordedRequest second = server.takeRequest(5, TimeUnit.SECONDS);
        assertEquals("trace-2", second.getHeader("X-Trace-Id"));
    }

    private static MockResponse json(int code, String body) {
        return new MockResponse()
                .setResponseCode(code)
                .setHeader("Content-Type", "application/json")
                .setBody(body.trim());
    }

    /** The music-library endpoints the application calls. */
    interface LibraryApi {

        @GET("me/playlists")
        Call<List<Playlist>> playlists();

        @POST("me/playlists")
        Call<Playlist> createPlaylist(@Body Playlist playlist);

        @GET("albums/{id}")
        Call<Album> album(@Path("id") String id);
    }

    /** The OAuth endpoint used to renew an access token. */
    interface AuthApi {

        @FormUrlEncoded
        @POST("oauth/token")
        Call<TokenGrant> refresh(
                @Field("grant_type") String grantType,
                @Field("refresh_token") String refreshToken);
    }

    /** Renews the access token once per failed request and gives up if the renewal fails. */
    static final class TokenRefreshAuthenticator implements Authenticator {

        private final TokenStore tokens;
        private final AuthApi authApi;

        TokenRefreshAuthenticator(TokenStore tokens, AuthApi authApi) {
            this.tokens = tokens;
            this.authApi = authApi;
        }

        @Override
        public Request authenticate(Route route, okhttp3.Response response) throws IOException {
            if (response.priorResponse() != null) {
                return null;
            }
            Response<TokenGrant> grant = authApi.refresh("refresh_token", tokens.refreshToken).execute();
            if (!grant.isSuccessful() || grant.body() == null) {
                return null;
            }
            tokens.accessToken = grant.body().accessToken;
            tokens.refreshToken = grant.body().refreshToken;
            return response.request().newBuilder()
                    .header("Authorization", "Bearer " + tokens.accessToken)
                    .build();
        }
    }

    /** The session's current credentials. */
    static final class TokenStore {

        volatile String accessToken;
        volatile String refreshToken;

        TokenStore(String accessToken, String refreshToken) {
            this.accessToken = accessToken;
            this.refreshToken = refreshToken;
        }
    }

    /** An OAuth token grant as the auth endpoint returns it. */
    static final class TokenGrant {

        @SerializedName("access_token")
        String accessToken;
        @SerializedName("refresh_token")
        String refreshToken;
    }

    /** A user playlist. */
    static final class Playlist {

        String id;
        String name;
        int trackCount;

        Playlist(String id, String name, int trackCount) {
            this.id = id;
            this.name = name;
            this.trackCount = trackCount;
        }
    }

    /** A catalogue album. */
    static final class Album {

        String id;
        String title;
        String artist;
    }
}
