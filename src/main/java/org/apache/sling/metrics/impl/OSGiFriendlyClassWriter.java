/*
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

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import org.apache.sling.metrics.api.LogServiceHolder;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;

/**
 * We need to override ASM's default behaviour in
 * {@link #getCommonSuperClass(String, String)} so that it doesn't load classes
 * (which it was doing on the wrong {@link ClassLoader} anyway...) Taken from
 * the org.apache.aries.proxy.impl module. Taken from
 * https://svn.apache.org/repos
 * /asf/aries/trunk/spi-fly/spi-fly-dynamic-bundle/src
 * /main/java/org/apache/aries/spifly/dynamic/OSGiFriendlyClassWriter.java
 */
public final class OSGiFriendlyClassWriter extends ClassWriter {

    private static final String OBJECT_INTERNAL_NAME = "java/lang/Object";

    public static final Set<String> EXCLUDE_INTERFACE = new HashSet<String>();

    static {
        // exclude interfaces that would be chosen instead of java/lang/Object
        EXCLUDE_INTERFACE.add("java/io/Serializable");
        EXCLUDE_INTERFACE.add("java/lang/Cloneable");
    }

    private final ClassLoader loader;

    private String className;

    private boolean dumpClass;

    private Map<String, OsgiClass2> classes = new HashMap<String, OsgiClass2>();

    public LogServiceHolder logServiceHolder;

    public OSGiFriendlyClassWriter(ClassReader arg0, int arg1, ClassLoader loader,
            LogServiceHolder logServiceHolder) {
        super(arg0, arg1);
        this.loader = loader;
        this.logServiceHolder = logServiceHolder;
    }

    public OSGiFriendlyClassWriter(int arg0, ClassLoader loader, LogServiceHolder logServiceHolder,
            String className, boolean dumpClass) {
        super(arg0);
        this.loader = loader;
        this.logServiceHolder = logServiceHolder;
        this.className = className;
        this.dumpClass = dumpClass;
    }

    @Override
    public byte[] toByteArray() {
        byte[] b = super.toByteArray();
        if (dumpClass) {
            try {
                FileOutputStream fo = new FileOutputStream(className + ".class");
                fo.write(b);
                fo.close();
                logServiceHolder.info("Written ", b.length, " to ", className, ".class");
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return b;
    }

    /**
     * We provide an implementation that doesn't cause class loads to occur. It
     * may not be sufficient because it expects to find the common parent using
     * a single classloader, though in fact the common parent may only be
     * loadable by another bundle from which an intermediate class is loaded
     * precondition: arg0 and arg1 are not equal. (checked before this method is
     * called)
     */
    @Override
    protected final String getCommonSuperClass(String classA, String classB) {
        // If either is Object, then Object must be the answer
        classA = classA.replace('.', '/');
        classB = classB.replace('.', '/');
        if (classA.equals(OBJECT_INTERNAL_NAME) || classB.equals(OBJECT_INTERNAL_NAME)) {
            return OBJECT_INTERNAL_NAME;
        }
        OsgiClass2 oClassB = new OsgiClass2(classB, classB, loader, logServiceHolder, true);
        OsgiClass2 oClassA = oClassB.get(classA, classA);
        return oClassA.getCommonSuperClass(classA, classB);
    }

    public static class OsgiClass2 {
        private ClassLoader classLoader;

        private OsgiClass2 superClass;

        private OsgiClass2[] interfaceClasses;

        private String initialClass;

        private Map<String, OsgiClass2> common;

        private Set<String> shared;

        private boolean breakOnShared;

        private LogServiceHolder logServiceHolder;

        public OsgiClass2(String initialClass, String className, ClassLoader loader, LogServiceHolder logServiceHolder,
                boolean breakOnShared) {
            this(initialClass, className, loader, logServiceHolder,  breakOnShared,
                new LinkedHashMap<String, OsgiClass2>(), new LinkedHashSet<String>());
        }

        public OsgiClass2(String initialClass, String className, ClassLoader loader, LogServiceHolder logServiceHolder,
                boolean breakOnShared, Map<String, OsgiClass2> common, Set<String> shared) {
            this.logServiceHolder = logServiceHolder;
            this.breakOnShared = breakOnShared;
            this.common = common;
            common.put(className, this);
            this.initialClass = initialClass;
            classLoader = loader;
            this.shared = shared;
            if (!abort()) {
                load(className);
            } else {
                logServiceHolder.debug("Loading aborted match found ",className);
            }
        }

        public String getCommonSuperClass(String classA, String classB) {
            shared.add(OBJECT_INTERNAL_NAME);
            logServiceHolder.debug(shared.iterator().next(), " is shared by ", classA, " and ",
                classB);
            return shared.iterator().next();
        }

        private void load(String className) {

            InputStream is = classLoader.getResourceAsStream(className + ".class");
            if (is != null) {
                try {
                    ClassReader cr = new ClassReader(is);
                    String[] interfaces = cr.getInterfaces();
                    String superClassName = cr.getSuperName();
                    if (superClassName == null) {
                        addInterfaces(interfaces);
                    } else if (!OBJECT_INTERNAL_NAME.equals(superClassName)) {
                        superClass = get(initialClass, superClassName);
                        addInterfaces(interfaces);
                    }
                } catch (IOException e) {
                } finally {
                    try {
                        is.close();
                    } catch (IOException e) {
                    }
                }
            } else {
                logServiceHolder.warn("No Stream for ", className);
            }
        }

        private void addInterfaces(String[] interfaces) {
            if (interfaces != null) {
                interfaceClasses = new OsgiClass2[interfaces.length];
                for (int i = 0; i < interfaces.length; i++) {
                    if (!EXCLUDE_INTERFACE.contains(interfaces[i])) {
                        interfaceClasses[i] = get(initialClass, interfaces[i]);
                        if (abort()) {
                            break;
                        }
                    }
                }
            } else {
                interfaceClasses = new OsgiClass2[0];
            }
        }

        private boolean abort() {
            return breakOnShared && shared.size() > 0;
        }

        public OsgiClass2 get(String initialClass, String className) {
            OsgiClass2 oc = common.get(className);
            if (oc == null) {
                oc = new OsgiClass2(initialClass, className, classLoader, logServiceHolder, breakOnShared, common,
                    shared);
                common.put(className, oc);
            } else if (!oc.initialClass.equals(initialClass)) {
                shared.add(className);
            }
            return oc;
        }

    }

}
