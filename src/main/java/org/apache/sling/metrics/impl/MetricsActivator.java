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
package org.apache.sling.metrics.impl;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import javax.annotation.Nonnull;

import org.apache.sling.metrics.api.CustomFieldExpander;
import org.apache.sling.metrics.api.LogServiceHolder;
import org.apache.sling.metrics.api.MetricsFactory;
import org.apache.sling.metrics.api.MetricsUtil;
import org.apache.sling.metrics.api.ReturnCapture;
import org.apache.sling.metrics.impl.dropwizard.DropwizardMetricsConfig;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceReference;
import org.osgi.framework.ServiceRegistration;
import org.osgi.framework.hooks.weaving.WeavingHook;
import org.osgi.service.log.LogService;
import org.osgi.util.tracker.ServiceTracker;

import com.codahale.metrics.MetricRegistry;

public class MetricsActivator implements BundleActivator, LogServiceHolder {

    private ServiceRegistration<?> weavingHookService;
    private ServiceRegistration<?> metricsFactoryService;
    private ServiceRegistration<?> metricsRegistryService;
    private DropwizardMetricsConfig metricsConfig;
    private List<LogService> logServices = new CopyOnWriteArrayList<LogService>();
    private List<CustomFieldExpander> customFieldExpanderServices = new CopyOnWriteArrayList<CustomFieldExpander>();
    private LogServiceTracker logServiceTracker;
    private CustomFieldExpanderServiceTracker customFieldExpanderServiceTracker;
    private boolean debugEnabled;


    public List<CustomFieldExpander> getCustomFieldExpanderServices() {
		return customFieldExpanderServices;
	}

	@Override
    public synchronized void start(BundleContext context) throws Exception {
        logServiceTracker = new LogServiceTracker(context);
        logServiceTracker.open();
        customFieldExpanderServiceTracker = new CustomFieldExpanderServiceTracker(context);
        customFieldExpanderServiceTracker.open();
        ReturnCapture.setLogServiceHolder(this);
        MetricsUtil.setLogServiceHolder(this);
        metricsConfig = new DropwizardMetricsConfig(this);
        debugEnabled = metricsConfig.debugEnabled();
        WeavingHook weavingHook = new MetricsWeavingHook(context, this);
        weavingHookService = context.registerService(WeavingHook.class.getName(), weavingHook, null);
        metricsFactoryService = context.registerService(MetricsFactory.class.getName(), metricsConfig.getMetricsFactory(), null);
        // this allows other bundles to implement reporters.
        metricsRegistryService = context.registerService(MetricRegistry.class.getName(), metricsConfig.getRegistry(), null);
    }

    @Override
    public synchronized void stop(BundleContext context) throws Exception {
        weavingHookService.unregister();
        metricsFactoryService.unregister();
        metricsRegistryService.unregister();
        metricsConfig.close();
        logServiceTracker.close();
        customFieldExpanderServiceTracker.close();
    }
    
    @Override
    public void debug(Object... message) {
        if (debugEnabled) {
            log(LogService.LOG_INFO, concat(message));
        }
    }

    @Override
    public void info(Object... message) {
        log(LogService.LOG_INFO, concat(message));
    }

    @Override
    public void warn(Object... message) {
        log(LogService.LOG_WARNING, concat(message));
    }

    @Override
    public void error(Object... message) {
        log(LogService.LOG_ERROR, concat(message));
    }

    private String concat(Object[] message) {
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        for(Object o : message) {
            if (o instanceof Throwable) {
                ((Throwable) o).printStackTrace(pw);
            } else {
                pw.append(String.valueOf(o));
            }
        }
        pw.flush();
        return sw.toString();
    }

    public void log(int level, @Nonnull String message) {
        synchronized (logServices) {
            if (logServices.size() == 0) {
                System.err.println("Metrics:"+message);                
            } else {
                for (LogService log : logServices) {
                    log.log(level, message);
                }
            }
        }
    }

    @Nonnull
    public DropwizardMetricsConfig getMetricsConfig() {
        return metricsConfig;
    }
    
    
    private class LogServiceTracker extends ServiceTracker {
        public LogServiceTracker(@Nonnull BundleContext context) {
            super(context, LogService.class.getName(), null);
        }

        public Object addingService(@SuppressWarnings("rawtypes") ServiceReference reference) {
            Object svc = super.addingService(reference);
            if (svc instanceof LogService)
                logServices.add((LogService) svc);
            return svc;
        }

        @Override
        public void removedService(@SuppressWarnings("rawtypes") ServiceReference reference, Object service) {
            logServices.remove(service);
        }
    }

    
    private class CustomFieldExpanderServiceTracker extends ServiceTracker {
        public CustomFieldExpanderServiceTracker(@Nonnull BundleContext context) {
            super(context, CustomFieldExpander.class.getName(), null);
        }

        public Object addingService(@SuppressWarnings("rawtypes") ServiceReference reference) {
            Object svc = super.addingService(reference);
            if (svc instanceof CustomFieldExpander)
                customFieldExpanderServices.add((CustomFieldExpander) svc);
            return svc;
        }

        @Override
        public void removedService(@SuppressWarnings("rawtypes") ServiceReference reference, Object service) {
            customFieldExpanderServices.remove(service);
        }
    }
}
