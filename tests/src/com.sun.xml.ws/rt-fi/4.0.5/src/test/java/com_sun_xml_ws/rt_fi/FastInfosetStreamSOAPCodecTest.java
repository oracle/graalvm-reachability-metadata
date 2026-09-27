/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_sun_xml_ws.rt_fi;

import com.sun.xml.ws.api.SOAPVersion;
import com.sun.xml.ws.api.pipe.Codecs;
import com.sun.xml.ws.api.pipe.StreamSOAPCodec;
import com.sun.xml.ws.encoding.fastinfoset.FastInfosetMIMETypes;
import com.sun.xml.ws.encoding.fastinfoset.FastInfosetStreamSOAPCodec;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class FastInfosetStreamSOAPCodecTest {
    @Test
    void createsStatelessSoapCodecsForBothProtocolVersions() {
        FastInfosetStreamSOAPCodec soap11 = createCodec(SOAPVersion.SOAP_11, false);
        FastInfosetStreamSOAPCodec soap12 = createCodec(SOAPVersion.SOAP_12, false);

        assertTrue(
                FastInfosetMIMETypes.SOAP_11.equals(soap11.getMimeType()),
                "SOAP 1.1 must use its stateless Fast Infoset MIME type");
        assertTrue(
                FastInfosetMIMETypes.SOAP_12.equals(soap12.getMimeType()),
                "SOAP 1.2 must use its stateless Fast Infoset MIME type");
    }

    @Test
    void createsStatefulSoapCodecsForBothProtocolVersions() {
        FastInfosetStreamSOAPCodec soap11 = createCodec(SOAPVersion.SOAP_11, true);
        FastInfosetStreamSOAPCodec soap12 = createCodec(SOAPVersion.SOAP_12, true);

        assertTrue(
                FastInfosetMIMETypes.STATEFUL_SOAP_11.equals(soap11.getMimeType()),
                "SOAP 1.1 must use its stateful Fast Infoset MIME type");
        assertTrue(
                FastInfosetMIMETypes.STATEFUL_SOAP_12.equals(soap12.getMimeType()),
                "SOAP 1.2 must use its stateful Fast Infoset MIME type");
    }

    private static FastInfosetStreamSOAPCodec createCodec(SOAPVersion version, boolean retainState) {
        StreamSOAPCodec xmlCodec = Codecs.createSOAPEnvelopeXmlCodec(version);
        return FastInfosetStreamSOAPCodec.create(xmlCodec, version, retainState);
    }
}
