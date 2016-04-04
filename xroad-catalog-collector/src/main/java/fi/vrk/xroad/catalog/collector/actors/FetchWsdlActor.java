package fi.vrk.xroad.catalog.collector.actors;

import akka.actor.Terminated;
import akka.actor.UntypedActor;
import com.google.common.base.Strings;
import fi.vrk.xroad.catalog.collector.XRoadCatalogCollector;
import fi.vrk.xroad.catalog.collector.util.XRoadCatalogID;
import fi.vrk.xroad.catalog.collector.util.XRoadCatalogMessage;
import fi.vrk.xroad.catalog.collector.wsimport.XRoadServiceIdentifierType;
import fi.vrk.xroad.catalog.persistence.CatalogService;
import fi.vrk.xroad.catalog.persistence.entity.ServiceId;
import fi.vrk.xroad.catalog.persistence.entity.SubsystemId;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestOperations;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Actor which fetches one wsdl
 */
@Component
@Scope("prototype")
@Slf4j
public class FetchWsdlActor extends UntypedActor {

    private static AtomicInteger COUNTER = new AtomicInteger(0);
    private static final String WSDL_CONTEXT_PATH = "/wsdl";

    @Value("${xroad-catalog.fetch-wsdl-host}")
    private String host;

    public String getHost() {
        return host;
    }

    @Autowired
    @Qualifier("wsdlRestOperations")
    private RestOperations restOperations;

    @Autowired
    protected CatalogService catalogService;

    @Override
    public void onReceive(Object message) throws Exception {
        if (message instanceof XRoadCatalogMessage) {
            XRoadCatalogMessage m = (XRoadCatalogMessage)message;
            try {
                log.info("fetching wsdl [{}] {}", COUNTER.addAndGet(1), message);
                XRoadServiceIdentifierType service = (XRoadServiceIdentifierType) m.getPayload();
                // get wsdl
                String url = buildUri(service);
                String wsdl = restOperations.getForObject(url, String.class);
                log.debug("url: {} received wsdl: {} for ", url, wsdl);
                catalogService.saveWsdl(createSubsystemId(service),
                        createServiceId(service),
                        wsdl);
                log.info("{} saved wsdl successfully", COUNTER);
            } catch (Exception e) {
                log.error("Fetching wsld failed. {}", e);
                throw e;
            }
            finally {
                getSender().tell(m.getId(), getSelf());
            }
        } else if (message instanceof Terminated) {
            throw new RuntimeException("Terminated: " + message);
        } else {
            log.error("Unable to handle message {}", message);
        }
    }

    @Override
    public void postStop() throws Exception {
        log.info("postStop {}", COUNTER);

        super.postStop();
    }

    private ServiceId createServiceId(XRoadServiceIdentifierType service) {
        ServiceId serviceId = new ServiceId(service.getServiceCode(),
                service.getServiceVersion());
        return serviceId;
    }

    private SubsystemId createSubsystemId(XRoadServiceIdentifierType service) {
        SubsystemId subsystemId = new SubsystemId(service.getXRoadInstance(),
                service.getMemberClass(),
                service.getMemberCode(),
                service.getSubsystemCode());
        return subsystemId;
    }

    private String buildUri(XRoadServiceIdentifierType service) {
        assert service.getXRoadInstance() != null;
        assert service.getMemberClass() != null;
        assert service.getMemberCode() != null;
        assert service.getServiceCode() != null;
        assert service.getServiceVersion() != null;

        UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(getHost())
                .path(WSDL_CONTEXT_PATH)
                .queryParam("xRoadInstance", service.getXRoadInstance())
                .queryParam("memberClass", service.getMemberClass())
                .queryParam("memberCode", service.getMemberCode())
                .queryParam("serviceCode", service.getServiceCode())
                .queryParam("version", service.getServiceVersion());
        if (!Strings.isNullOrEmpty(service.getSubsystemCode())) {
            builder = builder.queryParam("subsystemCode", service.getSubsystemCode());
        }
        return builder.toUriString();
    }
}
