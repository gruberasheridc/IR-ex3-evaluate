package evaluate;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.OptionalDouble;
import java.util.stream.Collectors;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.ImmutableTriple;

public class EvaluateSearch {
	
	private static final String QUERY_PREFIX = "q";
	private static final String DOCUMENT_PREFIX = "doc";
	private static final String DUMMAY_PREFIX = "dummy";

	public static void main(String[] args) {
		if (args.length != 2) {
			System.out.println("Must accept two parameter: (1) Search output file. (2) truth.txt.");
			return;
		}
				
		// Read the truth file and create a map from query ID to the list of relevant documents (i.e. Map<Query ID, List<DocID>>).
		String truthFile = args[0];
		Map<Integer, List<Integer>> truthQueryDocs = createQueryDocsTruthMap(truthFile);
		if (truthQueryDocs == null) {
            System.out.println("Faild to process the truth file: " + truthFile + ".");
		}
		
		// Read the search algorithm output file and create a map from query ID to the list of relevant document, rank pairs (i.e. Map<QueryID, List<ImmutablePair<DocID, Rank>>>).		
		String algoOutputFile = args[1];
		Map<Integer, List<ImmutablePair<Integer, Integer>>> queryDocsWithRanks = createQueryDocsAlgoOutputMap(algoOutputFile);		
		if (queryDocsWithRanks == null) {
            System.out.println("Faild to process the search algorithm output file: " + algoOutputFile + ".");
            return;
		}
		
		Map<Integer, Float> queryPrecisionAt5 = calcPrecisionAtK(truthQueryDocs, queryDocsWithRanks, 5);
		Map<Integer, Float> queryPrecisionAt10 = calcPrecisionAtK(truthQueryDocs, queryDocsWithRanks, 10);
		Map<Integer, Float> queryAvgPrecisionAt5 = calcAvgPrecisionAtK(truthQueryDocs, queryDocsWithRanks, 5);		
		Map<Integer, Float> queryAvgPrecisionAt10 = calcAvgPrecisionAtK(truthQueryDocs, queryDocsWithRanks, 10);
		float mapAt5 = calcMAPAtK(queryAvgPrecisionAt5);
		System.out.println("MAP@5: " + mapAt5 + ".");
		float mapAt10 = calcMAPAtK(queryAvgPrecisionAt10);
		System.out.println("MAP@10: " + mapAt10 + ".");
	}

	/**
	 * The method calculates the Mean Average Precision of the given queries information.
	 * @param queryAvgPrecisionAtk a map of query ID to Average Precision at K of all the queries in the query set.
	 * @return MAP of the given queries.
	 */
	private static float calcMAPAtK(Map<Integer, Float> queryAvgPrecisionAtk) {
		float mapAtK = 0;
		if (MapUtils.isNotEmpty(queryAvgPrecisionAtk)) {
			OptionalDouble mapAtKResult = queryAvgPrecisionAtk.values().stream().mapToDouble(v -> v).average();
			mapAtK = (float) mapAtKResult.getAsDouble();
		}
		
		return mapAtK;
	}

	/**
	 * The method calculates the Average Precision at K of the query set given to the algorithm.
	 * @param truthQueryDocs a map from query ID to the list of truth documents.
	 * @param queyDocRank a map from query ID to the list of relevant document, rank pairs (output from the algorithm).
	 * @param k the k for whom to perform the Average Precision at K calculation.
	 * @return a map of query ID to Average Precision at K of all the queries in the query set.
	 */
	private static Map<Integer, Float> calcAvgPrecisionAtK(Map<Integer, List<Integer>> truthQueryDocs, Map<Integer, 
			List<ImmutablePair<Integer, Integer>>> queryDocsWithRanks, int k) {
		Map<Integer, Float> queryPrecisionAtK = new HashMap<>();
		queryDocsWithRanks.entrySet().stream()
			.sorted(Map.Entry.comparingByKey())
			.forEach(queyDocRank -> 
				{
					// Calculate the Average Precision at K of the query ID.
					Integer queryID = queyDocRank.getKey();
					float avgPrecisionAtK = calcQueryAvgPrecisionAtK(truthQueryDocs, queyDocRank, k);
					queryPrecisionAtK.put(queryID, avgPrecisionAtK);
				}
			);
		
		return queryPrecisionAtK;
	}

	
	/**
	 * The method calculates the Average Precision at K of a given query.
	 * @param truthQueryDocs a map from query ID to the list of truth documents.
	 * @param queyDocRank a map from query ID to the list of relevant document, rank pairs (output from the algorithm).
	 * @param k the k for whom to perform the Average Precision at K calculation.
	 * @return the Average Precision at K of the given query.
	 */
	private static float calcQueryAvgPrecisionAtK(Map<Integer, List<Integer>> truthQueryDocs, Entry<Integer, List<ImmutablePair<Integer, Integer>>> queyDocRank, int k) {
		float avgPrecisionAtK = 0;
		
		List<ImmutableTriple<Integer, Integer, Integer>> docHitsInfo = getDocsMatchInfoAtK(truthQueryDocs, queyDocRank, k);
		if (CollectionUtils.isNotEmpty(docHitsInfo)) {
			int matchDocRelationSum = 0;
			for (ImmutableTriple<Integer, Integer, Integer> docHitInfo : docHitsInfo) {
				//List<ImmutableTriple<DocID, Doc location, matchingDocsCount>>
				int hitsUntilDoc = docHitInfo.getRight(); // The number of doc hits until the current doc (including the doc).
				int docLocation = docHitInfo.getMiddle(); // The document location from the top (top = 1).
				matchDocRelationSum += (hitsUntilDoc / docLocation);
			}
			
			int docHitsCount = docHitsInfo.size();
			avgPrecisionAtK = matchDocRelationSum / docHitsCount; 
		}
		
		return avgPrecisionAtK;
	}

	/**
	 * The method calculates the Precision at K of the query set given to the algorithm.
	 * @param truthQueryDocs a map from query ID to the list of truth documents.
	 * @param queyDocRank a map from query ID to the list of relevant document, rank pairs (output from the algorithm).
	 * @param k the k for whom to perform the precision at K calculation.
	 * @return a map of query ID to precision at K of all the queries in the query set.
	 */
	private static Map<Integer, Float> calcPrecisionAtK(Map<Integer, List<Integer>> truthQueryDocs,
			Map<Integer, List<ImmutablePair<Integer, Integer>>> queryDocsWithRanks, int k) {		
		Map<Integer, Float> queryPrecisionAtK = new HashMap<>();
		queryDocsWithRanks.entrySet().stream()
			.sorted(Map.Entry.comparingByKey())
			.forEach(queyDocRank -> 
				{
					Integer queryID = queyDocRank.getKey();
					float precisionAtK = calcQueryPrecisionAtK(truthQueryDocs, queyDocRank, k);
					queryPrecisionAtK.put(queryID, precisionAtK);
				}
			);
		
		return queryPrecisionAtK;
	}

	/**
	 * The method calculates the documents match information for the top K ranked documents of a given query.
	 * @param truthQueryDocs a map from query ID to the list of truth documents.
	 * @param queyDocRank a map from query ID to the list of relevant document, rank pairs (output from the algorithm).
	 * @param k the K for whom to calculate the documents match information.
	 * @return the documents which exist in the truth query documents list and the counts matches until there location (i.e. List<ImmutableTriple<DocID, Doc location, matchingDocsCount>>)
	 */
	private static List<ImmutableTriple<Integer,Integer,Integer>> getDocsMatchInfoAtK(Map<Integer, List<Integer>> truthQueryDocs,
			Entry<Integer, List<ImmutablePair<Integer, Integer>>> queyDocRank, int k) {
		// Take the query's top k documents which are already sorted. If we have less then k documents take the actual amount.
		List<ImmutablePair<Integer, Integer>> docksWithRank = queyDocRank.getValue();
		int topDocIdx = 0;
		int lastDocIdx = Math.min(docksWithRank.size(), k);
		List<ImmutablePair<Integer, Integer>> docsTopK = docksWithRank.subList(topDocIdx, lastDocIdx);
		
		// Take the truth documents of the query.
		Integer queryID = queyDocRank.getKey();
		List<Integer> queryTruthDocs = truthQueryDocs.get(queryID);
		
		// Finds the documents which exist in the truth query documents list and counts matches until 
		// there location (i.e. List<ImmutableTriple<DocID, Doc location, matchingDocsCount>>).
		List<ImmutableTriple<Integer, Integer, Integer>> docHitWithMatchUntil = new ArrayList<>();
		if (queryTruthDocs != null) {
			// We have truth documents for the query.
			int matchingDocsCount = 0;
			int docIdx = 1;
			for (ImmutablePair<Integer, Integer> docRank : docsTopK) {
				Integer docID = docRank.getLeft();
				if (queryTruthDocs.contains(docID)) {
					// Document match.
					matchingDocsCount++;
					ImmutableTriple<Integer, Integer, Integer> docMatchInfo = new ImmutableTriple<Integer, Integer, Integer>(docID, docIdx, matchingDocsCount);
					docHitWithMatchUntil.add(docMatchInfo);
				}
				
				docIdx++;
			}
		}
		
		return docHitWithMatchUntil;
	}

	/**
	 * The method calculates the Precision at K of a given query.
	 * @param truthQueryDocs a map from query ID to the list of truth documents.
	 * @param queyDocRank a map from query ID to the list of relevant document, rank pairs (output from the algorithm).
	 * @param k the k for whom to perform the precision at K calculation.
	 * @return the precision at K of the given query.
	 */
	private static float calcQueryPrecisionAtK(Map<Integer, List<Integer>> truthQueryDocs, Entry<Integer, List<ImmutablePair<Integer, Integer>>> queyDocRank, int k) {
		List<ImmutableTriple<Integer, Integer, Integer>> docHitInfo = getDocsMatchInfoAtK(truthQueryDocs, queyDocRank, k);
		int matchingDocsCount = CollectionUtils.isNotEmpty(docHitInfo) ? docHitInfo.size() : 0; 
		float precisionAtK = matchingDocsCount / k;
		return precisionAtK;
	}

	/**
	 * Read the search algorithm output file and create a map from query ID to the list of relevant document, rank pairs (i.e. Map<QueryID, List<ImmutablePair<DocID, Rank>>>).	
	 * @param algoOutputFile the search algorithm output file path.
	 * @return a map from query ID to the list of relevant document, rank pairs.
	 */
	private static Map<Integer, List<ImmutablePair<Integer, Integer>>> createQueryDocsAlgoOutputMap(String algoOutputFile) {
		Map<Integer, List<ImmutablePair<Integer, Integer>>> queryDocsWithRanks = null;
		Path algoOutput = Paths.get(algoOutputFile);
		try {
			List<String> algoOutputLines = Files.readAllLines(algoOutput);
			queryDocsWithRanks = algoOutputLines.stream()
				.map (line -> {
						String[] columns = line.split(",");
						Integer queryId = Integer.parseInt(columns[0].replace(QUERY_PREFIX, "")); // Extract the query ID for text format.
						
						// Extract the document ID form text in case it is a real document. Otherwise keep docId = null. 
						Integer docId = -1;
						String doc = columns[1];
						if (doc.startsWith(DOCUMENT_PREFIX)) {
							docId = Integer.parseInt(doc.replace(DOCUMENT_PREFIX, "")); 
						}						
						
						Integer rank = Integer.parseInt(columns[2]);
						return new ImmutableTriple<Integer, Integer, Integer>(queryId, docId, rank);
					})
				.collect(Collectors.groupingBy(entry -> entry.getLeft(), 
						 Collectors.mapping((ImmutableTriple<Integer, Integer, Integer> entry) -> new ImmutablePair<Integer, Integer>(entry.getMiddle(), entry.getRight()), 
						 Collectors.toList())));
		} catch (IOException e) {

		}
		
		return queryDocsWithRanks;
	}

	/**
	 * Read the truth file and create a map from query ID to the list of relevant documents (i.e. Map<Query ID, List<DocID>>).
	 * @param truthFile the truth file path.
	 * @return a map from query ID to the list of relevant documents.
	 */
	private static Map<Integer, List<Integer>> createQueryDocsTruthMap(String truthFile) {
		Map<Integer, List<Integer>> queryDocs = null;
		Path truthPath = Paths.get(truthFile);
		try {
			List<String> queryDocLines = Files.readAllLines(truthPath);
			queryDocs = queryDocLines.stream()
				.map (line -> {
						String[] columns = line.split(" ");
						Integer queryId = Integer.parseInt(columns[0]);
						Integer docId = Integer.parseInt(columns[2]);
						return new ImmutablePair<Integer, Integer>(queryId, docId);
					})
				.collect(Collectors.groupingBy(entry -> entry.getKey(), 
						 Collectors.mapping((ImmutablePair<Integer, Integer> entry) -> entry.getValue(), 
						 Collectors.toList())));					
		} catch (IOException e) {
			
		}
		
		return queryDocs;
	}

}
