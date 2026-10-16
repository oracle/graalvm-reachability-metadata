/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_google_guava.guava;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.common.collect.ArrayTable;
import com.google.common.collect.BiMap;
import com.google.common.collect.ConcurrentHashMultiset;
import com.google.common.collect.EnumBiMap;
import com.google.common.collect.EnumHashBiMap;
import com.google.common.collect.EnumMultiset;
import com.google.common.collect.HashBasedTable;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.ImmutableRangeMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableTable;
import com.google.common.collect.Iterables;
import com.google.common.collect.LinkedHashMultimap;
import com.google.common.collect.LinkedListMultimap;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.MultimapBuilder;
import com.google.common.collect.Multimaps;
import com.google.common.collect.Multiset;
import com.google.common.collect.Multisets;
import com.google.common.collect.Range;
import com.google.common.collect.RangeMap;
import com.google.common.collect.SetMultimap;
import com.google.common.collect.SortedSetMultimap;
import com.google.common.collect.Table;
import com.google.common.collect.TreeMultimap;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

/**
 * Collection modelling with `com.google.common.collect` and its Java serialization: multimaps
 * built by `MultimapBuilder`, `TreeMultimap`, `LinkedListMultimap`, and `LinkedHashMultimap`,
 * enum-keyed bimaps and multisets, `ConcurrentHashMultiset`, tables, and range maps.
 *
 * A workforce planner keeps its weekly roster in Guava collections: who works which station,
 * which skills each employee holds, the on-call rota, live clock-in counts, an hours grid, and
 * overtime pay bands. It snapshots that state with Java serialization so a standby node can
 * restore it, and the restored collections must behave exactly like the originals, including
 * ordering, comparators, and mutability.
 */
public class StaffRosterSnapshotTest {

    @Test
    void restoresBuilderMadeMultimapsWithTheirOrderingAndMutability() {
        ListMultimap<String, String> stationShifts = MultimapBuilder.hashKeys().arrayListValues().build();
        stationShifts.putAll("grill", ImmutableList.of("ana", "ben", "ana"));
        stationShifts.putAll("till", ImmutableList.of("cleo"));

        SetMultimap<String, String> skills = MultimapBuilder.linkedHashKeys().linkedHashSetValues().build();
        skills.putAll("ben", ImmutableList.of("grill", "forklift", "grill"));
        skills.putAll("ana", ImmutableList.of("till", "grill"));

        SortedSetMultimap<String, Integer> badgeNumbers = MultimapBuilder.treeKeys().treeSetValues().build();
        badgeNumbers.putAll("cleo", ImmutableList.of(42, 7, 19));
        badgeNumbers.put("ana", 3);

        ListMultimap<String, String> restoredShifts = Snapshots.roundTrip(stationShifts);
        SetMultimap<String, String> restoredSkills = Snapshots.roundTrip(skills);
        SortedSetMultimap<String, Integer> restoredBadges = Snapshots.roundTrip(badgeNumbers);

        assertEquals(stationShifts, restoredShifts);
        assertEquals(ImmutableList.of("ana", "ben", "ana"), restoredShifts.get("grill"));
        assertEquals(ImmutableList.of("ben", "ana"), ImmutableList.copyOf(restoredSkills.keySet()));
        assertEquals(ImmutableList.of("grill", "forklift"), ImmutableList.copyOf(restoredSkills.get("ben")));
        assertEquals(ImmutableList.of("ana", "cleo"), ImmutableList.copyOf(restoredBadges.keySet()));
        assertEquals(ImmutableList.of(7, 19, 42), ImmutableList.copyOf(restoredBadges.get("cleo")));

        restoredShifts.put("till", "dev");
        restoredSkills.put("ben", "first-aid");
        restoredBadges.put("cleo", 1);
        assertEquals(ImmutableList.of("cleo", "dev"), restoredShifts.get("till"));
        assertEquals(3, restoredSkills.get("ben").size());
        assertEquals(1, restoredBadges.get("cleo").first());
        assertEquals(ImmutableList.of("cleo"), stationShifts.get("till"));

        ListMultimap<String, String> staffByStation = Multimaps.invertFrom(
                skills, MultimapBuilder.treeKeys().arrayListValues().build());
        assertEquals(ImmutableList.of("ben", "ana"), staffByStation.get("grill"));
        assertEquals(ImmutableList.of("forklift", "grill", "till"),
                ImmutableList.copyOf(staffByStation.keySet()));
    }

    @Test
    void restoresOrderedMultimapsAndTheirComparators() {
        TreeMultimap<Integer, String> seniority = TreeMultimap.create(Comparator.reverseOrder(),
                Comparator.naturalOrder());
        seniority.put(3, "cleo");
        seniority.put(10, "ben");
        seniority.put(3, "ana");
        seniority.put(1, "dev");

        LinkedListMultimap<String, Integer> clockLog = LinkedListMultimap.create();
        clockLog.put("ana", 900);
        clockLog.put("ben", 905);
        clockLog.put("ana", 1300);

        LinkedHashMultimap<String, String> handovers = LinkedHashMultimap.create();
        handovers.put("night", "restock");
        handovers.put("morning", "clean grill");
        handovers.put("night", "count till");

        TreeMultimap<Integer, String> restoredSeniority = Snapshots.roundTrip(seniority);
        LinkedListMultimap<String, Integer> restoredClockLog = Snapshots.roundTrip(clockLog);
        LinkedHashMultimap<String, String> restoredHandovers = Snapshots.roundTrip(handovers);

        assertEquals(ImmutableList.of(10, 3, 1), ImmutableList.copyOf(restoredSeniority.keySet()));
        assertEquals(ImmutableList.of("ana", "cleo"), ImmutableList.copyOf(restoredSeniority.get(3)));
        restoredSeniority.put(5, "eve");
        assertEquals(ImmutableList.of(10, 5, 3, 1), ImmutableList.copyOf(restoredSeniority.keySet()));

        assertEquals(ImmutableList.of("ana", "ben", "ana"), ImmutableList.copyOf(restoredClockLog.keys()));
        assertEquals(ImmutableList.of(900, 905, 1300), restoredClockLog.values());
        assertEquals(ImmutableList.of(900, 1300), restoredClockLog.get("ana"));

        assertEquals(ImmutableList.of("restock", "clean grill", "count till"),
                ImmutableList.copyOf(restoredHandovers.values()));
        assertEquals(handovers, restoredHandovers);

        ImmutableListMultimap<Integer, String> byNameLength = Multimaps.index(
                restoredClockLog.keySet(), String::length);
        assertEquals(ImmutableList.of("ana", "ben"), byNameLength.get(3));
        assertEquals(byNameLength, Snapshots.roundTrip(byNameLength));
    }

    @Test
    void restoresEnumKeyedRotaAndAbsenceCounts() {
        EnumBiMap<Team, DayOfWeek> onCall = EnumBiMap.create(Team.class, DayOfWeek.class);
        onCall.put(Team.PLATFORM, DayOfWeek.MONDAY);
        onCall.put(Team.PAYMENTS, DayOfWeek.WEDNESDAY);
        onCall.put(Team.SEARCH, DayOfWeek.FRIDAY);
        assertThrows(IllegalArgumentException.class, () -> onCall.put(Team.MOBILE, DayOfWeek.MONDAY));

        EnumHashBiMap<DayOfWeek, String> dutyManager = EnumHashBiMap.create(DayOfWeek.class);
        dutyManager.put(DayOfWeek.MONDAY, "ana");
        dutyManager.put(DayOfWeek.TUESDAY, "ben");
        dutyManager.forcePut(DayOfWeek.SATURDAY, "ana");

        EnumMultiset<DayOfWeek> absences = EnumMultiset.create(DayOfWeek.class);
        absences.add(DayOfWeek.MONDAY, 3);
        absences.add(DayOfWeek.FRIDAY, 5);
        absences.add(DayOfWeek.TUESDAY);

        EnumBiMap<Team, DayOfWeek> restoredOnCall = Snapshots.roundTrip(onCall);
        BiMap<DayOfWeek, Team> restoredByDay = Snapshots.roundTrip(onCall.inverse());
        EnumHashBiMap<DayOfWeek, String> restoredManagers = Snapshots.roundTrip(dutyManager);
        EnumMultiset<DayOfWeek> restoredAbsences = Snapshots.roundTrip(absences);

        assertEquals(onCall, restoredOnCall);
        assertEquals(DayOfWeek.class, restoredOnCall.valueType());
        assertEquals(Team.SEARCH, restoredByDay.get(DayOfWeek.FRIDAY));
        restoredByDay.put(DayOfWeek.THURSDAY, Team.MOBILE);
        assertEquals(DayOfWeek.THURSDAY, restoredByDay.inverse().get(Team.MOBILE));

        assertNull(restoredManagers.get(DayOfWeek.MONDAY));
        assertEquals(DayOfWeek.SATURDAY, restoredManagers.inverse().get("ana"));
        assertEquals(ImmutableList.of(DayOfWeek.TUESDAY, DayOfWeek.SATURDAY),
                ImmutableList.copyOf(restoredManagers.keySet()));

        assertEquals(ImmutableList.of(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.FRIDAY),
                ImmutableList.copyOf(restoredAbsences.elementSet()));
        assertEquals(DayOfWeek.FRIDAY,
                Multisets.copyHighestCountFirst(restoredAbsences).elementSet().iterator().next());
        assertEquals(9, restoredAbsences.size());
    }

    @Test
    void restoresConcurrentClockInsHoursGridAndPayBands() throws Exception {
        ConcurrentHashMultiset<String> clockIns = ConcurrentHashMultiset.create();
        List<String> staff = ImmutableList.of("ana", "ben", "cleo", "dev");
        ExecutorService terminals = Executors.newFixedThreadPool(4);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<?>> swipes = new ArrayList<>();
            for (int terminal = 0; terminal < 4; terminal++) {
                swipes.add(terminals.submit(() -> {
                    start.await();
                    for (int round = 0; round < 250; round++) {
                        for (String employee : staff) {
                            clockIns.add(employee);
                        }
                    }
                    return null;
                }));
            }
            start.countDown();
            for (Future<?> swipe : swipes) {
                swipe.get(30, TimeUnit.SECONDS);
            }
        } finally {
            terminals.shutdown();
        }
        assertTrue(terminals.awaitTermination(30, TimeUnit.SECONDS));

        Multiset<String> restoredClockIns = Snapshots.roundTrip(clockIns);
        assertEquals(4000, restoredClockIns.size());
        assertEquals(1000, restoredClockIns.count("cleo"));
        assertTrue(restoredClockIns.setCount("cleo", 1000, 1001));

        ArrayTable<String, DayOfWeek, Integer> hours = ArrayTable.create(staff,
                ImmutableList.of(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY));
        hours.put("ana", DayOfWeek.MONDAY, 8);
        hours.put("ana", DayOfWeek.TUESDAY, 6);
        hours.put("dev", DayOfWeek.WEDNESDAY, 4);

        ArrayTable<String, DayOfWeek, Integer> restoredHours = Snapshots.roundTrip(hours);
        Integer[][] grid = restoredHours.toArray(Integer.class);
        assertEquals(4, grid.length);
        assertArrayEquals(new Integer[] {8, 6, null}, grid[0]);
        assertArrayEquals(new Integer[] {null, null, 4}, grid[3]);
        assertThrows(IllegalArgumentException.class, () -> restoredHours.put("eve", DayOfWeek.MONDAY, 1));

        Table<String, String, BigDecimal> tips = HashBasedTable.create();
        tips.put("ana", "grill", new BigDecimal("12.50"));
        tips.put("ben", "till", new BigDecimal("3.00"));
        Table<String, String, BigDecimal> restoredTips = Snapshots.roundTrip(tips);
        assertEquals(tips, restoredTips);
        ImmutableTable<String, String, BigDecimal> frozenTips = ImmutableTable.copyOf(restoredTips);
        assertEquals(frozenTips, Snapshots.roundTrip(frozenTips));
        assertEquals(ImmutableSet.of("grill", "till"), frozenTips.columnKeySet());

        RangeMap<Integer, BigDecimal> payMultiplier = ImmutableRangeMap.<Integer, BigDecimal>builder()
                .put(Range.closedOpen(0, 40), BigDecimal.ONE)
                .put(Range.closedOpen(40, 50), new BigDecimal("1.5"))
                .put(Range.atLeast(50), new BigDecimal("2"))
                .build();
        RangeMap<Integer, BigDecimal> restoredPay = Snapshots.roundTrip(payMultiplier);
        assertEquals(BigDecimal.ONE, restoredPay.get(39));
        assertEquals(new BigDecimal("1.5"), restoredPay.get(40));
        assertEquals(new BigDecimal("2"), restoredPay.get(70));
        assertNull(restoredPay.get(-1));

        String[] rosterNames = Iterables.toArray(restoredClockIns.elementSet(), String.class);
        assertEquals(ImmutableSet.copyOf(staff), ImmutableSet.copyOf(rosterNames));
    }

    /// Teams that rotate through on-call duty.
    enum Team {
        PLATFORM, PAYMENTS, SEARCH, MOBILE
    }

    /// Writes roster state to bytes and reads it back, as a standby node restoring a snapshot.
    static final class Snapshots {

        private Snapshots() {
        }

        @SuppressWarnings("unchecked")
        static <T> T roundTrip(T state) {
            assertTrue(state instanceof Serializable, () -> state.getClass() + " must be serializable");
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (ObjectOutputStream out = new ObjectOutputStream(bytes)) {
                out.writeObject(state);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
            try (ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
                T restored = (T) in.readObject();
                assertNotSame(state, restored);
                return restored;
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            } catch (ClassNotFoundException e) {
                throw new IllegalStateException(e);
            }
        }
    }
}
