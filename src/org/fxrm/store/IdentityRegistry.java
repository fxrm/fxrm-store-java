/**
 * Copyright 2011, Nick Matantsev
 * Dual-licensed under the MIT or GPL Version 2 licenses.
 */

package org.fxrm.store;

import java.lang.ref.WeakReference;
import java.util.HashMap;
import java.util.WeakHashMap;

/**
 * Internal class to track domain objects and their corresponding database IDs.
 */
class IdentityRegistry {
    // NOTE: DB identities must be stored using "strong" references
    private final WeakHashMap<Object, Backend.Identity> objectToId = new WeakHashMap<Object, Backend.Identity>();
    private final HashMap<Backend.Identity, WeakReference<Object>> idToObject = new HashMap<Backend.Identity, WeakReference<Object>>();
    private final Class objectClass;
    private final Store.ObjectMapping gen;

    IdentityRegistry(Class objectClass, Store.ObjectMapping gen) {
        this.objectClass = objectClass;
        this.gen = gen;
    }

    synchronized Backend.Identity peekId(Object obj) {
        return objectToId.get(obj);
    }

    synchronized Backend.Identity getId(Object obj) throws Exception {
        Backend.Identity id = objectToId.get(obj);
        if(id == null) {
            id = gen.createIdentity(objectClass);
            objectToId.put(obj, id);
            idToObject.put(id, new WeakReference<Object>(obj));
        }

        return id;
    }

    synchronized Object getObject(Backend.Identity id) {
        WeakReference<Object> objRef = idToObject.get(id);
        Object obj = objRef == null ? null : objRef.get();
        if(obj == null) {
            try {
                obj = objectClass.newInstance();
            } catch(Exception e) {
                throw new RuntimeException("identity object constructor error", e); // TODO: dedicated exception class?
            }

            objectToId.put(obj, id);
            idToObject.put(id, new WeakReference<Object>(obj));
        }

        return obj;
    }
}
