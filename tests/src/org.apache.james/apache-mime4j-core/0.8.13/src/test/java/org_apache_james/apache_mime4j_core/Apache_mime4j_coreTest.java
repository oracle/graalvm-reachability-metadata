/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_james.apache_mime4j_core;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.apache.james.mime4j.MimeException;
import org.apache.james.mime4j.codec.Base64InputStream;
import org.apache.james.mime4j.codec.DecodeMonitor;
import org.apache.james.mime4j.codec.DecoderUtil;
import org.apache.james.mime4j.codec.EncoderUtil;
import org.apache.james.mime4j.codec.QuotedPrintableInputStream;
import org.apache.james.mime4j.parser.AbstractContentHandler;
import org.apache.james.mime4j.parser.MimeStreamParser;
import org.apache.james.mime4j.stream.BodyDescriptor;
import org.apache.james.mime4j.stream.EntityState;
import org.apache.james.mime4j.stream.Field;
import org.apache.james.mime4j.stream.MimeConfig;
import org.apache.james.mime4j.stream.MimeTokenStream;
import org.apache.james.mime4j.stream.NameValuePair;
import org.apache.james.mime4j.stream.RawBody;
import org.apache.james.mime4j.stream.RawField;
import org.apache.james.mime4j.stream.RawFieldParser;
import org.apache.james.mime4j.stream.RecursionMode;
import org.apache.james.mime4j.util.ByteSequence;
import org.apache.james.mime4j.util.ContentUtil;
import org.apache.james.mime4j.util.MimeParameterMapping;
import org.apache.james.mime4j.util.MimeUtil;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

public class Apache_mime4j_coreTest {
    @Test
    void mimeStreamParserRecursesThroughMultipartMessageAndDecodesBodies() throws Exception {
        String message = "From: sender@example.org\r\n"
                + "To: recipient@example.org\r\n"
                + "Subject: =?UTF-8?Q?Multipart_caf=C3=A9?=\r\n"
                + "Content-Type: multipart/mixed; boundary=\"frontier\"\r\n"
                + "\r\n"
                + "Human-readable preamble\r\n"
                + "--frontier\r\n"
                + "Content-Type: text/plain; charset=UTF-8\r\n"
                + "Content-Transfer-Encoding: quoted-printable\r\n"
                + "\r\n"
                + "Hello=2C caf=C3=A9!\r\n"
                + "--frontier\r\n"
                + "Content-Type: application/octet-stream\r\n"
                + "Content-Transfer-Encoding: base64\r\n"
                + "\r\n"
                + "QmluYXJ5IHBheWxvYWQ=\r\n"
                + "--frontier--\r\n"
                + "Trailer epilogue\r\n";
        RecordingContentHandler handler = new RecordingContentHandler();
        MimeStreamParser parser = new MimeStreamParser(MimeConfig.DEFAULT);
        parser.setRecurse();
        parser.setContentDecoding(true);
        parser.setContentHandler(handler);

        parser.parse(input(message));

        assertThat(handler.events).containsSubsequence(
                "startMessage",
                "startHeader",
                "field:From",
                "field:To",
                "field:Subject",
                "field:Content-Type",
                "endHeader",
                "startMultipart:multipart/mixed:frontier",
                "preamble:Human-readable preamble",
                "startBodyPart",
                "body:text/plain:quoted-printable:Hello, caf\u00e9!",
                "endBodyPart",
                "startBodyPart",
                "body:application/octet-stream:base64:Binary payload",
                "endBodyPart",
                "epilogue:Trailer epilogue",
                "endMultipart",
                "endMessage");
        assertThat(handler.fieldBodies).contains("=?UTF-8?Q?Multipart_caf=C3=A9?=");
        assertThat(handler.bodyDescriptors)
                .extracting(descriptor -> descriptor.getMimeType(), descriptor -> descriptor.getTransferEncoding())
                .contains(tuple("text/plain", "quoted-printable"), tuple("application/octet-stream", "base64"));
    }

    @Test
    void mimeTokenStreamExposesStatesFieldsDescriptorsAndDecodedBodyReader() throws Exception {
        String message = "Subject: Token stream\r\n"
                + "Content-Type: text/plain; charset=ISO-8859-1\r\n"
                + "Content-Transfer-Encoding: quoted-printable\r\n"
                + "\r\n"
                + "Ol=E1 Mundo\r\n";
        MimeConfig config = MimeConfig.copy(MimeConfig.DEFAULT)
                .setCountLineNumbers(true)
                .build();
        MimeTokenStream stream = new MimeTokenStream(config);
        stream.setRecursionMode(RecursionMode.M_RECURSE);
        stream.parse(input(message));
        assertThat(stream.getState()).isEqualTo(EntityState.T_START_MESSAGE);
        List<EntityState> states = new ArrayList<>();
        List<String> fields = new ArrayList<>();
        String decodedBody = null;
        BodyDescriptor textDescriptor = null;

        for (EntityState state = stream.next(); state != EntityState.T_END_OF_STREAM; state = stream.next()) {
            states.add(state);
            if (state == EntityState.T_FIELD) {
                Field field = stream.getField();
                fields.add(field.getName() + ":" + field.getBody());
            } else if (state == EntityState.T_BODY) {
                textDescriptor = stream.getBodyDescriptor();
                decodedBody = ContentUtil.buffer(stream.getReader()).trim();
            }
        }

        assertThat(states).containsExactly(
                EntityState.T_START_HEADER,
                EntityState.T_FIELD,
                EntityState.T_FIELD,
                EntityState.T_FIELD,
                EntityState.T_END_HEADER,
                EntityState.T_BODY,
                EntityState.T_END_MESSAGE);
        assertThat(fields).containsExactly(
                "Subject:Token stream",
                "Content-Type:text/plain; charset=ISO-8859-1",
                "Content-Transfer-Encoding:quoted-printable");
        assertThat(textDescriptor.getMimeType()).isEqualTo("text/plain");
        assertThat(textDescriptor.getCharset()).isEqualTo("ISO-8859-1");
        assertThat(textDescriptor.getTransferEncoding()).isEqualTo("quoted-printable");
        assertThat(decodedBody).isEqualTo("Ol\u00e1 Mundo");
        assertThat(MimeTokenStream.stateToString(EntityState.T_BODY)).isNotBlank();
    }

    @Test
    void rawMimeStreamParserEmitsNestedEntityAsRawContent() throws Exception {
        String message = "Subject: Raw mode\r\n"
                + "Content-Type: multipart/mixed; boundary=frontier\r\n"
                + "\r\n"
                + "Preamble ignored by raw mode\r\n"
                + "--frontier\r\n"
                + "Content-Type: text/plain\r\n"
                + "\r\n"
                + "Nested body\r\n"
                + "--frontier--\r\n";
        RecordingContentHandler handler = new RecordingContentHandler();
        MimeStreamParser parser = new MimeStreamParser(MimeConfig.DEFAULT);
        parser.setRaw();
        parser.setContentHandler(handler);

        assertThat(parser.isRaw()).isTrue();
        parser.parse(input(message));

        assertThat(handler.events).containsExactly(
                "startMessage",
                "startHeader",
                "field:Subject",
                "field:Content-Type",
                "endHeader",
                "startMultipart:multipart/mixed:frontier",
                "preamble:Preamble ignored by raw mode",
                "raw:Content-Type: text/plain\r\n\r\nNested body",
                "endMultipart",
                "endMessage");
        assertThat(handler.events).doesNotContain("startBodyPart", "body:text/plain:7bit:Nested body");
        assertThat(handler.fieldBodies).contains("multipart/mixed; boundary=frontier");
    }

    @Test
    void mimeParserParsesHeadlessEntityUsingConfiguredContentType() throws Exception {
        String body = "headless text body\r\nsecond line\r\n";
        RecordingContentHandler handler = new RecordingContentHandler();
        MimeConfig config = MimeConfig.custom()
                .setHeadlessParsing("text/plain; charset=UTF-8")
                .build();
        MimeStreamParser parser = new MimeStreamParser(config);
        parser.setContentHandler(handler);

        parser.parse(input(body));

        assertThat(handler.events).containsSubsequence(
                "startMessage",
                "startHeader",
                "field:Content-Type",
                "endHeader",
                "body:text/plain:7bit:headless text body\r\nsecond line",
                "endMessage");
        assertThat(handler.fieldBodies).contains("text/plain; charset=UTF-8");
        assertThat(handler.bodyDescriptors)
                .extracting(BodyDescriptor::getMimeType, BodyDescriptor::getCharset)
                .contains(tuple("text/plain", "UTF-8"));
    }

    @Test
    void rawFieldParserSeparatesHeaderNameBodyAndQuotedParameters() throws Exception {
        String header = "Content-Type: text/html; charset=\"UTF-8\"; format=flowed; "
                + "name=\"report; Q1.txt\"";
        ByteSequence raw = ContentUtil.encode(header);

        RawField field = RawFieldParser.DEFAULT.parseField(raw);
        RawBody body = RawFieldParser.DEFAULT.parseRawBody(field);

        assertThat(field.getName()).isEqualTo("Content-Type");
        assertThat(field.getNameLowerCase()).isEqualTo("content-type");
        assertThat(field.getBody()).isEqualTo("text/html; charset=\"UTF-8\"; format=flowed; "
                + "name=\"report; Q1.txt\"");
        assertThat(body.getValue()).isEqualTo("text/html");
        assertThat(body.getParams())
                .extracting(NameValuePair::getName, NameValuePair::getValue)
                .containsExactly(
                        tuple("charset", "UTF-8"),
                        tuple("format", "flowed"),
                        tuple("name", "report; Q1.txt"));
    }

    @Test
    void mimeParameterMappingDecodesExtendedAndContinuedParameters() {
        MimeParameterMapping parameters = new MimeParameterMapping();

        parameters.addParameter("filename", "fallback.txt");
        parameters.addParameter("filename*", "UTF-8''caf%C3%A9%20menu.txt");
        parameters.addParameter("title*0*", "UTF-8''Quarterly%20");
        parameters.addParameter("title*1*", "caf%C3%A9%20report");
        parameters.addParameter("Content-Language", "en-US");
        Map<String, String> resolvedParameters = parameters.getParameters();

        assertThat(parameters.get("filename")).isEqualTo("caf\u00e9 menu.txt");
        assertThat(parameters.get("title")).isEqualTo("Quarterly caf\u00e9 report");
        assertThat(parameters.get("content-language")).isEqualTo("en-US");
        assertThat(resolvedParameters)
                .containsEntry("filename", "caf\u00e9 menu.txt")
                .containsEntry("title", "Quarterly caf\u00e9 report")
                .containsEntry("content-language", "en-US");
    }

    @Test
    void codecUtilitiesRoundTripEncodedWordsBase64AndQuotedPrintableStreams() throws Exception {
        String displayName = "R\u00e9sum\u00e9 caf\u00e9";
        String encodedWord = EncoderUtil.encodeEncodedWord(
                displayName,
                EncoderUtil.Usage.TEXT_TOKEN,
                0,
                StandardCharsets.UTF_8,
                EncoderUtil.Encoding.Q);
        byte[] base64Decoded;
        byte[] quotedPrintableDecoded;

        try (InputStream base64 = new Base64InputStream(input("U3RyZWFtIGJvZHk=\r\n"))) {
            base64Decoded = ContentUtil.buffer(base64);
        }
        try (InputStream quotedPrintable = new QuotedPrintableInputStream(input("soft=\r\nline=20caf=C3=A9"))) {
            quotedPrintableDecoded = ContentUtil.buffer(quotedPrintable);
        }

        assertThat(encodedWord).startsWith("=?").contains("?Q?").endsWith("?=");
        assertThat(DecoderUtil.decodeEncodedWords(encodedWord, DecodeMonitor.STRICT)).isEqualTo(displayName);
        assertThat(EncoderUtil.encodeB("mime4j".getBytes(StandardCharsets.US_ASCII))).isEqualTo("bWltZTRq");
        assertThat(ContentUtil.toString(base64Decoded, StandardCharsets.UTF_8)).isEqualTo("Stream body");
        assertThat(ContentUtil.toString(quotedPrintableDecoded, StandardCharsets.UTF_8)).isEqualTo("softline caf\u00e9");
        assertThat(EncoderUtil.isToken("multipart-mixed_123")).isTrue();
        assertThat(EncoderUtil.isToken("needs space")).isFalse();
    }

    @Test
    void mimeAndContentUtilitiesHandleBoundariesHeadersDatesAndCharsets() throws Exception {
        String header = "Subject: This header contains enough words to require folding by mime4j "
                + "while preserving all unfolded words in their original order";
        String folded = MimeUtil.fold(header, 9);
        ByteSequence utf8Bytes = ContentUtil.encode(StandardCharsets.UTF_8, "Gr\u00fc\u00dfe");
        byte[] asciiBytes = ContentUtil.toAsciiByteArray("plain-ascii");
        String boundary = MimeUtil.createUniqueBoundary();
        String messageId = MimeUtil.createUniqueMessageId("example.org");

        assertThat(MimeUtil.isSameMimeType("Text/Plain", "text/plain")).isTrue();
        assertThat(MimeUtil.isMultipart("multipart/mixed")).isTrue();
        assertThat(MimeUtil.isMessage("message/rfc822")).isTrue();
        assertThat(MimeUtil.isBase64Encoding("BASE64")).isTrue();
        assertThat(MimeUtil.isQuotedPrintableEncoded("quoted-printable")).isTrue();
        assertThat(folded).contains("\r\n");
        assertThat(MimeUtil.unfold(folded)).isEqualTo(header);
        assertThat(ContentUtil.decode(StandardCharsets.UTF_8, utf8Bytes)).isEqualTo("Gr\u00fc\u00dfe");
        assertThat(ContentUtil.toAsciiString(asciiBytes)).isEqualTo("plain-ascii");
        assertThat(boundary).isNotBlank();
        assertThat(messageId).startsWith("<").endsWith("@example.org>");
    }

    private static ByteArrayInputStream input(String value) {
        return new ByteArrayInputStream(value.getBytes(StandardCharsets.US_ASCII));
    }

    private static final class RecordingContentHandler extends AbstractContentHandler {
        private final List<String> events = new ArrayList<>();
        private final List<String> fieldBodies = new ArrayList<>();
        private final List<BodyDescriptor> bodyDescriptors = new ArrayList<>();

        @Override
        public void startMessage() {
            events.add("startMessage");
        }

        @Override
        public void endMessage() {
            events.add("endMessage");
        }

        @Override
        public void startHeader() {
            events.add("startHeader");
        }

        @Override
        public void field(Field field) {
            events.add("field:" + field.getName());
            fieldBodies.add(field.getBody());
        }

        @Override
        public void endHeader() {
            events.add("endHeader");
        }

        @Override
        public void startMultipart(BodyDescriptor bodyDescriptor) {
            events.add("startMultipart:" + bodyDescriptor.getMimeType() + ":" + bodyDescriptor.getBoundary());
        }

        @Override
        public void endMultipart() {
            events.add("endMultipart");
        }

        @Override
        public void startBodyPart() {
            events.add("startBodyPart");
        }

        @Override
        public void endBodyPart() {
            events.add("endBodyPart");
        }

        @Override
        public void preamble(InputStream inputStream) throws IOException {
            events.add("preamble:" + readTrimmed(inputStream));
        }

        @Override
        public void epilogue(InputStream inputStream) throws IOException {
            events.add("epilogue:" + readTrimmed(inputStream));
        }

        @Override
        public void body(BodyDescriptor bodyDescriptor, InputStream inputStream) throws IOException {
            bodyDescriptors.add(bodyDescriptor);
            events.add("body:" + bodyDescriptor.getMimeType() + ":" + bodyDescriptor.getTransferEncoding() + ":"
                    + readTrimmed(inputStream));
        }

        @Override
        public void raw(InputStream inputStream) throws MimeException, IOException {
            events.add("raw:" + readTrimmed(inputStream));
        }

        private static String readTrimmed(InputStream inputStream) throws IOException {
            return ContentUtil.toString(ContentUtil.buffer(inputStream), StandardCharsets.UTF_8).trim();
        }
    }
}
