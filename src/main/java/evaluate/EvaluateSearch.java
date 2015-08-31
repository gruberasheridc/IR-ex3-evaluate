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
		Path truthPath = Paths.get(truthFile);
		try {
			List<String> queryDocLines = Files.readAllLines(truthPath);
			Map<Integer, List<Integer>> queryDocs = queryDocLines.stream()
				.map (line -> {
						String[] columns = line.split(" ");
						Integer queryId = Integer.parseInt(columns[0]);
						Integer docId = Integer.parseInt(columns[2]);
						return new ImmutablePair<Integer, Integer>(queryId, docId);
					})
				.collect(Collectors.groupingBy(entry -> entry.getKey(), 
						 Collectors.mapping((ImmutablePair<Integer, Integer> entry) -> entry.getValue(), 
						 Collectors.toList())));
			
			System.out.println(queryDocs);
		} catch (IOException e) {
			System.out.println("Faild to process the truth file: " + truthFile + ".");
			return;
		}
		
		// Read the search algorithm output file and create a map from query ID to the list of relevant document, rank pairs (i.e. Map<QueryID, List<ImmutablePair<DocID, Rank>>>).
		String algoOutputFile = args[1];
		Path algoOutput = Paths.get(algoOutputFile);
		try {
			List<String> algoOutputLines = Files.readAllLines(algoOutput);
			Map<Integer, List<ImmutablePair<Integer, Integer>>> queryDocsWithRanks = algoOutputLines.stream()
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
			
			System.out.println(queryDocsWithRanks);
		} catch (IOException e) {
			System.out.println("Faild to process the search algorithm output file: " + algoOutputFile + ".");
			return;
		}
	}

}
