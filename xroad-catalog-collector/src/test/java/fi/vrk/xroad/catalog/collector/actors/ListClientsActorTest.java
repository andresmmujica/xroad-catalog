package fi.vrk.xroad.catalog.collector.actors;

import akka.actor.ActorSystem;
import akka.actor.InternalActorRef;
import akka.actor.Props;
import akka.testkit.JavaTestKit;
import akka.testkit.TestActorRef;
import akka.testkit.TestKit;
import com.google.common.collect.HashMultiset;
import fi.vrk.xroad.catalog.collector.util.XRoadCatalogID;
import fi.vrk.xroad.catalog.collector.util.XRoadCatalogMessage;
import fi.vrk.xroad.catalog.collector.wsimport.ClientListType;
import fi.vrk.xroad.catalog.collector.wsimport.ClientType;
import fi.vrk.xroad.catalog.collector.wsimport.XRoadClientIdentifierType;
import fi.vrk.xroad.catalog.collector.wsimport.XRoadObjectType;
import fi.vrk.xroad.catalog.persistence.CatalogService;
import fi.vrk.xroad.catalog.persistence.CatalogServiceImpl;
import fi.vrk.xroad.catalog.persistence.entity.Member;
import fi.vrk.xroad.catalog.persistence.entity.Subsystem;
import org.junit.*;
import org.junit.runner.RunWith;
import org.mockito.*;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestOperations;

import java.util.*;

import static org.mockito.Mockito.*;

/**
 * Created by sjk on 9.3.2016.
 */
@RunWith(PowerMockRunner.class)
@PrepareForTest({ListClientsActor.class, RelativeActorRefUtil.class})
@ActiveProfiles("development")
public class ListClientsActorTest extends TestKit {

    @Mock
    private CatalogService catalogService = new CatalogServiceImpl();

    @Mock
    private RestOperations restOperations;


    private InternalActorRef listMethodsPoolRef;


    private RelativeActorRefUtil relativeActorRefUtil;

    @InjectMocks
    protected ListClientsActor listClientsActor;

    @Captor ArgumentCaptor<Collection<Member>> argumentCaptor;

    static ActorSystem _system;

    public ListClientsActorTest() {
        super(_system);
    }

    @BeforeClass
    public static void setupTest() {
        _system = ActorSystem.create();
    }

    @AfterClass
    public static void teardown() {
        JavaTestKit.shutdownActorSystem(_system);
        _system = null;
    }

    @Before
    public void setup() throws Exception {
        listMethodsPoolRef = PowerMockito.mock(InternalActorRef.class);
        relativeActorRefUtil = PowerMockito.mock(RelativeActorRefUtil.class);

        PowerMockito.whenNew(RelativeActorRefUtil.class).withArguments(any()).thenReturn(relativeActorRefUtil);
        when(relativeActorRefUtil.resolvePoolRef(Supervisor.LIST_METHODS_ACTOR_ROUTER)).thenReturn(
                listMethodsPoolRef);

        final Props clientsProps = Props.create(ListClientsActor.class);
        final TestActorRef<ListClientsActor> clientsRef = TestActorRef.apply(clientsProps, _system);

        listClientsActor = clientsRef.underlyingActor();

        //TODO find a better way to set the host in test
        ReflectionTestUtils.setField(listClientsActor, "host", "http://localhost");


        MockitoAnnotations.initMocks(this);
    }

    @Test
    public void testOnReceive() throws Exception {



        List<ClientType> memberlist = new ArrayList<>();


        memberlist.add(createClientType(XRoadObjectType.MEMBER, "member1", null));
        memberlist.add(createClientType(XRoadObjectType.SUBSYSTEM, "member1", "sub1"));
        memberlist.add(createClientType(XRoadObjectType.SUBSYSTEM, "member1", "sub2"));
        memberlist.add(createClientType(XRoadObjectType.SUBSYSTEM, "member1", "sub3"));

        memberlist.add(createClientType(XRoadObjectType.MEMBER, "member2", null));
        memberlist.add(createClientType(XRoadObjectType.SUBSYSTEM, "member2", "sssub1"));
        memberlist.add(createClientType(XRoadObjectType.SUBSYSTEM, "member2", "sssub2"));

        ClientListType cMock = mock(ClientListType.class);
        doReturn(cMock).when(restOperations).getForObject("http://localhost/listClients", ClientListType
                .class);
        when(cMock.getMember()).thenReturn(memberlist);

//        doNothing().when(listMethodsPoolRef).tell(Matchers.anyObject(), Matchers.anyObject());
    //    doNothing().when(listMethodsPoolRef).tell(Matchers.any(ClientType.class), Matchers.any(ActorRef.class));

        // Call onReceive
        listClientsActor.onReceive(new XRoadCatalogMessage(new XRoadCatalogID(1,1),
                ListClientsActor.START_COLLECTING));

        Set<Member> expectedMembers = new HashSet<>();
        Member member1 = new Member("FI", "GOV", "member1", "member1");
        Set<Subsystem> subsystems = new HashSet<>();
        subsystems.add(new Subsystem(member1, "sub1"));
        subsystems.add(new Subsystem(member1, "sub2"));
        subsystems.add(new Subsystem(member1, "sub3"));
        member1.setSubsystems(subsystems);



        Member member2 = new Member("FI", "GOV", "member2", "member2");
        subsystems = new HashSet<>();
        subsystems.add(new Subsystem(member2, "sssub1"));
        subsystems.add(new Subsystem(member2, "sssub2"));
        member2.setSubsystems(subsystems);

        expectedMembers.add(member1);
        expectedMembers.add(member2);

        // Verify that the save method was called with correct member collection
        verify(catalogService).saveAllMembersAndSubsystems(argumentCaptor.capture());

        Collection<Member> resultMembers = argumentCaptor.getValue();
        Assert.assertEquals(HashMultiset.create(expectedMembers), HashMultiset.create(resultMembers));

        Assert.assertEquals(HashMultiset.create(member1.getAllSubsystems()), HashMultiset.create(resultMembers.stream()
                .filter(m -> member1.equals(m)).findAny().get().getAllSubsystems()));

        Assert.assertEquals(HashMultiset.create(member2.getAllSubsystems()), HashMultiset.create(resultMembers.stream()
                .filter(m -> member2.equals(m)).findAny().get().getAllSubsystems()));
    }

    protected ClientType createClientType(XRoadObjectType objectType, String memberCode, String subsystemCode) {
        ClientType c = new ClientType();
        XRoadClientIdentifierType xrcit = new XRoadClientIdentifierType();

        xrcit.setXRoadInstance("FI");
        xrcit.setMemberClass("GOV");
        xrcit.setMemberCode(memberCode);
        xrcit.setSubsystemCode(subsystemCode);
        xrcit.setObjectType(objectType);
        c.setId(xrcit);
        c.setName(memberCode);
        return c;

    }
}