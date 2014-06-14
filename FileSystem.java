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
    
    // Important constant values
    public static final int MAX_SYMBOLIC_FILENAME = 4;
    public static final int DIRECTORY_OFT_INDEX = 0;
    public static final int MAX_FILE_BLOCKS = 3;
    public static final int MAX_FILESIZE = 64 * MAX_FILE_BLOCKS;
    public static final int DIRECORY_ENTRY_SIZE = 8;
    
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
        this.bitmap = new BitMap();
        this.oft = new OFT(this.io, this.bitmap);
        
        // mark the directory descriptor file taken
        this.bitmap.setOne(0);
    }
    
    public void printDisk() { /** For debugging **/
        this.io.printDisk();
        this.oft.printTable();
    }
       
    
    /**
     * returns the new descriptor number (1-24)
     * or an error
     */
    private int createDescriptor() {
        // find empty file descriptor, only mark as taken after
        // we find a similar directory slot, otherwise return 
        // out of memory (false)
        int descNumb = closestDescriptorNumber(); 
                
        if (descNumb == FileSystem.OUT_OF_MEMORY_ERR) {
            return FileSystem.OUT_OF_MEMORY_ERR;
        }
        // populate empty descriptor with length of 0
        setDescriptorLength(descNumb, 0);
        
        // add block to descriptor
        newDescriptorBlock(descNumb);  
        return descNumb;
    }
    
    /**
     * completely handles the clearing of a file descriptor from it's 
     * (1-24) number. clears out the connected data blocks first
     * and then proceeds to clear out the actual descriptor index
     * 
     * however, the entry in the directory file is still not cleared
     */
    private void clearFileDescriptor(int descNumb) { 
        int descIndex = getDescriptorIndex(descNumb);
        int descBlockPos = getDescriptorBlockPosition(descNumb);
        
        byte[] descriptor = io.readBlock(descIndex);
        
        // clears out data blocks the descriptor links to first
        for (int blockNumb=1; blockNumb <= 3; blockNumb++) {
            boolean blockExists = 
                   isDescriptorBlockExist(descNumb, blockNumb);
            if (blockExists) {
                // remember that the first 4 bytes go to the file size
                int blockPos = blockNumb * 4;
                int jumpPos = blockPos + descBlockPos;
                
                // grab the actual block index from the descriptor position
                int dataBlockIndex = Utils.intUnpack(descriptor, jumpPos);
                
                // clear and update the bitmap
                clearDataBlock(dataBlockIndex);
                bitmap.setZero(dataBlockIndex);
            }
        }
        
        // clears out the chunk of the descriptor block
        // where our descriptor exists
        for (int i=descBlockPos; i < (descBlockPos + IO.DESCRIPTOR_SIZE); i++) {
            descriptor[i] = IO.EMPTY_BYTE;
        }
        io.writeBlock(descIndex, descriptor);
    }
    
    /**
     * very useful when we have the block number (1, 2, or 3)
     * but we need to extract out the actual block index on ldisk
     */
    public int blockNumToBlockIndex(int descNumb, int blockNum) {
        int descIndex = getDescriptorIndex(descNumb);
        int descBlockPos = getDescriptorBlockPosition(descNumb);
        
        int finalPos = (blockNum * 4) + descBlockPos;
        byte[] descriptor = io.readBlock(descIndex);
        int blockIndex = Utils.intUnpack(descriptor, finalPos);
        return blockIndex;
    }
        
    /**
     * Since 4 descriptors fit on a block (64/16) and we have
     * 24 total descriptors (for a total of 6 blocks), we need
     * to convert the "descriptor number" (1-24) into the descriptor
     * block index (1-6)
     */
    private int getDescriptorIndex(int descNumb) {
        // edge case just for the directory index
        if (descNumb == 0) {
            return 0;
        }
        
        // descriptorNumber is normally 1-24 (1 based)
        int zeroBased = descNumb - 1; // 0-23
        return (zeroBased / 4) + 1; // 1-6
    }
    
    /**
     * This refers to the actual byte position (1-3) on the block,
     * packaged with the above method
     */
    private int getDescriptorBlockPosition(int descNumb) {
        // edge case just for directory index
        if (descNumb == 0) {
            return 0;
        }
        // index: 1-24 inclusive
        int zeroBased = descNumb - 1; // 0-23
        return (zeroBased % 4) * IO.DESCRIPTOR_SIZE; // 0-3
    }
    
    private void clearDataBlock(int blockIndex) {
        byte[] clear = Utils.getClearBytes(64);
        io.writeBlock(blockIndex, clear);
    }
        
    private void setDescriptorLength(int descNumb, int length) { 
        int descIndex = getDescriptorIndex(descNumb);
        int descBlockPos = getDescriptorBlockPosition(descNumb);
        byte[] descriptor = io.readBlock(descIndex);
        Utils.intPack(descriptor, length, descBlockPos + 0);
        io.writeBlock(descIndex, descriptor);
    }
    
    private int getDescriptorLength(int descNumb) { 
        int descIndex = getDescriptorIndex(descNumb);
        int descBlockPos = getDescriptorBlockPosition(descNumb);
        byte[] descriptor = io.readBlock(descIndex);
        int length = Utils.intUnpack(descriptor, descBlockPos + 0);
        return length;
    }
    
    /**
     * note that by "descriptor number" we are referring to
     * 1-24 (ones based) while "descriptor index" refers to
     * the 1-6 blocks which the descriptor may reside on.
     */
    private int closestDescriptorNumber() { 
        // note <=, 1-24
        for (int descNumb=1; descNumb <= IO.NUMB_DESCRIPTORS; descNumb++) { 
            // if desc does not exist, it's free
            if (!isDescriptorExist(descNumb)) { 
                return descNumb;
            }
        }
        return FileSystem.OUT_OF_MEMORY_ERR;
    }
        
    private boolean isDescriptorExist(int descNumb) { 
        int descIndex = getDescriptorIndex(descNumb);
        int descBlockPos = getDescriptorBlockPosition(descNumb);
        
        byte[] descriptor = io.readBlock(descIndex);
        byte startByte = descriptor[descBlockPos]; // it's the length
        
        return (startByte != IO.EMPTY_BYTE);
    }
    
    private boolean isDescriptorBlockExist(int descNumb, int blockNumb) { 
        int descIndex = getDescriptorIndex(descNumb);
        int descBlockPos = getDescriptorBlockPosition(descNumb);
                
        byte[] descriptor = io.readBlock(descIndex);
        int jump = blockNumb * 4;
        int finalBlockIndex = descBlockPos + jump; // add the base
        
        return (descriptor[finalBlockIndex] != IO.EMPTY_BYTE);
    }
    
    /**
     * returns location of the new data block
     */
    public int newDescriptorBlock(int descNumb) {
        int descIndex = getDescriptorIndex(descNumb);
        int descBlockPos = getDescriptorBlockPosition(descNumb);
        
        byte[] descriptor = io.readBlock(descIndex);
        int blockNumb = 1;
        boolean isBlockExist = isDescriptorBlockExist(descNumb, blockNumb);
        
        // go until it does not exist
        while (isBlockExist) {
            blockNumb++;
            if (blockNumb > 3) {
                return FileSystem.OUT_OF_MEMORY_ERR;
            }
            isBlockExist = isDescriptorBlockExist(descNumb, blockNumb);
        }       
        int indexDataBlock = bitmap.closestDataBlock();
        
        // populate bitmap
        bitmap.setOne(indexDataBlock);
        
        // pack our new index into the descriptor
        int blockDescriptorIndex = (blockNumb * 4) + descBlockPos; // add the base
        Utils.intPack(descriptor, indexDataBlock, blockDescriptorIndex);
        
        // write it back in
        io.writeBlock(descIndex, descriptor);
        return indexDataBlock;
    }
    
    /**
     * skip 8 bytes at a time because a name field is
     * 4 bytes name + 4 bytes integer index
     * 
     * positionInDirectoryFile here refers to 0-23 (0 based index of # of descriptors)
     */
    private void clearDirectoryEntry(int positionInDirectoryFile) {
        byte[] clear = Utils.getClearBytes(8);
        int filePos = (positionInDirectoryFile * 8);
        this.lseek(DIRECTORY_OFT_INDEX, filePos);
        this.writeFile(DIRECTORY_OFT_INDEX, clear);
    }
        
    /** returns array holding filename + descNumb **/
    private byte[] getDirectoryEntry(int position) {
        this.lseek(DIRECTORY_OFT_INDEX, position * DIRECORY_ENTRY_SIZE);
        return this.readFile(DIRECTORY_OFT_INDEX, DIRECORY_ENTRY_SIZE);
    }
    
    private void writeDirectoryEntry(int position, byte[] filename, int descNumb) {
        // build byte array (4 bytes for name, 4 for index)
        byte[] readFrom = new byte[DIRECORY_ENTRY_SIZE];
        // write filename in
        for (int m=0; m < filename.length; m++) {
            readFrom[m] = filename[m];
        }
        // write descriptor number in
        Utils.intPack(readFrom, descNumb, DIRECORY_ENTRY_SIZE/2);
        int writeToPos = (position * DIRECORY_ENTRY_SIZE);
        
        this.lseek(DIRECTORY_OFT_INDEX, writeToPos);
        this.writeFile(DIRECTORY_OFT_INDEX, readFrom);
    }
    
    /** 
     * returns the index found in directory file
     * or not found error, jumps by the size of what we
     * are searching for.
     */
    private int searchDirectoryEntry(byte[] inFilename) {
        // start from the very beginning
        this.lseek(DIRECTORY_OFT_INDEX, 0);
        
        // Search 8 bytes (4 byte name + 4 byte integer index) 
        // at a time until find free slot
        for (int i=0; i < IO.NUMB_DESCRIPTORS; i++) {
            byte[] nameFile = this.readFile(
                    DIRECTORY_OFT_INDEX, DIRECORY_ENTRY_SIZE);
            
            byte[] filename = Arrays.copyOfRange(
                    nameFile, 0, DIRECORY_ENTRY_SIZE/2);
            boolean isEqual = Utils.isByteArrEquals(filename, inFilename);
            
            if (isEqual) {
                return i;
            }
        }
        return COMMAND_FAIL;
    }
        
    private int searchDirectoryForFreeEntry() {
        byte[] searchFor = Utils.getClearBytes(DIRECORY_ENTRY_SIZE/2);
        return searchDirectoryEntry(searchFor);
    }
        
    private boolean fileAlreadyExists(byte[] filename) {
        int pos = searchDirectoryEntry(filename);
        return (pos != COMMAND_FAIL);
    }
    
    /**
     * returns status for success or fail
     */
    public int create(byte[] filename) {
        int descNumb = createDescriptor();
        if (descNumb == FileSystem.OUT_OF_MEMORY_ERR) {
            return FileSystem.OUT_OF_MEMORY_ERR;
        }
        
        if (fileAlreadyExists(filename)) {
            return COMMAND_FAIL;
        }
       
        int position = searchDirectoryForFreeEntry();   
        if (position != COMMAND_FAIL) {
            this.writeDirectoryEntry(position, filename, descNumb);
            return COMMAND_SUCCEED;
        } 
        else {
            clearFileDescriptor(descNumb);
            return COMMAND_FAIL;
        }
    }
    
    public int destroy(byte[] inFilename) {                
        int position = this.searchDirectoryEntry(inFilename);
        if (position != COMMAND_FAIL) {
            byte[] nameEntry = getDirectoryEntry(position);
            this.clearDirectoryEntry(position); 
            int descNumb = Utils.intUnpack(nameEntry, DIRECORY_ENTRY_SIZE/2); 
            clearFileDescriptor(descNumb);
            int oftIndex = this.oft.getOFTIndexFromDescriptor(descNumb);
            if (oftIndex != COMMAND_FAIL) {
                this.close(oftIndex);
            }
            return COMMAND_SUCCEED;
        }
        return COMMAND_FAIL;
    }
    
    /**
     * Return Open File Table index. This "file index" is 
     * what's referred to in the next few methods.
     */
    public int open(byte[] inFilename) {     
        int position = this.searchDirectoryEntry(inFilename);
        if (position != COMMAND_FAIL) {
            byte[] nameEntry = getDirectoryEntry(position);  
            
            int descNumb = Utils.intUnpack(nameEntry, DIRECORY_ENTRY_SIZE/2); 
            int descIndex = getDescriptorIndex(descNumb);
            int descBlockPos = getDescriptorBlockPosition(descNumb);
            byte[] descriptor = io.readBlock(descIndex);
     
            int oftIndex = this.oft.getFreeOFTEntry();
            if (oftIndex == OUT_OF_MEMORY_ERR) {
                return COMMAND_FAIL;
            }
            this.bitmap.setOne(oftIndex);
            
            // files always open at the first position
            this.lseek(oftIndex, 0); 
            
            // set OFT's descriptor number
            this.oft.setDescriptorNumb(oftIndex, descNumb);
            
            // read the data from the first block into the OFT
            int blockOneLocation = descBlockPos + 4; // skip the file length 
            int blockOneIndex = Utils.intUnpack(descriptor, blockOneLocation);
            byte[] blockData = io.readBlock(blockOneIndex);
            for (int b=0; b < blockData.length; b++) {
                this.oft.copyToTable(oftIndex, b, blockData[b]);
            }
            // set filelength from descriptor into OFT
            int fileLengthLoc = descBlockPos + 0;
            int fileLength = Utils.intUnpack(descriptor, fileLengthLoc); 
            this.oft.setFileLength(oftIndex, fileLength);
            return oftIndex; // succeed     
        }
        return COMMAND_FAIL;
   }
    
    public int close(int oftIndex) {
        int blockNumb = this.oft.getBlockNumb(oftIndex);
        this.oft.writeBuffer(oftIndex, blockNumb, this);
        
        // extract descriptor number and the file length and update
        int newFileLen = this.oft.getFileLength(oftIndex);
        int descNumb = this.oft.getDescriptorNumb(oftIndex);
        
        setDescriptorLength(descNumb, newFileLen);
        this.oft.clearEntry(oftIndex);
        return COMMAND_SUCCEED;
    }
    
    public byte[] readFile(int oftIndex, int goalBytes) {
        // compute latest position in the r/w buffer
        int position = this.oft.getPosition(oftIndex);
        int progress = 0;
        
        int fileLength = MAX_FILESIZE;
        if (oftIndex != DIRECTORY_OFT_INDEX) {
            fileLength = this.oft.getFileLength(oftIndex);
        }
        // we can possibly request more bytes than possible given
        // our current position, only read till the file's end
        goalBytes = Math.min(fileLength - position, goalBytes); 
        
        // System.out.println("GOALBYTES: " + goalBytes);
        byte[] returnBytes = Utils.getClearBytes(goalBytes);
        
        // keep track of block number
        int curBlockIndex = this.oft.posToBlockIndex(position);
        
        // keep track of how much we've read to know when to exit
        int curNumBytes = 0;
        boolean keepReading = true;

        // copy from buffer to memory until
        while (keepReading) {
            int newPosition = position + progress;
            int newBlockIndex = this.oft.posToBlockIndex(newPosition);
            this.oft.setPosition(oftIndex, newPosition);
                        
            boolean isDesiredCount = (curNumBytes == goalBytes);
            boolean isEndOfBuffer = (newBlockIndex != curBlockIndex);
            boolean overSized = ((newBlockIndex + 1) > MAX_FILE_BLOCKS);
            
            if (isDesiredCount || overSized) {
                // update cur pos, return status
                this.oft.setPosition(oftIndex, newPosition);
                keepReading = false; 
            }
            // end of buffer is reached
            if (isEndOfBuffer && !overSized) {
                int curBlockNum = curBlockIndex + 1;
                
                // write the buffer to disk
                this.oft.writeBuffer(oftIndex, curBlockNum, this);
                
                // read the next block into buffer
                int newBlockNumb = newBlockIndex + 1;
                this.oft.readBuffer(oftIndex, newBlockNumb, this);
                
                // update to the new block number
                curBlockIndex = newBlockIndex;
            }       
            if (!keepReading) {
                return returnBytes;
            }
            // continue copying
            returnBytes[curNumBytes] = this.oft.copyFromTable(oftIndex, newPosition);
            progress++;
            curNumBytes++;
        }
        return null; 
    }
    
    /**
     * 2 method signatures for the writeFile(...) method b/c the API
     * allows for writing in "wr <channel> <char> <length>" syntax 
     */
    public int writeFile(int oftIndex, byte character, int length) {
        byte[] readFrom = new byte[length];
        for (int i=0; i < length; i++) {
            readFrom[i] = character;
        }
        return this.writeFile(oftIndex, readFrom);
    }
    
    public int writeFile(int oftIndex, byte[] readFrom) {
        int position = this.oft.getPosition(oftIndex);
        int descNumb = this.oft.getDescriptorNumb(oftIndex);
        // start at index immediately after where we left off in file
        int progress = 0; 
        int addedFileLen = 0;
        
        // keep track of block index to see if we are at end of buffer
        int curBlockIndex = this.oft.posToBlockIndex(position);
                
        // if the first block does not exist, create it
        int firstBlockNumb = 1;
        if (!isDescriptorBlockExist(descNumb, firstBlockNumb)) {
            newDescriptorBlock(descNumb);
        }

        boolean keepReading = true;
        // copy from memory to buffer until the desired count or EOF is reached
        while (keepReading) {
            int newPosition = progress + position;
            int newBlockIndex = this.oft.posToBlockIndex(newPosition);
            
            boolean isEOF = ((newBlockIndex + 1) > MAX_FILE_BLOCKS);
            boolean hitDesiredCount = (progress >= readFrom.length);
            boolean isEndOfBuffer = (newBlockIndex != curBlockIndex);
                    
            if (isEOF || hitDesiredCount) {
                this.oft.setPosition(oftIndex, newPosition);
                
                int curFileLen = this.oft.getFileLength(oftIndex);
                int newFileLen = curFileLen + addedFileLen;
    
                this.oft.setFileLength(oftIndex, newFileLen);
                setDescriptorLength(descNumb, newFileLen);
                
                keepReading = false; 
            }
            if (isEndOfBuffer) {
                int writeBlockIndex = curBlockIndex;
                curBlockIndex = newBlockIndex;
                int curBlockNumber = curBlockIndex + 1;
                
                boolean exceeded = curBlockNumber > MAX_FILE_BLOCKS;
                // if block does not exist yet (expanding file)
                if (!exceeded && !isDescriptorBlockExist(descNumb, curBlockNumber)) {
                    // allocate new block and update bitmap, also update file 
                    // descriptor with new index (all done in below method)
                    int status = newDescriptorBlock(descNumb); 
                    if (status == OUT_OF_MEMORY_ERR) {
                        return COMMAND_FAIL;
                    }
                }
                this.oft.setPosition(oftIndex, newPosition);
                int writeBlockNum = writeBlockIndex + 1;
                this.oft.writeBuffer(oftIndex, writeBlockNum, this);
                
                if (!exceeded) {
                    int readBlockNum = newBlockIndex + 1;
                    // read in the new empty block
                    this.oft.readBuffer(oftIndex, readBlockNum, this);
                }
            }
            if (keepReading) {
                // continue copying if we have not hit end
                this.oft.copyToTable(oftIndex, newPosition, readFrom[progress]);
                progress++;
                addedFileLen++; // in bytes
            }
        }
        return addedFileLen;
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

        // incase we try surpass the file size
        if ((newBlockIndex + 1) > MAX_FILE_BLOCKS) { 
            return COMMAND_FAIL;
        }
        
        boolean inCurBlock = (curBlockIndex == newBlockIndex);
        if (!inCurBlock) {
            int curBlockNum = curBlockIndex + 1;
            this.oft.writeBuffer(oftIndex, curBlockNum, this); 
            
            // read the new block (or maybe it's old, does not matter)
            int newBlockNum = newBlockIndex + 1;
            this.oft.readBuffer(oftIndex, newBlockNum, this);
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
        String output = "";
        
        for (int i=0; i < IO.NUMB_DESCRIPTORS; i++) {
            byte[] nameFile = this.readFile(DIRECTORY_OFT_INDEX, 8);
            
            if (!Utils.isBlockFree(nameFile)) { 
                String name = new String(Arrays.copyOfRange(nameFile, 0, 4));
                name = name.trim();
                output += name + " ";
            }
            else {
                continue;
            }
        }
        this.lseek(DIRECTORY_OFT_INDEX, 0);
        return output;
    }
    
    private void reinit(byte[][] newLdisk) {    
        byte[] savedDirectory = this.oft.getBuffer(DIRECTORY_OFT_INDEX);
        int savedDirectoryLen = this.oft.getFileLength(DIRECTORY_OFT_INDEX);
        int savedDirectoryDescriptor = this.oft.getDescriptorNumb(DIRECTORY_OFT_INDEX);
        int savedDirectoryPos = this.oft.getPosition(DIRECTORY_OFT_INDEX);
        
        this.io = new IO();
        this.oft = new OFT(this.io, this.bitmap);
        
        // this.bitmap = new BitMap(); Don't clean bitmap!
        
        // store the entire directory file
        this.oft.setBuffer(DIRECTORY_OFT_INDEX, savedDirectory);    
        this.oft.setPosition(DIRECTORY_OFT_INDEX, savedDirectoryPos);
        this.oft.setDescriptorNumb(DIRECTORY_OFT_INDEX, savedDirectoryDescriptor);
        this.oft.setFileLength(DIRECTORY_OFT_INDEX, savedDirectoryLen);
        
        for (int i=0; i < newLdisk.length; i++) {
            byte[] block = newLdisk[i];
            this.io.writeBlock(i, block);
        }
        // close open files
        for (int i=1; i <= 3; i++) {
            this.bitmap.setZero(i);
        }
    }
    
    private byte[][] buildLdisk(String backup) {
        String[] rows = backup.split(FileSystem.BLOCK_DELIMITER);   
        byte[][] ldisk = new byte[IO.LDISK_SIZE][0];
        
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
    
    public void save(String fn) throws FileNotFoundException {
        // write our OFT into disk before saving because we may have contents
        // within the OFT that has not been serialized yet.
        
        this.oft.saveEverything(this);
        
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
}
