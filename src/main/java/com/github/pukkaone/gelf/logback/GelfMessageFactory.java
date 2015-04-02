package com.github.pukkaone.gelf.logback;

import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.PatternLayout;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.util.LevelToSyslogSeverity;
import com.github.pukkaone.gelf.protocol.GelfMessage;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import org.slf4j.Marker;

public class GelfMessageFactory {

    private PatternLayout shortPatternLayout;
    private PatternLayout fullPatternLayout;

    static Map<String, Method> primitiveTypes;

    static {
        primitiveTypes = new HashMap<String, Method>();
        try {
            primitiveTypes.put("int", Integer.class.getDeclaredMethod("parseInt", String.class));
            primitiveTypes.put("Integer", Integer.class.getDeclaredMethod("parseInt", String.class));
            primitiveTypes.put("long", Long.class.getDeclaredMethod("parseLong", String.class));
            primitiveTypes.put("Long", Long.class.getDeclaredMethod("parseLong", String.class));
            primitiveTypes.put("float", Float.class.getDeclaredMethod("parseFloat", String.class));
            primitiveTypes.put("Float", Float.class.getDeclaredMethod("parseFloat", String.class));
            primitiveTypes.put("double", Double.class.getDeclaredMethod("parseDouble", String.class));
            primitiveTypes.put("Double", Double.class.getDeclaredMethod("parseDouble", String.class));
        } catch (NoSuchMethodException e) {
            e.printStackTrace();
        }
    }
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

        Map<String, String> fieldTypes = appender.getFieldTypes();
        if (appender.isMdcIncluded()) {
            Map<String, String> mdc = event.getMDCPropertyMap();
            if (mdc != null) {
                addFields(message, mdc, fieldTypes);
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

        message.setFacility(appender.getFacility());

        Map<String, String> fields = appender.getAdditionalFields();
        addFields(message, fields, fieldTypes);

        return message;
    }

    private void addFields(GelfMessage message, Map<String, String> fields, Map<String, String> fieldTypes)
    {
        for (Map.Entry<String, String> field : fields.entrySet()) {
                String key = field.getKey();
                Object value = convertFieldType(fieldTypes, key, field.getValue());
                message.addField(key, value);
        }
    }

    private Object convertFieldType(Map<String, String> fieldTypes, String key, Object value) {
        String fieldType = fieldTypes.get(key);
        if (primitiveTypes.containsKey(fieldType)) {
            try {
                value = primitiveTypes.get(fieldType).invoke(null, value);
            } catch (Exception e1) {
                e1.printStackTrace();
            }
        }
        return value;
    }
}
