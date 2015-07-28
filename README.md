Metrics
=======

This bundle adds Dorpwizard Metrics. It does 2 things.

1. Provides an API to interact with a Metrics integration (MetricsFactory, MetricsUtil)
2. Provides a Dropwizard MetricsRegistry as a service, which is also used for the implementation of MetricsFactory.

The bundle will instrument selected methods with Timer or Meter or Counter calls. It instruments by rewriting the byte code of the class as its loaded by the classloader using the OSGi WeaverHook. This eliminates the need for developers to instrument their own code with the restriction that only methods may be instrumented.

Should the developer wish to instrument their own code they can use the MetricsFactory service. Should the wish to access implementation details of the Dropwizard MetricsRegistry, they can use the MetricsRegistry service. This service is also required for anyone wanting to implement a Metrics Reporter specific to this implementation of the API.

The bundle should be loaded at as low a start level as possible so that its WeavingHook is active before any classes being instrumented are loaded. To do this with maven use

    mvn clean install sling:install -Dsling.bundle.startlevel=1

Configuration
-------------

To instrument a method, a configuration file must be created specifying which methods should be instrumented. By default this file is ./metrics.yaml relative to the JVM process working directory. This may be modified on the command line with -Dmetrics.config=<relative or absolute path to metrics.yaml>. The file is a standard yaml file. With the following structure

    global:
        <global settings>
    <classname>:
        <class specific settings>
        
Global Settings
---------------

Monitoring: 

    global:
      monitor: true
      debug: true

With global monitoring on the instance will log at info level the name of every class encountered. With debug, the metrics bundle will log debug messages at info level avoiding needing to reconfigure the logging service or wait for it to be available.

Dump File:

    global:
      dump:
        output: <dumpfilename>
        include:
           - <regex of class to include>
           - <regex of class to include>
        exclude:
           - <regex of class to exclude>
           - <regex of class to exclude>
      
If a dump file is specified a Yaml file will be created containing configuration for every class, every method, every signature configured to use timer instrumentation. This file could be larger, but makes the job of instrumenting a large system easier as the yaml can be post processed to create a configuration. Include, if present will filter all classes and only include those classes that match one of the regexes. Exclude will exclude a class that matches one of the regexes.

Reporters:

    global:
      reporters:
        jmx: true
        servlet: true
        kibana:
          url: http://monioring.example.com          
        graphite: true
        console:
          period: 30
  
A number of reporters are supported within the core bundle, and some of those reporters have configuration options. This area is WIP, currently only console and JMX are fully supported.


Per Class Configuration
-----------------------

Every class may have its own configuration identified by its classname, even classes in the monitoring bundle may be monitored provided they are loaded after the activator has initialized.

Monitoring:

    org.apache.jackrabbit.oak.core.MutableTree:
      _monitor_class: true
      
A class may be monitored to log all methods present at INFO level.

Instrumenting methods by name:
      
    org.apache.jackrabbit.oak.core.MutableTree:
      setProperty: timer
      
To instrument all methods that match a name, configure the method with an instrumentation type. All methods may be instrumented regardless of visibility. In the above configuration all methods with a method name of setProperty in MutableTree will be instrumented with the same timer metric. If different metrics are required, instrument by signature as below.

Instrumenting methods by signature:
      
    org.apache.jackrabbit.oak.core.MutableTree:
      setProperty:
        <T:Ljava/lang/Object;>(Ljava/lang/String;TT;)V: timer

There are several signatures of setProperty in MutableTree. In the above example only 1 will be instrumented. 

    public <T> void setProperty(@Nonnull String name, @Nonnull T value) { ... }
    
This method has a signature of 

    <T:Ljava/lang/Object;>(Ljava/lang/String;TT;)V
    
Which can be discovered by looking at the byte code for the class, mentally converting the signature into a byte code description or using the \_monitor\_class option to log the methods.

Instrumenting return values
---------------------------

At times it becomes necessary to monitor activity on a single method, segmenting the metrics by some aspect of the return object. This can be achieved using the count\_return or meter\_return values.
   
    org.apache.sling.servlets.resolver.internal.SlingServletResolver:
      resolveServletInternal:
        type: meter_return
        keyMethod: <methodname>
        helperClass: <helperClassName>
     
A type of meter\_return will create a meter metric on the return value. A type of count\_return will create a counter metric on the return value. If the keyMethod is specified a method of that name with the signature String <methodname>() on the return object will be used to get the name of the metric. If helperClass is specified, a class of that name, implementing the MetricsNameHelper interface will be instanced, and the MetricsNameHelper.getName(T returnObject) will be called to get the name of the metric. If the helper class is not available, the metric will be given a suitable name. Helper classes should be implemented in independent bundles loaded as soon as possible to ensure no statistics are missed. e.g., to meter Sling Component usage,

    org.apache.sling.servlets.resolver.internal.SlingServletResolver:
      resolveServletInternal:
        type: meter_return
        helperClass: org.apache.sling.metrics.helpers.ServletNameHelper
        
To meter resource resolution.
    
    org.apache.sling.resourceresolver.impl.ResourceResolverImpl:
      getResourceInternal:
        type: meter_return 
        keyMethod: getResourceType


      
Instrumentation types
---------------------

Timers time the duration of the method call, calculating the following metrics.

    org.apache.sling.resourceresolver.impl.ResourceResolverFactoryImpl.getResourceResolver
             count = 1
         mean rate = 0.00 calls/second
     1-minute rate = 0.00 calls/second
     5-minute rate = 0.00 calls/second
    15-minute rate = 0.00 calls/second
               min = 0.04 milliseconds
               max = 0.04 milliseconds
              mean = 0.04 milliseconds
            stddev = 0.00 milliseconds
            median = 0.04 milliseconds
              75% <= 0.04 milliseconds
              95% <= 0.04 milliseconds
              98% <= 0.04 milliseconds
              99% <= 0.04 milliseconds
            99.9% <= 0.04 milliseconds
    

A meter will measure the rate of call generating the following staticstics.

    org.apache.sling.resourceresolver.impl.ResourceResolverFactoryImpl.getResourceResolver
             count = 1
         mean rate = 0.00 calls/second
     1-minute rate = 0.00 calls/second
     5-minute rate = 0.00 calls/second
    15-minute rate = 0.00 calls/second

A counter is the simplest, measuring only the number of calls.        
        

    org.apache.sling.resourceresolver.impl.ResourceResolverFactoryImpl.getResourceResolver
             count = 1
    
    

Activating a configuration
--------------------------

While OSGi does allow this bundle to restart there are some issues. Restarting the bundle will not cause bundles that do not depend on this bundle to restart, and if the classes have already been loaded they wont be instrumented. Bundles that have already been instrumented by this bundle will restart. Both can be confusing and although care has been taken not to leak objects or references, it is probably best to restart the JVM to ensure that the weaving is performed as configured. Once a bundle is installed with a low start level (eg 1), that start level will be used on startup. If no start level is specified, the start level will be 20, too late to instrument many classes.


Instrumenting a code base
-------------------------

With millions of methods in a JVM creating a configuraiton for a code base can be daunting. Its often best to use the dump functionality to generate the configruation and work from there. To do that start with a configuration that dumps everything of interest. 


    global:
      reporters:
        jmx: true
        console:
          period: 30
      dump:
        output: metrics_dump.yaml
        exclude:
           - java\.lang\..*
        include:
           - org.apache.jackrabbit.core\..*
           
Then run the OSGi container. The matched classes and methods will be dumped in Yaml format to metrics_dump.yaml. 

    org.apache.jackrabbit.core.config.RepositoryConfig:
      install:
         (Ljava/io/File;)Lorg/apache/jackrabbit/core/config/RepositoryConfig;: timer
         (Ljava/util/Properties;)Lorg/apache/jackrabbit/core/config/RepositoryConfig;: timer
      getRepositoryHome:
         (Ljava/util/Properties;)Ljava/io/File;: timer
      install:
         (Ljava/io/File;Ljava/io/File;)Lorg/apache/jackrabbit/core/config/RepositoryConfig;: timer
      installRepositorySkeleton:
         (Ljava/io/File;Ljava/io/File;Ljava/net/URL;)V: timer
      create:
         (Ljava/io/File;)Lorg/apache/jackrabbit/core/config/RepositoryConfig;: timer
         (Ljava/io/File;Ljava/io/File;)Lorg/apache/jackrabbit/core/config/RepositoryConfig;: timer
         (Ljava/lang/String;Ljava/lang/String;)Lorg/apache/jackrabbit/core/config/RepositoryConfig;: timer
         (Ljava/net/URI;Ljava/lang/String;)Lorg/apache/jackrabbit/core/config/RepositoryConfig;: timer
         (Ljava/io/InputStream;Ljava/lang/String;)Lorg/apache/jackrabbit/core/config/RepositoryConfig;: timer
         (Lorg/xml/sax/InputSource;Ljava/lang/String;)Lorg/apache/jackrabbit/core/config/RepositoryConfig;: timer
         (Lorg/xml/sax/InputSource;Ljava/util/Properties;)Lorg/apache/jackrabbit/core/config/RepositoryConfig;: timer
         (Lorg/apache/jackrabbit/core/config/RepositoryConfig;)Lorg/apache/jackrabbit/core/config/RepositoryConfig;: timer
      <init>:
         (Ljava/lang/String;Lorg/apache/jackrabbit/core/config/SecurityConfig;Lorg/apache/jackrabbit/core/fs/FileSystemFactory;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;ILorg/w3c/dom/Element;Lorg/apache/jackrabbit/core/config/VersioningConfig;Lorg/apache/jackrabbit/core/query/QueryHandlerFactory;Lorg/apache/jackrabbit/core/config/ClusterConfig;Lorg/apache/jackrabbit/core/data/DataStoreFactory;Lorg/apache/jackrabbit/core/util/RepositoryLockMechanismFactory;Lorg/apache/jackrabbit/core/config/DataSourceConfig;Lorg/apache/jackrabbit/core/util/db/ConnectionFactory;Lorg/apache/jackrabbit/core/config/RepositoryConfigurationParser;)V: timer
         etc etc etc


You can then select several classes and methods to instrument the methods of interest.



    global:
      reporters:
        jmx: true
        console:
          period: 30
    org.apache.jackrabbit.core.SessionImpl:
      <init>: timer
      save: timer
      refresh: timer 
      logout: timer

and restart the OSGi container. The console reporter will produce output as follows every 30s.


    7/3/15 12:46:18 PM =============================================================

    -- Timers ----------------------------------------------------------------------
    org.apache.jackrabbit.core.SessionImpl.<init>
	             count = 327
	         mean rate = 0.59 calls/second
	     1-minute rate = 0.54 calls/second
	     5-minute rate = 0.79 calls/second
	    15-minute rate = 1.45 calls/second
	               min = 0.00 milliseconds
	               max = 0.05 milliseconds
	              mean = 0.00 milliseconds
	            stddev = 0.00 milliseconds
	            median = 0.00 milliseconds
	              75% <= 0.00 milliseconds
	              95% <= 0.00 milliseconds
	              98% <= 0.00 milliseconds
	              99% <= 0.00 milliseconds
	            99.9% <= 0.00 milliseconds
	org.apache.jackrabbit.core.SessionImpl.logout
	             count = 158
	         mean rate = 0.29 calls/second
	     1-minute rate = 0.27 calls/second
	     5-minute rate = 0.60 calls/second
	    15-minute rate = 1.43 calls/second
	               min = 0.00 milliseconds
	               max = 0.00 milliseconds
	              mean = 0.00 milliseconds
	            stddev = 0.00 milliseconds
	            median = 0.00 milliseconds
	              75% <= 0.00 milliseconds
	              95% <= 0.00 milliseconds
	              98% <= 0.00 milliseconds
	              99% <= 0.00 milliseconds
	            99.9% <= 0.00 milliseconds
	org.apache.jackrabbit.core.SessionImpl.refresh
	             count = 80
	         mean rate = 0.15 calls/second
	     1-minute rate = 0.16 calls/second
	     5-minute rate = 0.38 calls/second
	    15-minute rate = 0.94 calls/second
	               min = 0.00 milliseconds
	               max = 0.00 milliseconds
	              mean = 0.00 milliseconds
	            stddev = 0.00 milliseconds
	            median = 0.00 milliseconds
	              75% <= 0.00 milliseconds
	              95% <= 0.00 milliseconds
	              98% <= 0.00 milliseconds
	              99% <= 0.00 milliseconds
	            99.9% <= 0.00 milliseconds
	org.apache.jackrabbit.core.SessionImpl.save
	             count = 48
	         mean rate = 0.09 calls/second
	     1-minute rate = 0.11 calls/second
	     5-minute rate = 0.17 calls/second
	    15-minute rate = 0.37 calls/second
	               min = 0.00 milliseconds
	               max = 0.01 milliseconds
	              mean = 0.00 milliseconds
	            stddev = 0.00 milliseconds
	            median = 0.00 milliseconds
	              75% <= 0.00 milliseconds
	              95% <= 0.00 milliseconds
	              98% <= 0.00 milliseconds
	              99% <= 0.00 milliseconds
	            99.9% <= 0.00 milliseconds

Performance
-----------

Dropwizard Metrics was originally written to be enabled all the time in production. It has been written in a way to introduce minimal overhead in terms of time and heap. Many organizations are using it successfully in production. This bundle adds minimal overhead to metrics. For a Timer, 5 byte code instructions are added per method, for a counter and meter, 1. The wrappers are minimal and will inline if hotspot decided to do so.


Built in Reporting
------------------

The bundle comes with console and jmx reporting built in. To enable JMX reporting add

    _reporter_jmx: true
    
to the configuration file.

To enable console reporting add

    _reporter_console: <seconds>
    
Where <seconds> is the number of seconds between reports.

External reporting
------------------

Reporting of other forms should be implemented in independent bundles using the MetricsRegistry service to get hold of the metrics. The MetricsFactory API is designed to be a measurement only API and provided no interface to reporting on the metrics.