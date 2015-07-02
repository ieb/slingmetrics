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
import java.io.FileInputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.HashMap;
import java.util.Map.Entry;
import java.util.Properties;
import java.util.Stack;
import java.util.concurrent.TimeUnit;

import javax.annotation.Nonnull;

import org.apache.sling.metrics.api.MetricsUtil;
import org.apache.sling.metrics.impl.MetricsActivator;
import org.osgi.service.log.LogService;

import com.codahale.metrics.ConsoleReporter;
import com.codahale.metrics.JmxReporter;
import com.codahale.metrics.MetricRegistry;

public class DropwizardMetricsConfig {

    private static final String _OUTPUT_KEY = "_output";
    private PrintWriter output;
    private HashMap<String, String> config;
    private DropwizardMetricsFactory metricsFactory;
    private Stack<Closeable> reporters = new Stack<Closeable>();
    private MetricsActivator activator;
    private MetricRegistry metricsRegistry = new MetricRegistry();

    public DropwizardMetricsConfig(@Nonnull MetricsActivator activator) {
        this.activator = activator;
        String metricsConfigProperties = System.getProperty("metrics.config", "metrics.config");
        load(metricsConfigProperties);
        metricsFactory = new DropwizardMetricsFactory(metricsRegistry);
        MetricsUtil.setFactory(metricsFactory);
        
        createConsoleReporter();
        createJMXReporter();
        createOtherReporters();

        
    }
    
    private void createOtherReporters() {
        if (config.containsKey("_reporter_servlet")) {
            // TODO
        }
        if (config.containsKey("_reporter_graphite")) {
            // TODO
        }
        if (config.containsKey("_reporter_kibana")) {
            // TODO
        }
    }

    private void createJMXReporter() {
        if (config.containsKey("_reporter_jmx")) {
            JmxReporter jmxReporter = JmxReporter.forRegistry(metricsRegistry).build();
            jmxReporter.start();
            reporters.push(jmxReporter);
        }
    }

    private void createConsoleReporter() {
        if (config.containsKey("_reporter_console")) {
            int t = 30;
            try {
                t = Integer.parseInt(config.get("_reporter_console"));
                if ( t < 0) {
                    t = 30;
                }
            } catch (NumberFormatException e) {
                activator.log(LogService.LOG_WARNING, "Console period not recognised, useing default of 30 seconds");
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
    
    private void load(@Nonnull String metricsConfigProperties) {
        config = new HashMap<String, String>();
        try {
            Properties p = new Properties();
            FileInputStream f = new FileInputStream(metricsConfigProperties);
            p.load(f);
            f.close();
            if ( p.containsKey(_OUTPUT_KEY)) {
                output = new PrintWriter((String)p.get(_OUTPUT_KEY));
            } else {
                output = null;
                MetricsUtil.setWriter(null);            
            }
            
            for( Entry<Object, Object> e : p.entrySet()) {
                config.put((String)e.getKey(),  (String) e.getValue());
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        
        System.err.println("Metrics Config "+config);
        
        
    }
    public void close() {
        MetricsUtil.setWriter(null);
        MetricsUtil.setFactory(null);
        if (output != null) {
            output.close();
            output = null;
        }
        for (Closeable c : reporters) {
            try {
                c.close();
            } catch (IOException e) {
                activator.log(LogService.LOG_DEBUG, e.getMessage());
            }
        }
    }
    public boolean addMethodTimer(@Nonnull  String className, @Nonnull String name) {
        return "timer".equals(config.get(className + "." + name));
    }

    public boolean addCount(String className, String name) {
        return "count".equals(config.get(className + "." + name));
    }

    public boolean addMark(String className, String name) {
        return "mark".equals(config.get(className + "." + name));
    }

    
    @Nonnull 
    public String getMetricName(@Nonnull String className, @Nonnull String name) {
        return DropwizardMetricsFactory.name(className, name);
    }

    public boolean addMetrics(@Nonnull String className) {
        return "true".equals(config.get(className));
    }

 
}
