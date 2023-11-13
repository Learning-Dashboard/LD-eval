package eval2;

import java.io.File;
import java.util.*;
import java.util.Map.Entry;

import type.Factor;
import type.IndexItem;
import type.Indicator;
import type.Metric;
import type.Relation;

import java.util.logging.Logger;

import util.Evaluator;
import util.FileUtils;

public class EvalProject {
	
	private final Logger log = Logger.getLogger(this.getClass().getName());
	
	private String evaluationDate;

	// Project folder containing queries, properties etc.
	private File projectFolder;
	
	// Contents of projectFolder/project.properties
	private Properties projectProperties;
	
	// MongoDB source
	private MongoDB mongodbSource;
	
	// MongoDB target
	private MongoDB mongodbTarget;

	// Param query set of this project
	private Map<String,QueryDef> paramQuerySet;
	
	// Metric query set of this project
	private Map<String,QueryDef> metricQuerySet;
	
	public String projectErrorStrategy;

	
	public EvalProject(File projectFolder, String evaluationDate ) {
		this.projectFolder = projectFolder;
		String projectPropertyFilename = projectFolder.getAbsolutePath() + File.separatorChar + "project.properties";
		this.projectProperties = FileUtils.loadProperties( new File(projectPropertyFilename) );
		
		projectErrorStrategy = projectProperties.getProperty("onError", IndexItem.ON_ERROR_DROP);
		this.evaluationDate = evaluationDate;
	}
	
	public void validateModel() {
		File metricQueryFolder = new File( projectFolder.getAbsolutePath() + File.separatorChar + "metrics" );
		metricQuerySet = getQuerySet( metricQueryFolder );
		ModelChecker.check( metricQuerySet, readFactorMap(), readIndicatorMap() );
	}
	
	public void run() {
		
		validateModel();
		File metricQueryFolder = new File( projectFolder.getAbsolutePath() + File.separatorChar + "metrics" );
		metricQuerySet = getQuerySet( metricQueryFolder ); 

		log.info("Connecting to MongoDB Source (" + projectProperties.getProperty("mongodb.source.ip") + ")\n");
		mongodbSource = new MongoDB(
			projectProperties.getProperty("mongodb.source.user"),
			projectProperties.getProperty("mongodb.source.password"),
			projectProperties.getProperty("mongodb.source.ip"),
			Integer.parseInt(projectProperties.getProperty("mongodb.source.port")),
			projectProperties.getProperty("mongodb.source.database")
		);
		
		log.info("Connecting to MongoDB Target (" + projectProperties.getProperty("mongodb.target.ip") + ")\n");
		mongodbTarget = new MongoDB(
				projectProperties.getProperty("mongodb.target.user"),
				projectProperties.getProperty("mongodb.target.password"),
				projectProperties.getProperty("mongodb.target.ip"),
				Integer.parseInt(projectProperties.getProperty("mongodb.target.port")),
				projectProperties.getProperty("mongodb.target.database")
		);
		
		File paramQueryFolder = new File( projectFolder.getAbsolutePath() + File.separatorChar + "params" );
		paramQuerySet = getQuerySet( paramQueryFolder ); 
		
		log.info("Executing param queries (" + paramQuerySet.size() + " found)\n");
		Map<String,Object> queryParameter = executeParamQueryset( paramQuerySet, evaluationDate );
		log.info("Param query result: " + queryParameter + "\n"); 

		log.info("Executing metric queries (" + metricQuerySet.size() + " found)\n");
		List<Metric> metrics = executeMetricQueries(queryParameter, metricQuerySet);
		log.info("Storing metrics (" + metrics.size() + " computed)\n");
		mongodbTarget.storeMetrics( projectProperties, evaluationDate, metrics );

        log.info("Computing Factors...\n");
        Collection<Factor> factors = computeFactors();
		log.info("Storing factors (" + factors.size() + " computed)\n");
		mongodbTarget.storeFactors(projectProperties, evaluationDate, factors);

		log.info("Computing Indicators...\n");
		Collection<Indicator> indicators = computeIndicators();
		log.info("Storing indicators (" + factors.size() + " computed)\n");
		mongodbTarget.storeIndicators(projectProperties, evaluationDate, indicators);

		List<Relation> metricrelations = computeFactorMetricsRelations(metrics, factors);
		log.info("Storing metrics relations (" + metricrelations.size() + " computed)\n");
		mongodbTarget.storeRelations(projectProperties, metricrelations);

		/*
		List<Relation> factorrelations = computeIndicatorFactorsRelations(factors, indicators);
		log.info("Storing factors relations (" + factorrelations.size() + " computed)\n");
		mongodbTarget.storeRelations(projectProperties, factorrelations);
		 */
	}
	

	/**
	 * Computes Factor values based on relation items
	 * @return List of computed Factors
	 */
	private Collection<Factor> computeFactors() {
		List<Factor> result = new ArrayList<>();
		String factorQueryDir = projectFolder.getAbsolutePath() + File.separatorChar + "factors";
		QueryDef factorQuery = loadQueryDef(factorQueryDir, "factor");
		factorQuery.setIndex( factorQuery.getProperty("index") + "." + projectProperties.getProperty("project.name"));
		Map<String,Factor> factorMap = readFactorMap();
		
		for ( Entry<String,Factor> e : factorMap.entrySet() ) {
			
			Factor fact = e.getValue();
			if ( !fact.isEnabled() ) {
				log.info("Factor " + fact.getFactor() + " is disabled.\n");
				continue;
			}
			else log.info("Computing factor " + fact.getFactor() + ".\n");

			HashMap<String, Double> metricValues = new HashMap<>();
			List<String> missingMetrics = new ArrayList<>();

			for (String metric : fact.getMetrics()) {
				Map<String, Object> parameters = new HashMap<>();
				parameters.put("evaluationDate", evaluationDate);
				parameters.put("project", projectProperties.getProperty("project.name"));
				parameters.put("metric", metric);
				Map<String, Object> results = mongodbTarget.execute(parameters, factorQuery, false);
				if (!results.isEmpty() && results.containsKey("value")) {
					Object value = results.get("value");
					if (value instanceof Double)
						metricValues.put(metric, (Double) value);
					else if (value instanceof Integer)
						metricValues.put(metric, ((Integer) value).doubleValue());
				}
				else missingMetrics.add(metric);
			}

			Double factorValue;
			boolean nonWeighted = Arrays.asList(fact.getWeights()).contains(-1.0);
			boolean someNonWeighted = Arrays.stream(fact.getWeights()).anyMatch(val -> !val.equals(-1.0));

			try {
				if (fact.getMetrics().length != fact.getWeights().length)
					throw new IllegalArgumentException("Metrics and weights arrays must be the same length.");
				else {
					if (nonWeighted && someNonWeighted)
						throw new IllegalArgumentException("Weights arrays must be fully weighted or fully non weighted.");
				}
				factorValue = evaluate(fact.getMetrics(), fact.getWeights(), metricValues);
			}
			catch (IllegalArgumentException exc) {
				log.warning(exc.getMessage() + "\nFactor: " + fact.getName() + "\n");
				if (fact.onErrorSet0()) {
					log.warning("Factor " + fact.getFactor() + " set to 0.\n");
					factorValue = 0.0;
				}
				else {
					log.warning("Factor " + fact.getFactor() + " is dropped.\n");
					continue;
				}
			}
				
			if (factorValue.isNaN() || factorValue.isInfinite()) {
				log.warning("Evaluation of factor " + fact.getFactor() + " resulted in non-numeric value.\n" );
				if (fact.onErrorSet0()) {
					log.warning("Factor " + fact.getFactor() + " set to 0.\n");
					factorValue = 0.0;
				}
				else {
					log.warning("Factor " + fact.getFactor() + " is dropped.\n");
					continue;
				}
			}
			else log.info("Value of factor " + fact.getFactor() + " = " + factorValue + "\n");
			
			fact.setValue(factorValue);
			fact.setEvaluationDate(evaluationDate);
			String[] missingChildren = new String[missingMetrics.size()];
			fact.setMissingChildren(missingMetrics.toArray(missingChildren));
			boolean weighted = !Arrays.stream(fact.getWeights()).allMatch(val -> val.equals(-1.0));

			StringBuilder info = new StringBuilder("metrics: {");
			if (fact.getMetrics().length == fact.getWeights().length) {
				if (someNonWeighted && nonWeighted)
					info.append(" }, formula: average, value: ").append(fact.getValue().floatValue());
				else {
					for (Map.Entry<String, Double> entry : metricValues.entrySet()) {
						String metricInfo = " " + entry.getKey() + " (value: " + entry.getValue().floatValue() + ", ";
						if (weighted) {
							for (int i = 0; i < fact.getMetrics().length; ++i) {
								if (Objects.equals(entry.getKey(), fact.getMetrics()[i])) {
									Double w = fact.getWeights()[i].floatValue() * 100.0;
									metricInfo += "weight: " + w.intValue() + "%);";
									break;
								}
							}
						} else metricInfo += "no weighted);";
						info.append(metricInfo);
					}
					if (weighted) info.append(" }, formula: weighted average, value: ").append(fact.getValue().floatValue());
					else info.append(" }, formula: average, value: ").append(fact.getValue().floatValue());
				}
			}
			else info.append(" }, formula: average, value: ").append(fact.getValue().floatValue());

			fact.setInfo(info.toString());
			result.add(fact);
		}

		return result;
	}
	
	/**
	 * Compute Relations between Factors and Indicators
	 * @param factors factor evaluations to be computed (as source)
	 * @param indicators indicator evaluations to be computed (as target)
	 * @return List of Relation
	 */
	/*private List<Relation> computeIndicatorFactorsRelations(Collection<Factor> factors, Collection<Indicator> indicators) {
		List<Relation> result = new ArrayList<>();
		Map<String,Indicator> indicatorMap = readIndicatorMap();
		
		for ( Factor factor : factors ) {
			for ( int i = 0; i < factor.getIndicators().length; i++ ) {
				String indicatorid = factor.getIndicators()[i];
				Double weight = factor.getWeights()[i];
				Indicator indicator = indicatorMap.get(indicatorid);
				
				if ( indicator == null ) {
					log.info( "Warning: Impact of Factor " + factor.getName() + " on undefined Indicator " + indicatorid + "is not stored."  );
				} else {
					if ( !indicator.isEnabled() ) {
						log.info("Indicator " + indicator.getName() + " is disabled. No relation created.\n");
						continue;
					}
					Relation imp = new Relation(factor.getProject(), factor, indicator, evaluationDate, factor.getValue() * weight, weight);
					result.add(imp);
				}
			}
		}

		return result;
	}*/
	
	/**
	 * Compute Indicator values based on Factor-indicator relations
	 * @return List of Indicator
	 */
	private Collection<Indicator> computeIndicators() {
		List<Indicator> result = new ArrayList<>();
		String indicatorQueryDir = projectFolder.getAbsolutePath() + File.separatorChar + "indicators";
		QueryDef indicatorQuery = loadQueryDef(indicatorQueryDir, "indicator");
		indicatorQuery.setIndex(indicatorQuery.getProperty("index") + "." + projectProperties.getProperty("project.name"));
		Map<String,Indicator> indicatorMap = readIndicatorMap();

		for ( Entry<String,Indicator> e : indicatorMap.entrySet() ) {

			Indicator ind = e.getValue();
			if ( !ind.isEnabled() ) {
				log.info("Indicator " + ind.getIndicator() + " is disabled.\n");
				continue;
			}
			else log.info("Computing indicator " + ind.getIndicator() + ".\n");

			HashMap<String, Double> factorValues = new HashMap<>();
			List<String> missingFactors = new ArrayList<>();

			for (String factor : ind.getFactors()) {
				Map<String, Object> parameters = new HashMap<>();
				parameters.put("evaluationDate", evaluationDate);
				parameters.put("project", projectProperties.getProperty("project.name"));
				parameters.put("factor", factor);
				Map<String, Object> results = mongodbTarget.execute(parameters, indicatorQuery, false);
				if (!results.isEmpty() && results.containsKey("value")) {
					Object value = results.get("value");
					if (value instanceof Double)
						factorValues.put(factor, (Double) value);
					else if (value instanceof Integer)
						factorValues.put(factor, ((Integer) value).doubleValue());
				}
				else missingFactors.add(factor);
			}

			Double indicatorValue;
			boolean nonWeighted = Arrays.asList(ind.getWeights()).contains(-1.0);
			boolean someNonWeighted = Arrays.stream(ind.getWeights()).anyMatch(val -> !val.equals(-1.0));

			try {
				if (ind.getFactors().length != ind.getWeights().length)
					throw new IllegalArgumentException("Factors and weights arrays must be the same length.");
				else {
					if (nonWeighted && someNonWeighted)
						throw new IllegalArgumentException("Weights arrays must be fully weighted or fully non weighted.");
				}
				indicatorValue = evaluate(ind.getFactors(), ind.getWeights(), factorValues);
			}
			catch (IllegalArgumentException exc) {
				log.warning(exc.getMessage() + "\nIndicator: " + ind.getName() + "\n");
				if (ind.onErrorSet0()) {
					log.warning("Indicator " + ind.getName() + " set to 0.\n");
					indicatorValue = 0.0;
				} else {
					log.warning("Indicator " + ind.getName() + " is dropped.\n");
					continue;
				}
				
			}

			if (indicatorValue.isNaN() || indicatorValue.isInfinite()) {
				log.warning("Evaluation of indicator " + ind.getIndicator() + " resulted in non-numeric value.\n" );
				if ( ind.onErrorSet0() ) {
					log.warning("Indicator " + ind.getName() + " set to 0.\n");
					indicatorValue = 0.0;
				} else {
					log.warning("Indicator " + ind.getName() + " is dropped.\n");
					continue;
				}
			} else {
				log.info("Value of indicator " + ind.getIndicator() + " = " + indicatorValue + "\n");
			}
			
			ind.setValue(indicatorValue);
			ind.setEvaluationDate(evaluationDate);
			String[] missingChildren = new String[missingFactors.size()];
			ind.setMissingChildren(missingFactors.toArray(missingChildren));
			boolean weighted = !Arrays.stream(ind.getWeights()).allMatch(val -> val.equals(-1.0));
			
			StringBuilder info = new StringBuilder("factors: {");
			if (ind.getFactors().length == ind.getWeights().length) {
				if (someNonWeighted && nonWeighted)
					info.append(" }, formula: average, value: ").append(ind.getValue().floatValue());
				else {
					for (Map.Entry<String, Double> entry : factorValues.entrySet()) {
						String factorInfo = " " + entry.getKey() + " (value: " + entry.getValue().floatValue() + ", ";
						if (weighted) {
							for (int i = 0; i < ind.getFactors().length; ++i) {
								if (Objects.equals(entry.getKey(), ind.getFactors()[i])) {
									Double w = ind.getWeights()[i].floatValue() * 100.0;
									factorInfo += "weight: " + w.intValue() + "%);";
									break;
								}
							}
						}
						else factorInfo += "no weighted);";
						info.append(factorInfo);
					}
					if (weighted) info.append(" }, formula: weighted average, value: ").append(ind.getValue().floatValue());
					else info.append(" }, formula: average, value: ").append(ind.getValue().floatValue());
				}
			}
			else info.append(" }, formula: average, value: ").append(ind.getValue().floatValue());

			ind.setInfo(info.toString());
			result.add(ind);
		}
		
		return result;
	}

	/**
	 * Execute a Set of Queries
	 * @param querySets Map of QueryDef
	 * @return Map of execution results Name -> Value
	 */
	private Map<String, Object> executeParamQueryset( Map<String, QueryDef> querySets, String evaluationDate ) {
		Map<String,Object> allExecutionResults = new HashMap<>();
		allExecutionResults.put("evaluationDate", evaluationDate);

		for ( String key : querySets.keySet() ) {
			Map<String,Object> executionResult = mongodbSource.execute( allExecutionResults, querySets.get(key), true );
			allExecutionResults.putAll(executionResult);
		}
		
		return allExecutionResults;
	}
	
	/**
	 * Execute Metric queries
	 * @param parameters Parameter Map
	 * @param metricQuerySet Query Map
	 * @return List of Metric
	 */
	private List<Metric> executeMetricQueries( Map<String,Object> parameters, Map<String, QueryDef> metricQuerySet) {
		List<Metric> result = new ArrayList<>();

		for ( String key : metricQuerySet.keySet() ) {
			QueryDef metricQueryDef = metricQuerySet.get(key);
			if ( !metricQueryDef.isEnabled() ) {
				log.info("Metric " + metricQueryDef.getName() + " is disabled.\n");
				continue;
			}
			
			String info;
			info = "parameters: " + parameters.toString() + "\n";
			info += "query-properties: " + metricQueryDef.getQueryParameter().toString() + "\n";
			
			log.info("Executing metric query: " + key + "\n");
			Map<String,Object> executionResult = mongodbSource.execute( parameters, metricQueryDef, true );
			log.info("result: " + executionResult + "\n");
			
			info += "executionResults: " + executionResult.toString() + "\n";
			String metricDef = metricQueryDef.getProperty("metric");
			info += "formula: " + metricDef + "\n";
			
			Map<String,Object> evalParameters = new HashMap<>();
			evalParameters.putAll(parameters);
			evalParameters.putAll(executionResult);
			
			Double metricValue;
			try {
				metricValue = evaluate( metricDef, evalParameters );
				info += "value: " + metricValue;
			} catch (RuntimeException rte) {
				log.warning("Evaluation of formula " + metricDef + " failed. \nMetric: " + key);
				if ( metricQueryDef.onErrorDrop() ) {
					log.warning("Metric " + key + " is dropped.");
					continue;
				} else {
					metricValue = metricQueryDef.getErrorValue();
					log.warning("Metric " + key + " set to " + metricValue + ".");
				}
			}
			
			log.info("Metric " + metricQueryDef.getName() +" = " + metricValue + "\n");
			if( metricValue.isInfinite() || metricValue.isNaN() ) {
				log.warning("Formula evaluated as NaN or inifinite.");
				if ( metricQueryDef.onErrorDrop() ) {
					log.warning("Metric " + key + " is dropped.");
					continue;
				} else {
					metricValue = metricQueryDef.getErrorValue();
					log.warning("Metric " + key + " set to " + metricValue + ".");
				}
			}
			
			String project = projectProperties.getProperty("project.name");
			String metric = metricQueryDef.getName();
			String name = metricQueryDef.getProperty("name");
			String description = metricQueryDef.getProperty("description");

			String datasource = mongodbSource.getMongodbIP() + ":" + mongodbSource.getMongodbPort() +
				"/" + mongodbSource.getMongodbDatabaseName() + "." + metricQueryDef.getProperty("index");
		
			String onError = metricQueryDef.getProperty("onError");
			if ( onError == null ) onError = projectErrorStrategy;
		
			Metric m = new Metric(project, metric, evaluationDate, null, null, null, name, description, datasource, metricValue, info, onError);
			result.add(m);
		}
		
		return result;
	}
	
	/**
	 * Compute relations between (enabled) Metrics and Factors
	 * @param metrics metric evaluations to be computed (as source)
	 * @param factorsCollection factor evaluations to be computed (as target)
	 * @return List of Relation
	 */
	private List<Relation> computeFactorMetricsRelations(List<Metric> metrics, Collection<Factor> factorsCollection) {
		List<Relation> result = new ArrayList<>();
		List<Factor> factors = new ArrayList<>(factorsCollection);

		for (Factor factor : factors) {
			for (int i = 0; i < factor.getMetrics().length; ++i) {
				String metricId = factor.getMetrics()[i];
				Double weight = factor.getWeights()[i];
				Metric metric = null;
				for (Metric m : metrics)
					if (Objects.equals(m.getMetric(), metricId)) metric = m;

				if (metric == null)
					log.info( "WARNING: Impact of undefined or disabled metric " + metricId + " on factor " + factor.getFactor() + " is not stored."  );
				else {
					Relation imp = new Relation(metric.getProject(), metric, factor, evaluationDate, metric.getValue() * weight, weight);
					result.add(imp);
				}
			}
		}

		return result;
	}

	/**
	 * Read Map of Factors (id->Factor) from factors.properties file
	 * @return Map of Factors
	 */
	private Map<String, Factor> readFactorMap() {
		Map<String,Factor> result = new HashMap<>();
		File factorPropFile = new File( projectFolder.getAbsolutePath() + File.separatorChar + "factors.properties" );
		Properties factorProperties = FileUtils.loadProperties(factorPropFile);
		List<String> factors = getFactors( factorProperties );
		
		for ( String f : factors ) {
			Boolean enabled = Boolean.parseBoolean(  factorProperties.getProperty(f + ".enabled") );
			String project = projectProperties.getProperty("project.name");
			String[] metrics = getAsStringArray( factorProperties.getProperty(f + ".metrics") );
			Double[] weights = getAsDoubleArray( factorProperties.getProperty(f + ".weights") );
			String name = factorProperties.getProperty(f + ".name");
			String description = factorProperties.getProperty(f + ".description");
			String datasource = factorProperties.getProperty(f + ".datasource");

			Double value = null;
			String onError = factorProperties.getProperty(f + ".onError");
			if ( onError == null ) onError = projectErrorStrategy;

			Factor fact = new Factor(enabled, project, f, evaluationDate, metrics, weights, null, name, description, datasource, value, null, onError);
			result.put(f, fact);
		}
		
		return result;
	}
	
	/**
	 * Read Map of Indicators (id->Indicator) from indicators.properties file
	 * @return Map of Indicators
	 */
	private Map<String, Indicator> readIndicatorMap() {
		Map<String,Indicator> result = new HashMap<>();
		File indicatorPropFile = new File( projectFolder.getAbsolutePath() + File.separatorChar + "indicators.properties" );
		Properties indicatorProperties = FileUtils.loadProperties(indicatorPropFile);
		List<String> indicators = getFactors( indicatorProperties );
		
		for ( String i : indicators ) {
			Boolean enabled = Boolean.parseBoolean(  indicatorProperties.getProperty(i + ".enabled") );
			String project = projectProperties.getProperty("project.name");
			String[] factors = getAsStringArray( indicatorProperties.getProperty(i + ".factors") );
			Double[] weights = getAsDoubleArray( indicatorProperties.getProperty(i + ".weights") );
			String name = indicatorProperties.getProperty(i + ".name");
			String description = indicatorProperties.getProperty(i + ".description");

			Double value = null;
			String onError = indicatorProperties.getProperty(i + ".onError");
			if ( onError == null ) onError = projectErrorStrategy;

			Indicator ind = new Indicator(enabled, project, i, evaluationDate, factors, weights, null, name, description, null, value, null, onError);
			result.put(i, ind);
		}
		
		return result;
	}
	
	/**
	 * Read List of Factors ids from factors.properties file
	 * @param props contents of the factors.properties file
	 * @return a list of the Factors' ids
	 */
	private List<String> getFactors( Properties props ) {
		List<String> result = new ArrayList<>();
		Set<Object> keys = props.keySet();
		
		for ( Object k : keys ) {
			String ks = (String) k;
			if ( ks.endsWith(".name") ) {
				result.add(ks.substring(0, ks.indexOf(".name")));	
			}
		}

		return result;
	}

	/**
	 * Evaluate metric formula for given named parameters
	 * @param metric query to be executed
	 * @param evalParameters parameters to introduce into the query
	 * @return value of the evaluated metric
	 */
	private Double evaluate(String metric, Map<String, Object> evalParameters) {
		for ( String key : evalParameters.keySet() )
			metric = metric.replaceAll( key, evalParameters.get(key).toString() );
		double res = Evaluator.eval(metric);
		return Math.min(res, 1.0);
	}

	/**
	 * Evaluate entity value from their children values and weights
	 * @param children names of the child entities
	 * @param weights weights of those child entities
	 * @param childrenValues evaluated values of each child entity
	 * @return value of the evaluated entity
	 */
	private Double evaluate(String[] children, Double[] weights, Map<String, Double> childrenValues) {
		double value = 0.0;
		Double weight = 0.0;
		boolean weighted = !Arrays.stream(weights).allMatch(val -> val.equals(-1.0));
		if (!weighted) weight = (1.0 / children.length);
		for (int i = 0; i < children.length; ++i) {
			String childName = children[i];
			if (weighted) {
				if (weights.length > i) {
					weight = weights[i];
					if (weight == -1.0) weight = 0.0;
				}
				else weight = 0.0;
			}
			if (childrenValues.containsKey(childName))
				value += childrenValues.get(childName) * weight;
		}
		return Math.min(value, 1.0);
	}

	/**
	 * Read Map of QueryDefs from directory
	 * @param queryDirectory directory where the queries are stored
	 * @return Map of QueryDefs
	 */
	private Map<String,QueryDef> getQuerySet(File queryDirectory) {
		
		Map<String,QueryDef> querySets = new HashMap<>();
		
		String[] filenames = queryDirectory.list();
		if (filenames != null) {
			Arrays.sort(filenames);
			for (String fname : filenames) {

				String pathName = queryDirectory.getAbsolutePath() + File.separatorChar + fname;
				File f = new File(pathName);
				if (f.isFile()) {
					String filename = f.getName();
					String[] parts = filename.split("\\.");
					String name = parts[0];
					String type = parts[1];

					if (type.equals("query")) {
						String queryTemplate = FileUtils.readFile(f);
						if (querySets.containsKey(name))
							querySets.get(name).setQueryTemplate(queryTemplate);
						else querySets.put(name, new QueryDef(name, projectProperties, queryTemplate, null));
					}

					if (type.equals("properties")) {
						Properties props = FileUtils.loadProperties(f);
						if (querySets.containsKey(name)) querySets.get(name).setProperties(props);
						else querySets.put(name, new QueryDef(name, projectProperties, null, props));
					}
				}
			}
		}
		
		return querySets;
	}
	
	public QueryDef loadQueryDef( String directory, String name ) {
		File templateFile = new File( directory + File.separatorChar + name + ".query");
		String queryTemplate = FileUtils.readFile(templateFile);
		File propertyFile = new File( directory + File.separatorChar + name + ".properties");
		Properties props = FileUtils.loadProperties(propertyFile);
		return new QueryDef(name, projectProperties, queryTemplate, props);
	}

	public String[] getAsStringArray(String commaSeparated) {
		if (commaSeparated == null || commaSeparated.equals(""))
			return new String[0];
		else return commaSeparated.split(",");
	}
	
	/**
	 * Return Property values with comma as a Double array:
	 * "1.5,2.3" becomes [1.5,2.3]
	 */
	public Double[] getAsDoubleArray(String commaSeparated) {
		if (commaSeparated == null || commaSeparated.equals(""))
			return new Double[0];

		String[] parts = commaSeparated.split(",");
		Double[] doubleArray = new Double[parts.length];
		for (int i = 0; i < parts.length; ++i)
			doubleArray[i] = Double.parseDouble(parts[i]);
		return doubleArray;
	}

}
