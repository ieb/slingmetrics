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

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import aQute.bnd.annotation.ProviderType;

/**
 * Utiltiy method to perform basic operations on metrics without needding to bind to the metrics factory.
 */
@ProviderType
public class MetricsUtil {

    private static MetricsFactory factory = MetricsFactory.DEFAULT;

    /**
     * @param factory the current metrics factory in use. If set to null a
     *            default implementation, that does nothing will be used.
     */
    public static void setFactory(@Nullable MetricsFactory factory) {
        if (factory == null) {
            MetricsUtil.factory = MetricsFactory.DEFAULT;
        } else {
            MetricsUtil.factory = factory;
        }
    }

    /**
     * @param name name of the timer to start timing with, on return the timer will have started.
     * @return a TimerContext that must have TimerContext.stop() called to stop the timer.
     */
    @Nonnull
    public static TimerContext getTimer(@Nonnull String name) {
        return factory.timerContext(name);
    }

    /**
     * Increment a named counter by 1.
     * @param name name of the counter
     */
    @Nonnull
    public static void count(@Nonnull String name) {
        factory.counter(name).inc();
    }

    /**
     * Mark the meter with 1 occurrence.
     * @param name name of the meter.
     */
    @Nonnull
    public static void mark(@Nonnull String name) {
        factory.meter(name).mark();
    }

}
