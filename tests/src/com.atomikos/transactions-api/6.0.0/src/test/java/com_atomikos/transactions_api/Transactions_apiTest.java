/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_atomikos.transactions_api;

import com.atomikos.datasource.RecoverableResource;
import com.atomikos.icatch.CompositeCoordinator;
import com.atomikos.icatch.CompositeTransaction;
import com.atomikos.icatch.CompositeTransactionManager;
import com.atomikos.icatch.Extent;
import com.atomikos.icatch.Participant;
import com.atomikos.icatch.Propagation;
import com.atomikos.icatch.RecoveryCoordinator;
import com.atomikos.icatch.RecoveryService;
import com.atomikos.icatch.SubTxAwareParticipant;
import com.atomikos.icatch.SysException;
import com.atomikos.icatch.Synchronization;
import com.atomikos.icatch.config.Configuration;
import com.atomikos.icatch.event.transaction.ParticipantHeuristicEvent;
import com.atomikos.icatch.event.transaction.TransactionHeuristicEvent;
import com.atomikos.icatch.provider.ConfigProperties;
import com.atomikos.icatch.config.UserTransactionServiceImp;
import com.atomikos.recovery.PendingTransactionRecord;
import com.atomikos.recovery.TxState;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.Stack;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class Transactions_apiTest {

    @AfterEach
    void cleanUpConfigurationState() {
        List<RecoverableResource> resources = new ArrayList<>(Configuration.getResources());
        for (RecoverableResource resource : resources) {
            Configuration.removeResource(resource.getName());
        }
        Configuration.installCompositeTransactionManager(null);
    }

    @Test
    void configPropertiesResolvePlaceholdersApplyTypedAccessorsAndProvideDefaults() {
        String customReferenceProperty = "atomikos.test.reference";
        System.setProperty(customReferenceProperty, "resolved-segment");
        try {
            Properties properties = baseConfigProperties();
            properties.setProperty(ConfigProperties.LOG_BASE_DIR_PROPERTY_NAME, "/logs/${" + customReferenceProperty + "}");
            properties.setProperty(ConfigProperties.LOG_BASE_NAME_PROPERTY_NAME, " tx-log ");

            ConfigProperties configProperties = new ConfigProperties(properties);

            assertThat(configProperties.getLogBaseDir()).isEqualTo("/logs/resolved-segment");
            assertThat(configProperties.getLogBaseName()).isEqualTo("tx-log");
            assertThat(configProperties.getEnableLogging()).isTrue();
            assertThat(configProperties.getMaxTimeout()).isEqualTo(30000L);
            assertThat(configProperties.getMaxActives()).isEqualTo(17);
            assertThat(configProperties.getCheckpointInterval()).isEqualTo(1234L);
            assertThat(configProperties.getForceShutdownOnVmExit()).isFalse();
            assertThat(configProperties.getForgetOrphanedLogEntriesDelay()).isEqualTo(4321L);
            assertThat(configProperties.getOltpMaxRetries()).isEqualTo(3);
            assertThat(configProperties.getOltpRetryInterval()).isEqualTo(25L);
            assertThat(configProperties.getRecoveryDelay()).isEqualTo(6789L);
            assertThat(configProperties.getAllowSubTransactions()).isTrue();
            assertThat(configProperties.getThrowOnHeuristic()).isFalse();
            assertThat(configProperties.getTmUniqueName()).isNotBlank();
            assertThat(configProperties.getJvmId()).isNotBlank();

            Properties completed = configProperties.getCompletedProperties();
            assertThat(completed)
                .containsEntry(ConfigProperties.LOG_BASE_DIR_PROPERTY_NAME, "/logs/resolved-segment")
                .containsKeys(ConfigProperties.TM_UNIQUE_NAME_PROPERTY_NAME, ConfigProperties.JVM_ID_PROPERTY_NAME);
        } finally {
            System.clearProperty(customReferenceProperty);
        }
    }

    @Test
    void configPropertiesHonorSystemOverridesAndUserSpecificOverrides() {
        System.setProperty(ConfigProperties.LOG_BASE_NAME_PROPERTY_NAME, "system-log");
        try {
            ConfigProperties configProperties = new ConfigProperties(baseConfigProperties());
            assertThat(configProperties.getLogBaseName()).isEqualTo("system-log");

            Properties userOverrides = new Properties();
            userOverrides.setProperty(ConfigProperties.LOG_BASE_NAME_PROPERTY_NAME, "user-log");
            userOverrides.setProperty(ConfigProperties.MAX_ACTIVES_PROPERTY_NAME, "41");
            configProperties.applyUserSpecificProperties(userOverrides);

            assertThat(configProperties.getLogBaseName()).isEqualTo("system-log");
            assertThat(configProperties.getMaxActives()).isEqualTo(41);
        } finally {
            System.clearProperty(ConfigProperties.LOG_BASE_NAME_PROPERTY_NAME);
        }
    }

    @Test
    void configPropertiesRejectInvalidPlaceholdersAndMissingProperties() {
        Properties invalidProperties = baseConfigProperties();
        invalidProperties.setProperty(ConfigProperties.LOG_BASE_DIR_PROPERTY_NAME, "${}");
        ConfigProperties invalidConfig = new ConfigProperties(invalidProperties);

        assertThatThrownBy(invalidConfig::getLogBaseDir)
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("empty name");

        ConfigProperties configProperties = new ConfigProperties(baseConfigProperties());
        assertThatThrownBy(() -> configProperties.getProperty("missing.property"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Missing required property");
    }

    @Test
    void configurationFailsFastWhenAssemblerServiceIsUnavailable() {
        assertThat(Configuration.getTransactionService()).isNull();
        assertThat(Configuration.getRecoveryService()).isNull();
        assertThat(Configuration.getCompositeTransactionManager()).isNull();

        assertThatThrownBy(Configuration::getConfigProperties)
            .isInstanceOf(SysException.class)
            .hasMessageContaining("No Assembler service found");

        assertThatThrownBy(Configuration::init)
            .isInstanceOf(SysException.class)
            .hasMessageContaining("No Assembler service found");

        assertThat(Configuration.getTransactionService()).isNull();
        assertThat(Configuration.getRecoveryService()).isNull();
        assertThat(Configuration.getCompositeTransactionManager()).isNull();
    }

    @Test
    void userTransactionServiceImpPropagatesBootstrapFailuresAndSupportsSafeShutdownConveniences() {
        UserTransactionServiceImp userTransactionService = new UserTransactionServiceImp(baseConfigProperties());

        assertThatThrownBy(userTransactionService::init)
            .isInstanceOf(SysException.class)
            .hasMessageContaining("No Assembler service found");

        assertThat(userTransactionService.getCompositeTransactionManager()).isNull();
        assertThatCode(userTransactionService::shutdownWait).doesNotThrowAnyException();
        assertThatCode(userTransactionService::shutdownForce).doesNotThrowAnyException();
        assertThatCode(userTransactionService::close).doesNotThrowAnyException();
    }

    @Test
    void pendingTransactionRecordRoundTripsAndEvaluatesDomainFlags() {
        PendingTransactionRecord record = new PendingTransactionRecord(
            "tx-1",
            TxState.IN_DOUBT,
            99L,
            "domain-a",
            "http://remote-coordinator"
        );

        PendingTransactionRecord parsed = PendingTransactionRecord.fromRecord(record.toRecord());

        assertThat(parsed.id).isEqualTo(record.id);
        assertThat(parsed.state).isEqualTo(record.state);
        assertThat(parsed.expires).isEqualTo(record.expires);
        assertThat(parsed.recoveryDomainName).isEqualTo(record.recoveryDomainName);
        assertThat(parsed.superiorId).isEqualTo(record.superiorId + System.lineSeparator());

        assertThat(record.markAsCommitting().state).isEqualTo(TxState.COMMITTING);
        assertThat(record.markAsTerminated().state).isEqualTo(TxState.TERMINATED);
        assertThat(record.isForeignInDomain("domain-b")).isTrue();
        assertThat(record.isRecoveredByDomain("domain-b")).isFalse();
        assertThat(record.allowsHeuristicTermination("domain-b")).isFalse();
        assertThat(record.isLocalRoot("domain-a")).isFalse();

        PendingTransactionRecord heuristicCandidate = new PendingTransactionRecord("tx-2", TxState.IN_DOUBT, 100L, "domain-a", "local-parent");
        assertThat(heuristicCandidate.allowsHeuristicTermination("domain-b")).isTrue();

        PendingTransactionRecord localRoot = new PendingTransactionRecord("local-root", TxState.ACTIVE, 12L, "domain-a", null);
        assertThat(localRoot.isLocalRoot("domain-a")).isTrue();

        assertThatThrownBy(() -> PendingTransactionRecord.fromRecord("broken"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Invalid record value supplied");
    }

    @Test
    void pendingTransactionRecordFindsAndRemovesDescendantsAndExtractsIds() {
        PendingTransactionRecord root = new PendingTransactionRecord("root", TxState.ACTIVE, 1L, "domain", null);
        PendingTransactionRecord child = new PendingTransactionRecord("child", TxState.IN_DOUBT, 2L, "domain", "root");
        PendingTransactionRecord grandchild = new PendingTransactionRecord("grandchild", TxState.COMMITTING, 3L, "domain", "child");
        PendingTransactionRecord sibling = new PendingTransactionRecord("sibling", TxState.ABORTING, 4L, "domain", "root");
        PendingTransactionRecord unrelated = new PendingTransactionRecord("other", TxState.COMMITTED, 5L, "domain", "outside");
        List<PendingTransactionRecord> records = new ArrayList<>(List.of(root, child, grandchild, sibling, unrelated));

        Collection<PendingTransactionRecord> descendants = PendingTransactionRecord.findAllDescendants(root, records);
        assertThat(descendants).containsExactlyInAnyOrder(child, grandchild, sibling);

        assertThat(PendingTransactionRecord.extractCoordinatorIds(records, TxState.IN_DOUBT, TxState.COMMITTING))
            .containsExactlyInAnyOrder("child", "grandchild");

        PendingTransactionRecord.removeAllDescendants(root, records);
        assertThat(records).containsExactlyInAnyOrder(root, unrelated);
    }

    @Test
    void txStateFlagsAndTransitionsReflectThePublicContract() {
        assertThat(TxState.ACTIVE.transitionAllowedTo(TxState.PREPARING)).isTrue();
        assertThat(TxState.ACTIVE.transitionAllowedTo(TxState.COMMITTING)).isTrue();
        assertThat(TxState.ACTIVE.transitionAllowedTo(TxState.TERMINATED)).isFalse();
        assertThat(TxState.MARKED_ABORT.transitionAllowedTo(TxState.ACTIVE)).isFalse();

        assertThat(TxState.IN_DOUBT.isRecoverableState()).isTrue();
        assertThat(TxState.COMMITTING.isRecoverableState()).isTrue();
        assertThat(TxState.ACTIVE.isRecoverableState()).isFalse();

        assertThat(TxState.TERMINATED.isFinalState()).isTrue();
        assertThat(TxState.ABANDONED.isFinalState()).isFalse();
        assertThat(TxState.ABANDONED.isFinalStateForOltp()).isTrue();

        assertThat(TxState.HEUR_MIXED.isHeuristic()).isTrue();
        assertThat(TxState.ACTIVE.isHeuristic()).isFalse();
        assertThat(TxState.IN_DOUBT.isOneOf(TxState.ACTIVE, TxState.IN_DOUBT)).isTrue();
        assertThat(TxState.IN_DOUBT.isOneOf(TxState.ACTIVE, TxState.PREPARING)).isFalse();
    }

    @Test
    void extentAggregatesParticipantsAndProtectsReturnedCollections() throws Exception {
        FakeParticipant directParticipant = new FakeParticipant("participant://direct", "resource-direct");
        Extent extent = new Extent("parent-1");
        extent.add(directParticipant, 1);

        Map<String, Integer> remoteParticipants = new HashMap<>();
        remoteParticipants.put("participant://direct", 2);
        remoteParticipants.put("participant://remote", 3);
        extent.addRemoteParticipants(remoteParticipants);

        Map<String, Integer> remoteSnapshot = extent.getRemoteParticipants();
        Stack<Participant> participantSnapshot = extent.getParticipants();

        assertThat(extent.getParentTransactionId()).isEqualTo("parent-1");
        assertThat(remoteSnapshot)
            .containsEntry("participant://direct", 3)
            .containsEntry("participant://remote", 3);
        assertThat(participantSnapshot).containsExactly(directParticipant);

        remoteSnapshot.put("participant://extra", 99);
        participantSnapshot.pop();

        assertThat(extent.getRemoteParticipants()).doesNotContainKey("participant://extra");
        assertThat(extent.getParticipants()).containsExactly(directParticipant);
        assertThat(extent.toString())
            .contains("parent=parent-1")
            .contains("uri=participant://direct")
            .contains("responseCount=3")
            .contains("direct=true")
            .contains("uri=participant://remote")
            .contains("direct=false");
    }

    @Test
    void extentCopyAndExtentMergingRetainAggregatedInformation() throws Exception {
        FakeParticipant directParticipant = new FakeParticipant("participant://direct", "resource-direct");
        Extent source = new Extent("parent-source");
        source.add(directParticipant, 2);
        source.addRemoteParticipants(Map.of("participant://remote", 1));

        Extent copy = new Extent(source);
        assertThat(copy.getParentTransactionId()).isEqualTo("parent-source");
        assertThat(copy.getRemoteParticipants())
            .containsEntry("participant://direct", 2)
            .containsEntry("participant://remote", 1);
        assertThat(copy.getParticipants()).isEmpty();

        Extent merged = new Extent("parent-target");
        merged.add(source);
        assertThat(merged.getRemoteParticipants())
            .containsEntry("participant://direct", 2)
            .containsEntry("participant://remote", 1);
        assertThat(merged.getParticipants()).containsExactly(directParticipant);

        Extent queried = new Extent("queried");
        queried.getRemoteParticipants();
        assertThatThrownBy(() -> queried.add(source))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("no longer allowed");
    }

    @Test
    void propagationUsesParentCoordinatorByDefaultAndIncludesTransactionProperties() {
        Properties rootProperties = new Properties();
        rootProperties.setProperty("rootKey", "rootValue");
        Properties parentProperties = new Properties();
        parentProperties.setProperty("parentKey", "parentValue");

        FakeCompositeTransaction rootTransaction = new FakeCompositeTransaction(
            "root-tx",
            rootProperties,
            new FakeCompositeCoordinator("root-coordinator", "root-coordinator")
        );
        FakeCompositeTransaction parentTransaction = new FakeCompositeTransaction(
            "parent-tx",
            parentProperties,
            new FakeCompositeCoordinator("parent-coordinator", "root-coordinator")
        );

        Propagation propagation = new Propagation("recovery-domain", rootTransaction, parentTransaction, true, 1500L);

        assertThat(propagation.getRecoveryDomainName()).isEqualTo("recovery-domain");
        assertThat(propagation.getRootTransaction()).isSameAs(rootTransaction);
        assertThat(propagation.getParentTransaction()).isSameAs(parentTransaction);
        assertThat(propagation.getLineage()).containsExactly(rootTransaction, parentTransaction);
        assertThat(propagation.isSerial()).isTrue();
        assertThat(propagation.getTimeout()).isEqualTo(1500L);
        assertThat(propagation.getRecoveryCoordinatorURI()).isEqualTo("parent-coordinator");
        assertThat(propagation.toString())
            .contains("domain=recovery-domain")
            .contains("timeout=1500")
            .contains("serial=true")
            .contains("recoveryCoordinatorURI=parent-coordinator")
            .contains("parent=root-tx")
            .contains("property.rootKey=rootValue")
            .contains("parent=parent-tx")
            .contains("property.parentKey=parentValue");

        Propagation explicitRecoveryCoordinator = new Propagation(
            "recovery-domain",
            rootTransaction,
            parentTransaction,
            false,
            75L,
            "explicit-uri"
        );
        assertThat(explicitRecoveryCoordinator.getRecoveryCoordinatorURI()).isEqualTo("explicit-uri");
    }

    @Test
    void propagationRejectsNullTransactions() {
        FakeCompositeTransaction transaction = new FakeCompositeTransaction(
            "tx",
            new Properties(),
            new FakeCompositeCoordinator("coordinator", "coordinator")
        );

        assertThatThrownBy(() -> new Propagation("domain", null, transaction, false, 1L))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("rootTransaction cannot be null");
        assertThatThrownBy(() -> new Propagation("domain", transaction, null, false, 1L))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("parentTransaction cannot be null");
    }

    @Test
    void heuristicEventsExposePayloadAndMeaningfulMessages() {
        TransactionHeuristicEvent transactionEvent = new TransactionHeuristicEvent("tx-1", "parent-1", TxState.HEUR_HAZARD);
        ParticipantHeuristicEvent participantEvent = new ParticipantHeuristicEvent("tx-1", "participant://one", TxState.HEUR_ABORTED);
        TransactionHeuristicEvent nonHeuristicEvent = new TransactionHeuristicEvent("tx-2", null, TxState.COMMITTING);

        assertThat(transactionEvent.eventCreationTimestamp).isPositive();
        assertThat(transactionEvent.transactionId).isEqualTo("tx-1");
        assertThat(transactionEvent.parentTransactionId).isEqualTo("parent-1");
        assertThat(transactionEvent.state).isEqualTo(TxState.HEUR_HAZARD);
        assertThat(transactionEvent.toString())
            .contains("Detected state: HEUR_HAZARD")
            .contains("for transaction tx-1")
            .contains("with parent transaction parent-1")
            .contains("HINT");

        assertThat(participantEvent.eventCreationTimestamp).isPositive();
        assertThat(participantEvent.transactionId).isEqualTo("tx-1");
        assertThat(participantEvent.participantUri).isEqualTo("participant://one");
        assertThat(participantEvent.state).isEqualTo(TxState.HEUR_ABORTED);
        assertThat(participantEvent.toString())
            .contains("Heuristic state detected: HEUR_ABORTED")
            .contains("for participant participant://one")
            .contains("in transaction: tx-1")
            .contains("HINT");

        assertThat(nonHeuristicEvent.toString())
            .contains("Detected state: COMMITTING")
            .doesNotContain("HINT")
            .doesNotContain("with parent transaction");
    }

    @Test
    void configurationInstallsCompositeTransactionManagerAndTracksResources() {
        FakeCompositeTransactionManager transactionManager = new FakeCompositeTransactionManager();
        Configuration.installCompositeTransactionManager(transactionManager);
        assertThat(Configuration.getCompositeTransactionManager()).isSameAs(transactionManager);

        FakeRecoverableResource resource = new FakeRecoverableResource("resource-one", false);
        Configuration.addResource(resource);

        assertThat(resource.recoveryServiceAssignments).isEqualTo(1);
        assertThat(resource.lastRecoveryService).isNull();
        assertThat(Configuration.getResource("resource-one")).isSameAs(resource);
        assertThat(Configuration.getResources()).containsExactly(resource);

        Collection<RecoverableResource> snapshot = Configuration.getResources();
        snapshot.clear();
        assertThat(Configuration.getResources()).containsExactly(resource);

        assertThat(Configuration.removeResource("resource-one")).isSameAs(resource);
        assertThat(Configuration.getResources()).isEmpty();
        assertThat(Configuration.getResource("resource-one")).isNull();
    }

    @Test
    void configurationPurgesClosedResourcesAndRejectsOpenDuplicates() {
        FakeRecoverableResource closedResource = new FakeRecoverableResource("shared", true);
        Configuration.addResource(closedResource);

        FakeRecoverableResource openResource = new FakeRecoverableResource("shared", false);
        Configuration.addResource(openResource);
        assertThat(Configuration.getResources()).containsExactly(openResource);

        FakeRecoverableResource duplicateOpenResource = new FakeRecoverableResource("shared", false);
        assertThatThrownBy(() -> Configuration.addResource(duplicateOpenResource))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Another resource already exists with name");
    }

    @Test
    void pendingTransactionRecordCollectsLineagesForMatchingAncestors() {
        PendingTransactionRecord anchor = new PendingTransactionRecord("anchor", TxState.ACTIVE, 1L, "domain", null);
        PendingTransactionRecord child = new PendingTransactionRecord("child", TxState.IN_DOUBT, 2L, "domain", "anchor");
        PendingTransactionRecord grandchild = new PendingTransactionRecord("grandchild", TxState.COMMITTING, 3L, "domain", "child");
        PendingTransactionRecord directMatch = new PendingTransactionRecord("direct-match", TxState.ABORTING, 4L, "domain", null);
        PendingTransactionRecord unrelatedRoot = new PendingTransactionRecord("unrelated-root", TxState.ACTIVE, 5L, "domain", null);
        PendingTransactionRecord unrelatedChild = new PendingTransactionRecord("unrelated-child", TxState.IN_DOUBT, 6L, "domain", "unrelated-root");
        PendingTransactionRecord orphan = new PendingTransactionRecord("orphan", TxState.ACTIVE, 7L, "domain", "missing-root");
        List<PendingTransactionRecord> records = List.of(anchor, child, grandchild, directMatch, unrelatedRoot, unrelatedChild, orphan);

        Collection<PendingTransactionRecord> collected = PendingTransactionRecord.collectLineages(
            record -> Objects.equals(record.id, "anchor") || Objects.equals(record.id, "direct-match"),
            records
        );

        assertThat(collected).containsExactlyInAnyOrder(anchor, child, grandchild, directMatch);
    }

    private static Properties baseConfigProperties() {
        Properties properties = new Properties();
        properties.setProperty(ConfigProperties.LOG_BASE_DIR_PROPERTY_NAME, "/tmp/atomikos");
        properties.setProperty(ConfigProperties.LOG_BASE_NAME_PROPERTY_NAME, "tmlog");
        properties.setProperty(ConfigProperties.ENABLE_LOGGING_PROPERTY_NAME, "true");
        properties.setProperty(ConfigProperties.MAX_TIMEOUT_PROPERTY_NAME, "30000");
        properties.setProperty(ConfigProperties.MAX_ACTIVES_PROPERTY_NAME, "17");
        properties.setProperty(ConfigProperties.CHECKPOINT_INTERVAL_PROPERTY_NAME, "1234");
        properties.setProperty(ConfigProperties.FORCE_SHUTDOWN_ON_VM_EXIT_PROPERTY_NAME, "false");
        properties.setProperty(ConfigProperties.FORGET_ORPHANED_LOG_ENTRIES_DELAY_PROPERTY_NAME, "4321");
        properties.setProperty(ConfigProperties.OLTP_MAX_RETRIES_PROPERTY_NAME, "3");
        properties.setProperty(ConfigProperties.OLTP_RETRY_INTERVAL_PROPERTY_NAME, "25");
        properties.setProperty(ConfigProperties.RECOVERY_DELAY_PROPERTY_NAME, "6789");
        properties.setProperty(ConfigProperties.ALLOW_SUBTRANSACTIONS_PROPERTY_NAME, "true");
        properties.setProperty(ConfigProperties.THROW_ON_HEURISTIC_PROPERTY_NAME, "false");
        return properties;
    }

    private static final class FakeRecoverableResource implements RecoverableResource {

        private final String name;
        private boolean closed;
        private RecoveryService lastRecoveryService;
        private int recoveryServiceAssignments;

        private FakeRecoverableResource(String name, boolean closed) {
            this.name = name;
            this.closed = closed;
        }

        @Override
        public void setRecoveryService(RecoveryService recoveryService) {
            this.lastRecoveryService = recoveryService;
            this.recoveryServiceAssignments++;
        }

        @Override
        public void close() {
            this.closed = true;
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public boolean isSameRM(RecoverableResource otherResource) {
            return otherResource != null && Objects.equals(name, otherResource.getName());
        }

        @Override
        public boolean isClosed() {
            return closed;
        }

        @Override
        public boolean recover(long startOfRecoveryScan, Collection<PendingTransactionRecord> expiredCommittingCoordinators, Collection<PendingTransactionRecord> indoubtForeignCoordinatorsToKeep) {
            return false;
        }

        @Override
        public boolean hasPendingParticipantsFromLastRecoveryScan() {
            return false;
        }
    }

    private static final class FakeParticipant implements Participant {

        private final String uri;
        private final String resourceName;

        private FakeParticipant(String uri, String resourceName) {
            this.uri = uri;
            this.resourceName = resourceName;
        }

        @Override
        public String getURI() {
            return uri;
        }

        @Override
        public void setCascadeList(Map<String, Integer> cascadeList) {
        }

        @Override
        public void setGlobalSiblingCount(int count) {
        }

        @Override
        public int prepare() {
            return READ_ONLY;
        }

        @Override
        public void commit(boolean onePhase) {
        }

        @Override
        public void rollback() {
        }

        @Override
        public void forget() {
        }

        @Override
        public String getResourceName() {
            return resourceName;
        }
    }

    private static final class FakeCompositeTransactionManager implements CompositeTransactionManager {

        private final AtomicInteger transactionCounter = new AtomicInteger();
        private CompositeTransaction currentTransaction;

        @Override
        public CompositeTransaction createCompositeTransaction(long timeout) {
            int id = transactionCounter.incrementAndGet();
            currentTransaction = new FakeCompositeTransaction(
                "tx-" + id,
                new Properties(),
                new FakeCompositeCoordinator("coordinator-" + id, "coordinator-" + id)
            );
            return currentTransaction;
        }

        @Override
        public CompositeTransaction getCompositeTransaction() {
            return currentTransaction;
        }

        @Override
        public CompositeTransaction getCompositeTransaction(String tid) {
            if (currentTransaction != null && Objects.equals(currentTransaction.getTid(), tid)) {
                return currentTransaction;
            }
            return null;
        }

        @Override
        public void resume(CompositeTransaction transaction) {
            currentTransaction = transaction;
        }

        @Override
        public CompositeTransaction suspend() {
            CompositeTransaction suspended = currentTransaction;
            currentTransaction = null;
            return suspended;
        }

        @Override
        public CompositeTransaction recreateCompositeTransaction(Propagation propagation) {
            currentTransaction = propagation.getParentTransaction();
            return currentTransaction;
        }
    }

    private static final class FakeCompositeTransaction implements CompositeTransaction {

        private final String tid;
        private final Properties properties;
        private final CompositeCoordinator coordinator;
        private final Extent extent;
        private TxState state = TxState.ACTIVE;

        private FakeCompositeTransaction(String tid, Properties properties, CompositeCoordinator coordinator) {
            this.tid = tid;
            this.properties = properties;
            this.coordinator = coordinator;
            this.extent = new Extent(tid);
        }

        @Override
        public TxState getState() {
            return state;
        }

        @Override
        public boolean isRoot() {
            return true;
        }

        @Override
        public Stack<CompositeTransaction> getLineage() {
            Stack<CompositeTransaction> lineage = new java.util.Stack<>();
            lineage.push(this);
            return lineage;
        }

        @Override
        public String getTid() {
            return tid;
        }

        @Override
        public boolean isAncestorOf(CompositeTransaction transaction) {
            return false;
        }

        @Override
        public boolean isDescendantOf(CompositeTransaction transaction) {
            return false;
        }

        @Override
        public boolean isRelatedTransaction(CompositeTransaction transaction) {
            return transaction != null && Objects.equals(tid, transaction.getTid());
        }

        @Override
        public boolean isSameTransaction(CompositeTransaction transaction) {
            return transaction != null && Objects.equals(tid, transaction.getTid());
        }

        @Override
        public CompositeCoordinator getCompositeCoordinator() {
            return coordinator;
        }

        @Override
        public RecoveryCoordinator addParticipant(Participant participant) {
            return coordinator.getRecoveryCoordinator();
        }

        @Override
        public void registerSynchronization(Synchronization synchronization) {
        }

        @Override
        public void addSubTxAwareParticipant(SubTxAwareParticipant participant) {
        }

        @Override
        public boolean isSerial() {
            return false;
        }

        @Override
        public boolean isLocal() {
            return true;
        }

        @Override
        public Extent getExtent() {
            return extent;
        }

        @Override
        public long getTimeout() {
            return 0L;
        }

        @Override
        public void setRollbackOnly() {
            state = TxState.MARKED_ABORT;
        }

        @Override
        public void commit() {
            state = TxState.COMMITTED;
        }

        @Override
        public void rollback() {
            state = TxState.ABORTED;
        }

        @Override
        public void setProperty(String name, String value) {
            properties.setProperty(name, value);
        }

        @Override
        public String getProperty(String name) {
            return properties.getProperty(name);
        }

        @Override
        public Properties getProperties() {
            return properties;
        }

        @Override
        public CompositeTransaction createSubTransaction() {
            return new FakeCompositeTransaction(tid + "-sub", new Properties(), coordinator);
        }

        @Override
        public void setSerial() {
        }
    }

    private static final class FakeCompositeCoordinator implements CompositeCoordinator {

        private final String coordinatorId;
        private final String rootId;
        private final RecoveryCoordinator recoveryCoordinator;

        private FakeCompositeCoordinator(String coordinatorId, String rootId) {
            this.coordinatorId = coordinatorId;
            this.rootId = rootId;
            this.recoveryCoordinator = new FakeRecoveryCoordinator(coordinatorId, rootId);
        }

        @Override
        public String getCoordinatorId() {
            return coordinatorId;
        }

        @Override
        public String getRootId() {
            return rootId;
        }

        @Override
        public RecoveryCoordinator getRecoveryCoordinator() {
            return recoveryCoordinator;
        }
    }

    private static final class FakeRecoveryCoordinator implements RecoveryCoordinator {

        private final String uri;
        private final String recoveryDomainName;

        private FakeRecoveryCoordinator(String uri, String recoveryDomainName) {
            this.uri = uri;
            this.recoveryDomainName = recoveryDomainName;
        }

        @Override
        public String getURI() {
            return uri;
        }

        @Override
        public String getRecoveryDomainName() {
            return recoveryDomainName;
        }
    }
}
