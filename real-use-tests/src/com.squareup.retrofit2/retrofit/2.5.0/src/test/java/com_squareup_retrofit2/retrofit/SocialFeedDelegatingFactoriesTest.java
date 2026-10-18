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
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;
import io.reactivex.Single;
import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Type;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
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
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.adapter.rxjava2.RxJava2CallAdapterFactory;
import retrofit2.converter.gson.GsonConverterFactory;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.POST;
import retrofit2.http.Path;
import retrofit2.http.Query;

/**
 * Delegating factories that decorate the next factory in the chain through
 * `Retrofit.nextResponseBodyConverter`, `nextRequestBodyConverter`, and `nextCallAdapter`.
 *
 * A social-feed client talks to a backend that wraps payloads in a `{"data": ..., "meta": ...}`
 * envelope. An application converter factory unwraps and wraps that envelope for `@Enveloped`
 * endpoints by delegating the JSON work to the Gson factory behind it, and a metering call
 * adapter factory records every dispatched request before handing the call to the RxJava or
 * built-in adapter behind it. The scenario checks the unwrapped values, the envelope on the wire,
 * the dispatch log across clones, and the `Skipped` report when nothing behind a factory fits.
 */
public class SocialFeedDelegatingFactoriesTest {

    private MockWebServer server;
    private EnvelopeConverterFactory envelopes;
    private MeteringCallAdapterFactory metering;
    private GsonConverterFactory gson;
    private Retrofit retrofit;
    private FeedApi api;

    @BeforeEach
    void startBackend() throws IOException {
        server = new MockWebServer();
        server.start();
        envelopes = new EnvelopeConverterFactory("feed-client/4.2");
        metering = new MeteringCallAdapterFactory();
        gson = GsonConverterFactory.create();
        retrofit = new Retrofit.Builder()
                .baseUrl(server.url("/feed/"))
                .addConverterFactory(envelopes)
                .addConverterFactory(gson)
                .addCallAdapterFactory(metering)
                .addCallAdapterFactory(RxJava2CallAdapterFactory.create())
                .build();
        api = retrofit.create(FeedApi.class);
    }

    @AfterEach
    void stopBackend() throws IOException {
        server.shutdown();
    }

    @Test
    void keepsTheDecoratingFactoriesAheadOfTheOnesTheyDelegateTo() {
        List<Converter.Factory> converters = retrofit.converterFactories();
        assertTrue(converters.indexOf(envelopes) >= 0);
        assertTrue(converters.indexOf(envelopes) < converters.indexOf(gson));

        List<CallAdapter.Factory> adapters = retrofit.callAdapterFactories();
        assertSame(metering, adapters.get(0));
        assertTrue(adapters.get(1) instanceof RxJava2CallAdapterFactory);
    }

    @Test
    void unwrapsAnEnvelopedTimelineThroughTheReactiveAdapter() throws Exception {
        server.enqueue(json(200, """
                {
                  "data": [
                    {"id": "p1", "author": "ana", "text": "Native images start fast"},
                    {"id": "p2", "author": "bo", "text": "Retrofit on GraalVM"}
                  ],
                  "meta": {"requestId": "req-17", "nextCursor": "c-2"}
                }
                """));

        List<Post> timeline = api.timeline("c-1").blockingGet();

        assertEquals(List.of("ana: Native images start fast", "bo: Retrofit on GraalVM"),
                timeline.stream().map(post -> post.author + ": " + post.text)
                        .collect(Collectors.toList()));
        assertEquals(1, envelopes.received.size());
        assertEquals("req-17", envelopes.received.get(0).requestId);
        assertEquals("c-2", envelopes.received.get(0).nextCursor);
        assertEquals(List.of("GET /feed/timeline"), metering.dispatched);
        RecordedRequest request = server.takeRequest(5, TimeUnit.SECONDS);
        assertNotNull(request);
        assertEquals("/feed/timeline?cursor=c-1", request.getPath());
    }

    @Test
    void wrapsAPublishedPostInAnEnvelopeAndUnwrapsTheReply() throws Exception {
        server.enqueue(json(201, """
                {
                  "data": {"id": "p9", "author": "ana", "text": "Hello, feed"},
                  "meta": {"requestId": "req-18"}
                }
                """));
        Post draft = new Post();
        draft.author = "ana";
        draft.text = "Hello, feed";

        Response<Post> response = api.publish(draft).execute();

        assertEquals(201, response.code());
        Post published = response.body();
        assertNotNull(published);
        assertEquals("p9", published.id);
        assertEquals("req-18", envelopes.received.get(0).requestId);
        RecordedRequest request = server.takeRequest(5, TimeUnit.SECONDS);
        assertNotNull(request);
        assertEquals("POST", request.getMethod());
        assertTrue(request.getHeader("Content-Type").startsWith("application/json"));
        JsonObject sent = new Gson().fromJson(request.getBody().readUtf8(), JsonObject.class);
        assertEquals("Hello, feed", sent.getAsJsonObject("data").get("text").getAsString());
        assertEquals("ana", sent.getAsJsonObject("data").get("author").getAsString());
        assertEquals("feed-client/4.2",
                sent.getAsJsonObject("meta").get("client").getAsString());
        assertEquals(List.of("POST /feed/posts"), metering.dispatched);
    }

    @Test
    void leavesPlainEndpointsToTheFactoriesBehindIt() throws Exception {
        server.enqueue(json(200, """
                {"handle": "ana", "followers": 1200}
                """));

        Profile profile = api.profile("ana").execute().body();

        assertNotNull(profile);
        assertEquals("ana", profile.handle);
        assertEquals(1200, profile.followers);
        assertTrue(envelopes.received.isEmpty());
        assertEquals(List.of("GET /feed/users/ana"), metering.dispatched);
    }

    @Test
    void metersEveryCloneThatTheAdaptersBehindItDispatch() throws Exception {
        server.enqueue(json(503, """
                {"error": "busy"}
                """));
        server.enqueue(json(200, """
                {"data": {"id": "p1", "author": "ana", "text": "Retry worked"}, "meta": {}}
                """));
        server.enqueue(json(200, """
                {"data": [], "meta": {"requestId": "req-20"}}
                """));
        server.enqueue(json(200, """
                {"data": [], "meta": {"requestId": "req-21"}}
                """));

        Call<Post> lookup = api.post("p1");
        Response<Post> busy = lookup.execute();
        assertFalse(busy.isSuccessful());
        assertEquals(503, busy.code());
        Response<Post> retried = lookup.clone().execute();
        assertEquals("Retry worked", retried.body().text);

        Single<List<Post>> refresh = api.timeline("c-9");
        assertTrue(refresh.blockingGet().isEmpty());
        assertTrue(refresh.blockingGet().isEmpty());

        assertEquals(List.of("GET /feed/posts/p1", "GET /feed/posts/p1",
                "GET /feed/timeline", "GET /feed/timeline"), metering.dispatched);
        assertEquals(List.of("req-20", "req-21"), envelopes.received.stream()
                .map(meta -> meta.requestId).skip(1).collect(Collectors.toList()));
        assertEquals(4, server.getRequestCount());
    }

    @Test
    void reportsTheSkippedFactoryWhenNothingBehindItCanAdapt() {
        Type unsupported = TypeToken.getParameterized(Iterable.class, String.class).getType();

        IllegalArgumentException direct = assertThrows(IllegalArgumentException.class,
                () -> retrofit.nextCallAdapter(metering, unsupported, new Annotation[0]));
        assertTrue(direct.getMessage().startsWith(
                "Could not locate call adapter for java.lang.Iterable<java.lang.String>."));
        assertTrue(direct.getMessage().contains("Skipped:\n   * "
                + MeteringCallAdapterFactory.class.getName()));
        assertTrue(direct.getMessage().contains(RxJava2CallAdapterFactory.class.getName()));

        IllegalArgumentException declared = assertThrows(IllegalArgumentException.class,
                () -> api.archive("ana"));
        assertTrue(declared.getMessage().startsWith("Unable to create call adapter for "
                + "java.lang.Iterable<" + Post.class.getName() + ">"));
        assertTrue(declared.getMessage().contains("FeedApi.archive"));
        assertNotNull(declared.getCause());
        assertTrue(declared.getCause().getMessage().contains("Skipped:"));
        assertTrue(metering.dispatched.isEmpty());
        assertEquals(0, server.getRequestCount());
    }

    private static MockResponse json(int code, String body) {
        return new MockResponse()
                .setResponseCode(code)
                .addHeader("Content-Type", "application/json")
                .setBody(body);
    }

    /** Marks endpoints and bodies whose payload travels inside the backend's envelope. */
    @Retention(RetentionPolicy.RUNTIME)
    @Target({ElementType.METHOD, ElementType.PARAMETER})
    @interface Enveloped {
    }

    /** The social-feed API as the application declares it. */
    interface FeedApi {

        @Enveloped
        @GET("timeline")
        Single<List<Post>> timeline(@Query("cursor") String cursor);

        @Enveloped
        @POST("posts")
        Call<Post> publish(@Enveloped @Body Post post);

        @Enveloped
        @GET("posts/{id}")
        Call<Post> post(@Path("id") String id);

        @GET("users/{handle}")
        Call<Profile> profile(@Path("handle") String handle);

        @GET("users/{handle}/archive")
        Iterable<Post> archive(@Path("handle") String handle);
    }

    /** The wire envelope around every `@Enveloped` payload. */
    static final class Envelope<T> {
        T data;
        Meta meta;
    }

    /** Envelope metadata: the backend fills the request fields, the client names itself. */
    static final class Meta {
        String requestId;
        String nextCursor;
        String client;
    }

    /** A post in the feed. */
    static final class Post {
        String id;
        String author;
        String text;
    }

    /** A user profile, served without an envelope. */
    static final class Profile {
        String handle;
        int followers;
    }

    /**
     * Unwraps `@Enveloped` responses and wraps `@Enveloped` bodies, leaving the JSON encoding of
     * `Envelope<T>` to whichever converter factory follows it.
     */
    static final class EnvelopeConverterFactory extends Converter.Factory {

        final List<Meta> received = new CopyOnWriteArrayList<>();
        private final String clientName;

        EnvelopeConverterFactory(String clientName) {
            this.clientName = clientName;
        }

        @Override
        public Converter<ResponseBody, ?> responseBodyConverter(
                Type type, Annotation[] annotations, Retrofit retrofit) {
            if (!isEnveloped(annotations)) {
                return null;
            }
            Converter<ResponseBody, Envelope<Object>> delegate =
                    retrofit.nextResponseBodyConverter(this, envelopeOf(type), annotations);
            return body -> {
                Envelope<Object> envelope = delegate.convert(body);
                received.add(envelope.meta);
                return envelope.data;
            };
        }

        @Override
        public Converter<?, RequestBody> requestBodyConverter(Type type,
                Annotation[] parameterAnnotations, Annotation[] methodAnnotations,
                Retrofit retrofit) {
            if (!isEnveloped(parameterAnnotations)) {
                return null;
            }
            Converter<Envelope<Object>, RequestBody> delegate = retrofit.nextRequestBodyConverter(
                    this, envelopeOf(type), parameterAnnotations, methodAnnotations);
            return value -> {
                Envelope<Object> envelope = new Envelope<>();
                envelope.data = value;
                envelope.meta = new Meta();
                envelope.meta.client = clientName;
                return delegate.convert(envelope);
            };
        }

        private static Type envelopeOf(Type payload) {
            return TypeToken.getParameterized(Envelope.class, payload).getType();
        }

        private static boolean isEnveloped(Annotation[] annotations) {
            for (Annotation annotation : annotations) {
                if (annotation instanceof Enveloped) {
                    return true;
                }
            }
            return false;
        }
    }

    /**
     * Records every request dispatched through any service method, then adapts the call with the
     * call adapter factory that follows it.
     */
    static final class MeteringCallAdapterFactory extends CallAdapter.Factory {

        final List<String> dispatched = new CopyOnWriteArrayList<>();

        @Override
        public CallAdapter<?, ?> get(Type returnType, Annotation[] annotations, Retrofit retrofit) {
            @SuppressWarnings("unchecked")
            CallAdapter<Object, Object> delegate = (CallAdapter<Object, Object>)
                    retrofit.nextCallAdapter(this, returnType, annotations);
            return new CallAdapter<Object, Object>() {
                @Override
                public Type responseType() {
                    return delegate.responseType();
                }

                @Override
                public Object adapt(Call<Object> call) {
                    return delegate.adapt(new MeteredCall<>(call));
                }
            };
        }

        /** A call that logs its request each time it is executed or enqueued. */
        final class MeteredCall<T> implements Call<T> {

            private final Call<T> delegate;

            MeteredCall(Call<T> delegate) {
                this.delegate = delegate;
            }

            @Override
            public Response<T> execute() throws IOException {
                record();
                return delegate.execute();
            }

            @Override
            public void enqueue(Callback<T> callback) {
                record();
                delegate.enqueue(callback);
            }

            @Override
            public boolean isExecuted() {
                return delegate.isExecuted();
            }

            @Override
            public void cancel() {
                delegate.cancel();
            }

            @Override
            public boolean isCanceled() {
                return delegate.isCanceled();
            }

            @Override
            public Call<T> clone() {
                return new MeteredCall<>(delegate.clone());
            }

            @Override
            public Request request() {
                return delegate.request();
            }

            private void record() {
                Request request = delegate.request();
                dispatched.add(request.method() + " " + request.url().encodedPath());
            }
        }
    }
}
