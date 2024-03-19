package com.bhatman.astra;

import java.nio.file.Paths;
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
import com.datastax.oss.driver.api.core.cql.ResultSet;
import com.datastax.oss.driver.api.core.cql.Row;
import com.datastax.oss.driver.api.core.type.DataTypes;
import com.datastax.oss.driver.api.querybuilder.QueryBuilder;
import com.datastax.oss.driver.api.querybuilder.SchemaBuilder;

public class App {

	private static Logger LOGGER = LoggerFactory.getLogger(App.class);

	private static final String SCB_ORIGIN = "/path/to/scb/scb-origin.zip";
	private static final String SCB_TARGET = "/path/to/scb/scb-target.zip";
	private static final String CLIENT_ID = "xxx";
	private static final String SECRET = "yyy";

	private static final String KEYSPACE_NAME = "test_ks";
	private static final String LATENCY_TABLE = "LATENCY_CHECK";
	private static final int NUM_OF_ROWS = 50;
	private static PreparedStatement stmtInsertRecord;

	private CqlSession session;
	public String dcName;
	private PreparedStatement findDC;
	private PreparedStatement findRecords;

	public static void main(String[] args) {

		App originApp = new App(SCB_ORIGIN);
		App targetApp = new App(SCB_TARGET);

		originApp.writeRecordsAsync(NUM_OF_ROWS);

		Map<Integer, Long> originRecordTimestamp = new HashMap<>();
		Map<Integer, Long> targetRecordTimestamp = new HashMap<>();

		CompletionStage<AsyncResultSet> targetRespFuture = targetApp.readRecordsAsync(NUM_OF_ROWS,
				targetRecordTimestamp, 0);
		originApp.readRecordsAsync(NUM_OF_ROWS, originRecordTimestamp, 0);

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
				LOGGER.info("Found key {}: with {}:{} timestamps {}:{} and Observed latency: {}", k, originApp.dcName,
						targetApp.dcName, v, targetVal, observedLatency);
				return observedLatency;
			}).reduce(0l, Long::sum);

			LOGGER.info("Total latency between regions: {}", totLatency);
			LOGGER.info("Average latency between regions: {}", (totLatency / NUM_OF_ROWS));

			originApp.closeSession();
			targetApp.closeSession();
		});
	}

	public App(String scbPath) {
		super();
		session = getCQLSession(scbPath);
		findDC = session.prepare(QueryBuilder.selectFrom("SYSTEM", "LOCAL").all().build());
		dcName = getDCName();
		LOGGER.info("{}: Connected!", dcName);
		createLatencyTableIfNotExists(session);
		findRecords = session.prepare(QueryBuilder.selectFrom(LATENCY_TABLE).all().build());
	}

	private CompletionStage<AsyncResultSet> readRecordsAsync(int count, Map<Integer, Long> recordTimestamp,
			int rowCount) {
		LOGGER.info("{}: Looking for rows...", dcName);
		CompletionStage<AsyncResultSet> respFuture = session.executeAsync(findRecords.bind());
		return respFuture.whenComplete((nrs, err) -> {
			int newRowCount = processRows(nrs, err, rowCount, recordTimestamp);
			if (newRowCount < count) {
				readRecordsAsync(count, recordTimestamp, newRowCount);
			}
		});
	}

	int processRows(AsyncResultSet rs, Throwable error, int rowCount, Map<Integer, Long> recordTimestamp) {
		if (error != null) {
			LOGGER.error("{}: Error while running query {} ", dcName, error.getLocalizedMessage());
		} else {
			for (Row row : rs.currentPage()) {
				if (recordTimestamp.get(row.getInt("id")) == null) {
					recordTimestamp.put(row.getInt("id"), Long.valueOf(Calendar.getInstance().getTimeInMillis()));
					rowCount++;
				}
			}
//			if (rs.hasMorePages()) {
//				LOGGER.info("{}: Getting next page...", dcName);
//				rs.fetchNextPage().whenComplete((nrs, err) -> processRows(nrs, err, rowCount, recordTimestamp));
//			} else {
			LOGGER.info("{}: Found {} rows!", dcName, rowCount);
//			}
		}

		return rowCount;
	}

	private void writeRecordsAsync(int i) {
		if (null == stmtInsertRecord) {
			stmtInsertRecord = session.prepare(QueryBuilder.insertInto(LATENCY_TABLE)
					.value("id", QueryBuilder.bindMarker()).value("key", QueryBuilder.bindMarker())
					.value("value", QueryBuilder.bindMarker()).value("description", QueryBuilder.bindMarker()).build());
		}

		DriverExecutionProfile defaultProfile = session.getContext().getConfig().getDefaultProfile();
		DriverExecutionProfile dynamicProfile = defaultProfile.withString(DefaultDriverOption.REQUEST_CONSISTENCY,
				// DefaultConsistencyLevel.EACH_QUORUM.name());
				DefaultConsistencyLevel.LOCAL_QUORUM.name());
		IntStream.range(0, i).forEach(idx -> {
			session.executeAsync(
					stmtInsertRecord.bind(idx, LATENCY_TABLE, "Row: " + idx, "This is a multi-region latency check!")
							.setExecutionProfile(dynamicProfile));
		});
	}

	public CqlSession getCQLSession(String scbPath) {
		CqlSession cqlSession = CqlSession.builder().withCloudSecureConnectBundle(Paths.get(scbPath))
				.withAuthCredentials(CLIENT_ID, SECRET).withKeyspace(KEYSPACE_NAME).build();

		return cqlSession;
	}

	public void closeSession() {
		if (session != null) {
			session.execute(QueryBuilder.truncate(LATENCY_TABLE).build());
			LOGGER.info("{}: Table '{}' has been truncated!", dcName, LATENCY_TABLE);
			session.close();
		}
		LOGGER.info("{}: Closed connection!", dcName);
	}

	public String getDCName() {
		ResultSet rs = session.execute(findDC.bind());
		Row record = rs.one();

		return (null != record) ? record.getString("data_center") : "";
	}

	public void createLatencyTableIfNotExists(CqlSession session) {
		session.execute(SchemaBuilder.createTable(LATENCY_TABLE).ifNotExists().withPartitionKey("id", DataTypes.INT)
				.withColumn("key", DataTypes.TEXT).withColumn("value", DataTypes.TEXT)
				.withColumn("description", DataTypes.TEXT).build());
		LOGGER.info("{}: Table '{}' has been created (if not exists).", dcName, LATENCY_TABLE);
	}
}
