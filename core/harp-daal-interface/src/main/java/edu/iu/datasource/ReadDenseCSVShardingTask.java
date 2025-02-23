/*
 * Copyright 2013-2018 Indiana University
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package edu.iu.datasource;

import java.io.IOException;
import java.lang.*;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FSDataInputStream;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;

import java.util.LinkedList;
import java.util.List;

import edu.iu.harp.schdynamic.Task;

public class ReadDenseCSVShardingTask implements
    Task<String, List<double[][]>> {

  protected static final Log LOG = LogFactory
      .getLog(ReadDenseCSVShardingTask.class);

  private int valperline;
  private Configuration conf;
  private long threadId;
  private String sep;
  private int shardsize;

  public ReadDenseCSVShardingTask(int valperline, int shardsize, String sep, Configuration conf) {
    this.valperline = valperline;
    this.shardsize = shardsize;
    this.sep = sep;
    this.conf = conf;
    this.threadId = 0;
  }

  /**
   * @param fileName the hdfs file name
   * @return
   * @brief Java thread kernel
   * each file contains several records (lines)
   * vals of each line is stored as a double[] in a Java List
   */
  @Override
  public List<double[][]> run(String fileName)
      throws Exception {

    threadId = Thread.currentThread().getId();

    int count = 0;
    boolean isSuccess = false;
    do {

      try {

        List<double[][]> array = loadPoints(fileName, valperline, shardsize, conf);
        return array;

      } catch (Exception e) {
        LOG.error("load " + fileName
            + " fails. Count=" + count, e);
        Thread.sleep(100);
        isSuccess = false;
        count++;
      }

    } while (!isSuccess && count < 100);

    LOG.error("Fail to load files.");
    return null;
  }

  private List<double[][]> loadPoints(String file, int valperline, int shardsize,
                                      Configuration conf) throws Exception {//{{{

    System.out.println("filename: " + file);
    System.out.println("Shad size: " + shardsize);

    int shadptr = 0;
    double[][] points = new double[shardsize][];

    List<double[][]> outputlist = new LinkedList<double[][]>();

    Path pointFilePath = new Path(file);
    FileSystem fs =
        pointFilePath.getFileSystem(conf);
    FSDataInputStream in = fs.open(pointFilePath);
    String readline = null;

    try {

      while ((readline = in.readLine()) != null) {
        String[] line = readline.split(this.sep);
        double[] trainpoint = new double[valperline];
        for (int j = 0; j < valperline; j++)
          trainpoint[j] = Double.parseDouble(line[j]);

        points[shadptr++] = trainpoint;

        if (shadptr == shardsize) {
          outputlist.add(points);
          points = new double[shardsize][];
          shadptr = 0;
        }

      }

    } finally {
      in.close();
    }

    if (shadptr > 0) {
      //compress the last shad
      double[][] lastshad = new double[shadptr][];
      for (int j = 0; j < shadptr; j++)
        lastshad[j] = points[j];

      outputlist.add(lastshad);
      points = null;
    }

    return outputlist;

  }//}}}

}
