package com.github.pukkaone.gelf.logback;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.ThrowableProxy;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.pukkaone.gelf.protocol.GelfMessage;

import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class DefaultGelfMessageFactoryTest {

    private static final String HOST = "host";
    private static final String MDC_KEY = "mdcKey";
    private static final String MDC_VALUE = "mdcValue";
    private static final String FACILITY_VALUE = "facility";

    @Mock
    private GelfAppender appender;

    @Mock
    private ILoggingEvent event;

    private DefaultGelfMessageFactory marshaller;

    @Before
    public void beforeMethod() throws Exception {
        MockitoAnnotations.initMocks(this);

        when(event.getMDCPropertyMap())
                .thenReturn(Collections.singletonMap(MDC_KEY, MDC_VALUE));

        marshaller = new DefaultGelfMessageFactory();
    }

    @Test
    public void should_include_host() {
        when(appender.getOriginHost()).thenReturn(HOST);

        GelfMessage message = marshaller.createMessage(appender, event);

        assertThat(message.getHost(), is(HOST));
    }

    @Test
    public void should_exclude_MDC() {
        when(appender.isMdcIncluded()).thenReturn(false);

        GelfMessage message = marshaller.createMessage(appender, event);

        assertNull(message.getField(MDC_KEY));
    }

    @Test
    public void should_include_MDC() {
        when(appender.isMdcIncluded()).thenReturn(true);

        GelfMessage message = marshaller.createMessage(appender, event);

        assertEquals(MDC_VALUE, message.getField(MDC_KEY));
    }

    @Test
    public void should_include_facility() {
        when(appender.getFacility()).thenReturn(FACILITY_VALUE);

        GelfMessage message = marshaller.createMessage(appender, event);

        assertEquals(FACILITY_VALUE, message.getField(GelfMessage.FACILITY));
    }

    @Test
    public void should_use_default_short_message_format() throws IOException {
        when(event.getFormattedMessage()).thenReturn("log message");

        String jsonMessage = marshaller.createMessage(appender, event).toJson();
        HashMap result = new ObjectMapper().readValue(jsonMessage, HashMap.class);

        assertEquals("log message", result.get("short_message"));
    }

    @Test
    public void should_use_default_full_message_format() throws IOException {
        when(event.getFormattedMessage()).thenReturn("log message");
        Exception exc = new RuntimeException("whoops");
        when(event.getThrowableProxy()).thenReturn(new ThrowableProxy(exc));

        String jsonMessage = marshaller.createMessage(appender, event).toJson();
        HashMap result = new ObjectMapper().readValue(jsonMessage, HashMap.class);

        assertTrue(result.get("full_message").toString().startsWith(exc.toString()));
    }

    @Test
    public void should_use_custom_short_message_format() throws IOException {
        when(event.getFormattedMessage()).thenReturn("a very long log message a very long log message");

        marshaller.setShortMessagePattern("%.-23m");

        String jsonMessage = marshaller.createMessage(appender, event).toJson();
        HashMap result = new ObjectMapper().readValue(jsonMessage, HashMap.class);

        assertEquals("a very long log message", result.get("short_message"));
    }

    @Test
    public void should_use_custom_full_message_format() throws IOException {
        when(event.getFormattedMessage()).thenReturn("log message");

        marshaller.setFullMessagePattern("%.-1m");

        String jsonMessage = marshaller.createMessage(appender, event).toJson();
        HashMap result = new ObjectMapper().readValue(jsonMessage, HashMap.class);

        assertEquals("l", result.get("full_message"));
    }
}
