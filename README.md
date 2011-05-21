
**This is ALPHA code - use at your own risk!**

Unframework Store Library
=========================

Straightforward database persistence layer, intended for use in update-heavy
sections of your code (i.e. the model).

Current source is *less than 1kLOC* of Java code.

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
        db.setEmail(user, "foo@example.com");
    }

    void hairyInitFunction() {
        Data db = Store.create(Data.class, new MySQLBackend(/* ...getting DataSource from JNDI or wherever */ null);
    }

Schema is defined by creating a simple interface (see `Data` above). Then the library
"inflates" that interface by backing it with actual database gets/sets/lookups.

Inserting New Records
---------------------

    User shinyNewUser = new User();
    db.setHomeEmail(shinyNewUser, "i.just.registered@example.com");

    // .... and somewhere in success report
    out.println("New User ID: " + Store.extern(db, shinyNewUser));

Philosophy
----------

* [CQRS](http://abdullin.com/cqrs/): what works well for updates will not be elegant for reading
* [business primitives](http://codebetter.com/drusellers/2010/01/27/business-primitives-1-2/)

This library helps write update-heavy code. It allows rudimentary data read access,
but the latter is not optimized by design. To select data in bulk
amounts (e.g. for user interface display), use another library that is suited for that.

What About Validation?
----------------------

Typical tutorials will encourage putting syntax validation et al into object getters/setters. Instead,
we rely on the business primitive class pattern:

    public class Email {
        private final String value;

        public Email(String v) {
            // validation code belongs here
            if(!v.contains("@")) throw new IllegalArgumentException("Malformed email address");
            value = v;
        }

        @Override
        public toString() { return value; }
    }

    interface Data {
        Email getHomeEmail(User u);
        void setHomeEmail(User u, Email e);
    }

Why Are Entity Classes Empty?
-----------------------------

Each persistent property of an entity object, such as "user's home phone" is treated
like an independent "map", where keys are the identity instances and values are the property data.

Because of that, actual classes representing entities do not need any mutable fields, unlike with traditional ORM.
Also unlike with some traditional ORM, the database ID attribute of entity objects -
typically stored in a string or integer "id" field - is not defined as a member
of the object. Instead, the persistence layer keeps track of it separately.
