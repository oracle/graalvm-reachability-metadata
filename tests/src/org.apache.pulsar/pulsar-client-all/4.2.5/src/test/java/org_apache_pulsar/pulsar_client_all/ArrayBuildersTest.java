/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_pulsar.pulsar_client_all;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.pulsar.shade.com.fasterxml.jackson.databind.util.ArrayBuilders;
import org.junit.jupiter.api.Test;

public class ArrayBuildersTest {

    @Test
    void prependsNewValuesAndMovesExistingValuesToTheFront() {
        final String[] original = {"secondary", "primary"};

        assertThat(ArrayBuilders.insertInListNoDup(original, "new")).containsExactly("new", "secondary", "primary");
        assertThat(ArrayBuilders.insertInListNoDup(original, original[1])).containsExactly("primary", "secondary");
    }
}
