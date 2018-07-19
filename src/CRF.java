import java.io.File;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Scanner;
import java.util.Set;

import com.wcohen.ss.Levenstein;

import edu.stanford.nlp.ie.AbstractSequenceClassifier;
import edu.stanford.nlp.ie.crf.CRFClassifier;
import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreLabel;


public class CRF {

  public static void main(String[] args) throws Exception {

	  File keyParaDir = new File("./data/key_paragraphs_split/");
	  
	  // choose which CRF model you want to use and comment the other one out
//	  String serializedClassifier = "./res/classifiers/compenv-old-ner-model.ser.gz";
	  String serializedClassifier = "./res/classifiers/compenv-current-ner-model.ser.gz";
	  
	  // set of files to evaluate -- must be current, old, or all
	  String evalSet = "all";
	  
	  HashSet<String> currentFiles = new HashSet<>();
	  currentFiles.add("bio3");
	  currentFiles.add("bio4");	
	  currentFiles.add("bio12");
	  currentFiles.add("bio18");
	  currentFiles.add("che3");	
	  currentFiles.add("che5");
	  currentFiles.add("che10");
	  currentFiles.add("che13");
	  currentFiles.add("che17");
	  currentFiles.add("che18");
	  currentFiles.add("che19");
	  currentFiles.add("eng1"); 
	  currentFiles.add("eng7");
	  currentFiles.add("eng14");
	  currentFiles.add("eng19"); 
	  currentFiles.add("mth8");	
	  currentFiles.add("mth15");	
	  currentFiles.add("mth18");
	  currentFiles.add("phy7");
	  currentFiles.add("phy9");
	  
	  HashSet<String> evalFiles = new HashSet<>();

	  if (evalSet.equals("current")) { 
		  evalFiles = currentFiles;

	  } else if (evalSet.equals("old")) {
		  for (File f: keyParaDir.listFiles()) {
			  String filename = f.getName();
			  if (filename.startsWith("old") && filename.endsWith(".key")) {
				  evalFiles.add(filename.substring(0, filename.indexOf(".key")));
			  }
		  }
	  } else if (evalSet.equals("all")) {
		  for (File f: keyParaDir.listFiles()) {
			  String filename = f.getName();
			  if (filename.endsWith(".key") && !filename.contains("old")) {
				  evalFiles.add(filename.substring(0, filename.indexOf(".key")));
			  }
		  }
		  evalFiles.removeAll(currentFiles);
	  } else {
		  System.err.println("Invalid evaluation dataset: must be one of " + 
				  " current, old, or all");
		  System.exit(-1);
	  }
	  
	  // read in each answer file and create a HashMap of HashMaps:
	  // outer key (String): filename   
	  // inner key (String): tag name   
	  // values (HashSet of Strings): tag values
	  HashMap<String, HashMap<String, HashSet<String>>> answers = new HashMap<>();
	  File answerFile = new File("./data/answers/answers.txt");
	  Scanner scanner = new Scanner(answerFile);
	  
	  String activeFile = null;
	  HashMap<String, HashSet<String>> fileAnswers = new HashMap<>();
	  
	  while (scanner.hasNext()) {
		  String line = scanner.nextLine().trim().toLowerCase();
		  if (line.length() == 0) continue;
		  
		  if (line.startsWith("-")) {
			  if (activeFile != null && evalFiles.contains(activeFile)) {
				  answers.put(activeFile, fileAnswers);
			  }
			  activeFile = line.substring(1).trim();
			  fileAnswers = new HashMap<>();
		  } else {
			  String tag = line.substring(0, line.indexOf(":")).trim();
			  String value = line.substring(line.indexOf(":") + 1).trim();
			  if (fileAnswers.containsKey(tag)) {
				  fileAnswers.get(tag).add(value);
			  } else {
				  HashSet<String> values = new HashSet<>();
				  values.add(value);
				  fileAnswers.put(tag, values);
			  }
		  }
	  }
	  
	  if (evalFiles.contains(activeFile)) {
		  answers.put(activeFile, fileAnswers); // last file's tags
	  }
	  scanner.close();
	  
	  // displays tags + all unique values (used to get the charts in Section 4 of the paper)
//	  HashMap<String, Integer> tagCounts = new HashMap<>();
//	  HashMap<String, HashSet<String>> tagMap = new HashMap<>();
//	  for (String file: answers.keySet()) {
//		  HashMap<String, HashSet<String>> fileTags = answers.get(file);
//		  for (String tag: fileTags.keySet()) {
//			  if (!tagMap.containsKey(tag)) {
//				  HashSet<String> values = new HashSet<>();
//				  tagMap.put(tag, values);
//				  tagCounts.put(tag, 0);
//			  }
//			  tagMap.get(tag).addAll(fileTags.get(tag));
//			  tagCounts.put(tag, tagCounts.get(tag)+1);
//		  }
//	  }
//	  
//	  for (String tag: tagMap.keySet()) {
//		  System.out.println(tag + ": " + tagCounts.get(tag));
//		  for (String value: tagMap.get(tag)) {
//			  System.out.println("\t" + value);
//		  }
//	  }
	  
	  // for every file in the evaluation set, run the classifier and 
	  // add the results to the same type of data structure
	  HashMap<String, HashMap<String, HashSet<String>>> guesses = new HashMap<>();
	  
      AbstractSequenceClassifier<CoreLabel> classifier = 
    		  CRFClassifier.getClassifier(serializedClassifier);

      for (String filename: evalFiles) {
    	  System.out.println(filename);

    	  HashMap<String, HashSet<String>> tags = new HashMap<>();

    	  // get the key paragraph file for the set we want to evaluate
    	  String fileToClassify = keyParaDir + "/" + filename + ".key";

    	  // classify it and add the results to the data structure
    	  List<List<CoreLabel>> out = classifier.classifyFile(fileToClassify);

    	  for (List<CoreLabel> sentence : out) {

    		  String storedTag = null;
    		  String storedText = "";
    		  boolean oParen = false;
    		  
    		  for (CoreLabel word : sentence) {
    			  
    			  String tag = word.get(CoreAnnotations.AnswerAnnotation.class);
    			  String text = word.word().toLowerCase().trim();    			  
    			  
    			  // lrb and lsb are "left round brace", i.e. ( and "left square brace", i.e. [
    			  // this code basically removes untagged parentheticals from within a tag
    			  // This comes up a lot because of things like Intel (R) Core i5 or 
    			  // MATLAB (tm) Neural Network Toolbox.
    			  if (tag.equals("O") && (text.equals("-lrb-") || text.equals("-lsb-"))) {
    				  oParen = true;
    				  continue;
    			  }
    			  
    			  if (oParen && (text.equals("-rrb-") || text.equals("-rsb-"))) {
    				  oParen = false;
    				  continue;
    			  }
    			  
    			  if (oParen && !tag.equals("O")) {
    				  oParen = false;
    			  }
    			  
    			  if (storedTag != null && !tag.equals(storedTag) && !oParen) {
    				  
    				  if (!storedTag.equals("O")) {
    					  
    					  if (!tags.containsKey(storedTag)) {
    						  HashSet<String> values = new HashSet<>();
    						  tags.put(storedTag, values);
    					  }

    					  storedText = storedText.replaceAll("-lrb- ", ("("));
    					  storedText = storedText.replaceAll(" -rrb-", (")"));
    					  storedText = storedText.replaceAll("-lsb- ", ("["));
    					  storedText = storedText.replaceAll(" -rsb-", ("]"));
    					  storedText = storedText.replaceAll(" tm ", " ");
    					  
    					  tags.get(storedTag).add(storedText.trim());
    				  }
    				  
    				  storedText = new String();
    			  }
    			 
    			  if (!oParen) {
    				  storedTag = tag;
    				  storedText += " " + text;
    			  }
    		  }
    	  }

    	  guesses.put(filename, tags);
      }
	  

	  // compute precision, recall and f-measure overall and for each type
      // Custom contains and string equality metrics are used to compensate for
      // things like "Intel." instead of "Intel"
	  int truePositives = 0;
	  int falseNegatives = 0;
	  int falsePositives = 0;
	  
	  for (String filename: answers.keySet()) {
		  HashMap<String, HashSet<String>> answerSet = answers.get(filename);
		  HashMap<String, HashSet<String>> guessSet = guesses.get(filename);
		  
		  System.out.println("answers: " + answerSet);
		  System.out.println("guesses: " + guessSet);
		  
		  for (String answerTag: answerSet.keySet()) {
			  
			  HashSet<String> answerValues = answerSet.get(answerTag);
			  
			  if (!guessSet.containsKey(answerTag)) {
				  falseNegatives += answerValues.size();
				  System.out.println("FN: " + answerValues + "  /  " + answerTag);
				  continue;
			  }
			  
			  HashSet<String> guessValues = guessSet.get(answerTag);
			  
			  for (String answerValue: answerValues) {
				  String sim = myContains(guessValues, answerValue);
				  if (sim != null) {
					  truePositives++;
					  System.out.println("TP: " + answerValue + "  /  " + answerTag + 
							  "  (" + sim + ")");
				  } else {
					  falseNegatives++;
					  System.out.println("FN: " + answerValue + "  /  " + answerTag);
				  }
			  }
		  }
		  
		  for (String guessTag: guessSet.keySet()) {
			  
			  if (guessTag.equals("O")) continue; // uncategorized
			  
			  HashSet<String> guessValues = guessSet.get(guessTag);
			  
			  if (!answerSet.containsKey(guessTag)) {
				  falsePositives += guessValues.size();
				  System.out.println("FP: " + guessValues + "  /  " + guessTag);
				  continue;
			  }
			  
			  HashSet<String> answerValues = answerSet.get(guessTag);
			  
			  for (String guessValue: guessValues) {
				  if (myContains(answerValues, guessValue) == null) {
					  falsePositives++;
					  System.out.println("FP: " + guessValue + "  /  " + guessTag);
				  }
			  }
		  }
		  
		  System.out.println();
		  System.out.println("TP: " + truePositives);
		  System.out.println("FP: " + falsePositives);
		  System.out.println("FN: " + falseNegatives);
	  }
	  
	  double precision = truePositives / (float) (truePositives + falsePositives);
	  double recall = truePositives / (float) (truePositives + falseNegatives);
	  double fMeasure = (2 * precision * recall) / (precision + recall);
	  
	  System.out.println("Precision: " + precision);
	  System.out.println("Recall: " + recall);
	  System.out.println("F-Measure: " + fMeasure);
  }
  
  
  private static String myContains(Set<String> set, String value) {
	  
	  for (String s: set) {
		  double stringSim = myStringSim(s, value);
		  if (stringSim > 0.8) {
			  return s;
		  }
	  }
	  
	  return null;
  }
  
  
  private static double myStringSim(String a, String b) {
	  
	  Levenstein metric = new Levenstein();
	  
	  String shorter = a.length() < b.length() ? a : b;
	  String longer = a.length() > b.length() ? a : b;
	  
	  shorter = shorter.replaceAll(" ", "");
	  longer = longer.replaceAll(" ", "");
	  
	  double sim = Math.abs(metric.score(shorter, longer));
	  sim /= longer.length();
	  return 1 - sim;
  }
  
}
