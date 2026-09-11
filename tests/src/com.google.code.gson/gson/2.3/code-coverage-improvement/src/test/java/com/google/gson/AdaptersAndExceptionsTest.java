/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com.google.gson;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.google.gson.internal.$Gson$Preconditions;
import com.google.gson.internal.Streams;
import com.google.gson.internal.bind.ArrayTypeAdapter;
import com.google.gson.internal.bind.DateTypeAdapter;
import com.google.gson.internal.bind.SqlDateTypeAdapter;
import com.google.gson.internal.bind.TimeTypeAdapter;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.net.InetAddress;
import java.net.URI;
import java.net.URL;
import java.sql.Time;
import java.util.BitSet;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.Locale;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AdaptersAndExceptionsTest {
    @Test
    void dateAdaptersReadAndWriteTheirPublicDateRepresentations() throws Exception {
        TypeAdapter<Date> dateAdapter = new DateTypeAdapter();
        JsonTreeWriterProbe dateWriter = new JsonTreeWriterProbe();
        Date date = dateAdapter.read(new com.google.gson.internal.bind.JsonTreeReader(
                new JsonPrimitive("Jan 1, 1970, 12:00:00 AM")));
        assertThat(date).isNotNull();
        dateAdapter.write(dateWriter.writer, date);
        assertThat(dateWriter.writer.get().isJsonPrimitive()).isTrue();
        assertThat(dateAdapter.read(new com.google.gson.internal.bind.JsonTreeReader(JsonNull.INSTANCE)))
                .isNull();
        dateAdapter.write(dateWriter.writer, null);

        TypeAdapter<java.sql.Date> sqlAdapter = new SqlDateTypeAdapter();
        JsonTreeWriterProbe sqlWriter = new JsonTreeWriterProbe();
        java.sql.Date sqlDate = sqlAdapter.read(new com.google.gson.internal.bind.JsonTreeReader(
                new JsonPrimitive("Jan 1, 1970")));
        assertThat(sqlDate).isNotNull();
        sqlAdapter.write(sqlWriter.writer, sqlDate);
        assertThat(sqlWriter.writer.get().getAsString()).isEqualTo("Jan 1, 1970");
        sqlAdapter.write(sqlWriter.writer, null);
        assertThat(sqlAdapter.read(new com.google.gson.internal.bind.JsonTreeReader(JsonNull.INSTANCE)))
                .isNull();

        TypeAdapter<Time> timeAdapter = new TimeTypeAdapter();
        JsonTreeWriterProbe timeWriter = new JsonTreeWriterProbe();
        Time time = timeAdapter.read(new com.google.gson.internal.bind.JsonTreeReader(
                new JsonPrimitive("12:00:00 AM")));
        assertThat(time).isNotNull();
        timeAdapter.write(timeWriter.writer, time);
        assertThat(timeWriter.writer.get().getAsString()).isEqualTo("12:00:00 AM");
        timeAdapter.write(timeWriter.writer, null);
        assertThat(timeAdapter.read(new com.google.gson.internal.bind.JsonTreeReader(JsonNull.INSTANCE)))
                .isNull();

        assertThat(new $Gson$Preconditions()).isNotNull();
        assertThat(new Streams()).isNotNull();

        Gson configured = new GsonBuilder().setDateFormat("yyyy-MM-dd").create();
        assertThat(configured.fromJson("\"1970-01-01\"", Date.class)).isNotNull();
        Gson defaultDates = new Gson();
        assertThatThrownBy(() -> defaultDates.fromJson("1970-01-01", Date.class))
                .isInstanceOf(JsonSyntaxException.class);
        assertThat(defaultDates.fromJson("\"1970-01-01T00:00:00\\u005a\"", Date.class))
                .isNotNull();
    }

    @Test
    void gsonPublicAdaptersRoundTripPlatformTypes() throws Exception {
        Gson gson = new Gson();
        assertThat(gson.fromJson("null", Class.class)).isNull();
        assertThat(gson.toJson(null, Class.class)).isEqualTo("null");

        BitSet bits = gson.fromJson("[1,true,\"0\"]", BitSet.class);
        assertThat(bits.get(0)).isTrue();
        assertThat(bits.get(1)).isTrue();
        assertThat(bits.get(2)).isFalse();
        BitSet writtenBits = new BitSet();
        writtenBits.set(2);
        assertThat(gson.toJson(writtenBits, BitSet.class)).isEqualTo("[0,0,1]");

        assertThat(gson.fromJson("\"true\"", Boolean.class)).isTrue();
        assertThat(gson.toJson(Boolean.TRUE, Boolean.class)).isEqualTo("true");
        assertThat(gson.fromJson("7", Byte.class).byteValue()).isEqualTo((byte) 7);
        assertThat(gson.toJson(Byte.valueOf((byte) 7), Byte.class)).isEqualTo("7");
        assertThat(gson.fromJson("8", Short.class).shortValue()).isEqualTo((short) 8);
        assertThat(gson.toJson(Short.valueOf((short) 8), Short.class)).isEqualTo("8");
        assertThat(gson.fromJson("9", Long.class)).isEqualTo(9L);
        assertThat(gson.toJson(9L, Long.class)).isEqualTo("9");
        assertThat(gson.fromJson("1.25", Double.class)).isEqualTo(1.25d);
        assertThat(gson.toJson(1.25d, Double.class)).isEqualTo("1.25");
        assertThat(gson.fromJson("x", Character.class)).isEqualTo('x');
        assertThat(gson.toJson('x', Character.class)).isEqualTo("\"x\"");
        assertThat(gson.fromJson("12.50", BigDecimal.class)).isEqualTo(new BigDecimal("12.50"));
        assertThat(gson.toJson(new BigDecimal("12.50"), BigDecimal.class)).isEqualTo("12.50");
        assertThat(gson.fromJson("123", BigInteger.class)).isEqualTo(new BigInteger("123"));
        assertThat(gson.toJson(new BigInteger("123"), BigInteger.class)).isEqualTo("123");
        assertThat(gson.fromJson("\"builder\"", StringBuilder.class).toString())
                .isEqualTo("builder");
        assertThat(gson.toJson(new StringBuilder("builder"), StringBuilder.class))
                .isEqualTo("\"builder\"");
        Class<?> stringBufferType = Class.forName("java.lang.StringBuffer");
        Object decodedBuffer = gson.fromJson("\"buffer\"", stringBufferType);
        assertThat(decodedBuffer).hasToString("buffer");
        assertThat(gson.toJson(decodedBuffer, stringBufferType))
                .isEqualTo("\"buffer\"");

        URL url = gson.fromJson("\"file:/tmp/example\"", URL.class);
        assertThat(url.toExternalForm()).isEqualTo("file:/tmp/example");
        assertThat(gson.toJson(url, URL.class)).isEqualTo("\"file:/tmp/example\"");
        URI uri = gson.fromJson("\"https://example.com/uri\"", URI.class);
        assertThat(uri.toString()).isEqualTo("https://example.com/uri");
        assertThat(gson.toJson(uri, URI.class)).isEqualTo("\"https://example.com/uri\"");
        InetAddress address = gson.fromJson("\"127.0.0.1\"", InetAddress.class);
        assertThat(gson.toJson(address, InetAddress.class)).isEqualTo("\"127.0.0.1\"");
        UUID uuid = UUID.fromString("123e4567-e89b-12d3-a456-426655440000");
        assertThat(gson.fromJson(gson.toJson(uuid, UUID.class), UUID.class)).isEqualTo(uuid);

        java.sql.Timestamp timestamp = gson.fromJson("\"1970-01-01T00:00:00Z\"",
                java.sql.Timestamp.class);
        assertThat(timestamp).isNotNull();
        assertThat(gson.toJson(timestamp, java.sql.Timestamp.class)).isNotEmpty();
        Calendar calendar = gson.fromJson(
                "{\"year\":1970,\"month\":0,\"dayOfMonth\":1,\"hourOfDay\":0,"
                        + "\"minute\":0,\"second\":0}", Calendar.class);
        assertThat(calendar).isInstanceOf(GregorianCalendar.class);
        assertThat(gson.toJson(calendar, Calendar.class)).contains("\"year\":1970");
        Locale locale = gson.fromJson("\"en_US_POSIX\"", Locale.class);
        assertThat(locale).isEqualTo(new Locale("en", "US", "POSIX"));
        assertThat(gson.toJson(locale, Locale.class)).isEqualTo("\"en_US_POSIX\"");
    }

    @Test
    void arrayAdapterWritesRealJavaArraysThroughItsPublicAdapterApi() throws Exception {
        Gson gson = new Gson();
        ArrayTypeAdapter<String> adapter = new ArrayTypeAdapter<>(gson, gson.getAdapter(String.class),
                String.class);
        com.google.gson.internal.bind.JsonTreeWriter writer =
                new com.google.gson.internal.bind.JsonTreeWriter();
        adapter.write(writer, new String[] {"red", "blue"});
        assertThat(writer.get().toString()).isEqualTo("[\"red\",\"blue\"]");
        adapter.write(writer, null);
    }

    @Test
    void publicJsonExceptionsRetainMessagesAndCauses() {
        Throwable cause = new IllegalArgumentException("bad input");
        JsonIOException ioMessage = new JsonIOException("io");
        JsonIOException ioCause = new JsonIOException(cause);
        JsonIOException ioBoth = new JsonIOException("io", cause);
        assertThat(ioMessage).hasMessage("io");
        assertThat(ioCause).hasCause(cause);
        assertThat(ioBoth).hasMessage("io").hasCause(cause);

        JsonParseException parseMessage = new JsonParseException("parse");
        JsonParseException parseCause = new JsonParseException(cause);
        JsonParseException parseBoth = new JsonParseException("parse", cause);
        assertThat(parseMessage).hasMessage("parse");
        assertThat(parseCause).hasCause(cause);
        assertThat(parseBoth).hasMessage("parse").hasCause(cause);

        JsonSyntaxException syntaxMessage = new JsonSyntaxException("syntax");
        JsonSyntaxException syntaxCause = new JsonSyntaxException(cause);
        JsonSyntaxException syntaxBoth = new JsonSyntaxException("syntax", cause);
        assertThat(syntaxMessage).hasMessage("syntax");
        assertThat(syntaxCause).hasCause(cause);
        assertThat(syntaxBoth).hasMessage("syntax").hasCause(cause);

        com.google.gson.stream.MalformedJsonException malformedMessage =
                new com.google.gson.stream.MalformedJsonException("malformed");
        com.google.gson.stream.MalformedJsonException malformedCause =
                new com.google.gson.stream.MalformedJsonException(cause);
        com.google.gson.stream.MalformedJsonException malformedBoth =
                new com.google.gson.stream.MalformedJsonException("malformed", cause);
        assertThat(malformedMessage).hasMessage("malformed");
        assertThat(malformedCause).hasCause(cause);
        assertThat(malformedBoth).hasMessage("malformed").hasCause(cause);
    }

    private static final class JsonTreeWriterProbe {
        private final com.google.gson.internal.bind.JsonTreeWriter writer =
                new com.google.gson.internal.bind.JsonTreeWriter();
    }
}
