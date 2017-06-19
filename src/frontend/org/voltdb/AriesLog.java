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

import java.util.concurrent.atomic.AtomicBoolean;

// ARIES
public abstract class AriesLog implements Runnable {
	protected int logsize;			// in MBs
	protected long fsyncFrequency; 	// in millis
	
	public boolean isInitialized;
	
    public abstract void init();
    public abstract void setTxnIdToBeginReplay(long txnId);
	public abstract long getTxnIdToBeginReplay();
	public abstract boolean isReadyForReplay();
    public abstract void log(byte[] logbytes, AtomicBoolean isDurable);
	public abstract void setRecoveryCompleted(int siteId);
	public abstract boolean isRecoveryCompleted();
	public abstract boolean isRecoveryCompletedForSite(int siteId);
	public abstract void setPointerToReplayLog(long ariesReplayPointer, long size);
	public abstract long getPointerToReplayLog();
	public abstract long getReplayLogSize();
}
