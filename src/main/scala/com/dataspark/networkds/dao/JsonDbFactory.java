package com.dataspark.networkds.dao;

import com.dataspark.networkds.util.AppProperties;

import java.util.HashMap;
import java.util.Map;


public class JsonDbFactory {

    private static Map<String, JsonDb> jsonDbMap = new HashMap<>();

    public static <T> JsonDb<T> getInstance(String dataFile, Class<T> jsonDataModel) {
        String fullDataFile = AppProperties.get("data_dir") + "/" + dataFile;
        JsonDb<T> instance = jsonDbMap.get(dataFile);
        if (instance == null) {
            instance = new JsonDb<T>(fullDataFile, jsonDataModel);
            jsonDbMap.put(dataFile, instance);
        }
        return instance;
    }

}