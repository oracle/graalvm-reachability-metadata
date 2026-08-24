/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_squareup_okhttp3.okhttp;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.URL;
import okhttp3.HttpUrl;
import org.junit.jupiter.api.Test;

public class HttpUrlApiCoverageTest {
    @Test
    void parsesAndExposesEveryUrlComponent() throws Exception {
        HttpUrl url = HttpUrl.parse(
                "https://alice:secret@example.com:8443/a%20b/c/?q=one&q=two&empty=#frag");

        assertThat(url.scheme()).isEqualTo("https");
        assertThat(url.isHttps()).isTrue();
        assertThat(url.username()).isEqualTo("alice");
        assertThat(url.encodedUsername()).isEqualTo("alice");
        assertThat(url.password()).isEqualTo("secret");
        assertThat(url.encodedPassword()).isEqualTo("secret");
        assertThat(url.host()).isEqualTo("example.com");
        assertThat(url.port()).isEqualTo(8443);
        assertThat(url.pathSegments()).containsExactly("a b", "c", "");
        assertThat(url.encodedPathSegments()).containsExactly("a%20b", "c", "");
        assertThat(url.pathSize()).isEqualTo(3);
        assertThat(url.query()).isEqualTo("q=one&q=two&empty=");
        assertThat(url.encodedQuery()).isEqualTo("q=one&q=two&empty=");
        assertThat(url.querySize()).isEqualTo(3);
        assertThat(url.queryParameter("q")).isEqualTo("one");
        assertThat(url.queryParameterNames()).containsExactly("q", "empty");
        assertThat(url.queryParameterName(1)).isEqualTo("q");
        assertThat(url.queryParameterValue(1)).isEqualTo("two");
        assertThat(url.queryParameterValues("q")).containsExactly("one", "two");
        assertThat(url.fragment()).isEqualTo("frag");
        assertThat(url.encodedFragment()).isEqualTo("frag");
        assertThat(url.topPrivateDomain()).isEqualTo("example.com");
        assertThat(url.redact()).isEqualTo("https://example.com:8443/...");
        assertThat(url.url().toString()).isEqualTo(url.toString());
        assertThat(url.uri().toString()).isEqualTo(url.toString());
        assertThat(HttpUrl.get(new URL(url.toString()))).isEqualTo(url);
        assertThat(HttpUrl.get(new URI(url.toString()))).isEqualTo(url);
        assertThat(HttpUrl.defaultPort("http")).isEqualTo(80);
        assertThat(HttpUrl.defaultPort("https")).isEqualTo(443);
        assertThat(url).isEqualTo(HttpUrl.parse(url.toString()));
        assertThat(url.hashCode()).isEqualTo(HttpUrl.parse(url.toString()).hashCode());
    }

    @Test
    void buildsAndEditsEncodedAndDecodedComponents() {
        HttpUrl.Builder builder = new HttpUrl.Builder()
                .scheme("https")
                .encodedUsername("a%20b")
                .encodedPassword("p%2Fq")
                .host("example.com")
                .port(9443)
                .encodedPath("/one/two")
                .encodedQuery("a=1&a=2&remove=x")
                .fragment("part one");
        assertThat(builder.toString()).contains("a%20b:p%2Fq@example.com:9443");

        builder.encodedFragment("encoded%20fragment");
        HttpUrl encoded = builder.build();
        assertThat(encoded.username()).isEqualTo("a b");
        assertThat(encoded.encodedFragment()).isEqualTo("encoded%20fragment");
        assertThat(encoded.newBuilder().query("raw=1").build().query()).isEqualTo("raw=1");
        assertThat(encoded.password()).isEqualTo("p/q");
        assertThat(encoded.fragment()).isEqualTo("encoded fragment");

        HttpUrl edited = encoded.newBuilder()
                .addPathSegment("three/four")
                .addEncodedPathSegment("five%20six")
                .addPathSegments("seven/eight")
                .addEncodedPathSegments("nine/ten")
                .addQueryParameter("new name", "new value")
                .addEncodedQueryParameter("encoded%20name", "encoded%20value")
                .setQueryParameter("new name", "replacement")
                .setEncodedQueryParameter("encoded%20name", "replacement%20value")
                .removeAllQueryParameters("remove")
                .removeAllEncodedQueryParameters("encoded%20name")
                .setPathSegment(0, "root")
                .setEncodedPathSegment(1, "changed%20segment")
                .removePathSegment(7)
                .build();
        assertThat(edited.pathSegments()).contains("root", "changed segment", "seven", "eight");
        assertThat(edited.queryParameter("new name")).isEqualTo("replacement");
        assertThat(edited.queryParameter("remove")).isNull();

        HttpUrl rebuilt = edited.newBuilder("../next?x=1#done").build();
        assertThat(rebuilt.toString()).contains("/next?x=1#done");
        assertThat(edited.resolve("../other").toString()).contains("/other");
    }

}
