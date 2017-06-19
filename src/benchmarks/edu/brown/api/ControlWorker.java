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

package edu.brown.api;

import java.io.IOException;

import org.apache.log4j.Logger;
import org.voltdb.client.Client;

import edu.brown.logging.LoggerUtil;
import edu.brown.logging.LoggerUtil.LoggerBoolean;
import edu.brown.profilers.ProfileMeasurement;
import edu.brown.utils.ThreadUtil;

/**
 * Thread that executes the derives classes run loop which invokes stored
 * procedures indefinitely
 */
class ControlWorker extends Thread {
    private static final Logger LOG = Logger.getLogger(ControlWorker.class);
    private static final LoggerBoolean debug = new LoggerBoolean();
    static {
        LoggerUtil.setupLogging();
        LoggerUtil.attachObserver(LOG, debug);
    }
    
    /**
     * 
     */
    private final BenchmarkComponent cmp;
    
    /**
     * Time in milliseconds since requests were last sent.
     */
    private long m_lastRequestTime;

    private boolean profiling = false;
    private ProfileMeasurement execute_time = new ProfileMeasurement("EXECUTE");
    private ProfileMeasurement block_time = new ProfileMeasurement("BLOCK");
    
    
    /**
     * Constructor
     * @param benchmarkComponent
     */
    public ControlWorker(BenchmarkComponent benchmarkComponent) {
        cmp = benchmarkComponent;
    }

    @Override
    public void run() {
        Thread self = Thread.currentThread();
        self.setName(String.format("worker-%03d", cmp.getClientId()));
        
        cmp.invokeStartCallback();
        try {
            if (cmp.m_txnRate == -1) {
                if (cmp.m_sampler != null) {
                    cmp.m_sampler.start();
                }
                cmp.runLoop();
            } else {
                if (debug.val) LOG.debug(String.format("Running rate controlled [m_txnRate=%d, m_txnsPerMillisecond=%f]", cmp.m_txnRate, cmp.m_txnsPerMillisecond));
                this.rateControlledRunLoop();
            }
        } catch (Throwable ex) {
            ex.printStackTrace();
            throw new RuntimeException(ex);
        } finally {
            if (cmp.m_exitOnCompletion) {
                if (debug.val) LOG.debug(String.format("Stopping %s thread [id=%d]",
                                           this.getClass().getSimpleName(), cmp.getClientId()));
                        
                return;
            }
        }
    }

    private void rateControlledRunLoop() throws InterruptedException {
        final Client client = cmp.getClientHandle();
        m_lastRequestTime = System.currentTimeMillis();
        
        boolean hadErrors = false;
        boolean bp = false;
        
        //added by hawk, 2014/1/2, to make the number of fixed txns used by benchmark running
//        boolean fixed_txns = cmp.m_fixed_txns;
//        long fixed_txns_count = cmp.m_fixed_txns_count;
//        boolean hasFinished = false;
        //ended by hawk
        
        while (true) {
            // If there is back pressure don't send any requests. Update the
            // last request time so that a large number of requests won't
            // queue up to be sent when there is no longer any back
            // pressure.
            if (bp) {
                if (this.profiling) this.block_time.start();
                try {
                    client.backpressureBarrier();
                } finally {
                    if (this.profiling) this.block_time.stop();
                }
                bp = false;
            }
            
            // Check whether we are currently being paused
            // We will block until we're allowed to go again
            if (cmp.m_controlState == ControlState.PAUSED) {
                if (debug.val) LOG.debug("Pausing until control lock is released");
                cmp.m_pauseLock.acquire();
                if (debug.val) LOG.debug("Control lock is released! Resuming execution! Tiger style!");
            }
            assert(cmp.m_controlState != ControlState.PAUSED) : "Unexpected " + cmp.m_controlState;

            // Generate the correct number of transactions based on how much
            // time has passed since the last time transactions were sent.
            final long now = System.currentTimeMillis();
            final long delta = now - m_lastRequestTime;
            if (delta > 0) {
                final int transactionsToCreate = (int) (delta * cmp.m_txnsPerMillisecond);
                if (transactionsToCreate < 1) {
                    Thread.sleep(25);
                    continue;
                }

                if (debug.val) LOG.debug(String.format("Submitting %d txn requests from client #%d",
                                           transactionsToCreate, cmp.getClientId()));
                if (this.profiling) execute_time.start();
                try {
                    for (int ii = 0; ii < transactionsToCreate; ii++) {
                        bp = !cmp.runOnce();
                        //added by hawk, 2014/1/2
//                        System.out.println("hawkwang - worker cmp.runOnce()...");
//                        if (fixed_txns == true) {
//                            fixed_txns_count--;
//                            if(fixed_txns_count==0)
//                            {
//                                hasFinished = true;
//                                break;
//                            }
//                        }
                        //ended by hawk
                        if (bp || cmp.m_controlState != ControlState.RUNNING) {
                            break;
                        }
                    } // FOR
                } catch (final IOException e) {
                    if (hadErrors) return;
                    hadErrors = true;
                    
                    // HACK: Sleep for a little bit to give time for the site logs to flush
//                    if (debug.val) 
                    LOG.error("Failed to execute transaction: " + e.getMessage(), e);
                    ThreadUtil.sleep(5000);
                } finally {
                    if (this.profiling) execute_time.stop();
                }
            }
            else {
                Thread.sleep(25);
            }

            m_lastRequestTime = now;
            
            //added by hawk, 2014/1/2
//            if ( hasFinished==true ) {
//                //cmp.m_controlState = ControlState.PAUSED;
//                //break;
//            }
            //ended by hawk
        } // WHILE
    }
 
    public void enableProfiling(boolean val) {
        this.profiling = val;
    }
    public ProfileMeasurement getExecuteTime() {
        return (execute_time);
    }
    public ProfileMeasurement getBlockedTime() {
        return (this.block_time);
    }
    
}