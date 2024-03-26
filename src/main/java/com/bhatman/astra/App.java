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

	private static final String SCB_ORIGIN = "/Users/pravin.bhat/code/github/pravinbhat/learn/hello-dsbulk/secure-connect-petclinic.zip";
	private static final String SCB_TARGET = "/Users/pravin.bhat/code/github/pravinbhat/learn/hello-dsbulk/secure-connect-petclinic-us-west-2.zip";

	private static final int NUM_OF_ROWS = 1000;
	private static PreparedStatement stmtInsertRecord;

	private CqlSession session;
	public String dcName;
	private PreparedStatement findRecords;

	public App(String scbPath) {
		super();
		session = AppUtil.getCQLSession(scbPath);
		dcName = AppUtil.getDCName(session);
		LOGGER.info("{}: Connected!", dcName);
		AppUtil.createLatencyTableIfNotExists(session, dcName);
		Select select = QueryBuilder.selectFrom(AppUtil.LATENCY_TABLE).column("key").whereColumn("id").isEqualTo(QueryBuilder.bindMarker());
		findRecords = session.prepare(select.build());
	}

	public static void main(String[] args) {
		performLatencyCheck(DefaultConsistencyLevel.EACH_QUORUM);
		
		try {
			TimeUnit.SECONDS.sleep(1);
		} catch (InterruptedException ie) {
			Thread.currentThread().interrupt();
		}
		
		performLatencyCheck(DefaultConsistencyLevel.LOCAL_QUORUM);
	}

	public static void performLatencyCheck(DefaultConsistencyLevel consistencyLevel) {
		LOGGER.info(
				"====================== PERFORMING MULTI-REGION LATENCY CHEACK WITH CONSISTENCY-LEVEL: {} ======================",
				consistencyLevel);
		App originApp = new App(SCB_ORIGIN);
		App targetApp = new App(SCB_TARGET);

		long testStartTime = Calendar.getInstance().getTimeInMillis();
		LOGGER.info("Test Started at: {}", testStartTime);
		originApp.writeRecordsAsync(NUM_OF_ROWS, consistencyLevel);

		Map<Integer, Long> originRecordTimestamp = new HashMap<>();
		Map<Integer, Long> targetRecordTimestamp = new HashMap<>();

		originApp.readRecordsAsync(NUM_OF_ROWS, originRecordTimestamp, 0, testStartTime);
		CompletionStage<AsyncResultSet> targetRespFuture = targetApp.readRecordsAsync(NUM_OF_ROWS,
				targetRecordTimestamp, 0, testStartTime);

		try {
			TimeUnit.SECONDS.sleep(1);
		} catch (InterruptedException ie) {
			Thread.currentThread().interrupt();
		}

		targetRespFuture.whenComplete((nrs, err) -> {
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
		});
	}

	private void writeRecordsAsync(int i, DefaultConsistencyLevel consistencyLevel) {
		if (null == stmtInsertRecord) {
			stmtInsertRecord = session.prepare(QueryBuilder.insertInto(AppUtil.LATENCY_TABLE)
					.value("id", QueryBuilder.bindMarker()).value("key", QueryBuilder.bindMarker())
					.value("value", QueryBuilder.bindMarker()).value("description", QueryBuilder.bindMarker()).build());
		}

		DriverExecutionProfile defaultProfile = session.getContext().getConfig().getDefaultProfile();
		DriverExecutionProfile dynamicProfile = defaultProfile.withString(DefaultDriverOption.REQUEST_CONSISTENCY,
				consistencyLevel.name());
		IntStream.range(0, i).forEach(idx -> {
			session.executeAsync(stmtInsertRecord
					.bind(1, idx, "Row: " + idx, "This is a multi-region latency check!")
					.setExecutionProfile(dynamicProfile));
		});
	}

	private CompletionStage<AsyncResultSet> readRecordsAsync(int count, Map<Integer, Long> recordTimestamp,
			int rowCount, long testStartTime) {
		LOGGER.info("{}: Looking for rows...", dcName);
		CompletionStage<AsyncResultSet> respFuture = session.executeAsync(findRecords.bind(1));
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