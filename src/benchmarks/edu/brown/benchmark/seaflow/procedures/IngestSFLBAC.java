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
package edu.brown.benchmark.seaflow.procedures;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;

import org.voltdb.ProcInfo;
import org.voltdb.SQLStmt;
import org.voltdb.VoltProcedure;
import org.voltdb.VoltTable;

import edu.brown.benchmark.seaflow.SeaflowConstants;

//@ProcInfo (
//		singlePartition = true,
//		partitionInfo = "sfl_tbl.a_cruise_id:2"
//)
public class IngestSFLBAC extends VoltProcedure
{
    public final SQLStmt addSFL = //new SQLStmt("UPDATE argo_tbl SET a_lat = ? WHERE a_lat = ? AND a_lon = ? and a_month = ? and a_depth = ?;");
    		new SQLStmt("INSERT INTO SFL_tbl (s_id, s_cruise, s_date ,s_lat, s_lon, s_salinity, s_ocean_tmp, s_par, s_epoch_ms) "
    				+ "VALUES (?,?,?,?,?,?,?,?,?);"
    			);
    
    public final SQLStmt addBAC = //new SQLStmt("UPDATE argo_tbl SET a_lat = ? WHERE a_lat = ? AND a_lon = ? and a_month = ? and a_depth = ?;");
    		new SQLStmt("INSERT INTO BAC_tbl (b_id, b_cruise, b_date ," +
    				"b_prochloro_conc, b_synecho_conc, b_picoeuk_conc, b_beads_conc, " +
    				"b_prochloro_size, b_synecho_size, b_picoeuk_size, b_beads_size, b_epoch_ms) " +
    				"VALUES (?,?,?," +
    				"?,?,?,?" +
    				",?,?,?,?,?);"
    			);
    
    public final SQLStmt avgSFL = 
    		new SQLStmt("SELECT s_id, s_cruise, MIN(s_date), AVG(s_lat), AVG(s_lon), AVG(s_salinity), AVG(s_ocean_tmp), AVG(s_par), MIN(s_epoch_ms) "
    				+ "FROM SFL_tbl GROUP BY s_id, s_cruise;"
    			);
    
    public final SQLStmt avgBAC = 
    		new SQLStmt("SELECT b_id, b_cruise, " +
    				"AVG(b_prochloro_conc), AVG(b_synecho_conc), AVG(b_picoeuk_conc), AVG(b_beads_conc), " +
    				"AVG(b_prochloro_size), AVG(b_synecho_size), AVG(b_picoeuk_size), AVG(b_beads_size) "
    				+ "FROM BAC_tbl GROUP BY b_id, b_cruise;"
    			);
    
    public final SQLStmt deleteSFL =
    		new SQLStmt("DELETE from SFL_tbl;");
    
    public final SQLStmt deleteBAC =
    		new SQLStmt("DELETE from BAC_tbl;");
    
	
    public long run(long batch_id, String[] sfl, String[] bac) {
    	int i = 0;
    	for(String tuple : sfl) {
    		try {
    			//voltQueueSQL(addSFL,batch_id,"ShipA","8/5/2015 6:48:57 AM",-0.05,-0.05,1.1,1.2,1.3);
    		
	    		String splTuple[] = tuple.split(",");
	    		String s_cruise = splTuple[0];
	    		String s_date = splTuple[1];
	    		Double s_lat = splTuple[2].equals("") ? null : new Double(splTuple[2]);
	    		Double s_lon = splTuple[3].equals("") ? null : new Double(splTuple[3]);
	    		Double s_salinity = splTuple[4].equals("") ? null : new Double(splTuple[4]);
	    		Double s_ocean_tmp = splTuple[5].equals("") ? null : new Double(splTuple[5]);
	    		Double s_par = splTuple[6].equals("") ? null : new Double(splTuple[6]);
	    		
	    	    SimpleDateFormat df = new SimpleDateFormat("M/d/yyyy H:mm:ss a");
	    	    Date date = df.parse(s_date);
			
	    	    long s_epoch_ms = date.getTime();
	    	    voltQueueSQL(addSFL,batch_id,s_cruise,s_date,s_lat,s_lon,
	    	    		s_salinity,s_ocean_tmp,s_par,s_epoch_ms);
	    	    if(i > SeaflowConstants.MAX_SQL_BATCH){
	    	    	voltExecuteSQL();
	    	    	i = 0;
	    	    	continue;
	    	    }
	    	    i++;
    		} catch (ParseException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
    	}
    	
    	i = 0;
    	for(String tuple : bac) {
    		try {
    			//voltQueueSQL(addSFL,batch_id,"ShipA","8/5/2015 6:48:57 AM",-0.05,-0.05,1.1,1.2,1.3);
    		
	    		String splTuple[] = tuple.split(",");
	    		String b_cruise = splTuple[0];
	    		String b_date = splTuple[1];
	    		Double b_prochloro_conc = splTuple[2].equals("") ? null : new Double(splTuple[2]);
	    		Double b_synecho_conc = splTuple[3].equals("") ? null : new Double(splTuple[3]);
	    		Double b_picoeuk_conc = splTuple[4].equals("") ? null : new Double(splTuple[4]);
	    		Double b_beads_conc = splTuple[5].equals("") ? null : new Double(splTuple[5]);
	    		Double b_prochloro_size = splTuple[2].equals("") ? null : new Double(splTuple[6]);
	    		Double b_synecho_size = splTuple[3].equals("") ? null : new Double(splTuple[7]);
	    		Double b_picoeuk_size = splTuple[4].equals("") ? null : new Double(splTuple[8]);
	    		Double b_beads_size = splTuple[5].equals("") ? null : new Double(splTuple[9]);
	    		
	    	    SimpleDateFormat df = new SimpleDateFormat("M/d/yyyy H:mm:ss a");
	    	    Date date = df.parse(b_date);
			
	    	    long b_epoch_ms = date.getTime();
	    	    voltQueueSQL(addBAC,batch_id,b_cruise,b_date,
	    	    		b_prochloro_conc,b_synecho_conc,b_picoeuk_conc,b_beads_conc,
	    	    		b_prochloro_size,b_synecho_size,b_picoeuk_size,b_beads_size,b_epoch_ms);
	    	    if(i > SeaflowConstants.MAX_SQL_BATCH){
	    	    	voltExecuteSQL();
	    	    	i = 0;
	    	    	continue;
	    	    }
	    	    i++;
    		} catch (ParseException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
    	}
	    voltExecuteSQL();
	    voltQueueSQL(avgSFL);
	    voltQueueSQL(avgBAC);
	    voltQueueSQL(deleteSFL);
	    voltQueueSQL(deleteSFL);
	    VoltTable v[] = voltExecuteSQL();
	    
	    
	   
        return 0;
    }
}