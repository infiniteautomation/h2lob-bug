/**
 * Copyright (C) 2018 Infinite Automation Software. All rights reserved.
 */
package com.infiniteautomation;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectOutputStream;
import java.sql.Blob;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

import org.h2.jdbcx.JdbcConnectionPool;
import org.h2.jdbcx.JdbcDataSource;
import org.h2.tools.Server;

/**
 * Example to show how the database size grows dramatically from updating a longblob column.
 *
 * @author Terry Packer
 */
public class Main {
    protected static final File baseTestDir = new File("junit");
    protected static final int LOB_TIMEOUT = 25;
    
    public static void main(String[] args) throws SQLException, IOException, InterruptedException {
        
        delete(baseTestDir);
        
        //Setup the data source
        JdbcDataSource jds = new JdbcDataSource();
        jds.setURL(getUrl());
        jds.setDescription("test");
        JdbcConnectionPool pool = JdbcConnectionPool.create(jds);
             
        //Create the test table
        Connection conn = pool.getConnection();
        conn.setAutoCommit(true);
        conn.createStatement().executeUpdate("CREATE TABLE test (id int NOT NULL auto_increment, data longblob, PRIMARY KEY (id));");
        conn.close();
        
        Server web = Server.createWebServer(new String[] {"-webPort", "8091", "-ifExists"});
        web.start();
        
        //Get a handle on the file to check its size later
        File dbFile = new File(baseTestDir, "databases" + File.separator + "h2-test.h2.db");
        if(!dbFile.exists()) {
            throw new IOException("Database doesn't exist");
        }
        
        //Insert a row to update
        conn = pool.getConnection();
        conn.setAutoCommit(true);
        int id = conn.createStatement().executeUpdate("INSERT INTO test (data) VALUES (NULL)");
        conn.close();

        //Generate the data
        Map<Integer, Long> data = new HashMap<>(10000);
        for(int i=0; i<10000; i++) {
            data.put(i, 0L);
        }
        Map<String, Object> rtData = new HashMap<String, Object>();
        rtData.put("RT_DATA", data);
        
        //Update the row and watch the database size grow during runtime
        for(int i=0; i<2000; i++) {
            for(Entry<Integer, Long> entry : data.entrySet())
                entry.setValue(entry.getValue() + 1L);
            updateRow(id, rtData, pool.getConnection());
            Thread.sleep(LOB_TIMEOUT  + 1);
            if(i%10 == 0) {
                System.out.println(bytesDescription(new File(baseTestDir, "databases" + File.separator + "h2-test.h2.db").length()));
            }
        }
    
        conn = pool.getConnection();
        ResultSet rs = conn.createStatement().executeQuery("SELECT data FROM test WHERE id=" + id);
        rs.next();
        Blob blob = (Blob)rs.getObject(1);
        blob.getBytes(1, (int) blob.length());
        conn.close();
        
        web.stop();
        
        //Size before close of db (~36MB)
        System.out.println(bytesDescription(dbFile.length()));
        pool.dispose();
        //Size after close (~319k)
        System.out.println(bytesDescription(dbFile.length()));
    }
    
    public static String getUrl() {
        return "jdbc:h2:" + baseTestDir.getAbsolutePath() + "/databases/h2-test;MV_STORE=FALSE;LOB_TIMEOUT=" + LOB_TIMEOUT;
    }

    public static void updateRow(int id, Map<String, Object> rtData, Connection conn) throws SQLException, IOException {
        conn.setAutoCommit(true);
        PreparedStatement ps = conn.prepareStatement("UPDATE test SET data=? WHERE id=?");
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ObjectOutputStream oos = new ObjectOutputStream(baos);
        oos.writeObject(rtData);
        oos.flush();
        oos.close();
        InputStream is = new ByteArrayInputStream(baos.toByteArray());
        ps.setBinaryStream(1, is);
        ps.setInt(2, id);
        ps.executeUpdate();
        ps.close();
        conn.close();
    }
    
    public static void delete(File f) throws IOException {
        if (f.isDirectory()) {
            for (File c : f.listFiles())
                delete(c);
        }
        if (f.exists() && !f.delete())
            throw new FileNotFoundException("Failed to delete file: " + f);
    }
    
    public static String bytesDescription(long size) {
        String sizeStr;
        if (size < 1028)
            sizeStr = size + " B";
        else {
            size /= 1028;
            if (size < 1000)
                sizeStr = size + " KB";
            else {
                size /= 1000;
                if (size < 1000)
                    sizeStr = size + " MB";
                else {
                    size /= 1000;
                    if (size < 1000)
                        sizeStr = size + " GB";
                    else {
                        size /= 1000;
                        sizeStr = size + " TB";
                    }
                }
            }
        }

        return sizeStr;
    }
}
