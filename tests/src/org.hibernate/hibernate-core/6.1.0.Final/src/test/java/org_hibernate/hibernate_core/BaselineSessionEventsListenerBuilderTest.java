/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_hibernate.hibernate_core;

import org.hibernate.BaseSessionEventListener;
import org.hibernate.cfg.BaselineSessionEventsListenerBuilder;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class BaselineSessionEventsListenerBuilderTest {

    @Test
    public void createsTheConfiguredAutomaticListener() {
        BaselineSessionEventsListenerBuilder builder =
                new BaselineSessionEventsListenerBuilder(false, RecordingListener.class);

        assertThat(builder.buildBaseline())
                .singleElement()
                .isInstanceOf(RecordingListener.class);
    }

    public static class RecordingListener extends BaseSessionEventListener {
    }
}
