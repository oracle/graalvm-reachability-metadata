/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com.ecwid.consul;

import java.io.IOException;
import java.io.Writer;
import java.util.List;

import com.ecwid.consul.v1.ConsulRawClient;
import com.ecwid.consul.v1.QueryParams;
import com.ecwid.consul.v1.Response;
import com.ecwid.consul.v1.event.EventConsulClient;
import com.ecwid.consul.v1.event.EventListRequest;
import com.ecwid.consul.v1.event.model.Event;
import com.ecwid.consul.v1.event.model.EventParams;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.catalina.LifecycleException;
import org.apache.catalina.startup.Tomcat;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.ecwid.consul.utils.ConsulTestUtils.getFreePort;
import static com.ecwid.consul.utils.ConsulTestUtils.initTomcat;
import static org.assertj.core.api.Assertions.assertThat;

class EventConsulClientTests {

	private Tomcat tomcat;
	private EventConsulClient consulClient;
	private byte[] requestBody;
	private String requestUri;

	@BeforeEach
	void setUp() {
		int port = getFreePort();
		try {
			tomcat = initTomcat(port, new TestServlet());
		}
		catch (LifecycleException e) {
			throw new RuntimeException(e);
		}
		ConsulRawClient consulRawClient = new ConsulRawClient("localhost", port);
		consulClient = new EventConsulClient(consulRawClient);
	}

	@AfterEach
	void tearDownAll() {
		try {
			tomcat.stop();
			tomcat.destroy();
		}
		catch (LifecycleException e) {
			throw new RuntimeException(e);
		}
	}

	@Test
	void shouldFireEvent() {
		Response<Event> response = consulClient.eventFire("test", "test", new EventParams(), new QueryParams("test"));

		assertThat(response).isNotNull();
		Event event = response.getValue();
		assertThat(event.getId()).isEqualTo("test");
		assertThat(new String(requestBody)).isEqualTo("test");
		assertThat(requestUri).endsWith("/v1/event/fire/test");
	}

	@Test
	void shouldRetrieveEvents() {
		Response<List<Event>> response = consulClient.eventList(new EventListRequest.Builder().setService("test")
				.build());

		assertThat(response).isNotNull();
		List<Event> events = response.getValue();
		assertThat(events).hasSize(1);
		assertThat(events.get(0).getId()).isEqualTo("test");
		assertThat(requestUri).endsWith("/v1/event/list");
	}

	private class TestServlet extends HttpServlet {

		@Override
		protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
			requestUri = req.getRequestURI();
			resp.setStatus(200);
			resp.setContentType("JSON/UTF-8");
			try (Writer writer = resp.getWriter()) {
				writer.write("[{\"ID\":\"test\"}]");
			}
		}

		@Override
		protected void doPut(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
			requestUri = req.getRequestURI();
			requestBody = req.getInputStream().readAllBytes();
			resp.setStatus(200);
			resp.setContentType("JSON/UTF-8");
			try (Writer writer = resp.getWriter()) {
				writer.write("{\"ID\": \"test\"}");
			}
		}
	}
}
