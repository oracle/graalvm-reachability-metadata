/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_hibernate.hibernate_core;

import org.hibernate.jpa.internal.util.PersistenceUtilHelper;
import org.junit.jupiter.api.Test;

import jakarta.persistence.spi.LoadState;

import static org.assertj.core.api.Assertions.assertThat;

public class PersistenceUtilHelperInnerFieldAttributeAccessTest {

    @Test
    public void readsAnApplicationFieldWhenDeterminingLoadState() {
        LoadState state = PersistenceUtilHelper.isLoadedWithReference(
                new FieldValue(),
                "value",
                new PersistenceUtilHelper.MetadataCache()
        );

        assertThat(state).isEqualTo(LoadState.UNKNOWN);
    }

    public static class FieldValue {
        private String value = "hibernate";
    }
}
