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

package org.voltdb.planner;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map.Entry;

import org.voltdb.VoltType;
import org.voltdb.catalog.Column;
import org.voltdb.catalog.Database;
import org.voltdb.catalog.Table;
import org.voltdb.expressions.AbstractExpression;
import org.voltdb.expressions.ExpressionUtil;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 *
 *
 */
public class ParsedInsertStmt extends AbstractParsedStmt {

    //public HashMap<Column, AbstractExpression> columns = new HashMap<Column, AbstractExpression>();
    public List<HashMap<Column, AbstractExpression>> rows; 
    
    public ParsedSelectStmt select;
    public boolean hasSelect = false;

    ParsedInsertStmt() {
        //columns = new HashMap<Column, AbstractExpression>();
        rows = new ArrayList<HashMap<Column, AbstractExpression>>();
    }

    @Override
    void parse(Node stmtNode, Database db) {
        assert(tableList.size() <= 1);

        NamedNodeMap attrs = stmtNode.getAttributes();
        Node tableNameAttr = attrs.getNamedItem("table");
        String tableName = tableNameAttr.getNodeValue();
        Table table = db.getTables().getIgnoreCase(tableName);

        // if the table isn't in the list add it
        // if it's there, good
        // if something else is there, we have a problem
        if (tableList.size() == 0)
            tableList.add(table);
        else
            assert(tableList.get(0) == table);

        NodeList children = stmtNode.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node node = children.item(i);
            if (node.getNodeName().equalsIgnoreCase("rows")) {
                NodeList columnsChildren = node.getChildNodes();
                for (int j = 0; j < columnsChildren.getLength(); j++) {
                    Node columnsNode = columnsChildren.item(j);
                    if (columnsNode.getNodeName().equalsIgnoreCase("columns")) {
                        HashMap<Column, AbstractExpression> columns = new HashMap<Column, AbstractExpression>();
                        NodeList colChildren = columnsNode.getChildNodes();
                        for (int jj = 0; jj < colChildren.getLength(); jj++) {
                            Node colNode = colChildren.item(jj);
                            if (colNode.getNodeName().equalsIgnoreCase("column")) {
                                 parseInsertColumn(columns, colNode, db, table);
                            }
                        }
                        rows.add(columns);
                    }
                }
            }
            if (node.getNodeName().equalsIgnoreCase("select")) {
            	parseInsertSelect(node, db);
            }
        }
    }

    void parseInsertColumn(HashMap<Column, AbstractExpression> columns, Node columnNode, Database db, Table table) {
        NamedNodeMap attrs = columnNode.getAttributes();
        Node tableNameAttr = attrs.getNamedItem("table");
        Node columnNameAttr = attrs.getNamedItem("name");
        String tableName = tableNameAttr.getNodeValue();
        String columnName = columnNameAttr.getNodeValue();

        assert(tableName.equalsIgnoreCase(table.getTypeName()));
        Column column = table.getColumns().getIgnoreCase(columnName);

        AbstractExpression expr = null;
        NodeList children = columnNode.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node node = children.item(i);
            if (node.getNodeType() == Node.ELEMENT_NODE) {
                expr = parseExpressionTree(node, db);
                ExpressionUtil.assignLiteralConstantTypesRecursively(expr,
                        VoltType.get((byte)column.getType()));
                ExpressionUtil.assignOutputValueTypesRecursively(expr);
            }
        }

        columns.put(column, expr);
    }
    
    void parseInsertSelect(Node selectNode, Database db) {
    	hasSelect = true;
        select = new ParsedSelectStmt();
        NodeList children = selectNode.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node node = children.item(i);
            if (node.getNodeName().equalsIgnoreCase("parameters")) {
                select.parseParameters(node, db);
            }
            if (node.getNodeName().equalsIgnoreCase("tablescans")) {
                select.parseTables(node, db);
            }
        }
        select.parse(selectNode, db);
        select.analyzeWhereExpression(db);
        select.sql = sql;
    }
    
    ParsedSelectStmt getSelectStmt()
    {
    	return select;
    }

    @Override
    public String toString() {
        String retval = super.toString() + "\n";
        
        retval += "ROWS:\n";
        for(int i = 0; i < rows.size(); i++)
        {
            HashMap<Column, AbstractExpression> columns = rows.get(i);
            retval += "COLUMNS:\n";
            for (Entry<Column, AbstractExpression> col : columns.entrySet()) {
                retval += "\tColumn: " + col.getKey().getTypeName();
                if (col.getValue() != null) //to avoid breaking on INSERT INTO ... SELECT
                	retval +=  ": " + col.getValue().toString() + "\n";
            }
        }
        retval = retval.trim();
        return retval;
    }
    
    public boolean hasSelect()
    {
    	return hasSelect;
    }
}
