// Copyright © 2012-2018 Vaughn Vernon. All rights reserved.
//
// This Source Code Form is subject to the terms of the
// Mozilla Public License, v. 2.0. If a copy of the MPL
// was not distributed with this file, You can obtain
// one at https://mozilla.org/MPL/2.0/.

package io.vlingo.actors;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import io.vlingo.actors.plugin.mailbox.testkit.TestMailbox;
import io.vlingo.actors.testkit.TestActor;
import io.vlingo.common.Completes;
import io.vlingo.common.Scheduler;

public class Stage implements Stoppable {
  private final Map<Class<?>, Supervisor> commonSupervisors;
  private final Directory directory;
  private DirectoryScanner directoryScanner;
  private final String name;
  private final Scheduler scheduler;
  private AtomicBoolean stopped;
  private final World world;

  /**
   * Answers the {@code T} protocol type as the means to message the backing {@code Actor}.
   * @param actor the {@code Actor} that implements the {@code Class<T>} protocol
   * @param protocol the {@code Class<T>} protocol
   * @param <T> the protocol type
   * @return T
   */
  public <T> T actorAs(final Actor actor, final Class<T> protocol) {
    return actorProxyFor(protocol, actor, actor.lifeCycle.environment.mailbox);
  }

  /**
   * Answers the {@code T} protocol of the newly created {@code Actor} that implements the {@code protocol}.
   * @param protocol the {@code Class<T>} protocol
   * @param type the {@code Class<? extends Actor>} of the {@code Actor} to create
   * @param parameters the {@code Object[]} of constructor parameters
   * @param <T> the protocol type
   * @return T
   */
  public <T> T actorFor(final Class<T> protocol, final Class<? extends Actor> type, final Object...parameters) {
    return actorFor(Definition.has(type, Arrays.asList(parameters)), protocol);
  }

  /**
   * Answers the {@code T} protocol of the newly created {@code Actor} that implements the {@code protocol}.
   * @param definition the {@code Definition} used to initialize the newly created {@code Actor}
   * @param protocol the {@code Class<T>} protocol
   * @param <T> the protocol type
   * @return T
   */
  public <T> T actorFor(final Definition definition, final Class<T> protocol) {
    return actorFor(
            definition,
            protocol,
            definition.parentOr(world.defaultParent()),
            definition.supervisor(),
            definition.loggerOr(world.defaultLogger()));
  }

  /**
   * Answers the {@code T} protocol of the newly created {@code Actor} that implements the {@code protocol} and
   * that will be assigned the specific {@code address}.
   * @param definition the {@code Definition} used to initialize the newly created {@code Actor}
   * @param protocol the {@code Class<T>} protocol
   * @param address the {@code Address} to assign to the newly created {@code Actor}
   * @param <T> the protocol type
   * @return T
   */
  public <T> T actorFor(final Definition definition, final Class<T> protocol, final Address address) {
    final Address actorAddress = this.allocateAddress(definition, address);
    final Mailbox actorMailbox = this.allocateMailbox(definition, actorAddress, null);
    final ActorProtocolActor<T> actor =
            actorProtocolFor(
              definition,
              protocol,
              definition.parentOr(world.defaultParent()),
              actorAddress,
              actorMailbox,
              definition.supervisor(),
              definition.loggerOr(world.defaultLogger()));

    return actor.protocolActor();
  }

  /**
   * Answers the {@code T} protocol of the newly created {@code Actor} that implements the {@code protocol} and
   * that will be assigned the specific {@code logger}.
   * @param definition the {@code Definition} used to initialize the newly created {@code Actor}
   * @param protocol the {@code Class<T>} protocol
   * @param logger the {@code Logger} to assign to the newly created {@code Actor}
   * @param <T> the protocol type
   * @return T
   */
  public <T> T actorFor(final Definition definition, final Class<T> protocol, final Logger logger) {
    return actorFor(
            definition,
            protocol,
            definition.parentOr(world.defaultParent()),
            definition.supervisor(),
            logger);
  }

  /**
   * Answers the {@code T} protocol of the newly created {@code Actor} that implements the {@code protocol} and
   * that will be assigned the specific {@code address} and {@code logger}.
   * @param definition the {@code Definition} used to initialize the newly created {@code Actor}
   * @param protocol the {@code Class<T>} protocol
   * @param address the {@code Address} to assign to the newly created {@code Actor}
   * @param logger the {@code Logger} to assign to the newly created {@code Actor}
   * @param <T> the protocol type
   * @return T
   */
  public <T> T actorFor(final Definition definition, final Class<T> protocol, final Address address, final Logger logger) {
    final Address actorAddress = this.allocateAddress(definition, address);
    final Mailbox actorMailbox = this.allocateMailbox(definition, actorAddress, null);
    final ActorProtocolActor<T> actor =
            actorProtocolFor(
              definition,
              protocol,
              definition.parentOr(world.defaultParent()),
              actorAddress,
              actorMailbox,
              definition.supervisor(),
              logger);

    return actor.protocolActor();
  }

  /**
   * Answers a {@code Protocols} that provides one or more supported {@code protocols} for the
   * newly created {@code Actor} according to {@code definition}.
   * @param definition the {@code Definition} providing parameters to the {@code Actor}
   * @param protocols the {@code Class<T>}[] array of protocols that the {@code Actor} supports
   * @return Protocols
   */
  public Protocols actorFor(final Definition definition, final Class<?>[] protocols) {
    final ActorProtocolActor<Object>[] all =
            actorProtocolFor(
                    definition,
                    protocols,
                    definition.parentOr(world.defaultParent()),
                    definition.supervisor(),
                    definition.loggerOr(world.defaultLogger()));

    return new Protocols(ActorProtocolActor.toActors(all));
  }

  /**
   * Answers the {@code Completes<T>} that will eventually complete with the {@code T} protocol
   * of the backing {@code Actor} of the given {@code address}, or {@code null} if not found.
   * @param address the {@code Address} of the {@code Actor} to find
   * @param protocol the {@code Class<T>} protocol supported by the backing {@code Actor}
   * @param <T> the protocol type
   * @return {@code Completes<T>}
   */
  public <T> Completes<T> actorOf(final Address address, final Class<T> protocol) {
    return directoryScanner.actorOf(address, protocol);
  }

  /**
   * Answers the {@code TestActor<T>}, {@code T} being the protocol, of the new created {@code Actor} that implements the {@code protocol}.
   * The {@code TestActor<T>} is specifically used for test scenarios and provides runtime access to the internal
   * {@code Actor} instance. Test-based {@code Actor} instances are backed by the synchronous {@code TestMailbox}.
   * @param definition the {@code Definition} used to initialize the newly created {@code Actor}
   * @param protocol the {@code Class<T>} protocol
   * @param <T> the protocol type
   * @return T
   */
  public final <T> TestActor<T> testActorFor(final Definition definition, final Class<T> protocol) {
    final Definition redefinition =
            Definition.has(
                    definition.type(),
                    definition.parameters(),
                    TestMailbox.Name,
                    definition.actorName());
    
    try {
      return actorProtocolFor(
              redefinition,
              protocol,
              definition.parentOr(world.defaultParent()),
              null,
              null,
              definition.supervisor(),
              definition.loggerOr(world.defaultLogger())
              ).toTestActor();
      
    } catch (Exception e) {
      world.defaultLogger().log("vlingo/actors: FAILED: " + e.getMessage(), e);
      e.printStackTrace();
      return null;
    }
  }

  /**
   * Answers a {@code Protocols} that provides one or more supported {@code protocols} for the
   * newly created {@code Actor} according to {@code definition}, that can be used for testing.
   * Test-based {@code Actor} instances are backed by the synchronous {@code TestMailbox}.
   * @param definition the {@code Definition} providing parameters to the {@code Actor}
   * @param protocols the {@code Class<T>}[] array of protocols that the {@code Actor} supports
   * @return Protocols
   */
  public final Protocols testActorFor(final Definition definition, final Class<?>[] protocols) {
    final Definition redefinition =
            Definition.has(
                    definition.type(),
                    definition.parameters(),
                    TestMailbox.Name,
                    definition.actorName());
    
    final ActorProtocolActor<Object>[] all =
            actorProtocolFor(
                    redefinition,
                    protocols,
                    definition.parentOr(world.defaultParent()),
                    null,
                    null,
                    definition.supervisor(),
                    definition.loggerOr(world.defaultLogger()));
    
    return new Protocols(ActorProtocolActor.toTestActors(all));
  }

  /**
   * Answers the {@code int} count of the number of {@code Actor} instances contained in this {@code Stage}.
   * @return int
   */
  public int count() {
    return directory.count();
  }

  /**
   * A debugging tool used to print information about the {@code Actor} instances contained in this {@code Stage}.
   */
  public void dump() {
    final Logger logger = this.world.defaultLogger();
    if (logger.isEnabled()) {
      logger.log("STAGE: " + name);
      directory.dump(logger);
    }
  }

  /**
   * Answers the {@code name} of this {@code Stage}.
   * @return String
   */
  public String name() {
    return name;
  }

  /**
   * Registers with this {@code Stage} the {@code common} supervisor for the given {@code protocol}.
   * @param protocol the {@code Class<T>} protocol to be supervised by {@code common}
   * @param common the {@code Supervisor} to serve as the supervisor of all {@code Actor}s implementing protocol
   */
  public void registerCommonSupervisor(final Class<?> protocol, final Supervisor common) {
    commonSupervisors.put(protocol, common);
  }

  /**
   * Answers the {@code Scheduler} of this {@code Stage}.
   * @return Scheduler
   */
  public Scheduler scheduler() {
    return scheduler;
  }

  /**
   * Answers whether or not this {@code Stage} has been stopped or is in the process of stopping.
   * @return boolean
   */
  @Override
  public boolean isStopped() {
    return stopped.get();
  }

  /**
   * Initiates the process of stopping this {@code Stage}.
   */
  @Override
  public void stop() {
    if (!stopped.compareAndSet(false, true)) return;

    sweep();

    int retries = 0;
    while (count() > 1 && ++retries < 10) {
      try { Thread.sleep(10L); } catch (Exception e) {}
    }
    
    scheduler.close();
  }

  /**
   * Answers the {@code World} instance of this {@code Stage}.
   * @return World
   */
  public World world() {
    return world;
  }

  /**
   * Initializes the new {@code Stage} of the world and with name. (INTERNAL ONLY)
   * @param world the {@code World} parent of this {@code Stage}
   * @param name the {@code String} name of this {@code Stage}
   */
  Stage(final World world, final String name) {
    this.world = world;
    this.name = name;
    this.directory = new Directory(world.addressFactory().none());
    this.commonSupervisors = new HashMap<>();
    this.scheduler = new Scheduler();
    this.stopped = new AtomicBoolean(false);
  }

  /**
   * Answers the T protocol for the newly created Actor instance. (INTERNAL ONLY)
   * @param definition the Definition of the Actor
   * @param protocol the {@code Class<T>} protocol of the Actor
   * @param parent the Actor parent of this Actor
   * @param maybeSupervisor the possible Supervisor of this Actor
   * @param logger the Logger of this Actor
   * @param <T> the protocol type
   * @return T
   */
  <T> T actorFor(final Definition definition, final Class<T> protocol, final Actor parent, final Supervisor maybeSupervisor, final Logger logger) {
    ActorProtocolActor<T> actor = actorProtocolFor(definition, protocol, parent, null, null, maybeSupervisor, logger);
    return actor.protocolActor();
  }


  /**
   * Answers the ActorProtocolActor[] for the newly created Actor instance. (INTERNAL ONLY)
   * @param definition the Definition of the Actor
   * @param protocols the {@code Class<T>}[] protocols of the Actor
   * @param parent the Actor parent of this Actor
   * @param maybeSupervisor the possible Supervisor of this Actor
   * @param logger the Logger of this Actor
   * @return ActorProtocolActor[]
   */
  ActorProtocolActor<Object>[] actorProtocolFor(final Definition definition, final Class<?>[] protocols, final Actor parent, final Supervisor maybeSupervisor, final Logger logger) {
    return actorProtocolFor(definition, protocols, parent, null, null, maybeSupervisor, logger);
  }

  /**
   * Answers the ActorProtocolActor for the newly created Actor instance. (INTERNAL ONLY)
   * @param definition the Definition of the Actor
   * @param protocol the {@code Class<T>} protocol of the Actor
   * @param parent the Actor parent of this Actor
   * @param maybeAddress the possible Address of this Actor
   * @param maybeMailbox the possible Mailbox of this Actor
   * @param maybeSupervisor the possible Supervisor of this Actor
   * @param logger the Logger of this Actor
   * @param <T> the protocol type
   * @return ActorProtocolActor
   */
  <T> ActorProtocolActor<T> actorProtocolFor(
          final Definition definition,
          final Class<T> protocol,
          final Actor parent,
          final Address maybeAddress,
          final Mailbox maybeMailbox,
          final Supervisor maybeSupervisor,
          final Logger logger) {

    try {
      final Actor actor = createRawActor(definition, parent, maybeAddress, maybeMailbox, maybeSupervisor, logger);
      final T protocolActor = actorProxyFor(protocol, actor, actor.lifeCycle.environment.mailbox);
      return new ActorProtocolActor<T>(actor, protocolActor);
    } catch (Exception e) {
      e.printStackTrace();
      world.defaultLogger().log("vlingo/actors: FAILED: " + e.getMessage(), e);
      return null;
    }
  }

  /**
   * Answers the ActorProtocolActor[] for the newly created Actor instance. (INTERNAL ONLY)
   * @param definition the Definition of the Actor
   * @param protocols the {@code Class<T>}[] protocols of the Actor
   * @param parent the Actor parent of this Actor
   * @param maybeAddress the possible Address of this Actor
   * @param maybeMailbox the possible Mailbox of this Actor
   * @param maybeSupervisor the possible Supervisor of this Actor
   * @param logger the Logger of this Actor
   * @return ActorProtocolActor[]
   */
  ActorProtocolActor<Object>[] actorProtocolFor(
          final Definition definition,
          final Class<?>[] protocols,
          final Actor parent,
          final Address maybeAddress,
          final Mailbox maybeMailbox,
          final Supervisor maybeSupervisor,
          final Logger logger) {

    try {
      final Actor actor = createRawActor(definition, parent, maybeAddress, maybeMailbox, maybeSupervisor, logger);
      final Object[] protocolActors = actorProxyFor(protocols, actor, actor.lifeCycle.environment.mailbox);
      return ActorProtocolActor.allOf(actor, protocolActors);
    } catch (Exception e) {
      world.defaultLogger().log("vlingo/actors: FAILED: " + e.getMessage(), e);
      return null;
    }
  }

  /**
   * Answers the T protocol proxy for this newly created Actor. (INTERNAL ONLY)
   * @param protocol the {@code Class<T>} protocol of the Actor
   * @param actor the Actor instance that backs the proxy protocol
   * @param mailbox the Mailbox instance of this Actor
   * @param <T> the protocol type
   * @return T
   */
  final <T> T actorProxyFor(final Class<T> protocol, final Actor actor, final Mailbox mailbox) {
    return ActorProxy.createFor(protocol, actor, mailbox);
  }

  /**
   * Answers the Object[] protocol proxies for this newly created Actor. (INTERNAL ONLY)
   * @param protocols the {@code Class<T>}[] protocols of the Actor
   * @param actor the Actor instance that backs the proxy protocol
   * @param mailbox the Mailbox instance of this Actor
   * @return Object[]
   */
  final Object[] actorProxyFor(final Class<?>[] protocol, final Actor actor, final Mailbox mailbox) {
    final Object[] proxies = new Object[protocol.length];
    
    for (int idx = 0; idx < protocol.length; ++idx) {
      proxies[idx] = actorProxyFor(protocol[idx], actor, mailbox);
    }
    
    return proxies;
  }

  /**
   * Answers the common Supervisor for the given protocol or the defaultSupervisor if there is
   * no registered common Supervisor. (INTERNAL ONLY)
   * @param protocol the {@code Class<T>} protocol to supervise
   * @param defaultSupervisor the Supervisor default to be used if there is no registered common Supervisor
   * @return Supervisor
   */
  Supervisor commonSupervisorOr(final Class<?> protocol, final Supervisor defaultSupervisor) {
    final Supervisor common = commonSupervisors.get(protocol);
    
    if (common != null) {
      return common;
    }

    return defaultSupervisor;
  }

  /**
   * Answers my Directory instance. (INTERNAL ONLY)
   * @return Directory
   */
  Directory directory() {
    return directory; // FOR TESTING ONLY
  }

  /**
   * Handles a failure by suspending the Actor and dispatching to the Supervisor. (INTERNAL ONLY)
   * @param supervised the Supervised instance, which is an Actor
   */
  void handleFailureOf(final Supervised supervised) {
    supervised.suspend();
    supervised.supervisor().inform(supervised.throwable(), supervised);
  }

  /**
   * Start the directory scan process in search for a given Actor instance. (INTERNAL ONLY)
   */
  void startDirectoryScanner() {
    this.directoryScanner = actorFor(Definition.has(DirectoryScannerActor.class, Definition.parameters(directory)), DirectoryScanner.class);
  }

  /**
   * Stop the given Actor and all its children. The Actor instance is first removed from
   * the Directory of this Stage. (INTERNAL ONLY)
   * @param actor the Actor to stop
   */
  void stop(final Actor actor) {
    final Actor removedActor = directory.remove(actor.address());
    
    if (actor == removedActor) {
      removedActor.lifeCycle.stop(actor);
    }
  }

  /**
   * Answers an Address for an Actor. If maybeAddress is allocated answer it; otherwise
   * answer a newly allocated Address. (INTERNAL ONLY)
   * @param definition the Definition of the newly created Actor
   * @param maybeAddress the possible Address
   * @return Address
   */
  private Address allocateAddress(final Definition definition, final Address maybeAddress) {
    final Address address = maybeAddress != null ?
            maybeAddress : world.addressFactory().uniqueWith(definition.actorName());
    return address;
  }

  /**
   * Answers a Mailbox for an Actor. If maybeMailbox is allocated answer it; otherwise
   * answer a newly allocated Mailbox. (INTERNAL ONLY)
   * @param definition the Definition of the newly created Actor
   * @param address the Address allocated to the Actor
   * @param maybeMailbox the possible Mailbox
   * @return Mailbox
   */
  private Mailbox allocateMailbox(final Definition definition, final Address address, final Mailbox maybeMailbox) {
    final Mailbox mailbox = maybeMailbox != null ?
            maybeMailbox : ActorFactory.actorMailbox(this, address, definition);
    return mailbox;
  }

  /**
   * Answers a newly created Actor instance from the internal ActorFactory. (INTERNAL ONLY)
   * @param definition the Definition of the Actor to create
   * @param parent the Actor parent of the new Actor
   * @param maybeAddress the possible Address of the Actor to create
   * @param maybeMailbox the possible Mailbox of the Actor to create
   * @param maybeSupervisor the possible Supervisor of the Actor to create
   * @param logger the Logger of the Actor to create
   * @param <T> the protocol type
   * @return Actor
   * @throws Exception thrown if there is a problem with Actor creation
   */
  private <T> Actor createRawActor(
          final Definition definition,
          final Actor parent,
          final Address maybeAddress,
          final Mailbox maybeMailbox,
          final Supervisor maybeSupervisor,
          final Logger logger)
  throws Exception {

    if (isStopped()) {
      throw new IllegalStateException("Actor stage has been stopped.");
    }

    final Address address = maybeAddress != null ?
            maybeAddress : world.addressFactory().uniqueWith(definition.actorName());

    if (directory.isRegistered(address)) {
      throw new IllegalStateException("Address already exists: " + address);
    }

    final Mailbox mailbox = maybeMailbox != null ?
            maybeMailbox : ActorFactory.actorMailbox(this, address, definition);

    final Actor actor;

    try {
      actor = ActorFactory.actorFor(this, parent, definition, address, mailbox, maybeSupervisor, logger);
    } catch (Exception e) {
      logger.log("Actor instantiation failed because: " + e.getMessage(), e);
      throw new IllegalArgumentException("Actor instantiation failed because: " + e.getMessage(), e);
    }

    directory.register(actor.address(), actor);

    actor.lifeCycle.beforeStart(actor);

    return actor;
  }

  /**
   * Stops all Actor instances from the PrivateRootActor down to the last child. (INTERNAL ONLY)
   */
  private void sweep() {
    if (world.privateRoot() != null) {
      world.privateRoot().stop();
    }
  }
  
  /**
   * Internal type used to manage Actor proxy creation. (INTERNAL ONLY)
   * @param <T> the protocol type
   */
  static class ActorProtocolActor<T> {
    private final Actor actor;
    private final T protocolActor;

    @SuppressWarnings({ "rawtypes", "unchecked" })
    static ActorProtocolActor<Object>[] allOf(final Actor actor, Object[] protocolActors) {
      final ActorProtocolActor<Object>[] all = new ActorProtocolActor[protocolActors.length];
      for (int idx = 0; idx < protocolActors.length; ++idx) {
        all[idx] = new ActorProtocolActor(actor, protocolActors[idx]);
      }
      return all;
    }

    static Object[] toActors(final ActorProtocolActor<Object>[] all) {
      final Object[] actors = new Object[all.length];
      for (int idx = 0; idx < all.length; ++idx) {
        actors[idx] = all[idx].protocolActor();
      }
      return actors;
    }

    static TestActor<?>[] toTestActors(final ActorProtocolActor<Object>[] all) {
      final TestActor<?>[] testActors = new TestActor[all.length];
      for (int idx = 0; idx < all.length; ++idx) {
        testActors[idx] = all[idx].toTestActor();
      }
      return testActors;
    }

    ActorProtocolActor(final Actor actor, final T protocol) {
      this.actor = actor;
      this.protocolActor = protocol;
    }

    T protocolActor() {
      return protocolActor;
    }
    
    TestActor<T> toTestActor() {
      return new TestActor<T>(actor, protocolActor, actor.address());
    }
  }
}
