package graphlod.output;

import com.google.common.base.Charsets;
import com.google.common.io.Files;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;

public class HtmlDocument {
    private BufferedWriter out;

    public HtmlDocument(String fileName) throws IOException {
        File file = new File(fileName);
        Files.createParentDirs(file);
        out = Files.newWriter(file, Charsets.UTF_8);
        out.close();
    }

    public void print(String text) {

    }

    public void close() throws IOException {
        out.close();
    }
}
