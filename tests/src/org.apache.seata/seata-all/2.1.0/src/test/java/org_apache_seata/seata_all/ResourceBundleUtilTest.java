/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_seata.seata_all;

import java.util.Locale;

import org.apache.seata.common.exception.ResourceBundleUtil;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ResourceBundleUtilTest {
    @Test
    void constructorLoadsErrorCodeResourceBundleForEnglishLocale() {
        ResourceBundleUtil resourceBundleUtil = new ResourceBundleUtil("error/ErrorCode", Locale.US);

        assertThat(resourceBundleUtil.getMessage("ERR_CONFIG", "missing-key"))
                .isEqualTo("config error, missing-key");
    }

    @Test
    void getMessageFormatsErrorPrefixAndPostfixFromBundle() {
        ResourceBundleUtil resourceBundleUtil = new ResourceBundleUtil("error/ErrorCode", Locale.US);

        assertThat(resourceBundleUtil.getMessage("ERR_CONFIG", 1, "Config", "missing-key"))
                .isEqualTo("ERR-CODE: [Seata-1][ERR_CONFIG] config error, missing-key More: [https://seata.apache.org/docs/next/overview/faq#1]");
    }
}
