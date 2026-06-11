/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_eclipse_jgit.org_eclipse_jgit;

import org.eclipse.jgit.lib.ObjectId;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class TranslationBundleTest {

    @Test
    void formatsLocalizedExceptionMessageForInvalidObjectId() {
        assertThatThrownBy(() -> ObjectId.fromString("not-a-valid-object-id"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid");
    }
}
