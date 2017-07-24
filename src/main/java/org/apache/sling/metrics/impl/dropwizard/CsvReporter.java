package org.apache.sling.metrics.impl.dropwizard;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.Charset;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map.Entry;
import java.util.SortedMap;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.codahale.metrics.Clock;
import com.codahale.metrics.Counter;
import com.codahale.metrics.Gauge;
import com.codahale.metrics.Histogram;
import com.codahale.metrics.Meter;
import com.codahale.metrics.MetricFilter;
import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.ScheduledReporter;
import com.codahale.metrics.Snapshot;
import com.codahale.metrics.Timer;

// This class came from Drowizard 3.1. The sanitise method was broken, fixed here.

public class CsvReporter extends ScheduledReporter {
    private static final Logger LOGGER = LoggerFactory.getLogger(CsvReporter.class);
    private static final Charset UTF_8 = Charset.forName("UTF-8");
    private final File directory;
    private final Locale locale;
    private final Clock clock;

    public static CsvReporter.Builder forRegistry(MetricRegistry registry) {
        return new CsvReporter.Builder(registry);
    }

    private CsvReporter(MetricRegistry registry, File directory, Locale locale, TimeUnit rateUnit, TimeUnit durationUnit, Clock clock, MetricFilter filter) {
        super(registry, "csv-reporter", filter, rateUnit, durationUnit);
        this.directory = directory;
        this.locale = locale;
        this.clock = clock;
    }

    @Override
    public void report(SortedMap<String, Gauge> gauges, SortedMap<String, Counter> counters, SortedMap<String, Histogram> histograms, SortedMap<String, Meter> meters, SortedMap<String, Timer> timers) {
        long timestamp = TimeUnit.MILLISECONDS.toSeconds(this.clock.getTime());
        Iterator var8 = gauges.entrySet().iterator();

        Entry entry;
        while(var8.hasNext()) {
            entry = (Entry)var8.next();
            this.reportGauge(timestamp, (String)entry.getKey(), (Gauge)entry.getValue());
        }

        var8 = counters.entrySet().iterator();

        while(var8.hasNext()) {
            entry = (Entry)var8.next();
            this.reportCounter(timestamp, (String)entry.getKey(), (Counter)entry.getValue());
        }

        var8 = histograms.entrySet().iterator();

        while(var8.hasNext()) {
            entry = (Entry)var8.next();
            this.reportHistogram(timestamp, (String)entry.getKey(), (Histogram)entry.getValue());
        }

        var8 = meters.entrySet().iterator();

        while(var8.hasNext()) {
            entry = (Entry)var8.next();
            this.reportMeter(timestamp, (String)entry.getKey(), (Meter)entry.getValue());
        }

        var8 = timers.entrySet().iterator();

        while(var8.hasNext()) {
            entry = (Entry)var8.next();
            this.reportTimer(timestamp, (String)entry.getKey(), (Timer)entry.getValue());
        }

    }

    private void reportTimer(long timestamp, String name, Timer timer) {
        Snapshot snapshot = timer.getSnapshot();
        this.report(timestamp, name, "count,max,mean,min,stddev,p50,p75,p95,p98,p99,p999,mean_rate,m1_rate,m5_rate,m15_rate,rate_unit,duration_unit", "%d,%f,%f,%f,%f,%f,%f,%f,%f,%f,%f,%f,%f,%f,%f,calls/%s,%s", new Object[]{Long.valueOf(timer.getCount()), Double.valueOf(this.convertDuration(snapshot.getMax())), Double.valueOf(this.convertDuration(snapshot.getMean())), Double.valueOf(this.convertDuration(snapshot.getMin())), Double.valueOf(this.convertDuration(snapshot.getStdDev())), Double.valueOf(this.convertDuration(snapshot.getMedian())), Double.valueOf(this.convertDuration(snapshot.get75thPercentile())), Double.valueOf(this.convertDuration(snapshot.get95thPercentile())), Double.valueOf(this.convertDuration(snapshot.get98thPercentile())), Double.valueOf(this.convertDuration(snapshot.get99thPercentile())), Double.valueOf(this.convertDuration(snapshot.get999thPercentile())), Double.valueOf(this.convertRate(timer.getMeanRate())), Double.valueOf(this.convertRate(timer.getOneMinuteRate())), Double.valueOf(this.convertRate(timer.getFiveMinuteRate())), Double.valueOf(this.convertRate(timer.getFifteenMinuteRate())), this.getRateUnit(), this.getDurationUnit()});
    }

    private void reportMeter(long timestamp, String name, Meter meter) {
        this.report(timestamp, name, "count,mean_rate,m1_rate,m5_rate,m15_rate,rate_unit", "%d,%f,%f,%f,%f,events/%s", new Object[]{Long.valueOf(meter.getCount()), Double.valueOf(this.convertRate(meter.getMeanRate())), Double.valueOf(this.convertRate(meter.getOneMinuteRate())), Double.valueOf(this.convertRate(meter.getFiveMinuteRate())), Double.valueOf(this.convertRate(meter.getFifteenMinuteRate())), this.getRateUnit()});
    }

    private void reportHistogram(long timestamp, String name, Histogram histogram) {
        Snapshot snapshot = histogram.getSnapshot();
        this.report(timestamp, name, "count,max,mean,min,stddev,p50,p75,p95,p98,p99,p999", "%d,%d,%f,%d,%f,%f,%f,%f,%f,%f,%f", new Object[]{Long.valueOf(histogram.getCount()), Long.valueOf(snapshot.getMax()), Double.valueOf(snapshot.getMean()), Long.valueOf(snapshot.getMin()), Double.valueOf(snapshot.getStdDev()), Double.valueOf(snapshot.getMedian()), Double.valueOf(snapshot.get75thPercentile()), Double.valueOf(snapshot.get95thPercentile()), Double.valueOf(snapshot.get98thPercentile()), Double.valueOf(snapshot.get99thPercentile()), Double.valueOf(snapshot.get999thPercentile())});
    }

    private void reportCounter(long timestamp, String name, Counter counter) {
        this.report(timestamp, name, "count", "%d", new Object[]{Long.valueOf(counter.getCount())});
    }

    private void reportGauge(long timestamp, String name, Gauge gauge) {
        this.report(timestamp, name, "value", "%s", new Object[]{gauge.getValue()});
    }

    private void report(long timestamp, String name, String header, String line, Object... values) {
        try {
            File e = new File(this.directory, this.sanitize(name) + ".csv");
            boolean fileAlreadyExists = e.exists();
            if(fileAlreadyExists || (e.getParentFile().mkdirs() && e.createNewFile())) {
                PrintWriter out = new PrintWriter(new OutputStreamWriter(new FileOutputStream(e, true), UTF_8));

                try {
                    if(!fileAlreadyExists) {
                        out.println("t," + header);
                    }

                    out.printf(this.locale, String.format(this.locale, "%d,%s%n", new Object[]{Long.valueOf(timestamp), line}), values);
                } finally {
                    out.close();
                }
            }
        } catch (IOException var14) {
            LOGGER.warn("Error writing to {}", name, var14);
        }

    }

    protected String sanitize(String name) {
        try {
            return URLEncoder.encode(name, "UTF-8").replace('*', File.separatorChar);
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException(e);
        }
    }

    public static class Builder {
        private final MetricRegistry registry;
        private Locale locale;
        private TimeUnit rateUnit;
        private TimeUnit durationUnit;
        private Clock clock;
        private MetricFilter filter;

        private Builder(MetricRegistry registry) {
            this.registry = registry;
            this.locale = Locale.getDefault();
            this.rateUnit = TimeUnit.SECONDS;
            this.durationUnit = TimeUnit.MILLISECONDS;
            this.clock = Clock.defaultClock();
            this.filter = MetricFilter.ALL;
        }

        public CsvReporter.Builder formatFor(Locale locale) {
            this.locale = locale;
            return this;
        }

        public CsvReporter.Builder convertRatesTo(TimeUnit rateUnit) {
            this.rateUnit = rateUnit;
            return this;
        }

        public CsvReporter.Builder convertDurationsTo(TimeUnit durationUnit) {
            this.durationUnit = durationUnit;
            return this;
        }

        public CsvReporter.Builder withClock(Clock clock) {
            this.clock = clock;
            return this;
        }

        public CsvReporter.Builder filter(MetricFilter filter) {
            this.filter = filter;
            return this;
        }

        public CsvReporter build(File directory) {
            return new CsvReporter(this.registry, directory, this.locale, this.rateUnit, this.durationUnit, this.clock, this.filter);
        }
    }
}
