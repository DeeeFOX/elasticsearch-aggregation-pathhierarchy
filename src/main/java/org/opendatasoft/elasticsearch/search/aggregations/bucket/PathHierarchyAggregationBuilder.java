package org.opendatasoft.elasticsearch.search.aggregations.bucket;

import org.elasticsearch.common.ParseField;
import org.elasticsearch.common.io.stream.StreamInput;
import org.elasticsearch.common.io.stream.StreamOutput;
import org.elasticsearch.common.xcontent.ObjectParser;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.common.xcontent.XContentParser;
import org.elasticsearch.search.aggregations.AggregationBuilder;
import org.elasticsearch.search.aggregations.AggregatorFactories;
import org.elasticsearch.search.aggregations.AggregatorFactories.Builder;
import org.elasticsearch.search.aggregations.AggregatorFactory;
import org.elasticsearch.search.aggregations.BucketOrder;
import org.elasticsearch.search.aggregations.InternalOrder;
import org.elasticsearch.search.aggregations.bucket.MultiBucketAggregationBuilder;
import org.elasticsearch.search.aggregations.support.ValuesSource;
import org.elasticsearch.search.aggregations.support.ValuesSourceAggregationBuilder;
import org.elasticsearch.search.aggregations.support.ValuesSourceAggregatorFactory;
import org.elasticsearch.search.aggregations.support.ValuesSourceConfig;
import org.elasticsearch.search.aggregations.support.ValuesSourceParserHelper;
import org.elasticsearch.search.aggregations.support.ValuesSourceType;
import org.elasticsearch.search.aggregations.support.ValueType;
import org.elasticsearch.search.internal.SearchContext;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Objects;


/**
 * The builder of the aggregatorFactory. Also implements the parsing of the request.
 */
public class PathHierarchyAggregationBuilder extends ValuesSourceAggregationBuilder<ValuesSource, PathHierarchyAggregationBuilder>
        implements MultiBucketAggregationBuilder {
    public static final String NAME = "path_hierarchy";

    public static final ParseField SEPARATOR_FIELD = new ParseField("separator");
    public static final ParseField MIN_DEPTH_FIELD = new ParseField("minDepth");
    public static final ParseField MAX_DEPTH_FIELD = new ParseField("maxDepth");
    public static final ParseField DEPTH_FIELD = new ParseField("depth");
    public static final ParseField ORDER_FIELD = new ParseField("order");

    private static final ObjectParser<PathHierarchyAggregationBuilder, Void> PARSER;
    static {
        PARSER = new ObjectParser<>(PathHierarchyAggregationBuilder.NAME);
        ValuesSourceParserHelper.declareAnyFields(PARSER, true, true);

        PARSER.declareString(PathHierarchyAggregationBuilder::separator, SEPARATOR_FIELD);
        PARSER.declareInt(PathHierarchyAggregationBuilder::minDepth, MIN_DEPTH_FIELD);
        PARSER.declareInt(PathHierarchyAggregationBuilder::maxDepth, MAX_DEPTH_FIELD);
        PARSER.declareInt(PathHierarchyAggregationBuilder::depth, DEPTH_FIELD);
        PARSER.declareObjectArray(PathHierarchyAggregationBuilder::order, (p, c) -> InternalOrder.Parser.parseOrderParam(p),
                ORDER_FIELD);
    }

    public static AggregationBuilder parse(String aggregationName, XContentParser parser) throws IOException {
        return PARSER.parse(parser, new PathHierarchyAggregationBuilder(aggregationName, null), null);
    }

    private static final String DEFAULT_SEPARATOR = "/";
    private static final int DEFAULT_MIN_DEPTH = 0;
    private static final int DEFAULT_MAX_DEPTH = 2;
    private String separator = DEFAULT_SEPARATOR;
    private int minDepth = DEFAULT_MIN_DEPTH;
    private int maxDepth = DEFAULT_MAX_DEPTH;
    private int depth = 0;
    private BucketOrder order = BucketOrder.compound(BucketOrder.count(false)); // automatically adds tie-breaker key asc order


    private PathHierarchyAggregationBuilder(String name, ValueType valueType) {
        super(name, ValuesSourceType.ANY, valueType);
    }

    /**
     * Read from a stream
     *
     */
    public PathHierarchyAggregationBuilder(StreamInput in) throws IOException {
        super(in, ValuesSourceType.ANY);
        separator = in.readString();
        minDepth = in.readOptionalVInt();
        maxDepth = in.readOptionalVInt();
        depth = in.readOptionalVInt();
        order = InternalOrder.Streams.readOrder(in);
    }

    private PathHierarchyAggregationBuilder(PathHierarchyAggregationBuilder clone, Builder factoriesBuilder,
                                           Map<String, Object> metaData) {
        super(clone, factoriesBuilder, metaData);
        separator = clone.separator;
        minDepth = clone.minDepth;
        maxDepth = clone.maxDepth;
        depth = clone.depth;
        order = clone.order;
    }

    @Override
    protected AggregationBuilder shallowCopy(AggregatorFactories.Builder factoriesBuilder, Map<String, Object> metaData) {
        return new PathHierarchyAggregationBuilder(this, factoriesBuilder, metaData);
    }

    /**
     * Write to a stream
     */
    @Override
    protected void innerWriteTo(StreamOutput out) throws IOException {
        out.writeString(separator);
        out.writeOptionalVInt(minDepth);
        out.writeOptionalVInt(maxDepth);
        out.writeOptionalVInt(depth);
        order.writeTo(out);
    }

    private PathHierarchyAggregationBuilder separator(String separator) {
        this.separator = separator;
        return this;
    }

    private PathHierarchyAggregationBuilder minDepth(int minDepth) {
        this.minDepth = minDepth;
        return this;
    }

    private PathHierarchyAggregationBuilder maxDepth(int maxDepth) {
        this.maxDepth = maxDepth;
        return this;
    }

    private PathHierarchyAggregationBuilder depth(int depth) {
        this.depth = depth;
        return this;
    }

    /** Set the order in which the buckets will be returned. It returns the builder so that calls
     *  can be chained. A tie-breaker may be added to avoid non-deterministic ordering. */
    private PathHierarchyAggregationBuilder order(BucketOrder order) {
        if (order == null) {
            throw new IllegalArgumentException("[order] must not be null: [" + name + "]");
        }
        if(order instanceof InternalOrder.CompoundOrder || InternalOrder.isKeyOrder(order)) {
            this.order = order; // if order already contains a tie-breaker we are good to go
        } else { // otherwise add a tie-breaker by using a compound order
            this.order = BucketOrder.compound(order);
        }
        return this;
    }

    private PathHierarchyAggregationBuilder order(List<BucketOrder> orders) {
        if (orders == null) {
            throw new IllegalArgumentException("[orders] must not be null: [" + name + "]");
        }
        // if the list only contains one order use that to avoid inconsistent xcontent
        order(orders.size() > 1 ? BucketOrder.compound(orders) : orders.get(0));
        return this;
    }

    @Override
    protected ValuesSourceAggregatorFactory<ValuesSource, ?> innerBuild(
            SearchContext context,
            ValuesSourceConfig<ValuesSource> config,
            AggregatorFactory<?> parent,
            AggregatorFactories.Builder subFactoriesBuilder) throws IOException {

        if (minDepth > maxDepth)
            throw new IllegalArgumentException("[minDepth] (" + minDepth + ") must not be greater than [maxDepth] (" +
                    maxDepth + ")");

        return new PathHierarchyAggregatorFactory(
                name, config,
                separator, minDepth, maxDepth,
                order,
                context, parent, subFactoriesBuilder, metaData);
    }

    @Override
    protected XContentBuilder doXContentBody(XContentBuilder builder, Params params) throws IOException {
        builder.startObject();

        if (order != null) {
            builder.field(ORDER_FIELD.getPreferredName());
            order.toXContent(builder, params);
        }

        if (!separator.equals(DEFAULT_SEPARATOR)) {
            builder.field(SEPARATOR_FIELD.getPreferredName(), separator);
        }

        if (minDepth != DEFAULT_MIN_DEPTH) {
            builder.field(MIN_DEPTH_FIELD.getPreferredName(), minDepth);
        }

        if (maxDepth != DEFAULT_MAX_DEPTH) {
            builder.field(MAX_DEPTH_FIELD.getPreferredName(), maxDepth);
        }

        if (depth != 0) {
            builder.field(DEPTH_FIELD.getPreferredName(), depth);
        }

        return builder.endObject();
    }

    /**
     * Used for caching requests, amongst other things.
     */
    @Override
    protected int innerHashCode() {
        return Objects.hash(separator, minDepth, maxDepth, depth, order);
    }

    @Override
    protected boolean innerEquals(Object obj) {
        PathHierarchyAggregationBuilder other = (PathHierarchyAggregationBuilder) obj;
        return Objects.equals(separator, other.separator)
                && Objects.equals(minDepth, other.minDepth)
                && Objects.equals(maxDepth, other.maxDepth)
                && Objects.equals(depth, other.depth)
                && Objects.equals(order, other.order);
    }

    @Override
    public String getType() {
        return NAME;
    }
}
