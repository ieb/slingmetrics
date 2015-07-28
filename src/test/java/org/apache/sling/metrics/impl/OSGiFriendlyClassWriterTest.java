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

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import javax.jcr.RepositoryException;

import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.SyntheticResource;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.objectweb.asm.ClassWriter;
import org.osgi.framework.ServiceRegistration;


public class OSGiFriendlyClassWriterTest {
    
    private OSGiFriendlyClassWriter classWriter;
    private TestClassWriter standardClassWriter;
    
    public static class TestClassWriter extends ClassWriter {

        public TestClassWriter(int flags) {
            super(flags);
        }
        
        @Override
        protected String getCommonSuperClass(String type1, String type2) {
            return super.getCommonSuperClass(type1, type2);
        }
        
    }

    @Before
    public void before() {
        standardClassWriter = new TestClassWriter(ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);
        classWriter = new OSGiFriendlyClassWriter(ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES,
            this.getClass().getClassLoader(), OSGiFriendlyClassWriterTest.class.getName(), false);
    }
    @Test
    public void testMap() {
        compare(HashMap.class.getName(), Map.class.getName());
    }
    @Test
    public void testObject() {
        compare(ArrayList.class.getName(), ConcurrentHashMap.class.getName());
    }
    @Test
    public void testResourceProblemCase() {
        compare(Resource.class.getName(), SyntheticResource.class.getName());
    }
    @Test
    public void testString() {
        compare(String.class.getName(), StringBuilder.class.getName());
    }
    
    @Test
    public void testFromContainer() {
        compare(FileInputStream.class.getName(),InputStream.class.getName());
        compare(MalformedURLException.class.getName(), URL.class.getName());
        compare(RepositoryException.class.getName(), IOException.class.getName());
        compare(RepositoryException.class.getName(), Exception.class.getName());
        compare(IOException.class.getName(), Exception.class.getName());
        compare(Exception.class.getName(), ServiceRegistration.class.getName());
    
    }
    private void compare(String class1, String class2) {
        String osgi = classWriter.getCommonSuperClass(class1, class2).replace('.', '/');
        String osgiR = classWriter.getCommonSuperClass(class2, class1).replace('.', '/');
        String standard = standardClassWriter.getCommonSuperClass(class1, class2).replace('.', '/');
        String standardR = standardClassWriter.getCommonSuperClass(class2, class1).replace('.', '/');
        Assert.assertEquals("Standard doesnt match reverse resolution",standardR, standard);
        Assert.assertEquals("Osgi doesnt match standard", osgi, standard);
        Assert.assertEquals("Osgi doesnt match reverse resolution", osgi, osgiR);
    }
    

}
