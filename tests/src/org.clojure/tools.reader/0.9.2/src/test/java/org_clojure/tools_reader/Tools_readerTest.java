/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_clojure.tools_reader;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Date;
import java.util.UUID;
import java.util.regex.Pattern;

import clojure.lang.AFn;
import clojure.lang.ExceptionInfo;
import clojure.lang.IFn;
import clojure.lang.IPersistentMap;
import clojure.lang.IPersistentSet;
import clojure.lang.IPersistentVector;
import clojure.lang.IObj;
import clojure.lang.Keyword;
import clojure.lang.PersistentArrayMap;
import clojure.lang.PersistentHashSet;
import clojure.lang.PersistentVector;
import clojure.lang.RT;
import clojure.lang.Symbol;
import org.graalvm.internal.tck.NativeImageSupport;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

public class Tools_readerTest {
    private static final Error UNSUPPORTED_NATIVE_IMAGE_ERROR;
    private static final IFn REQUIRE;
    private static final IFn EDN_READ;
    private static final IFn EDN_READ_STRING;
    private static final IFn READER_READ;
    private static final IFn READER_READ_STRING;
    private static final IFn STRING_PUSH_BACK_READER;
    private static final IFn INPUT_STREAM_PUSH_BACK_READER;
    private static final IFn INDEXING_PUSH_BACK_READER;
    private static final IFn SOURCE_LOGGING_PUSH_BACK_READER;
    private static final IFn READ_CHAR;
    private static final IFn PEEK_CHAR;
    private static final IFn UNREAD;
    private static final IFn GET_LINE_NUMBER;
    private static final IFn GET_COLUMN_NUMBER;
    private static final IFn GET_FILE_NAME;
    private static final IFn INDEXING_READER;
    private static final IFn READ_LINE;
    private static final IFn LINE_START;

    private static final Keyword NAME = Keyword.intern(null, "name");
    private static final Keyword NUMBERS = Keyword.intern(null, "numbers");
    private static final Keyword SET = Keyword.intern(null, "set");
    private static final Keyword UUID_KEY = Keyword.intern(null, "uuid");
    private static final Keyword INSTANT = Keyword.intern(null, "inst");
    private static final Keyword DISCARD = Keyword.intern(null, "discard");
    private static final Keyword EOF = Keyword.intern(null, "eof");
    private static final Keyword READERS = Keyword.intern(null, "readers");
    private static final Keyword DEFAULT = Keyword.intern(null, "default");
    private static final Keyword LINE = Keyword.intern(null, "line");
    private static final Keyword COLUMN = Keyword.intern(null, "column");
    private static final Keyword FILE = Keyword.intern(null, "file");
    private static final Keyword PRIVATE = Keyword.intern(null, "private");
    private static final Keyword SOURCE = Keyword.intern(null, "source");
    private static final Keyword ANSWER = Keyword.intern(null, "answer");
    private static final Keyword READ_COND = Keyword.intern(null, "read-cond");
    private static final Keyword FEATURES = Keyword.intern(null, "features");
    private static final Keyword CLJ = Keyword.intern(null, "clj");
    private static final Keyword ALLOW = Keyword.intern(null, "allow");

    static {
        Error unsupportedNativeImageError = null;
        IFn require = null;
        IFn ednRead = null;
        IFn ednReadString = null;
        IFn readerRead = null;
        IFn readerReadString = null;
        IFn stringPushBackReader = null;
        IFn inputStreamPushBackReader = null;
        IFn indexingPushBackReader = null;
        IFn sourceLoggingPushBackReader = null;
        IFn readChar = null;
        IFn peekChar = null;
        IFn unread = null;
        IFn getLineNumber = null;
        IFn getColumnNumber = null;
        IFn getFileName = null;
        IFn indexingReader = null;
        IFn readLine = null;
        IFn lineStart = null;
        try {
            require = RT.var("clojure.core", "require");
            ednRead = RT.var("clojure.tools.reader.edn", "read");
            ednReadString = RT.var("clojure.tools.reader.edn", "read-string");
            readerRead = RT.var("clojure.tools.reader", "read");
            readerReadString = RT.var("clojure.tools.reader", "read-string");
            stringPushBackReader = RT.var("clojure.tools.reader.reader-types", "string-push-back-reader");
            inputStreamPushBackReader = RT.var("clojure.tools.reader.reader-types", "input-stream-push-back-reader");
            indexingPushBackReader = RT.var("clojure.tools.reader.reader-types", "indexing-push-back-reader");
            sourceLoggingPushBackReader = RT.var(
                    "clojure.tools.reader.reader-types", "source-logging-push-back-reader");
            readChar = RT.var("clojure.tools.reader.reader-types", "read-char");
            peekChar = RT.var("clojure.tools.reader.reader-types", "peek-char");
            unread = RT.var("clojure.tools.reader.reader-types", "unread");
            getLineNumber = RT.var("clojure.tools.reader.reader-types", "get-line-number");
            getColumnNumber = RT.var("clojure.tools.reader.reader-types", "get-column-number");
            getFileName = RT.var("clojure.tools.reader.reader-types", "get-file-name");
            indexingReader = RT.var("clojure.tools.reader.reader-types", "indexing-reader?");
            readLine = RT.var("clojure.tools.reader.reader-types", "read-line");
            lineStart = RT.var("clojure.tools.reader.reader-types", "line-start?");
        } catch (Error error) {
            if (!hasUnsupportedFeatureError(error)) {
                throw error;
            }
            unsupportedNativeImageError = error;
        }
        UNSUPPORTED_NATIVE_IMAGE_ERROR = unsupportedNativeImageError;
        REQUIRE = require;
        EDN_READ = ednRead;
        EDN_READ_STRING = ednReadString;
        READER_READ = readerRead;
        READER_READ_STRING = readerReadString;
        STRING_PUSH_BACK_READER = stringPushBackReader;
        INPUT_STREAM_PUSH_BACK_READER = inputStreamPushBackReader;
        INDEXING_PUSH_BACK_READER = indexingPushBackReader;
        SOURCE_LOGGING_PUSH_BACK_READER = sourceLoggingPushBackReader;
        READ_CHAR = readChar;
        PEEK_CHAR = peekChar;
        UNREAD = unread;
        GET_LINE_NUMBER = getLineNumber;
        GET_COLUMN_NUMBER = getColumnNumber;
        GET_FILE_NAME = getFileName;
        INDEXING_READER = indexingReader;
        READ_LINE = readLine;
        LINE_START = lineStart;
    }

    @BeforeAll
    static void loadClojureNamespaces() {
        if (nativeImageUnsupported()) {
            return;
        }
        REQUIRE.invoke(Symbol.intern("clojure.tools.reader"));
        REQUIRE.invoke(Symbol.intern("clojure.tools.reader.edn"));
        REQUIRE.invoke(Symbol.intern("clojure.tools.reader.reader-types"));
    }

    @Test
    void ednReadStringParsesCollectionsScalarsAndDefaultTaggedLiterals() {
        if (nativeImageUnsupported()) {
            return;
        }
        IPersistentMap data = (IPersistentMap) EDN_READ_STRING.invoke("""
                {:name "Ada"
                 :numbers [1 2N 3.5M]
                 :set #{:alpha :beta}
                 :uuid #uuid "01234567-89ab-cdef-0123-456789abcdef"
                 :inst #inst "2020-01-02T03:04:05.006-00:00"
                 :nil-value nil
                 :bool true
                 :character \\newline
                 :discard [1 #_ignored 3]}
                """);

        assertThat(data.valAt(NAME)).isEqualTo("Ada");

        IPersistentVector numbers = (IPersistentVector) data.valAt(NUMBERS);
        assertThat(numbers.count()).isEqualTo(3);
        assertThat(numbers.nth(0)).isEqualTo(1L);
        assertThat(numbers.nth(1).toString()).isEqualTo("2");
        assertThat(numbers.nth(2).toString()).isEqualTo("3.5");

        IPersistentSet set = (IPersistentSet) data.valAt(SET);
        assertThat(set.contains(Keyword.intern(null, "alpha"))).isTrue();
        assertThat(set.contains(Keyword.intern(null, "beta"))).isTrue();

        assertThat(data.valAt(UUID_KEY)).isEqualTo(UUID.fromString("01234567-89ab-cdef-0123-456789abcdef"));
        assertThat(data.valAt(INSTANT)).isInstanceOf(Date.class);
        assertThat(data.valAt(Keyword.intern(null, "nil-value"))).isNull();
        assertThat(data.valAt(Keyword.intern(null, "bool"))).isEqualTo(Boolean.TRUE);
        assertThat(data.valAt(Keyword.intern(null, "character"))).isEqualTo('\n');
        assertThat(data.valAt(DISCARD)).isEqualTo(PersistentVector.create(Arrays.asList(1L, 3L)));
    }

    @Test
    void ednReadUsesCustomTaggedReadersDefaultReadersAndEofOptions() {
        if (nativeImageUnsupported()) {
            return;
        }
        Object reader = STRING_PUSH_BACK_READER.invoke("#point [3 4] #domain/money {:amount 12}", 2);
        IPersistentMap customReaders = PersistentArrayMap.createWithCheck(new Object[] {
                Symbol.intern("point"), new PointReader()
        });
        IPersistentMap options = PersistentArrayMap.createWithCheck(new Object[] {
                READERS, customReaders,
                DEFAULT, new DefaultTaggedReader(),
                EOF, "finished"
        });

        assertThat(EDN_READ.invoke(options, reader)).isEqualTo("point(3,4)");
        IPersistentMap defaultTagged = (IPersistentMap) EDN_READ.invoke(options, reader);
        assertThat(defaultTagged.valAt(Keyword.intern(null, "tag"))).isEqualTo(Symbol.intern("domain", "money"));
        assertThat(defaultTagged.valAt(Keyword.intern(null, "form")).toString()).contains(":amount 12");
        assertThat(EDN_READ.invoke(options, reader)).isEqualTo("finished");
    }

    @Test
    void ednReaderReportsStructuredLocationForMalformedInput() {
        if (nativeImageUnsupported()) {
            return;
        }
        Object reader = INDEXING_PUSH_BACK_READER.invoke("\n{:broken", 2, "example.edn");

        assertThatExceptionOfType(ExceptionInfo.class)
                .isThrownBy(() -> EDN_READ.invoke(reader))
                .satisfies(exception -> {
                    IPersistentMap data = exception.getData();
                    assertThat(data.valAt(LINE)).isEqualTo(2);
                    assertThat(data.valAt(COLUMN)).isEqualTo(9);
                    assertThat(data.valAt(FILE)).isEqualTo("example.edn");
                    assertThat(exception).hasMessageContaining("EOF while reading");
                });
    }

    @Test
    void readerTypesSupportPushbackInputStreamsIndexingAndLineReading() {
        if (nativeImageUnsupported()) {
            return;
        }
        Object pushback = STRING_PUSH_BACK_READER.invoke("ab", 2);
        assertThat(READ_CHAR.invoke(pushback)).isEqualTo('a');
        UNREAD.invoke(pushback, 'a');
        assertThat(PEEK_CHAR.invoke(pushback)).isEqualTo('a');
        assertThat(READ_CHAR.invoke(pushback)).isEqualTo('a');
        assertThat(READ_CHAR.invoke(pushback)).isEqualTo('b');

        ByteArrayInputStream stream = new ByteArrayInputStream("xy".getBytes(StandardCharsets.UTF_8));
        Object streamReader = INPUT_STREAM_PUSH_BACK_READER.invoke(stream, 1);
        assertThat(PEEK_CHAR.invoke(streamReader)).isEqualTo('x');
        assertThat(READ_CHAR.invoke(streamReader)).isEqualTo('x');
        assertThat(READ_CHAR.invoke(streamReader)).isEqualTo('y');

        Object indexingReader = INDEXING_PUSH_BACK_READER.invoke("a\nb", 2, "source.clj");
        assertThat(INDEXING_READER.invoke(indexingReader)).isEqualTo(Boolean.TRUE);
        assertThat(GET_LINE_NUMBER.invoke(indexingReader)).isEqualTo(1);
        assertThat(GET_COLUMN_NUMBER.invoke(indexingReader)).isEqualTo(1);
        assertThat(GET_FILE_NAME.invoke(indexingReader)).isEqualTo("source.clj");
        assertThat(READ_CHAR.invoke(indexingReader)).isEqualTo('a');
        assertThat(LINE_START.invoke(indexingReader)).isEqualTo(Boolean.FALSE);
        assertThat(READ_CHAR.invoke(indexingReader)).isEqualTo('\n');
        assertThat(GET_LINE_NUMBER.invoke(indexingReader)).isEqualTo(2);
        assertThat(GET_COLUMN_NUMBER.invoke(indexingReader)).isEqualTo(1);
        assertThat(LINE_START.invoke(indexingReader)).isEqualTo(Boolean.TRUE);
        assertThat(READ_CHAR.invoke(indexingReader)).isEqualTo('b');
        UNREAD.invoke(indexingReader, 'b');
        assertThat(GET_COLUMN_NUMBER.invoke(indexingReader)).isEqualTo(1);

        Object lineReader = STRING_PUSH_BACK_READER.invoke("first line\nsecond line\n", 1);
        assertThat(READ_LINE.invoke(lineReader)).isEqualTo("first line");
        assertThat(READ_LINE.invoke(lineReader)).isEqualTo("second line");
    }

    @Test
    void clojureReaderHandlesMetadataRegexDiscardAndReaderConditionals() {
        if (nativeImageUnsupported()) {
            return;
        }
        IObj vectorWithMetadata = (IObj) READER_READ_STRING.invoke("^:private [1 2]");
        assertThat(vectorWithMetadata.meta().valAt(PRIVATE)).isEqualTo(Boolean.TRUE);
        assertThat((IPersistentVector) vectorWithMetadata).isEqualTo(PersistentVector.create(Arrays.asList(1L, 2L)));

        Pattern regex = (Pattern) READER_READ_STRING.invoke("#\"a+b\"");
        assertThat(regex.matcher("aaab").matches()).isTrue();
        assertThat(regex.matcher("bbb").matches()).isFalse();

        assertThat(READER_READ_STRING.invoke("[1 #_ignored 2 ; comment\n 3]"))
                .isEqualTo(PersistentVector.create(Arrays.asList(1L, 2L, 3L)));

        IPersistentSet features = PersistentHashSet.create(Arrays.asList(CLJ));
        IPersistentMap options = PersistentArrayMap.createWithCheck(new Object[] {
                READ_COND, ALLOW,
                FEATURES, features
        });
        assertThat(READER_READ_STRING.invoke(options, "#?(:clj :selected :cljs :ignored)"))
                .isEqualTo(Keyword.intern(null, "selected"));
    }

    @Test
    void clojureReaderExpandsQuoteVarQuoteAndDerefReaderMacros() {
        if (nativeImageUnsupported()) {
            return;
        }
        assertThat(READER_READ_STRING.invoke("'alpha"))
                .isEqualTo(RT.list(Symbol.intern("quote"), Symbol.intern("alpha")));
        assertThat(READER_READ_STRING.invoke("#'clojure.core/map"))
                .isEqualTo(RT.list(Symbol.intern("var"), Symbol.intern("clojure.core", "map")));
        assertThat(READER_READ_STRING.invoke("@state"))
                .isEqualTo(RT.list(Symbol.intern("clojure.core", "deref"), Symbol.intern("state")));
    }

    @Test
    void clojureReaderAttachesSourceMetadataWithSourceLoggingReader() {
        if (nativeImageUnsupported()) {
            return;
        }
        Object reader = SOURCE_LOGGING_PUSH_BACK_READER.invoke("  ^:private [1 2]\n{:answer 42}", 2, "forms.clj");

        IObj vectorWithMetadata = (IObj) READER_READ.invoke(reader);
        assertThat((IPersistentVector) vectorWithMetadata).isEqualTo(PersistentVector.create(Arrays.asList(1L, 2L)));
        assertThat(vectorWithMetadata.meta().valAt(PRIVATE)).isEqualTo(Boolean.TRUE);
        assertThat(vectorWithMetadata.meta().valAt(SOURCE)).isEqualTo("^:private [1 2]");
        assertThat(vectorWithMetadata.meta().valAt(FILE)).isEqualTo("forms.clj");

        IObj mapWithSourceMetadata = (IObj) READER_READ.invoke(reader);
        assertThat(((IPersistentMap) mapWithSourceMetadata).valAt(ANSWER)).isEqualTo(42L);
        assertThat(mapWithSourceMetadata.meta().valAt(SOURCE)).isEqualTo("{:answer 42}");
        assertThat(mapWithSourceMetadata.meta().valAt(FILE)).isEqualTo("forms.clj");
    }

    private static final class PointReader extends AFn {
        @Override
        public Object invoke(Object form) {
            IPersistentVector point = (IPersistentVector) form;
            return "point(" + point.nth(0) + "," + point.nth(1) + ")";
        }
    }

    private static final class DefaultTaggedReader extends AFn {
        @Override
        public Object invoke(Object tag, Object form) {
            return PersistentArrayMap.createWithCheck(new Object[] {
                    Keyword.intern(null, "tag"), tag,
                    Keyword.intern(null, "form"), form
            });
        }
    }

    private static boolean nativeImageUnsupported() {
        return UNSUPPORTED_NATIVE_IMAGE_ERROR != null;
    }

    private static boolean hasUnsupportedFeatureError(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof Error error && NativeImageSupport.isUnsupportedFeatureError(error)) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }
}
