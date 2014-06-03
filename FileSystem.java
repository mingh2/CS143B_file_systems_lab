import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Arrays;

// Lucas Ou-Yang
// #27404511

public class FileSystem {
    /**
     * Abstraction for the file system
     */
    
    public static final int MAX_SYMBOLIC_FILENAME = 4;
    public static final int DIRECTORY_OFT_INDEX = 0;
    
    // For serializing our ldisk to file
    public static final String BLOCK_DELIMITER = "\\$\\$";
    public static final String BYTE_DELIMITER = "\\*\\*";
    public static final String BLOCK_SEP = "$$";
    public static final String BYTE_SEP = "**";
    
    // Response codes
    public static final int COMMAND_FAIL = -10;
    public static final int COMMAND_SUCCEED = -11;
    public static final int OUT_OF_MEMORY_ERR = -5;
    public static final int DUPLICATE_FILE_ERR = -99;
    
    private IO io;
    private OFT oft;
    private BitMap bitmap;

    public FileSystem() {
        this.io = new IO();
        this.oft = new OFT();
        this.bitmap = new BitMap();
        // for directory
        this.bitmap.setOne(0);
    }
    
    /**
     * debugging helper methods
     */
    public void printDisk() {
        this.io.printDisk();
        this.oft.printTable();
    }
    
    public void printBytes(byte[] arr) {
        for (byte b : arr) {
            System.out.print(b + ", ");
        }
        System.out.println("");
    }
    
    /**
     * Helper methods to read and write 4 byte integers
     * in and out of byte[] arrays
     */
    private void intPack(byte[] arr, int val, int loc) {
        final int MASK = 0xff;
        for (int i = 3; i >= 0; i--) {
            arr[loc + i] = (byte) (val & MASK);
            val = val >> 8;
        }
    }

    private int intUnpack(byte[] arr, int loc) {
        final int MASK = 0xff;
        int v = (int) arr[loc] & MASK;
        for (int i = 1; i < 4; i++) {
            v = v << 8;
            v = v | ((int) arr[loc + i] & MASK);
        }
        return v;
    }
    
    private boolean isByteFree(byte input) {
        return (input == IO.EMPTY_BYTE);
    }
    
    /**
     * returns a byte[] of specified size filled with
     * our custom null vals
     */
    private byte[] getClearBytes(int size) {
        byte[] clear = new byte[size];
        for (int i=0; i< clear.length; i++) {
            clear[i] = IO.EMPTY_BYTE;
        }
        return clear;
    }
    
    private void clearDirectoryNameEntry(int positionInDirectoryFile) {
        // skip 8 bytes at a time because a name field is
        // 4 bytes name 4 bytes (int) index
        byte[] clear = this.getClearBytes(8);
        int filePos = (positionInDirectoryFile * 8);
        this.lseek(DIRECTORY_OFT_INDEX, filePos);
        this.writeFile(DIRECTORY_OFT_INDEX, clear);
    }
    
    private void clearDataBlock(int blockIndex) {
        byte[] clear = this.getClearBytes(64);
        this.io.writeBlock(blockIndex, clear);
    }
        
    private boolean isNameBlockFree(byte[] block) {
        boolean isEmpty = (this.isByteFree(block[0]));
        return isEmpty;
    }
    
    /**
     * helper method for byte[] comparison
     * important points, for names < 4 characters
     * there will be 0's padded towards the right 
     * which we need to ignore
     */
    private boolean isByteArrEquals(byte[] a1, byte[] a2) {
        int smallestLen = Math.min(a1.length, a2.length);

        for (int i=0; i < smallestLen; i++) {
            if (a1[i] != a2[i]) {
                return false;
            }
        }
        
        // we still need to make sure that the longer file
        // has zeros padded! this edge case is for when filenames
        //  are < 4 bytes and have zeros padded at the right end
        // [f, o, o, 0] should still equal [f, o, o] but not [f, o, o, p]
        byte[] bigger = null;
        if (a1.length >= a2.length) bigger = a1;
        else bigger = a2;
    
        for (int i=smallestLen; i < bigger.length; i++) {
            if (bigger[i] != 0) {
                return false;
            }
        }
        return true;
    }
    
    /**
     * very useful when we have the block number (0,1,or 2)
     * but we need to extract out the actual block index on ldisk
     */
    private int blockNumToBlockIndex(int descIndex, int blockNum) {
        int descBlockIndex = this.descIndexToDescBlock(descIndex);
        int descBlockPos = this.descIndexToBlockPos(descIndex);
        
        int pos = ((blockNum) * 4) + descBlockPos;
        byte[] descriptor = io.readBlock(descBlockIndex);
        int blockIndex = intUnpack(descriptor, pos);
        return blockIndex;
    }
        
    /**
     * Since 4 descriptors fit on a block we need to
     * churn the block number from an index
     */
    private int descIndexToDescBlock(int index) {
        // edge case just for the directory index
        if (index == 0) {
            return 0;
        }
        
        // index: 1-24 inclusive
        int zeroBased = index - 1; // 0-23
        return (zeroBased / 4) + 1; // 1-6
    }
    
    /**
     * This refers to the actual byte position on the block,
     * packaged with the above method
     */
    private int descIndexToBlockPos(int index) {
        // edge case just for directory index
        if (index == 0) {
            return 0;
        }
        // index: 1-24 inclusive
        int zeroBased = index - 1; // 0-23
        return (zeroBased % 4) * IO.DESCRIPTOR_SIZE; // 0-3
    }
    
    private void clearFileDescriptor(int descIndex) { 
        int descBlockIndex = this.descIndexToDescBlock(descIndex);
        int descBlockPos = this.descIndexToBlockPos(descIndex);
        
        byte[] descriptor = this.io.readBlock(descBlockIndex);
        
        // clears out data blocks desc links to
        for (int blockIndex=0; blockIndex < 3; blockIndex++) {
            boolean blockExists = 
                    this.isDescriptorBlockExist(descIndex, blockIndex);
            if (blockExists) {
                int blockPos = (blockIndex + 1) * 4;
                int jumpPos = blockPos + descBlockPos;
                // grab the actual block index from the descriptor position
                int dataBlockIndex = this.intUnpack(descriptor, jumpPos);
                this.clearDataBlock(dataBlockIndex);
                // update bitmap
                this.bitmap.setZero(dataBlockIndex);
            }
        }
        
        // clears out the chunk of the descriptor block
        // where our descriptor exists
        int cleanTo = descBlockPos + IO.DESCRIPTOR_SIZE;
        for (int i=descBlockPos; i < cleanTo; i++) {
            descriptor[i] = IO.EMPTY_BYTE;
        }
        this.io.writeBlock(descBlockIndex, descriptor);
    }
    
    private void setDescriptorLength(int descIndex, int length) { 
        int descBlockIndex = this.descIndexToDescBlock(descIndex);
        int descBlockPos = this.descIndexToBlockPos(descIndex);
        byte[] descriptor = io.readBlock(descBlockIndex);
        this.intPack(descriptor, length, descBlockPos + 0);
        this.io.writeBlock(descBlockIndex, descriptor);
    }
    
    private int closestDescriptorIndex() { 
        // note <=, 1-24
        for (int descIndex=1; descIndex <= IO.NUMB_DESCRIPTORS; descIndex++) { 
            // if desc does not exist, it's free
            if (!isDescriptorExist(descIndex)) { 
                return descIndex;
            }
        }
        return FileSystem.OUT_OF_MEMORY_ERR;
    }
        
    private boolean isDescriptorExist(int descIndex) { 
        int descBlockIndex = this.descIndexToDescBlock(descIndex);
        int descBlockPos = this.descIndexToBlockPos(descIndex);
        
        byte[] descriptor = this.io.readBlock(descBlockIndex);
        byte startByte = descriptor[descBlockPos]; // it's the length
        
        return (startByte != IO.EMPTY_BYTE);
    }
    
    private boolean isDescriptorBlockExist(int descIndex, int blockIndex) { 
        int descBlockIndex = this.descIndexToDescBlock(descIndex);
        int descBlockPos = this.descIndexToBlockPos(descIndex);
        
        // note that the blockNum is 1-indexed so we inc by 1!
        int blockNum = blockIndex + 1;
        
        byte[] descriptor = this.io.readBlock(descBlockIndex);
        int jump = blockNum * 4;
        int finalBlockIndex = descBlockPos + jump; // add the base
        
        return (descriptor[finalBlockIndex] != IO.EMPTY_BYTE);
    }
    
    /**
     * returns location of the new data block
     */
    private int newDescriptorBlock(int descIndex) {
        int descBlockIndex = this.descIndexToDescBlock(descIndex);
        int descBlockPos = this.descIndexToBlockPos(descIndex);
        
        byte[] descriptor = this.io.readBlock(descBlockIndex);
        int blockIndex = 0;
        boolean isBlockExist = this.isDescriptorBlockExist(descIndex, blockIndex);
        
        // go until it does not exist
        while (isBlockExist) {
            blockIndex++;
            if (blockIndex >= 3) {
                return OUT_OF_MEMORY_ERR;
            }
            isBlockExist = this.isDescriptorBlockExist(descIndex, blockIndex);
        }       
        int indexDataBlock = this.bitmap.closestDataBlock();
        // populate bitmap
        this.bitmap.setOne(indexDataBlock);
        
        // pack our new index into the descriptor
        int blockDescriptorIndex = ((blockIndex + 1) * 4) + descBlockPos; // add the base
        this.intPack(descriptor, indexDataBlock, blockDescriptorIndex);
        
        // write it back in
        this.io.writeBlock(descBlockIndex, descriptor);
        return indexDataBlock;
    }
            
    /**
     * returns status for success or fail
     */
    public int create(byte[] filename) {
        // find empty file descriptor, only mark as taken after
        // we find a similar directory slot, otherwise return 
        // out of memory (false)
        int descIndex = this.closestDescriptorIndex(); 
        
        /*
        System.out.println("create(..): descIndex, blockIndex, blockPos: " 
            + descIndex + " " + 
            this.descIndexToDescBlock(descIndex) + 
            " " + this.descIndexToBlockPos(descIndex));
        */
        
        if (descIndex == FileSystem.OUT_OF_MEMORY_ERR) {
            return FileSystem.OUT_OF_MEMORY_ERR;
        }
        
        // find empty directory slot (8 bytes) and fill in
        // treat directory like file, seek to beginning and read
        this.lseek(DIRECTORY_OFT_INDEX, 0);
        int numNameBlocks = (64 / 8) * 3; // 24
    
        // 8 bytes (4 byte name + 4 byte integer index) 
        // at a time until find free slot
        for (int i=0; i < numNameBlocks; i++) {
            byte[] nameFile = this.readFile(DIRECTORY_OFT_INDEX, 8);
            boolean isFree = this.isNameBlockFree(nameFile);
            
            // If we encounter a filename, check to see if it matches
            // the one we are searching for, we can't create dupe files
            if (!isFree) {
                byte[] curFilename = Arrays.copyOfRange(nameFile, 0, 4);
                boolean isDupe = this.isByteArrEquals(curFilename, filename);
                if (isDupe) {
                    return DUPLICATE_FILE_ERR;
                }
            }
            // Place our slot down on the first free file directory spot
            else if (isFree) {
                // build byte array (4 bytes for name, 4 for index)
                byte[] readFrom = new byte[8];
                // write filename in
                for (int m=0; m < filename.length; m++) {
                    readFrom[m] = filename[m];
                }
                
                // write descriptor index in
                this.intPack(readFrom, descIndex, 4);
                int writeToPos = (i * 8); 
                
                // link the file descriptor with this directory space
                // by writing this new 8 byte sequence into the file                
                this.lseek(DIRECTORY_OFT_INDEX, writeToPos);
                this.writeFile(DIRECTORY_OFT_INDEX, readFrom);

                // populate empty descriptor with length of 0
                this.setDescriptorLength(descIndex, 0);

                // add block to descriptor
                this.newDescriptorBlock(descIndex);
            
                return COMMAND_SUCCEED;
            }
        }
        return COMMAND_FAIL;
    }
    
    public int destroy(byte[] inFilename) {
        // seek to beginning of directory file
        this.lseek(DIRECTORY_OFT_INDEX, 0);
        
        // 8 bytes (4 byte name + 4 byte integer index) 
        int numNameBlocks = (64 / 8) * 3; // 24
        
        // search for descriptor name
        for (int i=0; i < numNameBlocks; i++) {
            byte[] nameSlot = this.readFile(DIRECTORY_OFT_INDEX, 8);
            // first 4 bytes of slot is the filename
            byte[] filename = Arrays.copyOfRange(nameSlot, 0, 4);
            // last 4 is descriptor index
            int descIndex = this.intUnpack(nameSlot, 4);
            
            boolean isEqual = this.isByteArrEquals(filename, inFilename);
            if (isEqual) {
                this.clearDirectoryNameEntry(i);    

                // free file descriptor, including all allocated blocks
                this.clearFileDescriptor(descIndex);
                
                // return status
                return COMMAND_SUCCEED;
            }
        }
        return COMMAND_FAIL;
    }
    
    /**
     * Return Open File Table index. This "file index" is 
     * what's referred to in the next few methods.
     */
    public int open(byte[] inFilename) {
        // search directory to find index of file descriptor i
        // at a time until find free slot
        int numNameBlocks = (64 / 8) * 3; // 24
        
        // seek to beginning of directory
        this.lseek(DIRECTORY_OFT_INDEX, 0);
        
        for (int i=0; i < numNameBlocks; i++) {
            // read chunks of 8 bytes at a time from the file
            byte[] nameSlot = this.readFile(DIRECTORY_OFT_INDEX, 8);
            // first 4 bytes of slot is the filename
            byte[] filename = Arrays.copyOfRange(nameSlot, 0, 4);
                        
            int descIndex = this.intUnpack(nameSlot, 4); 
            int descBlockIndex = this.descIndexToDescBlock(descIndex);
            byte[] descriptor = io.readBlock(descBlockIndex);
            
            boolean isEqual = this.isByteArrEquals(filename, inFilename);
            
            if (isEqual) {
                // allocate free OFT entry oftIndex
                int oftIndex = this.oft.getFreeOFTEntry();
                
                if (oftIndex == OUT_OF_MEMORY_ERR) {
                    return COMMAND_FAIL;
                }
                
                // set init pos to 0
                // this.lseek(oftIndex, 0);
                this.oft.setPosition(oftIndex, 0);
                
                // fill in current pos j and descriptor index i
                this.oft.setDescIndex(oftIndex, descIndex);
                
                // read block 0 (#1) of file into r/w buffer 
                int blockOneIndex = this.intUnpack(descriptor, 4);
                byte[] blockData = io.readBlock(blockOneIndex);
                
                for (int b=0; b < blockData.length; b++) {
                    this.oft.copyToTable(oftIndex, b, blockData[b]);
                }
                
                // set the descriptor file length in bytes
                int fileLength = this.intUnpack(descriptor, 0); // get length
                this.oft.setFileLength(oftIndex, fileLength);
                
                // return OFT index j (or return error)
                return oftIndex; // succeed
            }
        }
        return COMMAND_FAIL;
    }
    
    public int close(int oftIndex) {
        int blockNum = this.oft.getBlockNum(oftIndex);
        // write buffer to disk
        this.oft.writeBuffer(oftIndex, blockNum);
        
        // extract descriptor index and the file length
        int newFileLen = this.oft.getFileLength(oftIndex);
        int descIndex = this.oft.getDescriptorIndex(oftIndex);
        
        // update file length in descriptor
        this.setDescriptorLength(descIndex, newFileLen);
        
        // free OFT entry
        this.oft.clearEntry(oftIndex);
        
        // return status
        return COMMAND_SUCCEED;
    }
    
    public byte[] readFile(int oftIndex, int goalBytes) {
        // compute latest position in the r/w buffer
        int pos = this.oft.getPosition(oftIndex);
        
        // start off index immediately where we left off
        int incIndex = 0;
        
        // bytes we plan on returning as read
        byte[] returnBytes = new byte[goalBytes];
        
        // keep track of block number
        int curBlockIndex = this.oft.posToBlockIndex(pos);
        
        // keep track of how much we've read to know when to exit
        int curNumBytes = 0;
        boolean keepReading = true;
        
        // copy from buffer to memory until
        while (keepReading) {
            int newPos = pos + incIndex;
            int newBlockIndex = this.oft.posToBlockIndex(newPos);
            
            // desired count or EOF is reached
            boolean isDesiredCount = (curNumBytes == goalBytes);
            boolean isEndOfBuffer = (newBlockIndex != curBlockIndex);
            
            if (isDesiredCount) {
                // update cur pos, return status
                this.oft.setPosition(oftIndex, newPos);
                keepReading = false; 
            }
            // end of buffer is reached
            if (isEndOfBuffer) {
                int curBlockNum = curBlockIndex + 1;
                
                // write the buffer to disk
                this.oft.writeBuffer(oftIndex, curBlockNum);
                
                // read the next block into buffer
                int newBlockNumb = newBlockIndex + 1;
                this.oft.readBuffer(oftIndex, newBlockNumb);
                
                // update to the new block number
                curBlockIndex = newBlockIndex;
            }       
            if (!keepReading) {
                return returnBytes;
            }
            // continue copying
            returnBytes[curNumBytes] = this.oft.copyFromTable(oftIndex, newPos);
            incIndex++;
            curNumBytes++;
        }
        return null; 
    }
    
    /**
     * We have 2 method signatures for the writeFile(...) method
     * because we have the option of writing using the driver
     * API (a character + a length) or just writing a byte[]
     * directly
     */
    public int writeFile(int oftIndex, byte character, int length) {
        byte[] readFrom = new byte[length];
        for (int i=0; i < length; i++) {
            readFrom[i] = character;
        }
        return this.writeFile(oftIndex, readFrom);
    }
    
    public int writeFile(int oftIndex, byte[] readFrom) {
        // compute position in r/w buffer
        int pos = this.oft.getPosition(oftIndex);
        
        // extract descIndex
        int descIndex = this.oft.getDescriptorIndex(oftIndex);
        
        // start at index immediately after where we left off in file
        int incIndex = 0;
        
        // keep track of block number
        int curBlockIndex = this.oft.posToBlockIndex(pos);
        
        // keep track of added length for total length
        int addedFileLen = 0;
        
        // if the first block does not exist, create it
        if (!this.isDescriptorBlockExist(descIndex, 0)) {
            this.newDescriptorBlock(descIndex);
        }

        boolean keepReading = true;
        
        // copy from memory into buffer until either
        // the desired count or EOF is reached
        //   - update cur pos, return status
        while (keepReading) {
            int newPos = incIndex + pos;
            int newBlockIndex = this.oft.posToBlockIndex(newPos);
            
            boolean isEOF = (incIndex >= readFrom.length);
            boolean isEndOfBuffer = (newBlockIndex != curBlockIndex);
            
            // EOF or desired count reached
            if (isEOF) {
                // don't normalize the position (or we lose valuable info)
                this.oft.setPosition(oftIndex, newPos);
                
                // update file length in descriptor and OFT
                int curFileLen = this.oft.getFileLength(oftIndex);
                int newFileLen = curFileLen + addedFileLen;
    
                this.oft.setFileLength(oftIndex, newFileLen);
                this.setDescriptorLength(descIndex, newFileLen);
                
                keepReading = false; 
            }
            // end of buffer is reached
            if (isEndOfBuffer) {
                int writeBlockIndex = curBlockIndex;
                curBlockIndex = newBlockIndex;
                // if block does not exist yet (expanding file)
                if (!this.isDescriptorBlockExist(descIndex, curBlockIndex)) {
                    // allocate new block and update bitmap, also update file 
                    // descriptor with new index (all done in below method)
                    int status = this.newDescriptorBlock(descIndex); 
                    if (status == OUT_OF_MEMORY_ERR) {
                        return COMMAND_FAIL;
                    }
                }
                // update pos so we write to new updated block
                this.oft.setPosition(oftIndex, newPos);
                
                int writeBlockNum = writeBlockIndex + 1;
                // write buffer to disk block
                this.oft.writeBuffer(oftIndex, writeBlockNum);
                
                int readBlockNum = newBlockIndex + 1;
                // read in the new empty block
                this.oft.readBuffer(oftIndex, readBlockNum);
            }
            if (keepReading) {
                // continue copying if we have not hit end
                this.oft.copyToTable(oftIndex, newPos, readFrom[incIndex]);
                incIndex++;
                addedFileLen++; // in bytes
            }
        }
        return COMMAND_SUCCEED;
    }
    
    /**
     * Adds ability to rewind or fast-forward in a
     * specified file. Move to fileLoc in specified file.
     */
    public int lseek(int oftIndex, int newPosition) {
        // if new position is not in current block, write buffer to disk
        int curPos = this.oft.getPosition(oftIndex);
        int curBlockIndex = this.oft.posToBlockIndex(curPos);
        int newBlockIndex = this.oft.posToBlockIndex(newPosition);

        boolean inCurBlock = (curBlockIndex == newBlockIndex);
        if (!inCurBlock) {
            int curBlockNum = curBlockIndex + 1;
            // write buffer to disk
            this.oft.writeBuffer(oftIndex, curBlockNum); 
            
            // read the new block (or maybe it's old, does not matter)
            int newBlockNum = newBlockIndex + 1;
            this.oft.readBuffer(oftIndex, newBlockNum);
        }
        
        // set our pos. to new pos. (it's ok to go over 64, 
        // we perform modulus within the otf!)
        this.oft.setPosition(oftIndex, newPosition);
        
        // return status
        return COMMAND_SUCCEED;
    }
    
    /**
     * read directory file for each non-empty entry, print file name
     */
    public String ls() {
        this.lseek(DIRECTORY_OFT_INDEX, 0);
        int numNameBlocks = (64 / 8) * 3; // 24
        String output = "";
        
        // 8 bytes (4 byte name + 4 byte integer index) 
        // at a time until find free slot
        for (int i=0; i < numNameBlocks; i++) {
            byte[] nameFile = this.readFile(DIRECTORY_OFT_INDEX, 8);
            
            if (!this.isNameBlockFree(nameFile)) {
                String name = new String(Arrays.copyOfRange(nameFile, 0, 4));
                name = name.trim();
                output += name + "\r\n";
            }
            else {
                break;
            }
        }
        // kill trailing newline
        output = output.substring(0, output.length() - 2); 
        this.lseek(DIRECTORY_OFT_INDEX, 0);
        
        return output;
    }
    
    private void reinit(byte[][] newLdisk) {    
        byte[] savedDirectory = this.oft.getBuffer(DIRECTORY_OFT_INDEX);
        int savedDirectoryLen = this.oft.getFileLength(DIRECTORY_OFT_INDEX);
        int savedDirectoryDescriptor = this.oft.getDescriptorIndex(DIRECTORY_OFT_INDEX);
        int savedDirectoryPos = this.oft.getPosition(DIRECTORY_OFT_INDEX);
        
        this.io = new IO();
        this.oft = new OFT();
        
        // this.bitmap = new BitMap(); Don't clean bitmap!
        
        // store the entire directory file
        this.oft.setBuffer(DIRECTORY_OFT_INDEX, savedDirectory);    
        this.oft.setPosition(DIRECTORY_OFT_INDEX, savedDirectoryPos);
        this.oft.setDescIndex(DIRECTORY_OFT_INDEX, savedDirectoryDescriptor);
        this.oft.setFileLength(DIRECTORY_OFT_INDEX, savedDirectoryLen);
        
        for (int i=0; i < newLdisk.length; i++) {
            byte[] block = newLdisk[i];
            this.io.writeBlock(i, block);
        }
    }
    
    private byte[][] buildLdisk(String backup) {
        // System.out.println("BACKUP~~~~~: " + backup);
        String[] rows = backup.split(FileSystem.BLOCK_DELIMITER);   
        byte[][] ldisk = new byte[IO.LDISK_SIZE][0];
        
        // System.out.println("Saved row size: " + rows.length + 
        //      " ldisk size default: " + 64);
        
        for (int i=0; i < rows.length; i++) {
            String row = rows[i];
            String[] stringBytes = row.split(FileSystem.BYTE_DELIMITER);

            byte[] bytes = new byte[stringBytes.length];
            for (int j=0; j < bytes.length; j++) {
                bytes[j] = Byte.parseByte(stringBytes[j]);
            }
            ldisk[i] = bytes;
        }
        return ldisk;
    }
    
    /**
     * Restore ldisk from fn.txt
     * (Or create new if no fn.txt exists).
     */
    public String init(String[] chunkedInput) throws IOException {
        boolean fileExists = (chunkedInput.length == 2);
        if (fileExists) { 
            // restore the file
            String filename = chunkedInput[1];
            BufferedReader br = new BufferedReader(new FileReader(filename));
            try {
                StringBuilder sb = new StringBuilder();
                String line = br.readLine();

                while (line != null) {
                    sb.append(line);
                    line = br.readLine();
                }
                
                // restore our file system
                String contents = sb.toString();
                byte[][] newLdisk = this.buildLdisk(contents);
                this.reinit(newLdisk);
            } finally {
                br.close();
            }
            return "disk restored";
        }
        else {
            // first command! do nothing
            return "disk initialized";
        }
    }
    
    /**
     * Save ldisk to fn.txt
     */
    public void save(String fn) throws FileNotFoundException {
        PrintWriter out = new PrintWriter(fn);
        StringBuilder textForm = new StringBuilder();
        
        for (int i=0; i < IO.LDISK_SIZE; i++) {
            byte[] row = this.io.readBlock(i);      
            for (int j=0; j < row.length; j++) {
                textForm.append(row[j]);
                // don't append a delimiter to the very end
                if (j != row.length - 1) {
                    textForm.append(FileSystem.BYTE_SEP);
                }
            }
            if (i != IO.LDISK_SIZE - 1) {
                textForm.append(FileSystem.BLOCK_SEP);
            }
        }
        String output = textForm.toString();
        out.write(output);
        out.close();
    }
    
    class OFT {
        /**
         * Open File Table abstraction.
         */
        
        // directory + up to 3 open files
        public static final int OFT_MAX_SIZE = 4; 
        // r/w buffer + pos + descriptor index + length of file
        public static final int OFT_ENTRY_SIZE = 64 + 4 + 4 + 4;
        
        private byte[][] table;
        
        public OFT() {
            this.table = new byte[OFT_MAX_SIZE][OFT_ENTRY_SIZE];
            
            // for every entry in the OFT
            for (int i=0; i < this.table.length; i++) {
                // for every byte in the r/w buffer 
                // (nothing else! we keep other stuff init at 0)
                for (int j=0; j < 64; j++) {
                    this.table[i][j] = IO.EMPTY_BYTE;
                }
            }
            // init directory "file"
            this.openDirectory();
        }
        
        private void printTable() {
            System.out.println("~~~~~~~~~~OFT~~~~~~[buffer, pos, desc, length]~~~~");
            for (int i=0; i < this.table.length; i++) {
                System.out.print("|OFT-ROW|");
                for (int j=0; j < this.table[i].length; j++) {
                    System.out.print(" " + this.table[i][j]);
                }
                System.out.print("\r\n");
            }
        }
        
        private void openDirectory() {
            // oftIndex, val
            this.setPosition(DIRECTORY_OFT_INDEX, 0); 
            this.setDescIndex(DIRECTORY_OFT_INDEX, 0);
            this.setFileLength(DIRECTORY_OFT_INDEX, 0);
        }
        
        public int getFreeOFTEntry() {
            for (int i=1; i < 4; i++) {
                boolean isFree = (isByteFree(this.table[i][0]));
                if (isFree) {
                    return i;
                }
            }
            return OUT_OF_MEMORY_ERR;
        }
        
        /**
         * re-use the 3 slots open for files indices (1-3)
         */
        private void clearEntry(int entryIndex) {
            for (int i=0; i < OFT_ENTRY_SIZE; i++) {
                this.table[entryIndex][i] = IO.EMPTY_BYTE;
            }
        }
                
        /**
         * don't allow access to buffers, use this API
         */
        public void copyToTable(int entryIndex, int pos, byte val) {
            int normalizedPos = this.posToNewBlockPos(pos);
            this.table[entryIndex][normalizedPos] = val;
            // this.getBuffer(entryIndex)[normalizedPos] = val;
        }
        
        public byte copyFromTable(int entryIndex, int pos) {
            int normalizedPos = this.posToNewBlockPos(pos);
            return this.getBuffer(entryIndex)[normalizedPos];
        }
        
        /**
         * write contents of buffer into disk
         */
        public void writeBuffer(int entryIndex, int blockNum) {
            byte[] buffer = this.getBuffer(entryIndex);
            int descIndex = this.getDescriptorIndex(entryIndex);
            
            int ldiskBlockIndex = blockNumToBlockIndex(descIndex, blockNum);
            
            // if we go overboard, create a new data block
            if (ldiskBlockIndex == -1) { 
                ldiskBlockIndex = newDescriptorBlock(descIndex);
            }
            io.writeBlock(ldiskBlockIndex, buffer);
        }
        
        /**
         * read contents of ldisk into buffer 
         */
        public void readBuffer(int entryIndex, int blockNum) {
            int descIndex = this.getDescriptorIndex(entryIndex);
            int ldiskBlockIndex = blockNumToBlockIndex(descIndex, blockNum);
            
            // if we go overboard, create a new data block
            if (ldiskBlockIndex == -1) { // err
                ldiskBlockIndex = newDescriptorBlock(descIndex);
            }
            byte[] data = io.readBlock(ldiskBlockIndex);
            this.setBuffer(entryIndex, data);
        }
        
        /**
         * r/w buffer is 64 bytes long, if we go overboard
         * that means we've moved to the next block
         */
        public int posToNewBlockPos(int position) {
            return (position % 64);
        }
        
        public int posToBlockIndex(int position) {
            return (position / 64);
        }
        
        public int getBlockNum(int oftIndex) {
            int pos = this.getPosition(oftIndex);
            int blockIndex = this.posToBlockIndex(pos);
            int blockNum = blockIndex + 1;
            return blockNum;
        }
        
        private void setBuffer(int entryIndex, byte[] readFrom) {
            for (int i=0; i < readFrom.length; i++) {
                this.table[entryIndex][i] = readFrom[i];
            }
        }
        
        private void setPosition(int entryIndex, int posVal) {
            // int entryIndex = this.getEntryIndex(descIndex);
            intPack(this.table[entryIndex], posVal, 64);
        }
        
        private void setDescIndex(int entryIndex, int descVal) {
            intPack(this.table[entryIndex], descVal, 68);
        }
        
        private void setFileLength(int entryIndex, int fileLenVal) {
            intPack(this.table[entryIndex], fileLenVal, 72);
        }
        
        //
        //
        
        /**
         * Careful when using this, don't use this to modify the
         * buffer as it's a copy! Not a reference.
         */
        private byte[] getBuffer(int entryIndex) {
            return Arrays.copyOfRange(this.table[entryIndex], 0, 64);
        }
        
        private int getPosition(int entryIndex) {
            return intUnpack(this.table[entryIndex], 64);
        }
        
        private int getDescriptorIndex(int entryIndex) {
            return intUnpack(this.table[entryIndex], 68);
        }
        
        private int getFileLength(int entryIndex) {
            return intUnpack(this.table[entryIndex], 72);
        }
    }
}
