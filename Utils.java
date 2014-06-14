public class Utils {
    /**
     * Helper methods which are not dependent on the IO, 
     * BitMap, or FileSystem modules go here. 
     */
    
    /**
     * Print out the contents of a bytes array
     */
    public static void printBytes(byte[] arr) {
        for (byte b : arr) {
            System.out.print(b + ", ");
        }
        System.out.println("");
    }
    
    /**
     * Helper methods to read and write 4 byte integers
     * in and out of byte[] arrays
     */
    public static void intPack(byte[] arr, int val, int loc) {
        final int MASK = 0xff;
        for (int i = 3; i >= 0; i--) {
            arr[loc + i] = (byte) (val & MASK);
            val = val >> 8;
        }
    }

    public static int intUnpack(byte[] arr, int loc) {
        final int MASK = 0xff;
        int v = (int) arr[loc] & MASK;
        for (int i = 1; i < 4; i++) {
            v = v << 8;
            v = v | ((int) arr[loc + i] & MASK);
        }
        return v;
    }
    
    public static boolean isByteFree(byte input) {
        return (input == IO.EMPTY_BYTE);
    }
    
    public static boolean isBlockFree(byte[] block) {
        boolean isEmpty = (isByteFree(block[0]));
        return isEmpty;
    }
    
    /**
     * returns a byte[] of specified size filled with
     * our custom null vals
     */
    public static byte[] getClearBytes(int size) {
        byte[] clear = new byte[size];
        for (int i=0; i< clear.length; i++) {
            clear[i] = IO.EMPTY_BYTE;
        }
        return clear;
    }
    
    /**
     * helper method for byte[] comparison
     * important points, for names < 4 characters
     * there will be 0's padded towards the right 
     * which we need to ignore
     */
    public static boolean isByteArrEquals(byte[] a1, byte[] a2) {
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
}
