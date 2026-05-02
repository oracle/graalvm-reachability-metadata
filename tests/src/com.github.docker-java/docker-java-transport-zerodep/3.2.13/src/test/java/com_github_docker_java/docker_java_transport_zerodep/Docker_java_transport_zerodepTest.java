/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_github_docker_java.docker_java_transport_zerodep;

import com.github.dockerjava.zerodep.shaded.org.apache.commons.codec.language.DaitchMokotoffSoundex;
import com.github.dockerjava.zerodep.shaded.org.apache.hc.client5.http.impl.auth.BasicAuthCache;
import com.github.dockerjava.zerodep.shaded.org.apache.hc.client5.http.impl.auth.BasicScheme;
import com.github.dockerjava.zerodep.shaded.org.apache.hc.client5.http.auth.AuthScheme;
import com.github.dockerjava.zerodep.shaded.org.apache.hc.client5.http.auth.UsernamePasswordCredentials;
import com.github.dockerjava.zerodep.shaded.org.apache.hc.client5.http.protocol.HttpClientContext;
import com.github.dockerjava.zerodep.shaded.org.apache.hc.core5.http.ContentType;
import com.github.dockerjava.zerodep.shaded.org.apache.hc.core5.http.HttpHost;
import com.github.dockerjava.zerodep.shaded.org.apache.hc.core5.http.io.entity.SerializableEntity;
import com.github.dockerjava.zerodep.shaded.org.apache.hc.core5.http.message.BasicClassicHttpRequest;
import com.github.dockerjava.zerodep.shaded.org.apache.hc.core5.util.VersionInfo;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;

import static org.assertj.core.api.Assertions.assertThat;

class Docker_java_transport_zerodepTest {
    private static final HttpHost HTTP_HOST = new HttpHost("https", "example.com");
    private static final BasicClassicHttpRequest REQUEST = new BasicClassicHttpRequest("GET", "/_ping");
    private static final HttpClientContext HTTP_CLIENT_CONTEXT = HttpClientContext.create();

    @Test
    void cachesBasicSchemeBySerializingIt() throws Exception {
        final BasicScheme scheme = new BasicScheme();
        scheme.initPreemptive(new UsernamePasswordCredentials("docker", "java".toCharArray()));

        final BasicAuthCache authCache = new BasicAuthCache();
        authCache.put(HTTP_HOST, scheme);

        final AuthScheme cachedScheme = authCache.get(HTTP_HOST);

        assertThat(cachedScheme).isInstanceOf(BasicScheme.class);
        assertThat(cachedScheme.generateAuthResponse(HTTP_HOST, REQUEST, HTTP_CLIENT_CONTEXT))
                .isEqualTo("Basic ZG9ja2VyOmphdmE=");
    }

    @Test
    void serializableEntityWritesSerializablePayload() throws Exception {
        final BasicScheme scheme = new BasicScheme();
        scheme.initPreemptive(new UsernamePasswordCredentials("native", "image".toCharArray()));
        final SerializableEntity entity = new SerializableEntity(scheme, ContentType.APPLICATION_OCTET_STREAM);

        final ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        entity.writeTo(outputStream);

        assertThat(outputStream.toByteArray()).startsWith((byte) 0xAC, (byte) 0xED);
        assertThat(outputStream.size()).isGreaterThan(0);
    }

    @Test
    void loadsVersionInfoFromShadedHttpCoreResources() {
        final VersionInfo versionInfo = VersionInfo.loadVersionInfo(
                "com.github.dockerjava.zerodep.shaded.org.apache.hc.core5",
                VersionInfo.class.getClassLoader()
        );

        assertThat(versionInfo).isNotNull();
        assertThat(versionInfo.getModule()).isEqualTo("httpcore5");
        assertThat(versionInfo.getRelease()).isNotEqualTo(VersionInfo.UNAVAILABLE);
    }

    @Test
    void loadsCodecResourcesWhenEncodingDaitchMokotoffSoundex() {
        final String encoded = new DaitchMokotoffSoundex().encode("Schneider");

        assertThat(encoded).matches("(\\d{6})(\\|\\d{6})*");
    }
}
