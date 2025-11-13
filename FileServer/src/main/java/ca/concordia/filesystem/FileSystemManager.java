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


    }

    public void deleteFile(String filename) throws Exception {

        //find the FEntry of the specified file
        int fentryIndex = -1;
        for(int i =0; i<MAXFILES;i++) {
            if (inodeTable[i] != null && inodeTable[i].getFilename() != null &&
                    inodeTable[i].getFilename().equals(filename)) {
                {
                    fentryIndex = i;
                    break;
                }
            }
        }
            if (fentryIndex == -1)
                throw new Exception("ERROR: File not found.");

            FEntry delFentry = inodeTable[fentryIndex];
            int blockIndex = delFentry.getFirstBlock();

            while (blockIndex >= 1 && blockIndex < MAXBLOCKS) {

                //overwrite block with 0
                disk.seek(blockIndex * BLOCK_SIZE);  //move to beginning of block
                byte[] zeroBlock = new byte[BLOCK_SIZE];// default byte value is 0, we have 128 0's
                disk.write(zeroBlock);

                //mark block as free
                freeBlockList[blockIndex] = true;

                //get next block(if -1 the loop will end)
                int nextBlockIndex = fnodeTable[blockIndex].getNext();

                //set next as -1, and FNode block index to  negative value
                fnodeTable[blockIndex].setNext(-1);
                fnodeTable[blockIndex].setBlockIndex(-blockIndex);


                blockIndex = nextBlockIndex;

            }

            inodeTable[fentryIndex] = new FEntry();//makes FEntry free again

    }
    public void writeFile(String filename, byte[] contents) throws Exception{

        int entryIndex;
        FEntry target = null;
        //find FEntry
        for(int i =0;i<MAXFILES;i++){
            if(inodeTable[i] != null && filename.equals(inodeTable[i].getFilename())){
                entryIndex=i;
                target  = inodeTable[entryIndex];
            }
        }
        if (target == null)
            throw new Exception("ERROR: File not found.");

        //Calculate blocks needed for this file.
        int blockNeeded= (int) Math.ceil((double) contents.length / BLOCK_SIZE);

        //check if enough space available
        int numFreeBlocks=0;
        for(int i =0;i<MAXBLOCKS;i++)
            if(freeBlockList[i]) numFreeBlocks++; //counts number  of free blocks

        if(numFreeBlocks<blockNeeded)
            throw  new Exception("ERROR: Not enough free blocks.");

        //to keep track of allocated blocks in the case we need to changes if error occur
        int[] allocatedBlocks=new int[blockNeeded]; //keep hold of the blocks we will use for this file
        int allocatedIndex =0;
        try {
            int offset=0;
            int previousBlock = -1; //for FNode

            //loops until we went over all blocks or until we used all the blocks needed
            for(int i=1;i<MAXBLOCKS && allocatedIndex<blockNeeded ;i++){
                if(freeBlockList[i]){
                    freeBlockList[i]=false;
                    fnodeTable[i].setBlockIndex(i); // mark block as used by making it positive
                    allocatedBlocks[allocatedIndex++]=i;//holds the address of block we will use and updated variable that tracks number of blocks used

                    //write file
                    disk.seek(BLOCK_SIZE*i); //goes to start of block i
                    int remaining = Math.min(BLOCK_SIZE, contents.length - offset);
                    disk.write(contents, offset, remaining);
                    offset += remaining;

                    //set first block of fnode
                    if(previousBlock!=-1)
                        fnodeTable[previousBlock].setNext(i);
                    previousBlock=i;//update previous block with the current one

                }
            }
            fnodeTable[previousBlock].setNext(-1);//set next block of last block as -1

            // Free old blocks
            int oldBlock = target.getFirstBlock();
            while (oldBlock >= 1 && oldBlock < MAXBLOCKS) {
                freeBlockList[oldBlock] = true;
                fnodeTable[oldBlock].setBlockIndex(-oldBlock);
                oldBlock = fnodeTable[oldBlock].getNext();
            }

            //  Update FEntry
            target.setFirstBlock((short)allocatedBlocks[0]);
            target.setFilesize((short) contents.length);

            System.out.println("File written: " + filename);


        } catch (Exception e) {
            for (int b : allocatedBlocks) {
                if (b > 0) {
                    freeBlockList[b] = true;
                    fnodeTable[b].setBlockIndex(-b);
                }
            }
            throw new RuntimeException(e);
        }
    }

    public byte[] readFile(String filename) throws Exception{
        //find the FEntry of the specified file

        int fentryIndex = -1;
        FEntry target = null;


        for(int i =0; i<MAXFILES;i++){
           if (inodeTable[i] != null && inodeTable[i].getFilename() != null &&
                    inodeTable[i].getFilename().equals(filename)) {
                fentryIndex=i;
                target=inodeTable[i];
                break;
            }
        }
        if(target==null)
            throw new Exception("ERROR: File not found.");

        //create buffer that holds file data
        int fileSize = target.getFilesize();
        byte[] data= new byte[fileSize];

        int blockIndex = target.getFirstBlock();
        int offset = 0;

        if (target.getFirstBlock() == -1) {
            return new byte[0]; // empty file
        }

        while (blockIndex >= 1 && blockIndex < MAXBLOCKS && offset < fileSize) {
            disk.seek(blockIndex * BLOCK_SIZE);
            int bytesToRead = Math.min(BLOCK_SIZE, fileSize - offset);
            disk.readFully(data, offset, bytesToRead);

            offset += bytesToRead;
            blockIndex = fnodeTable[blockIndex].getNext(); // move to next block
        }

        return data;


    }
    public String[] listFiles(){
        // count existing files
        int count = 0;
        for (FEntry entry : inodeTable) {
            if (entry != null && entry.getFilename() != null)
                count++;
        }

        // fill array with names
        String[] files = new String[count];
        int index = 0;
        for (FEntry entry : inodeTable) {
            if (entry != null && entry.getFilename() != null)
                files[index++] = entry.getFilename();
        }

        return files;
    }
}
