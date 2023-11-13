package type;

import java.util.HashMap;
import java.util.Map;

public class Metric extends IndexItem {
	
	public Metric (
			String project, 
			String metric,
			String evaluationDate,
			String[] children,
			Double[] weights,
			String[] missingChildren,
			String name,
			String description,
			String datasource,
			Double value,
			String info,
			String onError) {

		this.project = project;
		this.id = metric;
		this.evaluationDate = evaluationDate;
		this.children = children;
		this.weights = weights;
		this.missingChildren = missingChildren;
		this.name = name;
		this.description = description;
		this.datasource = datasource;
		this.value = value;
		this.info = info;
		this.onError = onError;
	}
	
	@Override
	public String getType() {
		return "metrics";
	}

	public String getMetric() {
		return id;
	}

	public void setMetric(String metric) {
		this.id = metric;
	}

	public Map<String, Object> getMap() {
		Map<String, Object> result = new HashMap<>();
		result.put("type", getType() );
		result.put("project", project);
		result.put("metric", id);
		result.put("name", name);
		result.put("description", description);
		result.put("source", datasource);
		result.put("value", value);
		result.put("info", info);
		result.put("evaluationDate", evaluationDate);
		return result;
	}

}
