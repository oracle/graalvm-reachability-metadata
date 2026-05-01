/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_glassfish.javax_ejb;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.io.Serializable;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import javax.ejb.AccessLocalException;
import javax.ejb.ApplicationException;
import javax.ejb.BeanManagedConcurrency;
import javax.ejb.ConcurrentAccessException;
import javax.ejb.CreateException;
import javax.ejb.DependsOn;
import javax.ejb.DuplicateKeyException;
import javax.ejb.EJB;
import javax.ejb.EJBAccessException;
import javax.ejb.EJBException;
import javax.ejb.EJBTransactionRequiredException;
import javax.ejb.EJBTransactionRolledbackException;
import javax.ejb.FinderException;
import javax.ejb.MessageDriven;
import javax.ejb.NoSuchEJBException;
import javax.ejb.NoSuchEntityException;
import javax.ejb.NoSuchObjectLocalException;
import javax.ejb.ObjectNotFoundException;
import javax.ejb.Remote;
import javax.ejb.Remove;
import javax.ejb.RemoveException;
import javax.ejb.Singleton;
import javax.ejb.Startup;
import javax.ejb.Stateful;
import javax.ejb.Stateless;
import javax.ejb.Timeout;
import javax.ejb.TimedObject;
import javax.ejb.Timer;
import javax.ejb.TimerHandle;
import javax.ejb.TimerService;
import javax.ejb.TransactionAttribute;
import javax.ejb.TransactionAttributeType;
import javax.ejb.TransactionManagement;
import javax.ejb.TransactionManagementType;
import javax.ejb.TransactionRequiredLocalException;
import javax.ejb.TransactionRolledbackLocalException;
import javax.interceptor.AroundInvoke;
import javax.interceptor.ExcludeClassInterceptors;
import javax.interceptor.ExcludeDefaultInterceptors;
import javax.interceptor.Interceptors;
import javax.interceptor.InvocationContext;
import javax.xml.namespace.QName;
import javax.xml.rpc.handler.GenericHandler;
import javax.xml.rpc.handler.HandlerInfo;
import javax.xml.rpc.handler.MessageContext;

import org.junit.jupiter.api.Test;

public class Javax_ejbTest {
    @Test
    void xmlRpcHandlerInfoCopiesHeadersAndGenericHandlerDefaultsAllowProcessing() {
        QName firstHeader = new QName("urn:test", "first");
        QName secondHeader = new QName("urn:test", "second");
        QName[] headers = new QName[] {firstHeader, secondHeader};
        Map<String, String> configuration = new LinkedHashMap<>();
        configuration.put("tenant", "north");

        HandlerInfo handlerInfo = new HandlerInfo(AuditHandler.class, configuration, headers);
        headers[0] = new QName("urn:changed", "changed");

        assertThat(handlerInfo.getHandlerClass()).isEqualTo(AuditHandler.class);
        assertThat(handlerInfo.getHandlerConfig()).isSameAs(configuration);
        assertThat(handlerInfo.getHeaders()).containsExactly(firstHeader, secondHeader);

        QName[] returnedHeaders = handlerInfo.getHeaders();
        returnedHeaders[1] = new QName("urn:changed", "changed");
        assertThat(handlerInfo.getHeaders()).containsExactly(firstHeader, secondHeader);

        handlerInfo.setHandlerClass(AlternativeHandler.class);
        handlerInfo.setHandlerConfig(new HashMap<String, String>());
        handlerInfo.setHeaders(new QName[] {secondHeader});

        AuditHandler handler = new AuditHandler(handlerInfo.getHeaders());
        SimpleMessageContext messageContext = new SimpleMessageContext();

        assertThat(handlerInfo.getHandlerClass()).isEqualTo(AlternativeHandler.class);
        assertThat(handlerInfo.getHandlerConfig()).isEmpty();
        assertThat(handlerInfo.getHeaders()).containsExactly(secondHeader);
        assertThat(handler.handleRequest(messageContext)).isTrue();
        assertThat(handler.handleResponse(messageContext)).isTrue();
        assertThat(handler.handleFault(messageContext)).isTrue();
        assertThat(handler.getHeaders()).containsExactly(secondHeader);

        handler.init(handlerInfo);
        handler.destroy();
    }

    @Test
    void messageContextStoresPropertiesAndEnumeratesPropertyNames() {
        SimpleMessageContext context = new SimpleMessageContext();

        context.setProperty("operation", "checkout");
        context.setProperty("attempt", 2);

        assertThat(context.containsProperty("operation")).isTrue();
        assertThat(context.getProperty("operation")).isEqualTo("checkout");
        assertThat(context.getProperty("attempt")).isEqualTo(2);
        assertThat(context.propertyNames()).containsExactly("operation", "attempt");

        context.removeProperty("operation");

        assertThat(context.containsProperty("operation")).isFalse();
        assertThat(context.propertyNames()).containsExactly("attempt");
    }

    @Test
    void invocationContextCoordinatesInterceptorProceedAndMutableState() throws Exception {
        Method checkoutMethod = CheckoutService.class.getDeclaredMethod("checkout", String.class, int.class);
        SimpleInvocationContext context = new SimpleInvocationContext(
                new CheckoutService(),
                checkoutMethod,
                new Object[] {"A-100", 3});
        AuditInterceptor interceptor = new AuditInterceptor();

        assertThat(context.getTarget()).isInstanceOf(CheckoutService.class);
        assertThat(context.getMethod()).isEqualTo(checkoutMethod);
        assertThat(context.getParameters()).containsExactly("A-100", 3);
        assertThat(context.getContextData()).isEmpty();

        context.setParameters(new Object[] {"B-200", 5});
        Object result = interceptor.recordAndProceed(context);

        assertThat(result).isEqualTo("B-200:5");
        assertThat(context.getContextData()).containsEntry("interceptor", "audit");
    }

    @Test
    void enumConstantsExposeExpectedTransactionValues() {
        assertThat(TransactionManagementType.values()).containsExactly(
                TransactionManagementType.CONTAINER,
                TransactionManagementType.BEAN);
        assertThat(TransactionAttributeType.values()).containsExactly(
                TransactionAttributeType.MANDATORY,
                TransactionAttributeType.REQUIRED,
                TransactionAttributeType.REQUIRES_NEW,
                TransactionAttributeType.SUPPORTS,
                TransactionAttributeType.NOT_SUPPORTED,
                TransactionAttributeType.NEVER);

        assertThat(TransactionManagementType.valueOf("CONTAINER")).isEqualTo(TransactionManagementType.CONTAINER);
        assertThat(TransactionAttributeType.valueOf("REQUIRES_NEW"))
                .isEqualTo(TransactionAttributeType.REQUIRES_NEW);
    }

    @Test
    void exceptionConstructorsPreserveMessagesCausesAndHierarchy() {
        Exception cause = new Exception("database unavailable");
        EJBException ejbException = new EJBException("business method failed", cause);

        assertThat(ejbException).hasMessageContaining("business method failed");
        assertThat(ejbException).hasMessageContaining("database unavailable");
        assertThat(ejbException.getCausedByException()).isSameAs(cause);
        assertThat(ejbException.getCause()).isNull();
        assertThat(new EJBException(cause).getCausedByException()).isSameAs(cause);

        assertThat(new AccessLocalException("access denied", cause)).isInstanceOf(EJBException.class);
        assertThat(new ConcurrentAccessException("busy", cause)).isInstanceOf(EJBException.class);
        assertThat(new EJBTransactionRolledbackException("rolled back", cause)).isInstanceOf(EJBException.class);
        assertThat(new NoSuchEJBException("missing", cause)).isInstanceOf(EJBException.class);
        assertThat(new NoSuchObjectLocalException("removed", cause)).isInstanceOf(EJBException.class);
        assertThat(new NoSuchEntityException(cause)).isInstanceOf(EJBException.class);

        assertThat(new AccessLocalException("access denied")).hasMessage("access denied");
        assertThat(new EJBAccessException("security failure")).hasMessage("security failure");
        assertThat(new EJBTransactionRequiredException("transaction required")).hasMessage("transaction required");
        assertThat(new TransactionRequiredLocalException("local transaction required"))
                .hasMessage("local transaction required");
        assertThat(new TransactionRolledbackLocalException("local rollback")).hasMessage("local rollback");
        assertThat(new ConcurrentAccessException("timeout")).hasMessage("timeout");
        assertThat(new NoSuchEJBException("bean missing")).hasMessage("bean missing");
        assertThat(new NoSuchEntityException("entity missing")).hasMessage("entity missing");
        assertThat(new NoSuchObjectLocalException("object missing")).hasMessage("object missing");
        assertThat(new DuplicateKeyException("duplicate")).hasMessage("duplicate");
        assertThat(new DuplicateKeyException("duplicate")).isInstanceOf(CreateException.class);
        assertThat(new ObjectNotFoundException("not found")).isInstanceOf(FinderException.class);
        assertThat(new RemoveException("cannot remove")).hasMessage("cannot remove");
    }

    @Test
    void annotatedBeansCanBeInstantiatedAndInvokedWithoutContainer() throws Exception {
        InventoryBean inventory = new InventoryBean();
        BillingBean billing = new BillingBean();
        StartupCache cache = new StartupCache();
        QueueListener listener = new QueueListener();
        ScheduledTasks scheduledTasks = new ScheduledTasks();

        assertThat(inventory.reserve("SKU-1", 2)).isEqualTo("SKU-1:2");
        assertThat(inventory.cancel("SKU-1")).isEqualTo("cancelled:SKU-1");
        assertThat(billing.invoice("order-10")).isEqualTo("invoice:order-10");
        assertThat(cache.refresh()).isEqualTo("cache-refreshed");
        assertThat(listener.onMessage("reconcile")).isEqualTo("received:reconcile");
        assertThat(scheduledTasks.hourly()).isEqualTo("hourly");
    }

    @Test
    void timerServiceCreatesTimersAndTimedObjectReceivesTimerCallback() {
        SimpleTimerService timerService = new SimpleTimerService();
        Date firstExpiration = new Date(System.currentTimeMillis() + TimeUnit.MINUTES.toMillis(1));
        RecordingTimedObject timedObject = new RecordingTimedObject();

        Timer singleActionTimer = timerService.createTimer(firstExpiration, new TimerPayload("reconcile", 1));
        Timer intervalTimer = timerService.createTimer(500L, 1_000L, "interval");
        timedObject.ejbTimeout(singleActionTimer);

        assertThat(singleActionTimer.getNextTimeout()).isEqualTo(firstExpiration);
        assertThat(singleActionTimer.getNextTimeout()).isNotSameAs(firstExpiration);
        assertThat(singleActionTimer.getInfo()).isEqualTo(new TimerPayload("reconcile", 1));
        assertThat(singleActionTimer.getHandle().getTimer()).isSameAs(singleActionTimer);
        assertThat(singleActionTimer.getTimeRemaining()).isGreaterThanOrEqualTo(0L);
        assertThat(intervalTimer.getInfo()).isEqualTo("interval");
        assertThat(timerService.getTimers()).containsExactly(singleActionTimer, intervalTimer);
        assertThat(timedObject.lastTimeoutInfo()).isEqualTo(new TimerPayload("reconcile", 1));

        singleActionTimer.cancel();

        assertThat(timerService.getTimers()).containsExactly(intervalTimer);
        assertThatExceptionOfType(NoSuchObjectLocalException.class).isThrownBy(singleActionTimer::getInfo);
    }

    @Test
    void messageContextRejectsNullPropertyNamesLikeMapBackedImplementations() {
        SimpleMessageContext context = new SimpleMessageContext();

        assertThatExceptionOfType(NullPointerException.class).isThrownBy(() -> context.setProperty(null, "value"));
        assertThatExceptionOfType(NullPointerException.class).isThrownBy(() -> context.getProperty(null));
        assertThatExceptionOfType(NullPointerException.class).isThrownBy(() -> context.containsProperty(null));
        assertThatExceptionOfType(NullPointerException.class).isThrownBy(() -> context.removeProperty(null));
    }

    private static final class SimpleTimerService implements TimerService {
        private final Collection<Timer> timers = new java.util.ArrayList<>();

        @Override
        public Timer createTimer(long duration, Serializable info) {
            return register(new SimpleTimer(this, timeoutAfter(duration), info));
        }

        @Override
        public Timer createTimer(long initialDuration, long intervalDuration, Serializable info) {
            return register(new SimpleTimer(this, timeoutAfter(initialDuration), info));
        }

        @Override
        public Timer createTimer(Date expiration, Serializable info) {
            return register(new SimpleTimer(this, expiration, info));
        }

        @Override
        public Timer createTimer(Date initialExpiration, long intervalDuration, Serializable info) {
            return register(new SimpleTimer(this, initialExpiration, info));
        }

        @Override
        public Collection getTimers() {
            return Collections.unmodifiableCollection(timers);
        }

        private Timer register(Timer timer) {
            timers.add(timer);
            return timer;
        }

        private void remove(Timer timer) {
            timers.remove(timer);
        }

        private Date timeoutAfter(long duration) {
            return new Date(System.currentTimeMillis() + duration);
        }
    }

    private static final class SimpleTimer implements Timer {
        private final SimpleTimerService timerService;
        private final Date nextTimeout;
        private final Serializable info;
        private boolean cancelled;

        private SimpleTimer(SimpleTimerService timerService, Date nextTimeout, Serializable info) {
            this.timerService = timerService;
            this.nextTimeout = copyDate(nextTimeout);
            this.info = info;
        }

        @Override
        public void cancel() {
            ensureActive();
            cancelled = true;
            timerService.remove(this);
        }

        @Override
        public long getTimeRemaining() {
            ensureActive();
            return Math.max(0L, nextTimeout.getTime() - System.currentTimeMillis());
        }

        @Override
        public Date getNextTimeout() {
            ensureActive();
            return copyDate(nextTimeout);
        }

        @Override
        public Serializable getInfo() {
            ensureActive();
            return info;
        }

        @Override
        public TimerHandle getHandle() {
            ensureActive();
            return new SimpleTimerHandle(this);
        }

        private void ensureActive() {
            if (cancelled) {
                throw new NoSuchObjectLocalException("Timer was cancelled");
            }
        }

        private Date copyDate(Date date) {
            return new Date(date.getTime());
        }
    }

    private static final class SimpleTimerHandle implements TimerHandle {
        private final Timer timer;

        private SimpleTimerHandle(Timer timer) {
            this.timer = timer;
        }

        @Override
        public Timer getTimer() {
            return timer;
        }
    }

    private static final class RecordingTimedObject implements TimedObject {
        private Serializable lastTimeoutInfo;

        @Override
        public void ejbTimeout(Timer timer) {
            lastTimeoutInfo = timer.getInfo();
        }

        private Serializable lastTimeoutInfo() {
            return lastTimeoutInfo;
        }
    }

    private static final class TimerPayload implements Serializable {
        private final String name;
        private final int attempts;

        private TimerPayload(String name, int attempts) {
            this.name = name;
            this.attempts = attempts;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof TimerPayload)) {
                return false;
            }
            TimerPayload that = (TimerPayload) other;
            return attempts == that.attempts && name.equals(that.name);
        }

        @Override
        public int hashCode() {
            return 31 * name.hashCode() + attempts;
        }

        @Override
        public String toString() {
            return "TimerPayload[name=" + name + ", attempts=" + attempts + "]";
        }
    }

    private static final class AuditHandler extends GenericHandler {
        private final QName[] headers;

        private AuditHandler(QName[] headers) {
            this.headers = Arrays.copyOf(headers, headers.length);
        }

        @Override
        public QName[] getHeaders() {
            return Arrays.copyOf(headers, headers.length);
        }
    }

    private static final class AlternativeHandler extends GenericHandler {
        @Override
        public QName[] getHeaders() {
            return new QName[0];
        }
    }

    private static final class SimpleMessageContext implements MessageContext {
        private final Map<String, Object> properties = new LinkedHashMap<>();

        @Override
        public void setProperty(String name, Object value) {
            properties.put(requireName(name), value);
        }

        @Override
        public Object getProperty(String name) {
            return properties.get(requireName(name));
        }

        @Override
        public void removeProperty(String name) {
            properties.remove(requireName(name));
        }

        @Override
        public boolean containsProperty(String name) {
            return properties.containsKey(requireName(name));
        }

        @Override
        public Iterator getPropertyNames() {
            return properties.keySet().iterator();
        }

        String[] propertyNames() {
            return properties.keySet().toArray(new String[0]);
        }

        private String requireName(String name) {
            if (name == null) {
                throw new NullPointerException("Property name must not be null");
            }
            return name;
        }
    }

    private static final class SimpleInvocationContext implements InvocationContext {
        private final Object target;
        private final Method method;
        private final Map<String, Object> contextData = new LinkedHashMap<>();
        private Object[] parameters;

        private SimpleInvocationContext(Object target, Method method, Object[] parameters) {
            this.target = target;
            this.method = method;
            this.parameters = Arrays.copyOf(parameters, parameters.length);
        }

        @Override
        public Object getTarget() {
            return target;
        }

        @Override
        public Method getMethod() {
            return method;
        }

        @Override
        public Object[] getParameters() {
            return Arrays.copyOf(parameters, parameters.length);
        }

        @Override
        public void setParameters(Object[] parameters) {
            this.parameters = Arrays.copyOf(parameters, parameters.length);
        }

        @Override
        public Map<String, Object> getContextData() {
            return contextData;
        }

        @Override
        public Object proceed() {
            CheckoutService service = (CheckoutService) target;
            return service.checkout((String) parameters[0], (Integer) parameters[1]);
        }
    }

    private static final class CheckoutService {
        String checkout(String sku, int quantity) {
            return sku + ":" + quantity;
        }
    }

    private static final class AuditInterceptor {
        @AroundInvoke
        Object recordAndProceed(InvocationContext context) throws Exception {
            context.getContextData().put("interceptor", "audit");
            return context.proceed();
        }
    }

    private interface InventoryOperations {
        String reserve(String sku, int quantity);
    }

    @Stateless(name = "InventoryBean", mappedName = "ejb/inventory", description = "Inventory facade")
    @Remote({InventoryOperations.class})
    @TransactionManagement(TransactionManagementType.CONTAINER)
    @TransactionAttribute(TransactionAttributeType.REQUIRED)
    @Interceptors(AuditInterceptor.class)
    private static final class InventoryBean implements InventoryOperations {
        @EJB(beanName = "BillingBean", mappedName = "ejb/billing")
        private BillingBean billingBean;

        @Override
        public String reserve(String sku, int quantity) {
            return sku + ":" + quantity;
        }

        @Remove(retainIfException = true)
        @ExcludeDefaultInterceptors
        public String cancel(String sku) {
            return "cancelled:" + sku;
        }
    }

    @Stateful(name = "BillingBean", mappedName = "ejb/billing", description = "Billing facade")
    @TransactionManagement(TransactionManagementType.BEAN)
    private static final class BillingBean {
        @ExcludeClassInterceptors
        String invoice(String orderId) {
            return "invoice:" + orderId;
        }
    }

    @Singleton(name = "StartupCache", mappedName = "ejb/cache", description = "Preloaded cache")
    @Startup
    @DependsOn({"InventoryBean", "BillingBean"})
    @BeanManagedConcurrency
    private static final class StartupCache {
        String refresh() {
            return "cache-refreshed";
        }
    }

    @MessageDriven(name = "QueueListener", mappedName = "jms/reconcile", description = "Queue listener")
    private static final class QueueListener {
        String onMessage(String payload) {
            return "received:" + payload;
        }
    }

    @ApplicationException(rollback = true)
    private static final class BusinessRuleException extends Exception {
    }

    private static final class ScheduledTasks {
        @Timeout
        String hourly() {
            return "hourly";
        }
    }
}
