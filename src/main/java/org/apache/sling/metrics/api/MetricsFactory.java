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

import aQute.bnd.annotation.ProviderType;

/**
 * Wraps the metrics implementation.
 */
@ProviderType
public interface MetricsFactory {

    /**
     * Default, do nothing implementation.
     */
    MetricsFactory DEFAULT = new MetricsFactory() {

        @Override
        public TimerContext timerContext(String name) {
            return TimerContext.DEFAULT;
        }

        @Override
        public Counter counter(String name) {
            return Counter.DEFAULT;
        }

        @Override
        public Histogram histogram(String name) {
            return Histogram.DEFAULT;
        }

        @Override
        public Meter meter(String name) {
            return Meter.DEFAULT;
        }
    };

    /**
     * @param name name of the timer.
     * @return a timer context, already started, must be stopped to complete the timing operation and record.
     */
    @Nonnull
    TimerContext timerContext(@Nonnull String name);
    
    /**
     * @param name name of the counter.
     * @return the counter associated with the name.
     */
    @Nonnull
    Counter counter(@Nonnull String name);
    
    /**
     * @param name name of the histogram
     * @return the histogram associated with the name.
     */
    @Nonnull
    Histogram histogram(@Nonnull String name);
    
    /**
     * @param name name of the meter.
     * @return the meter associated with the name.
     */
    @Nonnull
    Meter meter(@Nonnull String name);

}
