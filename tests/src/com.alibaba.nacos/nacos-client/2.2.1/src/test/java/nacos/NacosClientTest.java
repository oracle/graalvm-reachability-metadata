/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package nacos;

import java.io.File;
import java.io.IOException;
import java.time.Duration;
import java.util.Properties;
import java.util.concurrent.Executor;

import com.alibaba.nacos.api.NacosFactory;
import com.alibaba.nacos.api.PropertyKeyConst;
import com.alibaba.nacos.api.config.ConfigService;
import com.alibaba.nacos.api.config.listener.Listener;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.naming.NamingFactory;
import com.alibaba.nacos.api.naming.NamingService;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class NacosClientTest {

	private static final String dataId = "nacos.example";

	private static final String group = "com.alibaba.nacos";

	private static final String namespace = "public";

	private static final String serverAddr = "localhost";

	private static final String contentValue = "test";

	private static final String msg = "The nacos server status exception!\n";

	private static Process process;

	/**
	 * get nacos server connection
	 * @return ConfigService
	 */
	private static ConfigService getConnection() throws NacosException {

		Properties properties = new Properties();
		properties.put(PropertyKeyConst.SERVER_ADDR, serverAddr);

		ConfigService configService = NacosFactory.createConfigService(properties);

		configService.addListener(dataId, group, new Listener() {
			@Override
			public void receiveConfigInfo(String configInfo) {
				System.out.println("receive:" + configInfo);
			}

			@Override
			public Executor getExecutor() {
				return null;
			}
		});

		return configService;
	}

	/**
	 * start nacos server
	 */
	@BeforeAll
	static void beforeAll() throws IOException, InterruptedException {

		System.out.println("Starting Nacos Server ...");

		process = new ProcessBuilder(
				"docker", "run", "--name", "nacos-server",
				"-e", "MODE=standalone", "-p", "8848:8848", "-d",
				"nacos/nacos-server:v2.2.0"
		)
				.redirectOutput(new File("nacos-server-stdout.txt"))
				.redirectError(new File("nacos-server-stderr.txt"))
				.start();

		// Wait until connection can be established
		Awaitility.await().atMost(Duration.ofMinutes(1)).ignoreExceptionsMatching(e ->
				e instanceof NacosException
		).until(() -> {
			ConfigService connection = getConnection();
			System.out.println(connection.getServerStatus());
			connection.shutDown();
			return true;
		});

		System.out.println("Nacos server started");
	}

	/**
	 * add config test
	 */
	@Test
	@Order(-1)
	void testAddConfig() throws NacosException {

		ConfigService service = getConnection();

		Assertions.assertEquals("UP", service.getServerStatus(), msg);

		boolean isPublishOk = service.publishConfig(dataId, group, contentValue);

		Assertions.assertTrue(isPublishOk);
	}

	/**
	 * get config test
	 */
	@Test
	@Order(1)
	void testGetConfig() throws NacosException {

		ConfigService service = getConnection();
		Assertions.assertEquals("UP", service.getServerStatus(), msg);

		String content = service.getConfig(dataId, group, 5000);
		Assertions.assertEquals(content, contentValue);
	}

	/**
	 * remove config test
	 */
	@Test
	@Order(3)
	void testRemoveConfig() throws NacosException {

		ConfigService service = getConnection();
		Assertions.assertEquals("UP", service.getServerStatus(), msg);

		boolean isRemoveOk = service.removeConfig(dataId, group);
		Assertions.assertTrue(isRemoveOk);
	}

	/**
	 * service register test
	 */
	@Test
	@Order(5)
	void testServiceRegisterAndDestroy() throws NacosException, InterruptedException {

		Properties properties = new Properties();
		properties.setProperty("serverAddr", serverAddr);
		properties.setProperty("namespace", namespace);

		NamingService naming = NamingFactory.createNamingService(properties);

		Assertions.assertEquals("UP", naming.getServerStatus(), msg);

		/* service register */
		naming.registerInstance("service-provider", "2.2.2.2", 9999, "DEFAULT");

		Thread.sleep(3000);

		Assertions.assertEquals(naming.getAllInstances("service-provider").size(), 1);

		/* service destroy */
		naming.deregisterInstance("service-provider", "2.2.2.2", 9999, "DEFAULT");

		Thread.sleep(3000);

		Assertions.assertEquals(naming.getAllInstances("service-provider").size(), 0);
	}

	/**
	 * shutdown nacos server
	 */
	@AfterAll
	static void tearDown() {

		if (process != null && process.isAlive()) {

			System.out.println("Shutting down Nacos Server");
			process.destroy();
		}
	}

}
