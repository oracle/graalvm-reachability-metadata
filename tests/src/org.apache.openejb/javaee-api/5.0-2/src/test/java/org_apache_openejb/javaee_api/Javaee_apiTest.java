/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_openejb.javaee_api;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import javax.mail.Flags;
import javax.mail.internet.ContentType;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimeMultipart;
import javax.mail.util.ByteArrayDataSource;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class Javaee_apiTest {
    @Test
    void parsesAndFormatsInternetAddresses() throws Exception {
        InternetAddress[] addresses = InternetAddress.parse("Alice Example <alice@example.com>, bob@example.com");

        assertThat(addresses).hasSize(2);
        assertThat(addresses[0].getAddress()).isEqualTo("alice@example.com");
        assertThat(addresses[0].getPersonal()).isEqualTo("Alice Example");
        assertThat(addresses[1].getAddress()).isEqualTo("bob@example.com");
        assertThat(addresses[1].getPersonal()).isNull();

        addresses[0].validate();
        addresses[1].validate();

        assertThat(InternetAddress.toString(addresses)).isEqualTo("Alice Example <alice@example.com>, bob@example.com");
    }

    @Test
    void parsesContentTypeParametersAndWildcardMatches() throws Exception {
        ContentType contentType = new ContentType("text/plain; charset=UTF-8; format=flowed");

        assertThat(contentType.getPrimaryType()).isEqualTo("text");
        assertThat(contentType.getSubType()).isEqualTo("plain");
        assertThat(contentType.getBaseType()).isEqualTo("text/plain");
        assertThat(contentType.getParameter("charset")).isEqualTo("UTF-8");
        assertThat(contentType.getParameter("format")).isEqualTo("flowed");
        assertThat(contentType.match("text/*")).isTrue();
        assertThat(contentType.match("application/json")).isFalse();

        contentType.setParameter("delsp", "yes");

        assertThat(contentType.toString())
                .contains("text/plain")
                .contains("charset=UTF-8")
                .contains("format=flowed")
                .contains("delsp=yes");
    }

    @Test
    void managesSystemAndUserMailFlags() {
        Flags flags = new Flags(Flags.Flag.ANSWERED);
        flags.add("custom");

        assertThat(flags.contains(Flags.Flag.ANSWERED)).isTrue();
        assertThat(flags.contains("custom")).isTrue();

        Flags expected = new Flags();
        expected.add(Flags.Flag.ANSWERED);
        expected.add("custom");

        assertThat(flags).isEqualTo(expected);

        flags.remove("custom");

        assertThat(flags.contains("custom")).isFalse();
        assertThat(flags.getSystemFlags()).containsExactly(Flags.Flag.ANSWERED);
        assertThat(flags.getUserFlags()).isEmpty();
    }

    @Test
    void parsesMultipartBodyParts() throws Exception {
        String boundary = "demo-boundary";
        String contentType = "multipart/alternative; boundary=\"" + boundary + "\"";
        String rawMultipart = "Generated message body\r\n"
                + "--" + boundary + "\r\n"
                + "Content-Type: text/plain; charset=UTF-8\r\n"
                + "Content-ID: <plain-text>\r\n"
                + "\r\n"
                + "Hello native image\r\n"
                + "--" + boundary + "\r\n"
                + "Content-Type: text/plain; charset=UTF-8\r\n"
                + "Content-ID: <fallback>\r\n"
                + "\r\n"
                + "Hello JVM\r\n"
                + "--" + boundary + "--\r\n";

        MimeMultipart parsedMultipart = new MimeMultipart(
                new ByteArrayDataSource(rawMultipart.getBytes(StandardCharsets.UTF_8), contentType));
        MimeBodyPart parsedPlainTextPart = (MimeBodyPart) parsedMultipart.getBodyPart(0);
        MimeBodyPart parsedFallbackPart = (MimeBodyPart) parsedMultipart.getBodyPart(1);

        assertThat(parsedMultipart.getPreamble()).contains("Generated message body");
        assertThat(parsedMultipart.getCount()).isEqualTo(2);
        assertThat(parsedPlainTextPart.getContentID()).isEqualTo("<plain-text>");
        assertThat(parsedFallbackPart.getContentID()).isEqualTo("<fallback>");
        assertThat(parsedPlainTextPart.getContentType()).isEqualTo("text/plain; charset=UTF-8");
        assertThat(parsedFallbackPart.getContentType()).isEqualTo("text/plain; charset=UTF-8");
        assertThat(readUtf8(parsedPlainTextPart.getInputStream())).isEqualTo("Hello native image");
        assertThat(readUtf8(parsedFallbackPart.getInputStream())).isEqualTo("Hello JVM");
    }

    private static String readUtf8(InputStream inputStream) throws IOException {
        return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
    }
}
