package nro.models.data;

import java.sql.Timestamp;

public interface LocalResultSet
{
    byte getByte(final int p0) throws Exception;
    
    byte getByte(final String p0) throws Exception;
    
    int getInt(final int p0) throws Exception;
    
    int getInt(final String p0) throws Exception;
    
    short getShort(final int p0) throws Exception;
    
    short getShort(final String p0) throws Exception;
    
    float getFloat(final int p0) throws Exception;
    
    float getFloat(final String p0) throws Exception;
    
    double getDouble(final int p0) throws Exception;
    
    double getDouble(final String p0) throws Exception;
    
    long getLong(final int p0) throws Exception;
    
    long getLong(final String p0) throws Exception;
    
    String getString(final int p0) throws Exception;
    
    String getString(final String p0) throws Exception;
    
    boolean getBoolean(final int p0) throws Exception;
    
    boolean getBoolean(final String p0) throws Exception;
    
    Object getObject(final int p0) throws Exception;
    
    Object getObject(final String p0) throws Exception;
    
    Timestamp getTimestamp(final int p0) throws Exception;
    
    Timestamp getTimestamp(final String p0) throws Exception;
    
    void dispose();
    
    boolean next() throws Exception;
    
    boolean first() throws Exception;
    
    boolean gotoResult(final int p0) throws Exception;
    
    boolean gotoFirst() throws Exception;
    
    void gotoBeforeFirst();
    
    boolean gotoLast() throws Exception;
    
    int getRows() throws Exception;

    public void close();
}
