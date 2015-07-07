Configuration files
===================

This folder contains example configuration files. To use, copy the configuratio file into metrics.yaml the working directory from where the OSGi container is started or point to the file here with -Dmetrics.config=<path to file> in the JVM that starts the OSGi container.

* metrics\_dump.yaml   dumps the methods of all classes matching org.apache.jackrabbit.oak\..* to metrics_\dump.yaml
* metrics_mongo.yaml   instruments methods related to the MongoDocumentStore