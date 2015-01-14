#!/bin/sh

java -Xmx80g -jar graphlod-0.1.jar --excludedNamespaces \
    "http://www4.wiwiss.fu-berlin.de/drugbank/resource/drugtype/" \
    "http://www4.wiwiss.fu-berlin.de/drugbank/resource/references/" \
    --skipChromatic \
    --namespace "http://www4.wiwiss.fu-berlin.de/drugbank/" \
    /data/graphlod/drugbank/drugbank.nt | tee drugbank.txt

java -Xmx80g -jar graphlod-0.1.jar --skipChromatic \
    --namespace "http://www4.wiwiss.fu-berlin.de/dailymed/" \
    /data/graphlod/dailymed/dailymed_dump.nt | tee dailymed_dump.txt

java -Xmx80g -jar graphlod-0.1.jar  --skipChromatic --excludedNamespaces \
    "http://www4.wiwiss.fu-berlin.de/diseasome/resource/diseaseClass/" \
    --namespace "http://www4.wiwiss.fu-berlin.de/diseasome/" \
    /data/graphlod/diseasome/diseasome.nt | tee diseasome.txt

java -Xmx80g -jar graphlod-0.1.jar --skipChromatic \
    --namespace "http://dbpedia.org/resource" \
    /data/graphlod/dbpedia/persondata_en.nt | tee dbpedia_persondata.txt

java -Xmx80g -jar graphlod-0.1.jar --skipChromatic  \
    --namespace "http://dbpedia.org/resource" \
    /data/graphlod/dbpedia/geo_coordinates_en.nt | tee geo_coordinate.txt

java -Xmx80g -jar graphlod-0.1.jar --skipChromatic  \
    --namespace "http://dbpedia.org/resource" \
    /data/graphlod/dbpedia/homepages_en.nt | tee dbpedia_homepages.txt

# fix mapping: sed 's/"\.$/" \./' mappingbased_properties_en.nt > mappingbased_properties_en_fixed.nt

java -Xmx100g -jar graphlod-0.1.jar --skipChromatic \
    --namespace "http://dbpedia.org/resource" \
    mappingbased_properties_en_fixed.nt | tee dbpedia_mapping.txt

java -Xmx80g -jar graphlod-0.1.jar --skipChromatic \
    /data/graphlod/linkedgeodata/2013-04-29-{Ae*,C*,E*,Mili*,H*,P*,S*,T*} \
    --namespace "http://linkedgeodata.org/" \
    | tee linkedgeodata.txt

