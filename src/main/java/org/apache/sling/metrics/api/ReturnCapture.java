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
package org.apache.sling.metrics.api;

import java.lang.reflect.Method;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Capture the return value and create a metric name from some part of it, either using toString or a method call.
 */
public class ReturnCapture {
    
    public static <T> void markCapture(@Nullable T o, @Nonnull String name, @Nonnull String methodName) {
        System.err.println("Mark with method name "+o);
        MetricsUtil.mark(getMetricName(o,name, methodName));
    }

    public static <T> void markCapture(@Nullable T o, @Nonnull String name) {
        System.err.println("Marking "+o);
        MetricsUtil.mark(getMetricName(o,name));
    }

    public static <T> void countCapture(@Nullable T o, @Nonnull String name, @Nonnull String methodName) {
        System.err.println("Count with method name "+o);
        MetricsUtil.count(name+getMetricName(o, name, methodName));
    }

    public static <T> void countCapture(@Nullable T o, @Nonnull String name) {
        System.err.println("Count "+o);
        MetricsUtil.count(name+getMetricName(o, name));
    }
    
    @Nonnull
    private static <T> String getMetricName(@Nullable T o, @Nonnull String name, @Nonnull String methodName) {
        if (o != null) {
            try {
                Class<T> c = (Class<T>) o.getClass();
                Method m = c.getDeclaredMethod(methodName); 
                if (!m.isAccessible()) {
                    m.setAccessible(true);
                }
                return name+String.valueOf(m.invoke(o));
            } catch (Exception e) {
                return name+"_"+e.getMessage();
            }
        }
        return name+"_nullreturn";
    }

    @Nonnull
    private static <T> String getMetricName(@Nullable T o, @Nonnull String name) {
        if (o != null) {
                return name+String.valueOf(o);
        }
        return name+"_nullreturn";
    }

}
