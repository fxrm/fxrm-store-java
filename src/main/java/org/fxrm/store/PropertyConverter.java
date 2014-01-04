/**
 * Copyright 2011, Nick Matantsev
 * Dual-licensed under the MIT or GPL Version 2 licenses.
 */

package org.fxrm.store;

/**
 * Internal class to convert domain values to and from database representations.
 */
abstract class PropertyConverter {
    abstract Object intern(Object val) throws Exception;
    abstract Object extern(Object val) throws Exception;

    static final PropertyConverter DUMMY = new PropertyConverter() {
        @Override
        public Object intern(Object val) {
            return val;
        }

        @Override
        public Object extern(Object val) {
            return val;
        }
    };

    /**
     * Property value converter dedicated to identity properties.
     */
    static class Identity extends PropertyConverter {
        private final IdentityRegistry ar;

        // special marker value when peeking
        static final Object NONEXISTENT = new Object();

        Identity(IdentityRegistry ar) {
            this.ar = ar;
        }

        @Override
        public Object intern(Object val) {
            return ar.getObject((Backend.Identity)val);
        }

        @Override
        public Object extern(Object val) throws Exception {
            return ar.getId(val);
        }

        public Object peek(Object val) {
            Object result = ar.peekId(val);
            return result == null ? NONEXISTENT : result;
        }
    }

    /**
     * Wrapper around custom-defined converters.
     */
    static class Custom extends PropertyConverter {
        private final Object store;
        private final Store.Converter impl;

        Custom(Object store, Store.Converter impl) {
            this.store = store;
            this.impl = impl;
        }

        @Override
        public Object intern(Object val) throws Exception {
            return impl.intern(store, (String)val);
        }

        @Override
        public Object extern(Object val) throws Exception {
            return impl.extern(store, val);
        }
    }
}
