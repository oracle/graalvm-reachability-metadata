/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_univocity.univocity_parsers;

import com.univocity.parsers.common.ArgumentUtils;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ArgumentUtilsTest {
    @Test
    void findsDuplicatesUsingInputArrayComponentType() {
        String[] values = {"id", "name", "id", "email", "name", "id"};

        String[] duplicates = ArgumentUtils.findDuplicates(values);

        assertThat(duplicates).isInstanceOf(String[].class);
        assertThat(duplicates).containsExactly("id", "name", "id");
    }
}
