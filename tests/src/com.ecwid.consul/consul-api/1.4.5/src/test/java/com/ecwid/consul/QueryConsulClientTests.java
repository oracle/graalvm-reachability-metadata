/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com.ecwid.consul;

import java.io.IOException;
import java.io.Writer;
import java.util.UUID;

import com.ecwid.consul.v1.ConsulRawClient;
import com.ecwid.consul.v1.QueryParams;
import com.ecwid.consul.v1.Response;
import com.ecwid.consul.v1.query.QueryConsulClient;
import com.ecwid.consul.v1.query.model.QueryExecution;
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

class QueryConsulClientTests {

	private Tomcat tomcat;
	private QueryConsulClient consulClient;
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
		consulClient = new QueryConsulClient(consulRawClient);
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
	void shouldExecuteQuery() {
		String uuid = UUID.randomUUID().toString();
		Response<QueryExecution> response = consulClient.executePreparedQuery(uuid, new QueryParams("test"));

		assertThat(response).isNotNull();
		assertThat(response.getValue()).isNotNull();
		assertThat(requestUri).endsWith("/v1/query/" + uuid + "/execute");
	}

	private class TestServlet extends HttpServlet {

		@Override
		protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
			requestUri = req.getRequestURI();
			resp.setStatus(200);
			resp.setContentType("JSON/UTF-8");
			try (Writer writer = resp.getWriter()) {
				writer.write("{\"service\":\"test\"}");
			}
		}

	}
}
