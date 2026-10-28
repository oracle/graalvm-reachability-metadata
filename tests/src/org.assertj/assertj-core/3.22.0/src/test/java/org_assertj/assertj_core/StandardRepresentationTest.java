/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_assertj.assertj_core;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Iterator;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import org.assertj.core.presentation.StandardRepresentation;
import org.junit.jupiter.api.Test;

public class StandardRepresentationTest {
    @Test
    void formatsAnIterableButRespectsItsCustomToString() {
        StandardRepresentation representation = new StandardRepresentation();
        assertThat(representation.toStringOf(new Stages())).isEqualTo("[\"queued\", \"sent\"]");
        assertThat(representation.toStringOf(new NamedStages())).isEqualTo("delivery stages");
    }

    @Test
    void formatsAtomicReferencesUnlessASubclassProvidesADescription() {
        StandardRepresentation representation = new StandardRepresentation();
        assertThat(representation.toStringOf(new AtomicReference<>("queued")))
                .isEqualTo("AtomicReference[\"queued\"]");
        assertThat(representation.toStringOf(new StatusReference()))
                .isEqualTo("StatusReference[\"queued\"]");
        assertThat(representation.toStringOf(new NamedStatusReference())).isEqualTo("delivery status");
    }

    public static class Stages implements Iterable<String> {
        @Override
        public Iterator<String> iterator() {
            return List.of("queued", "sent").iterator();
        }
    }

    public static class NamedStages extends Stages {
        @Override
        public String toString() {
            return "delivery stages";
        }
    }

    public static class StatusReference extends AtomicReference<String> {
        public StatusReference() {
            super("queued");
        }
    }

    public static class NamedStatusReference extends StatusReference {
        @Override
        public String toString() {
            return "delivery status";
        }
    }
}
