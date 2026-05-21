/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_google_http_client.google_http_client;

import com.google.api.client.util.Data;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class DataTest {

    @Test
    public void cloneCreatesIndependentArrayInstance() {
        String[] original = {"alpha", "beta"};

        String[] copy = Data.clone(original);

        assertThat(copy).isNotSameAs(original);
        assertThat(copy).containsExactly("alpha", "beta");
    }

    @Test
    public void nullOfCreatesMagicNullForMultidimensionalArray() {
        DataTest[][] nullArray = Data.nullOf(DataTest[][].class);

        assertThat(nullArray).isInstanceOf(DataTest[][].class);
        assertThat(nullArray).isEmpty();
        assertThat(Data.isNull(nullArray)).isTrue();
        assertThat(Data.nullOf(DataTest[][].class)).isSameAs(nullArray);
    }
}
