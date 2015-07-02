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
package org.apache.sling.metrics.impl.dropwizard;

import javax.annotation.Nonnull;

import org.apache.sling.metrics.api.Counter;
import org.apache.sling.metrics.api.Histogram;
import org.apache.sling.metrics.api.Meter;
import org.apache.sling.metrics.api.MetricsFactory;
import org.apache.sling.metrics.api.TimerContext;

import com.codahale.metrics.MetricRegistry;

public class DropwizardMetricsFactory implements MetricsFactory {

    private MetricRegistry metricsRegistry;

    public DropwizardMetricsFactory(@Nonnull MetricRegistry metricsRegistry) {
        this.metricsRegistry = metricsRegistry;
    }

    @Override
    @Nonnull
    public TimerContext timerContext(@Nonnull String name) {
        return new DropwizardTimerContext(metricsRegistry.timer(name).time());
    }


    @Nonnull
    public static String name(@Nonnull String className, @Nonnull String name) {
        return MetricRegistry.name(className, name);
    }

    @Override
    @Nonnull
    public Counter counter(@Nonnull String name) {
        return new DropwizardCounter(metricsRegistry.counter(name));
    }

    @Override
    @Nonnull
    public Histogram histogram(@Nonnull String name) {
        return new DropwizardHistogram(metricsRegistry.histogram(name));
    }

    @Override
    @Nonnull
    public Meter meter(@Nonnull String name) {
        return new DropwizardMeter(metricsRegistry.meter(name));
    }



}
