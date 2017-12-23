/*
 * Licensed to Elasticsearch under one or more contributor
 * license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright
 * ownership. Elasticsearch licenses this file to you under
 * the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.elasticsearch.search.aggregations.metrics;

import org.elasticsearch.common.io.stream.Writeable;
import org.elasticsearch.common.xcontent.ToXContent;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.common.xcontent.json.JsonXContent;
import org.elasticsearch.search.DocValueFormat;
import org.elasticsearch.search.aggregations.ParsedAggregation;
import org.elasticsearch.search.aggregations.metrics.stats.InternalStats;
import org.elasticsearch.search.aggregations.metrics.stats.ParsedStats;
import org.elasticsearch.search.aggregations.pipeline.PipelineAggregator;
import org.elasticsearch.test.InternalAggregationTestCase;

import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class InternalStatsTests extends InternalAggregationTestCase<InternalStats> {

    @Override
    protected InternalStats createTestInstance(String name, List<PipelineAggregator> pipelineAggregators, Map<String, Object> metaData) {
        long count = frequently() ? randomIntBetween(1, Integer.MAX_VALUE) : 0;
        double min = randomDoubleBetween(-1000000, 1000000, true);
        double max = randomDoubleBetween(-1000000, 1000000, true);
        double sum = randomDoubleBetween(-1000000, 1000000, true);
        DocValueFormat format = randomNumericDocValueFormat();
        return createInstance(name, count, sum, min, max, format, pipelineAggregators, metaData);
    }

    protected InternalStats createInstance(String name, long count, double sum, double min, double max, DocValueFormat formatter,
            List<PipelineAggregator> pipelineAggregators, Map<String, Object> metaData) {
        return new InternalStats(name, count, sum, min, max, formatter, pipelineAggregators, metaData);
    }

    @Override
    protected void assertReduced(InternalStats reduced, List<InternalStats> inputs) {
        long expectedCount = 0;
        double expectedSum = 0;
        double expectedMin = Double.POSITIVE_INFINITY;
        double expectedMax = Double.NEGATIVE_INFINITY;
        for (InternalStats stats : inputs) {
            expectedCount += stats.getCount();
            if (Double.compare(stats.getMin(), expectedMin) < 0) {
                expectedMin = stats.getMin();
            }
            if (Double.compare(stats.getMax(), expectedMax) > 0) {
                expectedMax = stats.getMax();
            }
            expectedSum += stats.getSum();
        }
        assertEquals(expectedCount, reduced.getCount());
        assertEquals(expectedSum, reduced.getSum(), 1e-7);
        assertEquals(expectedMin, reduced.getMin(), 0d);
        assertEquals(expectedMax, reduced.getMax(), 0d);
    }

    @Override
    protected void assertFromXContent(InternalStats aggregation, ParsedAggregation parsedAggregation) {
        assertTrue(parsedAggregation instanceof ParsedStats);
        ParsedStats parsed = (ParsedStats) parsedAggregation;
        assertStats(aggregation, parsed);
    }

    static void assertStats(InternalStats aggregation, ParsedStats parsed) {
        long count = aggregation.getCount();
        assertEquals(count, parsed.getCount());
        // for count == 0, fields are rendered as `null`, so  we test that we parse to default values used also in the reduce phase
        assertEquals(count > 0 ? aggregation.getMin() : Double.POSITIVE_INFINITY, parsed.getMin(), 0);
        assertEquals(count > 0 ? aggregation.getMax() : Double.NEGATIVE_INFINITY, parsed.getMax(), 0);
        assertEquals(count > 0 ? aggregation.getSum() : 0, parsed.getSum(), 0);
        assertEquals(count > 0 ? aggregation.getAvg() : 0, parsed.getAvg(), 0);
        // also as_string values are only rendered for count != 0
        if (count > 0) {
            assertEquals(aggregation.getMinAsString(), parsed.getMinAsString());
            assertEquals(aggregation.getMaxAsString(), parsed.getMaxAsString());
            assertEquals(aggregation.getSumAsString(), parsed.getSumAsString());
            assertEquals(aggregation.getAvgAsString(), parsed.getAvgAsString());
        }
    }

    @Override
    protected Writeable.Reader<InternalStats> instanceReader() {
        return InternalStats::new;
    }

    @Override
    protected InternalStats mutateInstance(InternalStats instance) {
        String name = instance.getName();
        long count = instance.getCount();
        double sum = instance.getSum();
        double min = instance.getMin();
        double max = instance.getMax();
        DocValueFormat formatter = instance.format;
        List<PipelineAggregator> pipelineAggregators = instance.pipelineAggregators();
        Map<String, Object> metaData = instance.getMetaData();
        switch (between(0, 5)) {
        case 0:
            name += randomAlphaOfLength(5);
            break;
        case 1:
            if (Double.isFinite(count)) {
                count += between(1, 100);
            } else {
                count = between(1, 100);
            }
            break;
        case 2:
            if (Double.isFinite(sum)) {
                sum += between(1, 100);
            } else {
                sum = between(1, 100);
            }
            break;
        case 3:
            if (Double.isFinite(min)) {
                min += between(1, 100);
            } else {
                min = between(1, 100);
            }
            break;
        case 4:
            if (Double.isFinite(max)) {
                max += between(1, 100);
            } else {
                max = between(1, 100);
            }
            break;
        case 5:
            if (metaData == null) {
                metaData = new HashMap<>(1);
            } else {
                metaData = new HashMap<>(instance.getMetaData());
            }
            metaData.put(randomAlphaOfLength(15), randomInt());
            break;
        default:
            throw new AssertionError("Illegal randomisation branch");
        }
        return new InternalStats(name, count, sum, min, max, formatter, pipelineAggregators, metaData);
    }

    public void testDoXContentBody() throws IOException {
        // count is greater than zero
        double min = randomDoubleBetween(-1000000, 1000000, true);
        double max = randomDoubleBetween(-1000000, 1000000, true);
        double sum = randomDoubleBetween(-1000000, 1000000, true);
        int count = randomIntBetween(1, 10);
        DocValueFormat format = randomNumericDocValueFormat();
        InternalStats internalStats = createInstance("stats", count, sum, min, max, format, Collections.emptyList(), null);
        XContentBuilder builder = JsonXContent.contentBuilder().prettyPrint();
        builder.startObject();
        internalStats.doXContentBody(builder, ToXContent.EMPTY_PARAMS);
        builder.endObject();

        String expected = "{\n" +
            "  \"count\" : " + count + ",\n" +
            "  \"min\" : " + min + ",\n" +
            "  \"max\" : " + max + ",\n" +
            "  \"avg\" : " + internalStats.getAvg() + ",\n" +
            "  \"sum\" : " + sum;
        if (format != DocValueFormat.RAW) {
            expected += ",\n"+
                "  \"min_as_string\" : \"" + format.format(internalStats.getMin()) + "\",\n" +
                "  \"max_as_string\" : \"" + format.format(internalStats.getMax()) + "\",\n" +
                "  \"avg_as_string\" : \"" + format.format(internalStats.getAvg()) + "\",\n" +
                "  \"sum_as_string\" : \"" + format.format(internalStats.getSum()) + "\"";
        }
        expected += "\n}";
        assertEquals(expected, builder.string());

        // count is zero
        format = randomNumericDocValueFormat();
        min = 0.0;
        max = 0.0;
        sum = 0.0;
        count = 0;
        internalStats = createInstance("stats", count, sum, min, max, format, Collections.emptyList(), null);
        builder = JsonXContent.contentBuilder().prettyPrint();
        builder.startObject();
        internalStats.doXContentBody(builder, ToXContent.EMPTY_PARAMS);
        builder.endObject();

        assertEquals("{\n" +
            "  \"count\" : 0,\n" +
            "  \"min\" : null,\n" +
            "  \"max\" : null,\n" +
            "  \"avg\" : null,\n" +
            "  \"sum\" : 0.0\n" +
            "}", builder.string());
    }
}

