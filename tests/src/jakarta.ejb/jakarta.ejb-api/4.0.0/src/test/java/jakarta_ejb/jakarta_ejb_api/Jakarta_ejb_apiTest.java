/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package jakarta_ejb.jakarta_ejb_api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import jakarta.ejb.AccessLocalException;
import jakarta.ejb.AccessTimeout;
import jakarta.ejb.ActivationConfigProperty;
import jakarta.ejb.AfterBegin;
import jakarta.ejb.AfterCompletion;
import jakarta.ejb.ApplicationException;
import jakarta.ejb.Asynchronous;
import jakarta.ejb.AsyncResult;
import jakarta.ejb.BeforeCompletion;
import jakarta.ejb.ConcurrencyManagement;
import jakarta.ejb.ConcurrencyManagementType;
import jakarta.ejb.ConcurrentAccessException;
import jakarta.ejb.ConcurrentAccessTimeoutException;
import jakarta.ejb.CreateException;
import jakarta.ejb.DependsOn;
import jakarta.ejb.DuplicateKeyException;
import jakarta.ejb.EJB;
import jakarta.ejb.EJBAccessException;
import jakarta.ejb.EJBException;
import jakarta.ejb.EJBTransactionRequiredException;
import jakarta.ejb.EJBTransactionRolledbackException;
import jakarta.ejb.EJBs;
import jakarta.ejb.FinderException;
import jakarta.ejb.IllegalLoopbackException;
import jakarta.ejb.Init;
import jakarta.ejb.Local;
import jakarta.ejb.LocalBean;
import jakarta.ejb.Lock;
import jakarta.ejb.LockType;
import jakarta.ejb.MessageDriven;
import jakarta.ejb.NoMoreTimeoutsException;
import jakarta.ejb.NoSuchEJBException;
import jakarta.ejb.NoSuchEntityException;
import jakarta.ejb.NoSuchObjectLocalException;
import jakarta.ejb.ObjectNotFoundException;
import jakarta.ejb.PostActivate;
import jakarta.ejb.PrePassivate;
import jakarta.ejb.Remote;
import jakarta.ejb.Remove;
import jakarta.ejb.RemoveException;
import jakarta.ejb.Schedule;
import jakarta.ejb.ScheduleExpression;
import jakarta.ejb.Schedules;
import jakarta.ejb.Singleton;
import jakarta.ejb.Startup;
import jakarta.ejb.Stateful;
import jakarta.ejb.StatefulTimeout;
import jakarta.ejb.Stateless;
import jakarta.ejb.Timeout;
import jakarta.ejb.Timer;
import jakarta.ejb.TimerConfig;
import jakarta.ejb.TimerHandle;
import jakarta.ejb.TimerService;
import jakarta.ejb.TransactionAttribute;
import jakarta.ejb.TransactionAttributeType;
import jakarta.ejb.TransactionManagement;
import jakarta.ejb.TransactionManagementType;
import jakarta.ejb.TransactionRequiredLocalException;
import jakarta.ejb.TransactionRolledbackLocalException;
import jakarta.ejb.embeddable.EJBContainer;
import jakarta.ejb.spi.EJBContainerProvider;
import javax.naming.Context;
import javax.naming.InitialContext;
import javax.xml.namespace.QName;
import javax.xml.rpc.handler.GenericHandler;
import javax.xml.rpc.handler.HandlerInfo;
import javax.xml.rpc.handler.MessageContext;
import org.junit.jupiter.api.Test;

public class Jakarta_ejb_apiTest {
    @Test
    void asyncResultReturnsValueAndRejectsFutureControlOperations()
            throws ExecutionException, InterruptedException {
        AsyncResult<String> result = new AsyncResult<>("completed-value");

        assertThat(result.get()).isEqualTo("completed-value");
        assertThatExceptionOfType(IllegalStateException.class)
                .isThrownBy(result::isDone)
                .withMessageContaining("Object does not represent an acutal Future");
        assertThatExceptionOfType(IllegalStateException.class)
                .isThrownBy(result::isCancelled)
                .withMessageContaining("Object does not represent an acutal Future");
        assertThatExceptionOfType(IllegalStateException.class)
                .isThrownBy(() -> result.cancel(true))
                .withMessageContaining("Object does not represent an acutal Future");
        assertThatExceptionOfType(IllegalStateException.class)
                .isThrownBy(() -> result.get(1, TimeUnit.MILLISECONDS))
                .withMessageContaining("Object does not represent an acutal Future");
    }

    @Test
    void scheduleExpressionSupportsDefaultsFluentSettersAndDefensiveDateCopies() {
        ScheduleExpression defaults = new ScheduleExpression();

        assertThat(defaults.getSecond()).isEqualTo("0");
        assertThat(defaults.getMinute()).isEqualTo("0");
        assertThat(defaults.getHour()).isEqualTo("0");
        assertThat(defaults.getDayOfMonth()).isEqualTo("*");
        assertThat(defaults.getMonth()).isEqualTo("*");
        assertThat(defaults.getDayOfWeek()).isEqualTo("*");
        assertThat(defaults.getYear()).isEqualTo("*");
        assertThat(defaults.getTimezone()).isNull();
        assertThat(defaults.getStart()).isNull();
        assertThat(defaults.getEnd()).isNull();

        Date start = new Date(1_000L);
        Date end = new Date(2_000L);
        ScheduleExpression expression = new ScheduleExpression()
                .second(15)
                .minute("10/20")
                .hour(9)
                .dayOfMonth("Last")
                .month(12)
                .dayOfWeek("Mon-Fri")
                .year(2030)
                .timezone("UTC")
                .start(start)
                .end(end);

        start.setTime(5_000L);
        end.setTime(6_000L);

        assertThat(expression.getSecond()).isEqualTo("15");
        assertThat(expression.getMinute()).isEqualTo("10/20");
        assertThat(expression.getHour()).isEqualTo("9");
        assertThat(expression.getDayOfMonth()).isEqualTo("Last");
        assertThat(expression.getMonth()).isEqualTo("12");
        assertThat(expression.getDayOfWeek()).isEqualTo("Mon-Fri");
        assertThat(expression.getYear()).isEqualTo("2030");
        assertThat(expression.getTimezone()).isEqualTo("UTC");
        assertThat(expression.getStart()).isEqualTo(new Date(1_000L));
        assertThat(expression.getEnd()).isEqualTo(new Date(2_000L));

        Date returnedStart = expression.getStart();
        returnedStart.setTime(7_000L);
        assertThat(expression.getStart()).isEqualTo(new Date(1_000L));
        assertThat(expression.toString())
                .contains("second=15", "minute=10/20", "hour=9", "dayOfMonth=Last", "timezoneID=UTC");
    }

    @Test
    void timerConfigStoresInfoPersistenceFlagAndStringRepresentation() {
        TimerConfig defaultConfig = new TimerConfig();

        assertThat(defaultConfig.getInfo()).isNull();
        assertThat(defaultConfig.isPersistent()).isTrue();

        TimerConfig config = new TimerConfig("batch-42", false);
        assertThat(config.getInfo()).isEqualTo("batch-42");
        assertThat(config.isPersistent()).isFalse();
        assertThat(config.toString()).isEqualTo("TimerConfig [persistent=false;info=batch-42]");

        config.setInfo("batch-43");
        config.setPersistent(true);

        assertThat(config.getInfo()).isEqualTo("batch-43");
        assertThat(config.isPersistent()).isTrue();
        assertThat(config.toString()).isEqualTo("TimerConfig [persistent=true;info=batch-43]");
    }

    @Test
    void ejbExceptionsExposeMessagesAndCauses() {
        Exception cause = new Exception("root cause");

        EJBException empty = new EJBException();
        assertThat(empty).hasNoCause();
        assertThat(empty.getCausedByException()).isNull();

        EJBException messageOnly = new EJBException("ejb failure");
        assertThat(messageOnly).hasMessage("ejb failure");
        assertThat(messageOnly).hasNoCause();

        EJBException fromCause = new EJBException(cause);
        assertThat(fromCause).hasCause(cause);
        assertThat(fromCause.getCausedByException()).isSameAs(cause);

        EJBException messageAndCause = new EJBException("wrapped", cause);
        assertThat(messageAndCause).hasMessage("wrapped");
        assertThat(messageAndCause.getCausedByException()).isSameAs(cause);

        assertRuntimeFailure(new AccessLocalException("access", cause), "access", cause);
        assertRuntimeFailure(new ConcurrentAccessException("concurrent", cause), "concurrent", cause);
        assertRuntimeFailure(new EJBTransactionRolledbackException("rolled back", cause), "rolled back", cause);
        assertRuntimeFailure(new NoSuchEJBException("missing", cause), "missing", cause);
        assertRuntimeFailure(new NoSuchEntityException(cause), "root cause", cause);
        assertRuntimeFailure(new NoSuchObjectLocalException("missing local", cause), "missing local", cause);
        assertRuntimeFailure(new TransactionRolledbackLocalException("local rollback", cause), "local rollback", cause);

        assertRuntimeFailure(new EJBAccessException("denied"), "denied", null);
        assertRuntimeFailure(new EJBTransactionRequiredException("transaction required"), "transaction required", null);
        assertRuntimeFailure(new ConcurrentAccessTimeoutException("timeout"), "timeout", null);
        assertRuntimeFailure(new IllegalLoopbackException("loopback"), "loopback", null);
        assertRuntimeFailure(new NoMoreTimeoutsException("no more"), "no more", null);
        assertRuntimeFailure(new NoSuchEntityException("entity"), "entity", null);
        assertRuntimeFailure(new TransactionRequiredLocalException("local required"), "local required", null);

        assertCheckedFailure(new CreateException("create"), "create");
        assertCheckedFailure(new DuplicateKeyException("duplicate"), "duplicate");
        assertCheckedFailure(new FinderException("find"), "find");
        assertCheckedFailure(new ObjectNotFoundException("not found"), "not found");
        assertCheckedFailure(new RemoveException("remove"), "remove");
    }

    @Test
    void enumConstantsRoundTripByName() {
        assertThat(LockType.values()).containsExactly(LockType.READ, LockType.WRITE);
        assertThat(LockType.valueOf("WRITE")).isSameAs(LockType.WRITE);

        assertThat(ConcurrencyManagementType.values())
                .containsExactly(ConcurrencyManagementType.CONTAINER, ConcurrencyManagementType.BEAN);
        assertThat(ConcurrencyManagementType.valueOf("BEAN")).isSameAs(ConcurrencyManagementType.BEAN);

        assertThat(TransactionManagementType.values())
                .containsExactly(TransactionManagementType.CONTAINER, TransactionManagementType.BEAN);
        assertThat(TransactionManagementType.valueOf("CONTAINER")).isSameAs(TransactionManagementType.CONTAINER);

        assertThat(TransactionAttributeType.values())
                .containsExactly(
                        TransactionAttributeType.MANDATORY,
                        TransactionAttributeType.REQUIRED,
                        TransactionAttributeType.REQUIRES_NEW,
                        TransactionAttributeType.SUPPORTS,
                        TransactionAttributeType.NOT_SUPPORTED,
                        TransactionAttributeType.NEVER);
        assertThat(TransactionAttributeType.valueOf("REQUIRES_NEW")).isSameAs(TransactionAttributeType.REQUIRES_NEW);
    }

    @Test
    void embeddableContainerReportsMissingProviderThroughEjbException() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put(EJBContainer.PROVIDER, "missing-provider");
        properties.put(EJBContainer.APP_NAME, "metadata-test-app");

        assertThat(EJBContainer.PROVIDER).isEqualTo("jakarta.ejb.embeddable.provider");
        assertThat(EJBContainer.MODULES).isEqualTo("jakarta.ejb.embeddable.modules");
        assertThat(EJBContainer.APP_NAME).isEqualTo("jakarta.ejb.embeddable.appName");
        assertThatExceptionOfType(EJBException.class)
                .isThrownBy(() -> EJBContainer.createEJBContainer(properties))
                .withMessageContaining("No EJBContainer provider available")
                .withMessageContaining("missing-provider");
    }

    @Test
    void embeddableContainerProviderCreatesCloseableContainers() throws Exception {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put(EJBContainer.APP_NAME, "provider-test-app");
        Context context = new InitialContext();
        RecordingEJBContainerProvider provider = new RecordingEJBContainerProvider(context);

        EJBContainer createdContainer = provider.createEJBContainer(properties);

        assertThat(provider.getReceivedProperties()).isSameAs(properties);
        assertThat(createdContainer.getContext()).isSameAs(context);
        assertThat(createdContainer).isInstanceOf(RecordingEJBContainer.class);

        RecordingEJBContainer container = (RecordingEJBContainer) createdContainer;
        assertThat(container.isClosed()).isFalse();

        try (EJBContainer closeableContainer = container) {
            assertThat(closeableContainer.getContext()).isSameAs(context);
        }

        assertThat(container.isClosed()).isTrue();
    }

    @Test
    void handlerInfoAndGenericHandlerManageHandlerMetadata() {
        QName firstHeader = new QName("urn:test", "first");
        QName secondHeader = new QName("urn:test", "second");
        Map<String, String> config = new LinkedHashMap<>();
        config.put("encoding", "utf-8");
        HandlerInfo info = new HandlerInfo(TestHandler.class, config, new QName[] {firstHeader});

        assertThat(info.getHandlerClass()).isEqualTo(TestHandler.class);
        assertThat(info.getHandlerConfig()).isSameAs(config);
        assertThat(info.getHeaders()).containsExactly(firstHeader);

        QName[] returnedHeaders = info.getHeaders();
        returnedHeaders[0] = secondHeader;
        assertThat(info.getHeaders()).containsExactly(firstHeader);

        info.setHandlerClass(GenericHandler.class);
        info.setHandlerConfig(Map.of("mode", "test"));
        info.setHeaders(new QName[] {firstHeader, secondHeader});

        assertThat(info.getHandlerClass()).isEqualTo(GenericHandler.class);
        assertThat(info.getHandlerConfig()).containsEntry("mode", "test");
        assertThat(info.getHeaders()).containsExactly(firstHeader, secondHeader);

        info.setHeaders(null);
        assertThat(info.getHeaders()).isNull();

        TestHandler handler = new TestHandler(firstHeader);
        MessageContext messageContext = new SimpleMessageContext();
        handler.init(info);
        assertThat(handler.handleRequest(messageContext)).isTrue();
        assertThat(handler.handleResponse(messageContext)).isTrue();
        assertThat(handler.handleFault(messageContext)).isTrue();
        assertThat(handler.getHeaders()).containsExactly(firstHeader);
        handler.destroy();
    }

    @Test
    void timerServiceCreatesProgrammaticTimersAndExposesTimerState() {
        RecordingTimerService timerService = new RecordingTimerService();
        TimerConfig oneShotConfig = new TimerConfig("refresh-cache", false);
        Timer oneShot = timerService.createSingleActionTimer(5_000L, oneShotConfig);
        ScheduleExpression calendarSchedule = new ScheduleExpression().hour(2).minute(30).second(0);
        TimerConfig calendarConfig = new TimerConfig("calendar-job", true);
        Timer calendar = timerService.createCalendarTimer(calendarSchedule, calendarConfig);

        assertThat(timerService.getTimers()).containsExactly(oneShot, calendar);
        assertThat(timerService.getAllTimers()).containsExactly(oneShot, calendar);
        assertThat(oneShot.isPersistent()).isFalse();
        assertThat(oneShot.isCalendarTimer()).isFalse();
        assertThat(oneShot.getInfo()).isEqualTo("refresh-cache");
        assertThat(oneShot.getTimeRemaining()).isPositive();
        assertThat(oneShot.getNextTimeout()).isAfter(new Date());

        Date returnedTimeout = oneShot.getNextTimeout();
        returnedTimeout.setTime(0L);
        assertThat(oneShot.getNextTimeout()).isAfter(new Date());

        TimerHandle handle = oneShot.getHandle();
        assertThat(handle.getTimer()).isSameAs(oneShot);
        assertThat(calendar.isPersistent()).isTrue();
        assertThat(calendar.isCalendarTimer()).isTrue();
        assertThat(calendar.getInfo()).isEqualTo("calendar-job");
        assertThat(calendar.getSchedule()).isSameAs(calendarSchedule);

        oneShot.cancel();
        assertThat(timerService.getTimers()).containsExactly(calendar);
        assertThatExceptionOfType(NoSuchObjectLocalException.class)
                .isThrownBy(oneShot::getInfo)
                .withMessage("Timer has been cancelled");
    }

    @Test
    void annotatedBeanTypesUseEjbAnnotationsAndBusinessMethods() throws Exception {
        AnnotatedSessionBean sessionBean = new AnnotatedSessionBean();

        assertThat(sessionBean.process("job")).isEqualTo("processed job");
        assertThat(sessionBean.processRemotely("job")).isEqualTo("remote job");
        assertThat(sessionBean.computeAsync("job").get()).isEqualTo("async job");
        sessionBean.afterBegin();
        sessionBean.beforeCompletion();
        sessionBean.afterCompletion(true);
        sessionBean.timeout();
        sessionBean.postActivate();
        sessionBean.prePassivate();
        sessionBean.remove();
        assertThat(sessionBean.lifecycleEvents)
                .containsExactly(
                        "afterBegin",
                        "beforeCompletion",
                        "afterCompletion:true",
                        "timeout",
                        "postActivate",
                        "prePassivate",
                        "remove");

        StatefulWorkflow statefulWorkflow = new StatefulWorkflow();
        statefulWorkflow.create("workflow-1");
        assertThat(statefulWorkflow.getName()).isEqualTo("workflow-1");

        StartupSingleton startupSingleton = new StartupSingleton();
        assertThat(startupSingleton.status()).isEqualTo("started");

        MessageListenerBean messageListenerBean = new MessageListenerBean();
        messageListenerBean.run();
        assertThat(messageListenerBean.wasRun()).isTrue();

        assertThat(new BusinessFailure("business failure")).hasMessage("business failure");
    }

    private static void assertRuntimeFailure(EJBException exception, String message, Exception cause) {
        assertThat(exception).hasMessageContaining(message);
        assertThat(exception.getCausedByException()).isSameAs(cause);
    }

    private static void assertCheckedFailure(Exception exception, String message) {
        assertThat(exception).hasMessage(message);
        assertThat(exception).hasNoCause();
    }

    private interface LocalBusiness {
        String process(String input);
    }

    private interface RemoteBusiness {
        String processRemotely(String input);
    }

    @Stateless(name = "annotatedSession", mappedName = "ejb/AnnotatedSession", description = "Session bean")
    @Local(LocalBusiness.class)
    @Remote(RemoteBusiness.class)
    @LocalBean
    @TransactionManagement(TransactionManagementType.CONTAINER)
    @TransactionAttribute(TransactionAttributeType.REQUIRED)
    @ConcurrencyManagement(ConcurrencyManagementType.CONTAINER)
    @Lock(LockType.WRITE)
    @AccessTimeout(value = 5, unit = TimeUnit.SECONDS)
    @DependsOn({"database", "messaging"})
    @EJBs({
        @EJB(
                name = "localBusiness",
                description = "Local business dependency",
                beanName = "annotatedSession",
                beanInterface = LocalBusiness.class,
                mappedName = "ejb/AnnotatedSession",
                lookup = "java:module/AnnotatedSession")
    })
    private static final class AnnotatedSessionBean implements LocalBusiness, RemoteBusiness {
        private final List<String> lifecycleEvents = new ArrayList<>();

        @Override
        public String process(String input) {
            return "processed " + input;
        }

        @Override
        public String processRemotely(String input) {
            return "remote " + input;
        }

        @Asynchronous
        public AsyncResult<String> computeAsync(String input) {
            return new AsyncResult<>("async " + input);
        }

        @Schedules({
            @Schedule(hour = "1", minute = "15", second = "0", info = "nightly", persistent = false),
            @Schedule(hour = "13", minute = "45", second = "30", timezone = "UTC", info = "midday")
        })
        public void scheduledWork() {
            lifecycleEvents.add("scheduledWork");
        }

        @Timeout
        public void timeout() {
            lifecycleEvents.add("timeout");
        }

        @Remove(retainIfException = true)
        public void remove() {
            lifecycleEvents.add("remove");
        }

        @AfterBegin
        public void afterBegin() {
            lifecycleEvents.add("afterBegin");
        }

        @BeforeCompletion
        public void beforeCompletion() {
            lifecycleEvents.add("beforeCompletion");
        }

        @AfterCompletion
        public void afterCompletion(boolean committed) {
            lifecycleEvents.add("afterCompletion:" + committed);
        }

        @PostActivate
        public void postActivate() {
            lifecycleEvents.add("postActivate");
        }

        @PrePassivate
        public void prePassivate() {
            lifecycleEvents.add("prePassivate");
        }
    }

    @Stateful(
            name = "statefulWorkflow",
            mappedName = "ejb/StatefulWorkflow",
            description = "Stateful workflow",
            passivationCapable = true)
    @StatefulTimeout(value = 10, unit = TimeUnit.MINUTES)
    private static final class StatefulWorkflow {
        private String name;

        @Init("create")
        public void create(String workflowName) {
            name = workflowName;
        }

        public String getName() {
            return name;
        }
    }

    @Singleton(name = "startupSingleton", mappedName = "ejb/StartupSingleton", description = "Startup singleton")
    @Startup
    private static final class StartupSingleton {
        public String status() {
            return "started";
        }
    }

    @MessageDriven(
            name = "messageListener",
            messageListenerInterface = Runnable.class,
            activationConfig = {
                @ActivationConfigProperty(propertyName = "destinationType", propertyValue = "queue"),
                @ActivationConfigProperty(propertyName = "destination", propertyValue = "jobs")
            },
            mappedName = "jms/jobs",
            description = "Message listener")
    private static final class MessageListenerBean implements Runnable {
        private boolean run;

        @Override
        public void run() {
            run = true;
        }

        public boolean wasRun() {
            return run;
        }
    }

    @ApplicationException(rollback = true, inherited = false)
    private static final class BusinessFailure extends Exception {
        private BusinessFailure(String message) {
            super(message);
        }
    }

    private static final class RecordingEJBContainerProvider implements EJBContainerProvider {
        private final Context context;
        private Map<?, ?> receivedProperties;

        private RecordingEJBContainerProvider(Context context) {
            this.context = context;
        }

        @Override
        public EJBContainer createEJBContainer(Map<?, ?> properties) {
            receivedProperties = properties;
            return new RecordingEJBContainer(context);
        }

        private Map<?, ?> getReceivedProperties() {
            return receivedProperties;
        }
    }

    private static final class RecordingTimerService implements TimerService {
        private final List<RecordingTimer> timers = new ArrayList<>();

        @Override
        public Timer createTimer(long duration, Serializable info) {
            return createSingleActionTimer(duration, new TimerConfig(info, true));
        }

        @Override
        public Timer createSingleActionTimer(long duration, TimerConfig timerConfig) {
            return addTimer(new RecordingTimer(new Date(System.currentTimeMillis() + duration), null, timerConfig));
        }

        @Override
        public Timer createTimer(long initialDuration, long intervalDuration, Serializable info) {
            return createIntervalTimer(initialDuration, intervalDuration, new TimerConfig(info, true));
        }

        @Override
        public Timer createIntervalTimer(long initialDuration, long intervalDuration, TimerConfig timerConfig) {
            return addTimer(new RecordingTimer(
                    new Date(System.currentTimeMillis() + initialDuration), null, timerConfig));
        }

        @Override
        public Timer createTimer(Date expiration, Serializable info) {
            return createSingleActionTimer(expiration, new TimerConfig(info, true));
        }

        @Override
        public Timer createSingleActionTimer(Date expiration, TimerConfig timerConfig) {
            return addTimer(new RecordingTimer(expiration, null, timerConfig));
        }

        @Override
        public Timer createTimer(Date initialExpiration, long intervalDuration, Serializable info) {
            return createIntervalTimer(initialExpiration, intervalDuration, new TimerConfig(info, true));
        }

        @Override
        public Timer createIntervalTimer(Date initialExpiration, long intervalDuration, TimerConfig timerConfig) {
            return addTimer(new RecordingTimer(initialExpiration, null, timerConfig));
        }

        @Override
        public Timer createCalendarTimer(ScheduleExpression schedule) {
            return createCalendarTimer(schedule, new TimerConfig(null, true));
        }

        @Override
        public Timer createCalendarTimer(ScheduleExpression schedule, TimerConfig timerConfig) {
            return addTimer(new RecordingTimer(new Date(System.currentTimeMillis() + 60_000L), schedule, timerConfig));
        }

        @Override
        public Collection<Timer> getTimers() {
            return activeTimers();
        }

        @Override
        public Collection<Timer> getAllTimers() {
            return activeTimers();
        }

        private Timer addTimer(RecordingTimer timer) {
            timers.add(timer);
            return timer;
        }

        private List<Timer> activeTimers() {
            List<Timer> activeTimers = new ArrayList<>();
            for (RecordingTimer timer : timers) {
                if (!timer.isCancelled()) {
                    activeTimers.add(timer);
                }
            }
            return activeTimers;
        }
    }

    private static final class RecordingTimer implements Timer {
        private final Date nextTimeout;
        private final ScheduleExpression schedule;
        private final Serializable info;
        private final boolean persistent;
        private boolean cancelled;

        private RecordingTimer(Date nextTimeout, ScheduleExpression schedule, TimerConfig timerConfig) {
            this.nextTimeout = new Date(nextTimeout.getTime());
            this.schedule = schedule;
            info = timerConfig.getInfo();
            persistent = timerConfig.isPersistent();
        }

        @Override
        public void cancel() {
            ensureActive();
            cancelled = true;
        }

        @Override
        public long getTimeRemaining() {
            ensureActive();
            return Math.max(0L, nextTimeout.getTime() - System.currentTimeMillis());
        }

        @Override
        public Date getNextTimeout() {
            ensureActive();
            return new Date(nextTimeout.getTime());
        }

        @Override
        public ScheduleExpression getSchedule() {
            ensureActive();
            return schedule;
        }

        @Override
        public boolean isPersistent() {
            ensureActive();
            return persistent;
        }

        @Override
        public boolean isCalendarTimer() {
            ensureActive();
            return schedule != null;
        }

        @Override
        public Serializable getInfo() {
            ensureActive();
            return info;
        }

        @Override
        public TimerHandle getHandle() {
            ensureActive();
            return new RecordingTimerHandle(this);
        }

        private boolean isCancelled() {
            return cancelled;
        }

        private void ensureActive() {
            if (cancelled) {
                throw new NoSuchObjectLocalException("Timer has been cancelled");
            }
        }
    }

    private static final class RecordingTimerHandle implements TimerHandle {
        private final Timer timer;

        private RecordingTimerHandle(Timer timer) {
            this.timer = timer;
        }

        @Override
        public Timer getTimer() {
            return timer;
        }
    }

    private static final class RecordingEJBContainer extends EJBContainer {
        private final Context context;
        private boolean closed;

        private RecordingEJBContainer(Context context) {
            this.context = context;
        }

        @Override
        public Context getContext() {
            return context;
        }

        @Override
        public void close() {
            closed = true;
        }

        private boolean isClosed() {
            return closed;
        }
    }

    private static final class TestHandler extends GenericHandler {
        private final QName[] headers;

        private TestHandler(QName... headers) {
            this.headers = headers;
        }

        @Override
        public QName[] getHeaders() {
            return headers;
        }
    }

    private static final class SimpleMessageContext implements MessageContext {
        private final Map<String, Object> properties = new LinkedHashMap<>();

        @Override
        public void setProperty(String name, Object value) {
            properties.put(name, value);
        }

        @Override
        public Object getProperty(String name) {
            return properties.get(name);
        }

        @Override
        public void removeProperty(String name) {
            properties.remove(name);
        }

        @Override
        public boolean containsProperty(String name) {
            return properties.containsKey(name);
        }

        @Override
        public Iterator<String> getPropertyNames() {
            return properties.keySet().iterator();
        }
    }
}
