/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_hibernate.hibernate_core;

import org.hibernate.jpa.HibernateHints;
import org.hibernate.jpa.QueryHints;
import org.hibernate.jpa.SpecHints;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

public class HintsCollectorTest {

    @Test
    public void collectsHibernateAndPersistenceHints() {
        Set<String> hints = QueryHints.getDefinedHints();

        assertThat(hints)
                .contains(HibernateHints.HINT_CACHEABLE, SpecHints.HINT_SPEC_FETCH_GRAPH);
    }
}
