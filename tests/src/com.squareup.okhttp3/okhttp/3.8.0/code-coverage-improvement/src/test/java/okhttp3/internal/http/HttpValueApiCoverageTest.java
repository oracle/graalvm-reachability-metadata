/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package okhttp3.internal.http;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.Proxy;
import java.util.Collections;
import okhttp3.CookieJar;
import okhttp3.Headers;
import okhttp3.OkHttpClient;
import okhttp3.HttpUrl;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import okio.Buffer;
import org.junit.jupiter.api.Test;

public class HttpValueApiCoverageTest {
    @Test
    void headersAndMethodsApplyHttpSemantics() {
        Headers headers = new Headers.Builder()
                .add("Content-Length", "12")
                .add("Vary", "Accept-Encoding, Cookie")
                .add("Vary", "X-Token")
                .build();
        assertThat(HttpHeaders.contentLength(headers)).isEqualTo(12L);
        assertThat(HttpHeaders.varyFields(headers)).containsExactlyInAnyOrder(
                "accept-encoding", "cookie", "x-token");
        assertThat(HttpHeaders.hasVaryAll(new Headers.Builder().add("Vary", "*").build()))
                .isTrue();
        assertThat(HttpHeaders.hasVaryAll(headers)).isFalse();

        Request request = new Request.Builder().url("https://example.com/a?q=1").build();
        Request networkRequest = new Request.Builder().url(request.url())
                .headers(Headers.of("Accept-Encoding", "gzip", "Cookie", "a=b", "X-Token", "yes"))
                .build();
        Response networkResponse = new Response.Builder().request(networkRequest)
                .protocol(Protocol.HTTP_1_1).code(200).message("OK").build();
        Response response = new Response.Builder().request(request).protocol(Protocol.HTTP_1_1)
                .code(200).message("OK").headers(headers).networkResponse(networkResponse)
                .body(ResponseBody.create(null, "body")).build();
        Response varyAllResponse = new Response.Builder().request(request)
                .protocol(Protocol.HTTP_1_1).code(200).message("OK")
                .header("Vary", "*").build();
        assertThat(HttpHeaders.hasVaryAll(varyAllResponse)).isTrue();
        assertThat(okhttp3.internal.cache.CacheStrategy.isCacheable(response, request)).isTrue();
        Request noStore = request.newBuilder().header("Cache-Control", "no-store").build();
        assertThat(okhttp3.internal.cache.CacheStrategy.isCacheable(response, noStore)).isFalse();
        assertThat(HttpHeaders.contentLength(response)).isEqualTo(12L);
        assertThat(HttpHeaders.hasBody(response)).isTrue();
        assertThat(HttpHeaders.varyHeaders(response).names()).containsExactly(
                "Accept-Encoding", "Cookie", "X-Token");
        assertThat(HttpHeaders.varyMatches(response, headers, request)).isTrue();
        assertThat(HttpHeaders.varyMatches(response, Headers.of("Vary", "Accept-Encoding"),
                request)).isTrue();

        Headers requestHeaders = new Headers.Builder().add("Accept-Encoding", "gzip")
                .add("Cookie", "a=b").add("X-Token", "yes").build();
        Headers responseHeaders = new Headers.Builder().add("Vary", "Accept-Encoding, Cookie")
                .build();
        assertThat(HttpHeaders.varyHeaders(requestHeaders, responseHeaders))
                .isEqualTo(new Headers.Builder().add("Accept-Encoding", "gzip")
                        .add("Cookie", "a=b").build());

        assertThat(HttpMethod.invalidatesCache("POST")).isTrue();
        assertThat(HttpMethod.invalidatesCache("GET")).isFalse();
        assertThat(HttpMethod.redirectsToGet("POST")).isTrue();
        assertThat(HttpMethod.redirectsToGet("PROPFIND")).isFalse();
        assertThat(HttpMethod.redirectsWithBody("PROPFIND")).isTrue();
        assertThat(HttpMethod.redirectsWithBody("GET")).isFalse();
        assertThat(HttpHeaders.parseChallenges(
                Headers.of("WWW-Authenticate", "Basic realm=\"users\""), "WWW-Authenticate"))
                .hasSize(1);
        java.util.concurrent.atomic.AtomicInteger savedCookies = new java.util.concurrent.atomic.AtomicInteger();
        CookieJar recordingCookies = new CookieJar() {
            public void saveFromResponse(okhttp3.HttpUrl cookieUrl,
                    java.util.List<okhttp3.Cookie> cookies) {
                savedCookies.addAndGet(cookies.size());
            }

            public java.util.List<okhttp3.Cookie> loadForRequest(okhttp3.HttpUrl cookieUrl) {
                return Collections.emptyList();
            }
        };
        HttpHeaders.receiveHeaders(recordingCookies, request.url(),
                Headers.of("Set-Cookie", "a=b; Path=/"));
        assertThat(savedCookies).hasValue(1);
    }

    @Test
    void requestLinesStatusLinesAndResponseBodiesRepresentWireValues() throws Exception {
        HttpUrl url = HttpUrl.parse("http://example.com/a%20b?q=1");
        Request request = new Request.Builder().url(url).method("GET", null).build();
        assertThat(RequestLine.requestPath(url)).isEqualTo("/a%20b?q=1");
        assertThat(RequestLine.get(request, Proxy.Type.DIRECT)).isEqualTo("GET /a%20b?q=1 HTTP/1.1");
        assertThat(RequestLine.get(request, Proxy.Type.HTTP)).isEqualTo("GET http://example.com/a%20b?q=1 HTTP/1.1");

        StatusLine status = StatusLine.parse("HTTP/1.1 418 I'm a teapot");
        assertThat(status.protocol).isEqualTo(Protocol.HTTP_1_1);
        assertThat(status.code).isEqualTo(418);
        assertThat(status.message).isEqualTo("I'm a teapot");
        assertThat(status.toString()).isEqualTo("HTTP/1.1 418 I'm a teapot");
        Response response = new Response.Builder().request(request).protocol(Protocol.HTTP_1_1)
                .code(204).message("No Content").build();
        assertThat(StatusLine.get(response).toString()).isEqualTo("HTTP/1.1 204 No Content");
        assertThat(new StatusLine(Protocol.HTTP_2, 200, "OK").toString()).isEqualTo("HTTP/1.1 200 OK");

        Buffer source = new Buffer().writeUtf8("payload");
        RealResponseBody body = new RealResponseBody(Headers.of("Content-Type", "text/plain"), source);
        assertThat(body.contentType().toString()).isEqualTo("text/plain");
        assertThat(body.contentLength()).isEqualTo(-1L);
        assertThat(body.source()).isSameAs(source);
        assertThat(body.string()).isEqualTo("payload");
    }

    @Test
    void interceptorChainExposesItsUserFacingRequestAndDelegates() throws Exception {
        Request request = new Request.Builder().url("http://example.com/").build();
        Response expected = new Response.Builder().request(request).protocol(Protocol.HTTP_1_1)
                .code(200).message("OK").build();
        RealInterceptorChain chain = new RealInterceptorChain(
                Collections.singletonList(interceptorChain -> {
                    assertThat(interceptorChain.request()).isSameAs(request);
                    assertThat(interceptorChain.connection()).isNull();
                    return expected;
                }), null, null, null, 0, request);
        assertThat(chain.request()).isSameAs(request);
        assertThat(chain.connection()).isNull();
        assertThat(chain.httpStream()).isNull();
        assertThat(chain.proceed(request)).isSameAs(expected);
    }

    @Test
    void retryInterceptorReportsCancellationAndAllocationState() {
        okhttp3.internal.http.RetryAndFollowUpInterceptor interceptor =
                new okhttp3.internal.http.RetryAndFollowUpInterceptor(new OkHttpClient(), false);
        assertThat(interceptor.isCanceled()).isFalse();
        assertThat(interceptor.streamAllocation()).isNull();
        interceptor.cancel();
        assertThat(interceptor.isCanceled()).isTrue();
        assertThat(interceptor.streamAllocation()).isNull();
    }

    private static okhttp3.Interceptor interceptor(okhttp3.Response response) {
        return chain -> response;
    }
}
