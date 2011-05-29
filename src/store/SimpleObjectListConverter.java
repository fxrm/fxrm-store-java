/**
 * Unframework Store Library
 *
 * Copyright 2011, Nick Matantsev
 * Dual-licensed under the MIT or GPL Version 2 licenses.
 */

package store;

import java.util.ArrayList;
import java.util.List;

/**
 * Utility class that converts lists to and from string representation.
 * The lists are understood to contain identity objects, and the resulting string
 * is a newline-separated list of database IDs for those objects. Any null object
 * reference is represented as an empty line.
 *
 * NOTE: to use this class, declare a sub-class with a no-arg constructor that
 * calls this constructor with the reference to appropriate component type.
 */
public class SimpleObjectListConverter implements Store.Converter {
    private final Class componentClass;

    public SimpleObjectListConverter(Class componentClass) {
        this.componentClass = componentClass;
    }

    @Override
    public Object intern(Object store, String val) throws Exception {
        ArrayList<Object> out = new ArrayList<Object>();

        int curPos = 0;
        while(true) {
            int nextNL = val.indexOf('\n', curPos);
            if(nextNL < 0)
                break;

            String id = val.substring(curPos, nextNL);
            curPos = nextNL + 1;

            out.add(id.isEmpty() ? null : Store.intern(store, componentClass, id));
        }

        // make sure that there is no left-over junk
        if(curPos != val.length())
            throw new Exception("list must be empty or end in a newline");

        return out;
    }

    @Override
    public String extern(Object store, Object val) throws Exception {
        StringBuffer out = new StringBuffer();
        for(Object comp: (List<Object>)val)
            out.append(comp == null ? "" : Store.extern(store, comp)).append('\n');
        return out.toString();
    }
}
