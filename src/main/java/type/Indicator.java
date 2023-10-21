package type;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

public class Indicator extends IndexItem {
	
	public Indicator( 
			
			Boolean enabled,
			String project,
			String indicator,
			String evaluationDate,
			
			String [] parents,
			Double [] weights,
			
			String name, 
			String description, 
			String datasource,
			Double value,
			String info,
			
			String onError
			
		) { 

		this.enabled = enabled;
		
		this.project  = project;
		this.id = indicator;
		this.evaluationDate = evaluationDate;
		
		this.parents = parents;
		this.weights = weights;
		
		this.name = name;
		this.description = description;
		this.datasource = datasource;
		
		this.value = value;
		this.info = info;
		
		this.onError = onError;

	}
	
	@Override
	public String getType() {
		return "indicators";
	}

	public String getIndicator() {
		return id;
	}

	public void setFactor(String factor) {
		this.id = factor;
	}

	public String[] getIndicators() {
		return parents;
	}

	public void setIndicators(String[] indicators) {
		this.parents = indicators;
	}

	public Map<String, Object> getMap() {
		Map<String, Object> result = new HashMap<>();
		ArrayList<Double> arrayListWeights = new ArrayList<>(Arrays.asList(weights));
		ArrayList<String> arrayListParents = new ArrayList<>(Arrays.asList(parents));
		ArrayList<String> arrayListFactors = new ArrayList<>();

		result.put("project", project);
		result.put("strategic_indicator", id);
		result.put("evaluationDate", evaluationDate);
		
		result.put("parents", arrayListParents);
		result.put("weights", arrayListWeights);
		
		result.put("name", name);
		result.put("description", description);
		result.put("datasource", datasource);
		
		result.put("value", value);
		result.put("info", info);

		result.put("missing_factors", arrayListFactors);
		result.put("dates_mismatch_days", 0);
		
		return result;
		
	}




}
