/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_jackrabbit.jackrabbit_webdav;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.io.ByteArrayOutputStream;
import java.io.OutputStream;

import org.apache.jackrabbit.webdav.DavConstants;
import org.apache.jackrabbit.webdav.io.OutputContextImpl;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;

public class OutputContextImplTest {
    @Test
    public void constructorStoresResponseAndOutputStream() {
        MockHttpServletResponse response = new MockHttpServletResponse();
        OutputStream outputStream = new ByteArrayOutputStream();

        OutputContextImpl context = new OutputContextImpl(response, outputStream);

        assertThat(context.hasStream()).isTrue();
        assertThat(context.getOutputStream()).isSameAs(outputStream);
    }

    @Test
    public void constructorRequiresResponseButAllowsMissingStream() {
        MockHttpServletResponse response = new MockHttpServletResponse();

        OutputContextImpl context = new OutputContextImpl(response, null);

        assertThat(context.hasStream()).isFalse();
        assertThat(context.getOutputStream()).isNull();
        assertThatIllegalArgumentException().isThrownBy(() -> new OutputContextImpl(null, null));
    }

    @Test
    public void settersWriteWebdavHeadersToResponse() {
        MockHttpServletResponse response = new MockHttpServletResponse();
        OutputContextImpl context = new OutputContextImpl(response, new ByteArrayOutputStream());

        context.setContentLanguage("en-US");
        context.setContentLength(11L);
        context.setContentType("text/plain;charset=UTF-8");
        context.setModificationTime(1_700_000_000_000L);
        context.setETag("\"etag-value\"");
        context.setProperty("X-Coverage-Header", "header-value");

        assertThat(response.getHeader(DavConstants.HEADER_CONTENT_LANGUAGE)).isEqualTo("en-US");
        assertThat(response.getContentLength()).isEqualTo(11);
        assertThat(response.getContentType()).isEqualTo("text/plain;charset=UTF-8");
        assertThat(response.getHeader(DavConstants.HEADER_LAST_MODIFIED)).isNotNull();
        assertThat(response.getHeader(DavConstants.HEADER_ETAG)).isEqualTo("\"etag-value\"");
        assertThat(response.getHeader("X-Coverage-Header")).isEqualTo("header-value");
    }

    @Test
    public void settersIgnoreNullAndNegativeValues() {
        MockHttpServletResponse response = new MockHttpServletResponse();
        OutputContextImpl context = new OutputContextImpl(response, new ByteArrayOutputStream());

        context.setContentLanguage(null);
        context.setContentLength(-1L);
        context.setContentType(null);
        context.setModificationTime(-1L);
        context.setETag(null);
        context.setProperty("X-Null-Value", null);
        context.setProperty(null, "value");

        assertThat(response.getHeader(DavConstants.HEADER_CONTENT_LANGUAGE)).isNull();
        assertThat(response.getContentLength()).isEqualTo(0);
        assertThat(response.getContentType()).isNull();
        assertThat(response.getHeader(DavConstants.HEADER_LAST_MODIFIED)).isNull();
        assertThat(response.getHeader(DavConstants.HEADER_ETAG)).isNull();
        assertThat(response.getHeader("X-Null-Value")).isNull();
    }
}
