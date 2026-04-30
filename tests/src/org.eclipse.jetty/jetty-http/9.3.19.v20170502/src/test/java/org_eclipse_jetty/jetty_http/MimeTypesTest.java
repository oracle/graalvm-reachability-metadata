/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_eclipse_jetty.jetty_http;

import org.eclipse.jetty.http.MimeTypes;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class MimeTypesTest {
    @Test
    void loadsDefaultMimeAndEncodingResources() {
        assertThat(MimeTypes.getDefaultMimeByExtension("index.html"))
                .isEqualTo(MimeTypes.Type.TEXT_HTML.asString());
        assertThat(MimeTypes.getDefaultMimeByExtension("IMAGE.PNG"))
                .isEqualTo("image/png");
        assertThat(MimeTypes.getKnownMimeTypes())
                .contains("application/json", "text/css");

        assertThat(MimeTypes.getCharsetInferredFromContentType("text/html"))
                .isEqualTo("utf-8");
        assertThat(MimeTypes.getCharsetAssumedFromContentType("text/json"))
                .isEqualTo("utf-8");
    }

    @Test
    void appliesInstanceMappingsBeforeDefaultResourceMappings() {
        MimeTypes mimeTypes = new MimeTypes();
        mimeTypes.addMimeMapping("HTML", "text/plain;charset=UTF-8");
        mimeTypes.addMimeMapping("custom", "application/vnd.example.Custom");

        assertThat(mimeTypes.getMimeByExtension("page.html"))
                .isEqualTo(MimeTypes.Type.TEXT_PLAIN_UTF_8.asString());
        assertThat(mimeTypes.getMimeByExtension("download.custom"))
                .isEqualTo("application/vnd.example.custom");
        assertThat(mimeTypes.getMimeByExtension("script.js"))
                .isEqualTo("application/javascript");
    }

    @Test
    void parsesAndRemovesCharsetsFromContentTypes() {
        assertThat(MimeTypes.getCharsetFromContentType("text/plain; charset=\"UTF-8\""))
                .isEqualTo("utf-8");
        assertThat(MimeTypes.getContentTypeWithoutCharset("text/plain; charset=UTF-8; boundary=simple"))
                .isEqualTo("text/plain;boundary=simple");
        assertThat(MimeTypes.getContentTypeWithoutCharset("application/json"))
                .isEqualTo("application/json");
    }
}
