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

import org.apache.sling.metrics.impl.dropwizard.DropwizardMetricsFactory;
import org.junit.Before;
import org.junit.Test;

import com.codahale.metrics.MetricRegistry;

public class OverheadCheck {
    
    @Before
    public void before() {
        MetricsFactory factory = new DropwizardMetricsFactory(new MetricRegistry());
        MetricsUtil.setFactory(factory);
    }
    
   @Test
   public void testSingleThreaded() {
       long j = 0;
       for(int i = 0; i < 10000000; i++) {
           j = testCall(j);
           j = testCallWithout(j);
       }
       // force j out.
       int ncalls = 100000;
       double toverhead = 0;
       System.err.println(j);
       for ( int k = 0; k < 100; k++ ) {
           j = 0;
          long t1 = System.nanoTime();
          for(int i = 0; i < ncalls; i++) {
              j = testCall(j);
          }
          long t2 = System.nanoTime();
          for(int i = 0; i < ncalls; i++) {
              j = testCallWithout(j);
          }
          long t3 = System.nanoTime();
          for(int i = 0; i < ncalls; i++) {
              j = testCallWithout(j);
          }
          long t4 = System.nanoTime();
          for(int i = 0; i < ncalls; i++) {
              j = testCall(j);
          }
          long t5 = System.nanoTime();
          long withTimer = (t2-t1+t5-t4)/2;
          long withoutTimer = (t3-t2+t4-t3)/2;
          double overhead = ((withTimer-withoutTimer)/ncalls)/1.0E9;
          toverhead = toverhead+overhead;
       }
       System.err.println(j);
       toverhead = toverhead/100;
       System.err.println("Average Overhead per call "+toverhead);
      
   }
   
   @Test
   public void testMultiThreaded() throws InterruptedException {
       Thread[] ts = new Thread[10];
       for (int i = 0; i < 10;  i++ ) {
           ts[i] = new Thread() {
               @Override
            public void run() {
                   testSingleThreaded();
            }
           };
           ts[i].start();
       }
       for (int i = 0; i < 10;  i++ ) {
           ts[i].join();
       }
       
   }

   private long testCall(long j) {
       TimerContext t = MetricsUtil.getTimer("testCall");
       try {
           j = j+1;
           return j;
       } finally {
           t.stop();
       }
   }

   private long testCallWithout(long j) {
       try {
           j = j+1;
           return j;
       } finally {
       }
   }

}
