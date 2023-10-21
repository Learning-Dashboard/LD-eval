package eval2;

import java.util.ArrayList;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.CreateCollectionOptions;
import com.mongodb.client.model.UpdateOptions;
import com.mongodb.client.model.ValidationOptions;
import com.mongodb.client.result.UpdateResult;
import org.bson.Document;

public class CollectionManager {

	private final MongoDatabase mongoDatabase;
	
	/**
	 * Create a IndexManager for a MongoDB instance
	 * @param mongoDB an instance of MongoDB
	 */
	public CollectionManager(MongoDB mongoDB) {
		this.mongoDatabase = mongoDB.getDatabase();
	}

	/**
	 * Create a Collection in a MongoDB database
	 * @param collectionName name of the MongoDB collection
	 * @param schema document containing the schema of the collection
	 */
	public void createIndex(String collectionName, Document schema) {
		if (mongoDatabase.listCollectionNames().into(new ArrayList<>()).contains(collectionName)) {
			System.out.println("Index already exists: " + collectionName);
		}
		else {
			ValidationOptions validationOptions = new ValidationOptions().validator(schema);
			CreateCollectionOptions options = new CreateCollectionOptions().validationOptions(validationOptions);
			mongoDatabase.createCollection(collectionName, options);
			for (String name : mongoDatabase.listCollectionNames())
				if (name.equals(collectionName)) System.out.println("Index created: " + collectionName);
		}
	}

	/**
	 * Write an object (Document) to a MongoDB collection.
	 * @param collectionName name of the MongoDB collection
	 * @param document object to be written
	 * @return an UpdateResult indicating if the operation was performed correctly
	 */
	public UpdateResult writeDocument(String collectionName, Document document) {
		MongoCollection<Document> collection = mongoDatabase.getCollection(collectionName);
		String id = document.getString("_id");
		UpdateOptions options = new UpdateOptions().upsert(true);
		Document query = new Document("_id", id);
		return collection.updateOne(query, new Document("$set", document), options);
	}

}

