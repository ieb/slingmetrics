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

To instrument a method, a configuration file must be created specifying which methods should be instrumented. By default this file is ./metrics.config relative to the JVM process working directory. This may be modified on the command line with -Dmetrics.config=<relative or absolute path to metrics.config>. The file is a standard properties file. It contains the names of classes and methods, and some special settings identified by a leading _. Instrumenting a method requires enabling the class for instrumentation and then defining the type of instrumentation on the method.

    <classname>: true
    <classname>.<methodname>: timer|meter|counter
    
Where timer causes a timer to be wrapped around the method, meter inserts a meter at the start of the method and counter inserts a counter at the start of the method. At present, all method names that match the pattern will have the same instrumentation applied. This may change if it proves un-workable.

Activating a configuration
--------------------------

While OSGi does allow this bundle to restart there are some issues. Restarting the bundle will not cause bundles that do not depend on this bundle to restart, and if the classes have already been loaded they wont be instrumented. Bundles that have already been instrumented by this bundle will restart. Both can be confusing and although care has been taken not to leak objects or references, it is probably best to restart the JVM to ensure that the weaving is performed as configured. Once a bundle is installed with a low start level (eg 1), that start level will be used on startup. If no start level is specified, the start level will be 20, too late to instrument many classes.

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