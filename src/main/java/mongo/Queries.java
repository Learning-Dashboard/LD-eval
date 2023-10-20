package mongo;

import com.mongodb.client.AggregateIterable;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import org.bson.Document;

import java.util.Arrays;

public class Queries {
	
	public static String getLatestDateValue(String collectionName, String dateField, String selectionField, String selectionVal) {
		MongoDatabase database = Client.getDatabase();
		MongoCollection<Document> collection = database.getCollection(collectionName);

		Document matchStage = new Document("$match", new Document(selectionField, selectionVal));
		Document groupStage = new Document("$group", new Document("_id", null)
			.append("maxDate", new Document("$max", "$" + dateField)));

		AggregateIterable<Document> result = collection.aggregate(Arrays.asList(matchStage, groupStage));
		for (Document document : result) return document.getString("maxDate");
		return null;
	}
	
	public static void main(String[] args) {
		String repo = "PES-Agenda-Cultural/CultureFinder-Backend";
		String s = getLatestDateValue("github_pes_pes11a.commits","date", "repository", repo);
		System.out.println(s);
	}

}
