package org.graalvm.internal.tck.model.contributing;

import java.util.List;

public record PredefinedClassesConfigModel(
        String type,
        List<Object> classes
) {
    public boolean isEmpty() {
        return this.type().equalsIgnoreCase("agent-extracted") && this.classes().isEmpty();
    }
}
