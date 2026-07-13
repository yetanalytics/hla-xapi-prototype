package com.yetanalytics.hlaxapi.injection;

import java.util.List;

public class RandomXapiValueGenerator {

    public static String getRandomValue(List<Object> statementPath, boolean inline) {
        // Generate a random string value based on the statement path
        StringBuilder sb = new StringBuilder();
        for (Object pathElement : statementPath) {
            sb.append(pathElement.toString()).append("-");
        }
        sb.append(generateRandomString(8)); // Append a random string of length 8
        return sb.toString();
    }

    public static String generateRandomString(int length) {
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            char randomChar = (char) ('a' + (int) (Math.random() * 26));
            sb.append(randomChar);
        }
        return sb.toString();
    }

}
