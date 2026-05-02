/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_gwtproject.gwt_user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.google.gwt.core.client.GWT;
import com.google.gwt.junit.GWTMockUtilities;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

public class GWTMockUtilitiesTest {
    @AfterEach
    public void restoreGwtBridge() {
        GWTMockUtilities.restore();
    }

    @Test
    public void disarmInstallsDummyBridgeAndRestoreClearsIt() {
        GWTMockUtilities.restore();
        assertGwtCreateIsUnsupportedWithoutBridge();

        GWTMockUtilities.disarm();

        Object created = GWT.create(Object.class);

        assertThat(created).isNull();
        assertThat(GWT.isClient()).isFalse();

        GWTMockUtilities.restore();
        assertGwtCreateIsUnsupportedWithoutBridge();
    }

    private static void assertGwtCreateIsUnsupportedWithoutBridge() {
        assertThatThrownBy(() -> GWT.create(Object.class))
                .isInstanceOf(UnsupportedOperationException.class)
                .hasMessageContaining("GWT.create()");
    }
}
