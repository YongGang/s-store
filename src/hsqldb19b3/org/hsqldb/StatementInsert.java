/* Copyright (c) 2001-2009, The HSQL Development Group
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * Redistributions of source code must retain the above copyright notice, this
 * list of conditions and the following disclaimer.
 *
 * Redistributions in binary form must reproduce the above copyright notice,
 * this list of conditions and the following disclaimer in the documentation
 * and/or other materials provided with the distribution.
 *
 * Neither the name of the HSQL Development Group nor the names of its
 * contributors may be used to endorse or promote products derived from this
 * software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL HSQL DEVELOPMENT GROUP, HSQLDB.ORG,
 * OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
 * PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
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

package org.hsqldb;

import org.hsqldb.HSQLInterface.HSQLParseException;
import org.hsqldb.ParserDQL.CompileContext;
import org.hsqldb.RangeVariable.RangeIteratorBase;
import org.hsqldb.navigator.RowSetNavigator;
import org.hsqldb.navigator.RowSetNavigatorClient;
import org.hsqldb.persist.PersistentStore;
import org.hsqldb.result.Result;
import org.hsqldb.types.Type;

/**
 * Implementation of Statement for INSERT statements.<p>
 *
 * @author Fred Toussi (fredt@users dot sourceforge.net)
 * @version 1.9.0
 * @since 1.9.0
 */
public class StatementInsert extends StatementDML {
	
	boolean hasSelect;

    /**
     * Instantiate this as an INSERT_VALUES statement.
     */
    StatementInsert(Session session, Table targetTable, int[] columnMap,
                    Expression insertExpression, boolean[] checkColumns,
                    CompileContext compileContext) {

        super(StatementTypes.INSERT, StatementTypes.X_SQL_DATA_CHANGE,
              session.currentSchema);

        this.targetTable            = targetTable;
        this.baseTable              = targetTable.getBaseTable();
        this.insertColumnMap        = columnMap;
        this.insertCheckColumns     = checkColumns;
        this.insertExpression       = insertExpression;
        this.isTransactionStatement = true;
        hasSelect = false;

        setDatabseObjects(compileContext);
        checkAccessRights(session);
    }

    /**
     * Instantiate this as an INSERT_SELECT statement.
     */
    StatementInsert(Session session, Table targetTable, int[] columnMap,
                    boolean[] checkColumns, QueryExpression queryExpression,
                    CompileContext compileContext) {

        super(StatementTypes.INSERT, StatementTypes.X_SQL_DATA_CHANGE,
              session.currentSchema);

        this.targetTable            = targetTable;
        this.baseTable              = targetTable.getBaseTable();
        this.insertColumnMap        = columnMap;
        this.insertCheckColumns     = checkColumns;
        this.queryExpression        = queryExpression;
        this.isTransactionStatement = true;
        hasSelect = true;

        setDatabseObjects(compileContext);
        checkAccessRights(session);
    }

    /**
     * Executes an INSERT_SELECT statement.  It is assumed that the argument
     * is of the correct type.
     *
     * @return the result of executing the statement
     */
    Result getResult(Session session) {

        Table           table              = baseTable;
        Result          resultOut          = null;
        RowSetNavigator generatedNavigator = null;
        PersistentStore store = session.sessionData.getRowStore(baseTable);

        if (generatedIndexes != null) {
            resultOut = Result.newUpdateCountResult(generatedResultMetaData,
                    0);
            generatedNavigator = resultOut.getChainedResult().getNavigator();
        }

        RowSetNavigator newDataNavigator = queryExpression == null
                                           ? getInsertValuesNavigator(session)
                                           : getInsertSelectNavigator(session);
        Expression        checkCondition = null;
        RangeIteratorBase checkIterator  = null;

        if (targetTable != baseTable) {
            QuerySpecification select =
                ((TableDerived) targetTable).getQueryExpression()
                    .getMainSelect();

            checkCondition = select.checkQueryCondition;

            if (checkCondition != null) {
                checkIterator = select.rangeVariables[0].getIterator(session);
            }
        }

        while (newDataNavigator.hasNext()) {
            Object[] data = newDataNavigator.getNext();

            if (checkCondition != null) {
                checkIterator.currentData = data;

                boolean check = checkCondition.testCondition(session);

                if (!check) {
                    throw Error.error(ErrorCode.X_44000);
                }
            }

            table.insertRow(session, store, data);

            if (generatedNavigator != null) {
                Object[] generatedValues = getGeneratedColumns(data);

                generatedNavigator.add(generatedValues);
            }
        }

        newDataNavigator.beforeFirst();
        table.fireAfterTriggers(session, Trigger.INSERT_AFTER,
                                newDataNavigator);

        if (resultOut == null) {
            resultOut =
                Result.getUpdateCountResult(newDataNavigator.getSize());
        } else {
            resultOut.setUpdateCount(newDataNavigator.getSize());
        }

        return resultOut;
    }

    RowSetNavigator getInsertSelectNavigator(Session session) {

        Type[] colTypes  = baseTable.getColumnTypes();
        int[]  columnMap = insertColumnMap;

        //
        Result                result = queryExpression.getResult(session, 0);
        RowSetNavigator       nav         = result.initialiseNavigator();
        Type[]                sourceTypes = result.metaData.columnTypes;
        RowSetNavigatorClient newData     = new RowSetNavigatorClient(2);

        while (nav.hasNext()) {
            Object[] data       = baseTable.getNewRowData(session);
            Object[] sourceData = (Object[]) nav.getNext();

            for (int i = 0; i < columnMap.length; i++) {
                int  j          = columnMap[i];
                Type sourceType = sourceTypes[i];

                data[j] = colTypes[j].convertToType(session, sourceData[i],
                                                    sourceType);
            }

            newData.add(data);
        }

        return newData;
    }

    RowSetNavigator getInsertValuesNavigator(Session session) {

        Type[] colTypes  = baseTable.getColumnTypes();
        int[]  columnMap = insertColumnMap;

        //
        Expression[]          list    = insertExpression.nodes;
        RowSetNavigatorClient newData = new RowSetNavigatorClient(list.length);

        for (int j = 0; j < list.length; j++) {
            Expression[] rowArgs = list[j].nodes;
            Object[]     data    = baseTable.getNewRowData(session);

            session.sessionData.startRowProcessing();

            for (int i = 0; i < rowArgs.length; i++) {
                Expression e        = rowArgs[i];
                int        colIndex = columnMap[i];

                if (e.getType() == OpTypes.DEFAULT) {
                    if (baseTable.identityColumn == colIndex) {
                        continue;
                    }

                    data[colIndex] =
                        baseTable.colDefaults[colIndex].getValue(session);

                    continue;
                }

                data[colIndex] = colTypes[colIndex].convertToType(session,
                        e.getValue(session), e.getDataType());
            }

            newData.add(data);
        }

        return newData;
    }


    /*************** VOLTDB *********************/

    private StringBuffer voltAppendInsertColumns(Session session,
                                                 StringBuffer sb,
                                                 String orig_indent)
    throws HSQLParseException
    {
        sb.append(orig_indent).append("<rows>\n");

        String indent = orig_indent + HSQLInterface.XML_INDENT;
        if(insertExpression != null)
        {
            for(int j = 0; j < insertExpression.nodes.length; j++) // each node represent a value row
            {
                sb.append(indent).append("<columns>\n");
                
        
                for (int i = 0; i < insertColumnMap.length; i++)
                {
                    sb.append(indent + HSQLInterface.XML_INDENT).append("<column table=\"").append(targetTable.tableName.name);
                    sb.append("\" name=\"").append(targetTable.getColumn(insertColumnMap[i]).getName().name);
                    sb.append("\">\n");
                    if(insertExpression != null)
                    	sb.append(insertExpression.nodes[j].nodes[i].voltGetXML(session, indent + HSQLInterface.XML_INDENT + HSQLInterface.XML_INDENT)).append("\n");
                    sb.append(indent + HSQLInterface.XML_INDENT).append("</column>\n");
                }
                sb.append(indent).append("</columns>\n");
            }
        }

        sb.append(orig_indent).append("</rows>");

        return sb;
    }

    private StringBuffer voltAppendParameters(Session session, StringBuffer sb, String orig_indent) {
        sb.append(orig_indent).append("<parameters>\n");
        String indent = orig_indent + HSQLInterface.XML_INDENT;
        for (int i = 0; i < parameters.length; i++) {
            sb.append(indent).append("<parameter index='").append(i).append("'");
            Expression param = parameters[i];
            sb.append(" id='").append(param.getUniqueId()).append("'");
            sb.append(" type='").append(Types.getTypeName(param.getDataType().typeCode)).append("'");
            sb.append(" />\n");
        }

        sb.append(orig_indent).append("</parameters>");

        return sb;
    }

    /**
     * VoltDB added method to get a non-catalog-dependent
     * representation of this HSQLDB object.
     * @param session The current Session object may be needed to resolve
     * some names.
     * @param indent A string of whitespace to be prepended to every line
     * in the resulting XML.
     * @return XML, correctly indented, representing this object.
     * @throws HSQLParseException
     */
     String voltGetXML(Session session, String orig_indent)
     throws HSQLParseException
     {
        StringBuffer sb;

        sb = new StringBuffer();
        String indent = orig_indent + HSQLInterface.XML_INDENT;
        
      //sb.replace(0, sb.length(), "<insert table=\"D1\"> <columns><columnref id=\"1667513825\" table=\"D2\" column=\"D2_PKEY\" alias=\"D2_PKEY\" /><columnref id=\"1243263425\" table=\"D2\" column=\"D2_NAME\" alias=\"D2_NAME\" /></columns><select><columns><columnref id=\"1667513825\" table=\"D2\" column=\"D2_PKEY\" alias=\"D2_PKEY\" /><columnref id=\"1243263425\" table=\"D2\" column=\"D2_NAME\" alias=\"D2_NAME\" /></columns><parameters></parameters><tablescans><tablescan type=\"sequential\" table=\"D2\"></tablescan></tablescans></select>");


        switch (type) {

            case StatementTypes.INSERT :
                sb.append(orig_indent).append("<insert table=\"");
                sb.append(targetTable.getName().name).append("\">\n");
                voltAppendInsertColumns(session, sb, indent).append('\n');
                if(queryExpression != null)	//INSERT INTO ... SELECT
                {
                	StatementQuery select = new StatementQuery(session, queryExpression, queryExpression.getCompileContext());
                	sb.append(select.voltGetXML(session, indent));	
                	sb.append('\n');
                }
                else
                	voltAppendParameters(session, sb, indent).append('\n');
                sb.append(orig_indent).append("</insert>");
                break;

            default :
                sb.append(orig_indent).append("<unknown/>");
                break;

        }
        
        return sb.toString();
    }
     
     boolean hasSelect()
     {
    	 return hasSelect;
     }
}
