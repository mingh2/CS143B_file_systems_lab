class BitMap {
    /**
     * We will be using the bitmap is a hash marker for determining which blocks
     * are free and which are taken. I decided to abstract it into a class
     * because bitmap opts involve annoying bit operations.
     */
    
    public static final int BITMAP_ERR = -1;

    private int[] bitmap;
    private int[] mask;

    public BitMap() {
        this.bitmap = new int[2]; // 2 ints -> 8 bytes -> 64 bits == # of blocks
        this.mask = new int[32]; // size of int

        this.mask[31] = 1;
        for (int i=30; i >= 0; i--) {
            this.mask[i] = this.mask[i + 1] << 1;
        }
    }

    /**
     * param index can range from 0->63 we are implementing the bitmap as two
     * int's of 32 bits each so we need to perform some norm opts bits 0->31 are
     * in the first integer, 32->63 in the second
     */
    public int normIndex(int index) {
        return (index % 32);
    }

    public int arrIndex(int index) {
        return (index / 32);
    }

    /**
     * convert back into 0->64 index from 2x 0->31
     */
    public int invIndex(int arrIndex, int normIndex) {
        return (arrIndex * 32) + normIndex;
    }

    public void setZero(int index) {
        int arrIndex = this.arrIndex(index);
        int normIndex = this.normIndex(index);

        int invMask = ~this.mask[normIndex];
        this.bitmap[arrIndex] = this.bitmap[arrIndex] & invMask;
    }

    public void setOne(int index) {
        int arrIndex = this.arrIndex(index);
        int normIndex = this.normIndex(index);

        this.bitmap[arrIndex] = this.bitmap[arrIndex] | this.mask[normIndex];
    }
    
    public int closestDataBlock() {
        for (int i=0; i < 2; i++) {
            for (int j=0; j < 32; j++) {
                int tempJ = j;
                // so retarded
                if (i == 0) {
                    tempJ = j + 7; // skip the first 7 slots!
                }
                if (tempJ >= 32) {
                    continue;
                }
                int isZeroBit = (this.bitmap[i] & this.mask[tempJ]);
                if (isZeroBit == 0) {
                    // bit j of bitmap[i] is zero
                    return this.invIndex(i, tempJ);
                }
            }
        }
        return BITMAP_ERR;
    }
    
    public int closestZero() {
        for (int i=0; i < 2; i++) {
            for (int j=0; j < 32; j++) {
                int isZeroBit = (this.bitmap[i] & this.mask[j]);
                if (isZeroBit == 0) {
                    // bit j of bitmap[i] is zero
                    return this.invIndex(i, j);
                }
            }
        }
        return BITMAP_ERR;
    }

    public int closestOne() {
        for (int i=0; i < 2; i++) {
            for (int j=0; j < 32; j++) {
                int isOneBit = (this.bitmap[i] | ~this.mask[j]);
                if (isOneBit == 1) {
                    // bit j of bitmap[i] is one
                    return this.invIndex(i, j);
                }
            }
        }
        return BITMAP_ERR;
    }

    public void print() {
        for (int i=0; i < this.bitmap.length; i++) {
            System.out.println(Integer.toBinaryString(this.bitmap[i]));
        }
    }
    
    public static void main(String[] args) {
        // Bitmap test
        BitMap bitmap = new BitMap();
        bitmap.print();
        bitmap.setOne(12);
        bitmap.setOne(0);
        bitmap.setOne(60);
        bitmap.setOne(63);
        bitmap.setOne(32);
        bitmap.print();
        System.out.println("The closest zero is at index " + 
                Integer.toString(bitmap.closestZero()));
    }
}
