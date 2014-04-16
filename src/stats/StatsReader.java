package stats;

import fergusonmodel.Main;
import java.io.*;

public class StatsReader {

   public StatsReader(String filename) {
      this.filename = filename;
   }

   public void Initialize() throws IOException {
      raf = new RandomAccessFile(filename, "r");
      int version = raf.readInt();
      int majorVersion = (version >> 16) & 0xFFFF;
      //int minorVersion = version & 0xFFFF;
      if (majorVersion != Main.VERSION_MAJOR) {
         String message = "(found " + majorVersion + ", expected " + Main.VERSION_MAJOR + ")";
         if (majorVersion >= 1 && majorVersion < Main.VERSION_MAJOR) {
            //This is an old file, but it may still be readable (backwards compatibility)
            System.err.println("StatsReader is running in compatibility mode " + message);
         } else {
            throw new IOException("Bad FILE_FORMAT_VERSION " + message);
         }
      }
      simulationInfo = SimulationInfo.Read(raf, majorVersion);
      runtimeInfo = RuntimeInfo.Read(raf, majorVersion);
      dayOffsets = new long[simulationInfo.numDays];
      for (int i = 0; i < simulationInfo.numDays; i++) {
         dayOffsets[i] = raf.readLong();
         //Check to see if the offset is defined. It's ok if it's not because it
         //is nice to be able to analyze the stats file while the simulation is
         //still running. If this happens on a completed stats file, then
         //something is terribly wrong.
         if (dayOffsets[i] == -1L) {
            //Assume that the data is truncated somewhere in the middle of the previous day
            simulationInfo.numDays = i - 1;
            System.err.println("Warning: The stats file is corrupted starting at (or before) day " + i);
            break;
         }
      }
      saveStateOffset = raf.readLong();
   }

   public void ReadDay(int day, PatchStats[][] patchStats, StrainStats[][] strainStats) throws IOException {
      raf.seek(dayOffsets[day]);
      DataInput input = new DataInputStream(new BufferedInputStream(new Input(raf)));
      patchStats[0] = new PatchStats[simulationInfo.numPatches];
      for (int i = 0; i < patchStats[0].length; i++) {
         patchStats[0][i] = new PatchStats(input.readInt(), input.readInt(), input.readInt(), input.readInt(), input.readInt());
      }
      int numStrains = input.readInt();
      strainStats[0] = new StrainStats[numStrains];
      for (int i = 0; i < strainStats[0].length; i++) {
         strainStats[0][i] = new StrainStats(input.readUTF(), input.readUTF(), input.readInt(), input.readInt(), input.readInt(), input.readInt(), input.readInt(), input.readInt());
      }
   }

   public DataInput GetSaveStateInput() throws IOException {
      //Update the save state file offset in the header
      raf.seek(saveStateOffset);
      //Return the output stream so the other classes can save their states
      bufferedInput = new BufferedInputStream(new Input(raf));
      DataInput input = new DataInputStream(bufferedInput);
      return input;
   }

   public void Close() throws IOException {
      raf.close();
   }

   //This wraps the RandomAcessFile as an input stream - it's much more efficient
   public static class Input extends InputStream {

      public Input(RandomAccessFile raf) {
         this.raf = raf;
      }

      @Override
      public int read() throws IOException {
         return raf.read();
      }

      @Override
      public int read(byte[] b) throws IOException {
         return raf.read(b);
      }

      @Override
      public int read(byte[] b, int off, int len) throws IOException {
         return raf.read(b, off, len);
      }
      protected RandomAccessFile raf;
   }
   public SimulationInfo simulationInfo;
   public RuntimeInfo runtimeInfo;
   protected String filename;
   protected RandomAccessFile raf;
   protected long[] dayOffsets;
   protected long saveStateOffset;
   protected BufferedInputStream bufferedInput;
}
