/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.sling.metrics.impl;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.annotation.Nonnull;

import org.apache.sling.metrics.api.LogServiceHolder;
import org.apache.sling.metrics.impl.dropwizard.DropwizardMetricsConfig;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

/**
 * This class implements an ASM ClassVisitor which puts the appropriate
 * ThreadContextClassloader calls around applicable method invocations. It does
 * the actual bytecode weaving.
 */
public class MetricsClassVisitor extends ClassVisitor implements Opcodes {

    private Map<String, Set<String>> ancestors;
    private DropwizardMetricsConfig metricsConfig;

    private boolean woven;

    private String className;

    private LogServiceHolder logServiceHolder;

    private List<String[]> methods = new ArrayList<String[]>();

    public MetricsClassVisitor(@Nonnull ClassVisitor cv, @Nonnull String className,
                               @Nonnull Map<String, Set<String>> ancestors,
            @Nonnull DropwizardMetricsConfig metricsConfig,
            @Nonnull LogServiceHolder logServiceHolder) {
        super(Opcodes.ASM4, cv);
        this.className = className;
        this.ancestors = ancestors;
        this.metricsConfig = metricsConfig;
        this.logServiceHolder = logServiceHolder;
        logServiceHolder.debug("Visiting class ",className," ",String.valueOf(ancestors.keySet()));
    }


    public boolean isWoven() {
        return woven;
    }

    @Override
    @Nonnull
    public MethodVisitor visitMethod(int access, @Nonnull String name, @Nonnull String desc,
            String signature, String[] exceptions) {
        try {
            methods.add(new String[] { name, desc });
            if (metricsConfig.addMethodTimer(className, ancestors, name, desc)) {
                String metricName = metricsConfig.getMetricName(className, ancestors, name, desc);
                logServiceHolder.info("Adding Metrics ", metricName, " to method ", className, " ", name);
                MethodVisitor mv = super.visitMethod(access, name, desc, signature, exceptions);
                woven = true;
                return new TimerAdapter(mv, access, name, desc, metricName);
            } else if (metricsConfig.addCount(className, ancestors, name, desc)) {
                String metricName = metricsConfig.getMetricName(className, ancestors, name, desc);
                logServiceHolder.info("Adding Metrics ", metricName, "to method ", className, " ", name);
                MethodVisitor mv = super.visitMethod(access, name, desc, signature, exceptions);
                woven = true;
                return new VoidAdapter(mv, access, name, desc, metricName, "count");
            } else if (metricsConfig.addAPITimerCount(className, ancestors, name, desc)) {
                String metricName = metricsConfig.getMetricName(className, ancestors, name, desc);
                logServiceHolder.info("Adding Metrics ", metricName, "to method ", className, " ", name);
                MethodVisitor mv = super.visitMethod(access, name, desc, signature, exceptions);
                woven = true;
                return new APITimerCounterAdapter(mv, access, name, desc, metricName);
            } else if (metricsConfig.addMark(className, ancestors, name, desc)) {
                String metricName = metricsConfig.getMetricName(className, ancestors, name, desc);
                logServiceHolder.info("Adding Metrics ", metricName, "to method ", className, " ", name);
                MethodVisitor mv = super.visitMethod(access, name, desc, signature, exceptions);
                woven = true;
                return new VoidAdapter(mv, access, name, desc, metricName, "mark");
            } else if (metricsConfig.addReturnCount(className, ancestors, name, desc)) {
                String metricName = metricsConfig.getMetricName(className, ancestors, name, desc);
                logServiceHolder.info("Adding Metrics ", metricName, "to method ", className, " ", name);
                MethodVisitor mv = super.visitMethod(access, name, desc, signature, exceptions);
                woven = true;
                return new ReturnAdapter(mv, access, name, desc, metricName,
                    metricsConfig.getReturnKeyMethod(className, ancestors, name, desc),
                    metricsConfig.getHelperClassName(className, ancestors, name, desc), false);
            } else if (metricsConfig.addReturnMark(className, ancestors, name, desc)) {
                String metricName = metricsConfig.getMetricName(className, ancestors, name, desc);
                logServiceHolder.info("Adding Metrics ", metricName, "to method ", className, " ", name);
                MethodVisitor mv = super.visitMethod(access, name, desc, signature, exceptions);
                woven = true;
                return new ReturnAdapter(mv, access, name, desc, metricName,
                    metricsConfig.getReturnKeyMethod(className, ancestors, name, desc),
                    metricsConfig.getHelperClassName(className, ancestors, name, desc), true);
            }
            logServiceHolder.debug("Not Adding Metrics to method ", className, " ", name);
        } catch (Exception e) {
            logServiceHolder.error("Failed Not Adding Metrics to method ", className, " ", e);
        }
        return super.visitMethod(access, name, desc, signature, exceptions);
    }

    public boolean additionalImportRequired() {
        return woven;
    }

    public void finish() {
        metricsConfig.checkMissedInstructions(className, methods);
    }
}
