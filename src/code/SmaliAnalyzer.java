package code;

import code.io.Arguments;

import java.io.*;
import java.util.*;

public class SmaliAnalyzer {

	private Arguments arguments;

	private ConfigFile configFile;

	private List<String> projectRoots = new ArrayList<>();

	public SmaliAnalyzer(Arguments arguments) {
		this.arguments = arguments;
	}

	private Map<String, Set<String>> dependencies = new HashMap<>();

	public Map<String, Set<String>> getDependencies() {
		return dependencies;
	}

	public boolean run() throws IOException {
		if (arguments.configFile != null) {
			configFile = new ConfigFile(arguments.configFile);
		} else {
			configFile = new ConfigFile();
			configFile.packages = Arrays.asList(arguments.filter.split(","));
		}

		List<File> projectFolder = getProjectFolders(configFile.packages);
		if (!projectFolder.isEmpty()) {
			traverseSmaliCode(projectFolder);
			return true;
		} else if (isInstantRunEnabled()) {
			System.err.println("Enabled Instant Run feature detected. We cannot decompile it. Please, disable Instant Run and rebuild your app.");
		} else {
			System.err.println(arguments.filter == null ? "Smali folder cannot be absent!" : "Please check your filter!");
		}
		return false;
	}

	private List<File> getProjectFolders(List<String> packages) {
		File projectFile = new File(arguments.projectPath);

		String[] projectRoots = {
				projectFile.getAbsolutePath() + File.separator + "smali",
				projectFile.getAbsolutePath() + File.separator + "smali_classes2",
		};
		this.projectRoots = Arrays.asList(projectRoots);

		List<File> includePaths = new ArrayList<>();
		if (packages.size() > 0) {
			for (String projectRoot : projectRoots) {

				for (String packageName : packages) {
					String includePath = projectRoot;
					String[] pathElements = packageName.split("\\.");
					for (String element : pathElements) {
						includePath += File.separator + element;
					}

					File file = new File(includePath);
					if (file.exists()) {
						includePaths.add(file);
					}
				}

			}
		} else {
			for (String projectRoot : projectRoots) {
				File file = new File(projectRoot);
				if (file.exists()) {
					includePaths.add(file);
				}
			}
		}

		return includePaths;
	}

	private boolean isInstantRunEnabled() {
		File unknownFolder = new File(arguments.projectPath + File.separator + "unknown");
		if (unknownFolder.exists()) {
			for (File file : unknownFolder.listFiles()) {
				if (file.getName().equals("instant-run.zip")) {
					return true;
				}
			}

		}
		return false;
	}

	private void traverseSmaliCode(List<File> folders) {
		for (File folder : folders) {
			File[] listOfFiles = folder.listFiles();
			for (int i = 0; i < listOfFiles.length; i++) {
				if (listOfFiles[i].isFile()) {
					if (listOfFiles[i].getName().endsWith(".smali")) {
						processSmaliFile(listOfFiles[i]);
					}
				} else if (listOfFiles[i].isDirectory()) {
					traverseSmaliCode(Collections.singletonList(listOfFiles[i]));
				}
			}
		}
	}

	private void processSmaliFile(File file) {
		try (BufferedReader br = new BufferedReader(new FileReader(file))) {

			String fileName = file.getName().substring(0, file.getName().lastIndexOf("."));
			if (CodeUtils.isClassR(fileName)) {
				return;
			}

			if (CodeUtils.isClassAnonymous(fileName)) {
				fileName = CodeUtils.getAnonymousNearestOuter(fileName);
			}

			Set<String> classNames = new HashSet<>();
			Set<String> dependencyNames = new HashSet<>();

			for (String line; (line = br.readLine()) != null;) {
				try {
					classNames.clear();

					parseAndAddClassNames(classNames, line);

					// filtering
					for (String fullClassName : classNames) {
						if (fullClassName != null && configFile.isIgnoring(false, fullClassName.replace(File.separator, "."))) {
							String simpleClassName = getClassSimpleName(fullClassName);
							if (isClassOk(simpleClassName, fileName)) {

								dependencyNames.add(simpleClassName);
							}
						}
					}
				} catch (Exception e) {
					// e.printStackTrace();
				}
			}

			// inner/nested class always depends on the outer class
			if (CodeUtils.isClassInner(fileName)) {
//				System.out.println("fileName = " + fileName);
				dependencyNames.add(CodeUtils.getOuterClass(fileName));
			}

			if (!dependencyNames.isEmpty()) {
				for (String root : this.projectRoots) {
					if (file.getAbsolutePath().startsWith(root + File.separatorChar)) {
						String path = file.getAbsolutePath().replace(File.separatorChar, '.');
						String className = path.substring(root.length() + 1);

						if (!configFile.isIgnoring(true, className.replace(File.separator, "."))) {
							return;
						}
					}
				}
				addDependencies(fileName, dependencyNames);
			}
		} catch (FileNotFoundException e) {
			System.err.println("Cannot found " + file.getAbsolutePath());
		} catch (IOException e) {
			System.err.println("Cannot read " + file.getAbsolutePath());
		}
	}

	private String getClassSimpleName(String fullClassName) {
		String simpleClassName = fullClassName.substring(fullClassName.lastIndexOf("/") + 1,
				fullClassName.length());
		int startGenericIndex = simpleClassName.indexOf("<");
		if (startGenericIndex != -1) {
			simpleClassName = simpleClassName.substring(0, startGenericIndex);
		}
		return simpleClassName;
	}

	/**
	 * The last filter. Do not show anonymous classes (their dependencies belongs to outer class), 
	 * generated classes, avoid circular dependencies, do not show generated R class
	 * @param simpleClassName class name to inspect
	 * @param fileName full class name
	 * @return true if class is good with these conditions
	 */
	private boolean isClassOk(String simpleClassName, String fileName) {
		return !CodeUtils.isClassAnonymous(simpleClassName) && !CodeUtils.isClassGenerated(simpleClassName)
				&& !fileName.equals(simpleClassName) && !CodeUtils.isClassR(simpleClassName);
	}

	private void parseAndAddClassNames(Set<String> classNames, String line) {
		int index = line.indexOf("L");
		while (index != -1) {
			int colonIndex = line.indexOf(";", index);
			if (colonIndex == -1) {
				break;
			}

			String className = line.substring(index + 1, colonIndex);
			if (className.matches("[\\w\\d/$<>]*")) {
				int startGenericIndex = className.indexOf("<");
				if (startGenericIndex != -1 && className.charAt(startGenericIndex + 1) == 'L') {
					// generic
					int startGenericInLineIndex = index + startGenericIndex + 1; // index of "<" in the original string
					int endGenericInLineIndex = getEndGenericIndex(line, startGenericInLineIndex);
					String generic = line.substring(startGenericInLineIndex + 1, endGenericInLineIndex);
					parseAndAddClassNames(classNames, generic);
					index = line.indexOf("L", endGenericInLineIndex);
					className = className.substring(0, startGenericIndex);
				} else {
					index = line.indexOf("L", colonIndex);
				}
			} else {
				index = line.indexOf("L", index+1);
				continue;
			}

			classNames.add(className);
		}
	}

	private int getEndGenericIndex(String line, int startGenericIndex) {
		int endIndex = line.indexOf(">", startGenericIndex);
		for (int i = endIndex + 2; i < line.length(); i += 2) {
			if (line.charAt(i) == '>') {
				endIndex = i;
			}
		}
		return endIndex;
	}

	private void addDependencies(String className, Set<String> dependenciesList) {
		Set<String> depList = dependencies.get(className);
		if (depList == null) {
			// add this class and its dependencies
			dependencies.put(className, dependenciesList);
		} else {
			// if this class is already added - update its dependencies
			depList.addAll(dependenciesList);
		}
	}
}
