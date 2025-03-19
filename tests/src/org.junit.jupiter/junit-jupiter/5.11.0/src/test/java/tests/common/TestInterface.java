package tests.common;

import java.util.List;

public interface TestInterface {

	static List<String> names() {
		return List.of("Sarah", "Susan");
	}
}
