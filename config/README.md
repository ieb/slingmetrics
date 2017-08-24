Configuration files
===================

This folder contains example configuration files. To use, copy the configuratio file into metrics.yaml the working directory from where the OSGi container is started or point to the file here with -Dmetrics.config=<path to file> in the JVM that starts the OSGi container.

* metrics\_dump.yaml   dumps the methods of all classes matching org.apache.jackrabbit.oak\..* to metrics_\dump.yaml
* metrics\_mongo.yaml   instruments methods related to the MongoDocumentStore
* metrics\_jcr.yaml.   Instruments all interfaces and classes under javax.jcr mainly used to discovering JCR API usage and performance.
* metrics\_jcrapi.yaml   Instruments all interfaces and classes under javax.jcr and org.apache.sling.jcr.resource mainly used to discovering JCR API usage and performance.
* metrics\_indexing.yaml Instruments Oak index operations.
* metrics\_observation.yaml Instruments Oak Observation operations.
* metrics\_repository.yaml Instruments Oak Repository operations.