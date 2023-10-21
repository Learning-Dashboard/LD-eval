package eval2;

import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.mongodb.bulk.BulkWriteResult;
import com.mongodb.client.MongoCollection;
import com.mongodb.MongoException;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.BulkWriteOptions;
import com.mongodb.client.model.UpdateOneModel;
import com.mongodb.client.model.UpdateOptions;
import com.mongodb.client.model.WriteModel;
import com.mongodb.client.result.DeleteResult;

import org.bson.BsonDocument;
import org.bson.BsonInt64;
import org.bson.Document;
import org.bson.conversions.Bson;

import type.*;
import util.Schemas;

public class MongoDB {
    private final Logger log = Logger.getLogger(this.getClass().getName());

    private final MongoClient client;
    private final MongoDatabase database;

    private final String mongodbIP;
    private final int mongodbPort;
    private final String mongodbUser;
    private final String mongodbPassword;
    private final String mongodbDatabase;

    private static final Map<String, MongoClient> clientCache = new HashMap<>();
    private static final Map<String, MongoDatabase> databaseCache = new HashMap<>();


    /**
     * Create on address of a MongoDB server
     * @param mongodbUser username of the MongoDB server [OPTIONAL]
     * @param mongodbPassword password of the MongoDB server [OPTIONAL]
     * @param mongodbIP ip address of the MongoDB server [MANDATORY]
     * @param mongodbPort port where the MongoDB server is listening [MANDATORY]
     * @param mongodbDatabase database where we are going to execute the queries [MANDATORY]
     */
    public MongoDB(String mongodbUser, String mongodbPassword, String mongodbIP, int mongodbPort, String mongodbDatabase) {

        this.mongodbUser = mongodbUser;
        this.mongodbPassword = mongodbPassword;
        this.mongodbIP = mongodbIP;
        this.mongodbPort = mongodbPort;
        this.mongodbDatabase = mongodbDatabase;

        if (clientCache.containsKey(mongodbIP) && databaseCache.containsKey(mongodbDatabase)) {
            log.info("Using cached MongoClient and MongoDatabase.\n");
            client = clientCache.get(mongodbIP);
            database = databaseCache.get(mongodbDatabase);
            return;
        }

        Logger mongodbLogger = Logger.getLogger("org.mongodb.driver");
        mongodbLogger.setLevel(Level.WARNING);

        String connectionString;
        if (mongodbUser == null || mongodbUser.isEmpty()
                || mongodbPassword == null || mongodbPassword.isEmpty()) {
            connectionString = "mongodb://" + mongodbIP + ":" + mongodbPort;
        }
        else {
            connectionString = "mongodb://" + mongodbUser + ":"
                + mongodbPassword + "@" + mongodbIP + ":" + mongodbPort;
        }
        client = MongoClients.create(connectionString);
        database = client.getDatabase(mongodbDatabase);

        try {
            Bson command = new BsonDocument("ping", new BsonInt64(1));
            database.runCommand(command);
            System.out.println("Successfully connected to MongoDB");
            clientCache.put(mongodbIP, client);
            databaseCache.put(mongodbDatabase, database);
        } catch (MongoException e) {
            e.printStackTrace();
            System.err.println("Error connecting to to MongoDB");
            System.exit(1);
        }

    }

    public MongoClient getClient() {
        return client;
    }

    public MongoDatabase getDatabase() {
        return database;
    }

    public String getMongodbIP() {
        return mongodbIP;
    }

    public int getMongodbPort() {
        return mongodbPort;
    }

    public String getMongodbUser() {
        return mongodbUser;
    }

    public String getMongodbPassword() {
        return mongodbPassword;
    }

    public String getMongodbDatabaseName() {
        return mongodbDatabase;
    }

    /**
     * Execute QueryDef with additional parameters
     * @param externalParameters additional parameters derived by i.e. param-queries
     * @param queryDef the queryDef to execute
     * @return a map containing the key-value (property-responseValue) pairs of the results
     */
    public Map<String,Object> execute( Map<String,Object> externalParameters, QueryDef queryDef ) {

        log.info("Executing QueryDef " + queryDef.getName() + "\n"
            + "Index: " + queryDef.getProperty("index") + "\n"
            + "External parameters: " + externalParameters + "\n"
            + "Query parameters: " + queryDef.getQueryParameter() + "\n");

        Map<String,Object> execParams = new HashMap<>();
        execParams.putAll(externalParameters);
        execParams.putAll(queryDef.getQueryParameter());

        List<Document> sr = search(queryDef.getQueryTemplate(), queryDef.getProperty("index"), execParams);
        Map<String,Object> executionResult = new HashMap<>();
        Map<String,String> queryResults = queryDef.getResults();

        if (sr == null) {
            log.warning("QueryDef " + queryDef.getName() + " failed.\n");
            return executionResult;
        }

        log.info("MongoDB response: " + sr);
        for ( Map.Entry<String,String> e : queryResults.entrySet() ) {
            if (sr.size() == 0) executionResult.put(e.getKey(), 0);
            else {
                Document doc = sr.get(0);
                Object o = doc.get(e.getValue());
                executionResult.put(e.getKey(), o);
            }
        }
        return executionResult;
    }

    /**
     * Perform a query in the MongoDB database
     * @param template the queryTemplate
     * @param index the index to run the query on
     * @param params parameters for the templateQuery
     * @return a list of Documents containing the query result
     */
    public List<Document> search(String template, String index, Map<String,Object> params) {
        String queryString = "{ \"pipeline\": " + template + "}";
        MongoCollection<Document> collection = database.getCollection(index);
        for (Map.Entry<String, Object> entry : params.entrySet())
            queryString = queryString.replace("{{" + entry.getKey() + "}}", String.valueOf(entry.getValue()));

        Document parsedQuery = Document.parse(queryString);
        List<Document> pipeline = parsedQuery.getList("pipeline", Document.class);
        return collection.aggregate(pipeline).into(new ArrayList<>());
    }

    public void storeMetrics(Properties projectProperties, String evaluationDate, Collection<Metric> metrics) {
        String projectName = projectProperties.getProperty("project.name");
        String metricIndex = projectProperties.getProperty("metrics.index") + "." + projectName;
        String relationsIndex = projectProperties.getProperty("relations.index") + "." + projectName;
        checkCreateIndex(metricIndex, Schemas.METRICS_SCHEMA);

        long deletedMetrics = deleteCurrentEvaluation(metricIndex, projectName, evaluationDate);
        long deletedRelations = deleteCurrentEvaluation(relationsIndex, projectName, evaluationDate);
        log.info("deleted " + deletedMetrics + " metrics (evaluationDate=" + evaluationDate + ").");
        log.info("deleted " + deletedRelations + " relations (evaluationDate=" + evaluationDate + ").\n");

        BulkWriteResult br = writeBulk(metricIndex, metrics);
        log.info(bulkResponseCheck(br));
    }

    public void storeRelations(Properties projectProperties, Collection<Relation> relations) {
        String projectName = projectProperties.getProperty("project.name");
        String relationsIndex = projectProperties.getProperty("relations.index") + "." + projectName;
        checkCreateIndex(relationsIndex, Schemas.RELATIONS_SCHEMA);

        BulkWriteResult br = writeBulk(relationsIndex, relations);
        log.info(bulkResponseCheck(br));
    }

    public void storeFactors(Properties projectProperties, String evaluationDate, Collection<Factor> factors ) {
        String projectName = projectProperties.getProperty("project.name");
        String factorsIndex = projectProperties.getProperty("factors.index") + "." + projectName;
        checkCreateIndex(factorsIndex, Schemas.FACTORS_SCHEMA);

        long deleted = deleteCurrentEvaluation(factorsIndex, projectName, evaluationDate);
        log.info("deleted " + deleted + " factors (evaluationDate=" + evaluationDate + ").\n");

        BulkWriteResult br = writeBulk(factorsIndex, factors);
        log.info(bulkResponseCheck(br));
    }

    public void storeIndicators(Properties projectProperties, String evaluationDate, Collection<Indicator> indicators) {
        String projectName = projectProperties.getProperty("project.name");
        String indicatorsIndex = projectProperties.getProperty("indicators.index") + "." + projectName;
        checkCreateIndex(indicatorsIndex, Schemas.STRATEGIC_INDICATORS_SCHEMA);

        long deleted = deleteCurrentEvaluation(indicatorsIndex, projectName, evaluationDate);
        log.info("deleted " + deleted + " indicators (evaluationDate=" + evaluationDate + ").\n");

        BulkWriteResult br = writeBulk(indicatorsIndex, indicators);
        log.info(bulkResponseCheck(br));
    }

    private void checkCreateIndex(String indexName, Document schemaPathname) {
        try {
            CollectionManager mgr = new CollectionManager(this);
            mgr.createIndex(indexName, schemaPathname);
        } catch (Exception e) {
            log.info(e.getMessage());
        }
    }

    private BulkWriteResult writeBulk(String collectionName, Collection<? extends IndexItem> items) {
        List<WriteModel<Document>> writeModels = new ArrayList<>();
        MongoCollection<Document> collection = database.getCollection(collectionName);
        for (IndexItem item : items) {
            Document filter = new Document("_id", item.getMongodbId());
            Document update = new Document("$set", item.getMap());
            WriteModel<Document> writeModel = new UpdateOneModel<>(filter, update, new UpdateOptions().upsert(true));
            writeModels.add(writeModel);
        }
        BulkWriteOptions bulkWriteOptions = new BulkWriteOptions().ordered(false);
        return collection.bulkWrite(writeModels, bulkWriteOptions);
    }

    private String bulkResponseCheck(BulkWriteResult result) {
        if (result == null) {
            log.warning("Result is null");
            return "";
        }

        int writtenItems = result.getUpserts().size() + result.getMatchedCount();
        if (result.wasAcknowledged()) return "BulkWrite success! " + writtenItems + " items written.";
        else {
            log.warning("BulkWrite operation was not acknowledged.");
            return "";
        }
    }

    private long deleteCurrentEvaluation(String collectionName, String project, String evaluationDate) {
        try {
            Document filter = new Document("evaluationDate", evaluationDate)
                .append("project", project);
            DeleteResult result = database.getCollection(collectionName).deleteMany(filter);
            return result.getDeletedCount();
        } catch (RuntimeException rte) {
            log.warning(rte.getMessage());
            return 0;
        }
    }

}
