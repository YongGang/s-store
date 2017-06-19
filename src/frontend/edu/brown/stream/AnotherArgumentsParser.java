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
package edu.brown.stream;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import org.voltdb.catalog.Catalog;

import edu.brown.designer.DesignerHints;
import edu.brown.hstore.conf.HStoreConf;
import edu.brown.utils.ArgumentsParser;
import edu.brown.utils.CollectionUtil;

public class AnotherArgumentsParser {

    // --------------------------------------------------------------
    // INPUT PARAMETERS
    // --------------------------------------------------------------
    public static final String ORIGIN_TERMINAL_HOST      = ArgumentsParser.PARAM_TERMINAL_HOST;
    public static final String ORIGIN_TERMINAL_PORT      = ArgumentsParser.PARAM_TERMINAL_PORT;
    public static final String PARAM_SOURCE_FILE        = "source.file";
    public static final String PARAM_SOURCE_SENDRATE       = "source.rate";
    public static final String PARAM_SOURCE_SENDSTOP       = "source.stop";
    public static final String PARAM_BATCH_INTERVAL        = "batch.interval";
    public static final String PARAM_BATCH_ROUNDS        = "batch.rounds";
    public static final String PARAM_RESULT_JSON        = "result.json";
    public static final String PARAM_RESULT_DISPLAY        = "result.display";
    public static final String PARAM_SHUFFLE_METHOD        = "shuffle.method";
    public static final String PARAM_SHUFFLE_SIZE        = "shuffle.size";
    
    public static final List<String> PARAMS = new ArrayList<String>();
    static {
        for (Field field : AnotherArgumentsParser.class.getDeclaredFields()) {
            try {
                if (field.getName().startsWith("PARAM_")) {
                    AnotherArgumentsParser.PARAMS.add(field.get(null).toString());
                }
            } catch (Exception ex) {
                ex.printStackTrace();
                System.exit(1);
            }
        } // FOR
    }; // STATIC
    
    ArgumentsParser m_origianlParser = null;
    
    public Catalog catalog = null;

    private final Map<String, String> params = new LinkedHashMap<String, String>();

    
    public AnotherArgumentsParser()
    {
        
    }
    
    public static AnotherArgumentsParser load(String args[]) throws Exception {
        
        return AnotherArgumentsParser.load(args, true);
        
    }
    
    public static AnotherArgumentsParser load(String args[], boolean loadOriginalParser) throws Exception {
        AnotherArgumentsParser au = new AnotherArgumentsParser();
        
        if(loadOriginalParser==true)
            au.setOriginalParser(au.process(args));
        else
            au.process(args);
        
        return (au);
        
    }
    
    public void setOriginalParser(ArrayList<String> vargs) throws Exception
    {
       
        String[] args = new String[vargs.size()];
        args = vargs.toArray(args);
        
        this.m_origianlParser = ArgumentsParser.load(args,
                ArgumentsParser.PARAM_CATALOG
                );
        
        catalog = this.m_origianlParser.catalog;
    }
    
    private ArrayList<String> process(String args[])
    {
        ArrayList<String> others = new ArrayList<String>();
        
        final Pattern p = Pattern.compile("=");
        for (int i = 0, cnt = args.length; i < cnt; i++) {
            final String arg = args[i];
            final String[] parts = p.split(arg, 2);
            if (parts[0].startsWith("-"))
                parts[0] = parts[0].substring(1);

            // ArgumentsParser Parameter
            if (PARAMS.contains(parts[0].toLowerCase())) {
                this.params.put(parts[0].toLowerCase(), parts[1]);
            }
            else
                others.add(arg);
        } // FOR
        
        return others;
    }
    
    public boolean hasParam(String key) {
        if((m_origianlParser!=null)&&(m_origianlParser.hasParam(key)))
            return true;
        System.out.println("hasParam: key-" + key);
        return (this.params.get(key) != null);
    }
    
    public String getParam(String key) {
        if((m_origianlParser!=null)&&(m_origianlParser.hasParam(key)))
            return m_origianlParser.getParam(key);
        
        return (this.params.get(key));
    }
    
    public Integer getIntParam(String key) {
        String val = this.getParam(key);
        Integer ret = null;
        if (val != null)
            ret = Integer.valueOf(val);
        return (ret);
    }

    public Long getLongParam(String key) {
        String val = this.getParam(key);
        Long ret = null;
        if (val != null)
            ret = Long.valueOf(val);
        return (ret);
    }

    public Double getDoubleParam(String key) {
        String val = this.getParam(key);
        Double ret = null;
        if (val != null)
            ret = Double.valueOf(val);
        return (ret);
    }

    public Boolean getBooleanParam(String key, Boolean defaultValue) {
        String val = this.getParam(key);
        Boolean ret = defaultValue;
        if (val != null)
        {
            ret = Boolean.valueOf(val);
        }
        return (ret);
    }

    public Boolean getBooleanParam(String key) {
        return (this.getBooleanParam(key, null));
    }
}
