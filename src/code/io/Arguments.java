package code.io;

public class Arguments {

	public String projectPath;
	public String resultPath;
	public String filter;
	public String configFile;

	public Arguments(String projectPath, String resultPath, String filter, String configFile) {
		super();
		this.projectPath = projectPath;
		this.resultPath = resultPath;
		this.filter = filter;
		this.configFile = configFile;
	}
}
