package code;


import code.io.ArgumentReader;
import code.io.Arguments;
import code.io.Writer;

import java.io.File;
import java.io.IOException;

public class Main {
	
	public static void main(String[] args) throws IOException {
		Arguments arguments = new ArgumentReader(args).read();
		if (arguments == null) {
			return;
		}

		File resultFile = new File(arguments.resultPath);
		SmaliAnalyzer analyzer = new SmaliAnalyzer(arguments);
		if (analyzer.run()) {
			new Writer(resultFile).write(analyzer.getDependencies());
			System.out.println("Success! Now open index.html in your browser.");
		}
	}
}