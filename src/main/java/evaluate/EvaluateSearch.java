package evaluate;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
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
		Map<Integer, List<Integer>> queryDocs = createQueryDocsTruthMap(truthFile);
		if (queryDocs == null) {
            System.out.println("Faild to process the truth file: " + truthFile + ".");
		}
		
		// Read the search algorithm output file and create a map from query ID to the list of relevant document, rank pairs (i.e. Map<QueryID, List<ImmutablePair<DocID, Rank>>>).		
		String algoOutputFile = args[1];
		Map<Integer, List<ImmutablePair<Integer, Integer>>> queryDocsWithRanks = createQueryDocsAlgoOutputMap(algoOutputFile);		
		if (queryDocsWithRanks == null) {
            System.out.println("Faild to process the search algorithm output file: " + algoOutputFile + ".");
            return;
		}
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
						Integer docId = null;
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
