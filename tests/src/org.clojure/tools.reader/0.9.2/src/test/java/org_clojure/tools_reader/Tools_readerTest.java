/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_clojure.tools_reader;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;
import java.util.regex.Pattern;

import clojure.lang.AFn;
import clojure.lang.Counted;
import clojure.lang.ExceptionInfo;
import clojure.lang.IMeta;
import clojure.lang.IPersistentMap;
import clojure.lang.IPersistentSet;
import clojure.lang.IPersistentVector;
import clojure.lang.Keyword;
import clojure.lang.PersistentHashSet;
import clojure.lang.RT;
import clojure.lang.Symbol;
import clojure.lang.Var;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

public class Tools_readerTest {
    private static final Symbol EDN_NS = Symbol.intern("clojure.tools.reader.edn");
    private static final Symbol READER_NS = Symbol.intern("clojure.tools.reader");
    private static final Symbol READER_TYPES_NS = Symbol.intern("clojure.tools.reader.reader-types");

    private static final Keyword DEFAULT = Keyword.intern(null, "default");
    private static final Keyword DOC = Keyword.intern(null, "doc");
    private static final Keyword EOF = Keyword.intern(null, "eof");
    private static final Keyword FEATURES = Keyword.intern(null, "features");
    private static final Keyword READ_COND = Keyword.intern(null, "read-cond");
    private static final Keyword READERS = Keyword.intern(null, "readers");
    private static final Keyword SOURCE = Keyword.intern(null, "source");
    private static final Keyword X = Keyword.intern(null, "x");

    private static Var ednRead;
    private static Var ednReadString;
    private static Var readerRead;
    private static Var readerReadString;
    private static Var dataReaders;
    private static Var defaultDataReaderFn;
    private static Var inputStreamPushBackReader;
    private static Var indexingPushBackReader;
    private static Var sourceLoggingPushBackReader;
    private static Var indexingReader;
    private static Var readChar;
    private static Var peekChar;
    private static Var unread;
    private static Var getFileName;
    private static Var getLineNumber;
    private static Var getColumnNumber;

    @BeforeAll
    public static void requireClojureReaderNamespaces() {
        Var require = RT.var("clojure.core", "require");
        require.invoke(EDN_NS);
        require.invoke(READER_NS);
        require.invoke(READER_TYPES_NS);

        ednRead = RT.var("clojure.tools.reader.edn", "read");
        ednReadString = RT.var("clojure.tools.reader.edn", "read-string");
        readerRead = RT.var("clojure.tools.reader", "read");
        readerReadString = RT.var("clojure.tools.reader", "read-string");
        dataReaders = RT.var("clojure.tools.reader", "*data-readers*");
        defaultDataReaderFn = RT.var("clojure.tools.reader", "*default-data-reader-fn*");
        inputStreamPushBackReader = RT.var("clojure.tools.reader.reader-types", "input-stream-push-back-reader");
        indexingPushBackReader = RT.var("clojure.tools.reader.reader-types", "indexing-push-back-reader");
        sourceLoggingPushBackReader = RT.var("clojure.tools.reader.reader-types", "source-logging-push-back-reader");
        indexingReader = RT.var("clojure.tools.reader.reader-types", "indexing-reader?");
        readChar = RT.var("clojure.tools.reader.reader-types", "read-char");
        peekChar = RT.var("clojure.tools.reader.reader-types", "peek-char");
        unread = RT.var("clojure.tools.reader.reader-types", "unread");
        getFileName = RT.var("clojure.tools.reader.reader-types", "get-file-name");
        getLineNumber = RT.var("clojure.tools.reader.reader-types", "get-line-number");
        getColumnNumber = RT.var("clojure.tools.reader.reader-types", "get-column-number");
    }

    @Test
    public void ednReaderParsesCoreScalarsAndCollections() {
        Object parsed = ednReadString.invoke("""
                {:nil nil
                 :booleans [true false]
                 :string "line\\n\\u03bb"
                 :char \\newline
                 :numbers [42 -0x2A 2r1010 3/4 12345678901234567890N 6.25M]
                 :list (alpha beta)
                 :vector [1 2 3]
                 :set #{:a :b}
                 :discard [1 #_ignored 2]}
                """);

        IPersistentMap map = assertInstanceOf(IPersistentMap.class, parsed);
        IPersistentVector booleans = vectorAt(map, "booleans");
        IPersistentVector numbers = vectorAt(map, "numbers");
        IPersistentVector discarded = vectorAt(map, "discard");
        IPersistentSet set = assertInstanceOf(IPersistentSet.class, map.valAt(keyword("set")));

        assertAll(
                () -> assertNull(map.valAt(keyword("nil"))),
                () -> assertEquals(Boolean.TRUE, booleans.nth(0)),
                () -> assertEquals(Boolean.FALSE, booleans.nth(1)),
                () -> assertEquals("line\nλ", map.valAt(keyword("string"))),
                () -> assertEquals('\n', map.valAt(keyword("char"))),
                () -> assertEquals(42L, numbers.nth(0)),
                () -> assertEquals(-42L, numbers.nth(1)),
                () -> assertEquals(10L, numbers.nth(2)),
                () -> assertEquals("3/4", numbers.nth(3).toString()),
                () -> assertEquals("12345678901234567890", numbers.nth(4).toString()),
                () -> assertEquals(new BigDecimal("6.25"), numbers.nth(5)),
                () -> assertEquals(2, ((Counted) map.valAt(keyword("list"))).count()),
                () -> assertEquals(3, ((Counted) map.valAt(keyword("vector"))).count()),
                () -> assertTrue(set.contains(keyword("a"))),
                () -> assertTrue(set.contains(keyword("b"))),
                () -> assertEquals(2, discarded.count()),
                () -> assertEquals(1L, discarded.nth(0)),
                () -> assertEquals(2L, discarded.nth(1)));
    }

    @Test
    public void ednReaderUsesBuiltInAndSuppliedTagReaders() {
        Object instant = ednReadString.invoke("#inst \"2020-02-29T12:34:56.789-00:00\"");
        Object uuid = ednReadString.invoke("#uuid \"123e4567-e89b-12d3-a456-426614174000\"");

        AFn pointReader = new AFn() {
            @Override
            public Object invoke(Object form) {
                IPersistentVector coordinates = assertInstanceOf(IPersistentVector.class, form);
                return "point(" + coordinates.nth(0) + "," + coordinates.nth(1) + ")";
            }
        };
        AFn defaultReader = new AFn() {
            @Override
            public Object invoke(Object tag, Object form) {
                IPersistentMap map = assertInstanceOf(IPersistentMap.class, form);
                return tag + "=" + map.valAt(X);
            }
        };
        IPersistentMap options = RT.map(
                READERS, RT.map(Symbol.intern("app", "point"), pointReader),
                DEFAULT, defaultReader);

        Object point = ednReadString.invoke(options, "#app/point [3 4]");
        Object fallback = ednReadString.invoke(options, "#app/unknown {:x 9}");

        assertAll(
                () -> assertEquals(Date.class, instant.getClass()),
                () -> assertEquals(Instant.parse("2020-02-29T12:34:56.789Z").toEpochMilli(),
                        ((Date) instant).getTime()),
                () -> assertEquals(UUID.fromString("123e4567-e89b-12d3-a456-426614174000"), uuid),
                () -> assertEquals("point(3,4)", point),
                () -> assertEquals("app/unknown=9", fallback));
    }

    @Test
    public void ednReadHonorsEofOptionsAndReportsIndexedReaderErrors() {
        Object eofReader = indexingPushBackReader.invoke("", 2, "empty.edn");
        Object eof = ednRead.invoke(RT.map(EOF, keyword("finished")), eofReader);

        Object invalidReader = indexingPushBackReader.invoke("{:a 1\n", 2, "broken.edn");
        RuntimeException thrown = assertThrows(RuntimeException.class, () -> ednRead.invoke(invalidReader));
        ExceptionInfo exceptionInfo = assertInstanceOf(ExceptionInfo.class, thrown);
        IPersistentMap data = exceptionInfo.getData();

        assertAll(
                () -> assertEquals(keyword("finished"), eof),
                () -> assertEquals(keyword("reader-exception"), data.valAt(keyword("type"))),
                () -> assertEquals("broken.edn", data.valAt(keyword("file"))),
                () -> assertNotNull(data.valAt(keyword("line"))),
                () -> assertNotNull(data.valAt(keyword("column"))),
                () -> assertTrue(thrown.getMessage().contains("EOF")));
    }

    @Test
    public void clojureReaderParsesReaderConditionalsMetadataRegexAndReaderMacros() {
        IPersistentMap conditionalOptions = RT.map(
                READ_COND, keyword("allow"),
                FEATURES, PersistentHashSet.create(keyword("jvm"), keyword("server")));

        Object conditional = readerReadString.invoke(
                conditionalOptions,
                "#?(:cljs :ignored :jvm {:platform :jvm} :default :fallback)");
        Object metadataVector = readerReadString.invoke("^{:doc \"sample\"} [1 2]");
        Object regex = readerReadString.invoke("#\"a+b\"");
        Object quoted = readerReadString.invoke("'alpha");
        Object anonymousFunction = readerReadString.invoke("#(+ % 2)");

        IPersistentMap conditionalMap = assertInstanceOf(IPersistentMap.class, conditional);
        IPersistentMap metadata = assertInstanceOf(IPersistentMap.class, ((IMeta) metadataVector).meta());

        assertAll(
                () -> assertEquals(keyword("jvm"), conditionalMap.valAt(keyword("platform"))),
                () -> assertEquals("sample", metadata.valAt(DOC)),
                () -> assertEquals("a+b", assertInstanceOf(Pattern.class, regex).pattern()),
                () -> assertEquals(Symbol.intern("quote"), RT.first(quoted)),
                () -> assertEquals(Symbol.intern("alpha"), RT.second(quoted)),
                () -> assertEquals(Symbol.intern("fn*"), RT.first(anonymousFunction)));
    }

    @Test
    public void clojureReaderUsesThreadBoundDataReadersAndDefaultReader() {
        AFn coordinateReader = new AFn() {
            @Override
            public Object invoke(Object form) {
                IPersistentVector coordinates = assertInstanceOf(IPersistentVector.class, form);
                return RT.map(keyword("x"), coordinates.nth(0), keyword("y"), coordinates.nth(1));
            }
        };
        AFn fallbackReader = new AFn() {
            @Override
            public Object invoke(Object tag, Object form) {
                return RT.vector(tag, form);
            }
        };

        Var.pushThreadBindings(RT.map(
                dataReaders, RT.map(Symbol.intern("app", "coordinates"), coordinateReader),
                defaultDataReaderFn, fallbackReader));
        try {
            Object coordinates = readerReadString.invoke("#app/coordinates [8 13]");
            Object fallback = readerReadString.invoke("#app/unknown {:payload true}");

            IPersistentMap coordinateMap = assertInstanceOf(IPersistentMap.class, coordinates);
            IPersistentVector fallbackVector = assertInstanceOf(IPersistentVector.class, fallback);
            IPersistentMap fallbackPayload = assertInstanceOf(IPersistentMap.class, fallbackVector.nth(1));

            assertAll(
                    () -> assertEquals(8L, coordinateMap.valAt(keyword("x"))),
                    () -> assertEquals(13L, coordinateMap.valAt(keyword("y"))),
                    () -> assertEquals(Symbol.intern("app", "unknown"), fallbackVector.nth(0)),
                    () -> assertEquals(Boolean.TRUE, fallbackPayload.valAt(keyword("payload"))));
        } finally {
            Var.popThreadBindings();
        }
    }

    @Test
    public void clojureReaderAttachesSourceMetadataFromSourceLoggingReader() {
        Object reader = sourceLoggingPushBackReader.invoke("(alpha\n beta) {:k 1}", 2, "forms.clj");

        Object list = readerRead.invoke(reader);
        Object map = readerRead.invoke(reader);

        IPersistentMap listMetadata = assertInstanceOf(IPersistentMap.class, ((IMeta) list).meta());
        IPersistentMap mapMetadata = assertInstanceOf(IPersistentMap.class, ((IMeta) map).meta());
        IPersistentMap parsedMap = assertInstanceOf(IPersistentMap.class, map);

        assertAll(
                () -> assertEquals("(alpha\n beta)", listMetadata.valAt(SOURCE)),
                () -> assertEquals("{:k 1}", mapMetadata.valAt(SOURCE)),
                () -> assertEquals(Symbol.intern("alpha"), RT.first(list)),
                () -> assertEquals(2, ((Counted) list).count()),
                () -> assertEquals(1L, parsedMap.valAt(keyword("k"))));
    }

    @Test
    public void readerTypesExposePushbackPeekingInputStreamAndIndexingState() {
        byte[] bytes = "ab\nç".getBytes(StandardCharsets.UTF_8);
        Object pushbackReader = inputStreamPushBackReader.invoke(new ByteArrayInputStream(bytes), 2);

        Object first = readChar.invoke(pushbackReader);
        Object second = peekChar.invoke(pushbackReader);
        unread.invoke(pushbackReader, first);
        Object reread = readChar.invoke(pushbackReader);

        Object indexing = indexingPushBackReader.invoke("x\ny", 2, "sample.clj");
        Object indexingFirst = readChar.invoke(indexing);
        Object indexingSecond = readChar.invoke(indexing);

        assertAll(
                () -> assertEquals('a', first),
                () -> assertEquals('b', second),
                () -> assertEquals('a', reread),
                () -> assertSame(Boolean.TRUE, indexingReader.invoke(indexing)),
                () -> assertEquals("sample.clj", getFileName.invoke(indexing)),
                () -> assertEquals('x', indexingFirst),
                () -> assertEquals('\n', indexingSecond),
                () -> assertEquals(2, ((Number) getLineNumber.invoke(indexing)).intValue()),
                () -> assertTrue(((Number) getColumnNumber.invoke(indexing)).intValue() >= 1),
                () -> assertFalse((Boolean) indexingReader.invoke(pushbackReader)));
    }

    private static Keyword keyword(String name) {
        return Keyword.intern(null, name);
    }

    private static IPersistentVector vectorAt(IPersistentMap map, String key) {
        return assertInstanceOf(IPersistentVector.class, map.valAt(keyword(key)));
    }
}
