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
import javax.annotation.Nullable;

import org.apache.sling.metrics.api.MetricsUtil;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.hooks.weaving.WeavingHook;
import org.osgi.framework.hooks.weaving.WovenClass;
import org.osgi.service.log.LogService;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public class MetricsWeavingHook implements WeavingHook {
    private String addedImport;
    private MetricsActivator activator;
    private String bundleSymbolicName;

    MetricsWeavingHook(@Nonnull BundleContext context, @Nonnull MetricsActivator dwActivator) {
        activator = dwActivator;

        Bundle b = context.getBundle();
        String bver = b.getVersion().toString();
        bundleSymbolicName = b.getSymbolicName();

        addedImport = MetricsUtil.class.getPackage().getName() +
            ";bundle-symbolic-name=" + bundleSymbolicName +
            ";bundle-version=" + bver;
    }

	@Override
	public void weave(@Nonnull WovenClass wovenClass) {
	    if ( foreignBundle(wovenClass) ) {
            ClassReader cr = new ClassReader(wovenClass.getBytes());
            String[] ancestors = toStringArray(loadAncestors(cr, new HashSet<String>()));
            if ( activator.getMetricsConfig().addMetrics(wovenClass.getClassName(), ancestors) ) {
                activator.log(LogService.LOG_DEBUG, "Adding Metrics to class " + wovenClass.getClassName());
                try {
                    if ((cr.getAccess() & Opcodes.ACC_INTERFACE) != Opcodes.ACC_INTERFACE) {
                        ClassWriter cw = new OSGiFriendlyClassWriter(ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES,
                                wovenClass.getBundleWiring().getClassLoader(), activator, wovenClass.getClassName(), activator.getMetricsConfig().dumpClass(wovenClass.getClassName()));

                        MetricsClassVisitor mcv = new MetricsClassVisitor(cw, wovenClass.getClassName(),
                                ancestors,
                                activator.getMetricsConfig(), activator);
                        cr.accept(mcv, ClassReader.SKIP_FRAMES);
                        if (mcv.isWoven()) {
                            wovenClass.setBytes(cw.toByteArray());
                            if (mcv.additionalImportRequired())
                                wovenClass.getDynamicImports().add(addedImport);
                        }
                        mcv.finish();
                    } else {
                        activator.log(LogService.LOG_DEBUG, "Not weaving interface, no non abstract methods on " + wovenClass.getClassName());
                    }

                } catch (Exception e) {
                    activator.log(LogService.LOG_DEBUG, "Unable to weave class " + wovenClass.getClassName() + " cause " + e.getMessage());
                }
            }
	        
	    }
	}

    private String[] toStringArray(Set<String> strings) {
        return strings.toArray(new String[strings.size()]);
    }

    @Nonnull
    private Set<String> loadAncestors(@Nullable ClassReader cr, @Nonnull Set<String> ancestors) {
        if ( cr != null) {
            addAll(ancestors, cr.getInterfaces());
            //addAll(ancestors, cr.getSuperName());
        }
        return ancestors;
    }

    private void addAll(Set<String> ancestors, String ... names) {
        for (String name : names) {
            if (!"java/lang/Object".equals(name)) {
                String javaName = name.replace('/', '.');
                if (!ancestors.contains(javaName)) {
                    ancestors.add(javaName);
                    // Not certain if we need to load ancestors or not, assume we dont. loadAncestors(readClass(iface), ancestors);
                }
            }
        }
    }


    /**
     * Get the classreader for the class, by loading the raw byte[]. This may be expensive.
     * @param name
     * @return
     */
    @Nullable
    private ClassReader readClass(@Nonnull String name) {
        if (!"java/lang/Object".equals(name)) {
            InputStream in = null;
            try {
                in = this.getClass().getClassLoader().getResourceAsStream(name + ".class");
                return new ClassReader(in);
            } catch (Exception ex) {
                activator.log(LogService.LOG_WARNING,"Unable to load bytes for class "+name);
            } finally {
                if ( in != null ) {
                    try {
                        in.close();
                    } catch (Exception e) {
                        activator.log(LogService.LOG_DEBUG,"Unable close stream for class "+name);
                    }
                }
            }
        }
        return null;
    }

    private boolean foreignBundle(WovenClass wovenClass) {
        return !bundleSymbolicName.equals(wovenClass.getBundleWiring().getBundle().getSymbolicName());
    }
}