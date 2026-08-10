/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_junit_jupiter.junit_jupiter_params;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.shadow.com.univocity.parsers.common.ArgumentUtils;

public class ArgumentUtilsTest {

    @Test
    void findsDuplicateValuesInAnArray() {
        String[] duplicates = ArgumentUtils.findDuplicates(new String[] {"first", "second", "first", "second"});

        assertThat(duplicates).containsExactly("first", "second");
    }
}
