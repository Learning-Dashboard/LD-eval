package eval2;

import java.util.Map;
import java.util.logging.Logger;
import type.Factor;
import type.Indicator;

public class ModelChecker {
	public static Logger log = Logger.getLogger("eval2.ModelChecker");
	
	public static void check(Map<String,QueryDef> metricQueries, Map<String, Factor> factorMap, Map<String, type.Indicator> indicatorMap) {
		
		// Metrics referenced in factor.properties that are defined in <metric>.properties
		for (Factor f : factorMap.values()) {
			if (!f.isEnabled()) continue;
			String[] influencedMetrics = f.getMetrics();
			for (String m : influencedMetrics) {
				if (!metricQueries.containsKey(m)) {
					log.warning("Metric " + m + " influences factor " + f.getFactor() + " but no " + m + ".properties file exists.\n");
				}
				else {
					QueryDef qd = metricQueries.get(m);
					if (!qd.isEnabled()) {
						log.warning( "Metric " + m + " influences factor " + f.getFactor() + " but is not enabled in " + m + ".properties.\n");
					}
				}
			}
		}

		// For each Factor defined in factor.properties, check that it is influenced by a Metric
		// Also check that each Metric has its corresponding weight, and vice versa
		for ( Factor f : factorMap.values() ) {
			if ( !f.isEnabled() ) continue;
			if (f.getMetrics() == null ||  f.getMetrics().length == 0) {
				log.warning("Factor " + f.getFactor() + " is defined in factor.properties but not influenced by any metric defined in <metric>.properties.\n");
			}
			if (f.getMetrics().length != f.getWeights().length) {
				log.warning("The number of metrics that influence factor " + f.getFactor() + " (" +
					f.getMetrics().length + ") does not match its number of metric weights (" +
					f.getWeights().length + ").\n");
			}
		}
		
		// Factors referenced in indicator.properties that are defined in factor.properties
		for ( Indicator i : indicatorMap.values() ) {
			if ( !i.isEnabled() ) continue;
			String[] influencedFactors = i.getFactors();
			for ( String f : influencedFactors ) {
				if ( !factorMap.containsKey(f) ) {
					log.warning("Factor " + f + " influences indicator " + i.getIndicator() + " but is not defined in factor.properties.\n");
				} else {
					if ( !factorMap.get(f).isEnabled() ) {
						log.warning( "Factor " + f + " influences indicator " + i.getIndicator() + " but is not enabled in factor.properties.\n" );
					}
				}
			}
		}
		
		// For each Indicator defined in indicator.properties check that it is influenced by a Factor
		// Also check that each Factor has its corresponding weight, and vice versa
		for ( Indicator i : indicatorMap.values() ) {
			if ( !i.isEnabled() ) continue;
			if ( i.getFactors() == null || i.getFactors().length == 0 ) {
				log.warning("Indicator " + i.getIndicator() + " is defined in indicator.properties but not influenced by any factor defined in factor.properties.\n");
			}
			if (i.getFactors().length != i.getWeights().length) {
				log.warning("The number of factors that influence indicator " + i.getIndicator() + " (" +
						i.getFactors().length + ") does not match its number of factor weights (" +
						i.getWeights().length + ").\n");
			}
		}
		
		
	}

}
