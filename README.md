# Learning-Dashboard eval ![](https://img.shields.io/badge/License-Apache2.0-blue.svg)
LD-eval computes metrics, factors, and indicators on raw data stored in a MongoDB database. In the Learning Dasboard context, raw data is produced by a series of Kafka connectors (which read from Taiga, Github and other sources). Learning-Dashboard eval aggregates the raw data into metrics, and further on into factors and indicators, according to a defined quality model.

## Configuration
Learning-Dashboard eval is a commandline tool and is configured via a set of text files (.query and .properties) that are stored in a special folder structure. The top-folder is named 'projects'. 
This folder has to be present in the same directory where the qrapids-eval executable JAR file is stored. Each subfolder defines a quality model for a project to be evaluated. 
Moreover, it can also be executed as a Docker container in a Docker environment by using a script.

The folder structure shown below defines the evaluation of one project named 'default'.

```
+---projects

    +---default
    |   |
    |   +---factors
    |   |     factor1.properties
    |   |     factor1.query
    |   |
    |   +---indicators
    |   |     indicator1.properties
    |   |     indicator1.query
    |   |
    |   +---metrics
    |   |     metric1.properties
    |   |     metric1.query
    |   |     metric2.properties
    |   |     metric2.query
    |   |     metric3.properties
    |   |     metric3.query
    |   |
    |   +---params
    |   |     01_params1.properties
    |   |     01_params1.query
    |   |     02_params2.properties
    |   |     02_params2.query
    |   |
    |   |  factors.properties
    |   |  indicators.properties
    |   |  project.properties
    |
    | eval.properties

```

### projects/eval.properties

The *eval.properties* file defines global configuration options. Currently, only the url for notifying the dashboard about a new evaluation is contained:

```
dashboard.notification.url=http://<address>/api/strategicIndicators/assess
```

### projects/default/project.properties
The project.properties file contains the top-level configuration for a project evaluation. It defines the project.name (which will be appended to the metrics, factors, indicators and relations index names), the addresses to source and target MongoDB servers (host name, port number, database name...), the name and other properties of the source indexes (e.g. Github, Taiga...), and the names and types of the created (or reused) target indexes (metrics, factors, indicators and relations). 

The Error Handling is configurable. Error handling takes place when the computation of metrics, factors, or indicators fails. This can happen because of missing data, errors in formulas (e.g. division by 0) and for other reasons. The onError property allows to set a project-wide default (which can be overwritten for metrics, factors...) how to handle these errors.

- The 'drop' option just drops the metrics/factors/indicators item that can't be computed, no record is stored.

- The 'set0' option stores a record with value 0.

```properties
# project name
project.name=<projectName>

# MongoDB source data
mongodb.source.ip=<ip>
mongodb.source.port=<port>
mongodb.source.database=<database>
mongodb.source.user=<user> [Optional]
mongodb.source.password=<password> [Optional]

# MongoDB target data (metrics, factors, indicators, relations...)
# Could be same as source
mongodb.target.ip=<ip>
mongodb.target.port=<port>
mongodb.target.database=<database>
mongodb.target.user=<user> [Optional]
mongodb.target.password=<password> [Optional]

########################
#### SOURCE INDEXES ####
########################

# Taiga indexes
taiga.issue.index=taiga.issues
taiga.epic.index=taiga.epics
taiga.userstory.index=taiga.userstories
taiga.task.index=taiga.tasks

# Github indexes
github.index=github.commits

# Google Sheets indexes
sheets.index=sheets.imputations

########################
#### TARGET INDEXES ####
########################

# Metrics index [Mandatory]
metrics.index=metrics
metrics.index.type=metrics

# Impacts index [Mandatory]
relations.index=relations
relations.index.type=relations

# Factors index [Mandatory]
factors.index=factors
factors.index.type=factors

# Strategic Indicators index [Mandatory]
indicators.index=strategic_indicators
indicators.index.type=strategic_indicators

# Global error handling default: 'drop' or 'set0', default is 'drop'
# Error handling takes place when the computation of a metric/factor/indicator/relation fails
# Strategy 'drop' doesn't store the item, 'set0' sets the item's value to 0
# The setting can be overwritten for specific metrics, factors, and indicators
onError=set0
```

Values of the *project.properties* can be used in *params* and *metrics* queries. To refer to a project property in a query's property file, prefix the property-name with '$$'. In the example below, the project property taiga.issue.index is being used in the *metric1.properties* in the metrics folder:

```properties
index=$$taiga.issue.index
result.issuesTotal=issuesTotal
```

### projects/default/params
In the first phase of a project evaluation, LD-eval executes the queries in the params folder (*params queries*). 
These do not compute metrics or factors, but allow for querying other arbitrary values (noted with prefix 'result.', which then can be used in subsequent *params* and *metrics* queries as parameters. 
The results of params queries can be used in subsequent params and metrics queries without declaration in the associated property-files (unlike values of project.properties, where declaration is necessary).

The *params* queries are executed in sequence (alphabetical order). For this reason, it is a good practice to follow the suggested naming scheme for parameter queries and start the name of with a sequence of numbers (e.g. 01_query_name, 02_other_name). Since params queries build on each other, a proper ordering is necessary.

A query consists of a pair of files:

* A .properties file, that declares the index the query should run on, as well as parameters and results of the query.

* A .query file that contains the actual query in MQL (MongoDB Query Language) syntax, in Bson format (see [Aggregation Operations](https://www.mongodb.com/docs/manual/aggregation/)).
 
__Example (01_params1)__

01_params1.properties

```properties
index=$$taiga.issue.index
param.bcKey=$$taiga.epic.index
result.issuesTotal=issuesTotal
```
+ The index property is read from the project.properties files ($$-notation).

+ The query uses one parameter (bcKey), which is also read from the project properties file. Parameters of a query are declared with prefix 'param.'

+ The query defines one result (issuesTotal), that is specified as a path within the query result delivered by MongoDB. Results are declared with prefix 'result.'
All results computed by params queries can be used as parameters (without declaration) in subsequent *params* and *metrics* queries. Make sure that the names of the results of params queries are unique, otherwise they will get overwritten.

__Query Parameters__

LD-eval internally uses search templates to perform *params, metrics*, and other queries. 
Search templates can receive parameters ( noted with double curly braces: {{parameter}} ). The parameters are replaced by actual values, before the query is executed. The replacement is done verbatim and doesn't care about data types. Thus, if you want a string parameter, you'll have to add quotes around the parameter yourself (for example, writing "{{parameter}}").

+ The evaluationDate is available to all *params* and *metrics* queries without declaration. LD-eval started without command-line options sets the evaluationDate to the date of today (string, format yyyy-MM-dd).
  
+ Elements of the *project.properties* can be declared as a parameter with the $$ notation, as seen above (param.bcKey).
  
+ Literals (numbers and strings) can be used after declaration as parameters (e.g by *param.myThreshold=15*).
  
+ Results (noted with prefix 'result.') of *params queries* can be used as parameters in succeeding *params* and *metrics* queries without declaration.

### projects/default/metrics
The folder contains the metrics definitions of a project. As *params queries*, *metrics queries* consist of a pair of files, a .properties and a .query file. In addition to params queries, metrics queries compute a metric value defined by a formula. The computed metric value is stored in the metrics index (defined in *project.properties*) after the query execution.

Computed metrics get aggregated into factors. Therefore you have to specify the factors, a metric is going to influence. Metrics can influence one or more factors, that are supplied as a comma-separated list of factor IDs together with the weight describing the strength of the influence. In the example below, the metric 'metric1' influences two factors (factor1 and factor2) with weights 0.5 for factor1 and 1.0 for factor2. The value of a factor is then computed as a weighted sum of all metrics influencing a factor.

__Example: metric1 query__

metric1.properties

```properties
# values starting with $$ are looked up in project.properties
index=$$taiga.task.index

# metric props
enabled=true
name=Metric 1
description=Description of metric 1
factors=factor1,factor2
weights=0.5,1.0

# query parameters
param.milestone=false

# query results
result.tasksTotal=tasksTotal
result.tasksUnassigned=tasksUnassigned

# metric defines a formula based on execution results of parameter- and metric-queries
metric=tasksUnassigned / tasksTotal
onError=set0
```

__Note:__ The onError property can be set to 'drop' or 'set0' and overwrites to setting in *project.properties*.


metric1.query

```
[
  {
    "$match": {
      "milestone_closed": {{milestone}}
    }
  },
  {
    "$group": {
      "_id": null,
      "tasksTotal": {
        "$sum": 1
      },
      "tasksUnassigned": {
        "$sum": {
          "$cond": [
            {
              "$eq": [
                "$assigned",
                null
              ]
            },
            1,
            0
          ]
        }
      }
    }
  },
  {
    "$project": {
      "_id": 0,
      "tasksTotal": 1,
      "tasksUnassigned": 1
    }
  }
]
```

The metric1 query is based on an aggregation query to derive its results, divided into 3 stages.

- First, we have a match stage, where only the documents where the field 'milestone_closed' matches the parameter 'milestone' (which in this case is false) are taken into consideration.

- Next, we can see the group stage. Here, do not group the documents by any field (we could, using the '_id' variable), and we create two new variables. In each of them, we apply an operation over the grouped documents.
  
  - In the first one (tasksTotal) we simply count all of the grouped documents.
    
  - In the second one (tasksUnassigned) we count the number of grouped documents that follow a specific condition. In this case, the condition is for a field to be null.

- Lastly, we simply choose the fields we want to present in the result, which in this case are the two variables tasksTotal and tasksUnassigned.

__Example result__

```
{
  "tasksTotal": 158,
  "tasksUnassigned": 12
}
```

The metric (percentage of tasks that are not assigned) is then computed as: 

```
metric= tasksUnassigned / tasksTotal = 12 / 158 = 7.59%
```

### projects/default/factors.properties
The factors.properties file defines factors to compute along with their properties. Factors don't do sophisticated computations, they serve as a point for the aggregation of metric values. Factors are then aggregated into indicators, so they have to specify the indicators they are influencing along with the weights of the influence. The notation used has to be read as *factorid.property=value*. 

+ The *enabled* attribute enables/disables a factor (no records written for a factor when disabled).
  
+ The *name* property supplies a user-friendly name of a factor.
  
+ The *decription* attribute describes the intention of the factor.
  
+ The *indicators* attribute contains a list of influenced indicators (which are defined in a separate properties file).
  
+ The *weights* attribute sets the strength of the influence. Obviously, the lists in 'indicators' and 'weights' have to have the same length!
  
+ The *onError* attribute tells qr-eval what to do in case of factor computation errors (e.g. no metrics influence a factor, which results in a division by zero).

Example of factor definition (factor1):

```properties
factor1.enabled=true
factor1.name=Factor 1
factor1.description=Factor 1 description
factor1.indicators=indicator1
factor1.weights=1.0
factor1.onError=set0
```

__Note:__ The onError property can be set to 'drop' or 'set0' and overwrites to setting in project.properties.

### projects/default/indicators.properties
The indicators.properties file defines the strategic indicators for a project. The parents and weights attribute currently have no effect, but could define an additional level of aggregation in future. 

Example of strategic indicator definition (indicator1):

```properties
indicator1.enabled=true
indicator1.name=Indicator 1
indicator1.description=Indicator 1 description
indicator1.parents=meta
indicator1.weights=1.0
```

### projects/default/factors
Defines the query for aggregation of metrics into factors, based on relations index. 
DON'T TOUCH, unless you know what you are doing.

### projects/default/indicators
Defines the query for aggregation of factors into indicators, based on relations index. 
DON'T TOUCH, unless you know what you are doing.

## Running LD-eval

### Prerequisites
* MongoDB source and target servers are running and contain the appropriate collections and data.
  
* Java 1.8 is installed.
  
* A projects folder exists in the directory of qrapids-eval-\<version\>-jar-with-dependecies.jar and contains a proper quality model configuration.

### Run without commandline parameters
The date of the current day (format yyyy-MM-dd) will be available as parameter 'evaluationDate' in params and metrics queries.

```
java -jar qrapids-eval-<version>-jar-with-dependencies.jar
```

### Specify a single evaluation date
The specified evaluationDate will be available as parameter 'evaluationDate' in params and metrics  queries.

```
java -jar qrapids-eval-<version>-jar-with-dependencies.jar evaluationDate 2019-03-01
```

### Specify a date range for evaluation
The defined projects will be evaluated for each day in the specified range.

```
java -jar qrapids-eval-<version>-jar-with-dependencies.jar from 2019-03-01 to 2019-03-30
```

### Build the connector
```
mvn package assembly:single
```
After build, you'll find the generated jar in the target folder.

## Model validation
Before the evaluation of a project starts, qrapids-eval performs a basic evaluation of the qualtity model. A warning is logged in the following cases:

+ A metrics-query mentions a factor in the factors-property, but the factor isn't defined in the factors.properties file.

+ A factor mentioned in a metric is not enabled.

+ A factor is defined in factors.properties, but not mentioned in any metrics-query.

+ An indicator is mentioned in the indicators-property of a defined factor, but is not defined in the indicators.properties file.

+ An indicator is mentioned in the indicators-property of a defined factor, but is not enabled.

+ An indicator is defined in indicators.properties, but it is not mentioned in any indicators-property of the defined factors.


## Built With

* [Maven](https://maven.apache.org/) - Dependency Management


## Authors

* **Axel Wickenkamp, Fraunhofer IESE**
* **Laura Cazorla, FIB-UPC**

