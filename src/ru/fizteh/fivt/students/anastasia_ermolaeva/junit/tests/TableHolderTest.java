package ru.fizteh.fivt.students.anastasia_ermolaeva.junit.tests;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import ru.fizteh.fivt.students.anastasia_ermolaeva.junit.TableHolder;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;


public class TableHolderTest {
    private final Path testDirectory = Paths.get(System.getProperty("fizteh.db.dir"));
    private final String tableDirectoryName = "Тест";
    private final Path tableDirPath = testDirectory.resolve(tableDirectoryName);
    private final String testTableName = "Тестовая таблица";
    private final String wrongTableName = ".";

    @Before
    public void setUp() {
        testDirectory.toFile().mkdir();
    }

    @Test
    public void testTableHolderCreatedForNonexistentDirectory() {
        new TableHolder(tableDirPath.toString());
        assertTrue(tableDirPath.toFile().exists());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testTableHolderThrowsIllegalArgumentExceptionCreatedNotForDirectory() throws IOException {
        File newFile = new File(testDirectory.toAbsolutePath() + File.separator + "filename.txt");
        newFile.createNewFile();
        new TableHolder(newFile.toString());
    }
    @Test(expected = IllegalArgumentException.class)
    public void testTableHolderThrowsIllegalArgumentExceptionCreatedForInvalidPath() {
        new TableHolder("\0");
    }

    @Test(expected = IllegalArgumentException.class)
    public void testTableHolderThrowsIllegalArgumentExceptionCreatedForDirectoryContainedNonDirectories() throws IOException {
        File newFile = new File(testDirectory.toAbsolutePath() + File.separator + "filename.txt");
        newFile.createNewFile();
        new TableHolder(newFile.toString());
    }

    @Test
    public void testGetTableReturnsNullIfTableNameDoesNotExist() {
        TableHolder test = new TableHolder(testDirectory.toString());
        test.createTable(tableDirectoryName);
        assertNull(test.getTable("MyTable"));
    }

    @Test (expected = IllegalArgumentException.class)
    public void testGetTableThrowsIllegalArgumentExceptionCalledForNullTableDirectoryName() {
        TableHolder test = new TableHolder(testDirectory.toString());
        test.getTable(null);
    }
    @Test (expected = IllegalArgumentException.class)
    public void testGetTableThrowsIllegalArgumentExceptionCalledForWrongTableDirectoryName() {
        TableHolder test = new TableHolder(testDirectory.toString());
        test.getTable(wrongTableName);
    }

    @Test (expected = IllegalArgumentException.class)
    public void testCreateTableThrowsIllegalArgumentExceptionCalledForNullTableDirectoryName() {
        TableHolder test = new TableHolder(testDirectory.toString());
        test.createTable(null);
    }
    @Test(expected = IllegalArgumentException.class)
    public void testCreateTableThrowsIllegalArgumentExceptionCalledForWrongTableDirectoryName() {
        TableHolder test = new TableHolder(testDirectory.toString());
        test.createTable(wrongTableName);
    }
    @Test
    public void testCreateTableCreatedTableDirectoryOnTheDiskCalledForValidTableDirectoryName() {
        TableHolder test = new TableHolder(testDirectory.toString());
        test.createTable(testTableName);
        Path newTablePath = testDirectory.resolve(testTableName);
        assertTrue(newTablePath.toFile().exists() && newTablePath.toFile().isDirectory());
    }

    @Test
    public void testCreateTableReturnsNullCalledForExistentOnDiskTable() {
        tableDirPath.toFile().mkdir();
        TableHolder test = new TableHolder(testDirectory.toString());
        assertNull(test.createTable(tableDirectoryName));
    }

    @Test
    public void testRemoveTableRemovedTableDirectoryOnTheDiskCalledForValidTableDirectoryName() throws Exception {
        TableHolder test = new TableHolder(testDirectory.toString());
        test.createTable(testTableName);
        Path newTablePath = testDirectory.resolve(testTableName);
        test.removeTable(testTableName);
        assertFalse(newTablePath.toFile().exists());
    }
    @Test(expected = IllegalArgumentException.class)
    public void testRemoveTableThrowsIllegalArgumentExceptionCalledForNullTableDirectoryName() {
        TableHolder test = new TableHolder(testDirectory.toString());
        test.removeTable(null);
    }

    @Test(expected = IllegalStateException.class)
    public void testRemoveTableThrowsIllegalStateExceptionIfTableNameDoesNotExist() {
        TableHolder test = new TableHolder(testDirectory.toString());
        test.removeTable("MyTable");
    }

    @After
    public void tearDown() {
        for (File currentFile : testDirectory.toFile().listFiles()) {
            if (currentFile.isDirectory()) {
                for (File subFile : currentFile.listFiles()) {
                    subFile.delete();
                }
            }
            currentFile.delete();
        }
        testDirectory.toFile().delete();
    }
}
