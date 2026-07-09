package com.yetanalytics.hlaxapi;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.yetanalytics.hlaxapi.config.XapiConfig;
import com.yetanalytics.hlaxapi.exception.StatementValidationException;
import com.yetanalytics.xapi.client.LRS;
import com.yetanalytics.xapi.model.Statement;
import com.yetanalytics.xapi.client.StatementClient;
import com.yetanalytics.xapi.exception.StatementClientException;
import com.yetanalytics.xapi.util.StatementValidator;
import com.yetanalytics.xapi.util.StatementValidator.StatementValidationResult;

@Component
public class XapiClient {

    private static final Logger logger = LogManager.getLogger(XapiClient.class);

    private StatementClient client;

    private List<Statement> buffer;

    private StatementValidator validator;

    private Integer batchSize;

    private Integer retryCount = 0;

    private Integer maxRetries;

    public XapiClient(XapiConfig xapiConfig, StatementValidator validator) {
        this.validator = validator;
        LRS lrs = new LRS(
            xapiConfig.lrsConfig.host,
            xapiConfig.lrsConfig.key,
            xapiConfig.lrsConfig.secret,
            xapiConfig.lrsConfig.batch
        );
        client = new StatementClient(lrs);
        buffer = new ArrayList<Statement>();
        batchSize = xapiConfig.lrsConfig.batch;
        maxRetries = xapiConfig.lrsConfig.maxRetries;
    }

    public void sendStatement(String s) throws StatementValidationException {
        addToBuffer(validator.validateStatement(s));
    }

    public void sendStatement(Statement stmt) throws StatementValidationException {
        addToBuffer(validator.validateStatement(stmt));
    }

    private void addToBuffer(StatementValidationResult res) throws StatementValidationException {
        if (!res.isValid()) {
            logger.error("Invalid statement: {}", res.getErrors());
            throw new StatementValidationException("Invalid statement", res.getErrors());
        }
        addToBuffer(res.getStatement());
    }

    /** Synchronized buffer methods */

    private synchronized void addToBuffer(Statement stmt){
        buffer.add(stmt);
    }

    // Check and post buffer to LRS every 10 seconds (or ENV) if contains statements
    @Scheduled(fixedRateString = "${xapi.buffer.clear-rate:10000}")
    private synchronized void clearBuffer() {
        logger.info("Buffer Size: {}", buffer.size());
        if (buffer.size() > 0){
            try {
                List<UUID> results = client.postStatements(buffer);
                logger.info("Stored statements: {}", results);
                buffer = new ArrayList<Statement>();
                logger.info("Cleared Buffer");
                retryCount = 0;
            } catch (StatementClientException e) {
                logger.error("Error sending statements to LRS:", e);
                if (retryCount < maxRetries) {
                    retryCount++;
                    logger.info("Retrying to send statements to LRS, attempt {}/{}", retryCount, maxRetries);
                } else {
                    // TODO: For durability, we should write the buffer to a file or database or DLQ for retrying later
                    // might be a case for a light queueing system like RabbitMQ. Also failures will be reduced if we
                    // pre-validate statements before sending to the LRS. Also we may want to reduce the accrual of
                    // statements in the buffer if we have a DLQ strategy, that way less innocent statements are lost.
                    // For now, we will just clear the buffer after max retries.
                    // TODO: Additionally we should consider a cap on the buffer size, and if it exceeds that cap,
                    // we should start dropping statements or writing to a DLQ.
                    buffer = new ArrayList<Statement>();
                    retryCount = 0;
                    logger.info("Cleared Buffer after max retries, statement data lost!");
                }
            }
        }
    }

}
