/**
 * Copyright 2011, Nick Matantsev
 * Dual-licensed under the MIT or GPL Version 2 licenses.
 */

package org.fxrm.store.backend;

import org.fxrm.store.Backend;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import javax.sql.DataSource;

/**
 * Simple MySQL data store backend.
 */
public class MySQLBackend implements Backend {
    private final DataSource ds;

    public MySQLBackend(DataSource ds) {
        this.ds = ds;
    }

    private static String bt(String nativeName) {
        return nativeName.replace("`", "``");
    }

    public class IdentityImpl implements Backend.Identity {
        private final String table;
        private final int rowId;

        private IdentityImpl(String table, int rowId) {
            this.table = table;
            this.rowId = rowId;
        }

        @Override
        public int hashCode() {
            return rowId;
        }

        @Override
        public boolean equals(Object obj) {
            if(obj instanceof IdentityImpl) {
                IdentityImpl id = (IdentityImpl)obj;
                return id.rowId == this.rowId && id.table.equals(this.table);
            }

            return false;
        }
    }

    public abstract class ColumnImpl implements Backend.Column {
        protected final String table, column, idColumn;

        private ColumnImpl(String table, String idColumn, String column) {
            this.table = table;
            this.column = column;
            this.idColumn = idColumn;
        }

        abstract Object readFirstValue(ResultSet rs) throws SQLException, NoSuchMethodException, InstantiationException, IllegalAccessException, InvocationTargetException;
        abstract void setValue(PreparedStatement ps, int i, Object value) throws SQLException;
    }

    @Override
    public Object get(Identity pid, Column pcol) throws SQLException, NoSuchMethodException, InstantiationException, IllegalAccessException, InvocationTargetException {
        final ColumnImpl col = (ColumnImpl)pcol;
        final String sql = "select `" + bt(col.column) + "` from `" + bt(col.table) + "` where `" + bt(col.idColumn) + "` = ?";

        IdentityImpl id = (IdentityImpl)pid;

        Connection conn = ds.getConnection();
        try {
            PreparedStatement ps = conn.prepareStatement(sql);
            ps.setInt(1, id.rowId);
            ps.execute();

            ResultSet rs = ps.getResultSet();
            if(!rs.next())
                throw new RuntimeException("object ID not found"); // TODO: dedicated error

            return col.readFirstValue(rs);
        } finally {
            conn.close();
        }
    }

    public void set(Identity pid, Column pcol, Object value) throws SQLException {
        final ColumnImpl col = (ColumnImpl)pcol;
        final String sql = "update `" + bt(col.table) + "` set `" + bt(col.column) + "` = ? where `" + bt(col.idColumn) + "` = ?";

        IdentityImpl id = (IdentityImpl)pid;

        Connection conn = ds.getConnection();
        try {
            CallableStatement cs = conn.prepareCall(sql);
            col.setValue(cs, 1, value);
            cs.setInt(2, id.rowId);
            cs.execute();

            // TODO: verify num updated rows?
        } finally {
            conn.close();
        }
    }

    public Collection<Identity> find(final Column[] cols, Object[] args) throws SQLException {
        String table = ((ColumnImpl)cols[0]).table;
        String idCol = ((ColumnImpl)cols[0]).idColumn;

        // TODO: make sure table name is consistent, but return empty result instead of throwing exception otherwise! (technically legal arguments)
        final String sql;
        {
            StringBuffer sb = new StringBuffer();
            sb.append("select `" + bt(idCol) + "` from `").append(bt(table)).append("` where ");

            boolean first = true;
            for(int i = 0; i < cols.length; i++) {
                sb.append(first ? "" : " and ");
                sb.append("`").append(bt(((ColumnImpl)cols[i]).column)).append(args[i] == null ? "` is null" : "` = ?");
                first = false;
            }

            sql = sb.toString();
        }

        Connection conn = ds.getConnection();
        try {
            PreparedStatement ps = conn.prepareStatement(sql);
            int argIndex = 1;
            for(int i = 0; i < cols.length; i++) {
                // NULL arguments do not need to be set
                if(args[i] == null)
                    continue;

                ((ColumnImpl)cols[i]).setValue(ps, argIndex, args[i]);
                argIndex++;
            }
            ps.execute();

            ResultSet rs = ps.getResultSet();

            ArrayList<Identity> result = new ArrayList<Identity>();
            while(rs.next())
                result.add(new IdentityImpl(table, rs.getInt(1)));

            return result;
        } finally {
            conn.close();
        }
    }

    public Identity createIdentity(String table, String idCol) throws SQLException {
        Connection conn = ds.getConnection();
        try {
            CallableStatement cs = conn.prepareCall("insert into `" + bt(table) + "` (`" + bt(idCol) + "`) values (NULL)");
            cs.execute();

            ResultSet rs = cs.getGeneratedKeys();
            if(!rs.next())
                throw new RuntimeException("no created ID returned"); // TODO: custom error

            return new IdentityImpl(table, rs.getInt(1));
        } finally {
            conn.close();
        }
    }

    public Identity intern(String table, String externalId) {
        int id = Integer.parseInt(externalId.toString()); // NOTE: triggering NPE explicitly
        return new IdentityImpl(table.toString(), id);
    }

    public String extern(Identity id) {
        return Integer.toString(((IdentityImpl)id).rowId);
    }

    public Column createIdentityColumn(String table, String idCol, String field, final Class referenceClass) {
        // identities are always treated as ints
        return new ColumnImpl(table, idCol, field) {
            @Override
            Object readFirstValue(ResultSet rs) throws SQLException {
                return new IdentityImpl(table, rs.getInt(1));
            }

            @Override
            void setValue(PreparedStatement ps, int i, Object value) throws SQLException {
                ps.setInt(i, ((IdentityImpl)value).rowId);
            }
        };
    }

    public Column createSimpleColumn(String table, String idCol, String field, final Class fieldType) throws NoSuchMethodException {
        if(fieldType == String.class) {

            // strings correspond to VARCHAR
            return new ColumnImpl(table, idCol, field) {
                @Override
                Object readFirstValue(ResultSet rs) throws SQLException {
                    return rs.getString(1);
                }

                @Override
                void setValue(PreparedStatement ps, int i, Object value) throws SQLException {
                    if(value == null)
                        ps.setNull(i, Types.VARCHAR);
                    else
                        ps.setString(i, (String)value);
                }
            };

        } else if(fieldType == Integer.class) {

            // integers correspond to INT
            return new ColumnImpl(table, idCol, field) {
                @Override
                Object readFirstValue(ResultSet rs) throws SQLException {
                    int r = rs.getInt(1);
                    return rs.wasNull() ? null : Integer.valueOf(r);
                }

                @Override
                void setValue(PreparedStatement ps, int i, Object value) throws SQLException {
                    if(value == null)
                        ps.setNull(i, Types.INTEGER);
                    else
                        ps.setInt(i, (Integer)value);
                }
            };

        } else if(fieldType == Date.class) {

            // dates are stored as BIGINT milliseconds since epoch
            return new ColumnImpl(table, idCol, field) {
                @Override
                Object readFirstValue(ResultSet rs) throws SQLException {
                    long r = rs.getLong(1);
                    return rs.wasNull() ? null : new Date(r);
                }

                @Override
                void setValue(PreparedStatement ps, int i, Object value) throws SQLException {
                    if(value == null)
                        ps.setNull(i, Types.BIGINT);
                    else
                        ps.setLong(i, ((Date)value).getTime());
                }
            };

        } else if(fieldType.isEnum()) {

            // each enum is stored as VARCHAR of the value's simple name
            return new ColumnImpl(table, idCol, field) {
                @Override
                Object readFirstValue(ResultSet rs) throws SQLException {
                    String name = rs.getString(1);
                    return name == null ? null : Enum.valueOf(fieldType, name);
                }

                @Override
                void setValue(PreparedStatement ps, int i, Object value) throws SQLException {
                    if(value == null)
                        ps.setNull(i, Types.VARCHAR);
                    else
                        ps.setString(i, ((Enum)value).name());
                }
            };

        } else {

            // generic values are stored as VARCHAR via their toString() method; when reading, constructor with a single String argument is called
            final Constructor ctor = fieldType.getConstructor(String.class);
            return new ColumnImpl(table, idCol, field) {
                @Override
                Object readFirstValue(ResultSet rs) throws SQLException, InstantiationException, IllegalAccessException, InvocationTargetException {
                    String val = rs.getString(1);
                    return ctor.newInstance(val);
                }

                @Override
                void setValue(PreparedStatement ps, int i, Object value) throws SQLException {
                    if(value == null)
                        ps.setNull(i, Types.VARCHAR);
                    else
                        ps.setString(i, value.toString());
                }
            };

        }
    }
}
