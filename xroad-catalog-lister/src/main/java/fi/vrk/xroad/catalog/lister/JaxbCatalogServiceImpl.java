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
package fi.vrk.xroad.catalog.lister;

import fi.vrk.xroad.catalog.persistence.entity.*;
import fi.vrk.xroad.catalog.persistence.service.CatalogService;
import fi.vrk.xroad.xroad_catalog_lister.ChangedValue;
import fi.vrk.xroad.xroad_catalog_lister.Member;
import fi.vrk.xroad.xroad_catalog_lister.Organization;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.xml.datatype.XMLGregorianCalendar;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;

/**
 * XML interface for lister
 */
@Component
@Slf4j
public class JaxbCatalogServiceImpl implements JaxbCatalogService {

    @Autowired
    @Setter
    private CatalogService catalogService;

    @Autowired
    @Setter
    private JaxbConverter jaxbConverter;

    @Override
    public Iterable<Member> getAllMembers(XMLGregorianCalendar changedAfter)  {
        log.info("getAllMembers changedAfter:{}", changedAfter);
        Iterable<fi.vrk.xroad.catalog.persistence.entity.Member> entities;
        if (changedAfter != null) {
            entities = catalogService.getAllMembers(jaxbConverter.toLocalDateTime(changedAfter));
        } else {
            entities = catalogService.getAllMembers();
        }

        return jaxbConverter.convertMembers(entities, false);
    }

    @Override
    public Iterable<Organization> getOrganizations(String businessCode) {
        log.info("get organizations with businessCode:{}", businessCode);
        Iterable<fi.vrk.xroad.catalog.persistence.entity.Organization> entities;
        entities = catalogService.getOrganizations(businessCode);

        return jaxbConverter.convertOrganizations(entities);
    }

    @Override
    public Iterable<ChangedValue> getChangedValues(String guid, XMLGregorianCalendar changedAfter) {
        log.info("get changed values for oganization with guid {} and changedAfter {}", guid, changedAfter);
        fi.vrk.xroad.catalog.persistence.entity.Organization organization = catalogService.getOrganization(guid);

        return getAllChangedValuesForOrganization(organization, jaxbConverter.toLocalDateTime(changedAfter));
    }

    private Iterable<ChangedValue> getAllChangedValuesForOrganization(fi.vrk.xroad.catalog.persistence.entity.Organization organization,
                                               LocalDateTime since) {
        List<ChangedValue> changedValueList = new ArrayList<>();
        Set<OrganizationName> organizationNames = organization.getAllOrganizationNames();
        Set<OrganizationDescription> organizationDescriptions = organization.getAllOrganizationDescriptions();
        Set<Email> emails = organization.getAllEmails();
        Set<PhoneNumber> phoneNumbers = organization.getAllPhoneNumbers();
        Set<WebPage> webPages = organization.getAllWebPages();
        Set<Address> addresses = organization.getAllAddresses();

        if (organization.getStatusInfo().getChanged().isAfter(since)) {
            ChangedValue changedValue = new ChangedValue();
            changedValue.setName("Organization");
            changedValueList.add(changedValue);
        }

        if (organizationNames.stream().anyMatch(obj -> obj.getStatusInfo().getChanged().isAfter(since))) {
            ChangedValue changedValue = new ChangedValue();
            changedValue.setName("OrganizationName");
            changedValueList.add(changedValue);
        }

        if (organizationDescriptions.stream().anyMatch(obj -> obj.getStatusInfo().getChanged().isAfter(since))) {
            ChangedValue changedValue = new ChangedValue();
            changedValue.setName("OrganizationDescription");
            changedValueList.add(changedValue);
        }

        if (emails.stream().anyMatch(obj -> obj.getStatusInfo().getChanged().isAfter(since))) {
            ChangedValue changedValue = new ChangedValue();
            changedValue.setName("Email");
            changedValueList.add(changedValue);
        }

        if (phoneNumbers.stream().anyMatch(obj -> obj.getStatusInfo().getChanged().isAfter(since))) {
            ChangedValue changedValue = new ChangedValue();
            changedValue.setName("PhoneNumber");
            changedValueList.add(changedValue);
        }

        if (webPages.stream().anyMatch(obj -> obj.getStatusInfo().getChanged().isAfter(since))) {
            ChangedValue changedValue = new ChangedValue();
            changedValue.setName("WebPage");
            changedValueList.add(changedValue);
        }

        changedValueList.addAll(getAllChangedValuesForAddress(addresses, since));

        return changedValueList;
    }

    private Collection<ChangedValue> getAllChangedValuesForAddress(Set<Address> addresses, LocalDateTime since) {
        List<ChangedValue> changedValueList = new ArrayList<>();
        addresses.forEach(address -> {
            if (address.getStatusInfo().getChanged().isAfter(since)) {
                ChangedValue changedValue = new ChangedValue();
                changedValue.setName("Address");
                changedValueList.add(changedValue);
            }

            Set<StreetAddress> streetAddresses = address.getAllStreetAddresses();
            streetAddresses.forEach(streetAddress -> {
                if (streetAddress.getStatusInfo().getChanged().isAfter(since)) {
                    ChangedValue changedValue = new ChangedValue();
                    changedValue.setName("StreetAddress");
                    changedValueList.add(changedValue);
                }

                Set<Street> streets = streetAddress.getAllStreets();
                streets.forEach(street -> {
                    if (street.getStatusInfo().getChanged().isAfter(since)) {
                        ChangedValue changedValue = new ChangedValue();
                        changedValue.setName("Street");
                        changedValueList.add(changedValue);
                    }
                });

                Set<StreetAddressPostOffice> postOffices = streetAddress.getAllPostOffices();
                postOffices.forEach(postOffice -> {
                    if (postOffice.getStatusInfo().getChanged().isAfter(since)) {
                        ChangedValue changedValue = new ChangedValue();
                        changedValue.setName("StreetAddress PostOffice");
                        changedValueList.add(changedValue);
                    }
                });

                Set<StreetAddressMunicipality> municipalities = streetAddress.getAllMunicipalities();
                municipalities.forEach(municipality -> {
                    if (municipality.getStatusInfo().getChanged().isAfter(since)) {
                        ChangedValue changedValue = new ChangedValue();
                        changedValue.setName("StreetAddress Municipality");
                        changedValueList.add(changedValue);
                    }

                    Set<StreetAddressMunicipalityName> municipalityNames = municipality.getAllMunicipalityNames();
                    municipalityNames.forEach(municipalityName -> {
                        if (municipalityName.getStatusInfo().getChanged().isAfter(since)) {
                            ChangedValue changedValue = new ChangedValue();
                            changedValue.setName("StreetAddress Municipality Name");
                            changedValueList.add(changedValue);
                        }
                    });
                });

                Set<StreetAddressAdditionalInformation> additionalInformationList = streetAddress.getAllAdditionalInformation();
                additionalInformationList.forEach(additionalInformation -> {
                    if (additionalInformation.getStatusInfo().getChanged().isAfter(since)) {
                        ChangedValue changedValue = new ChangedValue();
                        changedValue.setName("StreetAddress AdditionalInformation");
                        changedValueList.add(changedValue);
                    }
                });

            });

            Set<PostOfficeBoxAddress> postOfficeBoxAddresses = address.getAllPostOfficeBoxAddresses();
            postOfficeBoxAddresses.forEach(postOfficeBoxAddress -> {
                if (postOfficeBoxAddress.getStatusInfo().getChanged().isAfter(since)) {
                    ChangedValue changedValue = new ChangedValue();
                    changedValue.setName("PostOfficeBoxAddress");
                    changedValueList.add(changedValue);
                }

                Set<PostOffice> postOffices = postOfficeBoxAddress.getAllPostOffices();
                postOffices.forEach(postOffice -> {
                    if (postOffice.getStatusInfo().getChanged().isAfter(since)) {
                        ChangedValue changedValue = new ChangedValue();
                        changedValue.setName("PostOfficeBoxAddress PostOffice");
                        changedValueList.add(changedValue);
                    }
                });

                Set<PostOfficeBox> postOfficeBoxes = postOfficeBoxAddress.getAllPostOfficeBoxes();
                postOfficeBoxes.forEach(postOfficeBox -> {
                    if (postOfficeBox.getStatusInfo().getChanged().isAfter(since)) {
                        ChangedValue changedValue = new ChangedValue();
                        changedValue.setName("PostOfficeBoxAddress PostOfficeBox");
                        changedValueList.add(changedValue);
                    }
                });

                Set<PostOfficeBoxAddressAdditionalInformation> addressAdditionalInformationList = postOfficeBoxAddress.getAllAdditionalInformation();
                addressAdditionalInformationList.forEach(additionalInformation -> {
                    if (additionalInformation.getStatusInfo().getChanged().isAfter(since)) {
                        ChangedValue changedValue = new ChangedValue();
                        changedValue.setName("PostOfficeBoxAddress AdditionalInformation");
                        changedValueList.add(changedValue);
                    }
                });

                Set<PostOfficeBoxAddressMunicipality> municipalities = postOfficeBoxAddress.getAllMunicipalities();
                municipalities.forEach(municipality -> {
                    if (municipality.getStatusInfo().getChanged().isAfter(since)) {
                        ChangedValue changedValue = new ChangedValue();
                        changedValue.setName("PostOfficeBoxAddress Municipality");
                        changedValueList.add(changedValue);
                    }

                    Set<PostOfficeBoxAddressMunicipalityName> municipalityNames = municipality.getAllMunicipalityNames();
                    municipalityNames.forEach(municipalityName -> {
                        if (municipalityName.getStatusInfo().getChanged().isAfter(since)) {
                            ChangedValue changedValue = new ChangedValue();
                            changedValue.setName("PostOfficeBoxAddress Municipality Name");
                            changedValueList.add(changedValue);
                        }
                    });
                });
            });
        });

        return changedValueList;
    }
}
