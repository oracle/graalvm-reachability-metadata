/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_google_cloud.google_cloud_core;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.cloud.ServiceOptions;
import org.junit.jupiter.api.Test;

public class ServiceOptionsTest {

    @Test
    void newInstanceCreatesPublicNoArgumentClass() throws Exception {
        ServiceOptionsNewInstanceTarget target = ServiceOptions.newInstance(
            ServiceOptionsNewInstanceTarget.class.getName());

        assertThat(target.createdByDefaultConstructor).isTrue();
    }

    public static final class ServiceOptionsNewInstanceTarget {
        private final boolean createdByDefaultConstructor;

        public ServiceOptionsNewInstanceTarget() {
            this.createdByDefaultConstructor = true;
        }
    }
}
