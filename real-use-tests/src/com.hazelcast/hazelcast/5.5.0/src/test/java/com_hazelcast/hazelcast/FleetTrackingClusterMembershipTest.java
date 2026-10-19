/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_hazelcast.hazelcast;

import com.hazelcast.cluster.ClusterState;
import com.hazelcast.cluster.InitialMembershipEvent;
import com.hazelcast.cluster.InitialMembershipListener;
import com.hazelcast.cluster.Member;
import com.hazelcast.cluster.MembershipEvent;
import com.hazelcast.config.Config;
import com.hazelcast.config.JoinConfig;
import com.hazelcast.config.MapConfig;
import com.hazelcast.config.MemberAttributeConfig;
import com.hazelcast.core.Hazelcast;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.map.IMap;
import com.hazelcast.partition.MigrationListener;
import com.hazelcast.partition.MigrationState;
import com.hazelcast.partition.Partition;
import com.hazelcast.partition.PartitionAware;
import com.hazelcast.partition.PartitionService;
import com.hazelcast.partition.ReplicaMigrationEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Exercises cluster membership and data partitioning: membership listeners, member attributes,
 * partition-aware key placement, partition migration, backups, and cluster state changes.
 *
 * A fleet-tracking service runs on embedded members tagged with the depot they serve. The
 * operations console watches depots join and leave the cluster, keeps every vehicle of a fleet
 * on the same partition so that one member owns the whole fleet, rebalances positions onto a
 * newly started depot and keeps them when that depot leaves again, and freezes writes during
 * maintenance by switching the cluster to the passive state.
 */
class FleetTrackingClusterMembershipTest {

    private static final String POSITIONS = "vehicle-positions";

    private static final String DEPOT = "depot";

    private String clusterName;

    private final List<HazelcastInstance> members = new ArrayList<>();

    @BeforeEach
    void nameCluster() {
        clusterName = "fleet-tracking-" + UUID.randomUUID();
    }

    @AfterEach
    void stopDepots() {
        for (int index = members.size() - 1; index >= 0; index--) {
            HazelcastInstance member = members.get(index);
            if (member.getLifecycleService().isRunning()) {
                member.shutdown();
            }
        }
    }

    @Test
    void consoleSeesDepotsJoinAndLeaveWithTheirAttributes() throws Exception {
        HazelcastInstance north = startDepot("north");
        DepotWatch watch = new DepotWatch();
        north.getCluster().addMembershipListener(watch);

        assertThat(watch.initialDepots).containsExactly("north");
        Member local = north.getCluster().getLocalMember();
        assertThat(local.localMember()).isTrue();
        assertThat(local.getAttribute(DEPOT)).isEqualTo("north");
        assertThat(local.isLiteMember()).isFalse();

        HazelcastInstance south = startDepot("south");
        assertThat(watch.joined.await(60, TimeUnit.SECONDS)).isTrue();
        assertThat(watch.joinedDepots).containsExactly("south");
        assertThat(north.getCluster().getMembers())
                .extracting(member -> member.getAttribute(DEPOT))
                .containsExactly("north", "south");
        assertThat(south.getCluster().getMembers()).hasSize(2);
        Member oldest = south.getCluster().getMembers().iterator().next();
        assertThat(oldest.getUuid()).isEqualTo(local.getUuid());

        south.shutdown();
        assertThat(watch.left.await(60, TimeUnit.SECONDS)).isTrue();
        assertThat(watch.leftDepots).containsExactly("south");
        assertThat(north.getCluster().getMembers()).containsExactly(local);
    }

    @Test
    void everyVehicleOfAFleetIsOwnedByTheSameDepot() throws Exception {
        HazelcastInstance north = startDepot("north");
        HazelcastInstance south = startDepot("south");
        awaitClusterSafe(north);

        IMap<VehicleKey, String> positions = north.getMap(POSITIONS);
        List<VehicleKey> harborFleet = List.of(
                new VehicleKey("truck-1", "harbor"), new VehicleKey("truck-2", "harbor"),
                new VehicleKey("van-7", "harbor"), new VehicleKey("van-9", "harbor"));
        for (VehicleKey vehicle : harborFleet) {
            positions.put(vehicle, "52.37,4.89");
        }
        positions.put(new VehicleKey("truck-3", "airport"), "52.31,4.76");

        PartitionService partitions = south.getPartitionService();
        Partition fleetPartition = partitions.getPartition("harbor");
        Set<Integer> harborPartitions = new HashSet<>();
        for (VehicleKey vehicle : harborFleet) {
            harborPartitions.add(partitions.getPartition(vehicle).getPartitionId());
        }
        assertThat(harborPartitions).containsExactly(fleetPartition.getPartitionId());
        assertThat(partitions.getPartitions()).hasSize(271);

        Member owner = fleetPartition.getOwner();
        assertThat(owner).isNotNull();
        HazelcastInstance owningDepot = owner.getUuid().equals(north.getCluster().getLocalMember().getUuid())
                ? north : south;
        HazelcastInstance otherDepot = owningDepot == north ? south : north;
        IMap<VehicleKey, String> ownedPositions = owningDepot.getMap(POSITIONS);
        IMap<VehicleKey, String> otherPositions = otherDepot.getMap(POSITIONS);
        assertThat(ownedPositions.localKeySet()).containsAll(harborFleet);
        assertThat(otherPositions.localKeySet()).doesNotContainAnyElementsOf(harborFleet);
        assertThat(ownedPositions.getLocalMapStats().getBackupEntryCount()
                + otherPositions.getLocalMapStats().getBackupEntryCount()).isEqualTo(5);
        assertThat(otherPositions.get(new VehicleKey("van-7", "harbor"))).isEqualTo("52.37,4.89");
    }

    @Test
    void positionsMoveToANewDepotAndSurviveItsDeparture() throws Exception {
        HazelcastInstance north = startDepot("north");
        IMap<String, Integer> positions = north.getMap(POSITIONS);
        for (int vehicle = 0; vehicle < 200; vehicle++) {
            positions.put("vehicle-" + vehicle, vehicle);
        }
        assertThat(positions.localKeySet()).hasSize(200);

        Rebalancing rebalancing = new Rebalancing();
        north.getPartitionService().addMigrationListener(rebalancing);
        HazelcastInstance south = startDepot("south");
        assertThat(rebalancing.finished.await(60, TimeUnit.SECONDS)).isTrue();
        awaitClusterSafe(north);

        MigrationState state = rebalancing.lastFinished;
        assertThat(state.getPlannedMigrations()).isPositive();
        assertThat(state.getCompletedMigrations()).isEqualTo(state.getPlannedMigrations());
        assertThat(rebalancing.completedReplicas).isNotEmpty();
        assertThat(rebalancing.failedReplicas).isZero();

        int northOwned = positions.localKeySet().size();
        int southOwned = south.<String, Integer>getMap(POSITIONS).localKeySet().size();
        assertThat(northOwned).isPositive();
        assertThat(southOwned).isPositive();
        assertThat(northOwned + southOwned).isEqualTo(200);
        assertThat(south.getPartitionService().isLocalMemberSafe()).isTrue();

        south.shutdown();
        awaitClusterSafe(north);
        assertThat(north.getCluster().getMembers()).hasSize(1);
        assertThat(positions.size()).isEqualTo(200);
        assertThat(positions.localKeySet()).hasSize(200);
        assertThat(positions.get("vehicle-137")).isEqualTo(137);
    }

    @Test
    void passiveClusterRejectsPositionUpdatesDuringMaintenance() throws Exception {
        HazelcastInstance north = startDepot("north");
        HazelcastInstance south = startDepot("south");
        awaitClusterSafe(north);
        IMap<String, Integer> positions = south.getMap(POSITIONS);
        positions.put("vehicle-1", 1);
        assertThat(north.getCluster().getClusterState()).isEqualTo(ClusterState.ACTIVE);

        north.getCluster().changeClusterState(ClusterState.PASSIVE);
        assertThat(south.getCluster().getClusterState()).isEqualTo(ClusterState.PASSIVE);
        assertThatThrownBy(() -> positions.put("vehicle-2", 2)).isInstanceOf(IllegalStateException.class);
        assertThat(positions.get("vehicle-1")).isEqualTo(1);

        south.getCluster().changeClusterState(ClusterState.ACTIVE);
        assertThat(north.getCluster().getClusterState()).isEqualTo(ClusterState.ACTIVE);
        positions.put("vehicle-2", 2);
        assertThat(north.<String, Integer>getMap(POSITIONS).get("vehicle-2")).isEqualTo(2);
    }

    private HazelcastInstance startDepot(String depot) {
        HazelcastInstance member = Hazelcast.newHazelcastInstance(memberConfig(clusterName, depot));
        members.add(member);
        return member;
    }

    private static void awaitClusterSafe(HazelcastInstance member) throws InterruptedException {
        PartitionService partitions = member.getPartitionService();
        awaitCondition(partitions::isClusterSafe);
    }

    private static void awaitCondition(BooleanSupplier condition) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(60);
        while (!condition.getAsBoolean()) {
            assertThat(System.nanoTime()).as("condition reached within 60 seconds").isLessThan(deadline);
            Thread.sleep(50);
        }
    }

    private static Config memberConfig(String clusterName, String depot) {
        Config config = new Config();
        config.setClusterName(clusterName);
        config.setProperty("hazelcast.phone.home.enabled", "false");
        config.setMemberAttributeConfig(new MemberAttributeConfig().setAttribute(DEPOT, depot));
        config.addMapConfig(new MapConfig(POSITIONS).setBackupCount(1).setStatisticsEnabled(true));
        config.getNetworkConfig().setPort(5701).setPortAutoIncrement(true);
        JoinConfig join = config.getNetworkConfig().getJoin();
        join.getMulticastConfig().setEnabled(false);
        join.getAutoDetectionConfig().setEnabled(false);
        join.getTcpIpConfig().setEnabled(true).addMember("127.0.0.1");
        config.getNetworkConfig().getInterfaces().setEnabled(true).addInterface("127.0.0.1");
        return config;
    }

    /** A vehicle key whose fleet decides the partition that stores it. */
    record VehicleKey(String vehicle, String fleet) implements PartitionAware<String>, Serializable {

        @Override
        public String getPartitionKey() {
            return fleet;
        }
    }

    /** Records the depots the console sees in the cluster, joining it, and leaving it. */
    static final class DepotWatch implements InitialMembershipListener {

        final List<String> initialDepots = new CopyOnWriteArrayList<>();

        final List<String> joinedDepots = new CopyOnWriteArrayList<>();

        final List<String> leftDepots = new CopyOnWriteArrayList<>();

        final CountDownLatch joined = new CountDownLatch(1);

        final CountDownLatch left = new CountDownLatch(1);

        @Override
        public void init(InitialMembershipEvent event) {
            for (Member member : event.getMembers()) {
                initialDepots.add(member.getAttribute(DEPOT));
            }
        }

        @Override
        public void memberAdded(MembershipEvent event) {
            joinedDepots.add(event.getMember().getAttribute(DEPOT));
            joined.countDown();
        }

        @Override
        public void memberRemoved(MembershipEvent event) {
            leftDepots.add(event.getMember().getAttribute(DEPOT));
            left.countDown();
        }
    }

    /** Records the progress of the partition rebalancing that a joining depot triggers. */
    static final class Rebalancing implements MigrationListener {

        final CountDownLatch finished = new CountDownLatch(1);

        final List<Integer> completedReplicas = new CopyOnWriteArrayList<>();

        volatile int failedReplicas;

        volatile MigrationState lastFinished;

        @Override
        public void migrationStarted(MigrationState state) {
        }

        @Override
        public void migrationFinished(MigrationState state) {
            lastFinished = state;
            finished.countDown();
        }

        @Override
        public void replicaMigrationCompleted(ReplicaMigrationEvent event) {
            completedReplicas.add(event.getPartitionId());
        }

        @Override
        public void replicaMigrationFailed(ReplicaMigrationEvent event) {
            failedReplicas++;
        }
    }
}
