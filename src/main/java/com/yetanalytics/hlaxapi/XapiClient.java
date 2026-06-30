package com.yetanalytics.hlaxapi;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.yetanalytics.hlaxapi.config.XapiConfig;

import com.yetanalytics.xapi.client.StatementClient;
import com.yetanalytics.xapi.model.Statement;
import com.yetanalytics.xapi.client.LRS;

import com.yetanalytics.xapi.util.Mapper;

@Component
public class XapiClient {

    private static final Logger logger = LogManager.getLogger(XapiClient.class);

    private StatementClient client;

    private List<Statement> buffer;

    private Integer batchSize;

    public XapiClient(XapiConfig xapiConfig) {
        LRS lrs = new LRS(
            xapiConfig.lrsConfig.host,
            xapiConfig.lrsConfig.key,
            xapiConfig.lrsConfig.secret,
            xapiConfig.lrsConfig.batch);
        client = new StatementClient(lrs);
        buffer = new ArrayList<Statement>();
        batchSize = xapiConfig.lrsConfig.batch;
    }

    private StatementClient getClient() {
        return client;
    }

    public void sendStatementFromString(String s) throws JsonMappingException, JsonProcessingException{
        Statement stmt = Mapper.getMapper().readValue(s, Statement.class);
        addToBuffer(stmt);
    }

    /** Synchronized buffer methods */

    private synchronized void addToBuffer(Statement stmt){
        buffer.add(stmt);

        //trigger a clear if batch size reached
        if (buffer.size() >= batchSize)
            clearBuffer();
    }

    // Check and clean buffer to LRS every 10 seconds (or ENV) if contains statements
    @Scheduled(fixedRateString = "${xapi.buffer.clear-rate:10000}")
    private synchronized void clearBuffer() {
        logger.info("Buffer Size: {}", buffer.size());
        if (buffer.size() > 0){
            List<UUID> results = client.postStatements(buffer);
            logger.info("Stored statements: {}", results);

            //TODO: Error handling and such

            buffer = new ArrayList<Statement>();
            logger.info("Cleared Buffer");
        }
    }

}
