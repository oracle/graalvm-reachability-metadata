package org.graalvm.internal.tck;

import org.gradle.api.DefaultTask;
import org.gradle.api.tasks.TaskAction;

public abstract class ContributionTask extends DefaultTask {

    @TaskAction
    void run() {
        System.out.println("Hello from contribution task!");
    }

}
