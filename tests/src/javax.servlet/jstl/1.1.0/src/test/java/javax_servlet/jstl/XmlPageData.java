/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package javax_servlet.jstl;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import javax.servlet.jsp.tagext.PageData;

final class XmlPageData extends PageData {
    private final byte[] content;

    XmlPageData(String xml) {
        content = xml.getBytes(StandardCharsets.UTF_8);
    }

    @Override
    public InputStream getInputStream() {
        return new ByteArrayInputStream(content);
    }
}
