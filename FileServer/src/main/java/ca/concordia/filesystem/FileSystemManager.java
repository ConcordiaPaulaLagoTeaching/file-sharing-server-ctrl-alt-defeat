package ca.concordia.filesystem;

import ca.concordia.filesystem.datastructures.FEntry;
import ca.concordia.filesystem.datastructures.FNode;

import java.io.RandomAccessFile;
import java.util.Arrays;
import java.util.concurrent.locks.ReentrantLock;

public class FileSystemManager {

    private final int MAXFILES = 5;
    private final int MAXBLOCKS = 10;
    private final int FENTRYSIZE = 15;
    private final int FNODESIZE = 4;

    private/*final*/  static FileSystemManager instance;
    private /*final*/ RandomAccessFile disk;
    private final ReentrantLock globalLock = new ReentrantLock();

    private static final int BLOCK_SIZE = 128; // Example block size

    private FEntry[] inodeTable; // Array of inodes
    private boolean[] freeBlockList; // Bitmap for free blocks
    private FNode[] fnodeTable;

    public FileSystemManager(String filename, int totalSize) {
        // Initialize the file system manager with a file
        if(instance == null) {
            //TODO Initialize the file system
            initializeFileSystem(filename,totalSize);
            instance = this;
        } else {
            throw new IllegalStateException("FileSystemManager is already initialized.");
        }

    }

    private void initializeFileSystem(String filename, int totalSize){
        try{
            inodeTable = new FEntry[MAXFILES];
            fnodeTable= new FNode[MAXBLOCKS];
            freeBlockList= new boolean[MAXBLOCKS];
            disk = new RandomAccessFile(filename,"rw");

            Arrays.fill(freeBlockList,true);   //make the whole bitmap true indicating that all blocks are free

            for(int i =0; i<MAXFILES;i++)
                inodeTable[i]= new FEntry();

            for(int i=0;i<MAXBLOCKS;i++){
                fnodeTable[i]= new FNode(-i); //initalize FNode list showing all  blocks  free
            }

            freeBlockList[0]=false;//first block holds FEntry and FNode

            if(disk.length()==0){

                disk.setLength(totalSize); //creates new disk with specified size if new file system
                System.out.println("New file system created: " + filename);

            }
            else{ //if already exist it just opens it again, doesnt change disk
                System.out.println("Loaded existing file system: " + filename);

            }

        } catch (Exception e) {
            e.printStackTrace();
        }


    }

    public void createFile(String fileName) throws Exception {
        // TODO
        if(fileName.length()>11)
            throw new Exception("ERROR: filename too large");
        if (fileName.length()==0)
            throw new Exception("ERROR: filename cannot be empty");

        for(FEntry fentry : inodeTable){
            if( fentry!=null && fileName.equals(fentry.getFilename()))
                throw new Exception("ERROR: filename already exists");
        }

        //find free FEntry slot
        int fentryIndex = -1;
        for(int i =0; i<MAXFILES;i++){
            if( inodeTable[i].getFilename()==null ){
                fentryIndex=i;
                break;
            }
        }

        if (fentryIndex == -1)
            throw new Exception("ERROR: no free entries left.");

        FEntry entry = inodeTable[fentryIndex];
        entry.setFilename(fileName);
        entry.setFilesize((short) 0);
        entry.setFirstBlock((short) -1);


        throw new UnsupportedOperationException("Method not implemented yet.");
    }


    // TODO: Add readFile, writeFile and other required methods,
}
