package com.bhatman.astra;

import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;
import java.text.SimpleDateFormat;
import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.DefaultConsistencyLevel;
import com.datastax.oss.driver.api.core.config.DefaultDriverOption;
import com.datastax.oss.driver.api.core.config.DriverExecutionProfile;
import com.datastax.oss.driver.api.core.cql.AsyncResultSet;
import com.datastax.oss.driver.api.core.cql.PreparedStatement;
import com.datastax.oss.driver.api.core.cql.Row;
import com.datastax.oss.driver.api.querybuilder.QueryBuilder;
import com.datastax.oss.driver.api.querybuilder.select.Select;

public class App {

    private static Logger LOGGER = LoggerFactory.getLogger(App.class);

    private static PreparedStatement stmtInsertRecord;
    private static Select select = QueryBuilder.selectFrom(AppUtil.LATENCY_TABLE)
            .columns("event_identifier", "event_state", "event_name", "event_payload_url", "event_timestamp", "id")
            .whereColumn("event_identifier").isEqualTo(QueryBuilder.bindMarker())
            .whereColumn("event_state").isEqualTo(QueryBuilder.bindMarker());

    private static final String[] EVENT_STATES = {"VALIDATED", "CREATED", "RESCHEDULED", "CANCELLED", "MODIFIED"};
    private static final Random RANDOM = new Random();
    private static final AtomicInteger eventIdentifierCounter = new AtomicInteger(0);

    private CqlSession session;
    public String dcName;
    private PreparedStatement findRecords;
    DriverExecutionProfile dynamicProfile;
    private int numOfRows;

    public App(String scbPath, String clientId, String clientSecret, DefaultConsistencyLevel consistencyLevel,
            int numOfRows) {
        super();
        session = AppUtil.getCQLSession(scbPath, clientId, clientSecret);
        dcName = AppUtil.getDCName(session);
        AppUtil.createLatencyTableIfNotExists(session, dcName);
        stmtInsertRecord = session.prepare(QueryBuilder.insertInto(AppUtil.LATENCY_TABLE)
                .value("event_identifier", QueryBuilder.bindMarker())
                .value("event_state", QueryBuilder.bindMarker())
                .value("event_name", QueryBuilder.bindMarker())
                .value("event_payload_url", QueryBuilder.bindMarker())
                .value("event_timestamp", QueryBuilder.bindMarker())
                .value("id", QueryBuilder.bindMarker())
                .build());
        findRecords = session.prepare(select.build());
        DriverExecutionProfile defaultProfile = session.getContext().getConfig().getDefaultProfile();
        dynamicProfile = defaultProfile.withString(DefaultDriverOption.REQUEST_CONSISTENCY, consistencyLevel.name());
        this.numOfRows = numOfRows;
    }

    public static void main(String[] args) throws InterruptedException {
        if (args.length < 5) {
            LOGGER.error(
                    "Not all input args received. Please provide these four args: SCB-Origin-Path, SCB-Target-Path, Client-Id, Client-Secret, Consistency-Level");
            System.exit(-1);
        }
        int numOfRows = 1000; // Default
        if (args.length == 6) {
            numOfRows = Integer.parseInt(args[5]);
        }
		DefaultConsistencyLevel consistencyLevel = DefaultConsistencyLevel.valueOf(args[4]);
		performLatencyCheck(args[0], args[1], args[2], args[3], consistencyLevel, numOfRows);
        System.exit(0); // Ensure the application exits after runs
    }

    public static void performLatencyCheck(String scbPathOrigin, String scbPathTarget, String clientId,
            String clientSecret, DefaultConsistencyLevel consistencyLevel, int numOfRows) throws InterruptedException {
        LOGGER.info("=============== PERFORMING MULTI-REGION LATENCY CHECK WITH CONSISTENCY-LEVEL: {} ===============",
                consistencyLevel);
        App originApp = new App(scbPathOrigin, clientId, clientSecret, consistencyLevel, numOfRows);
        Map<Integer, Long> originRecordTimestamp = new HashMap<>();

        App targetApp = new App(scbPathTarget, clientId, clientSecret, consistencyLevel, numOfRows);
        Map<Integer, Long> targetRecordTimestamp = new HashMap<>();

        long testStartTime = Calendar.getInstance().getTimeInMillis();
        LOGGER.info("Test Started at: {}", testStartTime);
        originApp.writeRecordsAsync();

        originApp.readRecordsAsync(originRecordTimestamp, 1, testStartTime);
        targetApp.readRecordsAsync(targetRecordTimestamp, 1, testStartTime);

        waitForCompletion(numOfRows, targetRecordTimestamp);
        long testEndTime = Calendar.getInstance().getTimeInMillis();

        final Long totLatency = computeLatency(originApp, originRecordTimestamp, targetApp, targetRecordTimestamp);
        LOGGER.info("===== {} row test at ConsistencyLevel {} took {}ms. Overall Latency added for all the rows {}ms. Avg. latency between regions was {}ms =====",
                numOfRows, consistencyLevel, (testEndTime - testStartTime),totLatency, (totLatency / numOfRows));

        AppUtil.closeSession(originApp.session, originApp.dcName);
        AppUtil.closeSession(targetApp.session, targetApp.dcName);
    }

    private static Long computeLatency(App originApp, Map<Integer, Long> originRecordTimestamp, App targetApp,
            Map<Integer, Long> targetRecordTimestamp) {
        final Long totLatency = originRecordTimestamp.entrySet().stream().map(es -> {
            Integer key = es.getKey();
            Long val = es.getValue();
            Long targetVal = targetRecordTimestamp.get(key);
            if (targetVal == null) {
                LOGGER.warn("No matching record found in target for key: {}", key);
                return 0L;
            }
            long observedLatency = targetVal - val;
            LOGGER.info("Found key {}: in regions {}:{} at timestamps {}:{} with observed latency: {}", key,
                    originApp.dcName, targetApp.dcName, val, targetVal, observedLatency);
            return observedLatency;
        }).reduce(0L, Long::sum);
        LOGGER.debug("Total latency calculated: {}", totLatency);
        return totLatency;
    }

    private static void waitForCompletion(int numOfRows, Map<Integer, Long> targetRecordTimestamp)
            throws InterruptedException {
        while (targetRecordTimestamp.keySet().size() != numOfRows) {
            LOGGER.debug("Current size of targetRecordTimestamp: {}, Expected numOfRows: {}", targetRecordTimestamp.keySet().size(), numOfRows);
            TimeUnit.MILLISECONDS.sleep(10);
            LOGGER.trace("Waiting for MR Latency check to complete...");
        }
        // TimeUnit.MILLISECONDS.sleep(100); // Buffer time as HashMap may not be fully
        // loaded resulting in NPE
    }

    private void writeRecordsAsync() {
        IntStream.range(0, numOfRows).forEach(idx -> {
            String eventIdentifier = "MR_LAT_TEST_" + eventIdentifierCounter.incrementAndGet();
            String eventState = "CREATED";
            String eventName = "com.sephora.happpening.reservation." + eventState.toLowerCase();
            String datePattern = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm'Z'").format(new Date());
            String eventPayloadUrl = "https://sepeus1lowerhasm01.blob.core.windows.net/auditing-cloud-event/" + eventName + "_" + datePattern;

            session.executeAsync(stmtInsertRecord.bind(
                    eventIdentifier,
                    eventState,
                    eventName,
                    eventPayloadUrl,
                    Instant.now(),
                    UUID.randomUUID()
            ).setExecutionProfile(dynamicProfile));
        });
    }

    private CompletionStage<AsyncResultSet> readRecordsAsync(Map<Integer, Long> recordTimestamp, int rowCount,
            long testStartTime) {
        LOGGER.debug("{}: Looking for rows...", dcName);
        String eventIdentifier = "MR_LAT_TEST_" + rowCount;
        String eventState = "CREATED";
        LOGGER.debug("Querying for eventIdentifier: {} and eventState: {}", eventIdentifier, eventState);

        CompletionStage<AsyncResultSet> respFuture = session
                .executeAsync(findRecords.bind(eventIdentifier, eventState)
                        .setExecutionProfile(dynamicProfile));
        return respFuture.whenCompleteAsync((nrs, err) -> {
            LOGGER.debug("whenCompleteAsync triggered for eventIdentifier: {} and eventState: {}", eventIdentifier, eventState);
            int newRowCount = processRows(nrs, err, rowCount, recordTimestamp);
            LOGGER.debug("New rowCount after processing: {}", newRowCount);
            if (newRowCount <= numOfRows) {
                readRecordsAsync(recordTimestamp, newRowCount, testStartTime);
            } else {
                LOGGER.info("{}: Test completed in: {}ms", dcName,
                        (Calendar.getInstance().getTimeInMillis() - testStartTime));
            }
        });
    }

    int processRows(AsyncResultSet rs, Throwable error, int rowCount, Map<Integer, Long> recordTimestamp) {
        if (error != null) {
            LOGGER.error("{}: Error while running query {} ", dcName, error.getLocalizedMessage());
        } else {
            LOGGER.debug("Processing result set for rowCount: {}", rowCount);
            for (Row row : rs.currentPage()) {
                String eventIdentifier = row.getString("event_identifier");
                String eventState = row.getString("event_state");
                Instant eventTimestamp = row.getInstant("event_timestamp");
                LOGGER.debug("Processing row with eventIdentifier: {}, eventState: {}, eventTimestamp: {}", eventIdentifier, eventState, eventTimestamp);
                int key = eventIdentifier.hashCode() ^ eventState.hashCode();
                if (recordTimestamp.get(key) == null) {
                    recordTimestamp.put(key, Long.valueOf(Calendar.getInstance().getTimeInMillis()));
					LOGGER.info("{}: Found {} rows!", dcName, rowCount);
                    rowCount++;
                    LOGGER.debug("Added record with key: {} to recordTimestamp. New rowCount: {}", key, rowCount);
                } else {
                    LOGGER.debug("Record with key: {} already exists in recordTimestamp.", key);
                }
            }
            if (rs.hasMorePages()) {
                LOGGER.info("{}: Getting next page...", dcName);
                int newRowCnt = rowCount;
                rs.fetchNextPage().whenComplete((nrs, err) -> processRows(nrs, err, newRowCnt, recordTimestamp));
            }
        }

        return rowCount;
    }

}