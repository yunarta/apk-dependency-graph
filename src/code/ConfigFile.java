package code;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Created by yunarta on 4/10/16.
 */
public class ConfigFile {

    public List<String> packages = new ArrayList<>();

    public List<String> ignores = new ArrayList<>();

    public List<String> ignoreNames = new ArrayList<>();

    public List<String> ignoreRegex = new ArrayList<>();

    public List<Pattern> ignoreRegex2 = new ArrayList<>();

    public Pattern fileFilter = Pattern.compile("([^\\$]*)[\\$]?.*");

    public ConfigFile() {
    }

    public ConfigFile(String configFile) throws IOException {
        File file = new File(configFile);
        BufferedReader reader = new BufferedReader(new FileReader(file));

        String line = null;
        while (null != (line = reader.readLine())) {
            if ("".equals(line)) continue;
            if (line.startsWith("#")) continue;

            if (line.startsWith("!!!")) {
                ignoreRegex.add(line.substring(3));
            } else if (line.startsWith("!!")) {
                ignoreNames.add(line.substring(2));
            } else if (line.startsWith("!")) {
                ignores.add(line.substring(1));
            } else if (line.startsWith("@")) {
                System.out.println("line.substring(1) = " + line.substring(1));
                ignoreRegex2.add(Pattern.compile(line.substring(1)));
            } else {
                packages.add(line);
            }
        }
    }

    public boolean isIgnoring(boolean isFile, String path) {
        for (String packageName : packages) {
            if (path.startsWith(packageName)) {
                for (String name : ignores) {
                    if (path.startsWith(name)) {
                        return false;
                    }
                }

                if (path.endsWith(".smali")) {
                    path = path.substring(0, path.length() - 6);
                }

                if (path.lastIndexOf("<") != -1) {
                    path = path.substring(0, path.lastIndexOf("<"));
                }

                if (isFile) {
                    Matcher matcher = fileFilter.matcher(path);
                    if (matcher.find()) {
                        path = matcher.group(1);
                    }
                }

                for (Pattern name : ignoreRegex2) {
                    if (name.matcher(path).find()) {
                        return false;
                    }
                }

                return true;
            }
        }

        return false;
    }
}
