package evaluate;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.apache.commons.lang3.tuple.MutablePair;

public class EvaluateSearch {

	public static void main(String[] args) {
		if (args.length != 2) {
			System.out.println("Must accept two parameter: (1) Search output file. (2) truth.txt.");
			return;
		}
		
		String truthFile = args[0];
		String algoOutputFile = args[1];
		
		// Read the truth file and create a map from query ID to the list of relevant documents (i.e. Map<Query ID, List<DocID>>).
		Path truthPath = Paths.get(truthFile);
		try {
			List<String> queryDocLines = Files.readAllLines(truthPath);
			Map<Integer, List<Integer>> queryDocs = queryDocLines.stream()
				.map (line -> {
						String[] columns = line.split(" ");
						Integer queryId = Integer.parseInt(columns[0]);
						Integer docId = Integer.parseInt(columns[3]);
						return new MutablePair<Integer, Integer>(queryId, docId);
					})
				.collect(Collectors.groupingBy(entry -> entry.getKey(), Collectors.mapping((MutablePair<Integer, Integer> entry) -> entry.getValue(), Collectors.toList())));
			
			System.out.println(queryDocs);
		} catch (IOException e) {
			// TODO handle catch block
			e.printStackTrace();
		}
	}

}
