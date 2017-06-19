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

/* WARNING: THIS FILE IS AUTO-GENERATED
            DO NOT MODIFY THIS SOURCE
            ALL CHANGES MUST BE MADE IN THE CATALOG GENERATOR */

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


#ifndef CATALOG_TABLE_H_
#define CATALOG_TABLE_H_

#include <string>
#include "catalogtype.h"
#include "catalogmap.h"

namespace catalog {

class Column;
class Index;
class Constraint;
class Trigger;
class ProcedureRef;
class MaterializedViewInfo;
/**
 * A table (relation) in the database
 */
class Table : public CatalogType {
    friend class Catalog;
    friend class CatalogMap<Table>;

protected:
    Table(Catalog * catalog, CatalogType * parent, const std::string &path, const std::string &name);
    CatalogMap<Column> m_columns;
    CatalogMap<Index> m_indexes;
    CatalogMap<Constraint> m_constraints;
    CatalogMap<Trigger> m_triggers;
    CatalogMap<ProcedureRef> m_triggerProcedures;
    bool m_isreplicated;
    CatalogType* m_partitioncolumn;
    int32_t m_estimatedtuplecount;
    CatalogMap<MaterializedViewInfo> m_views;
    CatalogType* m_materializer;
    bool m_systable;
    bool m_mapreduce;
    bool m_evictable;
    bool m_isStream;
    bool m_isWindow;
    bool m_isRows;
    std::string m_streamName;
    int32_t m_size;
    int32_t m_slide;
    int32_t m_groupByIndex;

    virtual void update();

    virtual CatalogType * addChild(const std::string &collectionName, const std::string &name);
    virtual CatalogType * getChild(const std::string &collectionName, const std::string &childName) const;
    virtual bool removeChild(const std::string &collectionName, const std::string &childName);

public:
    ~Table();

    /** GETTER: The set of columns in the table */
    const CatalogMap<Column> & columns() const;
    /** GETTER: The set of indexes on the columns in the table */
    const CatalogMap<Index> & indexes() const;
    /** GETTER: The set of constraints on the table */
    const CatalogMap<Constraint> & constraints() const;
    /** GETTER: The set of triggers for this table */
    const CatalogMap<Trigger> & triggers() const;
    /** GETTER: The set of frontend trigger procedures for this table"  */
    const CatalogMap<ProcedureRef> & triggerProcedures() const;
    /** GETTER: Is the table replicated? */
    bool isreplicated() const;
    /** GETTER: On which column is the table horizontally partitioned */
    const Column * partitioncolumn() const;
    /** GETTER: A rough estimate of the number of tuples in the table; used for planning */
    int32_t estimatedtuplecount() const;
    /** GETTER: Information about materialized views based on this table's content */
    const CatalogMap<MaterializedViewInfo> & views() const;
    /** GETTER: If this is a materialized view, this field stores the source table */
    const Table * materializer() const;
    /** GETTER: Is this table an internal system table? */
    bool systable() const;
    /** GETTER: Is this table a MapReduce transaction table? */
    bool mapreduce() const;
    /** GETTER: Can contents of this table be evicted by the anti-cache? */
    bool evictable() const;
    /** GETTER: Is this table a Stream table? */
    bool isStream() const;
    /** GETTER: Is this table a Window for Stream? */
    bool isWindow() const;
    /** GETTER: Is this is a row based window or time based? */
    bool isRows() const;
    /** GETTER: The window related stream name */
    const std::string & streamName() const;
    /** GETTER: The window size */
    int32_t size() const;
    /** GETTER: The window slide"  */
    int32_t slide() const;
    /** GETTER: The window column index to group on before windowing for row based window only.
     *Negative index value indicates no group.
     */
    int32_t groupByIndex() const;
};

} // namespace catalog

#endif //  CATALOG_TABLE_H_
