package com.yetanalytics.hlaxapi;

import java.util.List;
import java.util.UUID;

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

    private StatementClient client;

    public XapiClient(XapiConfig xapiConfig) {
        LRS lrs = new LRS(
            xapiConfig.lrsConfig.host,
            xapiConfig.lrsConfig.key,
            xapiConfig.lrsConfig.secret,
            xapiConfig.lrsConfig.batch);
        client = new StatementClient(lrs);
    }

    public StatementClient getClient() {
        return client;
    }

    public List<UUID> postStatementFromString(String s) throws JsonMappingException, JsonProcessingException{
        Statement stmt = Mapper.getMapper().readValue(s, Statement.class);
        return client.postStatement(stmt);
    }

}
