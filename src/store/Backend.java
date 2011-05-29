/**
 * Unframework Store Library
 *
 * Copyright 2011, Nick Matantsev
 * Dual-licensed under the MIT or GPL Version 2 licenses.
 */

package store;

import java.util.Collection;

/**
 * Generic database backend, used to implement store actions.
 */
public interface Backend {
    Getter createGetter(Column col);
    Setter createSetter(Column[] cols);
    Finder createFinder(Column[] cols);

    Identity createIdentity(Class objectClass) throws Exception;

    Identity intern(Class objectClass, String externalId);
    String extern(Identity id);

    Column createIdentityColumn(Class objectClass, String field, Class referenceClass) throws Exception;
    Column createColumn(Class objectClass, String field, Class fieldType) throws Exception;

    public interface Identity {
    }

    public interface Column {
    }

    public interface Getter {
        Object invoke(Identity id) throws Exception;
    }

    public interface Setter {
        void invoke(Identity id, Object[] args) throws Exception;
    }

    public interface Finder {
        Collection<Identity> invoke(Object[] args) throws Exception;
    }
}
