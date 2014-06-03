
// Lucas Ou-Yang
// #27404511

public class IO {
	/**
	 * Presents a disk as a linear sequence of blocks. Implement mainly with a
	 * byte array: ldisk[L][B], L = # of logical blocks on disk B = block length
	 * (in bytes)
	 * 
	 * The file system, instead of the driver, will be interfacing with the IO
	 * system as an abstraction.
	 * 
	 * The file system will be only able to run two commands on the IO system:
	 * readBlock(blockNum) and writeBlock(blockNum, readFrom).
	 * 
	 * No direct access to ldisk is allowed! We also must be able to write both
	 * chars and integers as we are storing both locations and text.
	 */

	public static final int NUMB_DESCRIPTOR_BLOCKS = 6;
	public static final int NUMB_DESCRIPTORS = NUMB_DESCRIPTOR_BLOCKS * 4;

	public static final int LDISK_SIZE = 64; // blocks
	public static final int BLOCK_SIZE = 64; // 64 bytes ~ 16 integers
	public static final int DIR_DESCRIPTOR_SIZE = 16; // 16 bytes ~ 4 integers
	public static final int DESCRIPTOR_SIZE = 16;
	public static final byte EMPTY_BYTE = -1;

	private byte[][] ldisk;

	public IO() {
		// second dimension is zero b/c we build "jagged" array
		this.ldisk = new byte[LDISK_SIZE][0];

		// directory descriptor
		this.ldisk[0] = new byte[DIR_DESCRIPTOR_SIZE];

		for (int i=1; i < this.ldisk.length; i++) {
			this.ldisk[i] = new byte[BLOCK_SIZE];
			// file descriptors
			if (i <= NUMB_DESCRIPTOR_BLOCKS) {
				this.ldisk[i] = new byte[BLOCK_SIZE]; // 16 bytes
			 }
			// data blocks
			 else {
				this.ldisk[i] = new byte[BLOCK_SIZE]; // 64 bytes
			 }
		}
		
		// fill the ldisk with "empties"
		for (int i=0; i < this.ldisk.length; i++) {
			for (int j=0; j < this.ldisk[i].length; j++) {
				this.ldisk[i][j] = EMPTY_BYTE;
			}
		}
	}

	/**
	 * returns index of selected block, only full block operations 
	 * are allowed on read() and write() methods.
	 */
	public byte[] readBlock(int blockIndex) {
		return this.ldisk[blockIndex];
	}

	public void writeBlock(int blockIndex, byte[] readFrom) {
		// never change the original block size
		assert(readFrom.length == this.ldisk[blockIndex].length); 
		this.ldisk[blockIndex] = readFrom;
	}
		
	/**
	 * Print out the contents of the 2-D ldisk.
	 */
	public void printDisk() {
		for (int i=0; i<this.ldisk.length; i++) {
			System.out.print("|ROW " + i + "|");
			for (int j=0; j<this.ldisk[i].length; j++) {
				System.out.print(" " + this.ldisk[i][j]);
			}
			System.out.print("\r\n");
		}
	}
}
