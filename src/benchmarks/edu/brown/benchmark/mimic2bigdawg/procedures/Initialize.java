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
package edu.brown.benchmark.mimic2bigdawg.procedures;

import edu.brown.benchmark.mimic2bigdawg.Mimic2BigDawgConstants;
import org.voltdb.SQLStmt;
import org.voltdb.VoltProcedure;

public class Initialize extends VoltProcedure {
    // Check if the database has already been initialized
    public final SQLStmt insertMedEvents = new SQLStmt(
            "INSERT INTO MEDEVENTS "
                    + "(SUBJECT_ID,ICUSTAY_ID,ITEMID,CHARTTIME,ELEMID,REALTIME,"
                    + "CGID,CUID,VOLUME,DOSE,DOSEUOM,SOLUTIONID,SOLVOLUME,"
                    + "SOLUNITS,ROUTE,STOPPED) "
                    + "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?);"
    );
    
    public static Long longNull(String in) {
    	if(in.equals(""))
    		return null;
    	else
    		return new Long(in);
    }
    
    public static Double doubleNull(String in) {
    	if(in.equals(""))
    		return null;
    	else
    		return new Double(in);
    }

    // The streaming file should not contains columns title
    // Taking one tuple each time
    public long run(String tuple) {
        try {
            String[] st = tuple.split(",", -1);
            voltQueueSQL(insertMedEvents,
            		longNull(st[0]), longNull(st[1]), longNull(st[2]),
                    st[3], longNull(st[4]), st[5], longNull(st[6]),
                    longNull(st[7]), longNull(st[8]), doubleNull(st[9]),
                    st[10], longNull(st[11]), longNull(st[12]),
                    st[13], st[14], st[15]);
            voltExecuteSQL();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return Mimic2BigDawgConstants.PROC_SUCCESSFUL;
    }


    /*
    public long run() {
        try {
            voltQueueSQL(insertMedEvents, 26283, 32687, 45,
                    "2780-11-21 10:00:00 EST",1,"2780-11-21 10:41:00 EST",
                    4467,54,0,5,"Uhr",18,100,"ml","IV Drip", "Stopped");
            voltExecuteSQL();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return 1;
    }
    */
    /*
    public long run(long batchid, String[] tuples, long part_id) {
        try {
            for (int i = 0; i < tuples.length; i++) {
                String[] st = tuples[i].split(",", -1);
                voltQueueSQL(insertMedEvents, new Long(st[0]), new Long(st[1]), new Long(st[2]),
                        st[3], new Long(st[4]), st[5], new Long(st[6]),
                        new Long(st[7]), new Long(st[8]), new Long(st[9]),
                        st[10], new Long(st[11]), new Long(st[12]),
                        st[13], st[14], st[15],
                        batchid, part_id);
                voltExecuteSQL();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return Mimic2BigDawgConstants.PROC_SUCCESSFUL;
    }
    */
}
