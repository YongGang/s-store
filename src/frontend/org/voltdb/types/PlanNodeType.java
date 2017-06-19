/* This file is part of VoltDB.
 * Copyright (C) 2008-2010 VoltDB L.L.C.
 *
 * VoltDB is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * VoltDB is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with VoltDB.  If not, see <http://www.gnu.org/licenses/>.
 */

/***************************************************************************
 *  Copyright (C) 2017 by S-Store Project                                  *
 *  Brown University                                                       *
 *  Massachusetts Institute of Technology                                  *
 *  Portland State University                                              *
 *                                                                         *
 *  Author:  The S-Store Team (sstore.cs.brown.edu)                        *
 *                                                                         *
 *                                                                         *
 *  Permission is hereby granted, free of charge, to any person obtaining  *
 *  a copy of this software and associated documentation files (the        *
 *  "Software"), to deal in the Software without restriction, including    *
 *  without limitation the rights to use, copy, modify, merge, publish,    *
 *  distribute, sublicense, and/or sell copies of the Software, and to     *
 *  permit persons to whom the Software is furnished to do so, subject to  *
 *  the following conditions:                                              *
 *                                                                         *
 *  The above copyright notice and this permission notice shall be         *
 *  included in all copies or substantial portions of the Software.        *
 *                                                                         *
 *  THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,        *
 *  EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF     *
 *  MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. *
 *  IN NO EVENT SHALL THE AUTHORS BE LIABLE FOR ANY CLAIM, DAMAGES OR      *
 *  OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE,  *
 *  ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR  *
 *  OTHER DEALINGS IN THE SOFTWARE.                                        *
 ***************************************************************************/

package org.voltdb.types;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;

import org.voltdb.plannodes.AbstractPlanNode;
import org.voltdb.plannodes.AggregatePlanNode;
import org.voltdb.plannodes.DeletePlanNode;
import org.voltdb.plannodes.DistinctPlanNode;
import org.voltdb.plannodes.HashAggregatePlanNode;
import org.voltdb.plannodes.IndexScanPlanNode;
import org.voltdb.plannodes.InsertPlanNode;
import org.voltdb.plannodes.LimitPlanNode;
import org.voltdb.plannodes.MaterializePlanNode;
import org.voltdb.plannodes.NestLoopIndexPlanNode;
import org.voltdb.plannodes.NestLoopPlanNode;
import org.voltdb.plannodes.OrderByPlanNode;
import org.voltdb.plannodes.ProjectionPlanNode;
import org.voltdb.plannodes.ReceivePlanNode;
import org.voltdb.plannodes.SendPlanNode;
import org.voltdb.plannodes.SeqScanPlanNode;
import org.voltdb.plannodes.UnionPlanNode;
import org.voltdb.plannodes.UpdatePlanNode;
import org.voltdb.plannodes.UpsertPlanNode;


/**
 *
 */
public enum PlanNodeType {
    INVALID         (0, null), // for parsing...

    //
    // Scan Nodes
    //
    SEQSCAN         (10, SeqScanPlanNode.class),
    INDEXSCAN       (11, IndexScanPlanNode.class),

    //
    // Join Nodes
    //
    NESTLOOP        (20, NestLoopPlanNode.class),
    NESTLOOPINDEX   (21, NestLoopIndexPlanNode.class),

    //
    // Operator Nodes
    //
    UPDATE          (30, UpdatePlanNode.class),
    INSERT          (31, InsertPlanNode.class),
    DELETE          (32, DeletePlanNode.class),
    UPSERT          (33, UpsertPlanNode.class),

    //
    // Communication Nodes
    //
    SEND            (40, SendPlanNode.class),
    RECEIVE         (41, ReceivePlanNode.class),

    //
    // Misc Nodes
    //
    AGGREGATE       (50, AggregatePlanNode.class),
    HASHAGGREGATE   (51, HashAggregatePlanNode.class),
    UNION           (52, UnionPlanNode.class),
    ORDERBY         (53, OrderByPlanNode.class),
    PROJECTION      (54, ProjectionPlanNode.class),
    MATERIALIZE     (55, MaterializePlanNode.class),
    LIMIT           (56, LimitPlanNode.class),
    DISTINCT        (57, DistinctPlanNode.class)

    ;

    private final int val;
    private final Class<? extends AbstractPlanNode> planNodeClass;

    private PlanNodeType(int val, Class<? extends AbstractPlanNode> planNodeClass) {
        this.val = val;
        this.planNodeClass = planNodeClass;
    }

    public int getValue() {
        return val;
    }

    public Class<? extends AbstractPlanNode> getPlanNodeClass() {
        return planNodeClass;
    }

    protected static final Map<Integer, PlanNodeType> idx_lookup = new HashMap<Integer, PlanNodeType>();
    protected static final Map<String, PlanNodeType> name_lookup = new HashMap<String, PlanNodeType>();
    static {
        for (PlanNodeType vt : EnumSet.allOf(PlanNodeType.class)) {
            PlanNodeType.idx_lookup.put(vt.val, vt);
            PlanNodeType.name_lookup.put(vt.name().toLowerCase().intern(), vt);
        }
    }

    public static Map<Integer, PlanNodeType> getIndexMap() {
        return idx_lookup;
    }

    public static Map<String, PlanNodeType> getNameMap() {
        return name_lookup;
    }

    public static PlanNodeType get(Integer idx) {
        PlanNodeType ret = PlanNodeType.idx_lookup.get(idx);
        return (ret == null ? PlanNodeType.INVALID : ret);
    }

    public static PlanNodeType get(String name) {
        PlanNodeType ret = PlanNodeType.name_lookup.get(name.toLowerCase().intern());
        return (ret == null ? PlanNodeType.INVALID : ret);
    }
}
