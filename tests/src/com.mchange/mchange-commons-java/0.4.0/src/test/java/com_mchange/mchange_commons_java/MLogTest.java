/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_mchange.mchange_commons_java;

import com.mchange.v2.log.MLog;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class MLogTest {
    private static final String MLOG_PROPERTY = "com.mchange.v2.log.MLog";
    private static final String NAME_TRANSFORMER_PROPERTY = "com.mchange.v2.log.NameTransformer";

    @Test
    void refreshConfigLoadsConfiguredNameTransformer() {
        String previousMLog = System.getProperty(MLOG_PROPERTY);
        String previousNameTransformer = System.getProperty(NAME_TRANSFORMER_PROPERTY);

        try {
            System.setProperty(MLOG_PROPERTY, "jdk14");
            System.setProperty(NAME_TRANSFORMER_PROPERTY, "com.mchange.v2.log.PackageNames");

            MLog.refreshConfig(null, null);

            assertThat(MLog.getLogger(MLogTest.class).getName()).isEqualTo(MLogTest.class.getPackageName());
        } finally {
            restoreSystemProperty(MLOG_PROPERTY, previousMLog);
            restoreSystemProperty(NAME_TRANSFORMER_PROPERTY, previousNameTransformer);
            MLog.refreshConfig(null, null);
        }
    }

    private static void restoreSystemProperty(String key, String value) {
        if (value == null) {
            System.clearProperty(key);
        } else {
            System.setProperty(key, value);
        }
    }
}
