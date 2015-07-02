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

import javax.annotation.Nonnull;

import org.apache.sling.metrics.impl.dropwizard.DropwizardMetricsConfig;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.osgi.service.log.LogService;

/**
 * This class implements an ASM ClassVisitor which puts the appropriate ThreadContextClassloader
 * calls around applicable method invocations. It does the actual bytecode weaving.
 */
public class MetricsClassVisitor extends ClassVisitor implements Opcodes {


    private DropwizardMetricsConfig metricsConfig;
    private boolean woven;
    private String className;
    private MetricsActivator activator;


    public MetricsClassVisitor(@Nonnull ClassVisitor cv, @Nonnull String className, @Nonnull DropwizardMetricsConfig metricsConfig, @Nonnull MetricsActivator activator) {
        super(Opcodes.ASM4, cv);
        this.className = className;
        this.metricsConfig = metricsConfig;
        this.activator = activator;
    }

    public boolean isWoven() {
        return woven;
    }

    @Override
    @Nonnull
    public MethodVisitor visitMethod(int access, @Nonnull String name, @Nonnull String desc,
            String signature, String[] exceptions) {
        if ( metricsConfig.addMethodTimer(className, name) ) {
            activator.log(LogService.LOG_INFO, "Adding Metrics to method "+className+" "+name);
            MethodVisitor mv = super.visitMethod(access, name, desc, signature, exceptions);
            woven = true;
            return new TimerMethodVisitor(mv, access, name, desc, metricsConfig.getMetricName(className, name));            
        } else if (metricsConfig.addCount(className, name)) {
            activator.log(LogService.LOG_INFO, "Adding Metrics to method "+className+" "+name);
            MethodVisitor mv = super.visitMethod(access, name, desc, signature, exceptions);
            woven = true;
            return new VoidMethodVisitor(mv, access, name, desc, metricsConfig.getMetricName(className, name), "count");                        
        } else if (metricsConfig.addMark(className, name)) {
            activator.log(LogService.LOG_INFO, "Adding Metrics to method "+className+" "+name);
            MethodVisitor mv = super.visitMethod(access, name, desc, signature, exceptions);
            woven = true;
            return new VoidMethodVisitor(mv, access, name, desc, metricsConfig.getMetricName(className, name), "mark");                        
        }
        activator.log(LogService.LOG_INFO, "Not Adding Metrics to method "+className+" "+name);
        return super.visitMethod(access, name, desc, signature, exceptions);
    }



    public boolean additionalImportRequired() {
        return woven ;
    }
}