package nro.models.utils;

import java.io.IOException;

public class FileRunner {

    public static void runBatchFile(String batchFilePath) throws IOException {
        ProcessBuilder processBuilder = new ProcessBuilder("cmd", "/c", "start", batchFilePath);
        processBuilder.start();
    }

}
