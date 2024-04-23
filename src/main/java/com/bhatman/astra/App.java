package com.bhatman.astra;

import java.util.Calendar;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

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

	private static final int NUM_OF_ROWS = 1000;
	private static PreparedStatement stmtInsertRecord;
	private static Select select = QueryBuilder.selectFrom(AppUtil.LATENCY_TABLE).column("key").whereColumn("id")
			.isEqualTo(QueryBuilder.bindMarker());

	private CqlSession session;
	public String dcName;
	private PreparedStatement findRecords;
	DriverExecutionProfile dynamicProfile;

	public App(String scbPath, String clientId, String clientSecret, DefaultConsistencyLevel consistencyLevel) {
		super();
		session = AppUtil.getCQLSession(scbPath, clientId, clientSecret);
		dcName = AppUtil.getDCName(session);
		AppUtil.createLatencyTableIfNotExists(session, dcName);
		stmtInsertRecord = session.prepare(QueryBuilder.insertInto(AppUtil.LATENCY_TABLE)
				.value("id", QueryBuilder.bindMarker()).value("key", QueryBuilder.bindMarker())
				.value("value", QueryBuilder.bindMarker()).value("description", QueryBuilder.bindMarker()).build());
		findRecords = session.prepare(select.build());
		DriverExecutionProfile defaultProfile = session.getContext().getConfig().getDefaultProfile();
		dynamicProfile = defaultProfile.withString(DefaultDriverOption.REQUEST_CONSISTENCY, consistencyLevel.name());
	}

	public static void main(String[] args) throws InterruptedException {
		if (args.length < 4) {
			LOGGER.error("Not all input args received. Please provide these four args: SCB-Origin-Path, SCB-Target-Path, Client-Id, Client-Secret");
			System.exit(-1);
		}
		performLatencyCheck(args[0], args[1], args[2], args[3], DefaultConsistencyLevel.EACH_QUORUM);
		performLatencyCheck(args[0], args[1], args[2], args[3], DefaultConsistencyLevel.LOCAL_QUORUM);
	}

	public static void performLatencyCheck(String scbPathOrigin, String scbPathTarget, String clientId,
			String clientSecret, DefaultConsistencyLevel consistencyLevel) throws InterruptedException {
		LOGGER.info(
				"=============== PERFORMING MULTI-REGION LATENCY CHEACK WITH CONSISTENCY-LEVEL: {} ===============",
				consistencyLevel);
		App originApp = new App(scbPathOrigin, clientId, clientSecret, consistencyLevel);
		Map<Integer, Long> originRecordTimestamp = new HashMap<>();

		App targetApp = new App(scbPathTarget, clientId, clientSecret, consistencyLevel);
		Map<Integer, Long> targetRecordTimestamp = new HashMap<>();

		long testStartTime = Calendar.getInstance().getTimeInMillis();
		LOGGER.info("Test Started at: {}", testStartTime);
		originApp.writeRecordsAsync(NUM_OF_ROWS);

		originApp.readRecordsAsync(NUM_OF_ROWS, originRecordTimestamp, 0, testStartTime);
		targetApp.readRecordsAsync(NUM_OF_ROWS, targetRecordTimestamp, 0, testStartTime);

		waitForCompletion(targetRecordTimestamp);
		long testEndTime = Calendar.getInstance().getTimeInMillis();

		final Long totLatency = computeLatency(originApp, originRecordTimestamp, targetApp, targetRecordTimestamp);
		LOGGER.info(
				"===== {} row test at ConsistencyLevel {} took {}ms. Avg. latency between regions was {}ms =====",
				NUM_OF_ROWS, consistencyLevel, (testEndTime - testStartTime), (totLatency / NUM_OF_ROWS));

		AppUtil.closeSession(originApp.session, originApp.dcName);
		AppUtil.closeSession(targetApp.session, targetApp.dcName);
	}

	private static Long computeLatency(App originApp, Map<Integer, Long> originRecordTimestamp, App targetApp,
			Map<Integer, Long> targetRecordTimestamp) {
		final Long totLatency = originRecordTimestamp.entrySet().stream().map(es -> {
			Integer key = es.getKey();
			Long val = es.getValue();
			long targetVal = targetRecordTimestamp.get(key);
			long observedLatency = targetVal - val;
			LOGGER.trace("Found key {}: in regions {}:{} at timestamps {}:{} with observed latency: {}", key,
					originApp.dcName, targetApp.dcName, val, targetVal, observedLatency);
			return observedLatency;
		}).reduce(0l, Long::sum);
		return totLatency;
	}

	private static void waitForCompletion(Map<Integer, Long> targetRecordTimestamp) throws InterruptedException {
		while (targetRecordTimestamp.keySet().size() != NUM_OF_ROWS) {
			TimeUnit.MILLISECONDS.sleep(10);
			LOGGER.trace("Waiting for MR Latency check to complete...");
		}
		//TimeUnit.MILLISECONDS.sleep(100); // Buffer time as HashMap may not be fully loaded resulting in NPE
	}

	private void writeRecordsAsync(int i) {
		IntStream.range(0, i).forEach(idx -> {
			session.executeAsync(stmtInsertRecord.bind(1, idx, "Row: " + idx, "This is a multi-region latency check!")
					.setExecutionProfile(dynamicProfile));
		});
	}

	private CompletionStage<AsyncResultSet> readRecordsAsync(int count, Map<Integer, Long> recordTimestamp,
			int rowCount, long testStartTime) {
		LOGGER.debug("{}: Looking for rows...", dcName);
		CompletionStage<AsyncResultSet> respFuture = session
				.executeAsync(findRecords.bind(1).setExecutionProfile(dynamicProfile));
		return respFuture.whenCompleteAsync((nrs, err) -> {
			int newRowCount = processRows(nrs, err, rowCount, recordTimestamp);
			if (newRowCount < count) {
				readRecordsAsync(count, recordTimestamp, newRowCount, testStartTime);
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
			for (Row row : rs.currentPage()) {
				if (recordTimestamp.get(row.getInt("key")) == null) {
					recordTimestamp.put(row.getInt("key"), Long.valueOf(Calendar.getInstance().getTimeInMillis()));
					rowCount++;
				}
			}
			if (rs.hasMorePages()) {
				LOGGER.info("{}: Getting next page...", dcName);
				int newRowCnt = rowCount;
				rs.fetchNextPage().whenComplete((nrs, err) -> processRows(nrs, err, newRowCnt, recordTimestamp));
			} else {
				LOGGER.info("{}: Found {} rows!", dcName, rowCount);
			}
		}

		return rowCount;
	}

}