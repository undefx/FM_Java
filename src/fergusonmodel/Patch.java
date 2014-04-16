package fergusonmodel;

import java.io.*;
import stats.PatchStats;
import stats.SimulationInfo;
import util.LinkedHashMap;

public class Patch {

   public Patch(SimulationInfo simulationInfo, int id, Host[] refHosts) {
      stats = new PatchStats(simulationInfo.hostsPerPatch, 0, 0, 0, 0);
      //Allocate hosts
      hosts = new Host[simulationInfo.hostsPerPatch];
      //Create hosts
      if (id != 0 && refHosts != null) {
         //Reuse the existing neighborhood map
         for (int i = 0; i < hosts.length; i++) {
            hosts[i] = new Host(id, i);
            hosts[i].numNeighbors = refHosts[i].numNeighbors;
            hosts[i].neighborIndex = refHosts[i].neighborIndex;
         }
         return;
      } else {
         //Create the hosts now, and create/load the neighborhood later
         for (int i = 0; i < hosts.length; i++) {
            hosts[i] = new Host(id, i);
         }
      }
      //Try to read a saved patch file - the host locations and neighborhood map are stored in this file
      File file = new File(String.format("patch-%d.bin", hosts.length));
      try {
         if (file.exists()) {
            //A file with the right name exists
            RandomAccessFile raf = new RandomAccessFile(file, "r");
            //Read the entire file into memory for faster processing
            byte[] buffer = new byte[(int) raf.length()];
            raf.readFully(buffer);
            raf.close();
            //Load the file
            Load(new DataInputStream(new ByteArrayInputStream(buffer)));
            return;
         }
      } catch (Exception ex) {
         ex.printStackTrace();
      }
      throw new RuntimeException("Couldn't load patch file! (Does one exist?)");
   }

   public void Load(DataInput dis) throws IOException {
      int numHosts = dis.readInt();
      //Make sure the number oh hosts in the file matched the expected number
      if (numHosts != hosts.length) {
         throw new IOException("File has " + numHosts + " hosts, Patch has " + hosts.length + " hosts");
      }
      int arrayLength = dis.readInt();
      int offset = 0;
      Host.neighborList = new int[arrayLength];
      for (int i = 0; i < hosts.length; i++) {
         hosts[i].numNeighbors = dis.readByte();
         hosts[i].neighborIndex = offset;
         offset += hosts[i].numNeighbors;
      }
      for (int i = 0; i < arrayLength; i++) {
         Host.neighborList[i] = dis.readInt();
      }
   }

   public void SaveState(DataOutput out) throws IOException {
      for (Host host : hosts) {
         host.SaveState(out);
      }
   }

   public void LoadState(DataInput in, LinkedHashMap<Integer, Strain> strains) throws IOException {
      for (Host host : hosts) {
         host.LoadState(in, strains);
      }
   }
   public Host[] hosts;
   public PatchStats stats;
}
