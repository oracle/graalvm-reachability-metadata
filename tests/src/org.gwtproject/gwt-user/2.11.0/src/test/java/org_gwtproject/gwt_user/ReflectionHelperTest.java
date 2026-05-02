/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_gwtproject.gwt_user;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.gwt.user.client.rpc.impl.ReflectionHelper;

import org.junit.jupiter.api.Test;

public class ReflectionHelperTest {
    @Test
    void loadsClassCreatesInstanceAndMutatesPrivateField() throws Exception {
        Class<? extends ReflectiveRecord> recordType = ReflectionHelper
                .loadClass(ReflectiveRecord.class.getName())
                .asSubclass(ReflectiveRecord.class);

        ReflectiveRecord record = ReflectionHelper.newInstance(recordType);

        assertThat(record.message()).isEqualTo("constructed");
        assertThat(ReflectionHelper.getField(recordType, record, "message"))
                .isEqualTo("constructed");

        ReflectionHelper.setField(recordType, record, "message", "updated");

        assertThat(record.message()).isEqualTo("updated");
        assertThat(ReflectionHelper.getField(recordType, record, "message"))
                .isEqualTo("updated");
    }

    private static final class ReflectiveRecord {
        private String message;

        private ReflectiveRecord() {
            message = "constructed";
        }

        String message() {
            return message;
        }
    }
}
