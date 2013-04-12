/*
 * jGnash, a personal finance application
 * Copyright (C) 2001-2013 Craig Cavanaugh
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package jgnash.engine.jpa;

import jgnash.engine.Config;
import jgnash.engine.DataStore;
import jgnash.engine.Engine;
import jgnash.engine.EngineFactory;
import jgnash.engine.StoredObject;
import jgnash.util.FileUtils;
import jgnash.util.Resource;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Collection;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.Persistence;
import javax.persistence.TypedQuery;
import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Root;

/**
 * JPA specific code for data storage and creating an engine
 *
 * @author Craig Cavanaugh
 */
public class JpaHsqlDataStore implements DataStore {

    public static final String FILE_EXT = "script";

    private EntityManager em;

    private EntityManagerFactory factory;

    private boolean remote;

    private String fileName;

    private static final boolean DEBUG = false;

    private static final Logger logger = Logger.getLogger(JpaHsqlDataStore.class.getName());

    /**
     * Creates an empty database with the assumed default user name
     * @param fileName file name to use
     * @return true if successful
     */
    public static boolean initEmptyDatabase(final String fileName) {
        boolean result = false;

        StringBuilder urlBuilder = new StringBuilder("jdbc:hsqldb:file:");
        urlBuilder.append(FileUtils.stripFileExtension(fileName));

        try {
            Class.forName("org.hsqldb.jdbcDriver");
            Connection connection = DriverManager.getConnection(urlBuilder.toString(), "sa", "");
            connection.prepareStatement("CREATE USER " + JpaConfiguration.DEFAULT_USER + " PASSWORD \"\" ADMIN").execute();
            connection.commit();
            connection.close();

            connection = DriverManager.getConnection(urlBuilder.toString(), JpaConfiguration.DEFAULT_USER, "");
            connection.prepareStatement("DROP USER SA").execute();
            connection.commit();
            connection.close();

            result = true;

            logger.info("Initialized an empty database for " + FileUtils.stripFileExtension(fileName));
        } catch (ClassNotFoundException | SQLException e) {
            logger.log(Level.SEVERE, e.getMessage(), e);
        }

        return result;
    }

    @Override
    public void closeEngine() {

        logger.info("Closing");

        if (em != null && factory != null) {
            em.close();
            factory.close();
        } else {
            logger.severe("The EntityManger was already null!");
        }
    }

    @Override
    public Engine getClientEngine(final String host, final int port, final char[] password, final String dataBasePath) {
        Properties properties = JpaConfiguration.getClientProperties(Database.HSQLDB, dataBasePath, host, port, password);

        Engine engine = null;

        factory = Persistence.createEntityManagerFactory("jgnash", properties);

        em = factory.createEntityManager();

        if (em != null) {
            engine = new Engine(new JpaEngineDAO(em, true), EngineFactory.DEFAULT);

            logger.info("Created local JPA container and engine");
            fileName = null;
            remote = true;
        }

        return engine;
    }

    private boolean exists(final String fileName) {
        return Files.exists(Paths.get(FileUtils.stripFileExtension(fileName) + ".script"));
    }

    @Override
    public Engine getLocalEngine(final String fileName, final String engineName, final char[] password) {

        Properties properties = JpaConfiguration.getLocalProperties(Database.HSQLDB, fileName, password, false);

        Engine engine = null;

        if (DEBUG) {
            System.out.println(FileUtils.stripFileExtension(fileName));
        }

        // Check for existence of the file and init the database if needed
        if (!exists(fileName)) {
             initEmptyDatabase(fileName);
        }

        try {
            if (!FileUtils.isFileLocked(fileName)) {
                try {
                    factory = Persistence.createEntityManagerFactory("jgnash", properties);

                    em = factory.createEntityManager();

                    logger.info("Created local JPA container and engine");
                    engine = new Engine(new JpaEngineDAO(em, false), engineName);

                    this.fileName = fileName;
                    remote = false;
                } catch (final Exception e) {
                    logger.log(Level.SEVERE, e.getMessage(), e);
                }
            }
        } catch (FileNotFoundException e) {
            logger.info(Resource.get().getString("Message.FileNotFound"));
        }

        return engine;
    }

    @Override
    public String getFileExt() {
        return FILE_EXT;
    }

    @Override
    public String getFileName() {
        return fileName;
    }

    @Override
    public boolean isRemote() {
        return remote;
    }

    @Override
    public void saveAs(final File file, final Collection<StoredObject> objects) {

        // Remove the existing file so we don't mix entities and cause corruption
        if (file.exists()) {
            if (!file.delete()) {
                logger.log(Level.SEVERE, "Could not delete the old file for save");
                return;
            }
        }

        Properties properties = JpaConfiguration.getLocalProperties(Database.HSQLDB, file.getAbsolutePath(), new char[]{}, false);

        EntityManagerFactory factory = null;
        EntityManager em = null;

        try {
            factory = Persistence.createEntityManagerFactory("jgnash", properties);
            em = factory.createEntityManager();

            em.getTransaction().begin();

            for (StoredObject o : objects) {
                em.persist(o);
            }

            em.getTransaction().commit();
        } catch (Exception e) {
            logger.log(Level.SEVERE, e.getMessage(), e);
        } finally {
            if (em != null) {
                em.close();
            }

            if (factory != null) {
                factory.close();
            }
        }
    }

    /**
     * Opens the database in readonly mode and reads the version of the file format.
     *
     * @param file <code>File</code> to open
     * @return file version
     */
    public static float getFileVersion(final File file, final char[] password) throws Exception {
        float fileVersion = 0;

        Properties properties = JpaConfiguration.getLocalProperties(Database.HSQLDB, file.getAbsolutePath(), password, true);

        EntityManagerFactory factory = null;
        EntityManager em = null;

        // TODO, Just use SQL instead of booting JPA to find the version
        try {
            factory = Persistence.createEntityManagerFactory("jgnash", properties);

            em = factory.createEntityManager();

            CriteriaBuilder cb = em.getCriteriaBuilder();
            CriteriaQuery<Config> cq = cb.createQuery(Config.class);
            Root<Config> root = cq.from(Config.class);
            cq.select(root);

            TypedQuery<Config> q = em.createQuery(cq);

            Config defaultConfig = q.getSingleResult();

            fileVersion = defaultConfig.getFileVersion();
        } catch (Exception e) {
            throw new Exception(e);
        } finally {
            if (em != null) {
                em.close();
            }

            if (factory != null) {
                factory.close();
            }
        }

        return fileVersion;
    }

    /**
     * Returns the string representation of this <code>DataStore</code>.
     *
     * @return string representation of this <code>DataStore</code>.
     */
    @Override
    public String toString() {
        return Resource.get().getString("DataStoreType.HSQL");
    }

    /**
     * Deletes a Hsqldb database and associated files
     *
     * @param fileName one of the database files
     * @throws IOException
     */
    public static void deleteDatabase(final String fileName) throws IOException {
        String[] extensions = new String[]{".log", ".properties", ".script", ".data", ".backup", ".tmp", ".lobs"};

        String base = FileUtils.stripFileExtension(fileName);

        for (String extension : extensions) {
            Files.deleteIfExists(Paths.get(base + extension));
        }
    }
}