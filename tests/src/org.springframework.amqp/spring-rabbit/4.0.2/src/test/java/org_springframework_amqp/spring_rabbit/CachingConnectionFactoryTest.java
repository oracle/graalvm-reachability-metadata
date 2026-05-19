/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_amqp.spring_rabbit;

import java.io.IOException;
import java.net.InetAddress;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeoutException;

import com.rabbitmq.client.AMQP.Basic.RecoverOk;
import com.rabbitmq.client.AMQP.BasicProperties;
import com.rabbitmq.client.AMQP.Exchange.BindOk;
import com.rabbitmq.client.AMQP.Exchange.DeclareOk;
import com.rabbitmq.client.AMQP.Exchange.DeleteOk;
import com.rabbitmq.client.AMQP.Exchange.UnbindOk;
import com.rabbitmq.client.AMQP.Queue.PurgeOk;
import com.rabbitmq.client.AMQP.Tx.CommitOk;
import com.rabbitmq.client.AMQP.Tx.RollbackOk;
import com.rabbitmq.client.AMQP.Tx.SelectOk;
import com.rabbitmq.client.BlockedCallback;
import com.rabbitmq.client.BlockedListener;
import com.rabbitmq.client.BuiltinExchangeType;
import com.rabbitmq.client.CancelCallback;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Command;
import com.rabbitmq.client.ConfirmCallback;
import com.rabbitmq.client.ConfirmListener;
import com.rabbitmq.client.Consumer;
import com.rabbitmq.client.ConsumerShutdownSignalCallback;
import com.rabbitmq.client.DeliverCallback;
import com.rabbitmq.client.ExceptionHandler;
import com.rabbitmq.client.GetResponse;
import com.rabbitmq.client.Method;
import com.rabbitmq.client.ReturnCallback;
import com.rabbitmq.client.ReturnListener;
import com.rabbitmq.client.ShutdownListener;
import com.rabbitmq.client.ShutdownSignalException;
import com.rabbitmq.client.UnblockedCallback;
import org.junit.jupiter.api.Test;

import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.connection.ChannelProxy;
import org.springframework.amqp.rabbit.connection.Connection;

import static org.assertj.core.api.Assertions.assertThat;

public class CachingConnectionFactoryTest {

    @Test
    void createChannelBuildsCachedChannelProxy() throws Exception {
        TestRabbitConnectionFactory rabbitConnectionFactory = new TestRabbitConnectionFactory();
        CachingConnectionFactory connectionFactory = new CachingConnectionFactory(rabbitConnectionFactory);

        try {
            Connection connection = connectionFactory.createConnection();
            Channel channel = connection.createChannel(false);

            assertThat(channel).isInstanceOf(ChannelProxy.class);
            assertThat(((ChannelProxy) channel).getTargetChannel())
                    .isSameAs(rabbitConnectionFactory.connection.channel);
            channel.close();
        } finally {
            connectionFactory.destroy();
        }
    }

    static final class TestRabbitConnectionFactory extends com.rabbitmq.client.ConnectionFactory {

        private final TestRabbitConnection connection = new TestRabbitConnection();

        @Override
        public com.rabbitmq.client.Connection newConnection(ExecutorService executor, String connectionName)
                throws IOException, TimeoutException {

            return this.connection;
        }

    }

    private static final class TestRabbitConnection implements com.rabbitmq.client.Connection {

        private final TestChannel channel = new TestChannel(this);

        private boolean open = true;

        @Override
        public InetAddress getAddress() {
            return InetAddress.getLoopbackAddress();
        }

        @Override
        public int getPort() {
            return 5672;
        }

        @Override
        public int getChannelMax() {
            return 2047;
        }

        @Override
        public int getFrameMax() {
            return 0;
        }

        @Override
        public int getHeartbeat() {
            return 0;
        }

        @Override
        public Map<String, Object> getClientProperties() {
            return Collections.emptyMap();
        }

        @Override
        public String getClientProvidedName() {
            return "test-connection";
        }

        @Override
        public Map<String, Object> getServerProperties() {
            return Collections.emptyMap();
        }

        @Override
        public Channel createChannel() {
            return this.channel;
        }

        @Override
        public Channel createChannel(int channelNumber) {
            return this.channel;
        }

        @Override
        public void close() {
            this.open = false;
        }

        @Override
        public void close(int closeCode, String closeMessage) {
            close();
        }

        @Override
        public void close(int timeout) {
            close();
        }

        @Override
        public void close(int closeCode, String closeMessage, int timeout) {
            close();
        }

        @Override
        public void abort() {
            close();
        }

        @Override
        public void abort(int closeCode, String closeMessage) {
            close();
        }

        @Override
        public void abort(int timeout) {
            close();
        }

        @Override
        public void abort(int closeCode, String closeMessage, int timeout) {
            close();
        }

        @Override
        public void addBlockedListener(BlockedListener listener) {
        }

        @Override
        public BlockedListener addBlockedListener(BlockedCallback blockedCallback,
                UnblockedCallback unblockedCallback) {
            return null;
        }

        @Override
        public boolean removeBlockedListener(BlockedListener listener) {
            return false;
        }

        @Override
        public void clearBlockedListeners() {
        }

        @Override
        public ExceptionHandler getExceptionHandler() {
            return null;
        }

        @Override
        public String getId() {
            return "test-rabbit-connection";
        }

        @Override
        public void setId(String id) {
        }

        @Override
        public void addShutdownListener(ShutdownListener listener) {
        }

        @Override
        public void removeShutdownListener(ShutdownListener listener) {
        }

        @Override
        public ShutdownSignalException getCloseReason() {
            return null;
        }

        @Override
        public void notifyListeners() {
        }

        @Override
        public boolean isOpen() {
            return this.open;
        }

    }

    private static final class TestChannel implements Channel {

        private final com.rabbitmq.client.Connection connection;

        private boolean open = true;

        private TestChannel(com.rabbitmq.client.Connection connection) {
            this.connection = connection;
        }

        @Override
        public int getChannelNumber() {
            return 1;
        }

        @Override
        public com.rabbitmq.client.Connection getConnection() {
            return this.connection;
        }

        @Override
        public void close() {
            this.open = false;
        }

        @Override
        public void close(int closeCode, String closeMessage) {
            close();
        }

        @Override
        public void abort() {
            close();
        }

        @Override
        public void abort(int closeCode, String closeMessage) {
            close();
        }

        @Override
        public void addReturnListener(ReturnListener listener) {
        }

        @Override
        public ReturnListener addReturnListener(ReturnCallback returnCallback) {
            return null;
        }

        @Override
        public boolean removeReturnListener(ReturnListener listener) {
            return false;
        }

        @Override
        public void clearReturnListeners() {
        }

        @Override
        public void addConfirmListener(ConfirmListener listener) {
        }

        @Override
        public ConfirmListener addConfirmListener(ConfirmCallback ackCallback, ConfirmCallback nackCallback) {
            return null;
        }

        @Override
        public boolean removeConfirmListener(ConfirmListener listener) {
            return false;
        }

        @Override
        public void clearConfirmListeners() {
        }

        @Override
        public Consumer getDefaultConsumer() {
            return null;
        }

        @Override
        public void setDefaultConsumer(Consumer consumer) {
        }

        @Override
        public void basicQos(int prefetchSize, int prefetchCount, boolean global) {
        }

        @Override
        public void basicQos(int prefetchCount, boolean global) {
        }

        @Override
        public void basicQos(int prefetchCount) {
        }

        @Override
        public void basicPublish(String exchange, String routingKey, BasicProperties props, byte[] body) {
        }

        @Override
        public void basicPublish(String exchange, String routingKey, boolean mandatory, BasicProperties props,
                byte[] body) {
        }

        @Override
        public void basicPublish(String exchange, String routingKey, boolean mandatory, boolean immediate,
                BasicProperties props, byte[] body) {
        }

        @Override
        public DeclareOk exchangeDeclare(String exchange, String type) {
            return null;
        }

        @Override
        public DeclareOk exchangeDeclare(String exchange, BuiltinExchangeType type) {
            return null;
        }

        @Override
        public DeclareOk exchangeDeclare(String exchange, String type, boolean durable) {
            return null;
        }

        @Override
        public DeclareOk exchangeDeclare(String exchange, BuiltinExchangeType type, boolean durable) {
            return null;
        }

        @Override
        public DeclareOk exchangeDeclare(String exchange, String type, boolean durable, boolean autoDelete,
                Map<String, Object> arguments) {

            return null;
        }

        @Override
        public DeclareOk exchangeDeclare(String exchange, BuiltinExchangeType type, boolean durable, boolean autoDelete,
                Map<String, Object> arguments) {

            return null;
        }

        @Override
        public DeclareOk exchangeDeclare(String exchange, String type, boolean durable, boolean autoDelete,
                boolean internal, Map<String, Object> arguments) {

            return null;
        }

        @Override
        public DeclareOk exchangeDeclare(String exchange, BuiltinExchangeType type, boolean durable,
                boolean autoDelete, boolean internal, Map<String, Object> arguments) {

            return null;
        }

        @Override
        public void exchangeDeclareNoWait(String exchange, String type, boolean durable, boolean autoDelete,
                boolean internal, Map<String, Object> arguments) {
        }

        @Override
        public void exchangeDeclareNoWait(String exchange, BuiltinExchangeType type, boolean durable,
                boolean autoDelete, boolean internal, Map<String, Object> arguments) {
        }

        @Override
        public DeclareOk exchangeDeclarePassive(String name) {
            return null;
        }

        @Override
        public DeleteOk exchangeDelete(String exchange, boolean ifUnused) {
            return null;
        }

        @Override
        public void exchangeDeleteNoWait(String exchange, boolean ifUnused) {
        }

        @Override
        public DeleteOk exchangeDelete(String exchange) {
            return null;
        }

        @Override
        public BindOk exchangeBind(String destination, String source, String routingKey) {
            return null;
        }

        @Override
        public BindOk exchangeBind(String destination, String source, String routingKey,
                Map<String, Object> arguments) {

            return null;
        }

        @Override
        public void exchangeBindNoWait(String destination, String source, String routingKey,
                Map<String, Object> arguments) {
        }

        @Override
        public UnbindOk exchangeUnbind(String destination, String source, String routingKey) {
            return null;
        }

        @Override
        public UnbindOk exchangeUnbind(String destination, String source, String routingKey,
                Map<String, Object> arguments) {

            return null;
        }

        @Override
        public void exchangeUnbindNoWait(String destination, String source, String routingKey,
                Map<String, Object> arguments) {
        }

        @Override
        public com.rabbitmq.client.AMQP.Queue.DeclareOk queueDeclare() {
            return null;
        }

        @Override
        public com.rabbitmq.client.AMQP.Queue.DeclareOk queueDeclare(String queue, boolean durable, boolean exclusive,
                boolean autoDelete, Map<String, Object> arguments) {

            return null;
        }

        @Override
        public void queueDeclareNoWait(String queue, boolean durable, boolean exclusive, boolean autoDelete,
                Map<String, Object> arguments) {
        }

        @Override
        public com.rabbitmq.client.AMQP.Queue.DeclareOk queueDeclarePassive(String queue) {
            return null;
        }

        @Override
        public com.rabbitmq.client.AMQP.Queue.DeleteOk queueDelete(String queue) {
            return null;
        }

        @Override
        public com.rabbitmq.client.AMQP.Queue.DeleteOk queueDelete(String queue, boolean ifUnused, boolean ifEmpty) {
            return null;
        }

        @Override
        public void queueDeleteNoWait(String queue, boolean ifUnused, boolean ifEmpty) {
        }

        @Override
        public com.rabbitmq.client.AMQP.Queue.BindOk queueBind(String queue, String exchange, String routingKey) {
            return null;
        }

        @Override
        public com.rabbitmq.client.AMQP.Queue.BindOk queueBind(String queue, String exchange, String routingKey,
                Map<String, Object> arguments) {

            return null;
        }

        @Override
        public void queueBindNoWait(String queue, String exchange, String routingKey, Map<String, Object> arguments) {
        }

        @Override
        public com.rabbitmq.client.AMQP.Queue.UnbindOk queueUnbind(String queue, String exchange, String routingKey) {
            return null;
        }

        @Override
        public com.rabbitmq.client.AMQP.Queue.UnbindOk queueUnbind(String queue, String exchange, String routingKey,
                Map<String, Object> arguments) {

            return null;
        }

        @Override
        public PurgeOk queuePurge(String queue) {
            return null;
        }

        @Override
        public GetResponse basicGet(String queue, boolean autoAck) {
            return null;
        }

        @Override
        public void basicAck(long deliveryTag, boolean multiple) {
        }

        @Override
        public void basicNack(long deliveryTag, boolean multiple, boolean requeue) {
        }

        @Override
        public void basicReject(long deliveryTag, boolean requeue) {
        }

        @Override
        public String basicConsume(String queue, Consumer callback) {
            return "consumer";
        }

        @Override
        public String basicConsume(String queue, DeliverCallback deliverCallback, CancelCallback cancelCallback) {
            return "consumer";
        }

        @Override
        public String basicConsume(String queue, DeliverCallback deliverCallback,
                ConsumerShutdownSignalCallback shutdownSignalCallback) {

            return "consumer";
        }

        @Override
        public String basicConsume(String queue, DeliverCallback deliverCallback, CancelCallback cancelCallback,
                ConsumerShutdownSignalCallback shutdownSignalCallback) {

            return "consumer";
        }

        @Override
        public String basicConsume(String queue, boolean autoAck, Consumer callback) {
            return "consumer";
        }

        @Override
        public String basicConsume(String queue, boolean autoAck, DeliverCallback deliverCallback,
                CancelCallback cancelCallback) {

            return "consumer";
        }

        @Override
        public String basicConsume(String queue, boolean autoAck, DeliverCallback deliverCallback,
                ConsumerShutdownSignalCallback shutdownSignalCallback) {

            return "consumer";
        }

        @Override
        public String basicConsume(String queue, boolean autoAck, DeliverCallback deliverCallback,
                CancelCallback cancelCallback, ConsumerShutdownSignalCallback shutdownSignalCallback) {

            return "consumer";
        }

        @Override
        public String basicConsume(String queue, boolean autoAck, Map<String, Object> arguments, Consumer callback) {
            return "consumer";
        }

        @Override
        public String basicConsume(String queue, boolean autoAck, Map<String, Object> arguments,
                DeliverCallback deliverCallback, CancelCallback cancelCallback) {

            return "consumer";
        }

        @Override
        public String basicConsume(String queue, boolean autoAck, Map<String, Object> arguments,
                DeliverCallback deliverCallback, ConsumerShutdownSignalCallback shutdownSignalCallback) {

            return "consumer";
        }

        @Override
        public String basicConsume(String queue, boolean autoAck, Map<String, Object> arguments,
                DeliverCallback deliverCallback, CancelCallback cancelCallback,
                ConsumerShutdownSignalCallback shutdownSignalCallback) {

            return "consumer";
        }

        @Override
        public String basicConsume(String queue, boolean autoAck, String consumerTag, Consumer callback) {
            return consumerTag;
        }

        @Override
        public String basicConsume(String queue, boolean autoAck, String consumerTag, DeliverCallback deliverCallback,
                CancelCallback cancelCallback) {

            return consumerTag;
        }

        @Override
        public String basicConsume(String queue, boolean autoAck, String consumerTag, DeliverCallback deliverCallback,
                ConsumerShutdownSignalCallback shutdownSignalCallback) {

            return consumerTag;
        }

        @Override
        public String basicConsume(String queue, boolean autoAck, String consumerTag, DeliverCallback deliverCallback,
                CancelCallback cancelCallback, ConsumerShutdownSignalCallback shutdownSignalCallback) {

            return consumerTag;
        }

        @Override
        public String basicConsume(String queue, boolean autoAck, String consumerTag, boolean noLocal,
                boolean exclusive, Map<String, Object> arguments, Consumer callback) {

            return consumerTag;
        }

        @Override
        public String basicConsume(String queue, boolean autoAck, String consumerTag, boolean noLocal,
                boolean exclusive, Map<String, Object> arguments, DeliverCallback deliverCallback,
                CancelCallback cancelCallback) {

            return consumerTag;
        }

        @Override
        public String basicConsume(String queue, boolean autoAck, String consumerTag, boolean noLocal,
                boolean exclusive, Map<String, Object> arguments, DeliverCallback deliverCallback,
                ConsumerShutdownSignalCallback shutdownSignalCallback) {

            return consumerTag;
        }

        @Override
        public String basicConsume(String queue, boolean autoAck, String consumerTag, boolean noLocal,
                boolean exclusive, Map<String, Object> arguments, DeliverCallback deliverCallback,
                CancelCallback cancelCallback, ConsumerShutdownSignalCallback shutdownSignalCallback) {

            return consumerTag;
        }

        @Override
        public void basicCancel(String consumerTag) {
        }

        @Override
        public RecoverOk basicRecover() {
            return null;
        }

        @Override
        public RecoverOk basicRecover(boolean requeue) {
            return null;
        }

        @Override
        public SelectOk txSelect() {
            return null;
        }

        @Override
        public CommitOk txCommit() {
            return null;
        }

        @Override
        public RollbackOk txRollback() {
            return null;
        }

        @Override
        public com.rabbitmq.client.AMQP.Confirm.SelectOk confirmSelect() {
            return null;
        }

        @Override
        public long getNextPublishSeqNo() {
            return 1L;
        }

        @Override
        public boolean waitForConfirms() {
            return true;
        }

        @Override
        public boolean waitForConfirms(long timeout) {
            return true;
        }

        @Override
        public void waitForConfirmsOrDie() {
        }

        @Override
        public void waitForConfirmsOrDie(long timeout) {
        }

        @Override
        public void asyncRpc(Method method) {
        }

        @Override
        public Command rpc(Method method) {
            return null;
        }

        @Override
        public long messageCount(String queue) {
            return 0;
        }

        @Override
        public long consumerCount(String queue) {
            return 0;
        }

        @Override
        public CompletableFuture<Command> asyncCompletableRpc(Method method) {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public void addShutdownListener(ShutdownListener listener) {
        }

        @Override
        public void removeShutdownListener(ShutdownListener listener) {
        }

        @Override
        public ShutdownSignalException getCloseReason() {
            return null;
        }

        @Override
        public void notifyListeners() {
        }

        @Override
        public boolean isOpen() {
            return this.open;
        }

    }

}
