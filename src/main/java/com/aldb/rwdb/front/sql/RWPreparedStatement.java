package com.aldb.rwdb.front.sql;

import java.io.InputStream;
import java.io.Reader;
import java.math.BigDecimal;
import java.net.URL;
import java.sql.Array;
import java.sql.Blob;
import java.sql.Clob;
import java.sql.Date;
import java.sql.NClob;
import java.sql.ParameterMetaData;
import java.sql.PreparedStatement;
import java.sql.Ref;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.RowId;
import java.sql.SQLException;
import java.sql.SQLXML;
import java.sql.Statement;
import java.sql.Time;
import java.sql.Timestamp;
import java.util.Calendar;

import com.aldb.rwdb.front.sql.error.SQLError;

/**
 * 代理PreparedStatement,负责对预处理方式进行sql分析,获取到真实的connection
 * 
 */
public class RWPreparedStatement extends RWStatement implements PreparedStatement {

    private StringBuffer sb; // 这个sql语句，会逐步被更新，然后用于sql拦截解析，
    private RWParameters parameters; // 这个是对于preparement进行修改的参数记录
    private String originalSql; // 而这个sql语句，会保存原始的模样，用于传参给真正的preparement

    // 以下常量对应connection 中构建preparedStatement的那6个方法，目的是1-1对应
    static final int CREATE_PS_METHOD_BY_CON_S = 21;

    static final int CREATE_PS_METHOD_BY_CON_S_I = 22;

    static final int CREATE_PS_METHOD_BY_CON_S_$I = 23;

    static final int CREATE_PS_METHOD_BY_CON_S_$S = 24;

    static final int CREATE_PS_METHOD_BY_CON_S_I_I = 25;

    static final int CREATE_PS_METHOD_BY_CON_S_I_I_I = 26;

    public RWPreparedStatement(String sql, RWConnection conn) {
        super(conn);
        this.sb = new StringBuffer(sql);
        this.originalSql = sql;
        this.parameters = new RWParameters();
        super.createMethodByCon = RWPreparedStatement.CREATE_PS_METHOD_BY_CON_S;
    }

    public RWPreparedStatement(String sql, RWConnection conn, int autoGeneratedKeys) {
        this(sql, conn);
        super.autoGeneratedKeys = autoGeneratedKeys;
        super.createMethodByCon = RWPreparedStatement.CREATE_PS_METHOD_BY_CON_S_I;
    }

    public RWPreparedStatement(String sql, RWConnection conn, int[] columnIndexes) {
        this(sql, conn);
        super.columnIndexes = columnIndexes;
        super.createMethodByCon = RWPreparedStatement.CREATE_PS_METHOD_BY_CON_S_$I;
    }

    public RWPreparedStatement(String sql, RWConnection conn, String[] columnNames) {
        this(sql, conn);
        super.columnNames = columnNames;
        super.createMethodByCon = RWPreparedStatement.CREATE_PS_METHOD_BY_CON_S_$S;
    }

    public RWPreparedStatement(String sql, RWConnection conn, int resultSetType, int resultSetConcurrency) {
        this(sql, conn);
        super.resultSetType = resultSetType;
        super.resultSetConcurrency = resultSetConcurrency;
        super.createMethodByCon = RWPreparedStatement.CREATE_PS_METHOD_BY_CON_S_I_I;
    }

    public RWPreparedStatement(String sql, RWConnection conn, int resultSetType, int resultSetConcurrency,
            int resultSetHoldability) {
        this(sql, conn);
        super.resultSetType = resultSetType;
        super.resultSetConcurrency = resultSetConcurrency;
        super.resultSetHoldability = resultSetHoldability;
        super.createMethodByCon = RWPreparedStatement.CREATE_PS_METHOD_BY_CON_S_I_I_I;
    }

    private int searchIndex(int parameterIndex) {
        int index = 0;
        for (int i = 0; i <= parameterIndex; i++) {
            index = this.sb.indexOf("?", index);
        }
        return index;
    }

    private void replaceStrBuffer(int parameterIndex, Object strObj) {
        int strIndex = searchIndex(parameterIndex);
        this.sb = this.sb.replace(strIndex, strIndex + 1, warpParameter(strObj.toString()));
    }

    private String warpParameter(String param) {
        return String.format("'%s'", param);
    }

    @Override
    protected void createStatements(String sql) throws SQLException {
        switch (this.createMethodByCon) {
        case CREATE_PS_METHOD_BY_CON_S:
            realStat = fakeConn.getRealConn().prepareStatement(originalSql);
            break;
        case CREATE_PS_METHOD_BY_CON_S_I:
            realStat = fakeConn.getRealConn().prepareStatement(originalSql, autoGeneratedKeys);
            break;
        case CREATE_PS_METHOD_BY_CON_S_$I:
            realStat = fakeConn.getRealConn().prepareStatement(originalSql, columnIndexes);
            break;
        case CREATE_PS_METHOD_BY_CON_S_$S:
            realStat = fakeConn.getRealConn().prepareStatement(originalSql, columnNames);
            break;
        case CREATE_PS_METHOD_BY_CON_S_I_I:
            realStat = fakeConn.getRealConn().prepareStatement(originalSql, resultSetType, resultSetConcurrency);
            break;
        case CREATE_PS_METHOD_BY_CON_S_I_I_I:
            realStat = fakeConn.getRealConn().prepareStatement(originalSql, resultSetType, resultSetConcurrency,
                    resultSetHoldability);
            break;
        }
        if (realStat == null) {
            throw new SQLException("No real PreparedStatement exist");
        }
        this.parameters.fillPreparedStatement((PreparedStatement) realStat);
    }

    @Override
    protected void reset() {
        this.realStat = null;
        this.originalSql = null;
        this.fakeConn = null;
        this.autoGeneratedKeys = Statement.NO_GENERATED_KEYS;
        this.columnIndexes = null;
        this.columnNames = null;
        this.resultSetType = 0;
        this.resultSetConcurrency = 0;
        this.resultSetHoldability = 0;
        this.maxFieldSize = 0;
        this.maxRows = 0;
        this.escapeProcessing = true;
        this.queryTimeout = 0;
        this.cursorName = null;
        this.fetchDirection = 0;
        this.fetchSize = 0;
        this.poolable = true;
        this.parameters.clear();
    }

    @Override
    public ResultSet executeQuery() throws SQLException {
        prepare(this.sb.toString());
        return ((PreparedStatement) realStat).executeQuery();
    }

    @Override
    public int executeUpdate() throws SQLException {
        prepare(this.sb.toString());
        return ((PreparedStatement) realStat).executeUpdate();
    }

    @Override
    public boolean execute() throws SQLException {
        prepare(this.sb.toString());
        return ((PreparedStatement) realStat).execute();
    }

    @Override
    public void clearParameters() throws SQLException {
        if (realStat == null) {
            throw SQLError.createSQLException("no real Statement exist");
        }
        this.parameters.clear();
        ((PreparedStatement) realStat).clearParameters();
    }

    @Override
    public void addBatch() throws SQLException {
        throw SQLError.notImplemented();
    }

    @Override
    public ParameterMetaData getParameterMetaData() throws SQLException {
        if (realStat == null) {
            throw SQLError.createSQLException("no real Statement exist");
        }
        return ((PreparedStatement) realStat).getParameterMetaData();
    }

    @Override
    public ResultSetMetaData getMetaData() throws SQLException {
        if (realStat == null) {
            throw SQLError.createSQLException("no real Statement exist");
        }
        return ((PreparedStatement) realStat).getMetaData();
    }

    /*在PreparedStatement设置参数这个处理上所有的方法思路都是一样的，首先将参数接收替换掉预处理的sql中，
     * 然后用parameters将 设置方法名，参数类型，及参数值进行存储,在获取路由结果之后，通过回调将参数设置给真正的preparedstatement,
     * 原因是构造器入口时就作路由是不准确的，如果只是读写区分在构造器是获取sql就可以路由，这种错误概率不是很高，但是
     * 如果需要精准的路由所以需要获取完整的sql语句，所以才有了下面设置参数方法包装
     * 
     */
    @Override
    public void setNull(int parameterIndex, int sqlType) throws SQLException {
        int strIndex = searchIndex(parameterIndex);
        this.sb = this.sb.replace(strIndex, strIndex + 1, "NULL");
        parameters.set("setNull", new Class<?>[] { int.class, int.class }, new Object[] { parameterIndex, sqlType });
    }

    @Override
    public void setBoolean(int parameterIndex, boolean x) throws SQLException {
        replaceStrBuffer(parameterIndex, x);
        parameters.set("setBoolean", new Class<?>[] { int.class, boolean.class }, new Object[] { parameterIndex, x });
    }

    @Override
    public void setByte(int parameterIndex, byte x) throws SQLException {
        replaceStrBuffer(parameterIndex, x);
        parameters.set("setByte", new Class<?>[] { int.class, byte.class }, new Object[] { parameterIndex, x });
    }

    @Override
    public void setShort(int parameterIndex, short x) throws SQLException {
        replaceStrBuffer(parameterIndex, x);
        parameters.set("setShort", new Class<?>[] { int.class, short.class }, new Object[] { parameterIndex, x });
    }

    @Override
    public void setInt(int parameterIndex, int x) throws SQLException {
        replaceStrBuffer(parameterIndex, x);
        parameters.set("setInt", new Class<?>[] { int.class, int.class }, new Object[] { parameterIndex, x });
    }

    @Override
    public void setLong(int parameterIndex, long x) throws SQLException {
        replaceStrBuffer(parameterIndex, x);
        parameters.set("setLong", new Class<?>[] { int.class, long.class }, new Object[] { parameterIndex, x });
    }

    @Override
    public void setFloat(int parameterIndex, float x) throws SQLException {
        replaceStrBuffer(parameterIndex, x);
        parameters.set("setFloat", new Class<?>[] { int.class, float.class }, new Object[] { parameterIndex, x });
    }

    @Override
    public void setDouble(int parameterIndex, double x) throws SQLException {
        replaceStrBuffer(parameterIndex, x);
        parameters.set("setDouble", new Class<?>[] { int.class, double.class }, new Object[] { parameterIndex, x });
    }

    @Override
    public void setBigDecimal(int parameterIndex, BigDecimal x) throws SQLException {
        replaceStrBuffer(parameterIndex, x);
        parameters.set("setBigDecimal", new Class<?>[] { int.class, BigDecimal.class }, new Object[] { parameterIndex,
                x });
    }

    @Override
    public void setString(int parameterIndex, String x) throws SQLException {
        replaceStrBuffer(parameterIndex, x);
        parameters.set("setString", new Class<?>[] { int.class, String.class }, new Object[] { parameterIndex, x });

    }

    @Override
    public void setBytes(int parameterIndex, byte[] x) throws SQLException {
        replaceStrBuffer(parameterIndex, x);
        parameters.set("setBytes", new Class<?>[] { int.class, byte[].class }, new Object[] { parameterIndex, x });
    }

    @Override
    public void setDate(int parameterIndex, Date x) throws SQLException {
        replaceStrBuffer(parameterIndex, x);
        parameters.set("setDate", new Class<?>[] { int.class, Date.class }, new Object[] { parameterIndex, x });
    }

    @Override
    public void setTime(int parameterIndex, Time x) throws SQLException {
        replaceStrBuffer(parameterIndex, x);
        parameters.set("setTime", new Class<?>[] { int.class, Time.class }, new Object[] { parameterIndex, x });
    }

    @Override
    public void setTimestamp(int parameterIndex, Timestamp x) throws SQLException {
        replaceStrBuffer(parameterIndex, x);
        parameters.set("setTimestamp", new Class<?>[] { int.class, Timestamp.class },
                new Object[] { parameterIndex, x });

    }

    public void setDate(int parameterIndex, Date x, Calendar cal) throws SQLException {
        replaceStrBuffer(parameterIndex, x);
        parameters.set("setDate", new Class<?>[] { int.class, Date.class, Calendar.class }, new Object[] {
                parameterIndex, x, cal });
    }

    public void setTime(int parameterIndex, Time x, Calendar cal) throws SQLException {
        replaceStrBuffer(parameterIndex, x);
        parameters.set("setTime", new Class<?>[] { int.class, Time.class, Calendar.class }, new Object[] {
                parameterIndex, x, cal });
    }

    public void setTimestamp(int parameterIndex, Timestamp x, Calendar cal) throws SQLException {
        replaceStrBuffer(parameterIndex, x);
        parameters.set("setTime", new Class<?>[] { int.class, Timestamp.class, Calendar.class }, new Object[] {
                parameterIndex, x, cal });
    }

    @Override
    public void setNull(int parameterIndex, int sqlType, String typeName) throws SQLException {
        int strIndex = searchIndex(parameterIndex);
        this.sb = this.sb.replace(strIndex, strIndex + 1, "NULL");
        parameters.set("setNull", new Class<?>[] { int.class, int.class, String.class }, new Object[] { parameterIndex,
                sqlType, typeName });
    }

    @Override
    public void setURL(int parameterIndex, URL x) throws SQLException {
        replaceStrBuffer(parameterIndex, x);
        parameters.set("setNull", new Class<?>[] { int.class, URL.class }, new Object[] { parameterIndex, x });
    }

    @Override
    public void setRef(int parameterIndex, Ref x) throws SQLException {
        replaceStrBuffer(parameterIndex, x);
        parameters.set("setRef", new Class<?>[] { int.class, Ref.class }, new Object[] { parameterIndex, x });
    }

    @Override
    public void setBlob(int parameterIndex, Blob x) throws SQLException {
        replaceStrBuffer(parameterIndex, x);
        parameters.set("setBlob", new Class<?>[] { int.class, Blob.class }, new Object[] { parameterIndex, x });
    }

    @Override
    public void setClob(int parameterIndex, Clob x) throws SQLException {
        replaceStrBuffer(parameterIndex, x);
        parameters.set("setClob", new Class<?>[] { int.class, Clob.class }, new Object[] { parameterIndex, x });
    }

    @Override
    public void setArray(int parameterIndex, Array x) throws SQLException {
        replaceStrBuffer(parameterIndex, x);
        parameters.set("setArray", new Class<?>[] { int.class, Array.class }, new Object[] { parameterIndex, x });
    }

    @Override
    public void setObject(int parameterIndex, Object x, int targetSqlType) throws SQLException {
        replaceStrBuffer(parameterIndex, x);
        parameters.set("setObject", new Class<?>[] { int.class, Object.class, int.class }, new Object[] {
                parameterIndex, x, targetSqlType });
    }

    @Override
    public void setObject(int parameterIndex, Object x) throws SQLException {
        replaceStrBuffer(parameterIndex, x);
        parameters.set("setObject", new Class<?>[] { int.class, Object.class }, new Object[] { parameterIndex, x });

    }

    @Override
    public void setRowId(int parameterIndex, RowId x) throws SQLException {
        replaceStrBuffer(parameterIndex, x);
        parameters.set("setRowId", new Class<?>[] { int.class, RowId.class }, new Object[] { parameterIndex, x });
    }

    @Override
    public void setNString(int parameterIndex, String value) throws SQLException {
        replaceStrBuffer(parameterIndex, value);
        parameters
                .set("setNString", new Class<?>[] { int.class, String.class }, new Object[] { parameterIndex, value });
    }

    @Override
    public void setNClob(int parameterIndex, NClob value) throws SQLException {
        replaceStrBuffer(parameterIndex, value);
        parameters.set("setNClob", new Class<?>[] { int.class, NClob.class }, new Object[] { parameterIndex, value });
    }

    @Override
    public void setClob(int parameterIndex, Reader reader, long length) throws SQLException {
        parameters.set("setClob", new Class<?>[] { int.class, Reader.class, long.class }, new Object[] {
                parameterIndex, reader, length });
    }

    @Override
    public void setBlob(int parameterIndex, InputStream inputStream, long length) throws SQLException {
        parameters.set("setBlob", new Class<?>[] { int.class, InputStream.class, long.class }, new Object[] {
                parameterIndex, inputStream, length });
    }

    @Override
    public void setNClob(int parameterIndex, Reader reader, long length) throws SQLException {
        parameters.set("setNClob", new Class<?>[] { int.class, Reader.class, long.class }, new Object[] {
                parameterIndex, reader, length });
    }

    @Override
    public void setSQLXML(int parameterIndex, SQLXML xmlObject) throws SQLException {
        replaceStrBuffer(parameterIndex, xmlObject);
        parameters.set("setSQLXML", new Class<?>[] { int.class, SQLXML.class }, new Object[] { parameterIndex,
                xmlObject });
    }

    @Override
    public void setObject(int parameterIndex, Object x, int targetSqlType, int scaleOrLength) throws SQLException {
        parameters.set("setObject", new Class<?>[] { int.class, Object.class, int.class }, new Object[] {
                parameterIndex, x, targetSqlType });
    }

    @Override
    public void setNCharacterStream(int parameterIndex, Reader value) throws SQLException {
        parameters.set("setNCharacterStream", new Class<?>[] { int.class, Reader.class }, new Object[] {
                parameterIndex, value });
    }

    @Override
    public void setClob(int parameterIndex, Reader reader) throws SQLException {
        parameters.set("setClob", new Class<?>[] { int.class, Reader.class }, new Object[] { parameterIndex, reader });
    }

    @Override
    public void setBlob(int parameterIndex, InputStream inputStream) throws SQLException {
        parameters.set("setBlob", new Class<?>[] { int.class, InputStream.class }, new Object[] { parameterIndex,
                inputStream });
    }

    @Override
    public void setNClob(int parameterIndex, Reader reader) throws SQLException {
        parameters.set("setNClob", new Class<?>[] { int.class, Reader.class }, new Object[] { parameterIndex, reader });
    }

    @Override
    public void setNCharacterStream(int parameterIndex, Reader value, long length) throws SQLException {
        parameters.set("setNCharacterStream", new Class<?>[] { int.class, Reader.class }, new Object[] {
                parameterIndex, value });
    }

    @Override
    public void setAsciiStream(int parameterIndex, InputStream x, long length) throws SQLException {
        parameters.set("setAsciiStream", new Class<?>[] { int.class, InputStream.class, long.class }, new Object[] {
                parameterIndex, x, length });
    }

    @Override
    public void setBinaryStream(int parameterIndex, InputStream x, long length) throws SQLException {
        parameters.set("setBinaryStream", new Class<?>[] { int.class, InputStream.class, long.class }, new Object[] {
                parameterIndex, x, length });
    }

    public void setCharacterStream(int parameterIndex, Reader reader, long length) throws SQLException {
        parameters.set("setCharacterStream", new Class<?>[] { int.class, Reader.class }, new Object[] { parameterIndex,
                reader });
    }

    public void setAsciiStream(int parameterIndex, InputStream x) throws SQLException {
        parameters.set("setAsciiStream", new Class<?>[] { int.class, InputStream.class }, new Object[] {
                parameterIndex, x });
    }

    public void setBinaryStream(int parameterIndex, InputStream x) throws SQLException {
        parameters.set("setBinaryStream", new Class<?>[] { int.class, InputStream.class }, new Object[] {
                parameterIndex, x });
    }

    public void setCharacterStream(int parameterIndex, Reader reader) throws SQLException {
        parameters.set("setCharacterStream", new Class<?>[] { int.class, Reader.class }, new Object[] { parameterIndex,
                reader });
    }

    @Override
    public void setAsciiStream(int parameterIndex, InputStream x, int length) throws SQLException {
        parameters.set("setAsciiStream", new Class<?>[] { int.class, InputStream.class, int.class }, new Object[] {
                parameterIndex, x, length });
    }

    @Override
    public void setUnicodeStream(int parameterIndex, InputStream x, int length) throws SQLException {
        parameters.set("setUnicodeStream", new Class<?>[] { int.class, InputStream.class, int.class }, new Object[] {
                parameterIndex, x, length });
    }

    @Override
    public void setBinaryStream(int parameterIndex, InputStream x, int length) throws SQLException {
        parameters.set("setBinaryStream", new Class<?>[] { int.class, InputStream.class, int.class }, new Object[] {
                parameterIndex, x, length });
    }

    @Override
    public void setCharacterStream(int parameterIndex, Reader reader, int length) throws SQLException {
        parameters.set("setCharacterStream", new Class<?>[] { int.class, Reader.class, int.class }, new Object[] {
                parameterIndex, reader, length });
    }

}