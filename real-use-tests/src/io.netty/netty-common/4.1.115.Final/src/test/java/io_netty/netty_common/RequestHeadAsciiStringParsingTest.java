/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_netty.netty_common;

import io.netty.util.AsciiString;
import io.netty.util.ByteProcessor;
import io.netty.util.CharsetUtil;
import io.netty.util.HashingStrategy;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CharsetEncoder;
import java.nio.charset.CodingErrorAction;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Exercises the ASCII text subsystem of netty-common: `AsciiString` byte-backed strings with
 * zero-copy views, `ByteProcessor` scans, case-insensitive comparison through `HashingStrategy`,
 * numeric parsing, and the `CharsetUtil` encoders and decoders that turn text into wire bytes.
 *
 * A small HTTP/1.1 front end parses request heads straight from the bytes it received: it finds
 * line ends with byte processors, slices the request line and headers into views over the same
 * array, stores headers under case-insensitive names, parses `Content-Length`, reads connection
 * tokens, checks bearer credentials, and encodes response headers back to Latin-1 bytes.
 */
class RequestHeadAsciiStringParsingTest {

    private static final AsciiString CONTENT_LENGTH = AsciiString.cached("content-length");

    private static final AsciiString CONNECTION = AsciiString.cached("connection");

    private static final AsciiString AUTHORIZATION = AsciiString.cached("authorization");

    private static final AsciiString HOST = AsciiString.cached("host");

    private static final AsciiString BEARER = AsciiString.cached("Bearer ");

    private static final String REQUEST = """
            POST /api/orders?expand=lines HTTP/1.1\r
            Host: shop.example.com\r
            Content-Type: application/json; charset=utf-8\r
            CONTENT-LENGTH:   1842  \r
            Connection: keep-alive, Upgrade\r
            Authorization: bearer eyJhbGciOiJIUzI1NiJ9.e30.sig\r
            X-Trace:\t 7f3a \t\r
            \r
            """;

    @Test
    void requestHeadIsParsedIntoViewsOverTheReceivedBytes() throws Exception {
        byte[] wire = REQUEST.getBytes(CharsetUtil.US_ASCII);
        RequestHead head = RequestHeadParser.parse(new AsciiString(wire, false));

        assertThat((CharSequence) head.method()).isEqualTo(AsciiString.of("POST"));
        assertThat(head.path().toString()).isEqualTo("/api/orders");
        assertThat(head.query().toString()).isEqualTo("expand=lines");
        assertThat(head.version().contentEquals("HTTP/1.1")).isTrue();

        // The request-line parts are views over the received array, not copies of it.
        assertThat(head.method().array()).isSameAs(wire);
        assertThat(head.method().arrayOffset()).isZero();
        assertThat(head.method().isEntireArrayUsed()).isFalse();
        assertThat(head.path().arrayOffset()).isEqualTo(5);

        assertThat(head.headerNames()).containsExactly(
                "host", "content-type", "content-length", "connection", "authorization", "x-trace");
        assertThat(head.header(HOST).toString()).isEqualTo("shop.example.com");
        assertThat(head.header("X-TRACE").toString()).isEqualTo("7f3a");
        assertThat((CharSequence) head.header("x-missing")).isNull();
        assertThat(head.contentLength()).isEqualTo(1842);
    }

    @Test
    void headerNamesCompareAndHashWithoutRegardToCase() {
        HashingStrategy<CharSequence> names = AsciiString.CASE_INSENSITIVE_HASHER;
        AsciiString wireName = new AsciiString("Content-Length");

        assertThat(names.equals(wireName, CONTENT_LENGTH)).isTrue();
        assertThat(names.equals("CONTENT-LENGTH", CONTENT_LENGTH)).isTrue();
        assertThat(names.hashCode(wireName)).isEqualTo(names.hashCode("content-LENGTH"));
        assertThat(AsciiString.hashCode(wireName)).isEqualTo(AsciiString.hashCode("CONTENT-length"));
        assertThat(names.equals(wireName, CONNECTION)).isFalse();

        HashingStrategy<CharSequence> exact = AsciiString.CASE_SENSITIVE_HASHER;
        assertThat(exact.equals(wireName, "Content-Length")).isTrue();
        assertThat(exact.equals(wireName, CONTENT_LENGTH)).isFalse();

        assertThat(wireName.contentEqualsIgnoreCase(CONTENT_LENGTH)).isTrue();
        assertThat((CharSequence) wireName.toLowerCase()).isEqualTo(CONTENT_LENGTH);
        assertThat(wireName.toUpperCase().toString()).isEqualTo("CONTENT-LENGTH");
        assertThat(AsciiString.contentEqualsIgnoreCase("Keep-Alive", "keep-alive")).isTrue();
        assertThat(AsciiString.indexOfIgnoreCase("multipart/FORM-data", "form-data", 0))
                .isEqualTo(10);

        List<AsciiString> sorted = new ArrayList<>(Arrays.asList(
                AsciiString.of("via"), HOST, AsciiString.of("accept"), CONTENT_LENGTH));
        Collections.sort(sorted);
        assertThat(sorted).extracting(AsciiString::toString)
                .containsExactly("accept", "content-length", "host", "via");
    }

    @Test
    void connectionTokensAndCredentialsAreReadFromHeaderValues() throws Exception {
        RequestHead head = RequestHeadParser.parse(
                new AsciiString(REQUEST.getBytes(CharsetUtil.US_ASCII), false));

        AsciiString connection = head.header(CONNECTION);
        Collection<CharSequence> tokens = new ArrayList<>();
        for (AsciiString token : connection.split(',')) {
            tokens.add(token.trim());
        }
        assertThat(tokens).extracting(CharSequence::toString).containsExactly("keep-alive", "Upgrade");
        assertThat(AsciiString.containsContentEqualsIgnoreCase(tokens, "UPGRADE")).isTrue();
        assertThat(AsciiString.containsContentEqualsIgnoreCase(tokens, "close")).isFalse();
        assertThat(AsciiString.containsAllContentEqualsIgnoreCase(
                tokens, Arrays.<CharSequence>asList("Keep-Alive", "upgrade"))).isTrue();
        assertThat(AsciiString.containsIgnoreCase(connection, "KEEP")).isTrue();
        assertThat(connection.contains("KEEP")).isFalse();

        AsciiString authorization = head.header(AUTHORIZATION);
        assertThat(authorization.startsWith(BEARER)).isFalse();
        assertThat(authorization.regionMatches(true, 0, BEARER, 0, BEARER.length())).isTrue();
        AsciiString token = authorization.subSequence(BEARER.length());
        assertThat(token.split("\\.", 0)).hasSize(3);
        assertThat(token.lastIndexOf(".")).isEqualTo(token.length() - 4);
        assertThat(token.endsWith(".sig")).isTrue();
        assertThat(token.matches("[A-Za-z0-9_.-]+")).isTrue();

        AsciiString contentType = head.header("content-type");
        int semicolon = contentType.forEachByte(ByteProcessor.FIND_SEMI_COLON);
        assertThat(contentType.subSequence(0, semicolon).toString()).isEqualTo("application/json");
        AsciiString parameter = contentType.subSequence(semicolon + 1).trim();
        assertThat(parameter.toString()).isEqualTo("charset=utf-8");
        assertThat(parameter.subSequence(parameter.indexOf('=', 0) + 1).toUpperCase().toString())
                .isEqualTo(CharsetUtil.UTF_8.name());
    }

    @Test
    void numericHeaderValuesAreParsedInPlace() {
        AsciiString limits = AsciiString.of("max=65536;ttl=-30;ratio=0.75;id=ff");

        assertThat(limits.parseInt(4, 9)).isEqualTo(65536);
        assertThat(limits.parseLong(14, 17)).isEqualTo(-30L);
        assertThat(limits.parseDouble(24, 28)).isEqualTo(0.75);
        assertThat(limits.parseInt(32, 34, 16)).isEqualTo(255);
        assertThat(AsciiString.of("9007199254740993").parseLong()).isEqualTo(9007199254740993L);
        assertThat(AsciiString.of("42").parseShort()).isEqualTo((short) 42);

        assertThatThrownBy(() -> AsciiString.of("12a4").parseInt())
                .isInstanceOf(NumberFormatException.class);
        assertThatThrownBy(() -> AsciiString.of("99999999999").parseInt())
                .isInstanceOf(NumberFormatException.class);
    }

    @Test
    void responseHeadersAreEncodedToWireBytes() throws Exception {
        AsciiString disposition = new AsciiString(
                "attachment; filename=\"r\u00e9sum\u00e9.pdf\"", CharsetUtil.ISO_8859_1);
        assertThat(disposition.length()).isEqualTo(33);
        assertThat(disposition.byteAt(23) & 0xFF).isEqualTo(0xE9);
        assertThat(disposition.charAt(23)).isEqualTo('\u00e9');
        assertThat(new String(disposition.toByteArray(), CharsetUtil.ISO_8859_1))
                .isEqualTo(disposition.toString());

        AsciiString asciiOnly = new AsciiString("r\u00e9sum\u00e9", CharsetUtil.US_ASCII);
        assertThat(asciiOnly.toString()).isEqualTo("r?sum?");

        CharsetEncoder strict = CharsetUtil.encoder(CharsetUtil.US_ASCII, CodingErrorAction.REPORT);
        assertThatThrownBy(() -> strict.encode(CharBuffer.wrap("r\u00e9sum\u00e9")))
                .isInstanceOf(CharacterCodingException.class);
        assertThat(CharsetUtil.getEncoder(CharsetUtil.UTF_8).charset()).isEqualTo(CharsetUtil.UTF_8);
        assertThat(CharsetUtil.getEncoder(CharsetUtil.UTF_8))
                .isSameAs(CharsetUtil.getEncoder(CharsetUtil.UTF_8));

        byte[] truncatedUtf8 = {'o', 'k', (byte) 0xC3};
        CharsetDecoder lenient = CharsetUtil.decoder(CharsetUtil.UTF_8);
        assertThat(lenient.decode(ByteBuffer.wrap(truncatedUtf8)).toString()).isEqualTo("ok\ufffd");
        CharsetDecoder reporting = CharsetUtil.decoder(CharsetUtil.UTF_8, CodingErrorAction.REPORT);
        assertThatThrownBy(() -> reporting.decode(ByteBuffer.wrap(truncatedUtf8)))
                .isInstanceOf(CharacterCodingException.class);

        AsciiString statusLine = AsciiString.of("HTTP/1.1 200 OK").concat("\r\n");
        byte[] out = new byte[statusLine.length()];
        statusLine.copy(0, out, 0, out.length);
        assertThat(out).isEqualTo("HTTP/1.1 200 OK\r\n".getBytes(CharsetUtil.US_ASCII));
        assertThat(statusLine.forEachByte(ByteProcessor.FIND_CRLF)).isEqualTo(15);
        assertThat(statusLine.replace(' ', '_').toString()).isEqualTo("HTTP/1.1_200_OK\r\n");
    }

    /// A parsed request head; header values are views over the bytes of the request.
    record RequestHead(AsciiString method, AsciiString path, AsciiString query,
                       AsciiString version, Map<HeaderName, AsciiString> headers) {

        AsciiString header(CharSequence name) {
            return headers.get(new HeaderName(name));
        }

        List<String> headerNames() {
            List<String> names = new ArrayList<>();
            for (HeaderName name : headers.keySet()) {
                names.add(name.value().toString());
            }
            return names;
        }

        int contentLength() {
            return header(CONTENT_LENGTH).parseInt();
        }
    }

    /// A map key that hashes and compares a header name the way HTTP does: ignoring case.
    static final class HeaderName {

        private final CharSequence value;

        HeaderName(CharSequence value) {
            this.value = value;
        }

        CharSequence value() {
            return value;
        }

        @Override
        public int hashCode() {
            return AsciiString.CASE_INSENSITIVE_HASHER.hashCode(value);
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof HeaderName name
                    && AsciiString.CASE_INSENSITIVE_HASHER.equals(value, name.value);
        }
    }

    /// Parses an HTTP/1.1 request head without copying the bytes it slices.
    static final class RequestHeadParser {

        private RequestHeadParser() {
        }

        static RequestHead parse(AsciiString raw) throws Exception {
            int lineEnd = raw.forEachByte(ByteProcessor.FIND_CRLF);
            AsciiString requestLine = raw.subSequence(0, lineEnd, false);
            AsciiString[] parts = requestLine.split(' ');
            assertThat(parts).hasSize(3);

            AsciiString target = parts[1];
            int question = target.indexOf('?', 0);
            AsciiString path = question < 0 ? target : target.subSequence(0, question, false);
            AsciiString query = question < 0
                    ? AsciiString.EMPTY_STRING
                    : target.subSequence(question + 1, target.length(), false);

            Map<HeaderName, AsciiString> headers = new LinkedHashMap<>();
            int start = lineEnd + 2;
            while (true) {
                int end = raw.forEachByte(start, raw.length() - start, ByteProcessor.FIND_CRLF);
                if (end == start) {
                    break;
                }
                AsciiString line = raw.subSequence(start, end, false);
                int colon = line.indexOf(':', 0);
                AsciiString name = line.subSequence(0, colon, false).toLowerCase();
                headers.put(new HeaderName(name), trimLinearWhitespace(line, colon + 1));
                start = end + 2;
            }
            return new RequestHead(parts[0], path, query, parts[2], headers);
        }

        /// Strips the spaces and tabs HTTP allows around a header value.
        private static AsciiString trimLinearWhitespace(AsciiString line, int from) throws Exception {
            int first = line.forEachByte(from, line.length() - from,
                    ByteProcessor.FIND_NON_LINEAR_WHITESPACE);
            if (first < 0) {
                return AsciiString.EMPTY_STRING;
            }
            int last = line.forEachByteDesc(first, line.length() - first,
                    ByteProcessor.FIND_NON_LINEAR_WHITESPACE);
            return line.subSequence(first, last + 1, false);
        }
    }
}
