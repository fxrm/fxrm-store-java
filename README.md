
**This is ALPHA code - use at your own risk!**

Fxrm Store API
=====================

Type-safe database persistence layer API, intended for use in update-heavy
sections of your code (i.e. the behaviour model code). Within that part of the app it is meant to replace:

* ORM libraries
* hand-coded SQL `update` queries / direct NoSQL API calls

Current source is *less than 1kLOC* of Java.

Sample Code
-----------

    public class User {
    }

    public interface Data {
        String getHomeEmail(User u);
        void setHomeEmail(User u, String email);
    }

    Data db;

    void prettyApplicationBehaviour(User user) {
        // update existing user
        db.setEmail(user, "foo@example.com");

        // insert new record
        User shinyNewUser = new User();
        db.setHomeEmail(shinyNewUser, "i.just.registered@example.com");
    }

    void hairyInitFunction() {
        Data db = Store.create(Data.class, new MySQLBackend(/* ...getting DataSource from JNDI or wherever */ null);
    }

Schema is defined by creating a simple interface (see `Data` above). Then the library
"inflates" that interface by backing it with actual database gets/sets/lookups.
The data interface uses only domain entity and value classes and is not "polluted"
by library-specific code (there are some optional annotations).

Database backend implementation is pluggable. Currently, there is a rudimentary
MySQL backend, but the API really shines when used to wrap **NoSQL/key-value data stores**.
But then again, us work-a-day programmers can't always choose where we store data.

Transaction and unit-of-work functionality can be provided by the individual
backend implementation if necessary. There is no "pre-fetch" or data caching
for reads - this API is laser-focused on writing data with no fuss.

Inspiration
-----------

* founding ideas of [CQRS](http://abdullin.com/cqrs/)
* [business primitives](http://codebetter.com/drusellers/2010/01/27/business-primitives-1-2/)
* simple key-value or document-based stores (Couch, Mongo, SimpleDB, Bigtable)

I was quickly sold on the CQRS principle of splitting "view" and "model" code into
completely separate silos - first one being completely read-only, and the other one
being very small, testable and auditable. Then I realized that traditional ORM was
no longer working in either of those silos.

View code needed hand-tweaked bulk data fetches, quickly dumped into the UI. JPQL was
clunky and limited, so I actually just started using straight SQL again.

Model code needed simple idiomatic verbs to update stored information, with an
occasional query for e.g. user role access control, etc. I almost went back to
writing hand-coded SQL `update`s and `insert`s, and flexible databases like
SimpleDB or Mongo started to look really good.

But MySQL was a necessity, and I liked that ORM was type-safe and
relatively database-agnostic. This library's approach gave me those things.

Just to re-iterate: this API is not meant to be used for "view" code. Data reading
functionality is intentionally minimal and un-optimized. There are better-suited
tools for fetching data in bulk.

Business Primitives
-------------------

Typically, syntax validation has been put into behaviour code, or model object getters/setters.
This API is well-suited to rely on the business primitive class pattern:

    public class Email {
        private final String value;

        public Email(String v) {
            // sample validation condition
            if(!v.contains("@"))
                throw new IllegalArgumentException("Malformed email address");

            value = v;
        }

        @Override
        public toString() { return value; }
    }

    interface Data {
        // voila, syntax-validated database actions
        Email getHomeEmail(User u);
        void setHomeEmail(User u, Email e);
    }

Type-safe get/set actions ensure that un-sanitized data does not slip through easily.
Database backend transparently converts the value objects to and from string representation.

Why Are Entity Classes Empty?
-----------------------------

Entity property data is not cached or pre-fetched, just queried on demand when
the appropriate get method is called. Hence, there need to be no mutable fields
on the entity class.

Even database IDs (typically a string or integer field on entity objects) are managed
automatically by the API. Pure model code does not need to be aware of them!
Some ORM frameworks, such as JDO, support a similar approach.

Another way of looking at this: each persistent property of an entity object,
such as "user's home phone", is treated like an independent "map", where keys are
the identity instances and values are the property data. This maps squarely to
the column-based database concept. And again, the identity instance itself
needs to have no mutable state.
