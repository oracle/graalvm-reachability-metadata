/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_shardingsphere_elasticjob.elasticjob_lite_core.repository;

import org_apache_shardingsphere_elasticjob.elasticjob_lite_core.entity.TOrderPOJO;
import org_apache_shardingsphere_elasticjob.elasticjob_lite_core.entity.TableStatus;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.stream.LongStream;

public class VirtualTOrderRepository {
    private final Map<Long, TOrderPOJO> data = new ConcurrentHashMap<>(300, 1);

    public VirtualTOrderRepository() {
        addData(0L, 100L, "Norddorf");
        addData(100L, 200L, "Bordeaux");
        addData(200L, 300L, "Somerset");
    }

    private void addData(final long startId, final long endId, final String location) {
        LongStream.range(startId, endId).forEachOrdered(i -> data.put(i, new TOrderPOJO(i, location, TableStatus.TODO)));
    }

    public List<TOrderPOJO> findTodoData(final String location, final int limitNumber) {
        return data.entrySet().stream()
                .limit(limitNumber)
                .map(Map.Entry::getValue)
                .filter(tOrderPOJO -> location.equals(tOrderPOJO.location()) && TableStatus.TODO == tOrderPOJO.tableStatus())
                .collect(Collectors.toCollection(() -> new ArrayList<>(limitNumber)));
    }

    public void setCompleted(final long id) {
        data.replace(id, new TOrderPOJO(id, data.get(id).location(), TableStatus.COMPLETED));
    }
}
