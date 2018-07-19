import edu.stanford.nlp.ling.*;
import edu.stanford.nlp.pipeline.*;
import edu.stanford.nlp.util.ArraySet;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.*;

import org.python.core.PyObject;
import org.python.core.PyString;
import org.python.util.PythonInterpreter;


// Queries Wikipedia for all proper nouns in a key paragraph and tries 
// to infer their type, where the list of possible types is in the 
// tags.txt file. Synonyms for common terms involved in tags, like 
// processor for CPU, can be provided in synonyms.txt and may improve
// performance.
// 
// The code stores the results of all Wikipedia queries in a cache file
// (caches/wikiCache.txt) to avoid duplicate network calls. You may 
// need to delete this file if it gets very large.
//
// Note: this code uses Jython and requires that the wikipedia python
// library has been installed for Python 2 (i.e. pip2 install wikipedia)

public class WikipediaAnnotator implements Annotator {

	private String cacheFile;
	private HashSet<String> tags = new HashSet<>();
	private HashSet<String> terms = new HashSet<>();
	private HashMap<String, HashSet<String>> synonyms = new HashMap<>();
	private HashMap<String, String> wikiCache = new HashMap<>();
	
	private PyObject wikiQueryFunction;
	
	
	@SuppressWarnings("unchecked")
	public WikipediaAnnotator(String name, Properties props) {

		PythonInterpreter interpreter = new PythonInterpreter();
		interpreter.exec("import sys");
		interpreter.exec("sys.path.insert(0, '/usr/local/lib/python2.7/site-packages')");
		interpreter.exec("sys.path.insert(0, '/Users/michelle/Library/Python/2.7/lib/python/site-packages')");
		interpreter.exec("sys.path.insert(0, '')");
		interpreter.exec("from wikiQuery import queryWikipedia");
		wikiQueryFunction = interpreter.get("queryWikipedia");
		
		try {
			String tagFile = props.getProperty("wikipedia.tagFile");

			Scanner in = new Scanner(new File(tagFile));
			while (in.hasNext()) {
				String tag = in.nextLine();
				if (tag.startsWith("#")) continue;
				tags.add(tag);
				
				tag = tag.replaceAll("_", " ");
				tag = tag.replaceAll("-", " ");
				String[] words = tag.split("[ ]");
				for (String word: words) {
					terms.add(word);
				}
			}
			in.close();

		} catch (Exception e) {
			System.err.println("Error: couldn't read in the tags");
			e.printStackTrace();
		}
		
		try {
			String synFile = props.getProperty("wikipedia.synonymFile");
			
			Scanner in = new Scanner(new File(synFile));
			while (in.hasNext()) {
				String line = in.nextLine();
				if (line.startsWith("#")) continue;
				String term = line.substring(0, line.indexOf(":"));
				String rest = line.substring(line.indexOf(":")+1);
				String[] syns = rest.split("[,]");
				
				HashSet<String> set = new HashSet<>();
				for (String syn: syns) {
					set.add(syn.trim());
				}
				
				synonyms.put(term, set);
			}
			in.close();
			
		} catch (Exception e) {
			System.err.println("Error: couldn't read in the synonyms");
			e.printStackTrace();
		}

		try {
			cacheFile = props.getProperty("wikipedia.cacheFile");
			ObjectInputStream ois = new ObjectInputStream(new FileInputStream(
					new File(cacheFile)));
			wikiCache = (HashMap<String, String>) ois.readObject();
			ois.close();
		} catch (Exception e) {
			System.err.println("No Wikipedia cache found -- proceeding without it");
		}
	}

  
  public void annotate(Annotation annotation) {
	  
	  for (CoreLabel word : annotation.get(CoreAnnotations.TokensAnnotation.class)) {
		  
		  String text = word.get(CoreAnnotations.TextAnnotation.class).toLowerCase().trim();
		  String pos = word.get(CoreAnnotations.PartOfSpeechAnnotation.class);
		  String result = null;

		  // if the word is a noun or proper noun
		  if (pos.equals("NNP") && !terms.contains(text)) {
			  
			  // find the most relevant Wikipedia page
			  String bestTag = "";
			  float highestRelevancy = 0;
			  
			  String query = text;
			  if (wikiCache.containsKey(query)) {
				  result = wikiCache.get(query);
				  
			  } else {
				  try {
					  PyObject pyResult = wikiQueryFunction.__call__(new PyString(query));
					  result = (String) pyResult.__tojava__(String.class);
					  wikiCache.put(query, result);
				  } catch (Exception e) {
					  System.err.println(e.getMessage());
					  
					  try {
						  ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(
								  new File(cacheFile)));
						  oos.writeObject(wikiCache);
						  oos.close();
					  } catch (Exception e1) {
						  System.err.println("Unable to update the Wikipedia cache file.");
						  e1.printStackTrace();
					  }
				  }
			  }
			  
			  if (result == null) continue;
			  
			  for (String tag: tags) {
				  
				  String cleanTag = tag.replaceAll("_", " ");
				  cleanTag = cleanTag.replaceAll("-", " ");
				  
				  // chooses the most relevant tag
				  float relevancy = howRelevant(result, cleanTag);
				  
				  if (relevancy > highestRelevancy) {
					  highestRelevancy = relevancy;
					  bestTag = tag;
				  }
			  }
			  
			  if (highestRelevancy > 0) {
				  word.set(CoreAnnotations.NamedEntityTagAnnotation.class, bestTag);			  
			  }
		  }		  
	  }

	  try {
		  ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(
				  new File(cacheFile)));
		  oos.writeObject(wikiCache);
		  oos.close();
	  } catch (Exception e) {
		  System.err.println("Unable to update the Wikipedia cache file.");
		  e.printStackTrace();
	  }
  }

  
  private float howRelevant(String article, String text) {
	  
	  float relevant = 0;
	  int minIndex = article.length();
	  
	  article = article.toLowerCase();

	  String[] tokens = text.split("[ ]");
	  
	  HashSet<String> wordsToCheckFor = new HashSet<>();
	  for (String token: tokens) {
		  wordsToCheckFor.add(token);
		  if (synonyms.containsKey(token)) {
			  wordsToCheckFor.addAll(synonyms.get(token));
		  }
	  }

	  for (String token: wordsToCheckFor) {
		  
		  if (article.contains(token.toLowerCase())) {
			  relevant++;
		  }

		  int index = article.indexOf(" " + token);

		  if (index > -1 && index < minIndex) {
			  minIndex = index;
		  }

		  index = article.indexOf("(" + token);

		  if (index > -1 && index < minIndex) {
			  minIndex = index;
		  }
	  }

	  if (article.length() > 0) {
		  relevant -= tokens.length * (minIndex / ((float) (article.length())));
	  }

	  return relevant;
  }
  
  
  @SuppressWarnings("rawtypes")
  @Override
  public Set<Class<? extends CoreAnnotation>> requires() {
	  return Collections.unmodifiableSet(new ArraySet<>(Arrays.asList(
			  CoreAnnotations.TextAnnotation.class,
			  CoreAnnotations.TokensAnnotation.class,
			  CoreAnnotations.SentencesAnnotation.class,
			  CoreAnnotations.PartOfSpeechAnnotation.class
			  )));
  }

  @SuppressWarnings("rawtypes")
  @Override
  public Set<Class<? extends CoreAnnotation>> requirementsSatisfied() {
	  return new HashSet<>();
  }
  
}