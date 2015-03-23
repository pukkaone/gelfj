package com.github.pukkaone.gelf.logback;

import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.PatternLayout;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.util.LevelToSyslogSeverity;
import com.github.pukkaone.gelf.protocol.GelfMessage;
import java.lang.reflect.Method;
import java.text.NumberFormat;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Marker;

public class GelfMessageFactory {

    private PatternLayout shortPatternLayout;
    private PatternLayout fullPatternLayout;

    public GelfMessageFactory() {
        // Short message contains event message and no stack trace.
        shortPatternLayout = new PatternLayout();
        shortPatternLayout.setContext(new LoggerContext());
        shortPatternLayout.setPattern("%m%nopex");
        shortPatternLayout.start();

        // Full message contains stack trace.
        fullPatternLayout = new PatternLayout();
        fullPatternLayout.setContext(new LoggerContext());
        fullPatternLayout.setPattern("%xEx");
        fullPatternLayout.start();
    }

    public GelfMessage createMessage(GelfAppender appender, ILoggingEvent event) {
        GelfMessage message = new GelfMessage()
                .setTimestampMillis(event.getTimeStamp());

        String originHost = appender.getOriginHost();
        if (originHost != null) {
            message.setHost(originHost);
        }

        if (appender.isLevelIncluded()) {
            message.setLevel(LevelToSyslogSeverity.convert(event));
        }

        if (appender.isLocationIncluded()) {
            StackTraceElement locationInformation = event.getCallerData()[0];
            message.setFile(locationInformation.getFileName());
            message.setLine(locationInformation.getLineNumber());
        }

        if (appender.isLoggerIncluded()) {
            message.setLogger(event.getLoggerName());
        }

        if (appender.isMarkerIncluded()) {
            Marker marker = event.getMarker();
            if (marker != null) {
                message.setMarker(marker.getName());
            }
        }

        List<String> numericFields = appender.getNumericFields();
        if (appender.isMdcIncluded()) {
            Map<String, String> mdc = event.getMDCPropertyMap();
            if (mdc != null) {
                addFields(message, mdc, numericFields);
            }
        }

        if (appender.isThreadIncluded()) {
            message.setThread(event.getThreadName());
        }

        message.setShortMessage(shortPatternLayout.doLayout(event));

        String fullMessage = fullPatternLayout.doLayout(event);
        if (!fullMessage.isEmpty()) {
            message.setFullMessage(fullMessage);
        }

        Map<String, String> fields = appender.getAdditionalFields();
        addFields(message, fields, numericFields);

        return message;
    }

    private void addFields(GelfMessage message, Map<String, String> fields, List<String> numericFields)
    {
        for (Map.Entry<String, String> field : fields.entrySet()) {
                String key = field.getKey();
                Object value = convertNumericField(numericFields, key, field.getValue());
                message.addField(key, value);
        }
    }

    private Object convertNumericField(List<String> numericFields, String key, Object value) {
        if(numericFields.contains(key) && value != null) {
            try {
                value = NumberFormat.getInstance().parse(value.toString());
            } catch (Exception e1) {
                e1.printStackTrace();
            }
        }
        return value;
    }
}
