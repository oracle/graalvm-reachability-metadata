/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_james.apache_mime4j_core;

import org.apache.james.mime4j.stream.NameValuePair;
import org.apache.james.mime4j.stream.RawBody;
import org.apache.james.mime4j.stream.RawField;
import org.apache.james.mime4j.stream.RawFieldParser;
import org.apache.james.mime4j.util.ContentUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/// Parses MIME header fields so a mail gateway can route multipart messages and attachments.
public class InboundHeaderParameterParsingTest {
    @Test
    @Timeout(60)
    public void parsesContentTypeAndDispositionForMessageRouting() throws Exception {
        RawField contentType = parseField(
                "Content-Type: multipart/alternative; boundary=\"batch-42\"; charset=UTF-8 (generated)");
        RawField contentDisposition = parseField(
                "Content-Disposition: attachment; filename=\"report.txt\"; size=2048");

        assertEquals("Content-Type", contentType.getName());
        assertEquals("content-type", contentType.getNameLowerCase());
        assertEquals("multipart/alternative; boundary=\"batch-42\"; charset=UTF-8 (generated)",
                contentType.getBody());

        RawBody contentTypeBody = RawFieldParser.DEFAULT.parseRawBody(contentType);
        RawBody contentDispositionBody = RawFieldParser.DEFAULT.parseRawBody(contentDisposition);

        assertEquals("multipart/alternative", contentTypeBody.getValue());
        assertEquals(Map.of("boundary", "batch-42", "charset", "UTF-8"),
                parameters(contentTypeBody.getParams()));
        assertEquals("attachment", contentDispositionBody.getValue());
        assertEquals(Map.of("filename", "report.txt", "size", "2048"),
                parameters(contentDispositionBody.getParams()));
    }

    private static RawField parseField(String value) throws Exception {
        return RawFieldParser.DEFAULT.parseField(ContentUtil.encode(value));
    }

    private static Map<String, String> parameters(List<NameValuePair> pairs) {
        Map<String, String> result = new LinkedHashMap<>();
        for (NameValuePair pair : pairs) {
            result.put(pair.getName(), pair.getValue());
        }
        return result;
    }
}
