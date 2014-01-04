/**
 * Copyright 2011, Nick Matantsev
 * Dual-licensed under the MIT or GPL Version 2 licenses.
 */

package org.unframework.store;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.reflect.Array;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Simple non-intrusive data store interface. Allows type-safe declaration of simple schema.
 */
public class Store {
    @Retention(RetentionPolicy.RUNTIME)
    public static @interface Get {
        String value();
    }

    @Retention(RetentionPolicy.RUNTIME)
    public static @interface Set {
        String[] value();
    }

    @Retention(RetentionPolicy.RUNTIME)
    public static @interface Find {
        String[] by();
    }

    @Retention(RetentionPolicy.RUNTIME)
    public static @interface Info {
        Convert[] converters();
    }

    @Retention(RetentionPolicy.RUNTIME)
    public static @interface Convert {
        Class object();
        String property();
        Class<? extends Converter> convert();
    }

    /**
     * Interface that custom property value converters implement. Arguments
     * are guaranteed to be non-null, and results must be non-null.
     * Store instance reference is passed to e.g. allow resolving identities.
     */
    public static interface Converter {
        Object intern(Object store, String val) throws Exception;
        String extern(Object store, Object val) throws Exception;
    }

    /**
     * Helps map objects and properties into the column-based backend concepts.
     */
    public static interface ObjectMapping {
        Backend.Column getIdentityColumn(Object objectClass, String propertyName, Class referenceClass);
        Backend.Column getSimpleColumn(Object objectClass, String propertyName, Class valueClass);
        Backend.Identity createIdentity(Object objectClass);
        Backend.Identity intern(Object objectClass, Object externalId);
        Object extern(Backend.Identity id);
    }

    public static class ConfigurationException extends RuntimeException {
        public ConfigurationException(String message) {
            super(message);
        }
    }

    public static class BackendException extends RuntimeException {
        public BackendException(Throwable cause) {
            super(cause);
        }
    }

    private static interface StoreMethodImplementation {
        Object invoke(Object[] args) throws Exception;
    }

    private static interface FinderImplementation {
        Iterator<Object> invoke(Object[] args) throws Exception;
    }

    private static class StoreMethodInfo {
        private final Class objectClass;
        private final LinkedHashMap<String, Class> fields = new LinkedHashMap<String, Class>();

        private final int type; // 1/2/3 = getter/setter/finder
        private final int finderType; // 1/2/3 = single/array/collection

        private StoreMethodInfo(Method method) {
            final String name = method.getName();
            final Get getInfo = method.getAnnotation(Get.class);
            final Set setInfo = method.getAnnotation(Set.class);
            final Find findInfo = method.getAnnotation(Find.class);
            final Class returnType = method.getReturnType();
            final Class[] params = method.getParameterTypes();

            if(getInfo != null) {
                if(params.length != 1)
                    throw new ConfigurationException("getter method requires a single parameter: " + method);

                objectClass = params[0];
                fields.put(getInfo.value(), returnType);
                type = 1;
                finderType = 0;

            } else if(setInfo != null) {
                if(params.length < 2)
                    throw new ConfigurationException("setter method requires at least two parameters: " + method);

                if(params.length - 1 != setInfo.value().length)
                    throw new ConfigurationException("setter method annotation must specify " + (params.length - 1) + " fields: " + method);

                objectClass = params[0];
                for(int i = 1; i < params.length; i++)
                    fields.put(setInfo.value()[i - 1], params[i]);
                type = 2;
                finderType = 0;

            } else if(findInfo != null) {
                if(params.length < 1)
                    throw new ConfigurationException("finder method requires at least one parameter: " + method);

                if(params.length != findInfo.by().length)
                    throw new ConfigurationException("finder method annotation must specify " + params.length + " fields: " + method);

                for(int i = 0; i < params.length; i++)
                    fields.put(findInfo.by()[i], params[i]);

                type = 3;

                if(returnType.isArray()) {
                    // convert finder output into an array of appropriate type
                    objectClass = returnType.getComponentType();
                    finderType = 2;
                } else if(returnType == Collection.class) {
                    // determine identity type from collection's generic parameter
                    // TODO: explicitly detect other generic return types and throw appropriate error
                    objectClass = (Class)((ParameterizedType)method.getGenericReturnType()).getActualTypeArguments()[0];
                    finderType = 3;
                } else {
                    // return first item in finder output or null
                    objectClass = returnType;
                    finderType = 1;
                }
            } else if(name.startsWith("get") && name.length() > 3) {
                if(params.length != 1)
                    throw new ConfigurationException("implied getter method requires a single parameter: " + method);

                objectClass = params[0];
                fields.put(getImpliedPropertyName(objectClass, name.substring(3)), returnType);
                type = 1;
                finderType = 0;
            } else if(name.startsWith("set") && name.length() > 3) {
                if(params.length != 2)
                    throw new ConfigurationException("implied setter method requires two parameters: " + method);

                objectClass = params[0];
                fields.put(getImpliedPropertyName(objectClass, name.substring(3)), params[1]);
                type = 2;
                finderType = 0;
            } else {
                throw new ConfigurationException("cannot assign action to data interface method: " + method);
            }
        }

        private String getImpliedPropertyName(Class objectClass, String afterVerb) {
            // if the property name with owning class name remove the class name
            // e.g. "UserEmail" -> "Email"
            String classPrefix = objectClass.getSimpleName();
            if(afterVerb.startsWith(classPrefix))
                afterVerb = afterVerb.substring(classPrefix.length());

            if(afterVerb.length() < 1)
                throw new ConfigurationException("cannot determine implied property name");

            // lowercase the first character
            return Character.toLowerCase(afterVerb.charAt(0)) + afterVerb.substring(1);
        }

        private StoreMethodImplementation createImplementation(final Backend backend, ObjectMapping naming, Map<Class, IdentityRegistry> identities, Map<Class, Map<String, PropertyConverter>> customConvs) {
            final IdentityRegistry ir = identities.get(objectClass);
            final PropertyConverter[] conv = new PropertyConverter[fields.size()];
            final Backend.Column[] cols = new Backend.Column[fields.size()];

            int count = 0;
            for(Map.Entry<String, Class> field: fields.entrySet()) {
                final IdentityRegistry ar = identities.get(field.getValue());
                final PropertyConverter customConv = customConvs.get(objectClass).get(field.getKey());

                conv[count] = ar == null ? (customConv == null ? PropertyConverter.DUMMY : customConv) : new PropertyConverter.Identity(ar);

                try {
                    cols[count] = ar == null ?
                        naming.getSimpleColumn(objectClass, field.getKey(), customConv != null ? String.class : field.getValue()) :
                        naming.getIdentityColumn(objectClass, field.getKey(), field.getValue());
                } catch(Exception e) {
                    throw new BackendException(e);
                }
                count++;
            }

            switch(type) {
                case 1:
                    return new StoreMethodImplementation() {
                        public Object invoke(Object[] args) throws Exception {
                            Backend.Identity id = ir.peekId(args[0]);
                            Object result = id == null ? null : backend.get(id, cols[0]);
                            return result == null ? null : conv[0].intern(result);
                        }
                    };
                case 2:
                    return new StoreMethodImplementation() {
                        public Object invoke(Object[] args) throws Exception {
                            Backend.Identity id = ir.getId(args[0]); // NOTE: instantiating before any values

                            for(int i = 1; i < args.length; i++)
                                backend.set(id, cols[i - 1], args[i] == null ? null : conv[i - 1].extern(args[i]));

                            return null;
                        }
                    };
                case 3:
                    // common implementation returning an iterator of proper object class
                    final FinderImplementation findImpl = new FinderImplementation() {
                        public Iterator<Object> invoke(Object[] args) throws Exception {
                            Object[] findArgs = new Object[args.length];
                            for(int i = 0; i < args.length; i++) {
                                if(args[i] != null) {
                                    // use the "peek" mode if converting an identity object to detect brand new instances
                                    if(conv[i] instanceof PropertyConverter.Identity) {
                                        findArgs[i] = ((PropertyConverter.Identity)conv[i]).peek(args[i]);

                                        // if a brand new object is one of the criteria, result is always empty
                                        if(findArgs[i] == PropertyConverter.Identity.NONEXISTENT)
                                            return Collections.emptySet().iterator();
                                    } else {
                                        findArgs[i] = conv[i].extern(args[i]);
                                    }
                                }
                            }

                            final Iterator<Backend.Identity> found = backend.find(cols, findArgs).iterator();

                            // return a converting iterator
                            return new Iterator<Object>() {
                                public boolean hasNext() {
                                    return found.hasNext();
                                }

                                public Object next() {
                                    return ir.getObject(found.next());
                                }

                                public void remove() {
                                    found.remove();
                                }
                            };
                        }
                    };

                    switch(finderType) {
                        case 1:
                            return new StoreMethodImplementation() {
                                public Object invoke(Object[] args) throws Exception {
                                    Iterator found = findImpl.invoke(args);
                                    return found.hasNext() ? found.next() : null;
                                }
                            };
                        case 2:
                            return new StoreMethodImplementation() {
                                public Object invoke(Object[] args) throws Exception {
                                    Iterator found = findImpl.invoke(args);

                                    ArrayList<Object> result = new ArrayList<Object>();
                                    while(found.hasNext())
                                        result.add(found.next());

                                    return result.toArray((Object[])Array.newInstance(objectClass, 0));
                                }
                            };
                        case 3:
                            return new StoreMethodImplementation() {
                                public Object invoke(Object[] args) throws Exception {
                                    Iterator found = findImpl.invoke(args);

                                    ArrayList<Object> result = new ArrayList<Object>();
                                    while(found.hasNext())
                                        result.add(found.next());

                                    return result;
                                }
                            };
                    }
            }

            // NOTE: this is not expected
            throw new IllegalStateException();
        }
    }

    private static class StoreProxy implements InvocationHandler {
        private final Backend backend;
        private final ObjectMapping naming;
        private final Map<Method, StoreMethodImplementation> actions;
        private final Map<Class, IdentityRegistry> identities;

        public StoreProxy(Class iface, Backend backend, ObjectMapping naming) {
            this.backend = backend;
            this.naming = naming;

            if(!iface.isInterface())
                throw new ConfigurationException("interface class required");

            // parse database action info for each method
            HashMap<Method, StoreMethodInfo> info = new HashMap<Method, StoreMethodInfo>();
            HashMap<Class, IdentityRegistry> reg = new HashMap<Class, IdentityRegistry>();
            HashMap<Class, Map<String, PropertyConverter>> convs = new HashMap<Class, Map<String, PropertyConverter>>();
            for(Method method: iface.getMethods()) {
                StoreMethodInfo mi = new StoreMethodInfo(method);
                info.put(method, mi);

                // track another identity class if necessary
                if(!reg.containsKey(mi.objectClass)) {
                    reg.put(mi.objectClass, new IdentityRegistry(mi.objectClass, naming));
                    convs.put(mi.objectClass, new HashMap<String, PropertyConverter>());
                }
            }

            // register custom value converters
            Info other = (Info)iface.getAnnotation(Info.class); // TODO: process super-classes!
            if(other != null) {
                for(Convert c: other.converters()) {
                    try {
                        Converter convInstance = c.convert().newInstance();
                        convs.get(c.object()).put(c.property(), new PropertyConverter.Custom(this, convInstance));
                    } catch(IllegalAccessException e) {
                        throw new RuntimeException(e); // TODO: better error?
                    } catch(InstantiationException e) {
                        throw new RuntimeException(e); // TODO: better error?
                    }
                }
            }

            // now instantiate actual data method implementations
            HashMap<Method, StoreMethodImplementation> result = new HashMap<Method, StoreMethodImplementation>();
            for(Map.Entry<Method, StoreMethodInfo> kv: info.entrySet())
                result.put(kv.getKey(), kv.getValue().createImplementation(backend, naming, reg, convs));

            actions = Collections.unmodifiableMap(result);
            identities = Collections.unmodifiableMap(reg);
        }

        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            try {
                return actions.get(method).invoke(args);
            } catch(Exception e) {
                throw new BackendException(e);
            }
        }
    }

    /**
     * Create a new implementation of given data interface.
     * @param iface data interface to implement
     * @param backend data backend instance to use
     * @return
     */
    public static <T> T create(Class<T> iface, Backend backend, ObjectMapping naming) {
        return (T)Proxy.newProxyInstance(
                Thread.currentThread().getContextClassLoader(),
                new Class[] { iface },
                new StoreProxy(iface, backend, naming)
                );
    }

    /**
     * Get an external ID string for given identity object.
     * @param store data interface implementation
     * @param obj identity object
     * @return external ID string, or null if the object is not persisted
     */
    public static Object extern(Object store, Object obj) {
        StoreProxy sp = (StoreProxy)Proxy.getInvocationHandler(store);
        Backend.Identity id = sp.identities.get(obj.getClass()).peekId(obj);
        return sp.naming.extern(id);
    }

    /**
     * Get an object instance that corresponds to given external ID. Multiple calls with the same
     * external ID return the same object instance.
     * @param store data interface implementation
     * @param identity object identity class
     * @param externalId external ID string
     * @return object instance corresponding to given external ID
     */
    public static <T> T intern(Object store, Class<T> identity, Object externalId) {
        StoreProxy sp = (StoreProxy)Proxy.getInvocationHandler(store);
        Backend.Identity id = sp.naming.intern(identity, externalId);
        return (T)sp.identities.get(identity).getObject(id);
    }
}
