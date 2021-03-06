//
// Copyright (C) 2011 United States Government as represented by the
// Administrator of the National Aeronautics and Space Administration
// (NASA).  All Rights Reserved.
//
// This software is distributed under the NASA Open Source Agreement
// (NOSA), version 1.3.  The NOSA has been approved by the Open Source
// Initiative.  See the file NOSA-1.3-JPF at the top of the distribution
// directory tree for the complete NOSA document.
//
// THE SUBJECT SOFTWARE IS PROVIDED "AS IS" WITHOUT ANY WARRANTY OF ANY
// KIND, EITHER EXPRESSED, IMPLIED, OR STATUTORY, INCLUDING, BUT NOT
// LIMITED TO, ANY WARRANTY THAT THE SUBJECT SOFTWARE WILL CONFORM TO
// SPECIFICATIONS, ANY IMPLIED WARRANTIES OF MERCHANTABILITY, FITNESS FOR
// A PARTICULAR PURPOSE, OR FREEDOM FROM INFRINGEMENT, ANY WARRANTY THAT
// THE SUBJECT SOFTWARE WILL BE ERROR FREE, OR ANY WARRANTY THAT
// DOCUMENTATION, IF PROVIDED, WILL CONFORM TO THE SUBJECT SOFTWARE.
//

package gov.nasa.jpf.bfs;

import gov.nasa.jpf.Config;
import gov.nasa.jpf.jvm.MJIEnv;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.ListIterator;


/**
 *
 * @author Ivan Mushketik
 */
public class JPF_gov_nasa_jpf_FileState {

  private static File fsCacheDir;

  public static void init(Config config) {
    fsCacheDir = BFSUtils.getCacheDir(config);
  }
  
  public static int write__J_3BII__I(MJIEnv env, int thisPtr, long startPos, int dataPtr, int offset, int length) throws Exception {
    byte[] data = env.getByteArrayObject(dataPtr);

    if (offset + length > data.length) {
      env.throwException("java.lang.IndexOutOfBoundsException");
      return -1;
    }

    if (length == 0) {
      return 0;
    }

    File cacheFile = createCacheFile();
    
    long fileLength = env.getLongField(thisPtr, "length");
    
    int skip = 0;
    if (startPos > fileLength) {
      skip = (int) (startPos - fileLength);
    }
    
    // Write new data chunk
    int written = writeCacheData(skip, cacheFile, data, offset, length);
    // Add new data chunk in a linked list
    if (skip == 0) {
      addNewWriteChunk(env, thisPtr, startPos, length, cacheFile);
    } else {
      addNewWriteChunk(env, thisPtr, fileLength, written, cacheFile);
    }
    
    
    // File length was increased
    if (startPos + length > fileLength) {
      env.setLongField(thisPtr, "length", startPos + length);
    }

    return length;
  }

  // Create new file to write BFS data
  private static File createCacheFile() throws IOException {
    return File.createTempFile("jpf", "cache", fsCacheDir);
  }

  // Write data chunk in a separate file
  private static int writeCacheData(int skip, File cacheFile, byte[] data, int offset, int length) throws Exception {
    RandomAccessFile raf = new RandomAccessFile(cacheFile, "rws");
    raf.seek(skip);
    raf.write(data, offset, length);

    return length + skip;
  }

  // Create new data chunk object and add it to data chunks' list
  private static void addNewWriteChunk(MJIEnv env, int thisPtr, long startPos, int length, File cacheFile) {
    int newWCRef = env.newObject("gov.nasa.jpf.WriteChunk");
    env.setLongField(newWCRef, "startPos", startPos);
    env.setIntField(newWCRef, "length", length);
    int fileNameRef = env.newString(cacheFile.getName());
    env.setReferenceField(newWCRef, "fileName", fileNameRef);

    int lastWriteChunkRef = env.getReferenceField(thisPtr, "lastWriteChunk");
    env.setReferenceField(newWCRef, "prevChunk", lastWriteChunkRef);

    env.setReferenceField(thisPtr, "lastWriteChunk", newWCRef);
  }

  
  private static class ReadChunk {
    // Offset from the start pos in the buffer, where data should be written
    int offset;
    // Length of the data that user requested to read
    int length;
    
    public ReadChunk(int offset, int length) {
      this.offset = offset;
      this.length = length;
    }
  }
  
  public static int read__J_3BII__I(MJIEnv env, int thisPtr, long startPos, int dataPtr, int offset, int length) throws Exception {
    byte[] data = env.getByteArrayObject(dataPtr);

    if (offset + length > data.length) {
      env.throwException("java.lang.IndexOutOfBoundsException");
      return -1;
    }

    if (length == 0) {
      return 0;
    }

    long fileLength = env.getLongField(thisPtr, "length");

    // Attempt to read beyond a file
    if (fileLength < startPos) {
      return -1;
    }

    int readBytes;
    if (startPos + length < fileLength) {
      // Whole buffer will be filled with data
      readBytes = length;
      
    } else {
      // Only part of the buffer will be fileed
      readBytes = (int) (fileLength - startPos);
    }

    int writeChunk = env.getReferenceField(thisPtr, "lastWriteChunk");

    // This list stores arreas in the buffer that should be read. Initialy it contains
    // one read chunk that shows that whole buffer should be filled with data, but during
    // we will iterate through data chunks list some of buffer's parts will be filled
    ArrayList<ReadChunk> readList = new ArrayList<ReadChunk>();
    readList.add(new ReadChunk(0, readBytes));

    // Iterate through data chunks list
    while (writeChunk != MJIEnv.NULL) {
      long wcOffset = env.getLongField(writeChunk, "startPos");
      int wcLength = env.getIntField(writeChunk, "length");
      String cacheFileName = env.getStringField(writeChunk, "fileName");
      long delta = wcOffset - startPos;

      ListIterator<ReadChunk> iter = readList.listIterator();
      while (iter.hasNext()) {
        ReadChunk readChunk = iter.next();
        int rcOff = readChunk.offset;
        int rcLen = readChunk.length;
        final long writeChunkEnd = delta + wcLength;
        final int readChunkEnd = rcOff + rcLen;

        // Read chunk includes write chunk
        if (delta >= rcOff && writeChunkEnd <= readChunkEnd) {
          readData(cacheFileName, 0, data, (int) (rcOff + offset + delta), wcLength);
          iter.remove();


          addNewReadChunk(iter, rcOff, (int) delta - rcOff);
          addNewReadChunk(iter, (int) delta + wcLength, (int)( readChunkEnd - writeChunkEnd));

        } else if (delta >= rcOff && writeChunkEnd >= readChunkEnd && readChunkEnd >= delta) {
          // Left part of a read chunk can't be read from this write chunk
          readData(cacheFileName, 0, data, (int)(offset + delta), (int)(readChunkEnd - delta));

          iter.remove();
          addNewReadChunk(iter, rcOff, (int)(delta - rcOff));
        } else if (delta <= rcOff && writeChunkEnd > rcOff && writeChunkEnd < readChunkEnd) {
          // Right chunk can't be read from this write chunk
          readData(cacheFileName, rcOff - delta, data, offset + rcOff, (int) (wcLength + delta - rcOff));

          iter.remove();
          addNewReadChunk(iter,(int) (delta + wcLength), (int) (rcOff + rcLen - delta - wcLength));
        }
        else if (delta < rcOff && writeChunkEnd > readChunkEnd) {
          // Write chunk includes read chunk
          readData(cacheFileName, rcOff - delta, data, offset + rcOff, rcLen);

          iter.remove();
        }
      }

      writeChunk = env.getReferenceField(writeChunk, "prevChunk");
    }

    // If some parts of buffer still isn't filled with data we can fill them
    // with data on a native FS
    if (!readList.isEmpty()) {
      String fsNativeFile = env.getStringField(thisPtr, "nativeFSFileName");
      File nativeFile = new File(fsNativeFile);

      readLeftChunksFromNativeFS(nativeFile, startPos, data, offset, readList);
    }

    return readBytes;
  }

  private static void addNewReadChunk(ListIterator<ReadChunk> iter, int rcOff, int rcLen) {
    if (rcLen > 0) {
      ReadChunk rcChunk = new ReadChunk(rcOff, rcLen);
      iter.add(rcChunk);
    }
  }

  /**
   * Read data from cache file
   * @param cacheFileName - name of a cache file that stores data with a current chunk
   * @param filePos - offset in cache file
   * @param data - buffer to read data to
   * @param offset - offset in buffer
   * @param length - number of bytes to read
   * @throws Exception
   */
  private static void readData(String cacheFileName, long filePos, byte[] data, int offset, int length) throws Exception {
    File cacheFile = new File(fsCacheDir, cacheFileName);
    RandomAccessFile raf = new RandomAccessFile(cacheFile, "r");

    raf.seek(filePos);
    raf.read(data, offset, length);
  }

  /**
   * Read parts of file that wasn't overwritten by SUT and should be read into a buffer
   * @param nativeFile - canonical path of a file on a native FS
   * @param startPos - offset in a native file
   * @param data - buffer to read data to
   * @param bufferOffset - 
   * @param readList - list of data chunks to fill in a buffer
   * @throws Exception
   */
  private static void readLeftChunksFromNativeFS(File nativeFile, long startPos, byte[] data, int bufferOffset, ArrayList<ReadChunk> readList) throws Exception {
    RandomAccessFile raf = new RandomAccessFile(nativeFile, "r");

    for (ReadChunk readPos : readList) {      
      int rcOffset = readPos.offset;
      int rcLength = readPos.length;
      raf.seek(startPos + rcOffset);

      raf.read(data, bufferOffset + rcOffset, rcLength);
    }
  }
}
