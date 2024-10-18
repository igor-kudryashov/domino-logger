package com.github.igor_kudryashov.domino.logger;

/* ====================================================================
Licensed to the Apache Software Foundation (ASF) under one or more
contributor license agreements.  See the NOTICE file distributed with
this work for additional information regarding copyright ownership.
The ASF licenses this file to You under the Apache License, Version 2.0
(the "License"); you may not use this file except in compliance with
the License.  You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
==================================================================== */


import java.util.Date;
import java.util.Properties;
import java.util.logging.Formatter;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.SimpleFormatter;

import com.sun.istack.internal.NotNull;
import lotus.domino.*;

/**
 * JUL handler for logging into Domino document like "Miscellaneous Events" in log.nsf.
 * Short example of usage bellow:
 * <pre>
 * {@code
 * import lotus.domino.*;
 * import java.util.logging.Level;
 * import java.util.logging.Logger;
 *
 * public class JavaAgent extends AgentBase {
 *     public static final Logger logger = Logger.getLogger(JavaAgent.class.getName());
 *
 *     public void NotesMain() {
 *         Session session = getSession();
 *         try {
 *             Properties props = new Properties();
 *             // log name for display document in the view
 *             props.setProperty("name", "Your logger name");
 *             // server name of log database
 *             props.setProperty("server", session.getServerName());
 *             // filename of log database
 *             props.setProperty("filename", "log.nsf");
 *             DominoHandler handler = new DominoHandler(session, props);
 *             logger.addHandler(handler);
 *             // (Your code goes here)
 *             logger.info("This is Info Message ");
 *             logger.log(Level.WARNING, "Warning Message");
 *             session.recycle();
 *         } catch (NotesException e) {
 *             logger.log(Level.SEVERE, e.getMessage(), e);
 *         }
 *     }
 * }
 * }
 * </pre>
 *
 * @author igor.kudryashov@gmail.com
 * @version 1.0
 */
public class DominoHandler extends Handler {
    private final Session session;
    private Database database;
    private Document document;
    private final String name;
    private final String server;
    private final String filename;
    private final String form;
    private final String field;

    // The number of LoggingEvents to log in a single Domino document.
    protected static final int DEFAULT_MAX_LINES = 1000;

    // Default form name.
    protected static final String DEFAULT_FORM_NAME = "Events";

    // Default field name.
    protected static final String DEFAULT_FIELD_NAME = "EventList";

    // The maximum to write to a document is 32kB.
    protected static final int MAX_DOCUMENT_SIZE = 1024 * 31;

    // Current log size
    private int currentSize = 0;

    // Current number of lines.
    private int currentLines = 0;

    /**
     * The class constructor sets the log parameters and creates a document in the specified Domino database for logging.
     *
     * @param session    current Domino session
     * @param properties properties of Handler. Properties that can be used:
     *                   <ul>
     *                        <li><i>name</i> - The name of the log to display in the Domino view. Can be <code>null</code>, but it is recommended to specify</li>
     *                        <li><i>server</i> - Domino server name for the logging database</li>
     *                        <li><i>filename</i> - file name for the logging database</li>
     *                        <li><i>form</i> - The name of the form for logging, can be <code>null</code>. By default, the "Events" form is used.</li>
     *                        <li><i>field</i> - The name of the field for logging, can be <code>null</code>. By default, the "EventsList" field is used.</li>
     *                   </ul>
     */
    public DominoHandler(Session session, Properties properties) {
        this.session = session;
        this.name = properties.getProperty("name");
        this.server = properties.getProperty("server");
        this.filename = properties.getProperty("filename");
        this.form = properties.getProperty("form", DEFAULT_FORM_NAME);
        this.field = properties.getProperty("field", DEFAULT_FIELD_NAME);

        setFormatter(new Formatter() {
            private static final String format = "%1$tY.%1$tm.%1$td %1$tT %2$-7s %3$s: %4$s";

            @Override
            public String format(LogRecord record) {
                return String.format(format,
                        new Date(record.getMillis()),
                        record.getLevel().getLocalizedName(),
                        record.getLoggerName(),
                        record.getMessage());
            }
        });
        try {
            database = session.getDatabase(server, filename);
            document = getDocument();
        } catch (NotesException e) {
            System.err.println("Error initializing handler: " + e.id + ". " + e.text);
            e.printStackTrace();
        }

    }

    @Override
    public void publish(LogRecord record) {
        if (!isLoggable(record)) {
            return;
        }
        Formatter formatter = getFormatter();
        if (formatter == null) {
            formatter = new SimpleFormatter();
        }
        String text = formatter.format(record);
        if ((currentSize + text.length()) >= MAX_DOCUMENT_SIZE || currentLines + 1 >= DEFAULT_MAX_LINES) {
            document = getDocument();
        }
        currentSize = currentSize + text.length();
        currentLines++;
        try {
            document.replaceItemValue(field, document.getItemValueString(field) + System.lineSeparator() + text);
            document.replaceItemValue("FinishTime", session.createDateTime(new Date()));
            document.save();
        } catch (NotesException e) {
            System.err.println("Error while changing log document: " + e.id + ". " + e.text);
            e.printStackTrace();
        }
    }

    /**
     * Closes the current document, if any, and creates a new one
     *
     * @return current document
     */
    public Document getDocument() {
        try {
            if (document.isValid()) {
                document.replaceItemValue("FinishTime", session.createDateTime(new Date()));
                document.save();
                document.recycle();
            }
            document = database.createDocument();
            document.replaceItemValue("Form", form);
            document.replaceItemValue("StartTime", session.createDateTime(new Date()));
            document.replaceItemValue("Name", name);
            document.replaceItemValue("Server", server);
            document.save();
            currentSize = 0;
            currentLines = 0;
            return document;
        } catch (NotesException e) {
            System.err.println("Error while getting document log: " + e.id + ". " + e.text);
            e.printStackTrace();
        }
        return null;
    }

    @Override
    public void flush() {
        if (document != null) {
            try {
                document.save();
            } catch (NotesException e) {
                System.err.println("Error saving log document: " + e.id + ". " + e.text);
                e.printStackTrace();
            }
        }
    }

    @Override
    public void close() throws SecurityException {
        try {
            if (document.isValid()) {
                document.save();
                document.recycle();
            }
            if (database != null) {
                database.recycle();
            }
        } catch (NotesException e) {
            System.err.println("Error while close handler: " + e.id + ". " + e.text);
            e.printStackTrace();
        }
    }
}
