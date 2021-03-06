/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache license, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the license for the specific language governing permissions and
 * limitations under the license.
 */
package org.apache.logging.log4j.status;

import org.apache.logging.log4j.simple.SimpleLogger;
import org.apache.logging.log4j.spi.AbstractLogger;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.Marker;
import org.apache.logging.log4j.message.Message;
import org.apache.logging.log4j.util.PropertiesUtil;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Mechanism to record events that occur in the logging system.
 */
public final class StatusLogger extends AbstractLogger {

    /**
     * System property that can be configured with the number of entries in the queue. Once the limit
     * is reached older entries will be removed as new entries are added.
     */
    public static final String MAX_STATUS_ENTRIES = "log4j2.status.entries";

    private static final String NOT_AVAIL = "?";

    private static final PropertiesUtil PROPS = new PropertiesUtil("log4j2.StatusLogger.properties");

    private static final int MAX_ENTRIES = PROPS.getIntegerProperty(MAX_STATUS_ENTRIES, 200);

    // private static final String FQCN = AbstractLogger.class.getName();

    private static final StatusLogger STATUS_LOGGER = new StatusLogger();

    private final SimpleLogger logger;

    private final CopyOnWriteArrayList<StatusListener> listeners = new CopyOnWriteArrayList<StatusListener>();
    private final ReentrantReadWriteLock listenersLock = new ReentrantReadWriteLock();

    private final Queue<StatusData> messages = new BoundedQueue<StatusData>(MAX_ENTRIES);
    private final ReentrantLock msgLock = new ReentrantLock();

    private StatusLogger() {
        this.logger = new SimpleLogger("StatusLogger", Level.ERROR, false, true, false, false, "", null, PROPS,
            System.err);
    }

    /**
     * Retrieve the StatusLogger.
     * @return The StatusLogger.
     */
    public static StatusLogger getLogger() {
        return STATUS_LOGGER;
    }

    public void setLevel(final Level level) {
        logger.setLevel(level);
    }

    /**
     * Register a new listener.
     * @param listener The StatusListener to register.
     */
    public void registerListener(final StatusListener listener) {
        listenersLock.writeLock().lock();
        try {
            listeners.add(listener);
        } finally {
            listenersLock.writeLock().unlock();
        }
    }

    /**
     * Remove a StatusListener.
     * @param listener The StatusListener to remove.
     */
    public void removeListener(final StatusListener listener) {
        listenersLock.writeLock().lock();
        try {
            listeners.remove(listener);
        } finally {
            listenersLock.writeLock().unlock();
        }
    }

    /**
     * Returns a thread safe Iterator for the StatusListener.
     * @return An Iterator for the list of StatusListeners.
     */
    public Iterator<StatusListener> getListeners() {
        return listeners.iterator();
    }

    /**
     * Clears the list of status events and listeners.
     */
    public void reset() {
        listeners.clear();
        clear();
    }

    /**
     * Returns a List of all events as StatusData objects.
     * @return The list of StatusData objects.
     */
    public List<StatusData> getStatusData() {
        msgLock.lock();
        try {
            return new ArrayList<StatusData>(messages);
        } finally {
            msgLock.unlock();
        }
    }

    /**
     * Clears the list of status events.
     */
    public void clear() {
        msgLock.lock();
        try {
            messages.clear();
        } finally {
            msgLock.unlock();
        }
    }


    /**
     * Add an event.
     * @param marker The Marker
     * @param fqcn   The fully qualified class name of the <b>caller</b>
     * @param level  The logging level
     * @param msg    The message associated with the event.
     * @param t      A Throwable or null.
     */
    @Override
    public void log(final Marker marker, final String fqcn, final Level level, final Message msg, final Throwable t) {
        StackTraceElement element = null;
        if (fqcn != null) {
            element = getStackTraceElement(fqcn, Thread.currentThread().getStackTrace());
        }
        final StatusData data = new StatusData(element, level, msg, t);
        msgLock.lock();
        try {
            messages.add(data);
        } finally {
            msgLock.unlock();
        }
        if (listeners.size() > 0) {
            for (final StatusListener listener : listeners) {
                listener.log(data);
            }
        } else {
            logger.log(marker, fqcn, level, msg, t);
        }
    }

    private StackTraceElement getStackTraceElement(final String fqcn, final StackTraceElement[] stackTrace) {
        if (fqcn == null) {
            return null;
        }
        boolean next = false;
        for (final StackTraceElement element : stackTrace) {
            if (next) {
                return element;
            }
            final String className = element.getClassName();
            if (fqcn.equals(className)) {
                next = true;
            } else if (NOT_AVAIL.equals(className)) {
                break;
            }
        }
        return null;
    }

    @Override
    protected boolean isEnabled(final Level level, final Marker marker, final String data) {
        return isEnabled(level, marker);
    }

    @Override
    protected boolean isEnabled(final Level level, final Marker marker, final String data, final Throwable t) {
        return isEnabled(level, marker);
    }

    @Override
    protected boolean isEnabled(final Level level, final Marker marker, final String data, final Object... p1) {
        return isEnabled(level, marker);
    }

    @Override
    protected boolean isEnabled(final Level level, final Marker marker, final Object data, final Throwable t) {
        return isEnabled(level, marker);
    }

    @Override
    protected boolean isEnabled(final Level level, final Marker marker, final Message data, final Throwable t) {
        return isEnabled(level, marker);
    }

    protected boolean isEnabled(final Level level, final Marker marker) {
        if (listeners.size() > 0) {
            return true;
        }
        switch (level) {
            case FATAL:
                return logger.isFatalEnabled(marker);
            case TRACE:
                return logger.isTraceEnabled(marker);
            case DEBUG:
                return logger.isDebugEnabled(marker);
            case INFO:
                return logger.isInfoEnabled(marker);
            case WARN:
                return logger.isWarnEnabled(marker);
            case ERROR:
                return logger.isErrorEnabled(marker);
        }
        return false;
    }

    /**
     * Queue for status events.
     * @param <E> Object type to be stored in the queue.
     */
    private class BoundedQueue<E> extends ConcurrentLinkedQueue<E> {

        private static final long serialVersionUID = -3945953719763255337L;

        private final int size;

        public BoundedQueue(final int size) {
            this.size = size;
        }

        @Override
        public boolean add(final E object) {
            while (messages.size() > size) {
                messages.poll();
            }
            return super.add(object);
        }
    }
}
