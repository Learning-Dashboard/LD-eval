package mongo;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Properties;

public class Indexes {
	
	// Singleton
	private static final Indexes instance = new Indexes();

	// Targets
	private String metricsIndex;
	private String metricsIndexType;
	private String factorsIndex;
	private String factorsIndexType;
	private String indicatorsIndex;
	private String indicatorsIndexType;

	// Database connection
	private String mongodbServerIp;
	private String mongodbServerPort;
	private String mongodbServerUser;
	private String mongodbServerPassword;
	private String mongodbServerDatabase;

	InputStream input = null;

	private Indexes()  {
		Properties indexProps = new Properties();
		try {
			String indexPropertiesFile = "index.properties";
			input = Files.newInputStream(Paths.get(indexPropertiesFile));
			indexProps.load(input);

			mongodbServerIp = indexProps.getProperty("mongodb.server.ip");
			mongodbServerPort = indexProps.getProperty("mongodb.server.port");
			mongodbServerUser = indexProps.getProperty("mongodb.server.user");
			mongodbServerPassword = indexProps.getProperty("mongodb.server.password");
			mongodbServerDatabase = indexProps.getProperty("mongodb.server.database");

			metricsIndex = indexProps.getProperty("metrics.index");
			factorsIndex = indexProps.getProperty("factors.index");
			indicatorsIndex = indexProps.getProperty("indicators.index");
			
			metricsIndexType = indexProps.getProperty("metrics.index.type");
			factorsIndexType = indexProps.getProperty("factors.index.type");
			indicatorsIndexType = indexProps.getProperty("indicators.index.type");
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	public static Indexes getInstance() {
		return instance;
	}

	public String getMongodbServerIp() {
		return mongodbServerIp;
	}

	public String getMongodbServerPort() {
		return mongodbServerPort;
	}

	public String getMongodbServerUser() {
		return mongodbServerUser;
	}

	public String getMongodbServerPassword() {
		return mongodbServerPassword;
	}

	public String getMongodbServerDatabase() {
		return mongodbServerDatabase;
	}

	public String getMetricsIndex() {
		return metricsIndex;
	}

	public void setMetricsIndex(String metricsIndex) {
		this.metricsIndex = metricsIndex;
	}

	public String getFactorsIndex() {
		return factorsIndex;
	}

	public String getIndicatorsIndex() {
		return indicatorsIndex;
	}

	public String getMetricsIndexType() {
		return metricsIndexType;
	}

	public String getFactorsIndexType() {
		return factorsIndexType;
	}

	public String getIndicatorsIndexType() {
		return indicatorsIndexType;
	}

	public static void main(String[] args) {
		Indexes i = Indexes.getInstance();
		System.out.println(i.getFactorsIndex());
		System.out.println(i.getFactorsIndexType());

		System.out.println(i.getIndicatorsIndex());
		System.out.println(i.getIndicatorsIndexType());

		System.out.println(i.getMetricsIndex());
		System.out.println(i.getMetricsIndexType());
		System.out.println();
	}

}
