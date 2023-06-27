/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com.alibaba.nacos;

import java.io.File;
import java.io.IOException;
import java.time.Duration;

import com.alibaba.nacos.api.exception.NacosException;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

public class NacosClientTest {

	private static final String username = "nacos";

	private static final String password = "nacos";

	private static final String dataIdValue = "nacos.example";

	private static final String groupValue = "com.alibaba.nacos";

	private static final String contentValue = "test";

	private static final String address = "http://localhost:8848/nacos/v1/cs/configs";

	private static Process process;

	private static String checkNacosServer() {

		return com.alibaba.nacos.HttpRequest.sendPost(
				"http://localhost:8848/nacos/v1/auth/users/login?message=true",
				"username=" + username + "&password=" + password
		);
	}

	/**
	 * start nacos server
	 */
	@BeforeAll
	static void beforeAll() throws IOException {

		System.out.println("Starting Nacos Server ...");

		process = new ProcessBuilder(
				"docker", "run", "--name", "nacos-server", "-e", "MODE=standalone", "-p", "8848:8848", "-d",
				"nacos/nacos-server:v2.1.0")
				.redirectOutput(new File("nacos-server-stdout.txt"))
				.redirectError(new File("nacos-server-stderr.txt"))
				.start();

		// Wait until connection can be established
		Awaitility.await()
				.atMost(Duration.ofMinutes(1))
				.ignoreExceptionsMatching(e ->
						e instanceof NacosException
				).until(() -> {
					System.out.println(checkNacosServer());
					Assertions.assertNotNull(checkNacosServer());
					return true;
				});

		System.out.println("Nacos server started");
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

	@Test
	void testAddConfig() {

		String result = com.alibaba.nacos.HttpRequest.sendPost(
				address,
				"dataId=" + dataIdValue + "&group=" +
						groupValue + "&content=" + contentValue
		);

		Assertions.assertTrue(Boolean.parseBoolean(result));
	}

	@Test
	void testGetConfig() {

		String result = com.alibaba.nacos.HttpRequest.sendGet(
				address,
				"dataId=" + dataIdValue + "&group=" +
						groupValue
		);

		Assertions.assertEquals(result, contentValue);
	}

}
