/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_sun_xml_ws.rt_fi;

import com.sun.xml.ws.api.SOAPVersion;
import com.sun.xml.ws.api.pipe.Codec;
import com.sun.xml.ws.api.pipe.Codecs;
import com.sun.xml.ws.api.pipe.StreamSOAPCodec;
import com.sun.xml.ws.encoding.fastinfoset.FastInfosetMIMETypes;
import com.sun.xml.ws.encoding.fastinfoset.FastInfosetStreamSOAPCodec;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;

public class FastInfosetStreamSOAPCodecTest {
    @Test
    void createsFastInfosetStreamingSoapCodecs() {
        StreamSOAPCodec soap11XmlCodec = Codecs.createSOAPEnvelopeXmlCodec(SOAPVersion.SOAP_11);
        StreamSOAPCodec soap12XmlCodec = Codecs.createSOAPEnvelopeXmlCodec(SOAPVersion.SOAP_12);
        FastInfosetStreamSOAPCodec soap11Codec =
                FastInfosetStreamSOAPCodec.create(soap11XmlCodec, SOAPVersion.SOAP_11);
        FastInfosetStreamSOAPCodec soap12Codec =
                FastInfosetStreamSOAPCodec.create(soap12XmlCodec, SOAPVersion.SOAP_12);
        FastInfosetStreamSOAPCodec statefulSoap12Codec =
                FastInfosetStreamSOAPCodec.create(soap12XmlCodec, SOAPVersion.SOAP_12, true);

        assertEquals(FastInfosetMIMETypes.SOAP_11, soap11Codec.getMimeType());
        assertEquals(FastInfosetMIMETypes.SOAP_12, soap12Codec.getMimeType());
        assertEquals(FastInfosetMIMETypes.STATEFUL_SOAP_12, statefulSoap12Codec.getMimeType());

        Codec copy = statefulSoap12Codec.copy();
        assertNotSame(statefulSoap12Codec, copy);
        assertEquals(FastInfosetMIMETypes.STATEFUL_SOAP_12, copy.getMimeType());
    }

    @Test
    void createsStatefulSoap11Codec() {
        StreamSOAPCodec soap11XmlCodec = Codecs.createSOAPEnvelopeXmlCodec(SOAPVersion.SOAP_11);
        FastInfosetStreamSOAPCodec statefulSoap11Codec =
                FastInfosetStreamSOAPCodec.create(soap11XmlCodec, SOAPVersion.SOAP_11, true);

        assertEquals(FastInfosetMIMETypes.STATEFUL_SOAP_11, statefulSoap11Codec.getMimeType());
        assertEquals(
                FastInfosetMIMETypes.STATEFUL_SOAP_11,
                statefulSoap11Codec.copy().getMimeType());
    }
}
