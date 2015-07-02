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

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import javax.annotation.Nonnull;

import org.apache.sling.metrics.api.MetricsFactory;
import org.apache.sling.metrics.impl.dropwizard.DropwizardMetricsConfig;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceReference;
import org.osgi.framework.ServiceRegistration;
import org.osgi.framework.hooks.weaving.WeavingHook;
import org.osgi.service.log.LogService;
import org.osgi.util.tracker.ServiceTracker;

public class MetricsActivator implements BundleActivator {

    private ServiceRegistration<?> weavingHookService;
    private ServiceRegistration<?> metricsFactoryService;
    private DropwizardMetricsConfig metricsConfig;
    private List<LogService> logServices = new CopyOnWriteArrayList<LogService>();
    private LogServiceTracker logServiceTracker;


    @Override
    public synchronized void start(BundleContext context) throws Exception {
        logServiceTracker = new LogServiceTracker(context);
        logServiceTracker.open();
        metricsConfig = new DropwizardMetricsConfig(this);
        WeavingHook weavingHook = new MetricsWeavingHook(context, this);
        weavingHookService = context.registerService(WeavingHook.class.getName(), weavingHook, null);
        metricsFactoryService = context.registerService(MetricsFactory.class.getName(), metricsConfig.getMetricsFactory(), null);
    }

    @Override
    public synchronized void stop(BundleContext context) throws Exception {
        weavingHookService.unregister();
        metricsFactoryService.unregister();
        metricsConfig.close();
        logServiceTracker.close();
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

}
