/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_reactivex_rxjava3.rxjava;

import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.observables.GroupedObservable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.Comparator;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** GroupedObservable streams build regional sales summaries for an application dashboard. */
public class GroupedObservableRegionalSalesTest {
    @Test
    @Timeout(60)
    public void salesAreGroupedByRegionAndSummarizedForTheDashboard() {
        Observable<Sale> sales = Observable.fromIterable(List.of(
                new Sale("west", "ORD-101", 1200),
                new Sale("east", "ORD-102", 800),
                new Sale("west", "ORD-103", 450),
                new Sale("east", "ORD-104", 1250),
                new Sale("north", "ORD-105", 600)));

        List<RegionSummary> summaries = sales
                .groupBy(Sale::region)
                .flatMapSingle(GroupedObservableRegionalSalesTest::summarize)
                .toSortedList(Comparator.comparing(RegionSummary::region))
                .blockingGet();

        assertEquals(List.of(
                new RegionSummary("east", 2, 2050),
                new RegionSummary("north", 1, 600),
                new RegionSummary("west", 2, 1650)), summaries);
    }

    private static Single<RegionSummary> summarize(
            GroupedObservable<String, Sale> group) {
        return group.toList().map(sales -> new RegionSummary(group.getKey(), sales.size(),
                sales.stream().mapToInt(Sale::amountCents).sum()));
    }

    private record Sale(String region, String orderId, int amountCents) {
    }

    private record RegionSummary(String region, int orderCount, int totalCents) {
    }
}
