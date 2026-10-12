/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_modulith.spring_modulith_events_jpa;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import org.springframework.modulith.events.EventPublication.Status;
import org.springframework.modulith.events.jpa.archiving.ArchivedJpaEventPublication;
import org.springframework.modulith.events.jpa.updating.DefaultJpaEventPublication;

import static org.assertj.core.api.Assertions.assertThat;

public class Spring_modulith_events_jpaTest {

    @Test
    void createsPublicEventPublicationEntities() {
        UUID identifier = UUID.fromString("711e86d0-bbf6-4e3d-a12d-a66394d7e031");
        Instant publishedAt = Instant.parse("2025-01-02T10:15:30Z");
        DefaultJpaEventPublication defaultPublication = new DefaultJpaEventPublication(
                identifier,
                publishedAt,
                "inventory-listener",
                "item-42",
                String.class,
                Status.PUBLISHED,
                publishedAt,
                1);
        ArchivedJpaEventPublication archivedPublication = new ArchivedJpaEventPublication(
                identifier,
                publishedAt,
                "billing-listener",
                "invoice-7",
                String.class,
                Status.COMPLETED,
                publishedAt,
                2);

        assertThat(defaultPublication).isEqualTo(archivedPublication);
        assertThat(defaultPublication.hashCode()).isEqualTo(identifier.hashCode());
    }
}
