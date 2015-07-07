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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.Format;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.SortedMap;
import java.util.concurrent.TimeUnit;

import com.codahale.metrics.Counter;
import com.codahale.metrics.Gauge;
import com.codahale.metrics.Histogram;
import com.codahale.metrics.Meter;
import com.codahale.metrics.MetricFilter;
import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.ScheduledReporter;
import com.codahale.metrics.Snapshot;
import com.codahale.metrics.Timer;

public class ElasticSearchReporter extends ScheduledReporter {

    private static final String TEMPLATE_FLD = "template";

    private static final String HTTP_PUT = "PUT";

    private static final String HTTP_HEAD = "HEAD";

    private static final String _TEMPLATE_SLING_METRICS_URI = "/_template/sling_metrics";

    private static final String P999_FLD = "p999";

    private static final String P99_FLD = "p99";

    private static final String P78_FLD = "p78";

    private static final String P95_FLD = "p95";

    private static final String P75_FLD = "p75";

    private static final String P50_FLD = "p50";

    private static final String STDDEV_FLD = "stddev";

    private static final String MIN_FLD = "min";

    private static final String MEAN_FLD = "mean";

    private static final String MAX_FLD = "max";

    private static final String INSTANCE_ID_FLD = "iid";

    private static final String CUSTOMER_ID_FLD = "cid";

    private static final String TIMESTAMP_FLD = "ts";

    private static final String NAME_FLD = "name";

    private static final String INDEX_ACT = "index";

    private static final String UNITS_FLD = "units";

    private static final String DURATION_UNITS_FLD = "duration_units";

    private static final String RATE_UNITS_FLD = "rate_units";

    private static final String TIMER_TYP = "timer";

    private static final String METER_TYP = "meter";

    private static final String HISTOGRAM_TYP = "histogram";

    private static final String COUNTER_TYP = "counter";

    private static final String GAUGE_TYP = "gauge";

    private static final String VALUE_FLD = "value";

    private static final String MEAN_RATE_FLD = "mean_rate";

    private static final String M15_RATE_FLD = "m15_rate";

    private static final String M5_RATE_FLD = "m5_rate";

    private static final String M1_RATE_FLD = "m1_rate";

    private static final String COUNT_FLD = "count";

    private static final Map<String, Object> INDEX_MAPPINGS = createMap(
        "_default_",
        createMap("_all", createMap("enabled", false), "properties",
            createMap(
                "name", createMap("type", "string", "index", "not_analyzed"),
                TIMESTAMP_FLD, createMap("type", "date")
                    )));

    public static Builder fromRegistry(MetricRegistry registry) {
        return new Builder(registry);
    }

    public static class Builder {

        private MetricRegistry registry;

        private String name = "elasticsearch-reporter";

        private MetricFilter filter = MetricFilter.ALL;

        private TimeUnit rateUnit = TimeUnit.SECONDS;

        private TimeUnit durationUnit = TimeUnit.MILLISECONDS;

        private String instanceId = "na";

        private String customerId = "na";

        private List<URL> serverUrls = new ArrayList<URL>();

        private int timeout = 10000;

        private int maxCommands = 1000;

        public Builder(MetricRegistry registry) {
            this.registry = registry;
        }

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder filter(MetricFilter filter) {
            this.filter = filter;
            return this;
        }

        public Builder rateUnit(TimeUnit rateUnit) {
            this.rateUnit = rateUnit;
            return this;
        }

        public Builder durationUnit(TimeUnit durationUnit) {
            this.durationUnit = durationUnit;
            return this;
        }

        public Builder customerId(String customerId) {
            this.customerId = customerId;
            return this;
        }

        public Builder instanceId(String instanceId) {
            this.instanceId = instanceId;
            return this;
        }

        public Builder addServerUrl(URL serverUrl) {
            serverUrls.add(serverUrl);
            return this;
        }

        public Builder hostTimeout(int timeout) {
            this.timeout = timeout;
            return this;
        }

        public Builder maxCommands(int maxCommands) {
            this.maxCommands = maxCommands;
            return this;
        }

        public ElasticSearchReporter build() throws IOException {
            return new ElasticSearchReporter(registry, name, filter, rateUnit, durationUnit, customerId, instanceId, serverUrls, timeout, maxCommands);
        }

    }

    private String index = "metrics";

    private int timeout;

    private String customerId;

    private String instanceId;

    private double rateFactor;

    private String rateUnits;

    private double durationFactor;

    private String durationUnits;

    private int  maxCommands;

    private List<URL> hosts;

    protected ElasticSearchReporter(MetricRegistry registry, String name, MetricFilter filter,
            TimeUnit rateUnit, TimeUnit durationUnit,String customerId, String instanceId, List<URL> hosts, int timeout, int maxCommands) throws IOException {
        super(registry, name, filter, rateUnit, durationUnit);
        this.timeout = timeout;
        this.customerId = customerId;
        this.instanceId = instanceId;
        this.hosts = hosts;
        this.maxCommands = maxCommands;
        rateUnits = rateUnit.toString();
        rateFactor = rateUnit.toSeconds(1);
        durationUnits = durationUnit.toString();
        durationFactor = 1.0 / durationUnit.toNanos(1);
        checkIndexExists();
    }

    @Override
    public void report(@SuppressWarnings("rawtypes") SortedMap<String, Gauge> gauges,
            SortedMap<String, Counter> counters, SortedMap<String, Histogram> histograms,
            SortedMap<String, Meter> meters, SortedMap<String, Timer> timers) {
        if (gauges.isEmpty() && counters.isEmpty() && histograms.isEmpty() && meters.isEmpty()
            && timers.isEmpty()) {
            System.err.println("No Metrics data to send");
            return;
        }
        long timestamp = System.currentTimeMillis();
        System.err.println("Sending metrics data to elastic search");
        try{
            Updater u = new Updater(hosts, "/_bulk", "POST", timeout, maxCommands);
            System.err.println("Sending "+gauges.size()+" Gauges");
            for ( Entry<String, Gauge> e : gauges.entrySet()) {
                sendGuage(e.getKey(), e.getValue(), timestamp, u);
            }
            System.err.println("Sending "+counters.size()+" counters");
            for ( Entry<String, Counter> e : counters.entrySet()) {
                sendCounter(e.getKey(), e.getValue(), timestamp, u);
            }
            System.err.println("Sending "+histograms.size()+" histograms");
            for ( Entry<String, Histogram> e : histograms.entrySet()) {
                sendHistogram(e.getKey(), e.getValue(), timestamp, u);
            }
            System.err.println("Sending "+meters.size()+" counters");
            for ( Entry<String, Meter> e : meters.entrySet()) {
                sendMeter(e.getKey(), e.getValue(), timestamp, u);
            }
            System.err.println("Sending "+timers.size()+" timers");
            for ( Entry<String, Timer> e : timers.entrySet()) {
                System.err.println("Sending timer "+e.getKey());
                sendTimer(e.getKey(), e.getValue(), timestamp, u);
            }
            System.err.println("Final flush");
            u.close();
            System.err.println("All Done");
        } catch (IOException e) {
            System.err.println(" Failed ");
            e.printStackTrace();
        }
        System.err.println(" Done reporting ");
    }
    
    
    

    private void sendGuage(String name, Gauge value, long timestamp, Updater w) throws IOException {
        w.action(indexAction(index, GAUGE_TYP));
        w.source(mapAdd(standardFields(name, timestamp),VALUE_FLD, value.getValue()));
    }
    
    private void sendCounter(String name, Counter value, long ts, Updater w) throws IOException {
        w.action(indexAction(index, COUNTER_TYP));
        w.source(mapAdd(standardFields(name, ts),COUNT_FLD, value.getCount()));
    }


    private void sendHistogram(String name, Histogram value, long ts, Updater w) throws IOException {
        w.action(indexAction(index, HISTOGRAM_TYP));
        w.source(mapAddHistogtam(mapAdd(standardFields(name, ts),
            COUNT_FLD, value.getCount()), value.getSnapshot(), 1.0));
    }
    


    private void sendMeter(String name, Meter value, long ts, Updater w) throws IOException {
        w.action(indexAction(index, METER_TYP));
        w.source(mapAdd(standardFields(name, ts),
            COUNT_FLD, value.getCount(),
            M1_RATE_FLD, value.getOneMinuteRate() * rateFactor,
            M5_RATE_FLD, value.getFiveMinuteRate() * rateFactor,
            M15_RATE_FLD, value.getFifteenMinuteRate() * rateFactor,
            MEAN_RATE_FLD, value.getMeanRate() * rateFactor,
            UNITS_FLD, "events/"+rateUnits
            ));
    }

    private void sendTimer(String name, Timer value, long ts, Updater w) throws IOException {
        System.err.println("Sending action");
        w.action(indexAction(index, TIMER_TYP));
        System.err.println("Done Sending action, sending data");
        w.source(mapAdd(mapAddHistogtam(standardFields(name, ts), value.getSnapshot(), durationFactor),
            COUNT_FLD, value.getCount(),
            M1_RATE_FLD, value.getOneMinuteRate() * rateFactor,
            M5_RATE_FLD, value.getFiveMinuteRate() * rateFactor,
            M15_RATE_FLD, value.getFifteenMinuteRate() * rateFactor,
            MEAN_RATE_FLD, value.getMeanRate() * rateFactor,
            RATE_UNITS_FLD, "calls/"+rateUnits,
            DURATION_UNITS_FLD, durationUnits
            ));        
        System.err.println("Done Sending data");
    }
    

    private Map<String, Object> indexAction(String index, String type) {
        return createMap(INDEX_ACT,createMap("_index",index,"_type",type));
    }


    private Map<String, Object> standardFields(String name, long timestamp) {
        return createMap(NAME_FLD,name,TIMESTAMP_FLD,timestamp,CUSTOMER_ID_FLD,customerId, INSTANCE_ID_FLD, instanceId);
    }

    public static Map<String, Object> mapAdd(Map<String, Object> m, Object ... v) {
        for (int i = 0; i < v.length; i=i+2) {
            m.put((String)v[i], v[i+1]);
        }
        return m;
    }

    public static Map<String, Object> createMap(Object ... v) {
        return mapAdd(new HashMap<String, Object>(), v);
    }

    private Map<String, Object> mapAddHistogtam(Map<String, Object> m, Snapshot s, double factor) {
        return mapAdd(m, 
            MAX_FLD, s.getMax() * factor,
            MEAN_FLD, s.getMean() * factor,
            MIN_FLD, s.getMin() * factor,
            STDDEV_FLD, s.getStdDev() * factor,
            P50_FLD, s.getMedian() * factor,
            P75_FLD, s.get75thPercentile() * factor,
            P95_FLD, s.get95thPercentile() * factor,
            P78_FLD, s.get98thPercentile() * factor,
            P99_FLD, s.get99thPercentile() * factor,
            P999_FLD, s.get999thPercentile() * factor
            );
    }


    private void checkIndexExists() throws IOException {
        Updater u = new Updater(hosts,  _TEMPLATE_SLING_METRICS_URI, HTTP_HEAD, timeout, 10);
        u.openConection();
        HttpURLConnection c = u.disconnect();
        if (c.getResponseCode() == HttpURLConnection.HTTP_NOT_FOUND) {
            u = new Updater(hosts, _TEMPLATE_SLING_METRICS_URI, HTTP_PUT, timeout, 10);
            u.action(createMap(TEMPLATE_FLD, index+"*", "mappings", INDEX_MAPPINGS));
            c = u.disconnect();
            if (c.getResponseCode() != 200) {                
                throw new IllegalArgumentException(
                    "Error adding metrics template to elasticsearch: " + c.getResponseCode()
                        + "/" + c.getResponseMessage());
            }
        }
    }

    
    private static class Updater {
        
        private int maxCommands;
        private HttpURLConnection connection;
        private int currentCommand;
        private Writer writer;
        private String method;
        private String uri;
        private int timeout;
        private List<URL> hosts;
        private Format dateFormat;

        public Updater(List<URL> hosts, String uri,  String method, int timeout, int maxCommands) {
            this.hosts = hosts;
            this.timeout = timeout;
            this.maxCommands = maxCommands;
            this.method = method;
            this.uri = uri;
            this.currentCommand = 0;
        }
        
        public void source(Map<String, Object> source) throws IOException {
            writeMap(source);
            writer.write("\n");
        }

        public void action(Map<String, Object> action) throws IOException {
            System.err.println("Will send "+action+" currentCommand "+ currentCommand+ " max commands "+maxCommands);
            if (connection == null || ((currentCommand % maxCommands) == 0) ) {
                System.err.println("Opening connection");
                openConection();
                System.err.println("Done Opening connection");
            }
            currentCommand++;
            System.err.println("Writing action map "+action);
            writeMap(action);
            System.err.println("Done Writing action map "+action);
            writer.write("\n");
        }
        
        

        
        public void close() throws IOException {
            if (connection != null) {
                System.err.println("Closing Connection");
                if (writer != null) {
                    writer.flush();
                    writer.close();                    
                }
                connection.disconnect();
                dumpResult();
                connection = null;
                writer = null;
                System.err.println("Done Closing Connection");
            }            
        }
        
        private void dumpResult() throws IOException {
            System.err.println("Response "+connection.getResponseCode()+" "+connection.getResponseMessage());
            try {
                BufferedReader in = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                String l = in.readLine();
                System.err.println("-- start --");
                while (l != null) {
                    System.err.println(l);
                    l = in.readLine();
                }
                System.err.println("-- end --");
            } catch ( IOException e) {
                System.err.println(e.getMessage());
            }
        }

        public HttpURLConnection disconnect() throws IOException {
            HttpURLConnection c = connection;
            close();
            return c;
        }
        

        private void writeMap(Map<String, Object> map) throws IOException {
            if (writer == null) {
                throw new IllegalArgumentException("Method "+method+" does not allow output");
            }
            OutputStreamWriter o = new OutputStreamWriter(System.err);
            write(map, o);
            o.flush();
            write(map, writer);
        }

        @SuppressWarnings("unchecked")
        private void write(Object v, Writer w) throws IOException {
            if (v instanceof Map) {
                w.write("{");
                boolean cont = false;
                for(Entry<String,Object> o : ((Map<String,Object>)v).entrySet()) {
                    if (cont) {
                        w.write(",\"");                        
                    } else {
                        w.write("\"");
                        cont = true;
                    }
                    w.write(o.getKey());
                    w.write("\":");
                    write(o.getValue(),w);
                }
                w.write("}");
            } else if (v instanceof String) {
                w.write("\"");
                w.write(((String)v).replace("\"","\\\""));
                w.write("\"");
            } else if (v instanceof Integer || v instanceof Long || v instanceof Boolean || v instanceof Double || v instanceof Float) {
                w.write(String.valueOf(v));            
            } else {
                throw new IOException("Cant convert "+v.getClass()+" to JSON form ");
            }
        }




        public void openConection() throws IOException {
            System.err.println("Closing before opening");
            close();
            System.err.println(" opening");
            connection = openConnection(uri, method);
            System.err.println(" opened");
            if ("PUT".equals(method) || "POST".equals(method)) {
                writer = new OutputStreamWriter(connection.getOutputStream());
            }
        }
        
        private HttpURLConnection openConnection(String uri, String method) {
            for( URL host : hosts) {
                try {
                    URL url = new URL(host, uri);
                    System.err.println("Trying to open "+url);
                    HttpURLConnection c = (HttpURLConnection) url.openConnection();
                    c.setRequestMethod(method);
                    c.setConnectTimeout(timeout);
                    if ("POST".equals(method) || HTTP_PUT.equals(method)) {
                        c.setDoOutput(true);
                    }
                    c.connect();
                    System.err.println("Opened "+method+" "+url);
                    return c;
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            System.err.println("Failed to open any hosts from "+hosts);
            throw new IllegalArgumentException("Unable to connect to any host from "+hosts.toString());
        }

        
    }

}
