/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_james.apache_mime4j_core;

import org.apache.james.mime4j.util.MimeParameterMapping;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/// Resolves international attachment filenames from MIME parameter continuations.
public class InboundAttachmentFilenameParametersTest {
    @Test
    @Timeout(60)
    public void resolvesAttachmentFilenameAndMetadata() {
        MimeParameterMapping parameters = new MimeParameterMapping();
        parameters.addParameter("filename*0*", "UTF-8''quarterly%20report%20");
        parameters.addParameter("filename*1*", "%E2%80%93Q4.csv");
        parameters.addParameter("size", "2048");

        assertEquals("quarterly report \u2013Q4.csv", parameters.get("filename"));
        assertEquals("2048", parameters.get("size"));
        assertEquals(Map.of("filename", "quarterly report \u2013Q4.csv", "size", "2048"),
                parameters.getParameters());
    }
}
