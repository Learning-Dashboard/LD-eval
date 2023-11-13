package type;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

public class Indicator extends IndexItem {
	
	public Indicator (
			Boolean enabled,
			String project,
			String indicator,
			String evaluationDate,
			String [] children,
			Double [] weights,
			String[] missingChildren,
			String name, 
			String description, 
			String datasource,
			Double value,
			String info,
			String onError) {

		this.enabled = enabled;
		this.project  = project;
		this.id = indicator;
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
	public String getMongodbId() {
		return this.id + "-" + this.evaluationDate;
	}
	
	@Override
	public String getType() {
		return "indicators";
	}

	public String getIndicator() {
		return id;
	}

	public void setIndicator(String indicator) {
		this.id = indicator;
	}

	public String[] getFactors() {
		return children;
	}

	public void setFactors(String[] factors) {
		this.children = factors;
	}

	public Map<String, Object> getMap() {
		Map<String, Object> result = new HashMap<>();
		ArrayList<Double> arrayListWeights = new ArrayList<>(Arrays.asList(weights));
		ArrayList<String> arrayListChildren = new ArrayList<>(Arrays.asList(children));
		ArrayList<String> arrayListMissingFactors = new ArrayList<>();

		result.put("project", project);
		result.put("strategic_indicator", id);
		result.put("evaluationDate", evaluationDate);
		result.put("factors", arrayListChildren);
		result.put("weights", arrayListWeights);
		result.put("name", name);
		result.put("description", description);
		result.put("datasource", "Learning Dashboard");
		result.put("value", value);
		result.put("info", info);
		result.put("missing_factors", arrayListMissingFactors);
		return result;
	}

}
