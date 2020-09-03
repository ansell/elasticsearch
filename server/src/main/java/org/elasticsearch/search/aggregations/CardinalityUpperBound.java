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

package org.elasticsearch.search.aggregations;

import org.elasticsearch.search.aggregations.bucket.BucketsAggregator;
import org.elasticsearch.search.aggregations.bucket.filter.FilterAggregator;
import org.elasticsearch.search.aggregations.bucket.range.RangeAggregator;

import java.util.function.IntFunction;

/**
 * Upper bound of how many {@code owningBucketOrds} that an {@link Aggregator}
 * will have to collect into.
 */
public abstract class CardinalityUpperBound {
    /**
     * {@link Aggregator}s with this cardinality won't collect any data at
     * all. For the most part this happens when an aggregation is inside of a
     * {@link BucketsAggregator} that is pointing to an unmapped field. 
     */
    public static final CardinalityUpperBound NONE = new CardinalityUpperBound() {
        @Override
        public CardinalityUpperBound multiply(int bucketCount) {
            return NONE;
        }

        @Override
        public <R> R map(IntFunction<R> mapper) {
            return mapper.apply(0);
        }
    };

    /**
     * {@link Aggregator}s with this cardinality will collect be collected
     * once or zero times. This will only be true for top level {@linkplain Aggregator}s
     * and for sub-aggregator's who's ancestors are all single-bucket
     * aggregations like {@link FilterAggregator} or a {@link RangeAggregator}
     * configured to collect only a single range.
     */
    public static final CardinalityUpperBound ONE = new KnownCardinalityUpperBound(1);

    /**
     * {@link Aggregator}s with this cardinality may be collected many times.
     * Most sub-aggregators of {@link BucketsAggregator}s will have
     * this cardinality.
     */
    public static final CardinalityUpperBound MANY = new CardinalityUpperBound() {
        @Override
        public CardinalityUpperBound multiply(int bucketCount) {
            if (bucketCount == 0) {
                return NONE;
            }
            return MANY;
        }

        @Override
        public <R> R map(IntFunction<R> mapper) {
            return mapper.apply(Integer.MAX_VALUE);
        }
    };

    private CardinalityUpperBound() {
        // Sealed class
    }

    /**
     * Get the rough measure of the number of buckets a fixed-bucket
     * {@link Aggregator} will collect.
     *
     * @param bucketCount the number of buckets that this {@link Aggregator}
     *   will collect per owning ordinal
     */
    public abstract CardinalityUpperBound multiply(int bucketCount);

    /**
     * Map the cardinality to a value. The argument to the {@code mapper}
     * is the estimated cardinality, or {@code Integer.MAX_VALUE} if the
     * cardinality is unknown.
     */
    public abstract <R> R map(IntFunction<R> mapper);

    /**
     * Cardinality estimate with a known upper bound.
     */
    private static class KnownCardinalityUpperBound extends CardinalityUpperBound {
        private final int estimate;

        KnownCardinalityUpperBound(int estimate) {
            this.estimate = estimate;
        }

        @Override
        public CardinalityUpperBound multiply(int bucketCount) {
            if (bucketCount < 0) {
                throw new IllegalArgumentException("bucketCount must be positive but was [" + bucketCount + "]");
            }
            switch (bucketCount) {
                case 0:
                    return NONE;
                case 1:
                    return this;
                default:
                    long newEstimate = (long) estimate * (long) bucketCount;
                    if (newEstimate >= Integer.MAX_VALUE) {
                        return MANY;
                    }
                    return new KnownCardinalityUpperBound((int) newEstimate);
            }
        }

        @Override
        public <R> R map(IntFunction<R> mapper) {
            return mapper.apply(estimate);
        }
    }
}
