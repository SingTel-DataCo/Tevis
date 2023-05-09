package com.dataspark.networkds.dao;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.apache.commons.io.monitor.FileAlterationListenerAdaptor;
import org.apache.commons.io.monitor.FileAlterationMonitor;
import org.apache.commons.io.monitor.FileAlterationObserver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;

import com.fasterxml.jackson.module.scala.DefaultScalaModule;

public class JsonDb<T> {

    private static final Logger LOGGER = LoggerFactory.getLogger(JsonDb.class);

    private static final ObjectMapper OBJECT_MAPPER =
        new ObjectMapper().registerModule(new DefaultScalaModule())
        .setDateFormat(new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXX"))
                .enable(SerializationFeature.INDENT_OUTPUT);

    private String dataFile;
    private T dataCache;

    private Class<T> jsonDataModel;

    public JsonDb(String fullDataFile, Class<T> jsonDataModel) {
        this.dataFile = fullDataFile;
        this.jsonDataModel = jsonDataModel;
        dataCache = loadJson(fullDataFile, jsonDataModel);
        addFileChangeListener(fullDataFile);
    }

    private void addFileChangeListener(String fullDataFile) {

        FileAlterationObserver observer = new FileAlterationObserver(new File(fullDataFile).getParentFile());
        FileAlterationMonitor monitor = new FileAlterationMonitor(5000);
        monitor.addObserver(observer);
        observer.addListener(new FileAlterationListenerAdaptor(){
            @Override
            public void onFileChange(File file) {
                reload();
            }
        });

        try {
            monitor.start();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public T get() {
        if (dataCache == null) {
            dataCache = loadJson(dataFile, jsonDataModel);
        }
        return dataCache;
    }

    public void set(T data) {
        dataCache = data;
    }

    public boolean save() {
        return saveJson(dataCache, dataFile);
    }

    //utility methods

    public static <T> T loadJson(String dataFile, Class<T> jsonDataModel) {
        File f = new File(dataFile);
        if (f.exists()) {
            try (FileReader reader = new FileReader(f)) {
                T dataModel = OBJECT_MAPPER.readValue(reader, jsonDataModel);
                LOGGER.info("JSON DB location: " + f.getAbsolutePath());
                return dataModel;
            } catch (IOException e) {
                LOGGER.error("Error reading file " + dataFile, e);
            }
        }
        try {
            T dataModel = jsonDataModel.newInstance();
            LOGGER.info("JSON DB location: " + f.getAbsolutePath());
            return dataModel;
        } catch (InstantiationException | IllegalAccessException e) {
            LOGGER.error(e.getMessage(), e);
            return null;
        }
    }

    public static <T> boolean saveJson(T dataCache, String dataFile) {

        try {
            OBJECT_MAPPER.writeValue(new FileWriter(dataFile), dataCache);
            return true;
        } catch (IOException e) {
            LOGGER.error(e.getMessage(), e);
        }
        return false;
    }

    public T reload() {
        dataCache = null;
        return get();
    }

}