package org.graalvm.internal.tck.model.contributing;
import java.util.List;

public record ResourceConfigModel(
        Object resources,
        List<Object> bundles
) {
    public boolean isEmpty() {
        return this.bundles().isEmpty() && this.resources().toString().equalsIgnoreCase("{}");
    }
}
