/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.logging.log4j.nosql.appender.mongodb;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.appender.AppenderLoggingException;
import org.apache.logging.log4j.nosql.appender.NoSqlConnection;
import org.apache.logging.log4j.nosql.appender.NoSqlObject;
import org.apache.logging.log4j.status.StatusLogger;
import org.apache.logging.log4j.util.Strings;
import org.bson.BSON;
import org.bson.Transformer;

import com.mongodb.BasicDBObject;
import com.mongodb.DB;
import com.mongodb.DBCollection;
import com.mongodb.Mongo;
import com.mongodb.MongoException;
import com.mongodb.WriteConcern;
import com.mongodb.WriteResult;

/**
 * MongoDB implementation of {@link NoSqlConnection}.
 */
public final class MongoDbConnection implements NoSqlConnection<BasicDBObject, MongoDbObject> {

    private static final Logger LOGGER = StatusLogger.getLogger();

    static {
        BSON.addEncodingHook(Level.class, new Transformer() {
            @Override
            public Object transform(final Object o) {
                return (o instanceof Level) ? ((Level) o).name() : o;
            }
        });
    }

    private final DBCollection collection;
    private final Mongo mongo;
    private final WriteConcern writeConcern;

    public MongoDbConnection(final DB database,
                             final WriteConcern writeConcern,
                             final String collectionName) {
        this.mongo        = database.getMongo();
        this.collection   = database.getCollection(collectionName);
        this.writeConcern = writeConcern;
    }

    @Override
    public MongoDbObject createObject() {
        return new MongoDbObject();
    }

    @Override
    public MongoDbObject[] createList(final int length) {
        return new MongoDbObject[length];
    }

    @Override
    public void insertObject(final NoSqlObject<BasicDBObject> object) {
        try {
            final WriteResult result =
                    this.collection.insert(object.unwrap(), this.writeConcern);
            if (Strings.isNotEmpty(result.getError())) {
                throw new AppenderLoggingException(
                        "Failed to write log event to MongoDB due to error: "
                                + result.getError() + '.');
            }
        } catch (final MongoException e) {
            throw new AppenderLoggingException(
                    "Failed to write log event to MongoDB due to error: "
                            + e.getMessage(), e);
        }
    }

    @Override
    public void close() {
        // No need to call mongo.close(); connection pooling is handled internally.
        // See LOG4J2‑591 for details.
    }

    @Override
    public boolean isClosed() {
        return !this.mongo.getConnector().isOpen();
    }

    /**
     * Authenticates the supplied database.  Cannot live in MongoDbProvider
     * to avoid class‑loading issues during plugin discovery.
     *
     * @param database  Database to authenticate
     * @param username  Username
     * @param password  Password
     */
    static void authenticate(final DB database,
                             final String username,
                             final String password) {
        try {
            if (!database.authenticate(username, password.toCharArray())) {
                LOGGER.error("Failed to authenticate against MongoDB server. Unknown error.");
            }
        } catch (final MongoException e) {
            LOGGER.error("Failed to authenticate against MongoDB: " + e.getMessage(), e);
        } catch (final IllegalStateException e) {
            LOGGER.error("Factory‑supplied MongoDB database connection already "
                       + "authenticated with different credentials but lost connection.", e);
        }
    }
}
