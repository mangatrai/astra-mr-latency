package com.bhatman.astra;

import java.nio.file.Paths;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.cql.PreparedStatement;
import com.datastax.oss.driver.api.core.cql.ResultSet;
import com.datastax.oss.driver.api.core.cql.Row;
import com.datastax.oss.driver.api.core.type.DataTypes;
import com.datastax.oss.driver.api.querybuilder.QueryBuilder;
import com.datastax.oss.driver.api.querybuilder.SchemaBuilder;

public class AppUtil {
	private static Logger LOGGER = LoggerFactory.getLogger(AppUtil.class);

	public static final String KEYSPACE_NAME = "audit";
	public static final String LATENCY_TABLE = "auditing_events";

	public static CqlSession getCQLSession(String scbPath, String clientId, String clientSecret) {
		CqlSession cqlSession = CqlSession.builder().withCloudSecureConnectBundle(Paths.get(scbPath))
				.withAuthCredentials(clientId, clientSecret).withKeyspace(KEYSPACE_NAME).build();

		return cqlSession;
	}

	public static void closeSession(CqlSession session, String dcName) {
		if (session != null) {
			session.close();
		}
		LOGGER.debug("{}: Closed connection!", dcName);
	}

	public static String getDCName(CqlSession session) {
		PreparedStatement findDC = session.prepare(QueryBuilder.selectFrom("SYSTEM", "LOCAL").all().build());
		ResultSet rs = session.execute(findDC.bind());
		Row record = rs.one();

		String dcName = (null != record) ? record.getString("data_center") : "";
		LOGGER.debug("{}: Connected!", dcName);
		return dcName;
	}

	public static void createLatencyTableIfNotExists(CqlSession session, String dcName) {
		session.execute(SchemaBuilder.createTable(LATENCY_TABLE).ifNotExists()
				.withPartitionKey("event_identifier", DataTypes.TEXT)
				.withClusteringColumn("event_state", DataTypes.TEXT)
				.withColumn("event_name", DataTypes.TEXT)
				.withColumn("event_payload_url", DataTypes.TEXT)
				.withColumn("event_timestamp", DataTypes.TIMESTAMP)
				.withColumn("id", DataTypes.UUID)
				.build());
		session.execute(QueryBuilder.truncate(LATENCY_TABLE).build());
		LOGGER.info("{}: Table '{}' has been created (if not exists) OR truncated (if exists).", dcName, LATENCY_TABLE);
	}

}
