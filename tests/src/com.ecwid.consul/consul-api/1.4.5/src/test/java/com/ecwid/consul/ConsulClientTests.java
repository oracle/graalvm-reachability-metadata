package com.ecwid.consul;

import java.io.File;
import java.io.IOException;
import java.io.Writer;
import java.net.ServerSocket;
import java.util.Collections;
import java.util.List;
import java.util.Random;

import com.ecwid.consul.v1.ConsulClient;
import com.ecwid.consul.v1.Response;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.catalina.Context;
import org.apache.catalina.LifecycleException;
import org.apache.catalina.connector.Connector;
import org.apache.catalina.startup.Tomcat;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ConsulClientTests {

	private static Tomcat tomcat;
	private static ConsulClient consulClient;

	private static byte[] requestBody;
	private static String requestUri;
	private static List<String> requestHeaderNames;

	@BeforeAll
	static void setUpAll() {
		int port = getFreePort();
		try {
			tomcat = new Tomcat();
			Connector connector = new Connector("HTTP/1.1");
			connector.setPort(port);
			tomcat.setConnector(connector);
			Context context = tomcat.addContext("", new File(".").getAbsolutePath());
			Tomcat.addDefaultMimeTypeMappings(context);
			Tomcat.addServlet(context, "test", new TestServlet());
			context.addServletMappingDecoded("/*", "test");
			tomcat.start();
		}
		catch (LifecycleException e) {
			throw new RuntimeException(e);
		}
		consulClient = new ConsulClient("localhost", port);
	}

	@AfterAll
	static void tearDownAll() {
		try {
			tomcat.stop();
			tomcat.destroy();
		}
		catch (LifecycleException e) {
			throw new RuntimeException(e);
		}
	}

	@Test
	void shouldSetKVBinaryValue() {
		final String testKey = "test_key";
		final byte[] testValue = new byte[100];
		new Random().nextBytes(testValue);

		Response<Boolean> response = consulClient.setKVBinaryValue(testKey, testValue);

		assertThat(response.getValue()).isTrue();
		assertThat(testValue).isEqualTo(requestBody);
		assertThat(requestUri).endsWith(testKey);
		assertThat(requestHeaderNames).contains("x-consul-token");
	}


	private static int getFreePort() {
		try (ServerSocket socket = new ServerSocket(0)) {
			return socket.getLocalPort();
		}
		catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	static class TestServlet extends HttpServlet {

		@Override
		protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
			super.doGet(req, resp);
		}

		@Override
		protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
			requestBody = req.getInputStream().readAllBytes();
			resp.setStatus(200);
			resp.setContentType("JSON/UTF-8");
			try (Writer writer = resp.getWriter()) {
				writer.write("true");
			}
		}

		@Override
		protected void doPut(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
			requestUri = req.getRequestURI();
			requestBody = req.getInputStream().readAllBytes();
			requestHeaderNames = Collections.list(req.getHeaderNames());
			resp.setStatus(200);
			resp.setContentType("JSON/UTF-8");
			try (Writer writer = resp.getWriter()) {
				writer.write("true");
			}
		}
	}

}
