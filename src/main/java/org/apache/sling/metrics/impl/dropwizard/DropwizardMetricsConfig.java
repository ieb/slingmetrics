/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The SF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package org.apache.sling.metrics.impl.dropwizard;

import java.io.Closeable;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.Stack;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.apache.sling.metrics.api.LogServiceHolder;
import org.apache.sling.metrics.api.MetricsUtil;
import org.apache.sling.metrics.impl.MetricsActivator;
import org.apache.sling.metrics.impl.dropwizard.ElasticSearchReporter.Builder;
import org.slf4j.LoggerFactory;

import com.codahale.metrics.ConsoleReporter;
import com.codahale.metrics.JmxReporter;
import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.Slf4jReporter;
import com.esotericsoftware.yamlbeans.YamlReader;

/**
 * Configure dropwizards instrumentation from a yaml file.
 * <pre>
 * # global section
 * global:
 *    # montor the classnames loaded
 *    monitor: true
 *    # dump all classes and methods to a yaml config file
 *    dumpfile: dumpfilename
 *    # reporters section, true means run the reporter, some may have options.
 *    reporters:
 *      jmx: true
 *      servlet: true
 *      kibana: true
 *      graphite: true
 *      console:
 *        period: 30
 *
 * # class configuration, _monitor_class will monitor the class
 * # a method name with a recognized metric option will cause all methods of that name to be instrumented.
 * # a method name as a map will instrument individual method signatures as instructed.
 * org.apache.sling.metrics.impl.dropwizard.DropwizardMetricsConfig:
 *   _monitor_class: true
 *   getConfig: timer
 *   load:
 *     (Ljava/lang/String;)V : timer
 *
 * </pre>
 */
public class DropwizardMetricsConfig {

    private static final String HELPER_CLASS = "helperClass";
    private static final String TYPE = "type";
    private static final String KEY_METHOD = "keyMethod";
    /**
     * If true, as a property of a class, the classbytecode will be dumped to the working directory.
     */
    private static final String DUMP_CLASS = "_dumpClass";
    private static final String ED_PERIOD_OPT = "period";
    private static final String ES_INSTANCE_ID_OPT = "instanceId";
    private static final String ES_CUSTOMER_ID_OPT = "customerId";
    private static final String ES_SERVERS_OPT = "servers";
    private static final String ES_FIELD_EXPANDERS = "customFieldExpanders";
    private static final String PERIOD_OPT = "period";
    private static final String CONSOLE_OPT = "console";
    private static final String JMX_OPT = "jmx";
    private static final String ELASTIC_SEARCH = "elastic_search";
    private static final String CSV_REPORTER = "csv";
    private static final String CSV_OUT_DIR = "dir";
    private static final String SLF4J_REPORTER = "slf4j";
    private static final String SLF4J_LOGGER_OPT = "logger";
    private static final String GRAPHITE_OPT = "graphite";
    private static final String SERVLET_OPT = "servlet";
    private static final String REPORTERS_CONFIG = "reporters";
    private static final String DEBUG_CONFIG = "debug";
    private static final String DUMP_CONFIG = "dump";
    private static final String DUMPFILE_OPT = "output";
    private static final String DUMP_INCLUDE = "include";
    private static final String DUMP_EXCLUDE = "exclude";
    private static final String MONITOR_OPT = "monitor";
    private static final String MONITOR_CLASS_OPT = "_monitor_class";
    private static final String GLOBAL_CONFIG = "global";
    private static final String METRICS_CONFIG_FILENAME = "metrics.yaml";
    private static final String METRICS_CONFIG_PROP = "metrics.config";
    private static final String TRUE_OPTION = "true";
    private static final String METER_OPTION = "meter";
    private static final String COUNTER_OPTION = "counter";
    private static final String API_COUNTER_OPTION = "apicounter";
    private static final String TIMER_OPTION = "timer";
    private static final String RETURNCOUNT_OPTION = "count_return";
    private static final String RETURNMETER_OPTION = "meter_return";
    private static final String VALID_OPTIONS = METER_OPTION+","+COUNTER_OPTION+","+TIMER_OPTION+","+API_COUNTER_OPTION;
//    private static final String VALID_TYPES = METER_OPTION+","+COUNTER_OPTION+","+API_COUNTER_OPTION+","+TIMER_OPTION+","+RETURNCOUNT_OPTION+","+RETURNMETER_OPTION;
    private Map<String, Object> configMap;
    private DropwizardMetricsFactory metricsFactory;
    private Stack<Closeable> reporters = new Stack<Closeable>();
    private LogServiceHolder logServiceHolder;
    private MetricsActivator activator;
    private MetricRegistry metricsRegistry = new MetricRegistry();
    private boolean monitorClasses;
    private FileWriter dumpFile;
    private Object lastClassname;
    private String lastName;
    private String lastDesc;
    private List<Pattern> dumpIncludes = new ArrayList<Pattern>();
    private List<Pattern> dumpExcludes = new ArrayList<Pattern>();

    public DropwizardMetricsConfig(@Nonnull MetricsActivator activator) {
        this.logServiceHolder = activator;
        this.activator = activator;
        String metricsConfigProperties = System.getProperty(METRICS_CONFIG_PROP, METRICS_CONFIG_FILENAME);
        load(metricsConfigProperties);
        monitorClasses = TRUE_OPTION.equals(getConfig(GLOBAL_CONFIG,MONITOR_OPT));
        configureDump();

        metricsFactory = new DropwizardMetricsFactory(metricsRegistry);
        MetricsUtil.setFactory(metricsFactory);

        createConsoleReporter();
        createJMXReporter();
        createOtherReporters();


    }

    private void configureDump() {
        if (configExists(GLOBAL_CONFIG,DUMP_CONFIG)) {
            try {
                logServiceHolder.info(" Dumping Classes to ",getConfig(GLOBAL_CONFIG, DUMP_CONFIG, DUMPFILE_OPT));
                dumpFile = new FileWriter(getConfig(GLOBAL_CONFIG, DUMP_CONFIG, DUMPFILE_OPT));
                dumpIncludes = getPatterns(getConfigObject(GLOBAL_CONFIG, DUMP_CONFIG, DUMP_INCLUDE));
                dumpExcludes = getPatterns(getConfigObject(GLOBAL_CONFIG, DUMP_CONFIG, DUMP_EXCLUDE));
            } catch (IOException e) {
                logServiceHolder.info("Cant open dumpfile ",getConfig(GLOBAL_CONFIG,DUMP_CONFIG,  DUMPFILE_OPT));
            }
        } else {
            logServiceHolder.info("Not Dumping Classes");

        }
    }

    @SuppressWarnings("unchecked")
    @Nonnull
    private List<Pattern> getPatterns(@Nullable Object configObject) {
        List<Pattern> patterns = new ArrayList<Pattern>();
        if (configObject instanceof List) {
            for (String p : (List<String>)configObject) {
                patterns.add(Pattern.compile(p));
            }
        }
        return patterns;
    }

    private String getConfigWithAlternatives(String first, Map<String, Set<String>> alternatives, String methodName, String desc, String ... path) {
        String[] p = new String[path.length+3];
        if ( path.length > 0) {
            System.arraycopy(path,0,p,3,path.length);
        }
        p[0] = first;
        p[1] = methodName;
        p[2] = desc;
        String s = getConfig(p);
        if ( s != null) {
            return s;
        }
        // the alternatives are interface names, but we also need to check that the
        // methods an desc are in the interface.
        for ( Entry<String, Set<String>> alt : alternatives.entrySet() ) {
            Set<String> methods = alt.getValue();
            if ( methods.contains(methodName+desc) ) {
                p[0] = alt.getKey();
                s = getConfig(p);
                logServiceHolder.debug("Found Method ",methodName+desc," in interface  ", alt.getKey(), " with methods ",methods," config object is ",s);
                if (s != null) {
                    return s;
                }
            }
        }
        return null;
    }

    @Nullable
    private String getConfig(@Nonnull String ... path) {
        Object o = getConfigObject(path);
        if ( o instanceof String) {
            return (String) o;
        }
        return null;
    }



    @SuppressWarnings("unchecked")
    @Nullable
    private Object getConfigObject(@Nonnull String ... path) {
        if (configMap == null) {
            return null;
        }
        Map<String, Object> cfg =  configMap;
        Object o = null;
        for(String part : path) {
            o = cfg.get(part);
            if (o instanceof Map) {
                cfg = (Map<String, Object>)o;
            } else {
                break;
            }
        }
        if ( o == null && path.length > 0) {
            // only consider the first path element.
            Map<String, Object> classPatterns = (Map<String, Object>)configMap.get("packages");
            if (classPatterns != null) {
                for (Entry<String, Object> e: classPatterns.entrySet()) {
                    if (path[0].startsWith(e.getKey())) {
                        return e.getValue();
                    }
                }
            }

        }
        return o;
    }

    private String getMatchedClassName(@Nonnull String className, @Nonnull Map<String, Set<String>> alternatives) {
        if (configMap == null) {
            return className;
        }
        if ( configMap.containsKey(className)) {
            return className;
        }
        for (Entry<String, Set<String>> alternative: alternatives.entrySet()) {
            if (configMap.containsKey(alternative.getKey())) {
                return alternative.getKey();
            }
        }
        Map<String, Object> classPatterns = (Map<String, Object>)configMap.get("packages");
        if (classPatterns != null) {
            for (Entry<String, Object> e: classPatterns.entrySet()) {
                if (className.startsWith(e.getKey())) {
                        return className;
                }
            }
            for (Entry<String, Set<String>> alternative: alternatives.entrySet()) {
                for (Entry<String, Object> e: classPatterns.entrySet()) {
                    if (alternative.getKey().startsWith(e.getKey())) {
                            return alternative.getKey();
                    }
                }
            }
        }
        return className;
    }

    @Nullable
    private boolean configExists(@Nonnull String ... path) {
        return getConfigObject(path) != null;
    }


    private void createOtherReporters() {
        if (configExists(GLOBAL_CONFIG,REPORTERS_CONFIG, CSV_REPORTER)) {
            String outputdir = (String) getConfigObject(GLOBAL_CONFIG,REPORTERS_CONFIG, CSV_REPORTER, CSV_OUT_DIR);
            int reportingPeriod = 60;
            if (configExists(GLOBAL_CONFIG,REPORTERS_CONFIG,ELASTIC_SEARCH, PERIOD_OPT)) {
                reportingPeriod = Integer.parseInt(getConfig(GLOBAL_CONFIG,REPORTERS_CONFIG, CSV_REPORTER, PERIOD_OPT));
            }

            File output = new File(outputdir);
            output.mkdirs();
            CsvReporter reporter = CsvReporter.forRegistry(metricsRegistry)
                    .formatFor(Locale.US.US)
                            .convertRatesTo(TimeUnit.SECONDS)
                            .convertDurationsTo(TimeUnit.MILLISECONDS)
                            .build(output);
            reporter.start(reportingPeriod, TimeUnit.SECONDS);
            reporters.add(reporter);
            logServiceHolder.info("Registered CSV Reporter, in ", output.getAbsoluteFile()," reporting interval ", reportingPeriod);

        }
        if (configExists(GLOBAL_CONFIG,REPORTERS_CONFIG, SLF4J_REPORTER)) {
            String logger = "org.apache.sling.metrics.status";
            if (configExists(GLOBAL_CONFIG, REPORTERS_CONFIG, SLF4J_REPORTER, SLF4J_LOGGER_OPT)) {
                logger = (String)getConfigObject(GLOBAL_CONFIG, REPORTERS_CONFIG, SLF4J_REPORTER, SLF4J_LOGGER_OPT);
            }
            int reportingPeriod = 60;
            if (configExists(GLOBAL_CONFIG, REPORTERS_CONFIG, ELASTIC_SEARCH, PERIOD_OPT)) {
                reportingPeriod = Integer.parseInt(getConfig(GLOBAL_CONFIG, REPORTERS_CONFIG, SLF4J_REPORTER, PERIOD_OPT));
            }
            Slf4jReporter reporter = Slf4jReporter.forRegistry(metricsRegistry)
                    .outputTo(LoggerFactory.getLogger(logger))
                    .convertRatesTo(TimeUnit.SECONDS)
                    .convertDurationsTo(TimeUnit.MILLISECONDS)
                    .build();
            reporter.start(reportingPeriod, TimeUnit.SECONDS);
            reporters.add(reporter);
            logServiceHolder.info("Registered SLF4J Reporter, to logger ", logger, " reporting interval ", reportingPeriod);
        }
        if (configExists(GLOBAL_CONFIG,REPORTERS_CONFIG,SERVLET_OPT)) {
        }
        if (configExists(GLOBAL_CONFIG,REPORTERS_CONFIG,GRAPHITE_OPT)) {
            // TODO
        }
        if (configExists(GLOBAL_CONFIG,REPORTERS_CONFIG,ELASTIC_SEARCH)) {
            try {
                Builder b =  ElasticSearchReporter.fromRegistry(metricsRegistry);
                @SuppressWarnings("unchecked")
                List<String> hosts = (List<String>) getConfigObject(GLOBAL_CONFIG,REPORTERS_CONFIG,ELASTIC_SEARCH, ES_SERVERS_OPT);
                for (String h : hosts) {
                    URL u = new URL(h);
                    b.addServerUrl(u);
                }
                if ( configExists(GLOBAL_CONFIG,REPORTERS_CONFIG,ELASTIC_SEARCH, ES_CUSTOMER_ID_OPT)) {
                    b.customerId(getConfig(GLOBAL_CONFIG,REPORTERS_CONFIG,ELASTIC_SEARCH, ES_CUSTOMER_ID_OPT));
                }
                if ( configExists(GLOBAL_CONFIG,REPORTERS_CONFIG,ELASTIC_SEARCH, ES_INSTANCE_ID_OPT)) {
                    b.instanceId(getConfig(GLOBAL_CONFIG,REPORTERS_CONFIG,ELASTIC_SEARCH, ES_INSTANCE_ID_OPT));
                }
                if ( configExists(GLOBAL_CONFIG,REPORTERS_CONFIG,ELASTIC_SEARCH, ES_FIELD_EXPANDERS)) {
					@SuppressWarnings("unchecked")
					List<Object> expandersList = (List<Object>) getConfigObject(GLOBAL_CONFIG, REPORTERS_CONFIG,
							ELASTIC_SEARCH, ES_FIELD_EXPANDERS);
					if (expandersList != null) {
						for (Object obj : expandersList) {
							if (obj instanceof Map) {
								@SuppressWarnings("unchecked")
								Map<String, String> customExpander = (Map<String, String>) obj;
								b.addCustomFieldExpander(customExpander.get("pattern"),
										customExpander.get("helperClass"));
							}
						}

					}
                }
                b.withLogServiceHolder(logServiceHolder);
                b.withMetricsActivator(activator);
                ElasticSearchReporter r = b.build();
                int reportingPeriod = 60;
                if (configExists(GLOBAL_CONFIG,REPORTERS_CONFIG,ELASTIC_SEARCH, ED_PERIOD_OPT)) {
                    reportingPeriod = Integer.parseInt(getConfig(GLOBAL_CONFIG,REPORTERS_CONFIG,ELASTIC_SEARCH, ED_PERIOD_OPT));
                }
                r.start(reportingPeriod, TimeUnit.SECONDS);
                reporters.add(r);
                logServiceHolder.info("Registered Elastic Search Reporter, reporting interval ",reportingPeriod);
            } catch (Exception e) {
                logServiceHolder.error( "Unable to create elastic serach reportoer cause:",e);
            }
        }
    }

    private void createJMXReporter() {
        if (configExists(GLOBAL_CONFIG,REPORTERS_CONFIG,JMX_OPT)) {
            JmxReporter jmxReporter = JmxReporter.forRegistry(metricsRegistry).build();
            jmxReporter.start();
            reporters.push(jmxReporter);
            logServiceHolder.info("Registered JMX reporter");
        }
    }

    private void createConsoleReporter() {
        if (configExists(GLOBAL_CONFIG,REPORTERS_CONFIG,CONSOLE_OPT)) {
            int t = 30;
            try {
                t = Integer.parseInt(getConfig(GLOBAL_CONFIG,REPORTERS_CONFIG,CONSOLE_OPT,PERIOD_OPT));
                if ( t < 0) {
                    t = 30;
                }
            } catch (NumberFormatException e) {
                logServiceHolder.warn("Console period not recognised, useing default of 30 seconds",e);
            }
            ConsoleReporter reporter = ConsoleReporter.forRegistry(metricsRegistry)
                    .convertRatesTo(TimeUnit.SECONDS)
                    .convertDurationsTo(TimeUnit.MILLISECONDS)
                    .build();
             reporter.start(t, TimeUnit.SECONDS);
             reporters.push(reporter);
        }
    }

    @Nonnull
    public DropwizardMetricsFactory getMetricsFactory() {
        return metricsFactory;
    }

    @Nonnull
    public MetricRegistry getRegistry() {
        return metricsRegistry;
    }


    @SuppressWarnings("unchecked")
    private void load(@Nonnull String metricsConfigYaml) {
        try {
            YamlReader yamlReader = new YamlReader(new FileReader(metricsConfigYaml));
            configMap = (Map<String, Object>) yamlReader.read();
            yamlReader.close();
        } catch (Exception e) {
            logServiceHolder.error("Unable to read ",metricsConfigYaml," cause: ",e.getMessage());
            e.printStackTrace();
        }
        logServiceHolder.info("Metrics Config :",configMap);
    }
    public void close() {
        MetricsUtil.setFactory(null);
        if ( dumpFile != null) {
            try {
                dumpFile.close();
            } catch (IOException e) {
                logServiceHolder.debug(e);
            }
        }

        for (Closeable c : reporters) {
            try {
                c.close();
            } catch (IOException e) {
                logServiceHolder.debug(e);
            }
        }
    }
    public boolean addMethodTimer(@Nonnull String className, Map<String, Set<String>> ancestors, @Nonnull String name, @Nonnull String desc) {
        if (TRUE_OPTION.equals(getConfig(className,MONITOR_CLASS_OPT))) {
            logServiceHolder.info("Checking Method: ",className,".",name,desc);
        }
        dumpConfig(className, ancestors, name, desc);
        return TIMER_OPTION.equals(getConfigWithAlternatives(className, ancestors, name, desc)) || TIMER_OPTION.equals(getConfigWithAlternatives(className, ancestors, name, desc));
    }


    private void dumpConfig(@Nonnull String className, @Nonnull Map<String, Set<String>> ancestors, @Nonnull String name, @Nonnull String desc) {
        if(dumpFile != null) {
            try {
                if (!className.equals(lastClassname)) {
                   dumpFile.write(className);
                   dumpFile.write(":\n");
                   dumpFile.write("  ancestors:\n");
                    for (Entry<String, Set<String>> a: ancestors.entrySet()) {
                    dumpFile.write("    - ");
                    dumpFile.write(a.getKey());
                    dumpFile.write("\n");
                   }

                   lastClassname = className;
                   lastName = "---";
                   lastDesc = "---";
                }
                if (!name.equals(lastName)) {
                    dumpFile.write("  ");
                    dumpFile.write(name);
                    dumpFile.write(":\n");
                    lastName = name;
                    lastDesc = "---";
                }
                if (!desc.equals(lastDesc)) {
                    dumpFile.write("     ");
                    dumpFile.write(desc);
                    dumpFile.write(": ");
                    dumpFile.write(TIMER_OPTION);
                    dumpFile.write("\n");
                    lastDesc = desc;
                }
            } catch (IOException e) {
                logServiceHolder.warn("Unable to dump class config",e);
            }
        }
    }

    private boolean includeInDump(String className, Map<String, Set<String>> ancestors) {
        boolean include = true;
        if (dumpFile == null) {
            return false;
        }
        for(Pattern p : dumpIncludes) {
            include = false;
            if (p.matcher(className).matches()) {
                include = true;
                break;
            }
            for (Entry<String, Set<String>> a : ancestors.entrySet()) {
                if (p.matcher(a.getKey()).matches()) {
                    include = true;
                    break;
                }
            }
            if ( include ) {
                break;
            }
        }
        for (Pattern p : dumpExcludes) {
            if (p.matcher(className).matches()) {
                include = false;
                break;
            }

        }
        return include;
    }

    public boolean addCount(@Nonnull String className, Map<String, Set<String>> ancestors, @Nonnull String name, @Nonnull String desc) {
        return COUNTER_OPTION.equals(getConfigWithAlternatives(className, ancestors, name, desc)) || COUNTER_OPTION.equals(getConfigWithAlternatives(className, ancestors, name, desc));
    }

    public boolean addAPITimerCount(@Nonnull String className, Map<String, Set<String>> ancestors, @Nonnull String name, @Nonnull String desc) {
        return API_COUNTER_OPTION.equals(getConfigWithAlternatives(className, ancestors, name, desc)) || API_COUNTER_OPTION.equals(getConfigWithAlternatives(className, ancestors, name, desc));
    }

    public boolean addMark(@Nonnull String className, Map<String, Set<String>> ancestors, @Nonnull String name, @Nonnull String desc) {
        return METER_OPTION.equals(getConfigWithAlternatives(className, ancestors, name, desc)) || METER_OPTION.equals(getConfigWithAlternatives(className, ancestors, name, desc));
    }
    // this method might not work with interfaces,
    public boolean addReturnCount(@Nonnull String className, Map<String, Set<String>> ancestors, @Nonnull String name, @Nonnull String desc) {
        return RETURNCOUNT_OPTION.equals(getConfigWithAlternatives(className, ancestors, name, "", TYPE)) || RETURNCOUNT_OPTION.equals(getConfigWithAlternatives(className, ancestors, name, desc, TYPE));
    }
    // this method might not work with interfaces,
    public boolean addReturnMark(@Nonnull String className, Map<String, Set<String>> ancestors, @Nonnull String name, @Nonnull String desc) {
        return RETURNMETER_OPTION.equals(getConfigWithAlternatives(className, ancestors, name, "", TYPE)) || RETURNMETER_OPTION.equals(getConfigWithAlternatives(className, ancestors, name, desc, TYPE));
    }

    @Nullable
    public String getReturnKeyMethod(@Nonnull String className, Map<String, Set<String>> ancestors, @Nonnull String name, @Nonnull String desc) {
        String methodName = getConfigWithAlternatives(className, ancestors, name, desc, KEY_METHOD);
        if (methodName == null) {
            methodName = getConfigWithAlternatives(className, ancestors, name, "", KEY_METHOD);
        }
        return methodName;
    }

    public String getHelperClassName(String className, Map<String, Set<String>> ancestors, String name, String desc) {
        String methodName = getConfigWithAlternatives(className, ancestors, name, desc, HELPER_CLASS);
        if (methodName == null) {
            methodName = getConfigWithAlternatives(className, ancestors, name, "", HELPER_CLASS);
        }
        return methodName;
    }


    @Nonnull
    public String getMetricName(@Nonnull String className, @Nonnull Map<String, Set<String>> ancestors, @Nonnull String name, @Nonnull String desc) {
        String option = getConfig(className, name);
        String matchedClassName = getMatchedClassName(className, ancestors);
        if ( TIMER_OPTION.equals(option) || COUNTER_OPTION.equals(option) || METER_OPTION.equals(option) || API_COUNTER_OPTION.equals(option)) {
            return DropwizardMetricsFactory.name(matchedClassName, name);
        } else {
            return DropwizardMetricsFactory.name(matchedClassName, name+desc);
        }
    }



    public boolean addMetrics(@Nonnull String className, Map<String, Set<String>> ancestors) {
        if (monitorClasses) {
            logServiceHolder.info("Loading Class: ",className);
        }
        if (includeInDump(className, ancestors)) {
            // dump for all classes except asm classes, as if you try and instrument asm classes, you end up in recursion.
            logServiceHolder.info("Dumping Class: ",className);
            return true;
        }
        if (configExists(className)) {
            return true;
        }
        for(Entry<String, Set<String>> a : ancestors.entrySet()) {
            if (configExists(a.getKey())) {
                return true;
            }
        }
        return false;
    }

    public boolean dumpClass(String className) {
        return TRUE_OPTION.equals(getConfig(className,DUMP_CLASS));
    }

    @SuppressWarnings("unchecked")
    public void checkMissedInstructions(String className, List<String[]> methods) {
        Object o = getConfigObject(className);
        if ( o instanceof Map) {
            Map<String, Object> config = (Map<String, Object>) o;
            for(Entry<String, Object> classMethods : config.entrySet()) {
                if (!classMethods.getKey().startsWith("_")) {
                    if (classMethods.getValue() instanceof Map) {
                        for(Entry<String, Object> descs : ((Map<String, Object>) classMethods.getValue()).entrySet()) {
                            logConfigNotUsed(className, classMethods.getKey(), descs.getKey(), descs.getValue(), methods);
                        }
                    } else {
                        logConfigNotUsed(className, classMethods.getKey(), String.valueOf(classMethods.getValue()), methods);
                    }
                }
            }
        }
    }

    private void logConfigNotUsed(String className, String methodName, String option, List<String[]> methods) {
        if (!VALID_OPTIONS.contains(option) ) {
            logServiceHolder.warn("Config Option not valid for ",className," ",methodName," ",option," should be one of ",VALID_OPTIONS);
        }
        for(String[] m : methods) {
            if ( methodName.equals(m[0])) {
                break;
            }
        }
        logServiceHolder.warn("Unused Config for ",className," ",methodName," "+option);
    }

    private void logConfigNotUsed(String className, String methodName, String description, Object options, List<String[]> methods) {
        if (options instanceof String) {
            if (!VALID_OPTIONS.contains((String) options) ) {
                logServiceHolder.info("Config Option not valid for ",className," ",methodName," ",options," should be one of ",VALID_OPTIONS);
            }
        }
        for(String[] m : methods) {
            if ( methodName.equals(m[0]) && description.equals(m[1])) {
                break;
            }
        }
        logServiceHolder.warn( "Unused Config for ",className," ",methodName," ",options);
    }

    public boolean debugEnabled() {
        return TRUE_OPTION.equals(getConfig(GLOBAL_CONFIG,DEBUG_CONFIG));
    }
}
