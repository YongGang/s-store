/* This file is part of VoltDB.
* Copyright (C) 2008-2010 VoltDB L.L.C.
*
* This file contains original code and/or modifications of original code.
* Any modifications made by VoltDB L.L.C. are licensed under the following
* terms and conditions:
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
/* Copyright (C) 2008 by H-Store Project
* Brown University
* Massachusetts Institute of Technology
* Yale University
*
* Permission is hereby granted, free of charge, to any person obtaining
* a copy of this software and associated documentation files (the
* "Software"), to deal in the Software without restriction, including
* without limitation the rights to use, copy, modify, merge, publish,
* distribute, sublicense, and/or sell copies of the Software, and to
* permit persons to whom the Software is furnished to do so, subject to
* the following conditions:
*
* The above copyright notice and this permission notice shall be
* included in all copies or substantial portions of the Software.
*
* THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
* EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
* MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT
* IN NO EVENT SHALL THE AUTHORS BE LIABLE FOR ANY CLAIM, DAMAGES OR
* OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE,
* ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR
* OTHER DEALINGS IN THE SOFTWARE.
*/

/* Copyright (C) 2017 by S-Store Project
 * Brown University
 * Massachusetts Institute of Technology
 * Portland State University 
 *
 * Author: S-Store Team (sstore.cs.brown.edu)
 *
 * Permission is hereby granted, free of charge, to any person obtaining
 * a copy of this software and associated documentation files (the
 * "Software"), to deal in the Software without restriction, including
 * without limitation the rights to use, copy, modify, merge, publish,
 * distribute, sublicense, and/or sell copies of the Software, and to
 * permit persons to whom the Software is furnished to do so, subject to
 * the following conditions:
 *
 * The above copyright notice and this permission notice shall be
 * included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT
 * IN NO EVENT SHALL THE AUTHORS BE LIABLE FOR ANY CLAIM, DAMAGES OR
 * OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE,
 * ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR
 * OTHER DEALINGS IN THE SOFTWARE.
 */

#include "insertexecutor.h"
#include "common/debuglog.h"
#include "common/ValuePeeker.hpp"
#include "common/tabletuple.h"
#include "common/FatalException.hpp"
#include "plannodes/insertnode.h"
#include "execution/VoltDBEngine.h"
#include "storage/persistenttable.h"
#include "storage/streamedtable.h"
#include "storage/table.h"
#include "storage/tableiterator.h"
#include "storage/tableutil.h"
#include "storage/tablefactory.h"
#include "storage/temptable.h"
#include "common/types.h"
#include "storage/WindowTable.h"
#include "streaming/WindowTableTemp.h"
#include <sys/time.h>
#include <time.h>
#include <cassert>


#ifdef ARIES
#include "logging/Logrecord.h"
#endif

namespace voltdb {

	bool InsertExecutor::p_init(AbstractPlanNode *abstract_node, const catalog::Database* catalog_db, int* tempTableMemoryInBytes) {
		VOLT_TRACE("init Insert Executor");

		m_node = dynamic_cast<InsertPlanNode*>(abstract_node);
		assert(m_node);
		assert(m_node->getTargetTable());
		assert(m_node->getInputTables().size() == 1);

		// create an output table that currently will contain copies of modified tuples
		m_node->setOutputTable(TableFactory::getCopiedTempTable(
			m_node->databaseId(),
			m_node->getInputTables()[0]->name(),
			m_node->getInputTables()[0],
			tempTableMemoryInBytes));

		// 2013-07-29 - PAVLO
		// Sail yo ho, kids. John Meehan and I were here, and we decided that in
		// order to support INSERT INTO..SELECT statements, we had to let the input
		// tables be any kind of table. This is because sometimes for certain SELECTS
		// you will get a handle to the original PersistentTable and not a TempTable.
		// We don't think that this should cause any problems.
		m_inputTable = m_node->getInputTables()[0];
		if (m_inputTable == NULL) {
			VOLT_ERROR("Missing input table for InsertPlanNode #%d [numInputs=%ld]",
				m_node->getPlanNodeId(), m_node->getInputTables().size());
			// VOLT_ERROR("INPUT TABLE DUMP:\n%s", m_node->getInputTables()[0]->debug().c_str());
			return false;
		}
		assert(m_inputTable);

		// Target table can be StreamedTable or PersistentTable and must not be NULL
		m_targetTable = dynamic_cast<Table*>(m_node->getTargetTable());
		assert(m_targetTable);
		assert((m_targetTable == dynamic_cast<PersistentTable*>(m_targetTable)) ||
			(m_targetTable == dynamic_cast<StreamedTable*>(m_targetTable)));

		m_tuple = TableTuple(m_inputTable->schema());

		PersistentTable *persistentTarget = dynamic_cast<PersistentTable*>(m_targetTable);
		m_partitionColumn = -1;
		m_partitionColumnIsString = false;
		if (persistentTarget) {
			m_partitionColumn = persistentTarget->partitionColumn();
			if (m_partitionColumn != -1) {
				if (m_inputTable->schema()->columnType(m_partitionColumn) == voltdb::VALUE_TYPE_VARCHAR) {
					m_partitionColumnIsString = true;
				}
			}
			// TODO: Check if PersistentTable has triggers
			// TODO: If so, then set some flag in InsertExecutor to true
		}
		m_multiPartition = m_node->isMultiPartition();
		return true;
	}


int64_t another_timespecDiffNanoseconds(const timespec& end, const timespec& start) {
//	assert(timespecValid(end));
//	assert(timespecValid(start));
	return (end.tv_nsec - start.tv_nsec) + (end.tv_sec - start.tv_sec) * (int64_t) 1000000000;
}

	bool InsertExecutor::p_execute(const NValueArray &params, ReadWriteTracker *tracker) {
		assert(m_node == dynamic_cast<InsertPlanNode*>(abstract_node));
		assert(m_node);
		// XXX assert(m_inputTable == dynamic_cast<TempTable*>(m_node->getInputTables()[0]));
		assert(m_inputTable);
		assert(m_targetTable);
		VOLT_DEBUG("INPUT TABLE: %s\n", m_inputTable->debug().c_str());
#ifdef DEBUG
		//
		// This should probably just be a warning in the future when we are
		// running in a distributed cluster
		//
		if (m_inputTable->activeTupleCount() == 0) {
			VOLT_ERROR("No tuples were found in our input table '%s'",
				m_inputTable->name().c_str());
			return false;
		}
#endif

    assert (m_inputTable->activeTupleCount() > 0);

    // count the number of successful inserts
    int modifiedTuples = 0;

    Table* outputTable = m_node->getOutputTable();
    assert(outputTable);

    bool beProcessed = false;

	// Implement insert multiple values. Added by hawk, 10/2/2014
	std::vector<Table*> allInputTable = m_node->getInputTables();
	for (int ii = 0; ii < allInputTable.size(); ++ii)
	{
		 m_inputTable = allInputTable[ii];
	// ended by hawk

    //
    // An insert is quite simple really. We just loop through our m_inputTable
    // and insert any tuple that we find into our m_targetTable. It doesn't get any easier than that!
    //
    assert (m_tuple.sizeInValues() == m_inputTable->columnCount());
    TableIterator iterator(m_inputTable);
    while (iterator.next(m_tuple)) {
    	beProcessed = true;
        VOLT_DEBUG("Inserting tuple '%s' into target table '%s'",
                   m_tuple.debug(m_targetTable->name()).c_str(), m_targetTable->name().c_str());
        VOLT_TRACE("Target Table %s: %s",
                   m_targetTable->name().c_str(), m_targetTable->schema()->debug().c_str());


#ifdef ARIES
        if(m_engine->isARIESEnabled()){

        	// add persistency check:
			PersistentTable* table = dynamic_cast<PersistentTable*>(m_targetTable);

			// only log if we are writing to a persistent table.
			if (table != NULL) {
				LogRecord *logrecord = new LogRecord(computeTimeStamp(),
						LogRecord::T_INSERT,	// this is an insert record
						LogRecord::T_FORWARD,// the system is running normally
						-1,// XXX: prevLSN must be fetched from table!
						m_engine->getExecutorContext()->currentTxnId() ,// txn id
						m_engine->getSiteId(),// which execution site
						m_targetTable->name(),// the table affected
						NULL,// insert, no primary key
						-1,// inserting, all columns affected
						NULL,// insert, don't care about modified cols
						NULL,// no before image
						&m_tuple// after image
				);

				size_t logrecordEstLength = logrecord->getEstimatedLength();
				char *logrecordBuffer = new char[logrecordEstLength];

				FallbackSerializeOutput output;
				output.initializeWithPosition(logrecordBuffer, logrecordEstLength, 0);

				logrecord->serializeTo(output);

				LogManager* m_logManager = this->m_engine->getLogManager();
				Logger m_ariesLogger = m_logManager->getAriesLogger();
				//VOLT_WARN("m_logManager : %p AriesLogger : %p",&m_logManager, &m_ariesLogger);
				const Logger *logger = m_logManager->getThreadLogger(LOGGERID_MM_ARIES);

				// output.position() indicates the actual number of bytes written out
				logger->log(LOGLEVEL_INFO, output.data(), output.position());

				delete[] logrecordBuffer;
				logrecordBuffer = NULL;

				delete logrecord;
				logrecord = NULL;
			}

        }
#endif

        // if there is a partition column for the target table
        if (m_partitionColumn != -1) {

            // get the value for the partition column
            NValue value = m_tuple.getNValue(m_partitionColumn);
            bool isLocal = m_engine->isLocalSite(value);

            // if it doesn't map to this site
            if (!isLocal) {
                if (!m_multiPartition) {
                    VOLT_ERROR("Mispartitioned Tuple in single-partition plan.");
                    return false;
                }
                continue;
            }
        }

        // for insert multiple values, added by hawk, 10/2/2014
		// try to put the tuple into the target table
		if (!m_targetTable->insertTuple(m_tuple)) {
			VOLT_ERROR("Failed to insert tuple from input table '%s' into"
				" target table '%s'",
				m_inputTable->name().c_str(),
				m_targetTable->name().c_str());
			return false;
		}

		// try to put the tuple into the output table
		if (!outputTable->insertTuple(m_tuple)) {
			VOLT_ERROR("Failed to insert tuple from input table '%s' into"
				" output table '%s'",
				m_inputTable->name().c_str(),
				outputTable->name().c_str());
			return false;
		}

		// successfully inserted
		modifiedTuples++;

	}

    //for multiple insert values
	}   // ended by hawk

		// Check if the target table is persistent, and if hasTriggers flag is true
		// If it is, then iterate through each one and pass in outputTable
		VOLT_DEBUG("beProcessed = %d", beProcessed);
		if (beProcessed == true)
		{
			PersistentTable* persistTarget = dynamic_cast<PersistentTable*>(m_targetTable);
			VOLT_DEBUG("persistTarget = %s", persistTarget->name().c_str());
			VOLT_DEBUG("persistTarget hasTriggers = %d", persistTarget->hasTriggers());
			VOLT_DEBUG("persistTarget fireTriggers = %d", persistTarget->fireTriggers());
			if(persistTarget != NULL && persistTarget->hasTriggers() && persistTarget->fireTriggers()) {
                              
                                // added by hawk, 2013/12/13, for collect data
/*
                                struct timeval start_;
                                struct timeval delete_start_;
                                struct timeval end_;
                                struct timespec start;
                                struct timespec delete_start;
                                struct timespec end;
                        
                                int error = gettimeofday(&start_, NULL);
                                assert(error == 0);
*/
                                // ended by hawk
				std::vector<Trigger*>::iterator trig_iter;

				VOLT_DEBUG( "Start firing triggers of table '%s'", persistTarget->name().c_str());

				for(trig_iter = persistTarget->getTriggers()->begin();
					trig_iter != persistTarget->getTriggers()->end(); trig_iter++) {
						//if statement to make sure the trigger is an insert... breaking
						//if((*trig_iter)->getType() == (unsigned char)TRIGGER_INSERT)
						//(*trig_iter)->fire(m_engine, outputTable);
					m_engine->fireTrigger(*trig_iter);
				}
				VOLT_DEBUG( "End firing triggers of table '%s'", persistTarget->name().c_str());

                                // to ensure this is a stream, by hawk, 2013.11.25
                                //added by hawk, 2013/12/13, to collect data
                                /*
                                error = gettimeofday(&delete_start_, NULL);
                                assert(error == 0);
                                */
                                //ended by hawk
                                if (persistTarget->isStream() == true)
				    persistTarget->deleteAllTuples(true);

                                //added by hawk, 2013/12/13, to collect data
/*
                                error = gettimeofday(&end_, NULL);
                                assert(error == 0);

                                TIMEVAL_TO_TIMESPEC(&start_, &start);
                                TIMEVAL_TO_TIMESPEC(&delete_start_, &delete_start);
                                TIMEVAL_TO_TIMESPEC(&end_, &end);
                                
                                int64_t latency = another_timespecDiffNanoseconds(end, start);
                                int64_t delete_latency = another_timespecDiffNanoseconds(end, delete_start);
                                persistTarget->add_latency_data(latency, delete_latency);

                                 VOLT_DEBUG("hawk: latency: %ld with delete latency: %ld ...", latency, delete_latency);
*/
                                //ended by hawk
				WindowTable* windowTarget = dynamic_cast<WindowTable*>(persistTarget);
				if(windowTarget != NULL)
				{
					windowTarget->setFireTriggers(false);
				}

				//TODO: more hacks for a new windowTable version....
				WindowTableTemp* windowTargetTemp = dynamic_cast<WindowTableTemp*>(persistTarget);
				if(windowTargetTemp != NULL)
				{
					windowTargetTemp->setFireTriggers(false);
				}
			}

		}


		// add to the planfragments count of modified tuples
		m_engine->m_tuplesModified += modifiedTuples;
		VOLT_DEBUG("Finished inserting %d tuples", modifiedTuples);
		return true;
	}

}
