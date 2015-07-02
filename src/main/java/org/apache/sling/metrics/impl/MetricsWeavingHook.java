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

import org.apache.sling.metrics.api.MetricsUtil;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.hooks.weaving.WeavingHook;
import org.osgi.framework.hooks.weaving.WovenClass;
import org.osgi.service.log.LogService;

public class MetricsWeavingHook implements WeavingHook {
    private final String addedImport;
    private final MetricsActivator activator;

    MetricsWeavingHook(@Nonnull BundleContext context, @Nonnull MetricsActivator dwActivator) {
        activator = dwActivator;

        Bundle b = context.getBundle();
        String bver = b.getVersion().toString();
        String bsn = b.getSymbolicName();

        addedImport = MetricsUtil.class.getPackage().getName() +
            ";bundle-symbolic-name=" + bsn +
            ";bundle-version=" + bver;
    }

	@Override
	public void weave(@Nonnull WovenClass wovenClass) {
	    if ( activator.getMetricsConfig().addMetrics(wovenClass.getClassName())) {
	        activator.log(LogService.LOG_DEBUG, "Adding Metrics to class "+wovenClass.getClassName());
            ClassReader cr = new ClassReader(wovenClass.getBytes());
            ClassWriter cw = new OSGiFriendlyClassWriter(ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES,
                wovenClass.getBundleWiring().getClassLoader());
	        MetricsClassVisitor mcv = new MetricsClassVisitor(cw, wovenClass.getClassName(), activator.getMetricsConfig(), activator);
	        cr.accept(mcv, ClassReader.SKIP_FRAMES);
	        if (mcv.isWoven()) {
    	        wovenClass.setBytes(cw.toByteArray());
    	        if (mcv.additionalImportRequired())
    	            wovenClass.getDynamicImports().add(addedImport);
	        }
	    }
	}
}