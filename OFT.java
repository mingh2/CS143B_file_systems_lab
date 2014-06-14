import java.util.Arrays;

public class OFT {
    /**
     * Open File Table abstraction.
     */

    // directory + up to 3 open files
    public static final int OFT_MAX_SIZE = 4;
    // r/w buffer + pos + descriptor index + length of file
    public static final int OFT_ENTRY_SIZE = 64 + 4 + 4 + 4;

    private byte[][] table;
    private IO io;
    private BitMap bitmap;

    public OFT(IO io, BitMap bitmap) {
        this.io = io;
        this.bitmap = bitmap;
        this.table = new byte[OFT_MAX_SIZE][OFT_ENTRY_SIZE];

        // for every entry in the OFT
        for (int i = 0; i < this.table.length; i++) {
            // for every byte in the r/w buffer
            // (nothing else! we keep other stuff init at 0)
            for (int j = 0; j < 64; j++) {
                this.table[i][j] = IO.EMPTY_BYTE;
            }
        }
        // init directory "file"
        this.openDirectory();
    }

    public void printTable() {
        System.out.println("~~~~~~~~~~OFT~~~~~~"
                + "[buffer, pos, desc, length]~~~~");
        
        for (int i = 0; i < this.table.length; i++) {
            System.out.print("|OFT-ROW|");
            for (int j = 0; j < this.table[i].length; j++) {
                System.out.print(" " + this.table[i][j]);
            }
            System.out.print("\r\n");
        }
    }

    public void openDirectory() {
        // oftIndex, val
        this.setPosition(FileSystem.DIRECTORY_OFT_INDEX, 0);
        this.setDescriptorNumb(FileSystem.DIRECTORY_OFT_INDEX, 0);
        this.setFileLength(FileSystem.DIRECTORY_OFT_INDEX, 0);
    }

    public int getFreeOFTEntry() {
        int entryIndex = bitmap.closestOFTEntry();
        if (entryIndex == BitMap.BITMAP_ERR) {
            return FileSystem.OUT_OF_MEMORY_ERR;
        }
        return entryIndex;
    }

    public int getOFTIndexFromDescriptor(int descNumb) {
        for (int i = 1; i <= 3; i++) {
            int curNumb = this.getDescriptorNumb(i);
            if (curNumb == descNumb) {
                return i;
            }
        }
        return FileSystem.COMMAND_FAIL;
    }

    /**
     * re-use the 3 slots open for files indices (1-3)
     */
    public void clearEntry(int entryIndex) {
        for (int i = 0; i < OFT_ENTRY_SIZE; i++) {
            this.table[entryIndex][i] = IO.EMPTY_BYTE;
        }
        bitmap.setZero(entryIndex);
    }

    /**
     * don't allow access to buffers, use this API
     */
    public void copyToTable(int entryIndex, int pos, byte val) {
        int normalizedPos = this.posToNewBlockPos(pos);
        this.table[entryIndex][normalizedPos] = val;
    }

    public byte copyFromTable(int entryIndex, int pos) {
        int normalizedPos = this.posToNewBlockPos(pos);
        return this.getBuffer(entryIndex)[normalizedPos];
    }

    /**
     * write contents of buffer into disk
     */
    public void writeBuffer(int entryIndex, int blockNumb, FileSystem fs) {
        // edge case for when we write till the end
        if (blockNumb == FileSystem.MAX_FILE_BLOCKS + 1) {
            blockNumb -= 1;
        }
        if (blockNumb <= FileSystem.MAX_FILE_BLOCKS) {
            byte[] buffer = this.getBuffer(entryIndex);
            int descNumb = this.getDescriptorNumb(entryIndex);
            int ldiskBlockIndex = fs.blockNumToBlockIndex(descNumb, blockNumb);
    
            // if we go overboard, create a new data block
            if (ldiskBlockIndex == -1) {
                ldiskBlockIndex = fs.newDescriptorBlock(descNumb);
            }
            // perhaps update filelength here?
            // int fileLength = this.getFileLength(entryIndex);
            io.writeBlock(ldiskBlockIndex, buffer);
        }
    }

    /**
     * read contents of ldisk into buffer
     */
    public void readBuffer(int entryIndex, int blockNumb, FileSystem fs) {
        int descNumb = this.getDescriptorNumb(entryIndex);
        int ldiskBlockIndex = fs.blockNumToBlockIndex(descNumb, blockNumb);

        // if we go overboard, create a new data block
        if (ldiskBlockIndex == -1) { // error code
            ldiskBlockIndex = fs.newDescriptorBlock(descNumb);
        }
        byte[] data = io.readBlock(ldiskBlockIndex);
        this.setBuffer(entryIndex, data);
    }

    /**
     * useful for when we need to save all contents of OFT
     * back into ldisk (for example when we are saving the fs)
     */
    public void saveEverything(FileSystem fs) {
        for (int entryIndex=0; entryIndex < this.table.length; entryIndex++) {
            int blockNumb = this.getBlockNumb(entryIndex);
            this.writeBuffer(entryIndex, blockNumb, fs);
        }
    }
    
    /**
     * r/w buffer is 64 bytes long, if we go overboard that means we've moved to
     * the next block
     */
    public int posToNewBlockPos(int position) {
        return (position % 64);
    }

    public int posToBlockIndex(int position) {
        return (position / 64);
    }

    public int getBlockNumb(int oftIndex) {
        int pos = this.getPosition(oftIndex);
        int blockIndex = this.posToBlockIndex(pos);
        int blockNum = blockIndex + 1;
        return blockNum;
    }

    public void setBuffer(int entryIndex, byte[] readFrom) {
        for (int i = 0; i < readFrom.length; i++) {
            this.table[entryIndex][i] = readFrom[i];
        }
    }

    /**
     * if our cursor goes over the edge, keep it at the very
     * end
     */
    public void setPosition(int entryIndex, int posVal) {
        if (posVal > FileSystem.MAX_FILESIZE) {
            posVal = FileSystem.MAX_FILESIZE; // TODO:::
        }
        Utils.intPack(this.table[entryIndex], posVal, 64);
    }

    public void setDescriptorNumb(int entryIndex, int descVal) {
        Utils.intPack(this.table[entryIndex], descVal, 68);
    }

    public void setFileLength(int entryIndex, int fileLenVal) {
        Utils.intPack(this.table[entryIndex], fileLenVal, 72);
    }

    //
    //

    /**
     * Careful when using this, don't use this to modify the buffer as it's a
     * copy! Not a reference.
     */
    public byte[] getBuffer(int entryIndex) {
        return Arrays.copyOfRange(this.table[entryIndex], 0, 64);
    }

    public int getPosition(int entryIndex) {
        return Utils.intUnpack(this.table[entryIndex], 64);
    }

    public int getDescriptorNumb(int entryIndex) {
        return Utils.intUnpack(this.table[entryIndex], 68);
    }

    public int getFileLength(int entryIndex) {
        return Utils.intUnpack(this.table[entryIndex], 72);
    }
}
