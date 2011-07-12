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
import java.io.File;

/**
 * This class contains static methods that are used by several peer classes.
 * @author Ivan Mushketik
 */
public class BFSUtils {
  
  // This method is used by FileInfo and FileState peers.
  public static File getCacheDir(Config config) throws RuntimeException {    
    String fsCacheDirName = config.getString("jpf-bfs.writecache_dir");
    File fsCacheDir = new File(fsCacheDirName);
    // Create directory where data, that will be written on BFS during SUT run will be save
    // Each data chunk saved to a separate file
    if (!fsCacheDir.exists()) {
      if (!fsCacheDir.mkdir()) {
        throw new RuntimeException("Unable to create cache dir " + fsCacheDirName);
      }
    }

    return fsCacheDir;
  }
}
