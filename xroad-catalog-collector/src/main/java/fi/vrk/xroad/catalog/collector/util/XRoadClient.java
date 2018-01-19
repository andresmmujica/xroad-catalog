/**
 * The MIT License
 * Copyright (c) 2016, Population Register Centre (VRK)
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package fi.vrk.xroad.catalog.collector.util;

import fi.vrk.xroad.catalog.collector.wsimport.GetWsdl;
import fi.vrk.xroad.catalog.collector.wsimport.GetWsdlResponse;
import fi.vrk.xroad.catalog.collector.wsimport.ListMethods;
import fi.vrk.xroad.catalog.collector.wsimport.ListMethodsResponse;
import fi.vrk.xroad.catalog.collector.wsimport.MetaServicesPort;
import fi.vrk.xroad.catalog.collector.wsimport.ProducerPortService;
import fi.vrk.xroad.catalog.collector.wsimport.XRoadClientIdentifierType;
import fi.vrk.xroad.catalog.collector.wsimport.XRoadIdentifierType;
import fi.vrk.xroad.catalog.collector.wsimport.XRoadObjectType;
import fi.vrk.xroad.catalog.collector.wsimport.XRoadServiceIdentifierType;

import lombok.extern.slf4j.Slf4j;
import org.apache.cxf.endpoint.Client;
import org.apache.cxf.frontend.ClientProxy;
import org.apache.cxf.message.Attachment;
import org.apache.cxf.message.Message;
import org.apache.cxf.transport.http.HTTPConduit;

import javax.activation.DataHandler;
import javax.xml.ws.BindingProvider;
import javax.xml.ws.Holder;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * WS client
 */
@Slf4j
public class XRoadClient {

    final MetaServicesPort metaServicesPort;
    final XRoadClientIdentifierType clientId;

    public XRoadClient(XRoadClientIdentifierType clientId, URL serverUrl) {
        this.metaServicesPort = getMetaServicesPort(serverUrl);

        final XRoadClientIdentifierType tmp = new XRoadClientIdentifierType();
        copyIdentifierType(tmp, clientId);
        this.clientId = tmp;
    }

    /**
     * Calls the service using JAX-WS endpoints that have been generated from wsdl
     */
    public List<XRoadServiceIdentifierType> getMethods(XRoadClientIdentifierType member) {
        XRoadServiceIdentifierType serviceIdentifierType = new XRoadServiceIdentifierType();
        copyIdentifierType(serviceIdentifierType, member);

        XRoadClientIdentifierType tmpClientId = new XRoadClientIdentifierType();
        copyIdentifierType(tmpClientId, clientId);

        serviceIdentifierType.setServiceCode("listMethods");
        serviceIdentifierType.setServiceVersion("v1");
        serviceIdentifierType.setObjectType(XRoadObjectType.SERVICE);

        ListMethodsResponse response = metaServicesPort.listMethods(new ListMethods(),
                holder(tmpClientId),
                holder(serviceIdentifierType),
                userId(),
                queryId(),
                protocolVersion());

        return response.getService();
    }

    public String getWsdl(XRoadServiceIdentifierType service) {

        XRoadServiceIdentifierType serviceIdentifierType = new XRoadServiceIdentifierType();
        copyIdentifierType(serviceIdentifierType, service);

        XRoadClientIdentifierType tmpClientId = new XRoadClientIdentifierType();
        copyIdentifierType(tmpClientId, clientId);

        serviceIdentifierType.setServiceCode("getWsdl");
        serviceIdentifierType.setServiceVersion("v1");
        serviceIdentifierType.setObjectType(XRoadObjectType.SERVICE);

        final GetWsdl getWsdl = new GetWsdl();
        getWsdl.setServiceCode(service.getServiceCode());
        getWsdl.setServiceVersion(service.getServiceVersion());

        final Holder<GetWsdlResponse> response = new Holder<>();
        final Holder<DataHandler> wsdl = new Holder<>();

        metaServicesPort.getWsdl(getWsdl,
                holder(tmpClientId),
                holder(serviceIdentifierType),
                userId(),
                queryId(),
                protocolVersion(),
                response,
                wsdl);

        DataHandler dh;
        if ( wsdl.value == null ) {
            // Apache CXF does not map the attachment returned by the security server to the wsdl
            // output parameter due to missing Content-Id header. Extract the attachment from the
            // response context.

            final Client client = ClientProxy.getClient(metaServicesPort);
            final Collection<Attachment> attachments =
                    (Collection<Attachment>)client.getResponseContext().get(Message.ATTACHMENTS);
            if (attachments != null && attachments.size() == 1) {
                dh = attachments.iterator().next().getDataHandler();
            } else {
                throw new CatalogCollectorRuntimeException("Expected one WSDL attachment");
            }
        } else {
            dh = wsdl.value;
        }

        try (ByteArrayOutputStream buf = new ByteArrayOutputStream()) {
            dh.writeTo(buf);
            return buf.toString(StandardCharsets.UTF_8.name());
        } catch (IOException e) {
            throw new CatalogCollectorRuntimeException("Error downloading wsdl", e);
        }
    }


    private static Holder<String> queryId() {
        return holder("xroad-catalog-collector-" + UUID.randomUUID());
    }

    private static Holder<String> protocolVersion() {
        return holder("4.0");
    }

    private static Holder<String> userId() {
        return holder("xroad-catalog-collector");
    }

    private static <T> Holder<T> holder(T value) {
        return new Holder<>(value);
    }

    /**
     * MetaServicesPort for url
     */
    private static MetaServicesPort getMetaServicesPort(URL url) {
        ProducerPortService service = new ProducerPortService();
        MetaServicesPort port = service.getMetaServicesPortSoap11();
        BindingProvider bindingProvider = (BindingProvider) port;
        bindingProvider.getRequestContext().put(BindingProvider.ENDPOINT_ADDRESS_PROPERTY, url.toString());

        final HTTPConduit conduit = (HTTPConduit) ClientProxy.getClient(port).getConduit();
        conduit.getClient().setConnectionTimeout(30000);
        conduit.getClient().setReceiveTimeout(60000);

        return port;
    }

    private static void copyIdentifierType(XRoadIdentifierType target, XRoadIdentifierType source) {
        target.setGroupCode(source.getGroupCode());
        target.setObjectType(source.getObjectType());
        target.setMemberCode(source.getMemberCode());
        target.setServiceVersion(source.getServiceVersion());
        target.setMemberClass(source.getMemberClass());
        target.setServiceCode(source.getServiceCode());
        target.setSecurityCategoryCode(source.getSecurityCategoryCode());
        target.setServerCode(source.getServerCode());
        target.setXRoadInstance(source.getXRoadInstance());
        target.setSubsystemCode(source.getSubsystemCode());
    }

}
