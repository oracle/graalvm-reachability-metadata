/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_seata.seata_all;

import java.util.Map;

import org.apache.seata.integration.tx.api.interceptor.ActionContextUtil;
import org.apache.seata.rm.tcc.api.BusinessActionContextParameter;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ActionContextUtilTest {
    @Test
    void fetchContextFromObjectReadsAnnotatedFieldsFromTheTargetAndNestedProperties() {
        TryRequest request = new TryRequest("branch-7", new Participant("user-42", 3), "ignored");

        Map<String, Object> context = ActionContextUtil.fetchContextFromObject(request);

        assertThat(context)
                .containsEntry("branchCode", "branch-7")
                .containsEntry("accountId", "user-42")
                .containsEntry("retryCount", 3)
                .hasSize(3);
    }

    private static final class TryRequest {
        @BusinessActionContextParameter("branchCode")
        private final String branch;

        @BusinessActionContextParameter(isParamInProperty = true)
        private final Participant participant;

        private final String ignored;

        private TryRequest(String branch, Participant participant, String ignored) {
            this.branch = branch;
            this.participant = participant;
            this.ignored = ignored;
        }
    }

    private static final class Participant {
        @BusinessActionContextParameter("accountId")
        private final String userId;

        @BusinessActionContextParameter
        private final Integer retryCount;

        private final String ignored = "ignored";

        private Participant(String userId, Integer retryCount) {
            this.userId = userId;
            this.retryCount = retryCount;
        }
    }
}
