package basic;

import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class BasicTest {

    private static final String RESOURCE = "/resource.txt";

    @Test
    public void resourceTest() {
        try(InputStream is = BasicTest.class.getResourceAsStream(RESOURCE)) {
            assertNotNull(is);
            InputStreamReader isr = new InputStreamReader(is);
            BufferedReader br = new BufferedReader(isr);

            assertTrue(br.readLine().equalsIgnoreCase("Hello from resource!"));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

}
