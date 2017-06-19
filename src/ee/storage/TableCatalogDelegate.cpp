/* This file is part of VoltDB.
 * Copyright (C) 2008-2010 VoltDB Inc.
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

#include "TableCatalogDelegate.hpp"

#include "catalog/catalog.h"
#include "catalog/database.h"
#include "catalog/table.h"
#include "catalog/index.h"
#include "catalog/column.h"
#include "catalog/columnref.h"
#include "catalog/constraint.h"

#include "catalog/catalogmap.h"
#include "catalog/catalogtype.h"
#include "catalog/constraintref.h"

#include "catalog/materializedviewinfo.h"
#include "catalog/trigger.h"
#include "common/CatalogUtil.h"
#include "common/types.h"
#include "indexes/tableindex.h"
#include "storage/constraintutil.h"
#include "storage/MaterializedViewMetadata.h"
#include "storage/persistenttable.h"
#include "storage/StreamBlock.h"
#include "storage/table.h"
#include "storage/tablefactory.h"
#include "triggers/trigger.h"

#include <vector>
#include <map>

#ifdef ANTICACHE
#include "anticache/EvictedTable.h"
#endif

using namespace std;
namespace voltdb {

TableCatalogDelegate::TableCatalogDelegate(int32_t catalogVersion, int32_t catalogId, string path) :
    CatalogDelegate(catalogVersion, catalogId, path), m_table(NULL), m_exportEnabled(false)
{
}

TableCatalogDelegate::~TableCatalogDelegate()
{
    if (m_table) {
        m_table->decrementRefcount();
    }
}

int
TableCatalogDelegate::init(ExecutorContext *executorContext,
                           catalog::Database &catalogDatabase,
                           catalog::Table &catalogTable)
{
    VOLT_DEBUG("Initializing table '%s'", catalogTable.name().c_str());
    int32_t databaseId = catalogDatabase.relativeIndex();
    
    // Create a persistent table for this table in our catalog
    int32_t table_id = catalogTable.relativeIndex();

    // Columns:
    // Column is stored as map<String, Column*> in Catalog. We have to
    // sort it by Column index to preserve column order.
    const int numColumns = static_cast<int>(catalogTable.columns().size());
    vector<ValueType> columnTypes(numColumns);
    vector<int32_t> columnLengths(numColumns);
    vector<bool> columnAllowNull(numColumns);
    string *columnNames = new string[numColumns];
 
    
    map<string, catalog::Column*>::const_iterator col_iterator;
    
    for (col_iterator = catalogTable.columns().begin();
         col_iterator != catalogTable.columns().end(); col_iterator++) 
    {
        const catalog::Column *catalog_column = col_iterator->second;
        const int columnIndex = catalog_column->index();
        const ValueType type = static_cast<ValueType>(catalog_column->type());
        columnTypes[columnIndex] = type;
        const int32_t size = static_cast<int32_t>(catalog_column->size());
        
        //Strings length is provided, other lengths are derived from type
        bool varlength = (type == VALUE_TYPE_VARCHAR);
        const int32_t length = varlength ? size
            : static_cast<int32_t>(NValue::getTupleStorageSize(type));
        columnLengths[columnIndex] = length;
        columnAllowNull[columnIndex] = catalog_column->nullable();
        columnNames[catalog_column->index()] = catalog_column->name();
    }

    TupleSchema *schema = TupleSchema::createTupleSchema(columnTypes,
                                                         columnLengths,
                                                         columnAllowNull, true);

    // Indexes
    map<string, TableIndexScheme> index_map;
    map<string, catalog::Index*>::const_iterator idx_iterator;
    for (idx_iterator = catalogTable.indexes().begin();
         idx_iterator != catalogTable.indexes().end(); idx_iterator++) {
        catalog::Index *catalog_index = idx_iterator->second;
        vector<int> index_columns;
        vector<ValueType> column_types;

        // The catalog::Index object now has a list of columns that are to be used
        if (catalog_index->columns().size() == (size_t)0) {
            VOLT_ERROR("Index '%s' in table '%s' does not declare any columns"
                       " to use",
                       catalog_index->name().c_str(),
                       catalogTable.name().c_str());
            delete [] columnNames;
            return false;
        }

        // Since the columns are not going to come back in the proper order from
        // the catalogs, we'll use the index attribute to make sure we put them
        // in the right order
        index_columns.resize(catalog_index->columns().size());
        column_types.resize(catalog_index->columns().size());
        bool isIntsOnly = true;
        map<string, catalog::ColumnRef*>::const_iterator colref_iterator;
        for (colref_iterator = catalog_index->columns().begin();
             colref_iterator != catalog_index->columns().end();
             colref_iterator++) {
            catalog::ColumnRef *catalog_colref = colref_iterator->second;
            if (catalog_colref->index() < 0) {
                VOLT_ERROR("Invalid column '%d' for index '%s' in table '%s'",
                           catalog_colref->index(),
                           catalog_index->name().c_str(),
                           catalogTable.name().c_str());
                delete [] columnNames;
                return false;
            }
            // check if the column does not have an int type
            if ((catalog_colref->column()->type() != VALUE_TYPE_TINYINT) &&
                (catalog_colref->column()->type() != VALUE_TYPE_SMALLINT) &&
                (catalog_colref->column()->type() != VALUE_TYPE_INTEGER) &&
                (catalog_colref->column()->type() != VALUE_TYPE_BIGINT)) {
                isIntsOnly = false;
            }
            index_columns[catalog_colref->index()] = catalog_colref->column()->index();
            column_types[catalog_colref->index()] = (ValueType) catalog_colref->column()->type();
        }

        TableIndexScheme index_scheme(catalog_index->name(),
                                      (TableIndexType)catalog_index->type(),
                                      index_columns,
                                      column_types,
                                      catalog_index->unique(),
                                      isIntsOnly,
                                      schema);
        index_map[catalog_index->name()] = index_scheme;
    }

    // Constraints
    string pkey_index_id;
    map<string, catalog::Constraint*>::const_iterator constraint_iterator;
    for (constraint_iterator = catalogTable.constraints().begin();
         constraint_iterator != catalogTable.constraints().end();
         constraint_iterator++) {
        catalog::Constraint *catalog_constraint = constraint_iterator->second;

        // Constraint Type
        ConstraintType type = (ConstraintType)catalog_constraint->type();
        switch (type) {
            case CONSTRAINT_TYPE_PRIMARY_KEY:
                // Make sure we have an index to use
                if (catalog_constraint->index() == NULL) {
                    VOLT_ERROR("The '%s' constraint '%s' on table '%s' does"
                               " not specify an index",
                               constraintutil::getTypeName(type).c_str(),
                               catalog_constraint->name().c_str(),
                               catalogTable.name().c_str());
                    delete [] columnNames;
                    return false;
                }
                // Make sure they didn't declare more than one primary key index
                else if (pkey_index_id.size() > 0) {
                    VOLT_ERROR("Trying to declare a primary key on table '%s'"
                               "using index '%s' but '%s' was already set as"
                               " the primary key",
                               catalogTable.name().c_str(),
                               catalog_constraint->index()->name().c_str(),
                               pkey_index_id.c_str());
                    delete [] columnNames;
                    return false;
                }
                pkey_index_id = catalog_constraint->index()->name();
                break;
            case CONSTRAINT_TYPE_UNIQUE:
                // Make sure we have an index to use
                // TODO: In the future I would like bring back my Constraint
                //       object so that we can keep track of everything that a
                //       table has...
                if (catalog_constraint->index() == NULL) {
                    VOLT_ERROR("The '%s' constraint '%s' on table '%s' does"
                               " not specify an index",
                               constraintutil::getTypeName(type).c_str(),
                               catalog_constraint->name().c_str(),
                               catalogTable.name().c_str());
                    delete [] columnNames;
                    return false;
                }
                break;
            // Unsupported
            case CONSTRAINT_TYPE_CHECK:
            case CONSTRAINT_TYPE_FOREIGN_KEY:
            case CONSTRAINT_TYPE_MAIN:
                VOLT_WARN("Unsupported type '%s' for constraint '%s.%s'",
                          constraintutil::getTypeName(type).c_str(),
                          catalogTable.name().c_str(),
                          catalog_constraint->name().c_str());
                break;
            // Unknown
            default:
                VOLT_ERROR("Invalid constraint type '%s' for '%s.%s'",
                           constraintutil::getTypeName(type).c_str(),
                           catalogTable.name().c_str(),
                           catalog_constraint->name().c_str());
                delete [] columnNames;
                return false;
        }
    }

    // Build the index array
    vector<TableIndexScheme> indexes;
    TableIndexScheme pkey_index;
    map<string, TableIndexScheme>::const_iterator index_iterator;
    for (index_iterator = index_map.begin(); index_iterator != index_map.end();
         index_iterator++) {
        // Exclude the primary key
        if (index_iterator->first.compare(pkey_index_id) == 0) {
            pkey_index = index_iterator->second;
        // Just add it to the list
        } else {
            indexes.push_back(index_iterator->second);
        }
    }
    //MEEHAN: adding triggers to the table
    // Build the trigger array
    vector<Trigger*>* triggers = new vector<Trigger*>;
    map<string, catalog::Trigger*>::const_iterator trig_iter;
    for(trig_iter = catalogTable.triggers().begin();
    		trig_iter != catalogTable.triggers().end(); trig_iter++) {
    	catalog::Trigger* curTrig = trig_iter->second;
        VOLT_DEBUG("Initializing table '%s' - begin creating trigger '%s' with no %d", catalogTable.name().c_str(), curTrig->name().c_str(), curTrig->id());
    	Trigger* pushTrig = new Trigger(curTrig->id(), curTrig->name(), &(curTrig->statements()), (unsigned char)(curTrig->triggerType()), curTrig->forEach());
        VOLT_DEBUG("Initializing table '%s' - end creating trigger '%s'", catalogTable.name().c_str(), curTrig->name().c_str());
    	triggers->push_back(pushTrig);
    }

    // partition column:
    const catalog::Column* partitionColumn = catalogTable.partitioncolumn();
    int partitionColumnIndex = -1;
    if (partitionColumn != NULL) {
        partitionColumnIndex = partitionColumn->index();
    }

    // modified by hawk, 2013/11/5
    // determine if this is a window table
    if (catalogTable.isWindow())
    {
        VOLT_DEBUG("Creating WindowTable : '%s'",
                   catalogTable.name().c_str());

        int windowType = TUPLE_WINDOW;
        if(!catalogTable.isRows())
        	windowType = TIME_WINDOW;

        if (pkey_index_id.size() == 0) 
        {
            // FIXME: we need to extend with window type and slide
            m_table = TableFactory::getWindowTable(databaseId, executorContext,
                                                 catalogTable.name(), schema, columnNames,
                                                 indexes, triggers, partitionColumnIndex,
                                                 isExportEnabledForTable(catalogDatabase, table_id),
                                                 isTableExportOnly(catalogDatabase, table_id),
						 catalogTable.size(), catalogTable.slide(), windowType, catalogTable.groupByIndex());
        }
        else
        {
            m_table = TableFactory::getWindowTable(databaseId, executorContext,
                                                 catalogTable.name(), schema, columnNames,
                                                 pkey_index, indexes, triggers, partitionColumnIndex,
                                                 isExportEnabledForTable(catalogDatabase, table_id),
                                                 isTableExportOnly(catalogDatabase, table_id),
						 catalogTable.size(), catalogTable.slide(), windowType, catalogTable.groupByIndex());
        }
    }
    else
    // ended by hawk
    // no primary key
    if (pkey_index_id.size() == 0) {
        m_table = TableFactory::getPersistentTable(databaseId, executorContext,
                                                 catalogTable.name(), schema, columnNames,
                                                 indexes, triggers, partitionColumnIndex,
                                                 isExportEnabledForTable(catalogDatabase, table_id),
                                                 isTableExportOnly(catalogDatabase, table_id));
        
    } else {
        m_table = TableFactory::getPersistentTable(databaseId, executorContext,
                                                 catalogTable.name(), schema, columnNames,
                                                 pkey_index, indexes, triggers, partitionColumnIndex,
                                                 isExportEnabledForTable(catalogDatabase, table_id),
                                                 isTableExportOnly(catalogDatabase, table_id));
    }
    

    // get the stream flag
    bool isStream = catalogTable.isStream();
    m_table->setIsStream(isStream);


    #ifdef ANTICACHE
    // Create evicted table if anti-caching is enabled and this table is marked as evictable
    // is not generated from a materialized view
    if (executorContext->m_antiCacheEnabled && catalogTable.evictable()) {
        if (catalogTable.materializer() != NULL) {
            VOLT_ERROR("Trying to use the anti-caching feature on materialized view '%s'",
                        catalogTable.name().c_str());
            return (false);
        }
        if (catalogTable.mapreduce()) {
            VOLT_ERROR("Trying to use the anti-caching feature on mapreduce output table '%s'",
                        catalogTable.name().c_str());
            return (false);
        }
        
        std::ostringstream stream;
        stream << catalogTable.name() << "__EVICTED";
        const std::string evictedName = stream.str();
        VOLT_INFO("Creating EvictionTable '%s'", evictedName.c_str());
        TupleSchema *evictedSchema = TupleSchema::createEvictedTupleSchema();

        // Get the column names for the EvictedTable
        string *evictedColumnNames = new string[evictedSchema->columnCount()];
        evictedColumnNames[0] = std::string("BLOCK_ID");
        evictedColumnNames[1] = std::string("TUPLE_OFFSET"); 
        
        // TODO: Should we construct a primary key index?
        //       For now I'm going to skip that.
    
        voltdb::Table *evicted_table = TableFactory::getEvictedTable(
                                                        databaseId, 
                                                        executorContext,
                                                        evictedName,
                                                        evictedSchema, 
                                                        evictedColumnNames);
        // We'll shove the EvictedTable to the PersistentTable
        // It will be responsible for deleting it in its deconstructor
        dynamic_cast<PersistentTable*>(m_table)->setEvictedTable(evicted_table);
    } else {
        VOLT_WARN("Not creating EvictedTable for table '%s'", catalogTable.name().c_str());
    }
    #endif
    
    delete[] columnNames;

    m_exportEnabled = isExportEnabledForTable(catalogDatabase, table_id);
    m_table->incrementRefcount();
    VOLT_DEBUG("Finish initializing table '%s'", catalogTable.name().c_str());
    return 0;
}


void TableCatalogDelegate::deleteCommand()
{
    if (m_table) {
        m_table->decrementRefcount();
        m_table = NULL;
    }
}


}
