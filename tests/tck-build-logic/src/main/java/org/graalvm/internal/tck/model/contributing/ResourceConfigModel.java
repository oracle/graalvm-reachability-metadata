package org.graalvm.internal.tck.model.contributing;
import java.util.List;

public record ResourceConfigModel(
        Object resources,
        List<Object> bundles
) {
}
