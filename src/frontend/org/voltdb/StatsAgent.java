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

package org.voltdb;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;

/**
 * Agent responsible for collecting stats on this host.
 *
 */
public class StatsAgent {

    private final HashMap<SysProcSelector, HashMap<Integer, ArrayList<StatsSource>>> registeredStatsSources =
        new HashMap<SysProcSelector, HashMap<Integer, ArrayList<StatsSource>>>();

    private final HashSet<SysProcSelector> handledSelectors = new HashSet<SysProcSelector>();

    public StatsAgent() {
        SysProcSelector selectors[] = SysProcSelector.values();
        for (int ii = 0; ii < selectors.length; ii++) {
            registeredStatsSources.put(selectors[ii], new HashMap<Integer, ArrayList<StatsSource>>());
        }
        handledSelectors.add(SysProcSelector.PROCEDURE);
    }

    public synchronized void registerStatsSource(SysProcSelector selector, int catalogId, StatsSource source) {
        assert selector != null;
        assert source != null;
        final HashMap<Integer, ArrayList<StatsSource>> catalogIdToStatsSources = registeredStatsSources.get(selector);
        assert catalogIdToStatsSources != null;
        ArrayList<StatsSource> statsSources = catalogIdToStatsSources.get(catalogId);
        if (statsSources == null) {
            statsSources = new ArrayList<StatsSource>();
            catalogIdToStatsSources.put(catalogId, statsSources);
        }
        statsSources.add(source);
    }
    
    
    // added by hawk, 2013/11/25
//    public synchronized StatsSource getTransactionStatsSources(int txnId)
//    {
//        final HashMap<Integer, ArrayList<StatsSource>> catalogIdToStatsSources = registeredStatsSources.get(SysProcSelector.PROCEDURE);
//        if (catalogIdToStatsSources==null)
//            return null;
//        
//        assert catalogIdToStatsSources != null;
//        if(catalogIdToStatsSources.get(txnId)==null)
//            return null;
//        
//        assert(catalogIdToStatsSources.get(txnId) != null) :
//            "Invalid stats source type '" + SysProcSelector.PROCEDURE + "'";
//        ArrayList<StatsSource> statsSources = catalogIdToStatsSources.get(txnId);
//        StatsSource source = statsSources.get(0);
//        assert(source != null) :
//            "Invalid stats source '" + SysProcSelector.PROCEDURE + "' with id - " + Integer.toString(txnId);
//        return source;
//    }
    // ended by hawk

    public synchronized VoltTable getStats(
            final SysProcSelector selector,
            final ArrayList<Integer> catalogIds,
            final boolean interval,
            final Long now) {
        assert selector != null;
        assert catalogIds != null;
        assert catalogIds.size() > 0;
        final HashMap<Integer, ArrayList<StatsSource>> catalogIdToStatsSources = registeredStatsSources.get(selector);
        assert catalogIdToStatsSources != null;

        assert(catalogIdToStatsSources.get(catalogIds.get(0)) != null) :
            "Invalid stats source type '" + selector + "'";
        ArrayList<StatsSource> statsSources = catalogIdToStatsSources.get(catalogIds.get(0));
        assert statsSources != null && statsSources.size() > 0;
        final VoltTable.ColumnInfo columns[] = statsSources.get(0).getColumnSchema().toArray(new VoltTable.ColumnInfo[0]);
        final VoltTable resultTable = new VoltTable(columns);

        for (Integer catalogId : catalogIds) {
            statsSources = catalogIdToStatsSources.get(catalogId);
            assert statsSources != null;
            for (final StatsSource ss : statsSources) {
                assert ss != null;
                Object statsRows[][] = ss.getStatsRows(interval, now);
                for (Object[] row : statsRows) {
                    resultTable.addRow(row);
                }
            }
        }
        return resultTable;
    }
}
