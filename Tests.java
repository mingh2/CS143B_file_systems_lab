import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;


public class Tests {
    // we write a bunch of separate static methods
    // for our unit tests instead of using junit
    
    private Driver driver;
    
    public Tests() {
        
    }
    
    public void testWriteFileMax() {
        String inputFile = 
                "/Users/lucas/Dropbox/coding/java_space/CS143B_FileSystems/src/tests/write_max_test.txt";
        String outputFile = inputFile + ".OUTPUT";
        this.driver = new Driver(inputFile, outputFile);
        try {
            driver.runFile();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    
    public void testWriteFileMaxRestore() {
        String inputFile = 
                "/Users/lucas/Dropbox/coding/java_space/CS143B_FileSystems/src/tests/write_max_restore_test.txt";
        String outputFile = inputFile + ".OUTPUT";
        this.driver = new Driver(inputFile, outputFile);
        try {
            driver.runFile();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    
    public void testOpenAndDestroy() {
        String inputFile = 
                "/Users/lucas/Dropbox/coding/java_space/CS143B_FileSystems/src/tests/open_close_test.txt";
        String outputFile = inputFile + ".OUTPUT";
        this.driver = new Driver(inputFile, outputFile);
        try {
            driver.runFile();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    
    /**
     * Our sanity check test, has assertion.
     */
    public void testVERIFY() {
        String inputFile = 
                "/Users/lucas/Dropbox/coding/java_space/CS143B_FileSystems/src/tests/exam.txt";
        String outputFile = inputFile + ".OUTPUT";
        String inputFile2 = 
                "/Users/lucas/Dropbox/coding/java_space/CS143B_FileSystems/src/tests/richards_test.txt";
        String outputFile2 = inputFile2 + ".OUTPUT";
        
        this.driver = new Driver(inputFile, outputFile);
        try {
            driver.runFile();
        } catch (IOException e) {
            e.printStackTrace();
        }
        
        this.driver = new Driver(inputFile2, outputFile2);
        try {
            driver.runFile();
        } catch (IOException e) {
            e.printStackTrace();
        }
        
        String verify1 = readFile("/Users/lucas/Dropbox/coding/java_space/CS143B_FileSystems/src/tests/exam.txt.VERIFIED");
        String verify2 = readFile("/Users/lucas/Dropbox/coding/java_space/CS143B_FileSystems/src/tests/richards_test.txt.VERIFIED");

        String outputStr1 = readFile(outputFile);
        String outputStr2 = readFile(outputFile2);
        
        if (verify1.equals(outputStr1)) {
            System.out.println("**TEST ONE PASSED");
        } 
        else {
            System.out.println("**TEST ONE FAILED");
        }
        if (verify2.equals(outputStr2)) {
            System.out.println("**TEST TWO PASSED");
        } 
        else {
            System.out.println("**TEST TWO FAILED");
        }
    }
    
    @SuppressWarnings("resource")
    public String readFile(String filename) {
        StringBuilder sb = new StringBuilder();
        BufferedReader br = null;
        try {
            br = new BufferedReader(new FileReader(filename));
        } catch (FileNotFoundException e1) {
            e1.printStackTrace();
        }
        String line;

        try {
            while ((line = br.readLine()) != null) {
                sb.append(line);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        String finalStr = sb.toString();
        return finalStr.trim();
    }
    
    public static void main(String[] args) {
        // FileSystem fileSystem = new FileSystem();
        Tests t = new Tests();
        t.testVERIFY();
        // t.testOpenAndDestroy();
        // t.testWriteFileMaxRestore();
    }
}
