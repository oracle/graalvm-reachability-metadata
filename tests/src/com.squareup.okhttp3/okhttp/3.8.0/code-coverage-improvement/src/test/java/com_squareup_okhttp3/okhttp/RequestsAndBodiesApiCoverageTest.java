/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_squareup_okhttp3.okhttp;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.net.URL;
import okhttp3.CacheControl;
import okhttp3.FormBody;
import okhttp3.Handshake;
import okhttp3.Headers;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;
import okio.Buffer;
import okio.BufferedSink;
import okio.ByteString;
import org.junit.jupiter.api.Test;

public class RequestsAndBodiesApiCoverageTest {
    private static final MediaType TEXT = MediaType.parse("text/plain; charset=utf-8");

    @Test
    void requestBuildersChooseMethodsHeadersTagsAndUrls() throws Exception {
        RequestBody body = RequestBody.create(TEXT, "payload");
        Request request = new Request.Builder()
                .url("https://example.com/form")
                .addHeader("X-Trace", "first")
                .addHeader("X-Trace", "second")
                .header("X-Trace", "final")
                .cacheControl(new CacheControl.Builder().onlyIfCached().build())
                .tag("request-tag")
                .post(body)
                .build();
        assertThat(request.method()).isEqualTo("POST");
        assertThat(request.url().toString()).isEqualTo("https://example.com/form");
        assertThat(request.isHttps()).isTrue();
        assertThat(request.body()).isSameAs(body);
        assertThat(request.header("X-Trace")).isEqualTo("final");
        assertThat(request.headers("X-Trace")).containsExactly("final");
        assertThat(request.headers().size()).isEqualTo(2);
        assertThat(request.cacheControl().onlyIfCached()).isTrue();
        assertThat(request.tag()).isEqualTo("request-tag");
        assertThat(request.toString()).contains("POST", "example.com");

        Request changed = request.newBuilder()
                .removeHeader("X-Trace")
                .headers(Headers.of("X-New", "yes"))
                .url(new URL("http://example.org/new"))
                .get()
                .build();
        assertThat(changed.method()).isEqualTo("GET");
        assertThat(changed.body()).isNull();
        assertThat(changed.isHttps()).isFalse();
        assertThat(changed.header("X-New")).isEqualTo("yes");

        assertThat(new Request.Builder().url(okhttp3.HttpUrl.parse("http://example.com/"))
                .head().build().method()).isEqualTo("HEAD");
        assertThat(new Request.Builder().url("http://example.com/").delete().build().method())
                .isEqualTo("DELETE");
        assertThat(new Request.Builder().url("http://example.com/").delete(body).build().body())
                .isSameAs(body);
        assertThat(new Request.Builder().url("http://example.com/").put(body).build().method())
                .isEqualTo("PUT");
        assertThat(new Request.Builder().url("http://example.com/").patch(body).build().method())
                .isEqualTo("PATCH");
        assertThat(new Request.Builder().url("http://example.com/")
                .method("CUSTOM", null).build().method()).isEqualTo("CUSTOM");
    }

    @Test
    void formAndMultipartBodiesSerializeTheirPublicRepresentation() throws Exception {
        FormBody form = new FormBody.Builder()
                .add("name", "Alice Smith")
                .addEncoded("already", "one%20two")
                .build();
        assertThat(form.size()).isEqualTo(2);
        assertThat(form.name(0)).isEqualTo("name");
        assertThat(form.value(0)).isEqualTo("Alice Smith");
        assertThat(form.encodedName(0)).isEqualTo("name");
        assertThat(form.encodedValue(0)).isEqualTo("Alice%20Smith");
        assertThat(form.name(1)).isEqualTo("already");
        assertThat(form.value(1)).isEqualTo("one two");
        assertThat(form.contentType().toString()).isEqualTo("application/x-www-form-urlencoded");
        assertThat(form.contentLength()).isGreaterThan(0L);
        Buffer formBuffer = new Buffer();
        form.writeTo(formBuffer);
        assertThat(formBuffer.readUtf8()).isEqualTo("name=Alice%20Smith&already=one%20two");

        RequestBody text = RequestBody.create(TEXT, "part text");
        RequestBody bytes = RequestBody.create(TEXT, ByteString.encodeUtf8("bytes"));
        File file = File.createTempFile("okhttp-api", ".txt");
        java.nio.file.Files.write(file.toPath(), "file".getBytes("UTF-8"));
        RequestBody fileBody = RequestBody.create(TEXT, file);
        assertThat(text.contentLength()).isEqualTo(9L);
        assertThat(bytes.contentLength()).isEqualTo(5L);
        assertThat(fileBody.contentLength()).isEqualTo(4L);
        RequestBody custom = new RequestBody() {
            public MediaType contentType() {
                return TEXT;
            }

            public long contentLength() {
                return 7L;
            }

            public void writeTo(BufferedSink sink) throws java.io.IOException {
                sink.writeUtf8("custom");
            }
        };
        assertThat(custom.contentLength()).isEqualTo(7L);

        RequestBody streaming = new RequestBody() {
            public MediaType contentType() {
                return TEXT;
            }

            public void writeTo(BufferedSink sink) throws java.io.IOException {
                sink.writeUtf8("stream");
            }
        };
        assertThat(streaming.contentLength()).isEqualTo(-1L);

        MultipartBody.Part simplePart = MultipartBody.Part.create(text);
        MultipartBody.Part namedPart = MultipartBody.Part.createFormData("field", "value");
        MultipartBody.Part filePart = MultipartBody.Part.createFormData("upload", "x.txt", fileBody);
        assertThat(simplePart.body()).isSameAs(text);
        assertThat(simplePart.headers()).isNull();
        assertThat(namedPart.headers().get("Content-Disposition")).contains("field");
        assertThat(filePart.headers().get("Content-Disposition")).contains("x.txt");

        MultipartBody defaultMultipart = new MultipartBody.Builder()
                .addFormDataPart("default", "body")
                .build();
        assertThat(defaultMultipart.type()).isEqualTo(MultipartBody.MIXED);

        MultipartBody multipart = new MultipartBody.Builder("fixed-boundary")
                .setType(MultipartBody.FORM)
                .addPart(text)
                .addPart(simplePart)
                .addPart(Headers.of("X-Part", "yes"), bytes)
                .addFormDataPart("message", "hello")
                .addFormDataPart("file", "x.txt", fileBody)
                .build();
        assertThat(multipart.type()).isEqualTo(MultipartBody.FORM);
        assertThat(multipart.boundary()).isEqualTo("fixed-boundary");
        assertThat(multipart.size()).isEqualTo(5);
        assertThat(multipart.part(0).body()).isSameAs(text);
        assertThat(multipart.parts()).containsExactly(multipart.part(0), simplePart, multipart.part(2),
                multipart.part(3), multipart.part(4));
        assertThat(multipart.contentType().toString()).contains("multipart/form-data");
        assertThat(multipart.contentLength()).isGreaterThan(0L);
        Buffer multipartBuffer = new Buffer();
        multipart.writeTo(multipartBuffer);
        assertThat(multipartBuffer.readUtf8()).contains("fixed-boundary", "hello", "X-Part: yes");
        file.delete();
    }

    @Test
    void responseBuildersExposeHeadersBodiesAndStatusSemantics() throws Exception {
        Request request = new Request.Builder().url("https://example.com/").build();
        ResponseBody body = ResponseBody.create(TEXT, "response content");
        Response response = new Response.Builder()
                .request(request)
                .protocol(Protocol.HTTP_1_1)
                .code(401)
                .message("Unauthorized")
                .header("WWW-Authenticate", "Basic realm=\"users\"")
                .addHeader("X-Response", "one")
                .addHeader("X-Response", "two")
                .body(body)
                .sentRequestAtMillis(10L)
                .receivedResponseAtMillis(20L)
                .build();
        assertThat(response.request()).isSameAs(request);
        assertThat(response.protocol()).isEqualTo(Protocol.HTTP_1_1);
        assertThat(response.code()).isEqualTo(401);
        assertThat(response.message()).isEqualTo("Unauthorized");
        assertThat(response.isSuccessful()).isFalse();
        assertThat(response.isRedirect()).isFalse();
        assertThat(response.header("X-Response")).isEqualTo("two");
        assertThat(response.header("Missing", "fallback")).isEqualTo("fallback");
        assertThat(response.headers("X-Response")).containsExactly("one", "two");
        assertThat(response.headers()).isNotNull();
        assertThat(response.challenges()).extracting("scheme").containsExactly("Basic");
        assertThat(response.cacheControl()).isNotNull();
        assertThat(response.body()).isSameAs(body);
        assertThat(response.peekBody(8).string()).isEqualTo("response");
        assertThat(response.sentRequestAtMillis()).isEqualTo(10L);
        assertThat(response.receivedResponseAtMillis()).isEqualTo(20L);
        assertThat(response.toString()).contains("401", "Unauthorized");

        Response empty = new Response.Builder().request(request).protocol(Protocol.HTTP_1_1)
                .code(204).message("No Content").build();
        Handshake handshake = Handshake.get(okhttp3.TlsVersion.TLS_1_2,
                okhttp3.CipherSuite.TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256,
                java.util.Collections.emptyList(), java.util.Collections.emptyList());
        Response linked = response.newBuilder().body(ResponseBody.create(TEXT, "new body"))
                .handshake(handshake).removeHeader("X-Response")
                .networkResponse(empty).cacheResponse(empty).priorResponse(empty).build();
        assertThat(linked.networkResponse()).isSameAs(empty);
        assertThat(linked.handshake()).isSameAs(handshake);
        assertThat(linked.header("X-Response")).isNull();
        assertThat(linked.cacheResponse()).isSameAs(empty);
        assertThat(linked.priorResponse()).isSameAs(empty);
        assertThat(linked.isSuccessful()).isFalse();
        linked.close();

        Response redirect = new Response.Builder().request(request).protocol(Protocol.HTTP_1_1)
                .code(302).message("Found").build();
        assertThat(redirect.isRedirect()).isTrue();
        response.close();
    }

    @Test
    void responseBodiesOfferStreamingAndTextViews() throws Exception {
        ResponseBody responseBody = ResponseBody.create(TEXT, "streamed text");
        assertThat(responseBody.contentType()).isEqualTo(TEXT);
        assertThat(responseBody.contentLength()).isEqualTo(13L);
        assertThat(responseBody.charStream().read()).isEqualTo('s');
        responseBody.close();
        ResponseBody second = ResponseBody.create(TEXT, "streamed text");
        assertThat(second.byteStream().read()).isEqualTo('s');
        second.close();
        ResponseBody bytesBody = ResponseBody.create(TEXT, "streamed text");
        assertThat(bytesBody.bytes()).containsExactly("streamed text".getBytes("UTF-8"));
        bytesBody.close();
        ResponseBody third = ResponseBody.create(TEXT, "streamed text");
        assertThat(third.string()).isEqualTo("streamed text");
        third.close();
    }
}
