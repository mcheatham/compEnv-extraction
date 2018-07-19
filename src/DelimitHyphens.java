import java.io.File;
import java.io.PrintWriter;
import java.util.Scanner;


// This is a kludge because I can't get the Stanford parser to split on hyphens
public class DelimitHyphens {
	
	public static void main(String[] args) throws Exception {
		
		File inDir = new File("./data/key_paragraphs");
		File outDir = new File("./data/key_paragraphs_split");
		
		for (File f: inDir.listFiles()) {
			
			if (!f.getName().endsWith(".key")) continue;
			
			Scanner in = new Scanner(f);
			String fileContents = "";
			
			while (in.hasNext()) {
				fileContents += in.nextLine();
			}
			in.close();
			
			fileContents = fileContents.replaceAll("-", "\n-\n");
			
			PrintWriter out = new PrintWriter(outDir.getAbsolutePath() + "/" + f.getName());
			out.println(fileContents);
			out.close();
		}
	}

}
