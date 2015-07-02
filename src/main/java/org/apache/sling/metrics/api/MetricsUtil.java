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

import java.io.PrintStream;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;


/**
 * This is exported, but nothing should use it except the woven classes created by this package.
 */
public class MetricsUtil {

    protected static PrintStream writer = System.err;
    private static MetricsFactory factory = MetricsFactory.DEFAULT;
    
    public static void setWriter(@Nullable PrintStream writer) {
        if (writer == null) {
            MetricsUtil.writer = System.err;
        } else {
            MetricsUtil.writer = writer;            
        }
    }
    
    public static void setFactory(@Nullable MetricsFactory factory) {
        if (factory == null) {
            MetricsUtil.factory = MetricsFactory.DEFAULT;
        } else {
            MetricsUtil.factory = factory;
        }
    }
    


    @Nonnull
    public static TimerContext getTimer(@Nonnull String name) {
        return factory.timerContext(name);
    }
    
    @Nonnull
    public static void count(@Nonnull String name) {
        factory.counter(name).inc();
    }

    @Nonnull
    public static void mark(@Nonnull String name) {
        factory.meter(name).mark();
    }

}
