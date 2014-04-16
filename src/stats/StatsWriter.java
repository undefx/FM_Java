package stats;

import fergusonmodel.Main;
import java.io.*;

public class StatsWriter {

   public StatsWriter(String filename, SimulationInfo simulationInfo, RuntimeInfo runtimeInfo) {
      this.filename = filename;
      this.simulationInfo = simulationInfo;
      this.runtimeInfo = runtimeInfo;
   }

   public void Initialize() throws IOException {
      dayOffsetsTable = new int[simulationInfo.numDays];
      raf = new RandomAccessFile(filename, "rw");
      raf.writeInt(Main.VERSION);
      //Write the simulation info
      simulationInfo.Write(raf);
      //Write the runtime info here to allocate space in the file. This will
      //eventually be re-written, but first we need to know the location and size
      //of the data structure.
      runtimeInfoOffset = raf.getFilePointer();
      runtimeInfo.Write(raf);
      for (int i = 0; i < simulationInfo.numDays; i++) {
         dayOffsetsTable[i] = (int) raf.getFilePointer();
         raf.writeLong(-1);
      }
      saveStatePointerOffset = raf.getFilePointer();
      raf.writeLong(-1);
   }

   public void SaveDay(int day, PatchStats[] patchStats, StrainStats[] strainStats) throws IOException {
      long filePointer = raf.getFilePointer();
      raf.seek(dayOffsetsTable[day]);
      raf.writeLong(filePointer);
      raf.seek(filePointer);
      BufferedOutputStream bos = new BufferedOutputStream(new Output(raf));
      DataOutput output = new DataOutputStream(bos);
      for (PatchStats stats : patchStats) {
         output.writeInt(stats.naive);
         output.writeInt(stats.exposed);
         output.writeInt(stats.infectious);
         output.writeInt(stats.recovered);
         output.writeInt(stats.incidence);
      }
      output.writeInt(strainStats.length);
      for (StrainStats stats : strainStats) {
         output.writeUTF(stats.rna);
         output.writeUTF(stats.protein);
         output.writeInt(stats.id);
         output.writeInt(stats.parentID);
         output.writeInt(stats.infected);
         output.writeInt(stats.incidence);
         output.writeInt(stats.mutations);
         output.writeInt(stats.age);
      }
      bos.close();
   }

   public DataOutput GetSaveStateOutput() throws IOException {
      //Update the save state file offset in the header
      long filePointer = raf.getFilePointer();
      raf.seek(saveStatePointerOffset);
      raf.writeLong(filePointer);
      raf.seek(filePointer);
      //Return the output stream so the other classes can save their states
      bufferedOutput = new BufferedOutputStream(new Output(raf));
      DataOutput output = new DataOutputStream(bufferedOutput);
      return output;
   }

   public void Close() throws IOException {
      if (bufferedOutput != null) {
         bufferedOutput.close();
      }
      //Seek to the runtime info file location, update the simulation timer, and
      //(re)write the runtime info data structure
      raf.seek(runtimeInfoOffset);
      runtimeInfo.UpdateSimlationTimer();
      runtimeInfo.Write(raf);
      //All done, close the file
      raf.close();
   }

   //This wraps the RandomAcessFile as an output stream - it's much more efficient
   public static class Output extends OutputStream {

      public Output(RandomAccessFile raf) {
         this.raf = raf;
      }

      @Override
      public void write(int b) throws IOException {
         raf.write(b);
      }

      @Override
      public void write(byte[] b) throws IOException {
         raf.write(b);
      }

      @Override
      public void write(byte[] b, int off, int len) throws IOException {
         raf.write(b, off, len);
      }
      protected RandomAccessFile raf;
   }
   protected String filename;
   protected RandomAccessFile raf;
   protected int[] dayOffsetsTable;
   protected SimulationInfo simulationInfo;
   protected RuntimeInfo runtimeInfo;
   protected long runtimeInfoOffset;
   protected long saveStatePointerOffset;
   protected BufferedOutputStream bufferedOutput;
}
