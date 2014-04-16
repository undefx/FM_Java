package util;

import fergusonmodel.Main;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.List;

public class PatchGenerator {

   //Creates a population distribution (one of Ferguson's "patches") and saves it to disk
   public static void Run(long seed, int numHosts) throws Exception {
      File file = new File("patch-" + numHosts + ".bin");
      if (file.exists()) {
         throw new Exception("The file [" + file.getName() + "] already exists; please delete it first.");
      }
      MersenneTwister r = new MersenneTwister(seed);
      int numBins = (int) (Math.sqrt(numHosts) * .48);
      Main.Print("Points = " + numHosts);
      Main.Print("Bins = " + numBins + "x" + numBins + " = " + (numBins * numBins));
      float[] xs, ys;
      xs = new float[numHosts];
      ys = new float[numHosts];
      List<List<List<Integer>>> bins = new ArrayList<>();
      for (int i = 0; i < numBins; i++) {
         bins.add(new ArrayList<List<Integer>>());
         for (int j = 0; j < numBins; j++) {
            bins.get(i).add(new ArrayList<Integer>());
         }
      }
      Main.Print("Generating points...");
      for (int i = 0; i < numHosts; i++) {
         xs[i] = r.nextFloat();
         ys[i] = r.nextFloat();
         int xbin = (int) (xs[i] * numBins);
         int ybin = (int) (ys[i] * numBins);
         bins.get(xbin).get(ybin).add(i);
      }
      Main.Print("Finding average minimum distance...");
      double sum = 0;
      double num = 0;
//      int debug = 0;
      for (int i = 0; i < numHosts / 2; i += 2) {
         int xbin = (int) (xs[i] * numBins);
         int ybin = (int) (ys[i] * numBins);
         double min = 2;
         for (int j = xbin - 1; j <= xbin + 1; j++) {
            if (j < 0 || j >= numBins) {
               continue;
            }
            for (int k = ybin - 1; k <= ybin + 1; k++) {
               if (k < 0 || k >= numBins) {
                  continue;
               }
               for (int a : bins.get(j).get(k)) {
//         for (int a = 0; a < numPoints; a++) {
                  if (i == a) {
                     continue;
                  }
                  double dx = xs[i] - xs[a];
                  double dy = ys[i] - ys[a];
                  double dist = Math.sqrt(dx * dx + dy * dy);
                  if (dist < min) {
                     min = dist;
                  }
//         }
               }
            }
         }
         sum += min;
         ++num;
      }
      double avg = sum / num;
      Main.Print("Average min dist is " + avg);
      double multiplier = 1.0 / avg;
      Main.Print("Multiplier is " + multiplier);
      double binCoverage = (multiplier / numBins);
      Main.Print("There are " + numBins + " bins over a space of 1. That means each bin covers " + binCoverage + " units per axis.");
      if (binCoverage < 4) {
         throw new Exception("Bin coverage is too low!");
      }
      Main.Print("Scanning for neighbors...");
      List<List<Integer>> neighbors = new ArrayList<>();
      for (int i = 0; i < numHosts; i++) {
         List<Integer> list = new ArrayList<>();
         neighbors.add(list);
         int xbin = (int) (xs[i] * numBins);
         int ybin = (int) (ys[i] * numBins);
         for (int j = xbin - 1; j <= xbin + 1; j++) {
            if (j < 0 || j >= numBins) {
               continue;
            }
            for (int k = ybin - 1; k <= ybin + 1; k++) {
               if (k < 0 || k >= numBins) {
                  continue;
               }
               for (int a : bins.get(j).get(k)) {
                  if (i == a) {
                     continue;
                  }
                  double dx = xs[i] - xs[a];
                  double dy = ys[i] - ys[a];
                  double dist = Math.sqrt(dx * dx + dy * dy) * multiplier;
                  if (dist < 4) {
                     list.add(a);
                  }
               }
            }
         }
         if ((i + 1) % 100000 == 0) {
            Main.Print(" " + (i + 1) + "/" + numHosts + "   (" + list.size() + ")");
         }
      }
      Main.Print("Finding neighbor stats...");
      sum = 0;
      int min = 0xFF, max = 0, total = 0;
      for (List<Integer> list : neighbors) {
         int size = list.size();
         sum += size;
         if (size > max) {
            max = size;
         } else if (size < min) {
            min = size;
         }
         total += size;
      }
      System.out.printf("Min=%d | Avg=%.3f | Max=%d | Total=%d\n", min, (sum / (double) numHosts), max, total);
      if (max >= 0xFF) {
         throw new Exception("Some point has too many neighbors!");
      }
      Main.Print("Saving results...");
      //===File Format===
      //int numHosts
      //int arrayLength
      //int[numHosts] numNeighbors (sum to get array offset)
      //int[arrayLength] neighbors
      RandomAccessFile raf = new RandomAccessFile(file, "rw");
      ByteArrayOutputStream baos = new ByteArrayOutputStream();
      DataOutputStream dos = new DataOutputStream(baos);
      //Save the number of hosts - this will be used as a sanity check when loading the file
      dos.writeInt(numHosts);
      //Save the size of the neighbors array
      dos.writeInt(total);
      //Save the number of neighbors for each host (can be used to calcualte array offset)
      for (List<Integer> list : neighbors) {
         dos.writeByte(list.size());
      }
      //Save the neighbor array
      for (List<Integer> list : neighbors) {
         for (int a : list) {
            dos.writeInt(a);
         }
      }
      raf.write(baos.toByteArray());
      raf.close();
      Main.Print("Done!");
//      Main.Print("debug = " + debug);
   }
}
