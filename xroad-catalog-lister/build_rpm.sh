#!/bin/sh

pwd
ls -l
cd workspace
pwd
ls -l
cd packages/xroad-catalog-lister/redhat
rpmbuild --define "_topdir `pwd`" -ba SPECS/xroad-catalog-lister.spec