package ru.fizteh.fivt.students.Kudriavtsev_Dmitry.Storable;

import ru.fizteh.fivt.storage.structured.ColumnFormatException;
import ru.fizteh.fivt.storage.structured.Storeable;
import ru.fizteh.fivt.storage.structured.Table;
import ru.fizteh.fivt.storage.structured.TableProvider;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.util.*;
import java.text.ParseException;


/**
 * Created by Дмитрий on 25.11.2014.
 */
public class StoreableTableProvider implements TableProvider {

    private final String signatureFileName = "signature.tsv";

    private Map<Class<?>, String> classNames;
    public Map<String, Class<?>> revClassNames;
    private String dbDirectory;
    public Map<String, StoreableTable> tables;

    public StoreableTableProvider(String dbDir) throws IOException {
        dbDirectory = dbDir;
        tables = new HashMap<>();
        initClassNames();
        initProvider();
    }

    public StoreableTableProvider() {
        initClassNames();
    }

    private void initClassNames() {
        classNames = new HashMap<>();
        classNames.put(Integer.class, "int");
        classNames.put(Long.class, "long");
        classNames.put(Byte.class, "byte");
        classNames.put(Double.class, "double");
        classNames.put(Float.class, "float");
        classNames.put(Boolean.class, "boolean");
        classNames.put(String.class, "String");

        revClassNames = new HashMap<>();
        for (Class<?> tempClass : classNames.keySet()) {
            String name = classNames.get(tempClass);
            revClassNames.put(name, tempClass);
        }
    }

    private void initProvider() throws IOException {
        File dbDir = new File(dbDirectory);
        for (File curDir : dbDir.listFiles()) {
            if (curDir.isDirectory()) {
                if (curDir.getName().endsWith(".dir")) {
                    curDir = curDir.getParentFile();
                }
                File signatureFile = new File(
                        curDir.getCanonicalPath() + File.separator + signatureFileName);
                List<Class<?>> signature = readSignature(signatureFile);
                StoreableTable table = new StoreableTable(
                        this, curDir.getName(), dbDirectory, signature);
                tables.put(curDir.getName(), table);
            }
        }
    }

    private List<Class<?>> readSignature(File signatureFile) throws IOException {
        try (Scanner scanner = new Scanner(signatureFile)) {
            List<Class<?>> result = new ArrayList<>();
            while (scanner.hasNext()) {
                String str = scanner.next();
                result.add(revClassNames.get(str));
            }
            return result;
        }
    }

    private void writeSignature(File signatureFile, List<Class<?>> signature) throws IOException {
        try (PrintWriter writer = new PrintWriter(signatureFile)) {
            for (Class<?> cl : signature) {
                writer.print(classNames.get(cl) + " ");
            }
        }
    }

    private void checkTableName(String name) {
        if (name == null) {
            throw new IllegalArgumentException("Table name is null");
        }
        if (name.isEmpty()) {
            throw new IllegalArgumentException("Table name is empty");
        }
    }

    @Override
    public StoreableTable getTable(String name)
            throws IllegalArgumentException {
        checkTableName(name);
        return tables.get(name);
    }

    @Override
    public StoreableTable createTable(String name, List<Class<?>> columnTypes)
            throws IOException, IllegalArgumentException {
        checkTableName(name);
        if (columnTypes == null) {
            throw new IllegalArgumentException("Type can't be null");
        }

        try {
            new CurrentStoreable(columnTypes);
        } catch (ColumnFormatException e) {
            throw new IllegalArgumentException(e);
        }
        String directory = dbDirectory + File.separator + name;
        File tableDirectory = new File(directory);
        if (tableDirectory.exists() || tables.containsKey(name)) {
            System.out.println(name + " exists");
            return null;
        }
        try {
            Files.createDirectory(tableDirectory.toPath());
        } catch (IOException e) {
            System.err.println("Can't create directory: " + directory);
            return null;
        }
        File signatureFile = new File(tableDirectory
                + File.separator + signatureFileName);
        writeSignature(signatureFile, columnTypes);
        StoreableTable table = new StoreableTable(this, name, dbDirectory, columnTypes);
        tables.put(name, table);
        return table;
    }

    @Override
    public void removeTable(String name)
            throws IOException, IllegalArgumentException, IllegalStateException {
        checkTableName(name);
        String directory = dbDirectory + File.separator + name;
        File tableDirectory = new File(directory);
        if (!tableDirectory.exists() || !tables.containsKey(name)) {
            throw new IllegalArgumentException("table doesn't exist");
        }
        StoreableTable table = tables.remove(name);
        table.deleteFiles(name, true);
    }

    @Override
    public CurrentStoreable createFor(Table table) {
        List<Class<?>> signature = new ArrayList<>();
        for (int i = 0; i < table.getColumnsCount(); ++i) {
            signature.add(table.getColumnType(i));
        }
        return new CurrentStoreable(signature);
    }

    @Override
    public CurrentStoreable createFor(Table table, List<?> values)
            throws ColumnFormatException, IndexOutOfBoundsException {
        if (values.size() != table.getColumnsCount()) {
            throw new IndexOutOfBoundsException("Invalid number of values");
        }
        CurrentStoreable result = createFor(table);
        for (int i = 0; i < values.size(); ++i) {
            if (!table.getColumnType(i).equals(values.get(i).getClass())) {
                throw new ColumnFormatException("Invalid values format");
            }
            result.setColumnAt(i, values.get(i));
        }
        return result;
    }

    @Override
    public CurrentStoreable deserialize(Table table, String value)
            throws ParseException {
        if (!value.startsWith("<row>")) {
            throw new ParseException("<row> expected", 0);
        } else if (!value.endsWith("</row>")) {
            throw new ParseException("</row> expected", value.length() - 1);
        }
        value = value.substring(5, value.length() - 6);
        CurrentStoreable storeable = createFor(table);
        for (int columnNumber = 0; columnNumber < table.getColumnsCount();
             columnNumber++) {
            if (value.startsWith("<null/>")) {
                value = value.substring(7);
            } else if (value.startsWith("<col>")) {
                value = value.substring(5);
                int pos = value.indexOf("</col>");
                if (pos == -1) {
                    throw new ParseException("Incorrect xml syntax", 0);
                } else {
                    String column = value.substring(0, pos);
                    storeable.setColumnAt(columnNumber,
                            parseObject(column, table.getColumnType(columnNumber)));
                    value = value.substring(pos + 6);
                }
            } else {
                throw new ParseException("Incorrect xml syntax", 0);
            }
        }
        if (!"".equals(value)) {
            errorIncorrectFormat();
        }
        return storeable;
    }

    private Object parseObject(String str, Class<?> type) throws ParseException {
        try { // switch() { case: ... } почему-то не работает =(
            if (type == Integer.class) {
                return Integer.parseInt(str);
            }
            if (type == Long.class) {
                return Long.parseLong(str);
            }
            if (type == Byte.class) {
                return Byte.parseByte(str);
            }
            if (type == Float.class) {
                return Float.parseFloat(str);
            }
            if (type == Double.class) {
                return Double.parseDouble(str);
            }
            if (type == Boolean.class) {
                if (!"true".equals(str) && !"false".equals(str)) {
                    errorIncorrectFormat();
                }
                return Boolean.parseBoolean(str);
            } else {
                return str;
            }
        } catch (NumberFormatException e) {
            errorIncorrectFormat();
        } catch (NullPointerException e) {
            throw new ParseException("Null in parseObject", 0);
        }
        return str;
    }

    private void errorIncorrectFormat() throws ParseException {
        throw new ParseException("Incorrect storeable format", 0);
    }

    @Override
    public String serialize(Table table, Storeable value)
            throws ColumnFormatException, IndexOutOfBoundsException {
        for (int i = 0; i < table.getColumnsCount(); ++i) {
            Object val = value.getColumnAt(i);
            if (val != null && !val.getClass().equals(table.getColumnType(i))) {
                throw new ColumnFormatException("Invalid storeable format");
            }
        }

        StringBuilder serialized = new StringBuilder("<row>");
        for (int i = 0; i < table.getColumnsCount(); ++i) {
            Object column = value.getColumnAt(i);
            if (column == null) {
                serialized.append("<null/>");
            } else {
                serialized.append("<col>");
                if (column.getClass() == String.class) {
                    serialized.append(column);
                } else {
                    serialized.append(valueToString(column));
                }
                serialized.append("</col>");
            }
        }
        serialized.append("</row>");
        return serialized.toString();
    }

    private String valueToString(Object value) {
        if (value.getClass() == Integer.class) {
            return Integer.toString((Integer) value);
        }
        if (value.getClass() == Long.class) {
            return Long.toString((Long) value);
        }
        if (value.getClass() == Byte.class) {
            return Byte.toString((Byte) value);
        }
        if (value.getClass() == Float.class) {
            return Float.toString((Float) value);
        }
        if (value.getClass() == Double.class) {
            return Double.toString((Double) value);
        }
        if (value.getClass() == Boolean.class) {
            return Boolean.toString((Boolean) value);
        }
        return (String) value;
    }

    @Override
    public List<String> getTableNames() {
        return new ArrayList<>(tables.keySet());
    }

}
