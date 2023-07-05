/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package rocketmq;

import java.io.File;
import java.io.IOException;
import java.time.Duration;

import org.apache.rocketmq.client.consumer.DefaultMQPushConsumer;
import org.apache.rocketmq.client.consumer.listener.ConsumeConcurrentlyStatus;
import org.apache.rocketmq.client.consumer.listener.MessageListenerConcurrently;
import org.apache.rocketmq.client.exception.MQClientException;
import org.apache.rocketmq.client.producer.DefaultMQProducer;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.common.consumer.ConsumeFromWhere;
import org.apache.rocketmq.common.message.Message;
import org.apache.rocketmq.remoting.common.RemotingHelper;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

public class RocketMQTests {

	public static final String CONSUMER_GROUP = "rocketmq_test_group";

	public static final String PRODUCER_GROUP = "rocketmq_test_group";

	public static final String DEFAULT_NAMESRVADDR = "127.0.0.1:9876";

	public static final String TOPIC = "TopicTest";

	public static final String TAG = "TagA";

	public static final int MESSAGE_COUNT = 100;

	private static Process process;
	
	private static Process process1;

	private static Process process2;


	/**
	 * start RocketMQ Docker container.
	 */
	@BeforeAll
	static void beforeAll() throws IOException {

		System.out.println("Starting RocketMQ ...");
		
		process = new ProcessBuilder(
			"docker", "network", "create", "rocketmq"
		).start();

		process1 = new ProcessBuilder(
				"docker", "run", "-it", "--net", "rocketmq", "-d", "-p", "9876:9876", "--name", "rmqnamesrv", "apache/rocketmq:4.9.4", "./mqnamesrv"
		)
				.redirectOutput(new File("rocketmq-namesrv-stdout.txt"))
				.redirectError(new File("rocketmq-namesrv-stderr.txt"))
				.start();

		process2 = new ProcessBuilder(
				"docker", "run", "-it", "-d", "--net", "rocketmq", "--name", "rmqbroker", "apache/rocketmq:4.9.4", "./mqbroker", "-n", "rmqnamesrv:9876"
		)		
				.redirectOutput(new File("rocketmq-broker-stdout.txt"))
				.redirectError(new File("rocketmq-broker-stderr.txt"))
				.start();

		Awaitility.await().atMost(Duration.ofMinutes(1)).ignoreExceptionsMatching(e ->
				e instanceof MQClientException
		).until(() -> {
			getProducerConnection().shutdown();
			return true;
		});

		System.out.println("RocketMQ started");
	}

	private static DefaultMQProducer getProducerConnection() {

		DefaultMQProducer producer = new DefaultMQProducer(PRODUCER_GROUP);
		producer.setNamesrvAddr(DEFAULT_NAMESRVADDR);

		return producer;
	}

	/**
	 * Shutting down RocketMQ Docker container.
	 */
	@AfterAll
	static void tearDown() {
		if (process1 != null && process1.isAlive() && process2 != null && process2.isAlive()) {

			System.out.println("Shutting down RocketMQ");
			process.destroy();
			process1.destroy();
			process2.destroy();
		}
	}

	/*--------------------------------------- Simple Example Test ---------------------------------------*/

	/**
	 * test RocketMQ producer.
	 */
	@Test
	void testRocketMQProducer() throws InterruptedException, MQClientException {

		DefaultMQProducer producer = getProducerConnection();

		/*
		 * Launch the instance.
		 */
		producer.start();

		for (int i = 0; i < MESSAGE_COUNT; i++) {
			try {
				Message msg = new Message(
						TOPIC,
						TAG,
						("Hello RocketMQ " + i).getBytes(RemotingHelper.DEFAULT_CHARSET)
				);

				SendResult sendResult = producer.send(msg);

				System.out.printf("%s%n", sendResult);

			} catch (Exception e) {

				e.printStackTrace();
				Thread.sleep(1000);
			}
		}

		producer.shutdown();
	}

	/**
	 * test RocketMQ consumer.
	 */
	@Test
	void testRocketMQConsumer() throws MQClientException {

		DefaultMQPushConsumer consumer = new DefaultMQPushConsumer(CONSUMER_GROUP);
		consumer.setConsumeFromWhere(ConsumeFromWhere.CONSUME_FROM_FIRST_OFFSET);
		consumer.setNamesrvAddr(DEFAULT_NAMESRVADDR);
		consumer.subscribe(TOPIC, "*");

		consumer.registerMessageListener((MessageListenerConcurrently) (msg, context) -> {
			System.out.printf("%s Receive New Messages: %s %n", Thread.currentThread().getName(), msg);
			return ConsumeConcurrentlyStatus.CONSUME_SUCCESS;
		});

		consumer.start();

		System.out.printf("Consumer Started.%n");

	}

}