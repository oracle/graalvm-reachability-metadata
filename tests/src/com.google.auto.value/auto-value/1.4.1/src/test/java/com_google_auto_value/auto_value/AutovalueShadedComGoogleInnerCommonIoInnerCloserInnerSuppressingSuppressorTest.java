/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_google_auto_value.auto_value;

import autovalue.shaded.com.google$.common.io.$Closer;

import java.io.Closeable;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

public class AutovalueShadedComGoogleInnerCommonIoInnerCloserInnerSuppressingSuppressorTest {
    @Test
    void closeAddsLaterCloseFailuresAsSuppressedExceptions() {
        final List<String> closedResources = new ArrayList<>();
        final $Closer closer = $Closer.create();

        closer.register(new FailingCloseable("secondary", closedResources));
        closer.register(new FailingCloseable("primary", closedResources));

        final IOException thrown = catchThrowableOfType(closer::close, IOException.class);

        assertThat(closedResources).containsExactly("primary", "secondary");
        assertThat(thrown)
                .isNotNull()
                .hasMessage("primary");
        assertThat(thrown.getSuppressed())
                .singleElement()
                .isInstanceOfSatisfying(IOException.class, suppressed -> assertThat(suppressed).hasMessage("secondary"));
    }

    private static final class FailingCloseable implements Closeable {
        private final String name;
        private final List<String> closedResources;

        private FailingCloseable(String name, List<String> closedResources) {
            this.name = name;
            this.closedResources = closedResources;
        }

        @Override
        public void close() throws IOException {
            this.closedResources.add(this.name);
            throw new IOException(this.name);
        }
    }
}
