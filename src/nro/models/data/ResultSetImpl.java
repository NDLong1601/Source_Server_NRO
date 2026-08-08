package nro.models.data;

import java.sql.Timestamp;
import java.sql.ResultSetMetaData;
import java.util.HashMap;
import java.sql.ResultSet;
import java.util.Map;
import nro.models.data.LocalResultSet;

public class ResultSetImpl implements LocalResultSet {

    private Map<String, Object>[] data;
    private Object[][] values;
    private int indexData;

    public ResultSetImpl(final ResultSet rs) throws Exception {
        this.indexData = -1;
        try {
            rs.last();
            final int nRow = rs.getRow();
            rs.beforeFirst();
            final ResultSetMetaData rsmd = rs.getMetaData();
            final int nColumn = rsmd.getColumnCount();
            this.data = new HashMap[nRow];
            for (int i = 0; i < this.data.length; ++i) {
                this.data[i] = new HashMap<>();
            }
            this.values = new Object[nRow][nColumn];
            int index = 0;
            while (rs.next()) {
                for (int j = 1; j <= nColumn; ++j) {
                    final String tableName = rsmd.getTableName(j);
                    final String columnName = rsmd.getColumnName(j);
                    final Object columnValue = rs.getObject(j);
                    this.data[index].put(columnName.toLowerCase(), columnValue);
                    this.data[index].put(tableName.toLowerCase() + "." + columnName.toLowerCase(), columnValue);
                    this.values[index][j - 1] = columnValue;
                }
                ++index;
            }
        } catch (final Exception e) {
            throw e;
        } finally {
            if (rs != null) {
                try {
                    rs.getStatement().close();
                    rs.close();
                } catch (final Exception ex) {
                }
            }
        }
    }

    @Override
    public void dispose() {
        for (Map map : this.data) {
            map.clear();
            map = null;
        }
        this.data = null;
        for (final Object[] array : this.values) {
            Object[] obj = array;
            for (Object o : array) {
                o = null;
            }
            obj = null;
        }
        this.values = null;
    }

    @Override
    public boolean next() throws Exception {
        if (this.data == null) {
            throw new Exception("No data available");
        }
        ++this.indexData;
        return this.indexData < this.data.length;
    }

    @Override
    public boolean first() throws Exception {
        if (this.data == null) {
            throw new Exception("No data available");
        }
        ++this.indexData;
        return this.indexData == this.data.length - 1;
    }

    @Override
    public boolean gotoResult(final int index) throws Exception {
        if (this.data == null) {
            throw new Exception("No data available");
        }
        if (this.indexData < 0 || this.indexData >= this.data.length) {
            throw new Exception("Index out of bound");
        }
        this.indexData = index;
        return true;
    }

    @Override
    public boolean gotoFirst() throws Exception {
        if (this.data == null || this.data.length == 0) {
            throw new Exception("No data available");
        }
        this.indexData = 0;
        return true;
    }

    @Override
    public void gotoBeforeFirst() {
        this.indexData = -1;
    }

    @Override
    public boolean gotoLast() throws Exception {
        if (this.data == null) {
            throw new Exception("No data available");
        }
        this.indexData = this.data.length - 1;
        return true;
    }

    @Override
    public int getRows() throws Exception {
        if (this.data == null) {
            throw new Exception("No data available");
        }
        return this.data.length;
    }

    @Override
    public byte getByte(final int column) throws Exception {
        if (this.values == null) {
            throw new Exception("No data available");
        }
        if (this.indexData == -1) {
            throw new Exception("Results need to be prepared in advance");
        }
        return (byte) (int) this.values[this.indexData][column - 1];
    }

    @Override
    public byte getByte(final String column) throws Exception {
        if (this.data == null) {
            throw new Exception("No data available");
        }
        if (this.indexData == -1) {
            throw new Exception("Results need to be prepared in advance");
        }
        return (byte) (int) this.data[this.indexData].get(column.toLowerCase());
    }

    @Override
    public int getInt(final int column) throws Exception {
        if (this.values == null) {
            throw new Exception("No data available");
        }
        if (this.indexData == -1) {
            throw new Exception("Results need to be prepared in advance");
        }
        return (int) (long) this.values[this.indexData][column - 1];
    }

    @Override
    public int getInt(final String column) throws Exception {
        if (this.data == null) {
            throw new Exception("No data available");
        }
        if (this.indexData == -1) {
            throw new Exception("Results need to be prepared in advance");
        }
        return (int) this.data[this.indexData].get(column.toLowerCase());
    }

    @Override
    public float getFloat(final int column) throws Exception {
        if (this.values == null) {
            throw new Exception("No data available");
        }
        if (this.indexData == -1) {
            throw new Exception("Results need to be prepared in advance");
        }
        return (float) this.values[this.indexData][column - 1];
    }

    @Override
    public float getFloat(final String column) throws Exception {
        if (this.data == null) {
            throw new Exception("No data available");
        }
        if (this.indexData == -1) {
            throw new Exception("Results need to be prepared in advance");
        }
        return (float) this.data[this.indexData].get(column.toLowerCase());
    }

    @Override
    public double getDouble(final int column) throws Exception {
        if (this.values == null) {
            throw new Exception("No data available");
        }
        if (this.indexData == -1) {
            throw new Exception("Results need to be prepared in advance");
        }
        return (double) this.values[this.indexData][column - 1];
    }

    @Override
    public double getDouble(final String column) throws Exception {
        if (this.data == null) {
            throw new Exception("No data available");
        }
        if (this.indexData == -1) {
            throw new Exception("Results need to be prepared in advance");
        }
        return (double) this.data[this.indexData].get(column.toLowerCase());
    }

    @Override
    public long getLong(final int column) throws Exception {
        if (this.values == null) {
            throw new Exception("No data available");
        }
        if (this.indexData == -1) {
            throw new Exception("Results need to be prepared in advance");
        }
        return (long) this.values[this.indexData][column - 1];
    }

    @Override
    public long getLong(final String column) throws Exception {
        if (this.data == null) {
            throw new Exception("No data available");
        }
        if (this.indexData == -1) {
            throw new Exception("Results need to be prepared in advance");
        }
        return (long) this.data[this.indexData].get(column.toLowerCase());
    }

    @Override
    public String getString(final int column) throws Exception {
        if (this.values == null) {
            throw new Exception("No data available");
        }
        if (this.indexData == -1) {
            throw new Exception("Results need to be prepared in advance");
        }
        return String.valueOf(this.values[this.indexData][column - 1]);
    }

    @Override
    public String getString(final String column) throws Exception {
        if (this.data == null) {
            throw new Exception("No data available");
        }
        if (this.indexData == -1) {
            throw new Exception("Results need to be prepared in advance");
        }
        return String.valueOf(this.data[this.indexData].get(column.toLowerCase()));
    }

    @Override
    public Object getObject(final int column) throws Exception {
        if (this.values == null) {
            throw new Exception("No data available");
        }
        if (this.indexData == -1) {
            throw new Exception("Results need to be prepared in advance");
        }
        return this.values[this.indexData][column - 1];
    }

    @Override
    public Object getObject(final String column) throws Exception {
        if (this.data == null) {
            throw new Exception("No data available");
        }
        if (this.indexData == -1) {
            throw new Exception("Results need to be prepared in advance");
        }
        return this.data[this.indexData].get(column.toLowerCase());
    }

    @Override
    public boolean getBoolean(final int column) throws Exception {
        if (this.values == null) {
            throw new Exception("No data available");
        }
        if (this.indexData == -1) {
            throw new Exception("Results need to be prepared in advance");
        }
        try {
            return (int) this.values[this.indexData][column - 1] == 1;
        } catch (final Exception e) {
            return (boolean) this.values[this.indexData][column - 1];
        }
    }

    @Override
    public boolean getBoolean(final String column) throws Exception {
        if (this.data == null) {
            throw new Exception("No data available");
        }
        if (this.indexData == -1) {
            throw new Exception("Results need to be prepared in advance");
        }
        try {
            return (int) this.data[this.indexData].get(column.toLowerCase()) == 1;
        } catch (final Exception e) {
            return (boolean) this.data[this.indexData].get(column.toLowerCase());
        }
    }

    @Override
    public Timestamp getTimestamp(final int column) throws Exception {
        if (this.values == null) {
            throw new Exception("No data available");
        }
        if (this.indexData == -1) {
            throw new Exception("Results need to be prepared in advance");
        }
        return (Timestamp) this.values[this.indexData][column - 1];
    }

    @Override
    public Timestamp getTimestamp(final String column) throws Exception {
        if (this.data == null) {
            throw new Exception("No data available");
        }
        if (this.indexData == -1) {
            throw new Exception("Results need to be prepared in advance");
        }
        return (Timestamp) this.data[this.indexData].get(column.toLowerCase());
    }

    @Override
    public short getShort(final int column) throws Exception {
        if (this.values == null) {
            throw new Exception("No data available");
        }
        if (this.indexData == -1) {
            throw new Exception("Results need to be prepared in advance");
        }
        return (short) (int) this.values[this.indexData][column - 1];
    }

    @Override
    public short getShort(final String column) throws Exception {
        if (this.data == null) {
            throw new Exception("No data available");
        }
        if (this.indexData == -1) {
            throw new Exception("Results need to be prepared in advance");
        }
        return (short) (int) this.data[this.indexData].get(column.toLowerCase());
    }

    @Override
    public void close() {
        dispose();
    }

}
