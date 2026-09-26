package top.zedo.zxncore;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.util.logging.Formatter;
import java.util.logging.Level;
import java.util.logging.LogRecord;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ZXLoggerFormatterTest {
    @Test
    void formatsRecordsWithoutSourceLineParameters() throws Exception {
        for (boolean ansi : new boolean[]{true, false}) {
            Formatter formatter = newFormatter(ansi);
            for (Object[] parameters : new Object[][]{null, new Object[0]}) {
                LogRecord record = new LogRecord(Level.INFO, "startup message");
                record.setSourceClassName("top.zedo.Startup");
                record.setSourceMethodName("initialize");
                record.setParameters(parameters);

                String formatted = formatter.format(record);

                assertTrue(formatted.contains("?"), formatted);
                assertTrue(formatted.contains("startup message"), formatted);
            }
        }
    }

    private static Formatter newFormatter(boolean ansi) throws Exception {
        Class<?> formatterClass = Class.forName("top.zedo.zxncore.ZXLogger$LoggerFormatter");
        Constructor<?> constructor = formatterClass.getDeclaredConstructor(boolean.class);
        constructor.setAccessible(true);
        return (Formatter) constructor.newInstance(ansi);
    }
}
