/*
 * Copyright (C) 2017-2019 Dremio Corporation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.dremio.common.logical.data;

import java.util.Iterator;
import java.util.List;

import org.apache.calcite.rel.RelFieldCollation.Direction;
import org.apache.calcite.rel.RelFieldCollation.NullDirection;

import com.dremio.common.expression.FieldReference;
import com.dremio.common.expression.LogicalExpression;
import com.dremio.common.logical.data.visitors.LogicalVisitor;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeName;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterators;
import com.google.common.collect.Lists;

@JsonTypeName("order")
public class Order extends SingleInputOperator {

  private final List<Ordering> orderings;
  private final FieldReference within;

  @JsonCreator
  public Order(@JsonProperty("within") FieldReference within, @JsonProperty("orderings") List<Ordering> orderings) {
    this.orderings = orderings;
    this.within = within;
  }

  public List<Ordering> getOrderings() {
    return orderings;
  }

  public FieldReference getWithin() {
    return within;
  }

    @Override
    public <T, X, E extends Throwable> T accept(LogicalVisitor<T, X, E> logicalVisitor, X value) throws E {
        return logicalVisitor.visitOrder(this, value);
    }

    @Override
    public Iterator<LogicalOperator> iterator() {
        return Iterators.singletonIterator(getInput());
    }


  /**
   * Representation of a SQL &lt;sort specification>.
   */
  public static class Ordering {

    private final LogicalExpression expr;
    /** Net &lt;ordering specification>. */
    private final Direction direction;
    /** Net &lt;null ordering> */
    private final NullDirection nullOrdering;
    /** The values in the plans for ordering specification are ASC, DESC, not the
     * full words featured in the calcite {@link Direction} Enum, need to map between them. */
    private static ImmutableMap<String, Direction> DREMIO_TO_CALCITE_DIR_MAPPING =
        ImmutableMap.<String, Direction>builder()
        .put("ASC", Direction.ASCENDING)
        .put("DESC", Direction.DESCENDING).build();
    private static ImmutableMap<String, NullDirection> DREMIO_TO_CALCITE_NULL_DIR_MAPPING =
        ImmutableMap.<String, NullDirection>builder()
            .put("FIRST", NullDirection.FIRST)
            .put("LAST", NullDirection.LAST)
            .put("UNSPECIFIED", NullDirection.UNSPECIFIED).build();

    /**
     * Constructs a sort specification.
     * @param  expr  ...
     * @param  strOrderingSpec  the &lt;ordering specification> as string;
     *             allowed values: {@code "ASC"}, {@code "DESC"}, {@code null};
     *             null specifies default &lt;ordering specification>
     *                   ({@code "ASC"} / {@link Direction#ASCENDING})
     * @param  strNullOrdering   the &lt;null ordering> as string;
     *             allowed values: {@code "FIRST"}, {@code "LAST"},
     *             {@code "UNSPECIFIED"}, {@code null};
     *             null specifies default &lt;null ordering>
     *             (omitted / {@link NullDirection#UNSPECIFIED}, interpreted later)
     */
    @JsonCreator
    public Ordering( @JsonProperty("order") String strOrderingSpec,
                     @JsonProperty("expr") LogicalExpression expr,
                     @JsonProperty("nullDirection") String strNullOrdering ) {
      this.expr = expr;
      this.direction = getOrderingSpecFromString(strOrderingSpec);
      this.nullOrdering = getNullOrderingFromString(strNullOrdering);
    }

    public Ordering(Direction direction, LogicalExpression e, NullDirection nullOrdering) {
      this.expr = e;
      this.direction = filterSupportedDirections(direction);
      this.nullOrdering = nullOrdering;
    }

    public Ordering(Direction direction, LogicalExpression e) {
      this(direction, e, NullDirection.FIRST);
    }

    private static Direction getOrderingSpecFromString( String strDirection ) {
      Direction dir = DREMIO_TO_CALCITE_DIR_MAPPING.get(strDirection);
      if (dir != null || strDirection == null) {
        return filterSupportedDirections(dir);
      } else {
        throw new IllegalArgumentException(
            "Unknown <ordering specification> string (not \"ASC\", \"DESC\", "
                + "or null): \"" + strDirection + "\"" );
      }
    }

    private static NullDirection getNullOrderingFromString( String strNullOrdering ) {
      NullDirection nullDir = DREMIO_TO_CALCITE_NULL_DIR_MAPPING.get(strNullOrdering);
      if (nullDir != null || strNullOrdering == null) {
        return filterSupportedNullDirections(nullDir);
      } else {
        throw new IllegalArgumentException(
            "Internal error:  Unknown <null ordering> string (not "
                + "\"" + NullDirection.FIRST.name() + "\", "
                + "\"" + NullDirection.LAST.name() + "\", or "
                + "\"" + NullDirection.UNSPECIFIED.name() + "\" or null): "
                + "\"" + strNullOrdering + "\"" );
      }
    }

    /**
     * Disallows unsupported values for ordering direction (provided by Calcite but not implemented by Dremio)
     *
     * Provides a default value of ASCENDING in the case of a null.
     *
     * @param direction
     * @return - a sanitized direction value
     */
    private static Direction filterSupportedDirections(Direction direction) {
      if (direction == null || direction == Direction.ASCENDING) {
        return Direction.ASCENDING;
      } else if (Direction.DESCENDING.equals( direction) ) {
        return direction;
      } else {
        throw new IllegalArgumentException(
            "Unknown <ordering specification> string (not \"ASC\", \"DESC\", "
            + "or null): \"" + direction + "\"" );
      }
    }

    /**
     * Disallows unsupported values for null ordering (provided by Calcite but not implemented by Dremio),
     * currently all values are supported.
     *
     * Provides a default value of UNSPECIFIED in the case of a null.
     *
     * @param nullDirection
     * @return - a sanitized direction value
     */
    private static NullDirection filterSupportedNullDirections(NullDirection nullDirection) {
      if ( null == nullDirection) {
        return NullDirection.UNSPECIFIED;
      }
      switch (nullDirection) {
        case LAST:
        case FIRST:
        case UNSPECIFIED:
          return nullDirection;
        default:
          throw new RuntimeException(
              "Internal error:  Unknown <null ordering> string (not "
                  + "\"" + NullDirection.FIRST.name() + "\", "
                  + "\"" + NullDirection.LAST.name() + "\", or "
                  + "\"" + NullDirection.UNSPECIFIED.name() + "\" or null): "
                  + "\"" + nullDirection + "\"" );
      }
   }

    @Override
    public String toString() {
      return
          super.toString()
          + "[ "
          + " expr = " + expr
          + ", direction = " + direction
          + ", nullOrdering = " + nullOrdering
          + "] ";
    }

    @JsonIgnore
    public Direction getDirection() {
      return direction;
    }

    public LogicalExpression getExpr() {
      return expr;
    }

    public String getOrder() {
      switch (direction) {
      case ASCENDING:
        return Direction.ASCENDING.shortString;
      case DESCENDING:
        return Direction.DESCENDING.shortString;
      default:
        throw new RuntimeException(
            "Unexpected " + Direction.class.getName() + " value other than "
            + Direction.ASCENDING + " or " + Direction.DESCENDING + ": "
            + direction );
      }
    }

    public NullDirection getNullDirection() {
      return nullOrdering;
    }

    /**
     * Reports whether NULL sorts high or low in this ordering.
     *
     * @return
     * {@code true}  if NULL sorts higher than any other value;
     * {@code false} if NULL sorts lower  than any other value
     */
    public boolean nullsSortHigh() {
      final boolean nullsHigh;

      switch (nullOrdering) {

      case UNSPECIFIED:
        // Default:  NULL sorts high: like NULLS LAST if ASC, FIRST if DESC.
        nullsHigh = true;
        break;

      case FIRST:
        // FIRST: NULL sorts low with ASC, high with DESC.
        nullsHigh = Direction.DESCENDING == getDirection();
        break;

      case LAST:
        // LAST: NULL sorts high with ASC, low with DESC.
        nullsHigh = Direction.ASCENDING == getDirection();
        break;

      default:
        throw new RuntimeException(
            "Unexpected " + NullDirection.class.getName() + " value other than "
            + NullDirection.FIRST + ", " + NullDirection.LAST + " or " + NullDirection.UNSPECIFIED + ": "
            + nullOrdering );
      }

      return nullsHigh;
    }

  }

  public static Builder builder(){
    return new Builder();
  }

  public static class Builder extends AbstractSingleBuilder<Order, Builder>{
    private List<Ordering> orderings = Lists.newArrayList();
    private FieldReference within;

    public Builder setWithin(FieldReference within){
      this.within = within;
      return this;
    }

    public Builder addOrdering(Direction direction, LogicalExpression e, NullDirection collation){
      orderings.add(new Ordering(direction, e, collation));
      return this;
    }

    @Override
    public Order internalBuild() {
      return new Order(within, orderings);
    }


  }
}
