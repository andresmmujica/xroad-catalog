package fi.vrk.xroad.catalog.collector.actors;

import akka.actor.ActorRef;
import akka.actor.Terminated;
import akka.actor.UntypedActor;
import fi.vrk.xroad.catalog.collector.XRoadCatalogCollector;
import fi.vrk.xroad.catalog.collector.extension.SpringExtension;
import fi.vrk.xroad.catalog.collector.util.*;
import fi.vrk.xroad.catalog.collector.wsimport.ClientType;
import fi.vrk.xroad.catalog.collector.wsimport.XRoadClientIdentifierType;
import fi.vrk.xroad.catalog.collector.wsimport.XRoadObjectType;
import fi.vrk.xroad.catalog.collector.wsimport.XRoadServiceIdentifierType;
import fi.vrk.xroad.catalog.persistence.CatalogService;
import fi.vrk.xroad.catalog.persistence.entity.Member;
import fi.vrk.xroad.catalog.persistence.entity.Service;
import fi.vrk.xroad.catalog.persistence.entity.Subsystem;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Actor which fetches all clients, and delegates listing
 * their methods to ListMethodsActors
 */
@Component
@Scope("prototype")
@Slf4j
public class ListMethodsActor extends XRoadCatalogActor {


    // to test fault handling
    private static boolean FORCE_FAILURES = false;

    @Value("${xroad-catalog.xroad-instance}")
    private String xroadInstance;

    @Value("${xroad-catalog.member-code}")
    private String memberCode;

    @Value("${xroad-catalog.member-class}")
    private String memberClass;

    @Value("${xroad-catalog.subsystem-code}")
    private String subsystemCode;

    @Value("${xroad-catalog.webservices-endpoint}")
    private String webservicesEndpoint;

    @Autowired
    private SpringExtension springExtension;

    @Autowired
    protected CatalogService catalogService;

    // supervisor-created pool of list methods actors
    private ActorRef fetchWsdlPoolRef;



    @Override
    public void preStart() throws Exception {
        log.info("preStart {}", this.hashCode());
        fetchWsdlPoolRef = new RelativeActorRefUtil(getContext())
                .resolvePoolRef(Supervisor.FETCH_WSDL_ACTOR_ROUTER);
        super.preStart();
    }

    @Override
    public void postStop() throws Exception {
        log.info("postStop {}", COUNTER);
        super.postStop();
    }

    private void maybeFail() {
        if (FORCE_FAILURES) {
            if (COUNTER.get() % 3 == 0) {
                log.info("sending test failure {}", hashCode());
                throw new RuntimeException("test failure at " + hashCode());
            }
        }
    }

    @Override
    public void onReceive(Object message) throws Exception {

        ClientType clientType = (ClientType) handleXRoadCatalogMessage(message);
        if (clientType == null) {
            log.info("onReceive null");
            return;
        }

        Subsystem subsystem = new Subsystem(new Member(clientType.getId().getXRoadInstance(), clientType.getId()
                .getMemberClass(),
                clientType.getId().getMemberCode(), clientType.getName()), clientType.getId()
                .getSubsystemCode());



        log.info("{} Handling subsystem {} ", COUNTER, subsystem);
        log.info("Fetching methods for the client with listMethods -service...");

        XRoadClientIdentifierType xroadId = new XRoadClientIdentifierType();
        xroadId.setXRoadInstance(xroadInstance);
        xroadId.setMemberClass(memberClass);
        xroadId.setMemberCode(memberCode);
        xroadId.setSubsystemCode(subsystemCode);
        xroadId.setObjectType(XRoadObjectType.SUBSYSTEM);

        // fetch the methods
        try {
            log.info("calling web service at {}", webservicesEndpoint);
            List<XRoadServiceIdentifierType> result = XRoadClient.getMethods(webservicesEndpoint, xroadId,
                    clientType);
            log.info("Received all methods for client {} ", ClientTypeUtil.toString(clientType));
//                log.info("{} ListMethodsResponse {} ", COUNTER, result.stream().map(s -> ClientTypeUtil.toString(s))
//                        .collect
//                        (joining(", ")));

            maybeFail();

            // Save services for subsystems
            List<Service> services = new ArrayList<>();
            for (XRoadServiceIdentifierType service : result) {
                services.add(new Service(subsystem, service.getServiceCode(), service.getServiceVersion()));
            }
            catalogService.saveServices(subsystem.createKey(), services);

            // get wsdls
            for (XRoadServiceIdentifierType service : result) {
                log.info("{} Sending service {} to new MethodActor ", COUNTER, service.getServiceCode());
                XRoadCatalogID id = createXRoadCatalogIDForChild();
                try {
                    fetchWsdlPoolRef.tell(new XRoadCatalogMessage(id,
                            service),
                            getSelf());
                } catch (Throwable t) {
                    log.error("Failed to send message {}", t);
                    currentTransaction.removeChild(id.getChildID());
                    throw t;
                }

            }
            log.info("{} At the end of onReceive TransactionMap {}", COUNTER, transactionMap);
        } catch (Exception e) {
            log.error("Failed to get methods for subsystem {} \n {}", subsystem, e.toString());
            throw e;
        }
    }

}
