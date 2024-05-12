package mcpatch;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.instrument.Instrumentation;
import java.net.URL;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;

public class Mcpatch2Loader {
    public static void main(String[] args) throws IOException, InterruptedException {
        entrance();
    }

    public static void premain(String args, Instrumentation ins) throws IOException, InterruptedException {
        entrance();
    }

    static void entrance() throws IOException, InterruptedException {
        // 获取自己Jar文件位置
        File jarFile = getJarPath();

        if (jarFile == null)
            throw new RuntimeException("failed to get the path of self jar-file");

        // 读取启动列表文件
        File startListFile = new File(jarFile.getParentFile(), "startlist.txt");
        String startListPath = startListFile.getAbsolutePath();

        // 创建一个空的启动列表文件
        if (!startListFile.exists()) {
            try {
                //noinspection ResultOfMethodCallIgnored
                startListFile.createNewFile();
            } catch (IOException e) {
                throw new RuntimeException("failed to create the file: " + startListPath, e);
            }
        }

        // 加载启动列表文件
        List<String> content;

        try {
            content = Files.readAllLines(startListFile.toPath());
        } catch (IOException e) {
            throw new RuntimeException("failed to read the file: " + startListPath, e);
        }

        // 获取第一个可用的exe文件路径
        File exeFile = getExeFile(content, startListPath, jarFile);

        System.out.println("mcpatch-executable is " + exeFile.getAbsolutePath());

        // 准备启动进程
        ProcessBuilder pb = new ProcessBuilder(exeFile.getAbsolutePath());
        pb.redirectErrorStream(true);

        Process process = pb.start();

        // 捕获stdout并解码
        new Thread(() -> {
            InputStreamReader reader = new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8);
            BufferedReader input = new BufferedReader(reader);

            while (true) {
                String line;

                try {
                    line = input.readLine();
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }

                if (line == null)
                    break;

                System.out.println(line);
            }
        }).start();

        // 等待mcpatch退出
        int exitCode = process.waitFor();

        System.out.println("mcpatch returns: " + exitCode);

        // 如果返回值不是0则崩掉游戏
        if (exitCode != 0) {
            throw new RuntimeException("DLL returns " + exitCode + " as exitcode, it's not 0 as expected.");
        }
    }

    /**
     * 获取exe文件的路径
     * @param content 启动列表文件的内容
     * @param startListPath 启动列表文件路径
     * @param jarFile 自己Jar文件
     */
    private static File getExeFile(List<String> content, String startListPath, File jarFile) {
        if (content.isEmpty()) {
            throw new RuntimeException("the file can not be empty: " + startListPath);
        }

        // 寻找第一个存在的文件
        String exeName = "";

        for (String line : content) {
            String name = line.trim();
            File file = new File(jarFile.getParentFile(), name);

            if (file.exists())
            {
                exeName = name;
                break;
            }
        }

        if (exeName.isEmpty())
            throw new RuntimeException("no startable dll found in start");

        return new File(jarFile.getParentFile(), exeName);
    }

    /**
     * 获取自己Jar的路径
     */
    static File getJarPath() {
        URL resource = Mcpatch2Loader.class.getResource("");

        if (resource != null && resource.getProtocol().equals("file"))
            return null;

        try {
            URL location = Mcpatch2Loader.class.getProtectionDomain().getCodeSource().getLocation();
            String url = URLDecoder.decode(location.getPath(), "UTF-8").replace("\\", "/");

            if (url.endsWith(".class") && url.contains("!")) {
                String path = url.substring(0, url.lastIndexOf("!"));

                if (path.contains("file:/"))
                    path = path.substring(path.indexOf("file:/") + "file:/".length());

                return new File(path);
            } else {
                return new File(url);
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to decode or process URL", e);
        }
    }
}
