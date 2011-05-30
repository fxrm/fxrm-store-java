/**
 * Unframework Store Library
 *
 * Copyright 2011, Nick Matantsev
 * Dual-licensed under the MIT or GPL Version 2 licenses.
 */

package org.unframework.store;

import java.util.Collection;

/**
 * Minimal column-based database interface.
 * NOTE: identity objects are created/retrieved in a database-dependent way (e.g. SQL backends will need a table name and row ID).
 */
public interface Backend {
    public interface Identity {
    }

    public interface Column {
    }

    Object get(Identity id, Column col) throws Exception;
    void set(Identity id, Column col, Object value) throws Exception;

    Collection<Identity> find(Column[] cols, Object[] args) throws Exception;
}
