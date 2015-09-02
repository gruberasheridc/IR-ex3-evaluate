package evaluate;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.stream.Collectors;

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
		System.out.println("After query precision calc");
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
	 * The method calculates the Precision at K of a given query.
	 * @param truthQueryDocs a map from query ID to the list of truth documents.
	 * @param queyDocRank a map from query ID to the list of relevant document, rank pairs (output from the algorithm).
	 * @param k the k for whom to perform the precision at K calculation.
	 * @return the precision at K of the given query.
	 */
	private static float calcQueryPrecisionAtK(Map<Integer, List<Integer>> truthQueryDocs, Entry<Integer, List<ImmutablePair<Integer, Integer>>> queyDocRank, int k) {
		
		// Take the query's top k documents which are already sorted. If we have less then k documents take the actual amount.
		List<ImmutablePair<Integer, Integer>> docksWithRank = queyDocRank.getValue();
		int topDocIdx = 0;
		int lastDocIdx = Math.min(docksWithRank.size(), k);
		List<ImmutablePair<Integer, Integer>> docsTopK = docksWithRank.subList(topDocIdx, lastDocIdx);
		
		// Take the truth documents of the query.
		int matchingDocsCount = 0;
		Integer queryID = queyDocRank.getKey();
		List<Integer> queryTruthDocs = truthQueryDocs.get(queryID);
		
		// Calculate the number of documents which exist in the truth query documents list.
		if (queryTruthDocs != null) {
			// We have truth documents for the query.
			for (ImmutablePair<Integer, Integer> docRank : docsTopK) {
				Integer docID = docRank.getLeft();
				if (queryTruthDocs.contains(docID)) {
					matchingDocsCount++;
				}
			}
		}
		
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
