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
package edu.brown.stream.SparkBatchVoter;

import org.apache.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONObject;
import org.voltdb.SysProcSelector;
import org.voltdb.VoltSystemProcedure;
import org.voltdb.VoltTable;
import org.voltdb.VoltType;
import org.voltdb.catalog.Catalog;
import org.voltdb.catalog.Database;
import org.voltdb.catalog.ProcParameter;
import org.voltdb.catalog.Procedure;
import org.voltdb.catalog.Site;
import org.voltdb.client.Client;
import org.voltdb.client.ClientFactory;
import org.voltdb.client.ClientResponse;
import org.voltdb.client.NoConnectionsException;
import org.voltdb.sysprocs.DatabaseDump;
import org.voltdb.sysprocs.EvictHistory;
import org.voltdb.sysprocs.EvictedAccessHistory;
import org.voltdb.sysprocs.Quiesce;
import org.voltdb.sysprocs.Statistics;
import org.voltdb.types.TimestampType;
import org.voltdb.utils.VoltTableUtil;
import org.voltdb.utils.VoltTypeUtil;

import edu.brown.catalog.CatalogUtil;
import edu.brown.hstore.HStoreConstants;
import edu.brown.hstore.Hstoreservice.Status;
import edu.brown.hstore.conf.HStoreConf;
import edu.brown.logging.LoggerUtil.LoggerBoolean;
import edu.brown.statistics.ObjectHistogram;
import edu.brown.stream.AnotherArgumentsParser;
import edu.brown.stream.Batch;
import edu.brown.stream.BatchProducer;
import edu.brown.stream.BatchRunnerResults;
import edu.brown.stream.FinalResult;
import edu.brown.stream.Tuple;
import edu.brown.terminal.HStoreTerminal;
import edu.brown.terminal.HStoreTerminal.Command;
import edu.brown.utils.ArgumentsParser;
import edu.brown.utils.CollectionUtil;
import edu.brown.utils.MathUtil;
import edu.brown.utils.StringUtil;

import java.io.File;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.TreeMap;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.text.*;
import java.util.*;


public class InputClient implements Runnable {
    //
    private BlockingQueue<BatchRunnerResults> batchResultQueue = new LinkedBlockingQueue<BatchRunnerResults>();

    private int batchRounds = 10;

    private boolean json = false;
    private boolean display = false;
    private BatchRunnerResults finalResult = null;
    private boolean stop = false;
    
    // ---------------------------------------------------------------
    // CONSTRUCTOR
    // ---------------------------------------------------------------
    
    public InputClient() throws Exception{

    }
    
    public void setBatchRounds(int rounds) {
        this.batchRounds  = rounds;
    }
    
    private void setResultFormat(boolean json) {
        this.json  = json;
    }
    
    private void setDisplay(boolean display)
    {
        this.display = display;
    }
    
    public void setStop(boolean stop)
    {
        this.stop = stop;
    }
    
    @Override
    public void run() {
        try {
            long i = 0;
            BatchRunnerResults batchresult = null;

            StringBuilder sb = new StringBuilder();
            final int width = 80;
            sb.append(String.format("\n%s\n", StringUtil.repeat("=", width)));
            String strOutput = sb.toString();
            System.out.println(strOutput);

            
            while (true) {
                
                if (i == this.batchRounds)
                    break;

                batchresult = batchResultQueue.take();
                if(batchresult!=null)
                {
                    finalResult = batchresult;

                    if(display==true)
                    {
                        int size = batchresult.sizes.get((Long)i);
                        int latency = batchresult.latencies.get((Long)i);
                        int clusterlatency = batchresult.clusterlatencies.get((Long)i);
                        double batchthroughput = batchresult.batchthroughputs.get((Long)i);
                        double throughput = batchresult.throughputs.get((Long)i);
                        double clientbatchthroughput = batchresult.clientbatchthroughputs.get((Long)i);
                        double clientthroughput = batchresult.clientthroughputs.get((Long)i);
                        strOutput = "batch id:" + String.format("%2d", i);
                        strOutput += " size:" + String.format("%4d", size);
                        strOutput += " client latency:" + String.format("%4d", latency) + "ms";
                        strOutput += " cluster latency:" + String.format("%3d", clusterlatency) + "ms";
                        strOutput += " cluster #batch/s:" + String.format("%6.2f", batchthroughput);
                        strOutput += " #tuple/s:" + String.format("%6.2f", throughput);
                        strOutput += " client #batch/s:" + String.format("%6.2f", clientbatchthroughput);
                        strOutput += " #tuple/s:" + String.format("%6.2f", clientthroughput);
                        strOutput += " ";
                        System.out.println(strOutput);
                    }
    
                    i++;
                }
                else
                {
                    System.out.println("InputClient: run empty result - strange !");
                }
            }

            if(display==true)
                outputFinalResult(finalResult);

        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    public void outputFinalResult(BatchRunnerResults batchresult)
    {
        
        FinalResult finalResult = new FinalResult(batchresult);
        
        String strOutput = finalResult.generateNormalOutputFormat();
        
        // print out the final result
        System.out.println(strOutput);
        
        // if needed we can generate json results for experiments
        if(this.json==true)
        {
            strOutput = "\n\n" + finalResult.generateJSONOutputFormat();
            System.out.println(strOutput);
        }
        
    }

    public static void main(String vargs[]) throws Exception {

        AnotherArgumentsParser args = AnotherArgumentsParser.load( vargs );
        
        InputClient ic = new InputClient();

        boolean display = false; 
        if (args.hasParam(AnotherArgumentsParser.PARAM_RESULT_DISPLAY)) {
            display = args.getBooleanParam(AnotherArgumentsParser.PARAM_RESULT_DISPLAY);
        }
        
        int inverval = 1000; // ms
        if (args.hasParam(AnotherArgumentsParser.PARAM_BATCH_INTERVAL)) {
            inverval = args.getIntParam(AnotherArgumentsParser.PARAM_BATCH_INTERVAL);
        }

        int rounds = 10; // ms
        if (args.hasParam(AnotherArgumentsParser.PARAM_BATCH_ROUNDS)) {
            rounds = args.getIntParam(AnotherArgumentsParser.PARAM_BATCH_ROUNDS);
        }

        String filename = "votes-o-40000.ser";
        if (args.hasParam(AnotherArgumentsParser.PARAM_SOURCE_FILE)) {
            filename = args.getParam(AnotherArgumentsParser.PARAM_SOURCE_FILE);
        }

        int sendrate = 1000; // tuple/s
        if (args.hasParam(AnotherArgumentsParser.PARAM_SOURCE_SENDRATE)) {
            sendrate = args.getIntParam(AnotherArgumentsParser.PARAM_SOURCE_SENDRATE);
        }

        boolean sendstop = false; 
        if (args.hasParam(AnotherArgumentsParser.PARAM_SOURCE_SENDSTOP)) {
            sendstop = args.getBooleanParam(AnotherArgumentsParser.PARAM_SOURCE_SENDSTOP);
        }
        
        boolean json = false; 
        if (args.hasParam(AnotherArgumentsParser.PARAM_RESULT_JSON)) {
            json = args.getBooleanParam(AnotherArgumentsParser.PARAM_RESULT_JSON);
        }

        BatchRunner batchRunner = new BatchRunner(ic, ic.batchResultQueue, rounds, display);
        batchRunner.setCatalog(args.catalog);
        
        // HOSTNAME
        if (args.hasParam(AnotherArgumentsParser.ORIGIN_TERMINAL_HOST)) {
            batchRunner.setHosts(args.getParam(AnotherArgumentsParser.ORIGIN_TERMINAL_HOST));
        }
        // PORT
        if (args.hasParam(AnotherArgumentsParser.ORIGIN_TERMINAL_PORT)) {
            batchRunner.setPort(args.getIntParam(AnotherArgumentsParser.ORIGIN_TERMINAL_PORT));
        }
        
        batchRunner.setEntrySPName("Vote");
        
        //BlockingQueue<Batch> batchQueue = new LinkedBlockingQueue<Batch>();
        BatchProducer batchProducer = new BatchProducer(batchRunner.batchQueue, inverval);
        VoteProducer voteProducer = new VoteProducer(batchProducer.queue, filename, sendrate, sendstop);
        
        //starting producer to produce messages in queue
        new Thread(voteProducer).start();
        
        // starting batch producer to manager tuples in batch
        new Thread(batchProducer).start();

        // starting batch runner
        new Thread(batchRunner).start();
        
        // start inputclient monitor
        ic.setBatchRounds(rounds);
        ic.setResultFormat(json);
        ic.setDisplay(display);
        
        ic.run();
        
        voteProducer.stop();
        batchRunner.stop();
        
    }




}
