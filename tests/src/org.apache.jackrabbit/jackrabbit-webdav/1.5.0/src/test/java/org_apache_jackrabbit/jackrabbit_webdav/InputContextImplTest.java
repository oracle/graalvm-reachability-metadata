/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_jackrabbit.jackrabbit_webdav;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.io.ByteArrayInputStream;
import java.io.InputStream;

import org.apache.jackrabbit.webdav.DavConstants;
import org.apache.jackrabbit.webdav.io.InputContextImpl;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

public class InputContextImplTest {
    @Test
    public void constructorStoresRequestHeadersAndInputStream() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(DavConstants.HEADER_CONTENT_LANGUAGE, "en-US");
        request.addHeader(DavConstants.HEADER_CONTENT_LENGTH, "11");
        request.addHeader(DavConstants.HEADER_CONTENT_TYPE, "text/plain;charset=UTF-8");
        request.addHeader("X-Coverage-Header", "header-value");
        InputStream inputStream = new ByteArrayInputStream("hello world".getBytes());

        InputContextImpl context = new InputContextImpl(request, inputStream);

        assertThat(context.hasStream()).isTrue();
        assertThat(context.getInputStream()).isSameAs(inputStream);
        assertThat(context.getContentLanguage()).isEqualTo("en-US");
        assertThat(context.getContentLength()).isEqualTo(11L);
        assertThat(context.getContentType()).isEqualTo("text/plain;charset=UTF-8");
        assertThat(context.getProperty("X-Coverage-Header")).isEqualTo("header-value");
        assertThat(context.getModificationTime()).isPositive();
    }

    @Test
    public void constructorRequiresRequestButAllowsMissingStream() {
        MockHttpServletRequest request = new MockHttpServletRequest();

        InputContextImpl context = new InputContextImpl(request, null);

        assertThat(context.hasStream()).isFalse();
        assertThat(context.getInputStream()).isNull();
        assertThatIllegalArgumentException().isThrownBy(() -> new InputContextImpl(null, null));
    }
}
