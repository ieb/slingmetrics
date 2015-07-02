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

import aQute.bnd.annotation.ProviderType;


/**
 * Provides a mechanism to stop a timer in a context once it has been started.
 * The TimerContext is created via the MetricsFactory.
 */
@ProviderType
public interface TimerContext {
    
    /**
     * Default, do nothing implementation.
     */
    TimerContext DEFAULT = new TimerContext() {        
        @Override
        public void stop() {
        }
    };

    /**
     * Stop the timer, must be called to record the elapsed time.
     */
    void stop();

}
