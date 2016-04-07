package fi.vrk.xroad.catalog.collector.actors;

import akka.actor.ActorRef;
import akka.actor.OneForOneStrategy;
import akka.actor.Terminated;
import akka.actor.UntypedActor;
import akka.routing.SmallestMailboxPool;
import fi.vrk.xroad.catalog.collector.extension.SpringExtension;
import fi.vrk.xroad.catalog.persistence.CatalogService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;
import scala.Option;
import scala.concurrent.duration.Duration;

import static akka.actor.SupervisorStrategy.restart;

/**
 * Supervisor to get list of all clients in system and initiate a ClientActor for each
 * <p/>
 * A router is configured at startup time, managing a pool of task actors.
 */
@Component
@Scope("prototype")
@Slf4j
public class Supervisor extends XRoadCatalogActor {

    public static final String START_COLLECTING = "StartCollecting";

    public static final String LIST_CLIENTS_ACTOR_ROUTER = "list-clients-actor-router";
    public static final String LIST_METHODS_ACTOR_ROUTER = "list-methods-actor-router";
    public static final String FETCH_WSDL_ACTOR_ROUTER = "fetch-wsdl-actor-router";

    @Autowired
    private SpringExtension springExtension;

    @Autowired
    protected CatalogService catalogService;

    private ActorRef listClientsPoolRouter;
    private ActorRef listMethodsPoolRouter;
    private ActorRef fetchWsdlPoolRouter;

    @Value("${xroad-catalog.list-methods-pool-size}")
    private int listMethodsPoolSize;

    @Value("${xroad-catalog.fetch-wsdl-pool-size}")
    private int fetchWsdlPoolSize;

    @Override
    public void preStart() throws Exception {

        log.info("Starting up");

        // for this pool, supervisor strategy restarts each clientActor if it fails.
        // currently this is not needed and could "resume" just as well
        listClientsPoolRouter = getContext().actorOf(new SmallestMailboxPool(1)
                        .withSupervisorStrategy(new OneForOneStrategy(-1,
                                Duration.Inf(),
                                (Throwable t) -> restart()))
                .props(springExtension.props("listClientsActor")),
                LIST_CLIENTS_ACTOR_ROUTER);

        listMethodsPoolRouter = getContext().actorOf(new SmallestMailboxPool(listMethodsPoolSize)
                        .props(springExtension.props("listMethodsActor")),
                LIST_METHODS_ACTOR_ROUTER);

        fetchWsdlPoolRouter = getContext().actorOf(new SmallestMailboxPool(fetchWsdlPoolSize)
                        .props(springExtension.props("fetchWsdlActor")),
                FETCH_WSDL_ACTOR_ROUTER);

        super.preStart();
    }

    @Override
    protected boolean handleMessage(Object message) throws Exception {

        if (START_COLLECTING.equals(message)) {
            listClientsPoolRouter.tell(ListClientsActor.START_COLLECTING, getSelf());
            return true;
        } else {
            return false;
        }
    }

}