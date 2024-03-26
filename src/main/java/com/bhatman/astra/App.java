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

	private static final String SCB_ORIGIN = "/path/to/scb/scb-origin.zip";
	private static final String SCB_TARGET = "/path/to/scb/scb-target.zip";

	private static final int NUM_OF_ROWS = 1000;
	private static PreparedStatement stmtInsertRecord;
	private static Select select = QueryBuilder.selectFrom(AppUtil.LATENCY_TABLE).column("key").whereColumn("id")
			.isEqualTo(QueryBuilder.bindMarker());

	private CqlSession session;
	public String dcName;
	private PreparedStatement findRecords;
	DriverExecutionProfile dynamicProfile;

	public App(String scbPath, DefaultConsistencyLevel consistencyLevel) {
		super();
		session = AppUtil.getCQLSession(scbPath);
		dcName = AppUtil.getDCName(session);
		LOGGER.info("{}: Connected!", dcName);
		AppUtil.createLatencyTableIfNotExists(session, dcName);
		stmtInsertRecord = session.prepare(QueryBuilder.insertInto(AppUtil.LATENCY_TABLE)
				.value("id", QueryBuilder.bindMarker()).value("key", QueryBuilder.bindMarker())
				.value("value", QueryBuilder.bindMarker()).value("description", QueryBuilder.bindMarker()).build());
		findRecords = session.prepare(select.build());
		DriverExecutionProfile defaultProfile = session.getContext().getConfig().getDefaultProfile();
		dynamicProfile = defaultProfile.withString(DefaultDriverOption.REQUEST_CONSISTENCY, consistencyLevel.name());
	}

	public static void main(String[] args) throws InterruptedException {
		performLatencyCheck(DefaultConsistencyLevel.EACH_QUORUM);
		performLatencyCheck(DefaultConsistencyLevel.LOCAL_QUORUM);
	}

	public static void performLatencyCheck(DefaultConsistencyLevel consistencyLevel) throws InterruptedException {
		LOGGER.info(
				"====================== PERFORMING MULTI-REGION LATENCY CHEACK WITH CONSISTENCY-LEVEL: {} ======================",
				consistencyLevel);
		App originApp = new App(SCB_ORIGIN, consistencyLevel);
		App targetApp = new App(SCB_TARGET, consistencyLevel);

		long testStartTime = Calendar.getInstance().getTimeInMillis();
		LOGGER.info("Test Started at: {}", testStartTime);
		originApp.writeRecordsAsync(NUM_OF_ROWS);

		Map<Integer, Long> originRecordTimestamp = new HashMap<>();
		Map<Integer, Long> targetRecordTimestamp = new HashMap<>();

		originApp.readRecordsAsync(NUM_OF_ROWS, originRecordTimestamp, 0, testStartTime);
		targetApp.readRecordsAsync(NUM_OF_ROWS, targetRecordTimestamp, 0, testStartTime);

		while (!targetRecordTimestamp.containsKey(NUM_OF_ROWS-1)) {
			TimeUnit.MILLISECONDS.sleep(100);
			LOGGER.info("Test waiting to complete...");
		}

		final Long totLatency = originRecordTimestamp.entrySet().stream().map(es -> {
			Integer k = es.getKey();
			Long v = es.getValue();
			long targetVal = targetRecordTimestamp.get(k);
			long observedLatency = targetVal - v;
			LOGGER.trace("Found key {}: with {}:{} timestamps {}:{} and Observed latency: {}", k, originApp.dcName,
					targetApp.dcName, v, targetVal, observedLatency);
			return observedLatency;
		}).reduce(0l, Long::sum);
		AppUtil.closeSession(originApp.session, originApp.dcName);
		AppUtil.closeSession(targetApp.session, targetApp.dcName);

		LOGGER.info(
				"=================== Total Latency: {}, AVG Latency: {}, Rowcount: {}, ConsistencyLevel: {} =========================== ",
				totLatency, (totLatency / NUM_OF_ROWS), NUM_OF_ROWS, consistencyLevel);
	}

	private void writeRecordsAsync(int i) {
		IntStream.range(0, i).forEach(idx -> {
			session.executeAsync(stmtInsertRecord.bind(1, idx, "Row: " + idx, "This is a multi-region latency check!")
					.setExecutionProfile(dynamicProfile));
		});
	}

	private CompletionStage<AsyncResultSet> readRecordsAsync(int count, Map<Integer, Long> recordTimestamp,
			int rowCount, long testStartTime) {
		LOGGER.info("{}: Looking for rows...", dcName);
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