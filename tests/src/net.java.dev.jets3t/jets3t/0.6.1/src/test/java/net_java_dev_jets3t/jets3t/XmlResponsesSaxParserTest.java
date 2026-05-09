/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package net_java_dev_jets3t.jets3t;

import java.io.ByteArrayInputStream;
import java.io.InputStream;

import org.jets3t.service.Constants;
import org.jets3t.service.impl.rest.XmlResponsesSaxParser;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class XmlResponsesSaxParserTest {
    @Test
    public void parsesBucketLocationResponse() throws Exception {
        XmlResponsesSaxParser parser = new XmlResponsesSaxParser();

        String location = parser.parseBucketLocationResponse(xml("""
            <CreateBucketConfiguration xmlns="http://s3.amazonaws.com/doc/2006-03-01/"><LocationConstraint>EU</LocationConstraint></CreateBucketConfiguration>
            """));

        assertThat(location).isEqualTo("EU");
    }

    private static InputStream xml(String document) throws Exception {
        return new ByteArrayInputStream(document.getBytes(Constants.DEFAULT_ENCODING));
    }
}
