import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintWriter;

// Lucas Ou-Yang
// #27404511

public class Driver {
	/**
	 * Provides clean interface for a user to operate
	 * shell commands onto the file system. 
	 * Initialize and takes commands in the form of 
	 * string inputs.
	 */
	
	public static final String ERROR_OUTPUT = "error";
	public static final String FAIL_MSG = "ERROR COMMAND ";
	public static final String OUTPUT_FILE = "output.txt";
	
	FileSystem fileSystem;
	
	public Driver() {
		this.fileSystem = new FileSystem();
	}
	
	public byte[] stringToBytes(String input, int size) {
		if (input.length() > size) {
			input = input.substring(0, size);
		}
		byte[] bytes = input.getBytes();
		return bytes;
	}
	
	public void saveToFile(String output) {
		PrintWriter out = null;
		try {
			out = new PrintWriter(OUTPUT_FILE);
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		}		
		out.write(output);
		out.close();
	}
	
	public String feedLine(String input) {
		if (input == null) {
			return "";
		}
		input = input.trim();
		// commands are 2 chars
		String handle = input.substring(0, 2); 
		String[] chunkedInput = input.split(" ");
		
		// fail, not enough chars
		if (input.length() < 2) {
			return "";
		}
		System.out.println("RUNNING: " + input); // TODO: Debug
		
		if (handle.equals("cr") && chunkedInput.length == 2) {
			String filename = chunkedInput[1];
			byte[] byteFilename = stringToBytes(filename, 4);
			int status = this.fileSystem.create(byteFilename);
			if (status == FileSystem.COMMAND_FAIL ||
					status == FileSystem.DUPLICATE_FILE_ERR) {
				return ERROR_OUTPUT;
			}
			else if (status == FileSystem.COMMAND_SUCCEED) {
				return filename + " created";
			}
		}
		else if (handle.equals("de") && chunkedInput.length == 2) {
			String filename = chunkedInput[1];
			byte[] byteFilename = stringToBytes(filename, 4);
			int status = this.fileSystem.destroy(byteFilename);
			if (status == FileSystem.COMMAND_FAIL) {
				return ERROR_OUTPUT;
			}
			else if (status == FileSystem.COMMAND_SUCCEED) {
				return filename + " destroyed";
			}
		}
		else if (handle.equals("op") && chunkedInput.length == 2) {
			String filename = chunkedInput[1];
			byte[] byteFilename = stringToBytes(filename, 4);
			int oftIndex = this.fileSystem.open(byteFilename);
			if (oftIndex == FileSystem.COMMAND_FAIL) {
				return ERROR_OUTPUT;
			}
			else {
				return filename + " opened " + Integer.toString(oftIndex);
			}
		}
		else if (handle.equals("cl") && chunkedInput.length == 2) {
			int oftIndex = Integer.parseInt(chunkedInput[1]);
			int status = this.fileSystem.close(oftIndex);
			if (status == FileSystem.COMMAND_FAIL) {
				return FAIL_MSG;
			}
			else if (status == FileSystem.COMMAND_SUCCEED) {
				return Integer.toString(oftIndex) + " closed";
			}
		}
		else if (handle.equals("rd") && chunkedInput.length == 3) {
			int oftIndex = Integer.parseInt(chunkedInput[1]);
			int countBytes = Integer.parseInt(chunkedInput[2]);
			byte[] output = this.fileSystem.readFile(oftIndex, countBytes);
			return new String(output);
		}
		else if (handle.equals("wr") && chunkedInput.length == 4) {
			int oftIndex = Integer.parseInt(chunkedInput[1]);
			byte character = chunkedInput[2].getBytes()[0];
			int count = Integer.parseInt(chunkedInput[3]);
			int status = this.fileSystem.writeFile(oftIndex, character, count);
			if (status == FileSystem.COMMAND_FAIL) {
				return FAIL_MSG;
			}
			else if (status == FileSystem.COMMAND_SUCCEED) {
				return Integer.toString(count) + " bytes written";
			}
		}
		else if (handle.equals("sk") && chunkedInput.length == 3) {
			int oftIndex = Integer.parseInt(chunkedInput[1]);
			int position = Integer.parseInt(chunkedInput[2]);
			int status = this.fileSystem.lseek(oftIndex, position);
			if (status == FileSystem.COMMAND_FAIL) {
				return FAIL_MSG;
			}
			else if (status == FileSystem.COMMAND_SUCCEED) {
				return "position is " + Integer.toString(position);
			}
		}
		else if (handle.equals("dr") && chunkedInput.length == 1) {
			String output = this.fileSystem.ls();
			return output;
		}
		else if (handle.equals("in") && (chunkedInput.length == 2 || 
				chunkedInput.length == 1)) {
			
			String output = "";
			try {
				output = this.fileSystem.init(chunkedInput);
			} catch (IOException e) {
				e.printStackTrace();
				return ERROR_OUTPUT;
			}
			return output;
		}
		else if (handle.equals("sv") && chunkedInput.length == 2) {
			String filename = chunkedInput[1];
			try {
				this.fileSystem.save(filename);
			} catch (FileNotFoundException e) {
				e.printStackTrace();
			}
			return "disk saved";
		}
		return ERROR_OUTPUT;
	}
	
	public void feedFile(String filename) throws IOException {
		StringBuilder sb = new StringBuilder();
		BufferedReader br = new BufferedReader(new FileReader(filename));
		String line;

		while ((line = br.readLine()) != null) {
			String output = this.feedLine(line);
			System.out.println(">>>>>>>>>>> " + output); // TODO: Debug
			this.fileSystem.printDisk();
			sb.append(output);
			sb.append("\r\n");
		}
		br.close();
		// TODO: Maybe kill trailing newlines?
		String output = sb.toString();
		this.saveToFile(output);
		System.out.println(output);
	}
	
	public static void main(String[] args) {
		String filename = "/Users/lucas/Dropbox/coding/java_space/CS143B_FileSystems/src/tests/input.txt";
		Driver driver = new Driver();
		try {
			driver.feedFile(filename);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
}
