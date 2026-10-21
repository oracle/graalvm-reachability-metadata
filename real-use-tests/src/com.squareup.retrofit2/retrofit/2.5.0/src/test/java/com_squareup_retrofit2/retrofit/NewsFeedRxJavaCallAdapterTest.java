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

import io.reactivex.Completable;
import io.reactivex.Maybe;
import io.reactivex.Observable;
import io.reactivex.Single;
import io.reactivex.observers.TestObserver;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
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
import retrofit2.adapter.rxjava2.Result;
import retrofit2.adapter.rxjava2.RxJava2CallAdapterFactory;
import retrofit2.converter.gson.GsonConverterFactory;
import retrofit2.http.DELETE;
import retrofit2.http.GET;
import retrofit2.http.Path;
import retrofit2.http.Query;

/**
 * Call adapters that let service methods return RxJava 2 `Single`, `Observable`, `Maybe`, and
 * `Completable` streams, wrapped as bodies, `Response`s, or `Result`s.
 *
 * A news-reader client loads headlines, pages through a feed, and dismisses stories through a
 * reactive API registered with `RxJava2CallAdapterFactory`. The backend is a `MockWebServer`;
 * the scenario checks the values each stream type emits, how HTTP errors and network failures
 * surface as `HttpException`s or `Result` errors, and the requests Retrofit sends.
 */
public class NewsFeedRxJavaCallAdapterTest {

    private MockWebServer server;
    private NewsApi api;

    @BeforeEach
    void startBackend() throws IOException {
        server = new MockWebServer();
        server.start();
        api = newsApi(RxJava2CallAdapterFactory.create());
    }

    @AfterEach
    void stopBackend() throws IOException {
        server.shutdown();
    }

    @Test
    void loadsTheTopHeadlinesAsASingleBody() throws Exception {
        server.enqueue(json(200, """
                [
                  {"id": "s1", "title": "GraalVM 25 ships", "section": "tech"},
                  {"id": "s2", "title": "Local team wins final", "section": "sports"}
                ]
                """));

        List<Story> headlines = api.headlines(2).blockingGet();

        assertEquals(List.of("GraalVM 25 ships", "Local team wins final"),
                headlines.stream().map(story -> story.title).collect(Collectors.toList()));
        RecordedRequest request = server.takeRequest(5, TimeUnit.SECONDS);
        assertNotNull(request);
        assertEquals("GET", request.getMethod());
        assertEquals("/news/headlines?limit=2", request.getPath());
    }

    @Test
    void readsPagingHeadersFromAnObservableOfResponses() throws Exception {
        server.enqueue(json(200, """
                {"stories": [{"id": "s3", "title": "Markets rally", "section": "business"}]}
                """).addHeader("X-Next-Page", "3"));

        Response<Page> response = api.page("business", 2).blockingFirst();

        assertTrue(response.isSuccessful());
        assertEquals("3", response.headers().get("X-Next-Page"));
        Page page = response.body();
        assertNotNull(page);
        assertEquals(1, page.stories.size());
        assertEquals("Markets rally", page.stories.get(0).title);
        RecordedRequest request = server.takeRequest(5, TimeUnit.SECONDS);
        assertNotNull(request);
        assertEquals("/news/sections/business?page=2", request.getPath());
    }

    @Test
    void signalsAnHttpExceptionWhenTheBackendRejectsTheRequest() throws Exception {
        server.enqueue(json(404, """
                {"error": "unknown section"}
                """));

        Single<Story> story = api.story("missing");
        RuntimeException thrown = assertThrows(RuntimeException.class, story::blockingGet);

        HttpException httpError = (HttpException) thrown;
        assertEquals(404, httpError.code());
        Response<?> response = httpError.response();
        assertNotNull(response);
        assertEquals("{\"error\": \"unknown section\"}", response.errorBody().string().trim());
        assertEquals(1, server.getRequestCount());
    }

    @Test
    void reportsErrorStatusesAndNetworkFailuresAsResults() throws Exception {
        server.enqueue(json(503, """
                {"error": "maintenance"}
                """));
        server.enqueue(new MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START));

        Result<List<Story>> unavailable = api.headlinesResult(5).blockingGet();
        assertFalse(unavailable.isError());
        assertNull(unavailable.error());
        assertEquals(503, unavailable.response().code());
        assertFalse(unavailable.response().isSuccessful());

        Result<List<Story>> offline = api.headlinesResult(5).blockingGet();
        assertTrue(offline.isError());
        assertNull(offline.response());
        assertTrue(offline.error() instanceof IOException);
        assertEquals(2, server.getRequestCount());
    }

    @Test
    void dismissesAStoryThroughACompletable() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(204));
        server.enqueue(json(409, """
                {"error": "already dismissed"}
                """));

        Completable dismiss = api.dismiss("s1");
        dismiss.blockingAwait(5, TimeUnit.SECONDS);
        Throwable conflict = api.dismiss("s1").blockingGet();

        assertTrue(conflict instanceof HttpException);
        assertEquals(409, ((HttpException) conflict).code());
        RecordedRequest first = server.takeRequest(5, TimeUnit.SECONDS);
        assertNotNull(first);
        assertEquals("DELETE", first.getMethod());
        assertEquals("/news/stories/s1/dismissal", first.getPath());
    }

    @Test
    void deliversABookmarkAsynchronouslyThroughAMaybe() throws Exception {
        NewsApi asyncApi = newsApi(RxJava2CallAdapterFactory.createAsync());
        server.enqueue(json(200, """
                {"id": "s2", "title": "Local team wins final", "section": "sports"}
                """));

        Maybe<Story> bookmark = asyncApi.bookmark("reader-7");
        TestObserver<Story> observer = bookmark.test();

        assertTrue(observer.awaitTerminalEvent(10, TimeUnit.SECONDS));
        observer.assertNoErrors();
        observer.assertValueCount(1);
        Story story = observer.values().get(0);
        assertEquals("s2", story.id);
        assertEquals("sports", story.section);
        RecordedRequest request = server.takeRequest(5, TimeUnit.SECONDS);
        assertNotNull(request);
        assertEquals("/news/readers/reader-7/bookmark", request.getPath());
    }

    @Test
    void combinesTwoSectionsIntoOneFrontPage() throws Exception {
        server.enqueue(json(200, """
                {"stories": [{"id": "t1", "title": "Chips get faster", "section": "tech"}]}
                """));
        server.enqueue(json(200, """
                {"stories": [{"id": "w1", "title": "Storm clears", "section": "weather"}]}
                """));

        List<String> frontPage = Observable.concat(
                        api.page("tech", 1).map(response -> response.body().stories),
                        api.page("weather", 1).map(response -> response.body().stories))
                .flatMapIterable(stories -> stories)
                .map(story -> story.section + ": " + story.title)
                .toList()
                .blockingGet();

        assertEquals(List.of("tech: Chips get faster", "weather: Storm clears"), frontPage);
        assertEquals("/news/sections/tech?page=1", server.takeRequest(5, TimeUnit.SECONDS).getPath());
        assertEquals(
                "/news/sections/weather?page=1", server.takeRequest(5, TimeUnit.SECONDS).getPath());
    }

    private NewsApi newsApi(RxJava2CallAdapterFactory adapterFactory) {
        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl(server.url("/news/"))
                .addConverterFactory(GsonConverterFactory.create())
                .addCallAdapterFactory(adapterFactory)
                .build();
        return retrofit.create(NewsApi.class);
    }

    private static MockResponse json(int code, String body) {
        return new MockResponse()
                .setResponseCode(code)
                .addHeader("Content-Type", "application/json")
                .setBody(body);
    }

    /** The reactive news API as the application declares it. */
    interface NewsApi {

        @GET("headlines")
        Single<List<Story>> headlines(@Query("limit") int limit);

        @GET("headlines")
        Single<Result<List<Story>>> headlinesResult(@Query("limit") int limit);

        @GET("sections/{section}")
        Observable<Response<Page>> page(@Path("section") String section, @Query("page") int page);

        @GET("sections/{section}/lead")
        Single<Story> story(@Path("section") String section);

        @GET("readers/{reader}/bookmark")
        Maybe<Story> bookmark(@Path("reader") String reader);

        @DELETE("stories/{id}/dismissal")
        Completable dismiss(@Path("id") String id);
    }

    /** A story as the backend publishes it. */
    static final class Story {
        String id;
        String title;
        String section;
    }

    /** One page of a section feed. */
    static final class Page {
        List<Story> stories;
    }
}
