/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package jakarta_servlet.jakarta_servlet_api;

import java.io.IOException;

import jakarta.servlet.GenericServlet;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServlet;
import org.junit.jupiter.api.Test;


class JakartaServletTests {
    @Test
    void instantiateServlet() {
        new CustomServlet();
    }

  @Test
  void instantiateHttpServlet() {
    new CustomHttpServlet();
  }

  static class CustomServlet extends GenericServlet {
    @Override
    public void service(ServletRequest req, ServletResponse res) throws ServletException, IOException {

    }
  }

  static class CustomHttpServlet extends HttpServlet {

  }
}
