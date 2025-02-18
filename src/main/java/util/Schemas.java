package util;

import org.bson.Document;

import java.util.Arrays;

public class Schemas {

    public static final Document METRICS_SCHEMA = new Document("$jsonSchema", new Document()
        .append("bsonType", "object")
        .append("required", Arrays.asList("_id", "description", "evaluationDate", "factors", "info", "metric", "name", "project", "source", "type", "value", "weights"))
        .append("properties", new Document()
            .append("_id", new Document("bsonType", Arrays.asList("objectId", "string")))
            .append("project", new Document("bsonType", Arrays.asList("string", "null")))
            .append("metric", new Document("bsonType", Arrays.asList("string", "null")))
            .append("evaluationDate", new Document("bsonType", Arrays.asList("string", "null")))
            .append("factors", new Document()
                .append("bsonType", "array")
                .append("items", new Document("bsonType", "string"))
            )
            .append("weights", new Document()
                .append("bsonType", "array")
                .append("items", new Document("bsonType", Arrays.asList("double", "int")))
            )
            .append("name", new Document("bsonType", Arrays.asList("string", "null")))
            .append("description", new Document("bsonType", Arrays.asList("string", "null")))
            .append("value", new Document("bsonType", Arrays.asList("double", "int", "null")))
            .append("info", new Document("bsonType", Arrays.asList("string", "null")))
            .append("source", new Document("bsonType", Arrays.asList("string", "null")))
            .append("type", new Document("bsonType", Arrays.asList("string", "null")))
        )
    );

    public static final Document FACTORS_SCHEMA = new Document("$jsonSchema", new Document()
        .append("bsonType", "object")
        .append("required", Arrays.asList("_id", "project", "factor", "evaluationDate", "indicators", "name", "description", "datasource", "value", "info", "missing_metrics", "dates_mismatch_days"))
        .append("properties", new Document()
            .append("_id", new Document("bsonType", Arrays.asList("objectId", "string")))
            .append("project", new Document("bsonType", Arrays.asList("string", "null")))
            .append("factor", new Document("bsonType", Arrays.asList("string", "null")))
            .append("evaluationDate", new Document("bsonType", Arrays.asList("string", "null")))
            .append("datasource", new Document("bsonType", Arrays.asList("string", "null")))
            .append("name", new Document("bsonType", Arrays.asList("string", "null")))
            .append("description", new Document("bsonType", Arrays.asList("string", "null")))
            .append("value", new Document("bsonType", Arrays.asList("double", "int", "null")))
            .append("info", new Document("bsonType", Arrays.asList("string", "null")))
            .append("missing_metrics", new Document()
                .append("bsonType", "array")
                .append("items", new Document("bsonType", "string"))
            )
            .append("dates_mismatch_days", new Document("bsonType", Arrays.asList("int", "null")))
            .append("indicators", new Document()
                .append("bsonType", "array")
                .append("items", new Document("bsonType", "string"))
            )
        )
    );

    public static final Document STRATEGIC_INDICATORS_SCHEMA = new Document("$jsonSchema", new Document()
        .append("bsonType", "object")
        .append("required", Arrays.asList("_id", "datasource", "description", "evaluationDate", "strategic_indicator", "name", "project", "value", "info", "missing_factors", "dates_mismatch_days"))
        .append("properties", new Document()
            .append("_id", new Document("bsonType", Arrays.asList("objectId", "string")))
            .append("project", new Document("bsonType", Arrays.asList("string", "null")))
            .append("strategic_indicator", new Document("bsonType", Arrays.asList("string", "null")))
            .append("evaluationDate", new Document("bsonType", Arrays.asList("string", "null")))
            .append("datasource", new Document("bsonType", Arrays.asList("string", "null")))
            .append("name", new Document("bsonType", Arrays.asList("string", "null")))
            .append("description", new Document("bsonType", Arrays.asList("string", "null")))
            .append("value", new Document("bsonType", Arrays.asList("double", "int", "null")))
            .append("info", new Document("bsonType", Arrays.asList("string", "null")))
            .append("missing_factors", new Document()
                .append("bsonType", "array")
                .append("items", new Document("bsonType", "string"))
            )
            .append("dates_mismatch_days", new Document("bsonType", Arrays.asList("int", "null")))
        )
    );

    public static final Document RELATIONS_SCHEMA = new Document("$jsonSchema", new Document()
        .append("bsonType", "object")
        .append("required", Arrays.asList("_id", "project", "relation", "evaluationDate", "sourceId", "sourceType", "targetId", "targetType", "value", "weight", "targetValue", "sourceLabel"))
        .append("properties", new Document()
            .append("_id", new Document("bsonType", Arrays.asList("objectId", "string")))
            .append("project", new Document("bsonType", Arrays.asList("string", "null")))
            .append("relation", new Document("bsonType", Arrays.asList("string", "null")))
            .append("evaluationDate", new Document("bsonType", Arrays.asList("string", "null")))
            .append("sourceId", new Document("bsonType", Arrays.asList("string", "null")))
            .append("sourceType", new Document("bsonType", Arrays.asList("string", "null")))
            .append("targetId", new Document("bsonType", Arrays.asList("string", "null")))
            .append("targetType", new Document("bsonType", Arrays.asList("string", "null")))
            .append("value", new Document("bsonType", Arrays.asList("double", "int", "null")))
            .append("weight", new Document("bsonType", Arrays.asList("double", "int", "null")))
            .append("targetValue", new Document("bsonType", Arrays.asList("string", "null")))
            .append("sourceLabel", new Document("bsonType", Arrays.asList("string", "null")))
        )
    );

}
