package mongo;

import com.mongodb.MongoException;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoDatabase;
import org.bson.BsonDocument;
import org.bson.BsonInt64;
import org.bson.conversions.Bson;

import java.util.logging.Level;
import java.util.logging.Logger;

public class Client {

	private static Client instance = null;
	private static MongoClient client;
	private static MongoDatabase database;

	private Client() {
		String connectionString;
		String user = Indexes.getInstance().getMongodbServerUser();
		String password = Indexes.getInstance().getMongodbServerPassword();
		String ip = Indexes.getInstance().getMongodbServerIp();
		int port = Integer.parseInt(Indexes.getInstance().getMongodbServerPort());
		String databaseName = Indexes.getInstance().getMongodbServerDatabase();

		Logger mongodbLogger = Logger.getLogger("org.mongodb.driver");
		mongodbLogger.setLevel(Level.WARNING);

		if (user == null || user.isEmpty() || user.equals("null")
				||password == null || password.isEmpty() || password.equals("null"))
			connectionString = "mongodb://" + ip + ":" + port;
		else connectionString = "mongodb://" + user + ":" + password + "@" + ip + ":" + port;
		client = MongoClients.create(connectionString);
		database = client.getDatabase(databaseName);

		try {
			Bson command = new BsonDocument("ping", new BsonInt64(1));
			database.runCommand(command);
			System.out.println("Successfully connected to MongoDB");
		} catch (MongoException e) {
			e.printStackTrace();
			System.err.println("Error connecting to to MongoDB");
			System.exit(1);
		}
	}
	
	public static synchronized MongoClient getClient() {
		if (instance == null) instance = new Client();
		return client;
	}

	public static MongoDatabase getDatabase() {
		MongoClient client = getClient();
		return database;
	}
	
}
