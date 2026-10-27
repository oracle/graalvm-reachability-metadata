/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package isorelax.isorelax;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.StringReader;
import org.iso_relax.verifier.Schema;
import org.iso_relax.verifier.Verifier;
import org.iso_relax.verifier.VerifierFactory;
import org.junit.jupiter.api.Test;
import org.xml.sax.InputSource;
import org.xml.sax.helpers.DefaultHandler;

public class VerifierFactoryTest {
    private static final String RELAX_NG = "http://relaxng.org/ns/structure/1.0";
    private static final String SCHEMA =
            """
            <element name="message" xmlns="http://relaxng.org/ns/structure/1.0">
              <element name="body"><text/></element>
            </element>
            """;

    @Test
    void discoversProviderAndValidatesDocuments() throws Exception {
        VerifierFactory factory = VerifierFactory.newInstance(RELAX_NG);
        Schema schema = factory.compileSchema(inputSource(SCHEMA));

        Verifier validDocumentVerifier = schema.newVerifier();
        assertThat(validDocumentVerifier.verify(inputSource("<message><body>Hello</body></message>")))
                .isTrue();

        Verifier invalidDocumentVerifier = schema.newVerifier();
        invalidDocumentVerifier.setErrorHandler(new DefaultHandler());
        assertThat(invalidDocumentVerifier.verify(inputSource("<message><title>Hello</title></message>")))
                .isFalse();
    }

    private static InputSource inputSource(String xml) {
        return new InputSource(new StringReader(xml));
    }
}
