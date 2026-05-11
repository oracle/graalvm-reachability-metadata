/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package jakarta_resource.jakarta_resource_api;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.Method;
import java.security.Principal;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EventObject;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;
import java.util.Timer;
import javax.naming.NamingException;
import javax.naming.Reference;
import javax.security.auth.Subject;
import javax.security.auth.callback.Callback;
import javax.security.auth.callback.CallbackHandler;
import javax.transaction.xa.XAResource;
import javax.transaction.xa.Xid;

import jakarta.resource.NotSupportedException;
import jakarta.resource.ResourceException;
import jakarta.resource.cci.Connection;
import jakarta.resource.cci.ConnectionFactory;
import jakarta.resource.cci.ConnectionMetaData;
import jakarta.resource.cci.ConnectionSpec;
import jakarta.resource.cci.IndexedRecord;
import jakarta.resource.cci.Interaction;
import jakarta.resource.cci.InteractionSpec;
import jakarta.resource.cci.MappedRecord;
import jakarta.resource.cci.Record;
import jakarta.resource.cci.RecordFactory;
import jakarta.resource.cci.ResourceAdapterMetaData;
import jakarta.resource.cci.ResourceWarning;
import jakarta.resource.cci.ResultSetInfo;
import jakarta.resource.spi.ActivationSpec;
import jakarta.resource.spi.BootstrapContext;
import jakarta.resource.spi.ConnectionEvent;
import jakarta.resource.spi.ConnectionEventListener;
import jakarta.resource.spi.ConnectionManager;
import jakarta.resource.spi.ConnectionRequestInfo;
import jakarta.resource.spi.EISSystemException;
import jakarta.resource.spi.InvalidPropertyException;
import jakarta.resource.spi.ManagedConnection;
import jakarta.resource.spi.ManagedConnectionFactory;
import jakarta.resource.spi.ManagedConnectionMetaData;
import jakarta.resource.spi.ResourceAdapter;
import jakarta.resource.spi.ResourceAdapterInternalException;
import jakarta.resource.spi.RetryableException;
import jakarta.resource.spi.RetryableUnavailableException;
import jakarta.resource.spi.UnavailableException;
import jakarta.resource.spi.ValidatingManagedConnectionFactory;
import jakarta.resource.spi.XATerminator;
import jakarta.resource.spi.endpoint.MessageEndpoint;
import jakarta.resource.spi.endpoint.MessageEndpointFactory;
import jakarta.resource.spi.security.PasswordCredential;
import jakarta.resource.spi.work.DistributableWork;
import jakarta.resource.spi.work.ExecutionContext;
import jakarta.resource.spi.work.HintsContext;
import jakarta.resource.spi.work.SecurityContext;
import jakarta.resource.spi.work.TransactionContext;
import jakarta.resource.spi.work.Work;
import jakarta.resource.spi.work.WorkAdapter;
import jakarta.resource.spi.work.WorkCompletedException;
import jakarta.resource.spi.work.WorkContext;
import jakarta.resource.spi.work.WorkContextErrorCodes;
import jakarta.resource.spi.work.WorkContextLifecycleListener;
import jakarta.resource.spi.work.WorkContextProvider;
import jakarta.resource.spi.work.WorkEvent;
import jakarta.resource.spi.work.WorkException;
import jakarta.resource.spi.work.WorkListener;
import jakarta.resource.spi.work.WorkManager;
import jakarta.resource.spi.work.WorkRejectedException;
import jakarta.transaction.TransactionSynchronizationRegistry;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class JakartaResourceApiTest {
    @Test
    void resourceAndWorkExceptionsExposeErrorCodesMessagesAndRetryableMarkers() {
        ResourceException coded = new ResourceException("resource failed", "R-001");
        assertThat(coded.getErrorCode()).isEqualTo("R-001");
        assertThat(coded.getMessage()).isEqualTo("resource failed, error code: R-001");

        coded.setErrorCode("R-002");
        assertThat(coded.getMessage()).isEqualTo("resource failed, error code: R-002");
        assertThat(new ResourceException("message only").getMessage()).isEqualTo("message only");
        assertThat(new ResourceException(new IllegalArgumentException("bad input")))
                .hasCauseInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("bad input");

        NotSupportedException notSupported = new NotSupportedException("not supported", "NS-1");
        ResourceAdapterInternalException adapterFailure = new ResourceAdapterInternalException("adapter", "RA-1");
        EISSystemException eisFailure = new EISSystemException("eis", "EIS-1");
        jakarta.resource.spi.SecurityException securityFailure =
                new jakarta.resource.spi.SecurityException("security", "SEC-1");
        jakarta.resource.spi.IllegalStateException illegalState =
                new jakarta.resource.spi.IllegalStateException("state", "STATE-1");
        WorkException workFailure = new WorkCompletedException("work", WorkException.TX_RECREATE_FAILED);
        WorkRejectedException rejected = new WorkRejectedException("rejected", WorkException.START_TIMED_OUT);
        RetryableUnavailableException retryableUnavailable = new RetryableUnavailableException("retry", "RETRY-1");

        assertThat(notSupported.getMessage()).isEqualTo("not supported, error code: NS-1");
        assertThat(adapterFailure.getErrorCode()).isEqualTo("RA-1");
        assertThat(eisFailure.getErrorCode()).isEqualTo("EIS-1");
        assertThat(securityFailure.getErrorCode()).isEqualTo("SEC-1");
        assertThat(illegalState.getErrorCode()).isEqualTo("STATE-1");
        assertThat(workFailure.getErrorCode()).isEqualTo(WorkException.TX_RECREATE_FAILED);
        assertThat(rejected.getMessage()).contains(WorkException.START_TIMED_OUT);
        assertThat(retryableUnavailable).isInstanceOf(RetryableException.class);
        assertThat(new jakarta.resource.spi.work.RetryableWorkRejectedException("later"))
                .isInstanceOf(RetryableException.class);
        assertThat(WorkContextErrorCodes.UNSUPPORTED_CONTEXT_TYPE).isEqualTo("1");
        assertThat(WorkContextErrorCodes.CONTEXT_SETUP_UNSUPPORTED).isEqualTo("4");
    }

    @Test
    void workContextsEventsListenersAndCredentialsBehaveAsSpecified() throws Exception {
        Xid xid = new SimpleXid(27);
        ExecutionContext executionContext = new ExecutionContext();
        executionContext.setXid(xid);
        executionContext.setTransactionTimeout(30);

        assertThat(executionContext.getXid()).isSameAs(xid);
        assertThat(executionContext.getTransactionTimeout()).isEqualTo(30);
        assertThatThrownBy(() -> executionContext.setTransactionTimeout(0))
                .isInstanceOf(NotSupportedException.class)
                .hasMessage("Illegal timeout value");

        TransactionContext transactionContext = new TransactionContext();
        transactionContext.setXid(xid);
        assertThat(transactionContext.getName()).isEqualTo("TransactionContext");
        assertThat(transactionContext.getDescription()).isEqualTo("Transaction Context");
        assertThat(transactionContext.getXid()).isSameAs(xid);

        HintsContext hintsContext = new HintsContext();
        hintsContext.setName("batch-import");
        hintsContext.setDescription("QoS hints for import work");
        hintsContext.setHint(HintsContext.NAME_HINT, "importer");
        hintsContext.setHint(HintsContext.LONGRUNNING_HINT, Boolean.TRUE);
        assertThat(hintsContext.getName()).isEqualTo("batch-import");
        assertThat(hintsContext.getDescription()).isEqualTo("QoS hints for import work");
        assertThat(hintsContext.getHints()).containsEntry(HintsContext.NAME_HINT, "importer")
                .containsEntry(HintsContext.LONGRUNNING_HINT, Boolean.TRUE);

        SimpleWork work = new SimpleWork();
        WorkException exception = new WorkException("listener");
        WorkEvent event = new WorkEvent(this, WorkEvent.WORK_STARTED, work, exception, 12);
        assertThat(event).isInstanceOf(EventObject.class);
        assertThat(event.getSource()).isSameAs(this);
        assertThat(event.getType()).isEqualTo(WorkEvent.WORK_STARTED);
        assertThat(event.getWork()).isSameAs(work);
        assertThat(event.getException()).isSameAs(exception);
        assertThat(event.getStartDuration()).isEqualTo(12);

        WorkAdapter adapter = new WorkAdapter();
        adapter.workAccepted(event);
        adapter.workStarted(event);
        adapter.workCompleted(event);
        adapter.workRejected(event);

        Subject executionSubject = new Subject();
        Subject serviceSubject = new Subject();
        SecurityContext securityContext = new SimpleSecurityContext();
        RecordingCallbackHandler callbackHandler = new RecordingCallbackHandler();
        securityContext.setupSecurityContext(callbackHandler, executionSubject, serviceSubject);
        assertThat(callbackHandler.wasCalled()).isTrue();
        assertThat(securityContext.getName()).isEqualTo("SecurityContext");
        assertThat(securityContext.getDescription()).isEqualTo("Security Context");
        assertThat(executionSubject.getPrincipals()).extracting(Principal::getName).containsExactly("eis-user");
        assertThat(serviceSubject.getPublicCredentials()).contains("service-token");

        char[] password = new char[] {'s', 'e', 'c', 'r', 'e', 't'};
        PasswordCredential credential = new PasswordCredential("alice", password);
        password[0] = 'X';
        PasswordCredential sameCredential = new PasswordCredential("alice", new char[] {'s', 'e', 'c', 'r', 'e', 't'});
        PasswordCredential differentCredential = new PasswordCredential("alice",
                new char[] {'c', 'h', 'a', 'n', 'g', 'e', 'd'});
        SimpleManagedConnectionFactory managedConnectionFactory = new SimpleManagedConnectionFactory();
        credential.setManagedConnectionFactory(managedConnectionFactory);

        assertThat(credential.getUserName()).isEqualTo("alice");
        assertThat(credential.getPassword()).containsExactly('s', 'e', 'c', 'r', 'e', 't');
        assertThat(credential).isEqualTo(sameCredential).hasSameHashCodeAs(sameCredential);
        assertThat(credential).isNotEqualTo(differentCredential).isNotEqualTo("alice");
        assertThat(credential.getManagedConnectionFactory()).isSameAs(managedConnectionFactory);
    }

    @Test
    void distributableWorkProvidesContextsAndLifecycleCallbacksDuringScheduling() throws Exception {
        TransactionContext transactionContext = new TransactionContext();
        transactionContext.setXid(new SimpleXid(43));
        HintsContext hintsContext = new HintsContext();
        hintsContext.setName("remote-import");
        hintsContext.setDescription("Remote work scheduling hints");
        hintsContext.setHint(HintsContext.NAME_HINT, "remote-import");

        ContextAwareDistributableWork work = new ContextAwareDistributableWork(
                List.of(transactionContext, hintsContext));
        WorkContextAwareWorkManager workManager = new WorkContextAwareWorkManager();
        RecordingWorkListener listener = new RecordingWorkListener();

        workManager.scheduleWork(work, WorkManager.IMMEDIATE, new ExecutionContext(), listener);

        assertThat(work).isInstanceOf(DistributableWork.class)
                .isInstanceOf(WorkContextProvider.class)
                .isInstanceOf(WorkContextLifecycleListener.class);
        assertThat(work.wasRun()).isTrue();
        assertThat(work.wasReleased()).isFalse();
        assertThat(work.getLifecycleEvents()).containsExactly("setup-complete");
        assertThat(workManager.getInstalledContextNames()).containsExactly("TransactionContext", "remote-import");
        assertThat(listener.getEventTypes()).containsExactly(
                WorkEvent.WORK_ACCEPTED,
                WorkEvent.WORK_STARTED,
                WorkEvent.WORK_COMPLETED);
    }

    @Test
    void cciConnectionFactoryCreatesRecordsAndExecutesInteractions() throws Exception {
        SimpleConnectionFactory connectionFactory = new SimpleConnectionFactory();
        Reference reference = new Reference("testFactory");
        connectionFactory.setReference(reference);

        assertThat(connectionFactory.getReference()).isSameAs(reference);
        assertThat(connectionFactory.getMetaData().getAdapterName()).isEqualTo("Test Resource Adapter");
        assertThat(connectionFactory.getMetaData().getInteractionSpecsSupported())
                .containsExactly(SimpleInteractionSpec.class.getName());
        assertThat(connectionFactory.getMetaData().supportsExecuteWithInputAndOutputRecord()).isTrue();
        assertThat(connectionFactory.getMetaData().supportsExecuteWithInputRecordOnly()).isTrue();
        assertThat(connectionFactory.getMetaData().supportsLocalTransactionDemarcation()).isTrue();

        Connection connection = connectionFactory.getConnection(new SimpleConnectionSpec("alice"));
        assertThat(connection.getMetaData().getEISProductName()).isEqualTo("In-memory EIS");
        assertThat(connection.getMetaData().getUserName()).isEqualTo("alice");
        assertThat(connection.getResultSetInfo().supportsResultSetType(ResultSet.TYPE_FORWARD_ONLY)).isTrue();
        assertThat(connection.getResultSetInfo().supportsResultTypeConcurrency(
                ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY)).isTrue();

        jakarta.resource.cci.LocalTransaction transaction = connection.getLocalTransaction();
        transaction.begin();
        transaction.commit();
        transaction.begin();
        transaction.rollback();

        RecordFactory recordFactory = connectionFactory.getRecordFactory();
        MappedRecord<String, Object> input = recordFactory.createMappedRecord("input");
        input.setRecordShortDescription("request payload");
        input.put("operation", "echo");
        input.put("count", 2);
        IndexedRecord<String> child = recordFactory.createIndexedRecord("child");
        child.add("nested");
        input.put("child", child);

        MappedRecord<String, Object> output = recordFactory.createMappedRecord("output");
        Interaction interaction = connection.createInteraction();
        boolean executed = interaction.execute(new SimpleInteractionSpec("echo"), input, output);
        Record returned = interaction.execute(new SimpleInteractionSpec("echo"), input);
        ResourceWarning warning = interaction.getWarnings();
        interaction.clearWarnings();

        assertThat(executed).isTrue();
        assertThat(output).containsEntry("operation", "echo").containsEntry("count", 2);
        assertThat(output.getRecordName()).isEqualTo("output");
        assertThat(output.getRecordShortDescription()).isEqualTo("response payload");
        assertThat(returned).isInstanceOf(MappedRecord.class);
        MappedRecord<String, Object> returnedRecord = (MappedRecord<String, Object>) returned;
        assertThat(returnedRecord).containsEntry("operation", "echo");
        assertThat(input.getRecordShortDescription()).isEqualTo("request payload");
        assertThat(input.clone()).isEqualTo(input).isNotSameAs(input);
        assertThat(child.getRecordName()).isEqualTo("child");
        assertThat(child.clone()).isEqualTo(child).isNotSameAs(child);
        assertThat(warning.getMessage()).isEqualTo("Interaction completed with test warning, error code: W-001");
        assertThat(interaction.getWarnings()).isNull();

        interaction.close();
        connection.close();
        assertThat(((SimpleConnection) connection).isClosed()).isTrue();
    }

    @Test
    void spiManagedConnectionsFactoriesAndEventsCoordinateConnectionLifecycle() throws Exception {
        SimpleManagedConnectionFactory factory = new SimpleManagedConnectionFactory();
        PrintWriter logWriter = new PrintWriter(new StringWriter());
        factory.setLogWriter(logWriter);
        SimpleConnectionRequestInfo requestInfo = new SimpleConnectionRequestInfo("primary");
        SimpleConnectionManager manager = new SimpleConnectionManager();

        Object connectionFactory = factory.createConnectionFactory(manager);
        ManagedConnection managedConnection = factory.createManagedConnection(new Subject(), requestInfo);
        Object handle = managedConnection.getConnection(new Subject(), requestInfo);
        ManagedConnection matched = factory.matchManagedConnections(Collections.singleton(managedConnection), null,
                requestInfo);
        ManagedConnection unmatched = factory.matchManagedConnections(Collections.singleton(managedConnection), null,
                new SimpleConnectionRequestInfo("secondary"));

        assertThat(connectionFactory).isInstanceOf(SimpleConnectionFactory.class);
        assertThat(factory.createConnectionFactory()).isInstanceOf(SimpleConnectionFactory.class);
        assertThat(factory.getLogWriter()).isSameAs(logWriter);
        assertThat(matched).isSameAs(managedConnection);
        assertThat(unmatched).isNull();
        assertThat(handle).isInstanceOf(SimpleConnection.class);

        managedConnection.setLogWriter(logWriter);
        assertThat(managedConnection.getLogWriter()).isSameAs(logWriter);
        assertThat(managedConnection.getMetaData().getEISProductName()).isEqualTo("In-memory EIS");
        assertThat(managedConnection.getMetaData().getMaxConnections()).isEqualTo(8);

        RecordingConnectionEventListener listener = new RecordingConnectionEventListener();
        managedConnection.addConnectionEventListener(listener);
        jakarta.resource.spi.LocalTransaction localTransaction = managedConnection.getLocalTransaction();
        localTransaction.begin();
        localTransaction.commit();
        localTransaction.begin();
        localTransaction.rollback();
        ((SimpleManagedConnection) managedConnection).fireClosed(handle);
        ((SimpleManagedConnection) managedConnection).fireError(new ResourceException("broken"));

        assertThat(listener.eventIds).containsExactly(
                ConnectionEvent.LOCAL_TRANSACTION_STARTED,
                ConnectionEvent.LOCAL_TRANSACTION_COMMITTED,
                ConnectionEvent.LOCAL_TRANSACTION_STARTED,
                ConnectionEvent.LOCAL_TRANSACTION_ROLLEDBACK,
                ConnectionEvent.CONNECTION_CLOSED,
                ConnectionEvent.CONNECTION_ERROR_OCCURRED);
        assertThat(listener.lastHandle).isSameAs(handle);
        assertThat(listener.lastException).isInstanceOf(ResourceException.class).hasMessage("broken");

        assertThat(((ValidatingManagedConnectionFactory) factory)
                .getInvalidConnections(Collections.singleton(managedConnection))).isEmpty();
        assertThat(manager.allocateConnection(factory, requestInfo)).isInstanceOf(SimpleConnection.class);
        assertThatThrownBy(managedConnection::getXAResource).isInstanceOf(NotSupportedException.class);

        managedConnection.cleanup();
        assertThat(((SimpleManagedConnection) managedConnection).isCleaned()).isTrue();
        managedConnection.destroy();
        assertThat(((SimpleManagedConnection) managedConnection).isDestroyed()).isTrue();
    }

    @Test
    void resourceAdapterLifecycleUsesBootstrapWorkManagerActivationSpecsAndXaRecovery() throws Exception {
        RecordingWorkManager workManager = new RecordingWorkManager();
        SimpleBootstrapContext bootstrapContext = new SimpleBootstrapContext(workManager);
        SimpleResourceAdapter resourceAdapter = new SimpleResourceAdapter();

        resourceAdapter.start(bootstrapContext);

        assertThat(resourceAdapter.isStarted()).isTrue();
        assertThat(resourceAdapter.getBootstrapContext()).isSameAs(bootstrapContext);
        assertThat(bootstrapContext.getCreatedTimers()).hasSize(1);
        assertThat(bootstrapContext.isContextSupported(TransactionContext.class)).isTrue();
        assertThat(workManager.getCompletedWorkNames()).containsExactly("resource-adapter-start");

        SimpleActivationSpec activationSpec = new SimpleActivationSpec("incoming-orders");
        SimpleMessageEndpointFactory endpointFactory = new SimpleMessageEndpointFactory();
        resourceAdapter.endpointActivation(endpointFactory, activationSpec);

        assertThat(activationSpec.getResourceAdapter()).isSameAs(resourceAdapter);
        assertThat(activationSpec.isValidated()).isTrue();
        assertThat(resourceAdapter.getActiveDestinations()).containsExactly("incoming-orders");
        assertThat(endpointFactory.getActivationName()).isEqualTo("incoming-orders-activation");
        assertThat(endpointFactory.getEndpointClass()).isEqualTo(SimpleMessageEndpoint.class);
        assertThat(endpointFactory.isDeliveryTransacted(null)).isFalse();

        XAResource[] xaResources = resourceAdapter.getXAResources(new ActivationSpec[] {activationSpec});
        MessageEndpoint endpoint = endpointFactory.createEndpoint(xaResources[0], WorkManager.IMMEDIATE);
        endpoint.release();

        assertThat(xaResources).containsExactly(activationSpec.getXaResource());
        assertThat(endpointFactory.getCreatedEndpointCount()).isEqualTo(1);
        assertThat(((SimpleMessageEndpoint) endpoint).isReleased()).isTrue();

        assertThatThrownBy(() -> resourceAdapter.endpointActivation(endpointFactory, new SimpleActivationSpec("")))
                .isInstanceOf(InvalidPropertyException.class)
                .hasMessageContaining("destinationName");

        resourceAdapter.endpointDeactivation(endpointFactory, activationSpec);
        resourceAdapter.stop();

        assertThat(resourceAdapter.getActiveDestinations()).isEmpty();
        assertThat(resourceAdapter.isStarted()).isFalse();
        assertThat(bootstrapContext.getXATerminator().recover(XAResource.TMSTARTRSCAN)).isEmpty();
    }

    private static final class SimpleResourceAdapter implements ResourceAdapter {
        private final List<String> activeDestinations = new ArrayList<>();
        private BootstrapContext bootstrapContext;
        private Timer timer;
        private boolean started;

        @Override
        public void start(BootstrapContext ctx) throws ResourceAdapterInternalException {
            bootstrapContext = ctx;
            try {
                timer = ctx.createTimer();
                ctx.getWorkManager().scheduleWork(new NamedWork("resource-adapter-start"));
            } catch (UnavailableException | WorkException e) {
                throw new ResourceAdapterInternalException(e);
            }
            started = true;
        }

        @Override
        public void stop() {
            if (timer != null) {
                timer.cancel();
            }
            activeDestinations.clear();
            started = false;
        }

        @Override
        public void endpointActivation(MessageEndpointFactory endpointFactory, ActivationSpec spec)
                throws ResourceException {
            SimpleActivationSpec activationSpec = (SimpleActivationSpec) spec;
            activationSpec.setResourceAdapter(this);
            activationSpec.validate();
            activeDestinations.add(activationSpec.getDestinationName());
        }

        @Override
        public void endpointDeactivation(MessageEndpointFactory endpointFactory, ActivationSpec spec) {
            activeDestinations.remove(((SimpleActivationSpec) spec).getDestinationName());
        }

        @Override
        public XAResource[] getXAResources(ActivationSpec[] specs) {
            XAResource[] resources = new XAResource[specs.length];
            for (int i = 0; i < specs.length; i++) {
                resources[i] = ((SimpleActivationSpec) specs[i]).getXaResource();
            }
            return resources;
        }

        private boolean isStarted() {
            return started;
        }

        private BootstrapContext getBootstrapContext() {
            return bootstrapContext;
        }

        private List<String> getActiveDestinations() {
            return activeDestinations;
        }
    }

    private static final class SimpleActivationSpec implements ActivationSpec {
        private final String destinationName;
        private final XAResource xaResource = new SimpleXAResource();
        private ResourceAdapter resourceAdapter;
        private boolean validated;

        private SimpleActivationSpec(String destinationName) {
            this.destinationName = destinationName;
        }

        @Override
        public void validate() throws InvalidPropertyException {
            if (destinationName == null || destinationName.isBlank()) {
                throw new InvalidPropertyException("destinationName must not be blank");
            }
            validated = true;
        }

        @Override
        public ResourceAdapter getResourceAdapter() {
            return resourceAdapter;
        }

        @Override
        public void setResourceAdapter(ResourceAdapter ra) {
            resourceAdapter = ra;
        }

        private String getDestinationName() {
            return destinationName;
        }

        private XAResource getXaResource() {
            return xaResource;
        }

        private boolean isValidated() {
            return validated;
        }
    }

    private static final class SimpleBootstrapContext implements BootstrapContext {
        private final RecordingWorkManager workManager;
        private final SimpleXATerminator xaTerminator = new SimpleXATerminator();
        private final List<Timer> createdTimers = new ArrayList<>();

        private SimpleBootstrapContext(RecordingWorkManager workManager) {
            this.workManager = workManager;
        }

        @Override
        public Timer createTimer() {
            Timer createdTimer = new Timer("jakarta-resource-api-test", true);
            createdTimers.add(createdTimer);
            return createdTimer;
        }

        @Override
        public WorkManager getWorkManager() {
            return workManager;
        }

        @Override
        public XATerminator getXATerminator() {
            return xaTerminator;
        }

        @Override
        public boolean isContextSupported(Class<? extends WorkContext> workContextClass) {
            return TransactionContext.class.equals(workContextClass);
        }

        @Override
        public TransactionSynchronizationRegistry getTransactionSynchronizationRegistry() {
            return null;
        }

        private List<Timer> getCreatedTimers() {
            return createdTimers;
        }
    }

    private static final class RecordingWorkManager implements WorkManager {
        private final List<String> completedWorkNames = new ArrayList<>();

        @Override
        public void doWork(Work work) throws WorkException {
            runWork(work, null);
        }

        @Override
        public void doWork(Work work, long startTimeout, ExecutionContext execContext, WorkListener workListener)
                throws WorkException {
            runWork(work, workListener);
        }

        @Override
        public long startWork(Work work) throws WorkException {
            runWork(work, null);
            return 0;
        }

        @Override
        public long startWork(Work work, long startTimeout, ExecutionContext execContext, WorkListener workListener)
                throws WorkException {
            runWork(work, workListener);
            return 0;
        }

        @Override
        public void scheduleWork(Work work) throws WorkException {
            runWork(work, null);
        }

        @Override
        public void scheduleWork(Work work, long startTimeout, ExecutionContext execContext, WorkListener workListener)
                throws WorkException {
            runWork(work, workListener);
        }

        private void runWork(Work work, WorkListener listener) throws WorkException {
            notify(listener, WorkEvent.WORK_ACCEPTED, work, null);
            notify(listener, WorkEvent.WORK_STARTED, work, null);
            try {
                work.run();
                if (work instanceof NamedWork) {
                    completedWorkNames.add(((NamedWork) work).getName());
                }
                notify(listener, WorkEvent.WORK_COMPLETED, work, null);
            } catch (RuntimeException e) {
                WorkException workException = new WorkCompletedException("Work failed");
                workException.initCause(e);
                notify(listener, WorkEvent.WORK_COMPLETED, work, workException);
                throw workException;
            }
        }

        private void notify(WorkListener listener, int eventType, Work work, WorkException exception) {
            if (listener == null) {
                return;
            }
            WorkEvent event = new WorkEvent(this, eventType, work, exception, 0);
            switch (eventType) {
                case WorkEvent.WORK_ACCEPTED:
                    listener.workAccepted(event);
                    break;
                case WorkEvent.WORK_STARTED:
                    listener.workStarted(event);
                    break;
                case WorkEvent.WORK_COMPLETED:
                    listener.workCompleted(event);
                    break;
                case WorkEvent.WORK_REJECTED:
                    listener.workRejected(event);
                    break;
                default:
                    throw new IllegalArgumentException("Unsupported work event " + eventType);
            }
        }

        private List<String> getCompletedWorkNames() {
            return completedWorkNames;
        }
    }

    private static final class WorkContextAwareWorkManager implements WorkManager {
        private final List<String> installedContextNames = new ArrayList<>();

        @Override
        public void doWork(Work work) throws WorkException {
            runWork(work, null);
        }

        @Override
        public void doWork(Work work, long startTimeout, ExecutionContext execContext, WorkListener workListener)
                throws WorkException {
            runWork(work, workListener);
        }

        @Override
        public long startWork(Work work) throws WorkException {
            runWork(work, null);
            return 0;
        }

        @Override
        public long startWork(Work work, long startTimeout, ExecutionContext execContext, WorkListener workListener)
                throws WorkException {
            runWork(work, workListener);
            return 0;
        }

        @Override
        public void scheduleWork(Work work) throws WorkException {
            runWork(work, null);
        }

        @Override
        public void scheduleWork(Work work, long startTimeout, ExecutionContext execContext,
                WorkListener workListener) throws WorkException {
            runWork(work, workListener);
        }

        private void runWork(Work work, WorkListener listener) {
            notify(listener, WorkEvent.WORK_ACCEPTED, work, null);
            installWorkContexts(work);
            notify(listener, WorkEvent.WORK_STARTED, work, null);
            work.run();
            notify(listener, WorkEvent.WORK_COMPLETED, work, null);
        }

        private void installWorkContexts(Work work) {
            if (!(work instanceof WorkContextProvider)) {
                return;
            }
            WorkContextProvider contextProvider = (WorkContextProvider) work;
            for (WorkContext context : contextProvider.getWorkContexts()) {
                installedContextNames.add(context.getName());
            }
            if (work instanceof WorkContextLifecycleListener) {
                ((WorkContextLifecycleListener) work).contextSetupComplete();
            }
        }

        private void notify(WorkListener listener, int eventType, Work work, WorkException exception) {
            if (listener == null) {
                return;
            }
            WorkEvent event = new WorkEvent(this, eventType, work, exception, 0);
            switch (eventType) {
                case WorkEvent.WORK_ACCEPTED:
                    listener.workAccepted(event);
                    break;
                case WorkEvent.WORK_STARTED:
                    listener.workStarted(event);
                    break;
                case WorkEvent.WORK_COMPLETED:
                    listener.workCompleted(event);
                    break;
                case WorkEvent.WORK_REJECTED:
                    listener.workRejected(event);
                    break;
                default:
                    throw new IllegalArgumentException("Unsupported work event " + eventType);
            }
        }

        private List<String> getInstalledContextNames() {
            return installedContextNames;
        }
    }

    private static final class ContextAwareDistributableWork implements DistributableWork, WorkContextProvider,
            WorkContextLifecycleListener {
        private static final long serialVersionUID = 1L;

        private final List<WorkContext> workContexts;
        private final List<String> lifecycleEvents = new ArrayList<>();
        private boolean run;
        private boolean released;

        private ContextAwareDistributableWork(List<WorkContext> workContexts) {
            this.workContexts = workContexts;
        }

        @Override
        public void run() {
            run = true;
        }

        @Override
        public void release() {
            released = true;
        }

        @Override
        public List<WorkContext> getWorkContexts() {
            return workContexts;
        }

        @Override
        public void contextSetupComplete() {
            lifecycleEvents.add("setup-complete");
        }

        @Override
        public void contextSetupFailed(String errorCode) {
            lifecycleEvents.add("setup-failed:" + errorCode);
        }

        private boolean wasRun() {
            return run;
        }

        private boolean wasReleased() {
            return released;
        }

        private List<String> getLifecycleEvents() {
            return lifecycleEvents;
        }
    }

    private static final class RecordingWorkListener implements WorkListener {
        private final List<Integer> eventTypes = new ArrayList<>();

        @Override
        public void workAccepted(WorkEvent event) {
            eventTypes.add(event.getType());
        }

        @Override
        public void workRejected(WorkEvent event) {
            eventTypes.add(event.getType());
        }

        @Override
        public void workStarted(WorkEvent event) {
            eventTypes.add(event.getType());
        }

        @Override
        public void workCompleted(WorkEvent event) {
            eventTypes.add(event.getType());
        }

        private List<Integer> getEventTypes() {
            return eventTypes;
        }
    }

    private static final class NamedWork implements Work {
        private final String name;

        private NamedWork(String name) {
            this.name = name;
        }

        @Override
        public void run() {
        }

        @Override
        public void release() {
        }

        private String getName() {
            return name;
        }
    }

    private static final class SimpleMessageEndpointFactory implements MessageEndpointFactory {
        private int createdEndpointCount;

        @Override
        public MessageEndpoint createEndpoint(XAResource xaResource) {
            createdEndpointCount++;
            return new SimpleMessageEndpoint();
        }

        @Override
        public MessageEndpoint createEndpoint(XAResource xaResource, long timeout) {
            return createEndpoint(xaResource);
        }

        @Override
        public boolean isDeliveryTransacted(Method method) {
            return false;
        }

        @Override
        public String getActivationName() {
            return "incoming-orders-activation";
        }

        @Override
        public Class<?> getEndpointClass() {
            return SimpleMessageEndpoint.class;
        }

        private int getCreatedEndpointCount() {
            return createdEndpointCount;
        }
    }

    private static final class SimpleMessageEndpoint implements MessageEndpoint {
        private boolean released;

        @Override
        public void beforeDelivery(Method method) {
        }

        @Override
        public void afterDelivery() {
        }

        @Override
        public void release() {
            released = true;
        }

        private boolean isReleased() {
            return released;
        }
    }

    private static final class SimpleXATerminator implements XATerminator {
        @Override
        public void commit(Xid xid, boolean onePhase) {
        }

        @Override
        public void forget(Xid xid) {
        }

        @Override
        public int prepare(Xid xid) {
            return XAResource.XA_OK;
        }

        @Override
        public Xid[] recover(int flag) {
            return new Xid[0];
        }

        @Override
        public void rollback(Xid xid) {
        }
    }

    private static final class SimpleXAResource implements XAResource {
        @Override
        public void commit(Xid xid, boolean onePhase) {
        }

        @Override
        public void end(Xid xid, int flags) {
        }

        @Override
        public void forget(Xid xid) {
        }

        @Override
        public int getTransactionTimeout() {
            return 0;
        }

        @Override
        public boolean isSameRM(XAResource xaResource) {
            return xaResource == this;
        }

        @Override
        public int prepare(Xid xid) {
            return XA_OK;
        }

        @Override
        public Xid[] recover(int flag) {
            return new Xid[0];
        }

        @Override
        public void rollback(Xid xid) {
        }

        @Override
        public boolean setTransactionTimeout(int seconds) {
            return true;
        }

        @Override
        public void start(Xid xid, int flags) {
        }
    }

    private static final class SimpleXid implements Xid {
        private final int formatId;

        private SimpleXid(int formatId) {
            this.formatId = formatId;
        }

        @Override
        public int getFormatId() {
            return formatId;
        }

        @Override
        public byte[] getGlobalTransactionId() {
            return new byte[] {(byte) formatId};
        }

        @Override
        public byte[] getBranchQualifier() {
            return new byte[] {(byte) (formatId + 1)};
        }
    }

    private static final class SimpleWork implements Work {
        @Override
        public void run() {
        }

        @Override
        public void release() {
        }
    }

    private static final class RecordingCallbackHandler implements CallbackHandler {
        private boolean called;

        @Override
        public void handle(Callback[] callbacks) {
            called = true;
        }

        private boolean wasCalled() {
            return called;
        }
    }

    private static final class SimpleSecurityContext extends SecurityContext {
        @Override
        public void setupSecurityContext(CallbackHandler handler, Subject executionSubject, Subject serviceSubject) {
            try {
                handler.handle(new Callback[0]);
            } catch (Exception e) {
                throw new AssertionError(e);
            }
            executionSubject.getPrincipals().add(() -> "eis-user");
            serviceSubject.getPublicCredentials().add("service-token");
        }
    }

    private static final class SimpleConnectionSpec implements ConnectionSpec {
        private final String userName;

        private SimpleConnectionSpec(String userName) {
            this.userName = userName;
        }
    }

    private static final class SimpleInteractionSpec implements InteractionSpec {
        private static final long serialVersionUID = 1L;

        private final String operation;

        private SimpleInteractionSpec(String operation) {
            this.operation = operation;
        }
    }

    private static final class SimpleConnectionFactory implements ConnectionFactory {
        private static final long serialVersionUID = 1L;

        private final SimpleRecordFactory recordFactory = new SimpleRecordFactory();
        private Reference reference;

        @Override
        public Connection getConnection() {
            return new SimpleConnection("anonymous");
        }

        @Override
        public Connection getConnection(ConnectionSpec properties) {
            SimpleConnectionSpec spec = (SimpleConnectionSpec) properties;
            return new SimpleConnection(spec.userName);
        }

        @Override
        public RecordFactory getRecordFactory() {
            return recordFactory;
        }

        @Override
        public ResourceAdapterMetaData getMetaData() {
            return new SimpleResourceAdapterMetaData();
        }

        @Override
        public void setReference(Reference reference) {
            this.reference = reference;
        }

        @Override
        public Reference getReference() throws NamingException {
            return reference;
        }
    }

    private static final class SimpleConnection implements Connection {
        private final String userName;
        private final SimpleCciLocalTransaction localTransaction = new SimpleCciLocalTransaction();
        private boolean closed;

        private SimpleConnection(String userName) {
            this.userName = userName;
        }

        @Override
        public Interaction createInteraction() throws ResourceException {
            assertOpen();
            return new SimpleInteraction(this);
        }

        @Override
        public jakarta.resource.cci.LocalTransaction getLocalTransaction() throws ResourceException {
            assertOpen();
            return localTransaction;
        }

        @Override
        public ConnectionMetaData getMetaData() throws ResourceException {
            assertOpen();
            return new SimpleConnectionMetaData(userName);
        }

        @Override
        public ResultSetInfo getResultSetInfo() throws ResourceException {
            assertOpen();
            return new SimpleResultSetInfo();
        }

        @Override
        public void close() {
            closed = true;
        }

        private boolean isClosed() {
            return closed;
        }

        private void assertOpen() throws ResourceException {
            if (closed) {
                throw new ResourceException("Connection is closed");
            }
        }
    }

    private static final class SimpleInteraction implements Interaction {
        private final Connection connection;
        private ResourceWarning warning;
        private boolean closed;

        private SimpleInteraction(Connection connection) {
            this.connection = connection;
        }

        @Override
        public void close() {
            closed = true;
        }

        @Override
        public Connection getConnection() {
            return connection;
        }

        @Override
        public boolean execute(InteractionSpec ispec, Record input, Record output) throws ResourceException {
            assertOpen();
            SimpleInteractionSpec spec = (SimpleInteractionSpec) ispec;
            if (!"echo".equals(spec.operation)) {
                throw new NotSupportedException("Unsupported operation " + spec.operation);
            }
            MappedRecord<String, Object> source = (MappedRecord<String, Object>) input;
            MappedRecord<String, Object> target = (MappedRecord<String, Object>) output;
            target.putAll(source);
            target.setRecordShortDescription("response payload");
            warning = new ResourceWarning("Interaction completed with test warning", "W-001");
            return true;
        }

        @Override
        public Record execute(InteractionSpec ispec, Record input) throws ResourceException {
            SimpleMappedRecord<String, Object> output = new SimpleMappedRecord<>(input.getRecordName() + "Response");
            execute(ispec, input, output);
            return output;
        }

        @Override
        public ResourceWarning getWarnings() throws ResourceException {
            assertOpen();
            return warning;
        }

        @Override
        public void clearWarnings() throws ResourceException {
            assertOpen();
            warning = null;
        }

        private void assertOpen() throws ResourceException {
            if (closed) {
                throw new ResourceException("Interaction is closed");
            }
        }
    }

    private static final class SimpleCciLocalTransaction implements jakarta.resource.cci.LocalTransaction {
        @Override
        public void begin() {
        }

        @Override
        public void commit() {
        }

        @Override
        public void rollback() {
        }
    }

    private static final class SimpleRecordFactory implements RecordFactory {
        @Override
        public <K, V> MappedRecord<K, V> createMappedRecord(String recordName) {
            return new SimpleMappedRecord<>(recordName);
        }

        @Override
        public <E> IndexedRecord<E> createIndexedRecord(String recordName) {
            return new SimpleIndexedRecord<>(recordName);
        }
    }

    private static final class SimpleMappedRecord<K, V> extends LinkedHashMap<K, V> implements MappedRecord<K, V> {
        private static final long serialVersionUID = 1L;

        private String recordName;
        private String recordShortDescription;

        private SimpleMappedRecord(String recordName) {
            this.recordName = recordName;
        }

        @Override
        public String getRecordName() {
            return recordName;
        }

        @Override
        public void setRecordName(String name) {
            recordName = name;
        }

        @Override
        public void setRecordShortDescription(String description) {
            recordShortDescription = description;
        }

        @Override
        public String getRecordShortDescription() {
            return recordShortDescription;
        }

        @Override
        public Object clone() {
            SimpleMappedRecord<K, V> copy = new SimpleMappedRecord<>(recordName);
            copy.setRecordShortDescription(recordShortDescription);
            copy.putAll(this);
            return copy;
        }
    }

    private static final class SimpleIndexedRecord<E> extends ArrayList<E> implements IndexedRecord<E> {
        private static final long serialVersionUID = 1L;

        private String recordName;
        private String recordShortDescription;

        private SimpleIndexedRecord(String recordName) {
            this.recordName = recordName;
        }

        @Override
        public String getRecordName() {
            return recordName;
        }

        @Override
        public void setRecordName(String name) {
            recordName = name;
        }

        @Override
        public void setRecordShortDescription(String description) {
            recordShortDescription = description;
        }

        @Override
        public String getRecordShortDescription() {
            return recordShortDescription;
        }

        @Override
        public Object clone() {
            SimpleIndexedRecord<E> copy = new SimpleIndexedRecord<>(recordName);
            copy.setRecordShortDescription(recordShortDescription);
            copy.addAll(this);
            return copy;
        }
    }

    private static final class SimpleConnectionMetaData implements ConnectionMetaData {
        private final String userName;

        private SimpleConnectionMetaData(String userName) {
            this.userName = userName;
        }

        @Override
        public String getEISProductName() {
            return "In-memory EIS";
        }

        @Override
        public String getEISProductVersion() {
            return "test";
        }

        @Override
        public String getUserName() {
            return userName;
        }
    }

    private static final class SimpleResultSetInfo implements ResultSetInfo {
        @Override
        public boolean updatesAreDetected(int type) {
            return type == ResultSet.TYPE_SCROLL_SENSITIVE;
        }

        @Override
        public boolean insertsAreDetected(int type) {
            return type == ResultSet.TYPE_SCROLL_SENSITIVE;
        }

        @Override
        public boolean deletesAreDetected(int type) {
            return type == ResultSet.TYPE_SCROLL_SENSITIVE;
        }

        @Override
        public boolean supportsResultSetType(int type) {
            return type == ResultSet.TYPE_FORWARD_ONLY || type == ResultSet.TYPE_SCROLL_INSENSITIVE;
        }

        @Override
        public boolean supportsResultTypeConcurrency(int type, int concurrency) {
            return supportsResultSetType(type) && concurrency == ResultSet.CONCUR_READ_ONLY;
        }

        @Override
        public boolean othersUpdatesAreVisible(int type) {
            return false;
        }

        @Override
        public boolean othersDeletesAreVisible(int type) {
            return false;
        }

        @Override
        public boolean othersInsertsAreVisible(int type) {
            return false;
        }

        @Override
        public boolean ownUpdatesAreVisible(int type) {
            return type == ResultSet.TYPE_SCROLL_INSENSITIVE;
        }

        @Override
        public boolean ownInsertsAreVisible(int type) {
            return type == ResultSet.TYPE_SCROLL_INSENSITIVE;
        }

        @Override
        public boolean ownDeletesAreVisible(int type) {
            return type == ResultSet.TYPE_SCROLL_INSENSITIVE;
        }
    }

    private static final class SimpleResourceAdapterMetaData implements ResourceAdapterMetaData {
        @Override
        public String getAdapterVersion() {
            return "test";
        }

        @Override
        public String getAdapterVendorName() {
            return "Example";
        }

        @Override
        public String getAdapterName() {
            return "Test Resource Adapter";
        }

        @Override
        public String getAdapterShortDescription() {
            return "In-memory CCI adapter";
        }

        @Override
        public String getSpecVersion() {
            return "test-spec";
        }

        @Override
        public String[] getInteractionSpecsSupported() {
            return new String[] {SimpleInteractionSpec.class.getName()};
        }

        @Override
        public boolean supportsExecuteWithInputAndOutputRecord() {
            return true;
        }

        @Override
        public boolean supportsExecuteWithInputRecordOnly() {
            return true;
        }

        @Override
        public boolean supportsLocalTransactionDemarcation() {
            return true;
        }
    }

    private static final class SimpleConnectionRequestInfo implements ConnectionRequestInfo {
        private final String name;

        private SimpleConnectionRequestInfo(String name) {
            this.name = name;
        }

        @Override
        public boolean equals(Object other) {
            if (!(other instanceof SimpleConnectionRequestInfo)) {
                return false;
            }
            SimpleConnectionRequestInfo that = (SimpleConnectionRequestInfo) other;
            return name.equals(that.name);
        }

        @Override
        public int hashCode() {
            return name.hashCode();
        }
    }

    private static final class SimpleConnectionManager implements ConnectionManager {
        private static final long serialVersionUID = 1L;

        @Override
        public Object allocateConnection(ManagedConnectionFactory mcf, ConnectionRequestInfo cxRequestInfo)
                throws ResourceException {
            ManagedConnection managedConnection = mcf.createManagedConnection(new Subject(), cxRequestInfo);
            return managedConnection.getConnection(new Subject(), cxRequestInfo);
        }
    }

    private static final class SimpleManagedConnectionFactory implements ManagedConnectionFactory,
            ValidatingManagedConnectionFactory {
        private static final long serialVersionUID = 1L;

        private PrintWriter logWriter;

        @Override
        public Object createConnectionFactory(ConnectionManager cxManager) {
            return new SimpleConnectionFactory();
        }

        @Override
        public Object createConnectionFactory() {
            return new SimpleConnectionFactory();
        }

        @Override
        public ManagedConnection createManagedConnection(Subject subject, ConnectionRequestInfo cxRequestInfo) {
            return new SimpleManagedConnection((SimpleConnectionRequestInfo) cxRequestInfo, logWriter);
        }

        @Override
        public ManagedConnection matchManagedConnections(Set connectionSet, Subject subject,
                ConnectionRequestInfo cxRequestInfo) {
            for (Object candidate : connectionSet) {
                if (candidate instanceof SimpleManagedConnection) {
                    SimpleManagedConnection connection = (SimpleManagedConnection) candidate;
                    if (connection.matches(cxRequestInfo)) {
                        return connection;
                    }
                }
            }
            return null;
        }

        @Override
        public void setLogWriter(PrintWriter out) {
            logWriter = out;
        }

        @Override
        public PrintWriter getLogWriter() {
            return logWriter;
        }

        @Override
        public Set getInvalidConnections(Set connectionSet) {
            return Collections.emptySet();
        }
    }

    private static final class SimpleManagedConnection implements ManagedConnection {
        private final SimpleConnectionRequestInfo requestInfo;
        private final List<ConnectionEventListener> listeners = new ArrayList<>();
        private PrintWriter logWriter;
        private boolean cleaned;
        private boolean destroyed;

        private SimpleManagedConnection(SimpleConnectionRequestInfo requestInfo, PrintWriter logWriter) {
            this.requestInfo = requestInfo;
            this.logWriter = logWriter;
        }

        @Override
        public Object getConnection(Subject subject, ConnectionRequestInfo cxRequestInfo) {
            return new SimpleConnection(((SimpleConnectionRequestInfo) cxRequestInfo).name);
        }

        @Override
        public void destroy() {
            destroyed = true;
        }

        @Override
        public void cleanup() {
            cleaned = true;
        }

        @Override
        public void associateConnection(Object connection) {
        }

        @Override
        public void addConnectionEventListener(ConnectionEventListener listener) {
            listeners.add(listener);
        }

        @Override
        public void removeConnectionEventListener(ConnectionEventListener listener) {
            listeners.remove(listener);
        }

        @Override
        public XAResource getXAResource() throws ResourceException {
            throw new NotSupportedException("XA transactions are not supported");
        }

        @Override
        public jakarta.resource.spi.LocalTransaction getLocalTransaction() {
            return new SimpleSpiLocalTransaction(this);
        }

        @Override
        public ManagedConnectionMetaData getMetaData() {
            return new SimpleManagedConnectionMetaData(requestInfo.name);
        }

        @Override
        public void setLogWriter(PrintWriter out) {
            logWriter = out;
        }

        @Override
        public PrintWriter getLogWriter() {
            return logWriter;
        }

        private boolean matches(ConnectionRequestInfo cxRequestInfo) {
            return requestInfo.equals(cxRequestInfo);
        }

        private boolean isCleaned() {
            return cleaned;
        }

        private boolean isDestroyed() {
            return destroyed;
        }

        private void fireClosed(Object handle) {
            ConnectionEvent event = new ConnectionEvent(this, ConnectionEvent.CONNECTION_CLOSED);
            event.setConnectionHandle(handle);
            for (ConnectionEventListener listener : listeners) {
                listener.connectionClosed(event);
            }
        }

        private void fireError(Exception exception) {
            ConnectionEvent event = new ConnectionEvent(this, ConnectionEvent.CONNECTION_ERROR_OCCURRED, exception);
            for (ConnectionEventListener listener : listeners) {
                listener.connectionErrorOccurred(event);
            }
        }

        private void fireTransactionEvent(int eventId) {
            ConnectionEvent event = new ConnectionEvent(this, eventId);
            for (ConnectionEventListener listener : listeners) {
                switch (eventId) {
                    case ConnectionEvent.LOCAL_TRANSACTION_STARTED:
                        listener.localTransactionStarted(event);
                        break;
                    case ConnectionEvent.LOCAL_TRANSACTION_COMMITTED:
                        listener.localTransactionCommitted(event);
                        break;
                    case ConnectionEvent.LOCAL_TRANSACTION_ROLLEDBACK:
                        listener.localTransactionRolledback(event);
                        break;
                    default:
                        throw new IllegalArgumentException("Unsupported transaction event " + eventId);
                }
            }
        }
    }

    private static final class SimpleSpiLocalTransaction implements jakarta.resource.spi.LocalTransaction {
        private final SimpleManagedConnection connection;

        private SimpleSpiLocalTransaction(SimpleManagedConnection connection) {
            this.connection = connection;
        }

        @Override
        public void begin() {
            connection.fireTransactionEvent(ConnectionEvent.LOCAL_TRANSACTION_STARTED);
        }

        @Override
        public void commit() {
            connection.fireTransactionEvent(ConnectionEvent.LOCAL_TRANSACTION_COMMITTED);
        }

        @Override
        public void rollback() {
            connection.fireTransactionEvent(ConnectionEvent.LOCAL_TRANSACTION_ROLLEDBACK);
        }
    }

    private static final class SimpleManagedConnectionMetaData implements ManagedConnectionMetaData {
        private final String userName;

        private SimpleManagedConnectionMetaData(String userName) {
            this.userName = userName;
        }

        @Override
        public String getEISProductName() {
            return "In-memory EIS";
        }

        @Override
        public String getEISProductVersion() {
            return "test";
        }

        @Override
        public int getMaxConnections() {
            return 8;
        }

        @Override
        public String getUserName() {
            return userName;
        }
    }

    private static final class RecordingConnectionEventListener implements ConnectionEventListener {
        private final List<Integer> eventIds = new ArrayList<>();
        private Object lastHandle;
        private Exception lastException;

        @Override
        public void connectionClosed(ConnectionEvent event) {
            eventIds.add(event.getId());
            lastHandle = event.getConnectionHandle();
        }

        @Override
        public void localTransactionStarted(ConnectionEvent event) {
            eventIds.add(event.getId());
        }

        @Override
        public void localTransactionCommitted(ConnectionEvent event) {
            eventIds.add(event.getId());
        }

        @Override
        public void localTransactionRolledback(ConnectionEvent event) {
            eventIds.add(event.getId());
        }

        @Override
        public void connectionErrorOccurred(ConnectionEvent event) {
            eventIds.add(event.getId());
            lastException = event.getException();
        }
    }
}
