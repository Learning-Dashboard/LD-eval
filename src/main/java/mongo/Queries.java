package mongo;

public class Queries {
	
	public static Long getLastestDateValue( String index, String dateField, String selectionField, String selectionVal ) {
		
		
		/*TransportClient tc = Client.getClient();

		try {
			SearchResponse sr = tc
			        .prepareSearch(index)
			        .setQuery(QueryBuilders.matchQuery(selectionField, selectionVal))
			        .addAggregation( AggregationBuilders.max("lastSnapshot").field(dateField) )
			        .execute()
			        .actionGet();
			Max last = sr.getAggregations().get("lastSnapshot");
			
			return (long) last.getValue() ;
			
		} catch( NoNodeAvailableException nna) {
			nna.printStackTrace();
			return null;
		}*/
		return null;
	}
	
	public static void main(String[] args) {
		getLastestDateValue("sonarqube.measures","snapshotDate", "bcKey", "tomcat:9.x");
	}

}
