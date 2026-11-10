package ste.wakemeapp.auth;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import static org.assertj.core.api.BDDAssertions.then;

/** MSAL4J logs through SLF4J; its messages must reach java.util.logging, i.e. the application log. */
class MsalLoggingTest {

    @Test
    void a_message_logged_through_slf4j_reaches_java_util_logging() {
        List<LogRecord> records = new ArrayList<>();
        Logger jul = Logger.getLogger("com.microsoft.aad.msal4j");
        jul.setLevel(Level.ALL);
        Handler capture = new Handler() {
            @Override public void publish(LogRecord record) { records.add(record); }
            @Override public void flush() { }
            @Override public void close() { }
        };
        jul.addHandler(capture);
        try {
            LoggerFactory.getLogger("com.microsoft.aad.msal4j.SomeClass").warn("token trouble");
        } finally {
            jul.removeHandler(capture);
        }

        then(records).hasSize(1);
        then(records.get(0).getLevel()).isEqualTo(Level.WARNING);
        then(records.get(0).getMessage()).isEqualTo("token trouble");
    }
}
