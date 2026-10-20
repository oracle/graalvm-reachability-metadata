/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_netflix_curator.curator_framework;

import java.util.Arrays;
import java.util.List;

import com.google.common.collect.Iterables;
import com.netflix.curator.framework.api.transaction.CuratorTransactionResult;
import com.netflix.curator.framework.api.transaction.OperationType;
import org.apache.zookeeper.data.Stat;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class CuratorTransactionResultTest {
    @Test
    void findsTheResultForASpecificTransactionOperationAndPath() {
        Stat createdStat = new Stat();
        CuratorTransactionResult deleted =
                new CuratorTransactionResult(OperationType.DELETE, "/obsolete", null, null);
        CuratorTransactionResult created =
                new CuratorTransactionResult(OperationType.CREATE, "/jobs", "/jobs/job-0001", createdStat);
        CuratorTransactionResult otherCreate =
                new CuratorTransactionResult(OperationType.CREATE, "/workers", "/workers/worker-0001", null);
        List<CuratorTransactionResult> results = Arrays.asList(deleted, created, otherCreate);

        CuratorTransactionResult matching = Iterables.find(
                results, CuratorTransactionResult.ofTypeAndPath(OperationType.CREATE, "/jobs"));

        assertThat(matching).isSameAs(created);
        assertThat(matching.getType()).isEqualTo(OperationType.CREATE);
        assertThat(matching.getForPath()).isEqualTo("/jobs");
        assertThat(matching.getResultPath()).isEqualTo("/jobs/job-0001");
        assertThat(matching.getResultStat()).isSameAs(createdStat);
    }
}
