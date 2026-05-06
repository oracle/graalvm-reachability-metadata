/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_pekko.pekko_actor_2_13;

import java.util.concurrent.CompletionStage;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeoutException;

import org.apache.pekko.actor.ActorPath;
import org.apache.pekko.actor.ActorRef;
import org.apache.pekko.actor.ActorRefProvider;
import org.apache.pekko.actor.ActorSystem;
import org.apache.pekko.actor.ActorSystemImpl;
import org.apache.pekko.actor.DynamicAccess;
import org.apache.pekko.actor.Extension;
import org.apache.pekko.actor.ExtensionId;
import org.apache.pekko.actor.ExtendedActorSystem;
import org.apache.pekko.actor.InternalActorRef;
import org.apache.pekko.actor.Props;
import org.apache.pekko.actor.ReflectiveDynamicAccess;
import org.apache.pekko.actor.Scheduler;
import org.apache.pekko.actor.Terminated;
import org.apache.pekko.dispatch.Dispatchers;
import org.apache.pekko.dispatch.Mailboxes;
import org.apache.pekko.event.EventStream;
import org.apache.pekko.event.LoggingAdapter;
import org.apache.pekko.event.LoggingFilter;

import scala.Function0;
import scala.collection.Iterable;
import scala.concurrent.ExecutionContextExecutor;
import scala.concurrent.Future;

public final class ManifestInfoStubActorSystem extends ExtendedActorSystem {
    private final DynamicAccess dynamicAccess;

    public ManifestInfoStubActorSystem(ClassLoader classLoader) {
        this.dynamicAccess = new ReflectiveDynamicAccess(classLoader);
    }

    @Override
    public String name() {
        return "manifest-info-stub";
    }

    @Override
    public ActorSystem classicSystem() {
        return this;
    }

    @Override
    public ActorSystem.Settings settings() {
        throw unsupported();
    }

    @Override
    public void logConfiguration() {
        throw unsupported();
    }

    @Override
    public ActorPath $div(String name) {
        throw unsupported();
    }

    @Override
    public ActorPath $div(Iterable<String> names) {
        throw unsupported();
    }

    @Override
    public EventStream eventStream() {
        throw unsupported();
    }

    @Override
    public LoggingAdapter log() {
        throw unsupported();
    }

    @Override
    public ActorRef deadLetters() {
        throw unsupported();
    }

    @Override
    public Scheduler scheduler() {
        throw unsupported();
    }

    @Override
    public Dispatchers dispatchers() {
        throw unsupported();
    }

    @Override
    public ExecutionContextExecutor dispatcher() {
        throw unsupported();
    }

    @Override
    public Mailboxes mailboxes() {
        throw unsupported();
    }

    @Override
    public <T> void registerOnTermination(Function0<T> code) {
        throw unsupported();
    }

    @Override
    public void registerOnTermination(Runnable code) {
        throw unsupported();
    }

    @Override
    public Future<Terminated> terminate() {
        throw unsupported();
    }

    @Override
    public void close() throws TimeoutException {
    }

    @Override
    public Future<Terminated> whenTerminated() {
        throw unsupported();
    }

    @Override
    public CompletionStage<Terminated> getWhenTerminated() {
        throw unsupported();
    }

    @Override
    public <T extends Extension> T registerExtension(ExtensionId<T> ext) {
        throw unsupported();
    }

    @Override
    public <T extends Extension> T extension(ExtensionId<T> ext) {
        throw unsupported();
    }

    @Override
    public boolean hasExtension(ExtensionId<? extends Extension> ext) {
        return false;
    }

    @Override
    public ActorSystemImpl systemImpl() {
        throw unsupported();
    }

    @Override
    public ActorRefProvider provider() {
        throw unsupported();
    }

    @Override
    public InternalActorRef guardian() {
        throw unsupported();
    }

    @Override
    public InternalActorRef lookupRoot() {
        throw unsupported();
    }

    @Override
    public ActorRef actorOf(Props props) {
        throw unsupported();
    }

    @Override
    public ActorRef actorOf(Props props, String name) {
        throw unsupported();
    }

    @Override
    public void stop(ActorRef actorRef) {
        throw unsupported();
    }

    @Override
    public InternalActorRef systemGuardian() {
        throw unsupported();
    }

    @Override
    public ActorRef systemActorOf(Props props, String name) {
        throw unsupported();
    }

    @Override
    public ThreadFactory threadFactory() {
        throw unsupported();
    }

    @Override
    public DynamicAccess dynamicAccess() {
        return dynamicAccess;
    }

    @Override
    public LoggingFilter logFilter() {
        throw unsupported();
    }

    @Override
    public String printTree() {
        throw unsupported();
    }

    @Override
    public long uid() {
        return 0L;
    }

    @Override
    public void finalTerminate() {
    }

    @Override
    public boolean isTerminating() {
        return false;
    }

    private UnsupportedOperationException unsupported() {
        return new UnsupportedOperationException("Only dynamicAccess is supported by the manifest-info stub system");
    }

}
