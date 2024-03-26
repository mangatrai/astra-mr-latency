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


	private static final String CLIENT_ID = "ntRECwopxDYoFIcRKcRMdETi";
	private static final String SECRET = "CLxr0zZAQ.cWTcKQCBw7,a0B5SeCKNfyUyNZwB-C_dNdCeKtrqjQWW9MSBnnLya2XhL4ds8tHIsP7ny,0UHmLPC9NRMA91BHNyM2C36w_ucd9XeyYuwI_83mT,eDNuFi";

	public static final String KEYSPACE_NAME = "test_ks";
	public static final String LATENCY_TABLE = "LATENCY_CHECK";
	
	
	public static CqlSession getCQLSession(String scbPath) {
		CqlSession cqlSession = CqlSession.builder().withCloudSecureConnectBundle(Paths.get(scbPath))
				.withAuthCredentials(CLIENT_ID, SECRET).withKeyspace(KEYSPACE_NAME).build();

		return cqlSession;
	}

	public static void closeSession(CqlSession session, String dcName) {
		if (session != null) {
			session.execute(QueryBuilder.truncate(LATENCY_TABLE).build());
			LOGGER.info("{}: Table '{}' has been truncated!", dcName, LATENCY_TABLE);
			session.close();
		}
		LOGGER.info("{}: Closed connection!", dcName);
	}

	public static String getDCName(CqlSession session) {
		PreparedStatement findDC = session.prepare(QueryBuilder.selectFrom("SYSTEM", "LOCAL").all().build());
		ResultSet rs = session.execute(findDC.bind());
		Row record = rs.one();

		return (null != record) ? record.getString("data_center") : "";
	}

	public static void createLatencyTableIfNotExists(CqlSession session, String dcName) {
		session.execute(SchemaBuilder.createTable(LATENCY_TABLE).ifNotExists().withPartitionKey("id", DataTypes.INT)
				.withClusteringColumn("key", DataTypes.INT).withColumn("value", DataTypes.TEXT)
				.withColumn("description", DataTypes.TEXT).build());
		LOGGER.info("{}: Table '{}' has been created (if not exists).", dcName, LATENCY_TABLE);
	}

}
