/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_mysema_commons.mysema_commons_lang;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.mysema.commons.lang.Assert;
import com.mysema.commons.lang.CloseableIterator;
import com.mysema.commons.lang.EmptyCloseableIterator;
import com.mysema.commons.lang.IteratorAdapter;
import com.mysema.commons.lang.Pair;
import com.mysema.commons.lang.URIResolver;
import com.mysema.commons.lang.URLEncoder;
import java.io.Closeable;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import org.junit.jupiter.api.Test;

public class Mysema_commons_langTest {
    @Test
    void assertionHelpersReturnOriginalValuesWhenConditionsAreSatisfied() {
        String text = "  value  ";
        String[] array = new String[] {"alpha", "beta"};
        Collection<String> collection = Arrays.asList("one", "two");
        Map<String, Integer> map = new LinkedHashMap<>();
        map.put("answer", 42);

        assertThat(Assert.hasText(text, "text")).isSameAs(text);
        assertThat(Assert.hasLength(text, "text")).isSameAs(text);
        assertThat(Assert.notNull(collection, "collection")).isSameAs(collection);
        assertThat(Assert.notEmpty(array, "array")).isSameAs(array);
        assertThat(Assert.notEmpty(collection, "collection")).isSameAs(collection);
        assertThat(Assert.notEmpty(map, "map")).isSameAs(map);
        assertThat(Assert.isTrue(5 > 2, "comparison")).isTrue();
        assertThat(Assert.isFalse(2 > 5, "comparison")).isFalse();
        assertThat(Assert.assertThat(true, "custom", "failed", "returned")).isEqualTo("returned");
    }

    @Test
    void assertionHelpersBuildPropertyMessagesAndPreserveExplicitMessages() {
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> Assert.notNull(null, "input"))
                .withMessage("input should not be null");
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> Assert.hasLength("", "name"))
                .withMessage("name should not be empty");
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> Assert.hasText(" \t\n", "title"))
                .withMessage("title should have text");
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> Assert.notEmpty(new String[0], "array"))
                .withMessage("array should not be empty");
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> Assert.notEmpty(new ArrayList<String>(), "items"))
                .withMessage("items should not be empty");
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> Assert.notEmpty(new HashMap<String, String>(), "mapping"))
                .withMessage("mapping should not be empty");
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> Assert.isTrue(false, "must be enabled"))
                .withMessage("must be enabled");
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> Assert.isFalse(true, "flag"))
                .withMessage("flag is false");
    }

    @Test
    void pairStoresTypedValuesAndUsesBothValuesForEqualityAndHashCode() {
        Pair<String, Integer> pair = Pair.of("left", 7);
        Pair<String, Integer> same = new Pair<>("left", 7);
        Pair<String, Integer> differentFirst = Pair.of("right", 7);
        Pair<String, Integer> differentSecond = Pair.of("left", 8);
        Pair<String, Integer> withNull = Pair.of(null, 7);
        Pair<String, Integer> sameNull = new Pair<>(null, 7);

        assertThat(pair.getFirst()).isEqualTo("left");
        assertThat(pair.getSecond()).isEqualTo(7);
        assertThat(pair).isEqualTo(pair);
        assertThat(pair).isEqualTo(same);
        assertThat(pair.hashCode()).isEqualTo(same.hashCode());
        assertThat(withNull).isEqualTo(sameNull);
        assertThat(withNull.hashCode()).isEqualTo(sameNull.hashCode());
        assertThat(pair).isNotEqualTo(differentFirst);
        assertThat(pair).isNotEqualTo(differentSecond);
        assertThat(pair).isNotEqualTo("left");
        assertThat(pair).isNotEqualTo(null);
    }

    @Test
    void emptyCloseableIteratorBehavesLikeAnEmptyIterator() {
        CloseableIterator<String> iterator = new EmptyCloseableIterator<>();

        assertThat(iterator.hasNext()).isFalse();
        assertThatThrownBy(iterator::next).isInstanceOf(NoSuchElementException.class);
        assertThatThrownBy(iterator::remove).isInstanceOf(UnsupportedOperationException.class);
        iterator.close();
        iterator.close();
    }

    @Test
    void iteratorAdapterDelegatesIterationRemovalAndExplicitClosing() {
        List<String> values = new ArrayList<>(Arrays.asList("first", "second", "third"));
        RecordingCloseable closeable = new RecordingCloseable();
        IteratorAdapter<String> adapter = new IteratorAdapter<>(values.iterator(), closeable);

        assertThat(adapter.hasNext()).isTrue();
        assertThat(adapter.next()).isEqualTo("first");
        adapter.remove();
        assertThat(values).containsExactly("second", "third");
        assertThat(adapter.asList()).containsExactly("second", "third");

        adapter.close();
        adapter.close();
        assertThat(closeable.closeCount).isEqualTo(2);
    }

    @Test
    void iteratorAdapterUsesIteratorAsCloseableAndWrapsCloseFailures() {
        RecordingCloseableIterator iterator = new RecordingCloseableIterator("a", "b");
        IteratorAdapter<String> adapter = new IteratorAdapter<>(iterator);

        assertThat(adapter.next()).isEqualTo("a");
        adapter.close();
        assertThat(iterator.closeCount).isEqualTo(1);

        IOException failure = new IOException("cannot close");
        IteratorAdapter<String> failingAdapter = new IteratorAdapter<>(
                Arrays.asList("x").iterator(), new FailingCloseable(failure));
        assertThatExceptionOfType(RuntimeException.class)
                .isThrownBy(failingAdapter::close)
                .withMessage("cannot close")
                .withCause(failure);
    }

    @Test
    void staticAsListConsumesIteratorAndClosesCloseableIterators() {
        RecordingCloseableIterator iterator = new RecordingCloseableIterator("red", "green", "blue");

        assertThat(IteratorAdapter.asList(iterator)).containsExactly("red", "green", "blue");
        assertThat(iterator.closeCount).isEqualTo(1);
        assertThat(iterator.hasNext()).isFalse();
    }

    @Test
    void staticAsListClosesCloseableIteratorWhenIterationFails() {
        FailingNextCloseableIterator iterator = new FailingNextCloseableIterator();

        assertThatThrownBy(() -> IteratorAdapter.asList(iterator))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("cannot read next item");
        assertThat(iterator.closeCount).isEqualTo(1);
    }

    @Test
    void staticAsListWrapsCloseFailuresFromCloseableIterators() {
        IOException failure = new IOException("cannot close iterator");
        FailingCloseCloseableIterator iterator = new FailingCloseCloseableIterator(failure, "one", "two");

        assertThatThrownBy(() -> IteratorAdapter.asList(iterator))
                .isInstanceOf(RuntimeException.class)
                .hasCause(failure);
        assertThat(iterator.closeCount).isEqualTo(1);
    }

    @Test
    void uriResolverRecognizesAbsoluteUrlsAndRejectsRelativeOrInvalidSchemes() {
        assertThat(URIResolver.isAbsoluteURL("http://example.com/path")).isTrue();
        assertThat(URIResolver.isAbsoluteURL("git+ssh://example.com/repository.git")).isTrue();
        assertThat(URIResolver.isAbsoluteURL("urn:isbn:9780134685991")).isTrue();
        assertThat(URIResolver.isAbsoluteURL(null)).isFalse();
        assertThat(URIResolver.isAbsoluteURL("../relative/path")).isFalse();
        assertThat(URIResolver.isAbsoluteURL("http example://host")).isFalse();
    }

    @Test
    void uriResolverHandlesAbsoluteQueryFragmentAndPathResolution() {
        assertThat(URIResolver.resolve("http://example.com/base/index.html?old=1", "?new=2"))
                .isEqualTo("http://example.com/base/index.html?new=2");
        assertThat(URIResolver.resolve("http://example.com/base/index.html", "?new=2"))
                .isEqualTo("http://example.com/base/index.html?new=2");
        assertThat(URIResolver.resolve("http://example.com/base/index.html#old", "#section-2"))
                .isEqualTo("http://example.com/base/index.html#section-2");
        assertThat(URIResolver.resolve("http://example.com/base/index.html", "#section-2"))
                .isEqualTo("http://example.com/base/index.html#section-2");
        assertThat(URIResolver.resolve("http://example.com/base/docs/index.html", "../images/logo.png"))
                .isEqualTo("http://example.com/base/images/logo.png");
        assertThat(URIResolver.resolve("http://example.com/base/docs/index.html", "https://cdn.example.com/a.js"))
                .isEqualTo("https://cdn.example.com/a.js");
    }

    @Test
    void urlEncoderEncodesParametersUsingRequestedCharset() {
        assertThat(URLEncoder.encodeParam("a b+c/d?\u00E4", "UTF-8"))
                .isEqualTo("a+b%2Bc%2Fd%3F%C3%A4");
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> URLEncoder.encodeParam("value", "not-a-real-charset"))
                .withCauseInstanceOf(IOException.class);
    }

    @Test
    void urlEncoderKeepsUrlDelimitersButEscapesUnsafeAsciiAndUnicode() {
        assertThat(URLEncoder.encodeURL("https://example.com/a path?q=hello world&x=1+2#\u00E4\u20AC"))
                .isEqualTo("https%3a//example.com/a+path?q=hello+world&x=1%2b2%23%c3%a4%e2%82%ac");
        assertThat(URLEncoder.encodeURL("AZaz09-_.!~*\\()/&=?"))
                .isEqualTo("AZaz09-_.!~*\\()/&=?");
    }

    private static final class RecordingCloseable implements Closeable {
        private int closeCount;

        @Override
        public void close() {
            closeCount++;
        }
    }

    private static final class FailingCloseable implements Closeable {
        private final IOException failure;

        private FailingCloseable(IOException failure) {
            this.failure = failure;
        }

        @Override
        public void close() throws IOException {
            throw failure;
        }
    }

    private static final class RecordingCloseableIterator implements Iterator<String>, Closeable {
        private final Iterator<String> delegate;
        private int closeCount;

        private RecordingCloseableIterator(String... values) {
            this.delegate = Arrays.asList(values).iterator();
        }

        @Override
        public boolean hasNext() {
            return delegate.hasNext();
        }

        @Override
        public String next() {
            return delegate.next();
        }

        @Override
        public void remove() {
            delegate.remove();
        }

        @Override
        public void close() {
            closeCount++;
        }
    }

    private static final class FailingNextCloseableIterator implements Iterator<String>, Closeable {
        private int closeCount;

        @Override
        public boolean hasNext() {
            return true;
        }

        @Override
        public String next() {
            throw new IllegalStateException("cannot read next item");
        }

        @Override
        public void close() {
            closeCount++;
        }
    }

    private static final class FailingCloseCloseableIterator implements Iterator<String>, Closeable {
        private final Iterator<String> delegate;
        private final IOException failure;
        private int closeCount;

        private FailingCloseCloseableIterator(IOException failure, String... values) {
            this.delegate = Arrays.asList(values).iterator();
            this.failure = failure;
        }

        @Override
        public boolean hasNext() {
            return delegate.hasNext();
        }

        @Override
        public String next() {
            return delegate.next();
        }

        @Override
        public void close() throws IOException {
            closeCount++;
            throw failure;
        }
    }
}
